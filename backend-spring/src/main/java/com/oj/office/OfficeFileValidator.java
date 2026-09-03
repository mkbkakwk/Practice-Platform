package com.oj.office;

import com.oj.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.oj.office.OfficeDocumentException.Category.*;

@Component
public class OfficeFileValidator {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_MAIN_PART_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            DOCX_CONTENT_TYPE,
            "application/octet-stream",
            "application/zip");
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "[Content_Types].xml", "_rels/.rels", "word/document.xml");

    private final AppProperties.Office limits;

    public OfficeFileValidator(AppProperties properties) {
        this.limits = properties.getOffice();
    }

    public String validateMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OfficeDocumentException(INVALID_DOCUMENT, "empty upload");
        }
        if (file.getSize() < 0 || file.getSize() > limits.getMaxUploadBytes()) {
            throw new OfficeDocumentException(FILE_TOO_LARGE, "upload exceeds hard byte limit");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new OfficeDocumentException(INVALID_FILE_TYPE, "missing filename");
        }
        if (original.indexOf('/') >= 0 || original.indexOf('\\') >= 0
                || original.contains("..") || Path.of(original).isAbsolute()
                || original.matches("(?i)^[a-z]:.*")) {
            throw new OfficeDocumentException(INVALID_FILE_TYPE, "unsafe upload filename");
        }
        String baseName = Path.of(original).getFileName().toString();
        if (baseName.isBlank() || baseName.contains("\r") || baseName.contains("\n")
                || !baseName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new OfficeDocumentException(INVALID_FILE_TYPE, "filename is not DOCX");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new OfficeDocumentException(INVALID_FILE_TYPE, "content type is not OOXML");
        }
        return sanitizeDisplayName(baseName);
    }

    public void validateContainer(Path path) {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new OfficeDocumentException(INVALID_DOCUMENT, "upload is not a regular file");
        }
        try {
            long fileSize = Files.size(path);
            if (fileSize <= 0 || fileSize > limits.getMaxUploadBytes()) {
                throw new OfficeDocumentException(FILE_TOO_LARGE, "stored upload exceeds hard byte limit");
            }
            try (InputStream raw = new BufferedInputStream(Files.newInputStream(path))) {
                raw.mark(8);
                byte[] signature = raw.readNBytes(8);
                if (isOle2(signature)) {
                    throw new OfficeDocumentException(PASSWORD_PROTECTED,
                            "OLE2/encrypted Office packages are not supported");
                }
                if (signature.length < 2 || signature[0] != 'P' || signature[1] != 'K') {
                    throw new OfficeDocumentException(INVALID_FILE_TYPE, "missing ZIP signature");
                }
            }
            inspectZip(path);
        } catch (OfficeDocumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OfficeDocumentException(INVALID_DOCUMENT, "unable to inspect OOXML package", exception);
        }
    }

    private void inspectZip(Path path) throws IOException {
        Set<String> entries = new HashSet<>();
        long expanded = 0;
        int count = 0;
        byte[] buffer = new byte[8192];
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var iterator = zip.entries().asIterator();
            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                count++;
                if (count > limits.getMaxZipEntries()) {
                    throw new OfficeDocumentException(INVALID_DOCUMENT, "too many ZIP entries");
                }
                String rawName = entry.getName();
                String name = rawName.replace('\\', '/');
                if (!rawName.equals(name) || name.startsWith("/") || name.contains("../")
                        || name.equals("..") || name.matches("(?i)^[a-z]:.*")) {
                    throw new OfficeDocumentException(INVALID_DOCUMENT, "unsafe ZIP entry path");
                }
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (lowerName.endsWith("vbaproject.bin")
                        || lowerName.startsWith("embeddings/")
                        || lowerName.contains("/embeddings/")) {
                    throw new OfficeDocumentException(UNSUPPORTED_DOCUMENT, "macro or embedded object present");
                }
                if (!entries.add(name)) {
                    throw new OfficeDocumentException(INVALID_DOCUMENT, "duplicate ZIP entry");
                }
                long declaredSize = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (declaredSize > limits.getMaxEntryBytes()) {
                    throw new OfficeDocumentException(INVALID_DOCUMENT, "declared ZIP entry too large");
                }
                if (declaredSize > 1024 && compressed > 0
                        && ((double) compressed / declaredSize) < limits.getMinInflateRatio()) {
                    throw new OfficeDocumentException(INVALID_DOCUMENT, "unsafe ZIP inflate ratio");
                }
                long entryBytes = 0;
                StringBuilder relationship = lowerName.endsWith(".rels") ? new StringBuilder() : null;
                try (InputStream input = zip.getInputStream(entry)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        entryBytes += read;
                        expanded += read;
                        if (entryBytes > limits.getMaxEntryBytes()
                                || expanded > limits.getMaxExpandedBytes()) {
                            throw new OfficeDocumentException(INVALID_DOCUMENT, "expanded OOXML limit exceeded");
                        }
                        if (relationship != null) {
                            if (relationship.length() + read > 512 * 1024) {
                                throw new OfficeDocumentException(INVALID_DOCUMENT,
                                        "relationships XML too large");
                            }
                            relationship.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                        }
                    }
                }
                if (entryBytes > 1024 && compressed > 0
                        && ((double) compressed / entryBytes) < limits.getMinInflateRatio()) {
                    throw new OfficeDocumentException(INVALID_DOCUMENT, "unsafe ZIP inflate ratio");
                }
                if (relationship != null
                        && relationship.toString().toLowerCase(Locale.ROOT)
                        .matches("(?s).*targetmode\\s*=\\s*['\"]external['\"].*")) {
                    throw new OfficeDocumentException(UNSUPPORTED_DOCUMENT, "external relationship present");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new OfficeDocumentException(INVALID_DOCUMENT, "malformed ZIP package", exception);
        }
        if (!entries.containsAll(REQUIRED_ENTRIES)) {
            throw new OfficeDocumentException(INVALID_DOCUMENT, "missing required OOXML entries");
        }
        verifyWordContentType(path);
    }

    private void verifyWordContentType(Path path) throws IOException {
        String contentTypes = null;
        byte[] buffer = new byte[8192];
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var iterator = zip.entries().asIterator();
            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                if ("[Content_Types].xml".equals(entry.getName())) {
                    StringBuilder value = new StringBuilder();
                    try (InputStream input = zip.getInputStream(entry)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            value.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                            if (value.length() > 512 * 1024) {
                                throw new OfficeDocumentException(INVALID_DOCUMENT, "content types XML too large");
                            }
                        }
                    }
                    contentTypes = value.toString();
                    break;
                }
            }
        }
        if (contentTypes == null || !contentTypes.contains(DOCX_MAIN_PART_CONTENT_TYPE)) {
            throw new OfficeDocumentException(INVALID_FILE_TYPE, "OOXML package is not DOCX");
        }
    }

    private boolean isOle2(byte[] signature) {
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        return signature.length >= ole2.length && java.util.Arrays.equals(signature, ole2);
    }

    private String sanitizeDisplayName(String name) {
        String sanitized = name.replaceAll("[\\p{Cntrl}]", "_")
                .replaceAll("[^\\p{L}\\p{N} ._()\\-]", "_");
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(sanitized.length() - 120);
        }
        return sanitized;
    }
}

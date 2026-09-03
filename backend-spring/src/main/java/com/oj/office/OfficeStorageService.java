package com.oj.office;

import com.oj.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.oj.office.OfficeDocumentException.Category.FILE_TOO_LARGE;
import static com.oj.office.OfficeDocumentException.Category.STORAGE_FAILED;

@Component
public class OfficeStorageService {

    private final Path root;
    private final long maxUploadBytes;

    public OfficeStorageService(AppProperties properties) {
        this.root = Path.of(properties.getDocStorage()).toAbsolutePath().normalize();
        this.maxUploadBytes = properties.getOffice().getMaxUploadBytes();
    }

    public StagedDocument stage(MultipartFile file, String displayName) {
        try {
            rejectUnsafeRoot();
            Files.createDirectories(root);
            rejectUnsafeRoot();
            Path temp = Files.createTempFile(root, ".upload-", ".tmp");
            long copied;
            try (InputStream input = file.getInputStream()) {
                copied = copyBounded(input, temp);
            } catch (RuntimeException | IOException exception) {
                Files.deleteIfExists(temp);
                throw exception;
            }
            if (copied == 0) {
                Files.deleteIfExists(temp);
                throw new OfficeDocumentException(STORAGE_FAILED, "empty upload stream");
            }
            return new StagedDocument(temp, displayName);
        } catch (OfficeDocumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OfficeDocumentException(STORAGE_FAILED, "unable to stage document", exception);
        }
    }

    public StoredDocument commit(StagedDocument staged) {
        Path target = root.resolve(UUID.randomUUID() + ".docx").normalize();
        if (!target.getParent().equals(root)) {
            throw new OfficeDocumentException(STORAGE_FAILED, "unsafe generated storage path");
        }
        try {
            try {
                Files.move(staged.path(), target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged.path(), target);
            }
            return new StoredDocument(target, target.getFileName().toString(), staged.displayName());
        } catch (IOException exception) {
            throw new OfficeDocumentException(STORAGE_FAILED, "unable to commit document", exception);
        }
    }

    public Path require(String storedPath) {
        Path path = resolve(storedPath);
        if (path == null || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new OfficeDocumentException(STORAGE_FAILED, "stored document missing");
        }
        return path;
    }

    public boolean delete(String storedPath) {
        Path path = resolve(storedPath);
        if (path == null || Files.isSymbolicLink(path)) return false;
        try {
            if (Files.exists(path) && !Files.isRegularFile(path)) return false;
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            return false;
        }
    }

    public void discard(StagedDocument staged) {
        if (staged == null) return;
        try {
            Files.deleteIfExists(staged.path());
        } catch (IOException ignored) {
        }
    }

    public String path(StoredDocument stored) {
        return stored.storageId();
    }

    public List<String> managedFilesOlderThan(Instant cutoff) {
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) return List.of();
        try (var files = Files.list(root)) {
            return files.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                    .filter(path -> path.getFileName().toString().matches("[0-9a-f-]{36}\\.docx"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                        } catch (IOException exception) {
                            return false;
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private long copyBounded(InputStream input, Path target) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        try (var output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxUploadBytes) {
                    throw new OfficeDocumentException(FILE_TOO_LARGE, "stream exceeds hard byte limit");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;
        try {
            Path supplied = Path.of(storedPath);
            Path path;
            if (supplied.isAbsolute()) {
                path = supplied.toAbsolutePath().normalize();
            } else {
                if (supplied.getNameCount() != 1
                        || !supplied.getFileName().toString().matches("[0-9a-f-]{36}\\.docx")) {
                    return null;
                }
                path = root.resolve(supplied).normalize();
            }
            if (!path.getParent().equals(root)) return null;
            return path;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void rejectUnsafeRoot() {
        Path current = root;
        while (current != null) {
            if (Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(current)) {
                throw new OfficeDocumentException(STORAGE_FAILED, "unsafe document storage ancestry");
            }
            current = current.getParent();
        }
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new OfficeDocumentException(STORAGE_FAILED, "unsafe document storage root");
        }
    }

    public record StagedDocument(Path path, String displayName) {
    }

    public record StoredDocument(Path path, String storageId, String displayName) {
    }
}

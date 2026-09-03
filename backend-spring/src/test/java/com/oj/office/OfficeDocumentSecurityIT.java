package com.oj.office;

import com.oj.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.oj.office.OfficeDocumentException.Category.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfficeDocumentSecurityIT {

    @TempDir
    Path temp;

    @Test
    void validDocxMetadataAndContainerAreAccepted() throws Exception {
        byte[] docx = minimalDocx("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>");
        OfficeFileValidator validator = validator(new AppProperties.Office());
        MockMultipartFile upload = upload("valid.docx", OfficeFileValidator.DOCX_CONTENT_TYPE, docx);
        Path path = write("valid.docx", docx);

        assertThat(validator.validateMetadata(upload)).isEqualTo("valid.docx");
        validator.validateContainer(path);
    }

    @Test
    void fakeExtensionMimeAndPathTraversalAreRejected() {
        OfficeFileValidator validator = validator(new AppProperties.Office());
        assertCategory(() -> validator.validateMetadata(upload("fake.doc", OfficeFileValidator.DOCX_CONTENT_TYPE, new byte[]{1})), INVALID_FILE_TYPE);
        assertCategory(() -> validator.validateMetadata(upload("fake.docx", "text/plain", new byte[]{1})), INVALID_FILE_TYPE);
        assertCategory(() -> validator.validateMetadata(upload("../escape.docx", OfficeFileValidator.DOCX_CONTENT_TYPE, new byte[]{1})), INVALID_FILE_TYPE);
        assertCategory(() -> validator.validateMetadata(upload("C:\\escape.docx", OfficeFileValidator.DOCX_CONTENT_TYPE, new byte[]{1})), INVALID_FILE_TYPE);
    }

    @Test
    void oversizedUploadIsRejectedBeforeParsing() {
        AppProperties.Office limits = new AppProperties.Office();
        limits.setMaxUploadBytes(8);
        OfficeFileValidator validator = validator(limits);
        assertCategory(() -> validator.validateMetadata(upload("large.docx", OfficeFileValidator.DOCX_CONTENT_TYPE, new byte[9])), FILE_TOO_LARGE);
    }

    @Test
    void invalidAndTruncatedZipAreRejected() throws Exception {
        OfficeFileValidator validator = validator(new AppProperties.Office());
        assertCategory(() -> validator.validateContainer(write("fake.docx", "PKnot-a-zip".getBytes(StandardCharsets.UTF_8))), INVALID_DOCUMENT);
        byte[] valid = minimalDocx("<w:p/>");
        assertCategory(() -> validator.validateContainer(write("truncated.docx", java.util.Arrays.copyOf(valid, valid.length / 2))), INVALID_DOCUMENT);
    }

    @Test
    void highCompressionRatioIsRejected() throws Exception {
        AppProperties.Office limits = new AppProperties.Office();
        limits.setMinInflateRatio(0.20);
        OfficeFileValidator validator = validator(limits);
        Map<String, byte[]> entries = requiredEntries();
        entries.put("word/document.xml", ("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"
                + "A".repeat(100_000) + "</w:t></w:r></w:p></w:body></w:document>").getBytes(StandardCharsets.UTF_8));
        assertCategory(() -> validator.validateContainer(write("bomb.docx", zip(entries))), INVALID_DOCUMENT);
    }

    @Test
    void hugeXmlEntryIsRejected() throws Exception {
        AppProperties.Office limits = new AppProperties.Office();
        limits.setMinInflateRatio(0);
        limits.setMaxEntryBytes(2048);
        OfficeFileValidator validator = validator(limits);
        Map<String, byte[]> entries = requiredEntries();
        entries.put("word/document.xml", "X".repeat(2049).getBytes(StandardCharsets.UTF_8));
        assertCategory(() -> validator.validateContainer(write("huge.docx", zip(entries))), INVALID_DOCUMENT);
    }

    @Test
    void macroAndEmbeddedObjectAreRejected() throws Exception {
        OfficeFileValidator validator = validator(new AppProperties.Office());
        Map<String, byte[]> macro = requiredEntries();
        macro.put("word/vbaProject.bin", new byte[]{1});
        assertCategory(() -> validator.validateContainer(write("macro.docx", zip(macro))), UNSUPPORTED_DOCUMENT);
        Map<String, byte[]> embedded = requiredEntries();
        embedded.put("word/embeddings/object1.bin", new byte[]{1});
        assertCategory(() -> validator.validateContainer(write("embedded.docx", zip(embedded))), UNSUPPORTED_DOCUMENT);
    }

    @Test
    void externalRelationshipsAreRejected() throws Exception {
        OfficeFileValidator validator = validator(new AppProperties.Office());
        Map<String, byte[]> entries = requiredEntries();
        entries.put("word/_rels/document.xml.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="r1" TargetMode="External" Target="https://example.invalid/evil"/>
                </Relationships>
                """.getBytes(StandardCharsets.UTF_8));
        assertCategory(() -> validator.validateContainer(write("external.docx", zip(entries))), UNSUPPORTED_DOCUMENT);
    }

    @Test
    void encryptedOle2PackageIsRejectedWithSafeCategory() throws Exception {
        OfficeFileValidator validator = validator(new AppProperties.Office());
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        assertCategory(() -> validator.validateContainer(write("encrypted.docx", ole2)), PASSWORD_PROTECTED);
    }

    @Test
    void malformedOoxmlAndUnsafeZipEntryAreRejected() throws Exception {
        OfficeFileValidator validator = validator(new AppProperties.Office());
        Map<String, byte[]> missingCore = new LinkedHashMap<>();
        missingCore.put("[Content_Types].xml", contentTypes());
        assertCategory(() -> validator.validateContainer(write("missing.docx", zip(missingCore))), INVALID_DOCUMENT);

        Map<String, byte[]> traversal = requiredEntries();
        traversal.put("../outside.xml", new byte[]{1});
        assertCategory(() -> validator.validateContainer(write("traversal.docx", zip(traversal))), INVALID_DOCUMENT);
    }

    private OfficeFileValidator validator(AppProperties.Office limits) {
        AppProperties properties = new AppProperties();
        properties.setOffice(limits);
        return new OfficeFileValidator(properties);
    }

    private MockMultipartFile upload(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private Path write(String name, byte[] content) throws Exception {
        return Files.write(temp.resolve(name), content);
    }

    private byte[] minimalDocx(String body) throws Exception {
        Map<String, byte[]> entries = requiredEntries();
        entries.put("word/document.xml", ("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>%s</w:body></w:document>
                """.formatted(body)).getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private Map<String, byte[]> requiredEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", contentTypes());
        entries.put("_rels/.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="r1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """.getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>".getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private byte[] contentTypes() {
        return """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private void assertCategory(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                OfficeDocumentException.Category category) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(OfficeDocumentException.class,
                        exception -> assertThat(exception.category()).isEqualTo(category));
    }
}

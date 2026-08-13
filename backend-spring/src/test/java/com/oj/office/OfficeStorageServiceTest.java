package com.oj.office;

import com.oj.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OfficeStorageServiceTest {

    private static final Pattern STORAGE_ID = Pattern.compile("[0-9a-f-]{36}\\.docx");

    @TempDir
    Path root;

    @Test
    void storageUsesServerUuidAndImmediatelyReleasesFileHandles() throws Exception {
        OfficeStorageService storage = storage();
        var staged = storage.stage(upload("display name.docx", new byte[]{1, 2, 3}), "display name.docx");
        var stored = storage.commit(staged);

        assertThat(stored.path().getParent()).isEqualTo(root.toAbsolutePath().normalize());
        assertThat(stored.path().getFileName().toString()).matches(STORAGE_ID);
        assertThat(stored.path().getFileName().toString()).doesNotContain("display name");
        assertThat(storage.require(storage.path(stored))).isEqualTo(stored.path());
        assertThat(storage.delete(storage.path(stored))).isTrue();
        assertThat(stored.path()).doesNotExist();
    }

    @Test
    void outsidePathsCannotBeReadOrDeleted() throws Exception {
        OfficeStorageService storage = storage();
        Path outside = Files.write(root.getParent().resolve("outside-office.docx"), new byte[]{1});
        try {
            assertThat(storage.delete(outside.toString())).isFalse();
            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void twentyConcurrentUploadsHaveUniqueFilesAndNoCrossDeletion() throws Exception {
        OfficeStorageService storage = storage();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<OfficeStorageService.StoredDocument>> tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<OfficeStorageService.StoredDocument>) () -> {
                        byte[] bytes = ("document-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        var staged = storage.stage(upload("same.docx", bytes), "same.docx");
                        return storage.commit(staged);
                    }).toList();
            List<OfficeStorageService.StoredDocument> stored = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
                    }).toList();

            assertThat(stored).hasSize(20);
            Set<Path> unique = new HashSet<>(stored.stream().map(OfficeStorageService.StoredDocument::path).toList());
            assertThat(unique).hasSize(20);
            for (int index = 0; index < stored.size(); index++) {
                assertThat(Files.readString(stored.get(index).path())).startsWith("document-");
            }
            for (OfficeStorageService.StoredDocument document : stored) {
                assertThat(storage.delete(storage.path(document))).isTrue();
            }
        }
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    private OfficeStorageService storage() {
        AppProperties properties = new AppProperties();
        properties.setDocStorage(root.toString());
        return new OfficeStorageService(properties);
    }

    private MockMultipartFile upload(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, OfficeFileValidator.DOCX_CONTENT_TYPE, bytes);
    }
}

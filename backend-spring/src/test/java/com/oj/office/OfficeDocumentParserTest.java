package com.oj.office;

import com.oj.config.AppProperties;
import com.oj.office.model.OfficeDocumentModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfficeDocumentParserTest {

    @TempDir
    Path temp;

    private final OfficeDocumentParser parser = new OfficeDocumentParser(new AppProperties());

    @Test
    void canonicalizesAllRunsAndFormatting() throws Exception {
        OfficeDocumentModel model = parser.parse(fixture("mixed-format.docx"));

        assertThat(model.paragraphs()).hasSize(1);
        assertThat(model.paragraphs().getFirst().text()).isEqualTo("First run Second run");
        assertThat(model.paragraphs().getFirst().runs()).hasSize(2);
        assertThat(model.paragraphs().getFirst().runs().getFirst().fontFamily()).isEqualTo("calibri");
        assertThat(model.paragraphs().getFirst().runs().getFirst().fontSizeHundredths()).isEqualTo(1200);
        assertThat(model.paragraphs().getFirst().runs().getFirst().bold()).isTrue();
        assertThat(model.paragraphs().getFirst().runs().get(1).italic()).isTrue();
    }

    @Test
    void canonicalizesTableStructureInsteadOfFlatteningIt() throws Exception {
        OfficeDocumentModel model = parser.parse(fixture("table.docx"));

        assertThat(model.tables()).hasSize(1);
        assertThat(model.tables().getFirst().rows()).hasSize(2);
        assertThat(model.tables().getFirst().rows()).allSatisfy(row -> assertThat(row).hasSize(2));
    }

    @Test
    void handlesEmptyDocumentAndClosesFileHandle() throws Exception {
        Path path = fixture("empty.docx");
        assertThat(parser.parse(path).paragraphs()).isEmpty();

        Files.delete(path);
        assertThat(path).doesNotExist();
    }

    @Test
    void damagedDocumentFailsWithControlledCategory() throws Exception {
        assertThatThrownBy(() -> parser.parse(fixture("damaged.docx")))
                .isInstanceOfSatisfying(OfficeDocumentException.class,
                        exception -> assertThat(exception.category())
                                .isEqualTo(OfficeDocumentException.Category.INVALID_DOCUMENT));
    }

    @Test
    void concurrentReadOnlyParsingDoesNotMutateSharedReference() throws Exception {
        Path path = fixture("normal.docx");
        String before = digest(path);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<OfficeDocumentModel>) () -> parser.parse(path))
                    .toList();
            for (var future : executor.invokeAll(tasks)) {
                assertThat(future.get().paragraphs()).isNotEmpty();
            }
        }

        assertThat(digest(path)).isEqualTo(before);
        Files.delete(path);
        assertThat(path).doesNotExist();
    }

    private String digest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private Path fixture(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/docx/" + name)) {
            assertThat(input).isNotNull();
            Path target = temp.resolve(name + "-" + java.util.UUID.randomUUID());
            Files.copy(input, target);
            return target;
        }
    }
}

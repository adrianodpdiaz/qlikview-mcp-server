package com.qlikview.mcp.analysis;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FieldTagReaderTest {

    @TempDir
    Path tempDir;

    private final FieldTagReader reader = new FieldTagReader();

    @Language("XML")
    private static final String DOC_INTERNALS_FIXTURE = """
        <DocInternals>
          <FieldTags>
            <ScriptBased>
              <FieldTagData>
                <Tag>$key</Tag>
                <FieldNames>
                  <String>CustomerID</String>
                </FieldNames>
              </FieldTagData>
              <FieldTagData>
                <Tag>$system</Tag>
                <FieldNames>
                  <String>$Field</String>
                  <String>$Table</String>
                </FieldNames>
              </FieldTagData>
              <FieldTagData>
                <Tag>$numeric</Tag>
                <FieldNames>
                  <String>CustomerID</String>
                  <String>SalesAmount</String>
                </FieldNames>
              </FieldTagData>
            </ScriptBased>
          </FieldTags>
        </DocInternals>
        """;

    @Test
    void readsFieldTagsFromPrjExport() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("DocInternals.xml"), DOC_INTERNALS_FIXTURE, StandardCharsets.UTF_8);

        Optional<Set<FieldTagReader.FieldTag>> result = reader.readFieldTags(document);

        assertThat(result).isPresent();
        assertThat(result.get()).extracting(FieldTagReader.FieldTag::name)
            .containsExactlyInAnyOrder("CustomerID", "$Field", "$Table", "SalesAmount");
    }

    @Test
    void marksKeyFieldCorrectly() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("DocInternals.xml"), DOC_INTERNALS_FIXTURE, StandardCharsets.UTF_8);

        Set<FieldTagReader.FieldTag> tags = reader.readFieldTags(document).orElseThrow();

        FieldTagReader.FieldTag customerId = tags.stream()
            .filter(t -> t.name().equals("CustomerID")).findFirst().orElseThrow();
        assertThat(customerId.isKey()).isTrue();
        assertThat(customerId.isSystem()).isFalse();
        assertThat(customerId.tags()).contains("$key", "$numeric");
    }

    @Test
    void marksSystemFieldCorrectly() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("DocInternals.xml"), DOC_INTERNALS_FIXTURE, StandardCharsets.UTF_8);

        Set<FieldTagReader.FieldTag> tags = reader.readFieldTags(document).orElseThrow();

        FieldTagReader.FieldTag field = tags.stream()
            .filter(t -> t.name().equals("$Field")).findFirst().orElseThrow();
        assertThat(field.isSystem()).isTrue();
        assertThat(field.isKey()).isFalse();
    }

    @Test
    void returnsEmptyWhenNoPrjFolderExists() {
        Path document = tempDir.resolve("report.qvw");
        assertThat(reader.readFieldTags(document)).isEmpty();
    }

    @Test
    void returnsEmptyWhenPrjFolderExistsButHasNoDocInternals() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Files.createDirectory(tempDir.resolve("report-prj"));
        assertThat(reader.readFieldTags(document)).isEmpty();
    }
}

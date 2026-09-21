package com.qlikview.mcp.analysis;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectIndexReaderTest {

    @TempDir
    Path tempDir;

    private final ProjectIndexReader reader = new ProjectIndexReader();

    @Language("XML")
    private static final String PROJECT_FIXTURE = """
        <PrjQlikViewProject>
          <SHEETS>
            <PrjSheetProperties>
              <SheetId>Document\\SH01</SheetId>
              <ChildObjects>
                <PrjFrameParentDef>
                  <ObjectId>Document\\CS01</ObjectId>
                </PrjFrameParentDef>
                <PrjFrameParentDef>
                  <ObjectId>Document\\CH01</ObjectId>
                </PrjFrameParentDef>
              </ChildObjects>
            </PrjSheetProperties>
          </SHEETS>
        </PrjQlikViewProject>
        """;

    @Test
    void readsSheetsAndTheirObjectIds() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("QlikViewProject.xml"), PROJECT_FIXTURE, StandardCharsets.UTF_8);

        Optional<List<ProjectIndexReader.SheetEntry>> sheets = reader.readSheets(document);

        assertThat(sheets).isPresent();
        assertThat(sheets.get()).hasSize(1);
        assertThat(sheets.get().getFirst().sheetId()).isEqualTo("Document\\SH01");
        assertThat(sheets.get().getFirst().objectIds()).containsExactly("Document\\CS01", "Document\\CH01");
    }

    @Test
    void returnsEmptyWhenNoPrjFolderExists() {
        Path document = tempDir.resolve("report.qvw");
        assertThat(reader.readSheets(document)).isEmpty();
    }

    @Test
    void readsObjectTypeFromItsOwnFileRootElement() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("CH01.xml"), "<GraphProperties></GraphProperties>", StandardCharsets.UTF_8);

        Optional<ProjectIndexReader.ObjectEntry> object = reader.readObject(document, "Document\\CH01");

        assertThat(object).isPresent();
        assertThat(object.get().type()).isEqualTo("GraphProperties");
        assertThat(object.get().objectId()).isEqualTo("Document\\CH01");
    }

    @Test
    void returnsEmptyWhenObjectFileDoesNotExist() {
        Path document = tempDir.resolve("report.qvw");
        assertThat(reader.readObject(document, "Document\\CH99")).isEmpty();
    }
}

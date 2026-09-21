package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ProjectIndexReader;
import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetSheetsToolTest {

    @TempDir
    Path tempDir;

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
              </ChildObjects>
            </PrjSheetProperties>
          </SHEETS>
        </PrjQlikViewProject>
        """;

    private final ProjectIndexReader projectIndexReader = new ProjectIndexReader();
    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private GetSheetsTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetSheetsTool(guard(), projectIndexReader, gateway);
    }

    @Test
    void defaultsToStaticSource() throws IOException {
        Path document = writeDocumentWithProjectXml();

        List<GetSheetsTool.SheetSummary> result = tool.getSheets(document.toString(), null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sheetId()).isEqualTo("Document\\SH01");
        assertThat(result.getFirst().caption()).isEmpty();
        assertThat(result.getFirst().objects()).extracting(GetSheetsTool.SheetObjectSummary::objectId)
            .containsExactly("Document\\CS01");
    }

    @Test
    void liveTrueReadsFromGatewayInstead() throws IOException {
        Path document = writeDocumentWithProjectXml();
        when(gateway.getSheets(document)).thenReturn(new QlikViewGateway.SheetInfo[]{
            new QlikViewGateway.SheetInfo("Main", new QlikViewGateway.SheetObjectInfo[]{
                new QlikViewGateway.SheetObjectInfo("Document\\CH03", "11")
            })
        });

        List<GetSheetsTool.SheetSummary> result = tool.getSheets(document.toString(), true);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().caption()).isEqualTo("Main");
        assertThat(result.getFirst().sheetId()).isEmpty();
        assertThat(result.getFirst().objects().getFirst().objectType()).isEqualTo("11");
    }

    @Test
    void throwsWhenNoPrjExportExistsForStaticSource() {
        Path document = tempDir.resolve("nodoc.qvw");

        assertThatThrownBy(() -> tool.getSheets(document.toString(), null))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining("No -prj export found");
    }

    private Path writeDocumentWithProjectXml() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("QlikViewProject.xml"), PROJECT_FIXTURE, StandardCharsets.UTF_8);
        return document;
    }

    private DocumentPathGuard guard() {
        QlikViewProperties properties = new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000));
        return new DocumentPathGuard(properties);
    }
}

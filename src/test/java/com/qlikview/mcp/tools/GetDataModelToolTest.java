package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.FieldTagReader;
import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.analysis.TableNameParser;
import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import com.qlikview.mcp.guard.OutputLimiter;
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

class GetDataModelToolTest {

    @TempDir
    Path tempDir;

    private final ScriptReader scriptReader = new ScriptReader();
    private final TableNameParser tableNameParser = new TableNameParser();
    private final FieldTagReader fieldTagReader = new FieldTagReader();
    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private GetDataModelTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetDataModelTool(guard(), scriptReader, tableNameParser, fieldTagReader, gateway, outputLimiter());
    }

    @Test
    void defaultsToStaticSource() throws IOException {
        Path document = writeDocumentWithScriptAndTags(
            "Fact:\nLOAD RecNo() as FactID AUTOGENERATE 10;",
            """
                    <DocInternals>
                      <FieldTags>
                        <ScriptBased>
                          <FieldTagData>
                            <Tag>$numeric</Tag>
                            <FieldNames><String>FactID</String></FieldNames>
                          </FieldTagData>
                        </ScriptBased>
                      </FieldTags>
                    </DocInternals>
                    """);

        GetDataModelTool.DataModelSummary result = tool.getDataModel(document.toString(), null);

        assertThat(result.tables()).containsExactly("Fact");
        assertThat(result.fields()).hasSize(1);
        assertThat(result.fields().getFirst().name()).isEqualTo("FactID");
        assertThat(result.fields().getFirst().cardinal()).isZero();
        assertThat(result.fields().getFirst().srcTables()).isEmpty();
    }

    @Test
    void liveTrueReadsRealCardinalityAndTableMembershipFromGateway() throws IOException {
        Path document = writeDocumentWithScriptAndTags("Fact:\nLOAD RecNo() as FactID AUTOGENERATE 10;", null);
        when(gateway.getDataModel(document)).thenReturn(new QlikViewGateway.DataModel(
            new QlikViewGateway.TableInfo[]{new QlikViewGateway.TableInfo("Fact"), new QlikViewGateway.TableInfo("Customer")},
            new QlikViewGateway.FieldInfo[]{
                new QlikViewGateway.FieldInfo("FactID", 10, false, true, new String[]{"Fact"}),
                new QlikViewGateway.FieldInfo("CustomerID", 5, false, true, new String[]{"Fact", "Customer"})
            }));

        GetDataModelTool.DataModelSummary result = tool.getDataModel(document.toString(), true);

        assertThat(result.tables()).containsExactly("Fact", "Customer");
        assertThat(result.fields()).extracting(GetDataModelTool.FieldSummary::name).containsExactly("FactID", "CustomerID");
        assertThat(result.fields().get(0).cardinal()).isEqualTo(10);
        assertThat(result.fields().get(0).isKey()).isFalse();
        assertThat(result.fields().get(1).isKey()).isTrue();
        assertThat(result.fields().get(1).srcTables()).containsExactly("Fact", "Customer");
    }

    @Test
    void liveFalseExplicitlyUsesStaticSource() throws IOException {
        Path document = writeDocumentWithScriptAndTags("Fact:\nLOAD RecNo() as FactID AUTOGENERATE 10;", null);

        GetDataModelTool.DataModelSummary result = tool.getDataModel(document.toString(), false);
        assertThat(result.tables()).containsExactly("Fact");
    }

    @Test
    void throwsWhenNoPrjExportExistsForStaticSource() {
        Path document = tempDir.resolve("nodoc.qvw");

        assertThatThrownBy(() -> tool.getDataModel(document.toString(), null))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining("No -prj export found");
    }

    @Test
    void truncatesTablesAndFieldsIndependentlyWhenExceedingConfiguredLimit() throws IOException {
        tool = new GetDataModelTool(guard(), scriptReader, tableNameParser, fieldTagReader, gateway, outputLimiter(1));
        Path document = writeDocumentWithScriptAndTags("Fact:\nLOAD RecNo() as FactID AUTOGENERATE 10;", null);
        when(gateway.getDataModel(document)).thenReturn(new QlikViewGateway.DataModel(
            new QlikViewGateway.TableInfo[]{new QlikViewGateway.TableInfo("Fact"), new QlikViewGateway.TableInfo("Customer")},
            new QlikViewGateway.FieldInfo[]{
                new QlikViewGateway.FieldInfo("FactID", 10, false, true, new String[]{"Fact"}),
                new QlikViewGateway.FieldInfo("CustomerID", 5, false, true, new String[]{"Fact", "Customer"})
            }));

        GetDataModelTool.DataModelSummary result = tool.getDataModel(document.toString(), true);

        assertThat(result.tables()).hasSize(1);
        assertThat(result.fields()).hasSize(1);
    }

    private Path writeDocumentWithScriptAndTags(String script, String docInternalsXml) throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("LoadScript.txt"), script, StandardCharsets.UTF_8);
        if (docInternalsXml != null) {
            Files.writeString(prjFolder.resolve("DocInternals.xml"), docInternalsXml, StandardCharsets.UTF_8);
        }
        return document;
    }

    private DocumentPathGuard guard() {
        return new DocumentPathGuard(properties(1000));
    }

    private OutputLimiter outputLimiter() {
        return outputLimiter(1000);
    }

    private OutputLimiter outputLimiter(int maxItems) {
        return new OutputLimiter(properties(maxItems));
    }

    private QlikViewProperties properties(int maxItems) {
        return new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, maxItems));
    }
}

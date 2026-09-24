package com.qlikview.mcp.tools;

import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDataModelToolTest {

    @TempDir
    Path tempDir;

    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private GetDataModelTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetDataModelTool(guard(), gateway, outputLimiter());
    }

    @Test
    void readsRealCardinalityAndTableMembershipFromGateway() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getDataModel(document)).thenReturn(new QlikViewGateway.DataModel(
            new QlikViewGateway.TableInfo[]{new QlikViewGateway.TableInfo("Fact"), new QlikViewGateway.TableInfo("Customer")},
            new QlikViewGateway.FieldInfo[]{
                new QlikViewGateway.FieldInfo("FactID", 10, false, true, new String[]{"Fact"}),
                new QlikViewGateway.FieldInfo("CustomerID", 5, false, true, new String[]{"Fact", "Customer"})
            }));

        GetDataModelTool.DataModelSummary result = tool.getDataModel(document.toString());

        assertThat(result.tables()).containsExactly("Fact", "Customer");
        assertThat(result.fields()).extracting(GetDataModelTool.FieldSummary::name)
            .containsExactly("FactID", "CustomerID");
        assertThat(result.fields().get(0).cardinal()).isEqualTo(10);
        assertThat(result.fields().get(0).isKey()).isFalse();
        assertThat(result.fields().get(1).isKey()).isTrue();
        assertThat(result.fields().get(1).srcTables()).containsExactly("Fact", "Customer");
    }

    @Test
    void truncatesTablesAndFieldsIndependentlyWhenExceedingConfiguredLimit() {
        tool = new GetDataModelTool(guard(), gateway, outputLimiter(1));
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getDataModel(document)).thenReturn(new QlikViewGateway.DataModel(
            new QlikViewGateway.TableInfo[]{new QlikViewGateway.TableInfo("Fact"), new QlikViewGateway.TableInfo("Customer")},
            new QlikViewGateway.FieldInfo[]{
                new QlikViewGateway.FieldInfo("FactID", 10, false, true, new String[]{"Fact"}),
                new QlikViewGateway.FieldInfo("CustomerID", 5, false, true, new String[]{"Fact", "Customer"})
            }));

        GetDataModelTool.DataModelSummary result = tool.getDataModel(document.toString());

        assertThat(result.tables()).hasSize(1);
        assertThat(result.fields()).hasSize(1);
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

package com.qlikview.mcp.tools;

import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Returns a document's actual in-memory associative data model, read from a running QlikView
 * Desktop instance: real table names, real cardinality per field, and real table membership (from
 * which {@code isKey} is derived - a field belonging to more than one table is a key). Requires
 * QlikView Desktop installed, licensed, and running, with the document open or reachable, at the
 * moment the tool is called.
 * <p>
 * {@code tables} and {@code fields} are each capped at {@code qlikview.output.max-items}
 * independently; a document with more of either than that returns only the first {@code
 * max-items} of that list.
 */
@Component
@RequiredArgsConstructor
public class GetDataModelTool {

    private final DocumentPathGuard pathGuard;
    private final QlikViewGateway gateway;
    private final OutputLimiter outputLimiter;

    /**
     * A document's data model: table names, and fields.
     */
    public record DataModelSummary(List<String> tables, List<FieldSummary> fields) {
    }

    /**
     * One field: its name, whether it is an associative key linking multiple tables, whether it
     * is one of QlikView's own built-in fields, its real cardinality, whether its values are
     * numeric, and which table(s) it belongs to.
     */
    public record FieldSummary(
        String name, boolean isKey, boolean isSystem,
        long cardinal, boolean isNumeric, List<String> srcTables) {
    }

    @McpTool(
        name = "get_data_model",
        description = "Get a QlikView document's actual associative data model from a running QlikView Desktop "
            + "instance: table names, and per field its associative-key status, system-field status, real "
            + "cardinality, and table membership.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public DataModelSummary getDataModel(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);
        QlikViewGateway.DataModel model = gateway.getDataModel(resolved);

        List<String> tables = Arrays.stream(model.tables())
            .map(QlikViewGateway.TableInfo::name)
            .toList();

        List<FieldSummary> fields = Arrays.stream(model.fields())
            .map(f -> new FieldSummary(f.name(), f.srcTables().length > 1, f.isSystem(),
                f.cardinal(), f.isNumeric(), Arrays.asList(f.srcTables())))
            .toList();

        return new DataModelSummary(
            outputLimiter.limitList(tables).items(),
            outputLimiter.limitList(fields).items());
    }
}

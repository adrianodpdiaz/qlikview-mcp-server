package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.FieldTagReader;
import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.analysis.TableNameParser;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Returns a document's table names and field tags, read from its load script and {@code -prj}
 * export. This is a partial data model, not the full associative structure QlikView itself has in
 * memory: table names come from the script's {@code TableName:} labels, and each field is
 * reported with whether it is an associative key linking multiple tables ({@code isKey}) and
 * whether it is one of QlikView's own built-in fields ({@code isSystem}) - but not which table(s)
 * a given field belongs to, nor row counts or distinct-value counts, none of which are recorded
 * anywhere in the {@code -prj} export.
 */
@Component
@RequiredArgsConstructor
public class GetDataModelTool {

    private final DocumentPathGuard pathGuard;
    private final ScriptReader scriptReader;
    private final TableNameParser tableNameParser;
    private final FieldTagReader fieldTagReader;

    /**
     * A document's partial data model: table names from the script, and field tags from the
     * {@code -prj} export. See the class-level documentation for what is and is not included.
     */
    public record DataModelSummary(List<String> tables, List<FieldSummary> fields) { }

    /**
     * One field's tags as recorded by QlikView: whether it links multiple tables, whether it is
     * one of QlikView's own built-in fields, and every raw tag QlikView recorded for it.
     */
    public record FieldSummary(String name, boolean isKey, boolean isSystem, List<String> tags) { }

    @McpTool(
        name = "get_data_model",
        description = "Get a QlikView document's table names (from the script) and field tags (from the -prj export): "
            + "which fields are associative keys, which are QlikView's own system fields. "
            + "Does not include table-to-field grouping, row counts, or distinct-value counts.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public DataModelSummary getDataModel(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);

        String script = scriptReader.readScript(resolved).orElseThrow(() -> new GuardException(
            "No -prj export found for this document. Create a folder named '"
                + resolved.getFileName() + "-prj' next to it and save the document in "
                + "QlikView Desktop, then try again."));
        List<String> tables = tableNameParser.parse(script);

        List<FieldSummary> fields = fieldTagReader.readFieldTags(resolved)
            .map(tags -> tags.stream()
                .map(t -> new FieldSummary(t.name(), t.isKey(), t.isSystem(), t.tags().stream().toList()))
                .toList())
            .orElse(List.of());

        return new DataModelSummary(tables, fields);
    }
}

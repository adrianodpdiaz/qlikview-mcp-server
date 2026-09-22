package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.FieldTagReader;
import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.analysis.TableNameParser;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import com.qlikview.mcp.guard.OutputLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Returns a document's data model, either read from its load script and {@code -prj} export (the
 * default) or from a running QlikView Desktop instance's actual in-memory associative model
 * ({@code live=true}).
 * <p>
 * The static (default) source is partial: table names come from the script's {@code TableName:}
 * labels, and each field is reported with whether it is an associative key linking multiple
 * tables ({@code isKey}) and whether it is one of QlikView's own built-in fields ({@code
 * isSystem}) - but not which table(s) a given field belongs to, nor row counts or distinct-value
 * counts, none of which are recorded anywhere in the {@code -prj} export. The live source has all
 * of this: real cardinality ({@code cardinal}) and real table membership ({@code srcTables}, from
 * which {@code isKey} is derived - a field belonging to more than one table is a key) - but
 * requires QlikView Desktop installed, licensed, and running, with the document open or
 * reachable, at the moment the tool is called.
 * <p>
 * {@code tables} and {@code fields} are each capped at {@code qlikview.output.max-items}
 * independently; a document with more of either than that returns only the first {@code
 * max-items} of that list.
 */
@Component
@RequiredArgsConstructor
public class GetDataModelTool {

    private final DocumentPathGuard pathGuard;
    private final ScriptReader scriptReader;
    private final TableNameParser tableNameParser;
    private final FieldTagReader fieldTagReader;
    private final QlikViewGateway gateway;
    private final OutputLimiter outputLimiter;

    /**
     * A document's data model: table names, and fields. See the class-level documentation for
     * what each source (static vs. live) does and does not include.
     */
    public record DataModelSummary(List<String> tables, List<FieldSummary> fields) {
    }

    /**
     * One field. {@code tags} and {@code cardinal}/{@code srcTables} are only populated by their
     * respective source (static populates {@code tags}, live populates {@code cardinal}/{@code
     * isNumeric}/{@code srcTables}); {@code isKey} and {@code isSystem} are populated by both.
     */
    public record FieldSummary(
        String name, boolean isKey, boolean isSystem, List<String> tags,
        long cardinal, boolean isNumeric, List<String> srcTables) {
    }

    @McpTool(
        name = "get_data_model",
        description = "Get a QlikView document's data model: by default, table names (from the script) and field "
            + "tags (from the -prj export), including associative keys and QlikView's own system fields, but "
            + "not table-to-field grouping, row counts, or distinct-value counts. With live=true, the "
            + "document's actual associative data model from a running QlikView Desktop instance, including "
            + "real cardinality and table membership.",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public DataModelSummary getDataModel(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "Read the actual data model from a running QlikView Desktop instance "
                + "instead of the -prj export (default false)", required = false) Boolean live) {
        Path resolved = pathGuard.resolve(document);
        DataModelSummary model = Boolean.TRUE.equals(live) ? getLiveDataModel(resolved) : getStaticDataModel(resolved);
        return new DataModelSummary(
            outputLimiter.limitList(model.tables()).items(),
            outputLimiter.limitList(model.fields()).items());
    }

    private DataModelSummary getStaticDataModel(Path resolved) {
        String script = scriptReader.readScript(resolved).orElseThrow(() -> new GuardException(
            "No -prj export found for this document. Create a folder named '"
                + resolved.getFileName() + "-prj' next to it and save the document in "
                + "QlikView Desktop, then try again."));
        List<String> tables = tableNameParser.parse(script);

        List<FieldSummary> fields = fieldTagReader.readFieldTags(resolved)
            .map(tags -> tags.stream()
                .map(t -> new FieldSummary(t.name(), t.isKey(), t.isSystem(), t.tags().stream().toList(),
                    0, false, List.of()))
                .toList())
            .orElse(List.of());

        return new DataModelSummary(tables, fields);
    }

    private DataModelSummary getLiveDataModel(Path resolved) {
        QlikViewGateway.DataModel model = gateway.getDataModel(resolved);

        List<String> tables = Arrays.stream(model.tables())
            .map(QlikViewGateway.TableInfo::name)
            .toList();

        List<FieldSummary> fields = Arrays.stream(model.fields())
            .map(f -> new FieldSummary(f.name(), f.srcTables().length > 1, f.isSystem(), List.of(),
                f.cardinal(), f.isNumeric(), Arrays.asList(f.srcTables())))
            .toList();

        return new DataModelSummary(tables, fields);
    }
}

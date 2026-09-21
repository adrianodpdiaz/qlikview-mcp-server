package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.DataSourceParser;
import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Returns a document's data sources, derived by parsing its load script: connection statements
 * (with credentials included exactly as written - callers must treat this as sensitive, the same
 * as for {@code get_script}), files referenced in {@code FROM} clauses with their format
 * specifications, {@code $(Include=...)}/{@code $(Must_Include=...)} references, and the
 * {@code BINARY} statement's source document, if present. This is a best-effort text parse, not
 * an evaluation of the script - a source path built from a variable is returned as the literal, unevaluated text.
 */
@Component
@RequiredArgsConstructor
public class GetDataSourcesTool {

    private final DocumentPathGuard pathGuard;
    private final ScriptReader scriptReader;
    private final DataSourceParser dataSourceParser;

    /**
     * One connection statement's declared type and connection string, exactly as written.
     */
    public record ConnectionSummary(String type, String connectionString) { }

    /**
     * One file referenced in a FROM clause. {@code format} is empty when no format specification
     * (such as {@code (qvd)}) was given.
     */
    public record FileSourceSummary(String path, String format) { }

    /**
     * One include reference and whether it was a Must_Include.
     */
    public record IncludeSummary(String path, boolean mustInclude) { }

    /**
     * A document's data sources, grouped by kind. {@code binarySource} is empty when the script has no BINARY statement.
     */
    public record DataSourcesSummary(
        List<ConnectionSummary> connections,
        List<FileSourceSummary> fileSources,
        List<IncludeSummary> includes,
        String binarySource) {
    }

    @McpTool(
        name = "get_data_sources",
        description = "Get a QlikView document's data sources, derived from its load script: connection statements, "
            + "FROM-clause file references, includes, and the BINARY statement's source document",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public DataSourcesSummary getDataSources(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);
        String script = scriptReader.readScript(resolved).orElseThrow(() -> new GuardException(
            "No -prj export found for this document. Create a folder named '"
                + resolved.getFileName() + "-prj' next to it and save the document in "
                + "QlikView Desktop, then try again."));

        DataSourceParser.DataSources sources = dataSourceParser.parse(script);
        return new DataSourcesSummary(
            sources.connections().stream().map(c -> new ConnectionSummary(c.type(), c.connectionString())).toList(),
            sources.fileSources().stream().map(f -> new FileSourceSummary(f.path(), orEmpty(f.format()))).toList(),
            sources.includes().stream().map(i -> new IncludeSummary(i.path(), i.mustInclude())).toList(),
            orEmpty(sources.binarySource()));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

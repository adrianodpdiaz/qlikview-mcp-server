package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.DataSourceParser;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import com.qlikview.mcp.guard.SecretRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Returns a document's data sources, derived by parsing its load script read from a running
 * QlikView Desktop instance: connection statements (with credentials redacted - see
 * {@code connectionString} below), files referenced in {@code FROM} clauses with their format
 * specifications, {@code $(Include=...)}/{@code $(Must_Include=...)} references, and the
 * {@code BINARY} statement's source document, if present. This is a best-effort text parse, not
 * an evaluation of the script - a source path built from a variable is returned as the literal,
 * unevaluated text. Requires QlikView Desktop installed, licensed, and running, with the document
 * open or reachable, at the moment the tool is called.
 * <p>
 * {@code connections}, {@code fileSources}, and {@code includes} are each capped at {@code
 * qlikview.output.max-items} independently; a script with more of any one of them than that
 * returns only the first {@code max-items} of that list.
 */
@Component
@RequiredArgsConstructor
public class GetDataSourcesTool {

    private final DocumentPathGuard pathGuard;
    private final QlikViewGateway gateway;
    private final DataSourceParser dataSourceParser;
    private final SecretRedactor secretRedactor;
    private final OutputLimiter outputLimiter;

    /**
     * One connection statement's declared type and connection string, with any credential-shaped
     * {@code key=value} pair (password, uid, user id, ...) replaced by a masked placeholder.
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
        description = "Get a QlikView document's data sources, derived from its load script read from a running "
            + "QlikView Desktop instance: connection statements, FROM-clause file references, includes, and the "
            + "BINARY statement's source document",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public DataSourcesSummary getDataSources(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);
        String script = gateway.getScript(resolved);

        DataSourceParser.DataSources sources = dataSourceParser.parse(script);
        List<ConnectionSummary> connections = sources.connections().stream()
            .map(c -> new ConnectionSummary(c.type(), secretRedactor.redactConnectionCredentials(c.connectionString())))
            .toList();
        List<FileSourceSummary> fileSources = sources.fileSources().stream()
            .map(f -> new FileSourceSummary(f.path(), orEmpty(f.format()))).toList();
        List<IncludeSummary> includes = sources.includes().stream()
            .map(i -> new IncludeSummary(i.path(), i.mustInclude())).toList();

        return new DataSourcesSummary(
            outputLimiter.limitList(connections).items(),
            outputLimiter.limitList(fileSources).items(),
            outputLimiter.limitList(includes).items(),
            orEmpty(sources.binarySource()));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

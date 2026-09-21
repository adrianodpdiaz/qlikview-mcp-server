package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.analysis.VariableParser;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Returns a document's variables as declared in its load script ({@code SET}/{@code LET}
 * statements). These are the script-defined default values, not necessarily a document's current
 * in-memory state: a {@code LET} value that depends on a function or another variable is returned
 * as the literal, unevaluated script text, and any variable changed at runtime by an input box,
 * action, or macro after the last reload will not be reflected here.
 */
@Component
@RequiredArgsConstructor
public class GetVariablesTool {

    private final DocumentPathGuard pathGuard;
    private final ScriptReader scriptReader;
    private final VariableParser variableParser;

    /**
     * One variable as declared in the script.
     */
    public record VariableSummary(String name, String value, boolean isLet) { }

    @McpTool(
        name = "get_variables",
        description = "Get a QlikView document's variables as declared in its load script (SET/LET statements)",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<VariableSummary> getVariables(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "Only return variables whose name contains this text (case-insensitive)", required = false) String nameFilter) {
        Path resolved = pathGuard.resolve(document);
        String script = scriptReader.readScript(resolved).orElseThrow(() -> new GuardException(
            "No -prj export found for this document. Create a folder named '"
                + resolved.getFileName() + "-prj' next to it and save the document in "
                + "QlikView Desktop, then try again."));

        return variableParser.parse(script).stream()
            .filter(v -> matchesFilter(v.name(), nameFilter))
            .map(v -> new VariableSummary(v.name(), v.value(), v.isLet()))
            .toList();
    }

    private static boolean matchesFilter(String name, String nameFilter) {
        return nameFilter == null || nameFilter.isBlank()
            || name.toLowerCase().contains(nameFilter.toLowerCase());
    }
}

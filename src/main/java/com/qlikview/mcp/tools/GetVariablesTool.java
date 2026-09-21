package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.analysis.VariableParser;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Returns a document's variables, either as declared in its load script (the default) or as
 * currently held in memory by a running QlikView Desktop instance ({@code live=true}).
 * <p>
 * The static (default) source reflects the script's declared values, not necessarily a
 * document's current in-memory state: a {@code LET} value that depends on a function or another
 * variable is returned as the literal, unevaluated script text, and any variable changed at
 * runtime by an input box, action, or macro after the last reload will not be reflected. The live
 * source reflects the document's actual current values, but requires QlikView Desktop installed,
 * licensed, and running, with the document open or reachable, at the moment the tool is called.
 */
@Component
@RequiredArgsConstructor
public class GetVariablesTool {

    private final DocumentPathGuard pathGuard;
    private final ScriptReader scriptReader;
    private final VariableParser variableParser;
    private final QlikViewGateway gateway;

    /**
     * One variable. {@code isLet} is only meaningful for the static (script) source; {@code
     * isSystem} is only meaningful for the live source.
     */
    public record VariableSummary(String name, String value, boolean isLet, boolean isSystem) { }

    @McpTool(
            name = "get_variables",
            description = "Get a QlikView document's variables: by default, as declared in its load script "
                + "(SET/LET statements); with live=true, as currently held in memory by a running QlikView "
                + "Desktop instance",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public List<VariableSummary> getVariables(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "Only return variables whose name contains this text (case-insensitive)", required = false) String nameFilter,
            @McpToolParam(description = "Read current in-memory values from a running QlikView Desktop instance "
                + "instead of the script's declared values (default false)", required = false) Boolean live) {
        Path resolved = pathGuard.resolve(document);
        List<VariableSummary> variables = Boolean.TRUE.equals(live) ?
            getLiveVariables(resolved) : getStaticVariables(resolved);

        return variables.stream().filter(v -> matchesFilter(v.name(), nameFilter)).toList();
    }

    private List<VariableSummary> getStaticVariables(Path resolved) {
        String script = scriptReader.readScript(resolved).orElseThrow(() -> new GuardException(
            "No -prj export found for this document. Create a folder named '"
                + resolved.getFileName() + "-prj' next to it and save the document in "
                + "QlikView Desktop, then try again."));

        return variableParser.parse(script).stream()
            .map(v -> new VariableSummary(v.name(), v.value(), v.isLet(), false))
            .toList();
    }

    private List<VariableSummary> getLiveVariables(Path resolved) {
        QlikViewGateway.VariableDescription[] descriptions = gateway.getVariables(resolved);
        return Arrays.stream(descriptions)
            .map(v -> new VariableSummary(v.name(), v.rawValue(), false, v.isSystem()))
            .toList();
    }

    private static boolean matchesFilter(String name, String nameFilter) {
        return nameFilter == null || nameFilter.isBlank()
            || name.toLowerCase().contains(nameFilter.toLowerCase());
    }
}

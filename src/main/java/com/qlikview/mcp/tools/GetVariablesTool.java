package com.qlikview.mcp.tools;

import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import com.qlikview.mcp.guard.SecretRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Returns a document's variables as currently held in memory by a running QlikView Desktop
 * instance. Requires QlikView Desktop installed, licensed, and running, with the document open or
 * reachable, at the moment the tool is called.
 * <p>
 * A variable whose name suggests it holds a secret (password, token, API key, and similar) is
 * flagged via {@code possibleSecret} rather than having its value withheld - QlikView exposes no
 * marker distinguishing an actual secret from a variable that merely mentions one in its name
 * (e.g. a label), so the caller makes the final call on how to treat it.
 * <p>
 * The returned list is capped at {@code qlikview.output.max-items}, applied after {@code
 * nameFilter}; a document with more matching variables than that returns only the first
 * {@code max-items} of them.
 */
@Component
@RequiredArgsConstructor
public class GetVariablesTool {

    private final DocumentPathGuard pathGuard;
    private final QlikViewGateway gateway;
    private final SecretRedactor secretRedactor;
    private final OutputLimiter outputLimiter;

    /**
     * One variable. {@code isSystem} is true for one of QlikView's own built-in variables.
     * {@code possibleSecret} is true when the variable's name suggests it holds a credential.
     */
    public record VariableSummary(String name, String value, boolean isSystem, boolean possibleSecret) { }

    @McpTool(
            name = "get_variables",
            description = "Get a QlikView document's variables as currently held in memory by a running QlikView "
                + "Desktop instance. Variables whose name suggests a credential are flagged, not withheld.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public List<VariableSummary> getVariables(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "Only return variables whose name contains this text (case-insensitive)", required = false) String nameFilter) {
        Path resolved = pathGuard.resolve(document);
        QlikViewGateway.VariableDescription[] descriptions = gateway.getVariables(resolved);

        List<VariableSummary> variables = Arrays.stream(descriptions)
            .map(v -> new VariableSummary(v.name(), v.rawValue(), v.isSystem(), secretRedactor.looksLikeSecretName(v.name())))
            .filter(v -> matchesFilter(v.name(), nameFilter))
            .toList();

        return outputLimiter.limitList(variables).items();
    }

    private static boolean matchesFilter(String name, String nameFilter) {
        return nameFilter == null || nameFilter.isBlank()
            || name.toLowerCase().contains(nameFilter.toLowerCase());
    }
}

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

/**
 * Returns a document's load script, read from a running QlikView Desktop instance. Requires
 * QlikView Desktop installed, licensed, and running, with the document open or reachable, at the
 * moment the tool is called. Connection-string credentials embedded in the script (e.g.
 * {@code PWD=...;}) are redacted before the response is returned; nothing else in the script is
 * altered.
 */
@Component
@RequiredArgsConstructor
public class GetScriptTool {

    private final DocumentPathGuard pathGuard;
    private final QlikViewGateway gateway;
    private final OutputLimiter outputLimiter;
    private final SecretRedactor secretRedactor;

    @McpTool(
        name = "get_script",
        description = "Get a QlikView document's load script from a running QlikView Desktop instance, including "
            + "tab markers. Connection-string credentials are redacted.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getScript(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);
        String script = gateway.getScript(resolved);
        String redacted = secretRedactor.redactConnectionCredentials(script);
        return outputLimiter.limit(redacted).text();
    }
}

package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import com.qlikview.mcp.guard.OutputLimiter;
import com.qlikview.mcp.guard.SecretRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Returns a document's load script, read from its {@code -prj} export folder. This is a plain
 * text file QlikView writes on save; reading it does not require QlikView to be installed or
 * running, only that the document has been saved at least once with its {@code -prj} folder
 * already present alongside it. Connection-string credentials embedded in the script (e.g.
 * {@code PWD=...;}) are redacted before the response is returned; nothing else in the script is
 * altered.
 */
@Component
@RequiredArgsConstructor
public class GetScriptTool {

    private final DocumentPathGuard pathGuard;
    private final ScriptReader scriptReader;
    private final OutputLimiter outputLimiter;
    private final SecretRedactor secretRedactor;

    @McpTool(
        name = "get_script",
        description = "Get a QlikView document's load script, including tab markers. Connection-string "
            + "credentials are redacted.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String getScript(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);
        String script = scriptReader.readScript(resolved).orElseThrow(() -> new GuardException(
            "No -prj export found for this document. Create a folder named '"
                + resolved.getFileName() + "-prj' next to it and save the document in "
                + "QlikView Desktop, then try again."));
        String redacted = secretRedactor.redactConnectionCredentials(script);
        return outputLimiter.limit(redacted).text();
    }
}

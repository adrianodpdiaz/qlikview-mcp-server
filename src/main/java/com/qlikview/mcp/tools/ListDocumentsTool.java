package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.DocumentScanner;
import com.qlikview.mcp.guard.OutputLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Lists the QlikView documents found under the allowlisted root folders. Pure file-system scan;
 * does not open or read inside any document, and does not require QlikView to be installed or
 * running. The list is capped at {@code qlikview.output.max-items}; a folder with more documents
 * than that returns only the first {@code max-items} found.
 */
@Component
@RequiredArgsConstructor
public class ListDocumentsTool {

    private final DocumentScanner documentScanner;
    private final OutputLimiter outputLimiter;

    /**
     * One listed document, as returned to the MCP client.
     */
    public record DocumentSummary(String name, String path, long sizeBytes, Instant modifiedAt, boolean hasPrjFolder) { }

    @McpTool(
        name = "list_documents",
        description = "List QlikView documents (.qvw/.qvf) found under the configured allowlisted folders",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<DocumentSummary> listDocuments() {
        List<DocumentSummary> documents = documentScanner.listDocuments().stream()
            .map(d -> new DocumentSummary(d.name(), d.path().toString(), d.sizeBytes(), d.modifiedAt(), d.hasPrjFolder()))
            .toList();
        return outputLimiter.limitList(documents).items();
    }
}

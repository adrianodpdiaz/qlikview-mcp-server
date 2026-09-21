package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ProjectIndexReader;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Returns a document's sheets and the objects placed on each, read from its {@code -prj} export.
 * Each object is identified by its id and type (a chart, a current-selections box, and so on);
 * use {@code get_object} with an object's id to see a chart's dimensions and expressions.
 */
@Component
@RequiredArgsConstructor
public class GetSheetsTool {

    private final DocumentPathGuard pathGuard;
    private final ProjectIndexReader projectIndexReader;

    /**
     * One sheet and the objects placed on it.
     */
    public record SheetSummary(String sheetId, List<String> objectIds) { }

    @McpTool(
        name = "get_sheets",
        description = "Get a QlikView document's sheets and the ids of the objects placed on each",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<SheetSummary> getSheets(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);
        List<ProjectIndexReader.SheetEntry> sheets = projectIndexReader.readSheets(resolved)
            .orElseThrow(() -> new GuardException(
                "No -prj export found for this document. Create a folder named '"
                    + resolved.getFileName() + "-prj' next to it and save the document in "
                    + "QlikView Desktop, then try again."));

        return sheets.stream()
            .map(s -> new SheetSummary(s.sheetId(), s.objectIds()))
            .toList();
    }
}

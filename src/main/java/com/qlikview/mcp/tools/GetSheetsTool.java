package com.qlikview.mcp.tools;

import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Returns a document's sheets and the objects placed on each, read from a running QlikView
 * Desktop instance. Use {@code get_object} with an object's id to see a chart's dimensions and
 * expressions. Requires QlikView Desktop installed, licensed, and running, with the document open
 * or reachable, at the moment the tool is called.
 * <p>
 * The returned sheet list is capped at {@code qlikview.output.max-items}; a document with more
 * sheets than that returns only the first {@code max-items} of them. Objects nested under each
 * returned sheet are not capped.
 */
@Component
@RequiredArgsConstructor
public class GetSheetsTool {

    private final DocumentPathGuard pathGuard;
    private final QlikViewGateway gateway;
    private final OutputLimiter outputLimiter;

    /**
     * One sheet and the objects placed on it: its displayed title and, per object, its id and
     * QlikView's own numeric object-type code.
     */
    public record SheetSummary(String caption, List<SheetObjectSummary> objects) { }

    /** One object on a sheet: its id and type. */
    public record SheetObjectSummary(String objectId, String objectType) { }

    @McpTool(
        name = "get_sheets",
        description = "Get a QlikView document's sheets and the objects placed on each, from a running QlikView "
            + "Desktop instance",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public List<SheetSummary> getSheets(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document) {
        Path resolved = pathGuard.resolve(document);
        QlikViewGateway.SheetInfo[] sheets = gateway.getSheets(resolved);

        List<SheetSummary> summaries = Arrays.stream(sheets)
            .map(s -> new SheetSummary(s.caption(),
                Arrays.stream(s.objects())
                    .map(o -> new SheetObjectSummary(o.objectId(), o.objectType()))
                    .toList()))
            .toList();

        return outputLimiter.limitList(summaries).items();
    }
}

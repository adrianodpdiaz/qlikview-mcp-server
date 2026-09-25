package com.qlikview.mcp.tools;

import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Returns one sheet object's type and, for chart-type objects, its dimensions and expressions -
 * the fields it groups by and the expressions it calculates - read from a running QlikView
 * Desktop instance. Non-chart objects (current-selections boxes, search objects, and so on) are
 * returned with their type only; QlikView does not use the dimension/expression structure for
 * those. Requires QlikView Desktop installed, licensed, and running, with the document open or
 * reachable, at the moment the tool is called.
 */
@Component
@RequiredArgsConstructor
public class GetObjectTool {

    private final DocumentPathGuard pathGuard;
    private final QlikViewGateway gateway;

    /**
     * One sheet object: its id, QlikView's own numeric object-type code, and - for chart-type
     * objects only - its dimensions (resolved to real field names) and expressions.
     */
    public record ObjectSummary(String objectId, String type, List<String> dimensions, List<String> expressions) { }

    @McpTool(
        name = "get_object",
        description = "Get one sheet object's type, and for chart-type objects, its dimensions (field names) "
            + "and expressions, from a running QlikView Desktop instance",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public ObjectSummary getObject(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "Object id, as returned by get_sheets, e.g. Document\\CH03", required = true) String objectId) {
        Path resolved = pathGuard.resolve(document);
        QlikViewGateway.ObjectDetail detail = gateway.getObject(resolved, objectId);
        return new ObjectSummary(detail.objectId(), detail.objectType(),
            Arrays.asList(detail.dimensions()), Arrays.asList(detail.expressions()));
    }
}

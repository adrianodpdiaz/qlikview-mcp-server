package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ChartReader;
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
 * Returns one sheet object's type and, for chart-type objects, its dimensions and expressions -
 * the fields it groups by and the expressions it calculates. Non-chart objects (current-selections
 * boxes, search objects, and so on) are returned with their type only; QlikView does not use the
 * dimension/expression structure for those.
 */
@Component
@RequiredArgsConstructor
public class GetObjectTool {

    private static final String GRAPH_PROPERTIES_TYPE = "GraphProperties";

    private final DocumentPathGuard pathGuard;
    private final ProjectIndexReader projectIndexReader;
    private final ChartReader chartReader;

    /**
     * One sheet object: its id, type, and - for chart-type objects only - its dimensions
     * (resolved to real field names) and expressions.
     */
    public record ObjectSummary(String objectId, String type, List<String> dimensions, List<String> expressions) { }

    @McpTool(
        name = "get_object",
        description = "Get one sheet object's type, and for chart-type objects, its dimensions (field names) and expressions",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public ObjectSummary getObject(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "Object id, as returned by get_sheets, e.g. Document\\CH03", required = true) String objectId) {
        Path resolved = pathGuard.resolve(document);
        ProjectIndexReader.ObjectEntry entry = projectIndexReader.readObject(resolved, objectId)
            .orElseThrow(() -> new GuardException(
                "Object '" + objectId + "' was not found in this document's -prj export. "
                    + "Use get_sheets first to list valid object ids."));

        if (!entry.type().equals(GRAPH_PROPERTIES_TYPE)) {
            return new ObjectSummary(entry.objectId(), entry.type(), List.of(), List.of());
        }

        ChartReader.ChartContent chart = chartReader.read(entry.file());
        List<String> dimensions = chart.dimensions().stream().map(ChartReader.ChartDimension::fieldName).toList();
        List<String> expressions = chart.expressions().stream().map(ChartReader.ChartExpression::definition).toList();
        return new ObjectSummary(entry.objectId(), entry.type(), dimensions, expressions);
    }
}

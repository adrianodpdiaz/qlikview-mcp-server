package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ProjectIndexReader;
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
 * Returns a document's sheets and the objects placed on each, either read from its {@code -prj}
 * export (the default) or from a running QlikView Desktop instance ({@code live=true}). Use
 * {@code get_object} with an object's id to see a chart's dimensions and expressions.
 * <p>
 * The two sources identify a sheet and its objects differently and are not directly comparable:
 * the static (default) source gives the sheet's internal id (e.g. {@code Document\SH01}) and,
 * per object, its id and its type as an XML element name (e.g. {@code GraphProperties}); the live
 * source gives the sheet's caption (its displayed title, e.g. {@code "Main"}) and, per object, its
 * id and its type as QlikView's own numeric object-type code (e.g. {@code "11"}). Object ids from
 * either source are valid input to {@code get_object}.
 */
@Component
@RequiredArgsConstructor
public class GetSheetsTool {

    private final DocumentPathGuard pathGuard;
    private final ProjectIndexReader projectIndexReader;
    private final QlikViewGateway gateway;

    /**
     * One sheet and the objects placed on it. {@code sheetId} is populated (non-empty) by the
     * static source and empty for the live source; {@code caption} is the reverse. Each object's
     * {@code objectType} means something different depending on the source, and is empty for
     * static-source objects - see the class-level documentation.
     */
    public record SheetSummary(String sheetId, String caption, List<SheetObjectSummary> objects) { }

    /** One object on a sheet: its id and type. */
    public record SheetObjectSummary(String objectId, String objectType) { }

    @McpTool(
        name = "get_sheets",
        description = "Get a QlikView document's sheets and the objects placed on each: by default, from the "
            + "-prj export; with live=true, from a running QlikView Desktop instance",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public List<SheetSummary> getSheets(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "Read sheets from a running QlikView Desktop instance instead of the "
                + "-prj export (default false)", required = false) Boolean live) {
        Path resolved = pathGuard.resolve(document);
        return Boolean.TRUE.equals(live) ? getLiveSheets(resolved) : getStaticSheets(resolved);
    }

    private List<SheetSummary> getStaticSheets(Path resolved) {
        List<ProjectIndexReader.SheetEntry> sheets = projectIndexReader.readSheets(resolved)
            .orElseThrow(() -> new GuardException(
                "No -prj export found for this document. Create a folder named '"
                    + resolved.getFileName() + "-prj' next to it and save the document in "
                    + "QlikView Desktop, then try again."));

        return sheets.stream()
            .map(s -> new SheetSummary(s.sheetId(), "",
                s.objectIds().stream().map(id -> new SheetObjectSummary(id, "")).toList()))
            .toList();
    }

    private List<SheetSummary> getLiveSheets(Path resolved) {
        QlikViewGateway.SheetInfo[] sheets = gateway.getSheets(resolved);
        return Arrays.stream(sheets)
            .map(s -> new SheetSummary("", s.caption(),
                Arrays.stream(s.objects())
                    .map(o -> new SheetObjectSummary(o.objectId(), o.objectType()))
                    .toList()))
            .toList();
    }
}

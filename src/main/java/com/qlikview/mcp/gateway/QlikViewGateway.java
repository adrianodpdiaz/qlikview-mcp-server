package com.qlikview.mcp.gateway;

import java.nio.file.Path;

/**
 * Read-only bridge to a running QlikView Desktop instance. This only ever talks to a QlikView
 * Desktop instance on the same machine as the server process, so it cannot be centralised.
 * <p>
 * Implementations must never expose write, reload or macro-execution capabilities (by design),
 * regardless of what the underlying automation surface supports.
 */
public interface QlikViewGateway {

    /**
     * Full load script for the document, exactly as QlikView returns it (unredacted — callers
     * are responsible for redaction before this leaves the process).
     */
    String getScript(Path document);

    /**
     * Name/raw-value pairs for every variable currently defined in the document.
     */
    VariableDescription[] getVariables(Path document);

    /**
     * Tables and fields making up the document's associative data model.
     */
    DataModel getDataModel(Path document);

    /**
     * Evaluates a QlikView expression against the document's current selection state.
     */
    String evaluate(Path document, String expression);

    /**
     * One variable's name and current raw value, and whether it is one of QlikView's own
     * built-in/system variables rather than one the document's script defines.
     */
    record VariableDescription(String name, String rawValue, boolean isSystem) { }

    /**
     * A document's full associative data model: every table and every field.
     */
    record DataModel(TableInfo[] tables, FieldInfo[] fields) { }

    /**
     * One table in the data model, identified by name.
     */
    record TableInfo(String name) { }

    /**
     * One field in the data model: its name, distinct-value count, whether it is a QlikView
     * system field, whether its values are numeric, and which table(s) it belongs to.
     */
    record FieldInfo(String name, long cardinal, boolean isSystem, boolean isNumeric, String[] srcTables) { }

}

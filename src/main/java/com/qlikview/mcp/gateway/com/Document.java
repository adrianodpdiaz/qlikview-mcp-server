package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;

/**
 * A QlikView document, as returned by {@link QlikView#activeDocument()} or
 * {@link QlikView#openDoc}. Mirrors {@code $doc} in {@code qlikview-worker.ps1}.
 */
public interface Document {

    @ComMethod
    DocumentProperties getProperties();

    @ComMethod
    VariableDescriptions getVariableDescriptions();

    @ComMethod
    int getTableCount();

    @ComMethod
    String getTableName(int index);

    @ComMethod
    FieldDescriptions getFieldDescriptions();

    /**
     * A native array of sheets (a {@code VT_ARRAY} of {@code VT_DISPATCH}), unpacked via
     * {@link ComArrays#toDispatchArray} - see {@link FieldDescription#getSrcTables()} for why
     * this is declared {@code Object} rather than an array/collection type.
     */
    @ComMethod
    Object getSheetsAll();

    @ComMethod
    String evaluate(String expression);

}

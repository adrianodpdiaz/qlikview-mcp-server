package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;

/**
 * One sheet, as returned within {@link Document#getSheetsAll()}.
 */
public interface Sheet {

    @ComMethod
    SheetProperties getProperties();

    /**
     * A native array of sheet objects (a {@code VT_ARRAY} of {@code VT_DISPATCH}), unpacked via
     * {@link ComArrays#toDispatchArray} - see {@link FieldDescription#getSrcTables()} for why
     * this is declared {@code Object} rather than an array/collection type.
     */
    @ComMethod
    Object getSheetObjects();
}

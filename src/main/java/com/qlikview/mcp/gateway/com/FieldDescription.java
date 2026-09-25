package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * One item of {@link FieldDescriptions}. Mirrors {@code $fd.Name}/{@code .Cardinal}/
 * {@code .IsSystem}/{@code .IsNumeric}/{@code .SrcTables} in {@code qlikview-worker.ps1}'s
 * {@code getDataModel} case.
 * <p>
 * {@code SrcTables} is a native array (a {@code VT_ARRAY} VARIANT, unlike the {@code Count}/
 * {@code Item()}-style collections elsewhere in this package), so it is declared {@code Object}
 * here and unpacked via {@link ComArrays#toStringArray} rather than through JNA's normal
 * declared-return-type conversion, which does not turn a SAFEARRAY into a Java array on its own.
 */
public interface FieldDescription {

    @ComProperty
    String getName();

    @ComProperty
    long getCardinal();

    @ComProperty
    boolean getIsSystem();

    @ComProperty
    boolean getIsNumeric();

    @ComProperty
    Object getSrcTables();
}

package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;

/**
 * One object placed on a {@link Sheet}. Mirrors {@code $obj} in {@code qlikview-worker.ps1}'s
 * {@code getSheets}/{@code getObject} cases.
 */
public interface SheetObject {

    @ComMethod
    String getObjectId();

    /**
     * QlikView's own numeric object-type code (e.g. {@code 11} for a chart). Declared
     * {@code int} rather than an enum, matching the PowerShell script's plain-value handling
     * (it just interpolates the number into a string).
     */
    @ComMethod
    int getObjectType();

    @ComMethod
    ChartProperties getProperties();
}

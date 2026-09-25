package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;

/**
 * One object placed on a {@link Sheet}.
 */
public interface SheetObject {

    @ComMethod
    String getObjectId();

    /**
     * QlikView's own numeric object-type code (e.g. {@code 11} for a chart). Declared
     * {@code int} rather than an enum; callers format it as a string themselves.
     */
    @ComMethod
    int getObjectType();

    @ComMethod
    ChartProperties getProperties();
}

package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * One item of {@link ChartDimensions}.
 */
public interface ChartDimension {

    @ComProperty
    PseudoDef getPseudoDef();
}

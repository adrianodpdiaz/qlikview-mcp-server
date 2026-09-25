package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * One item of {@link ChartDimensions}. Mirrors {@code $dims.Item($i).PseudoDef} in
 * {@code qlikview-worker.ps1}'s {@code getObject} case.
 */
public interface ChartDimension {

    @ComProperty
    PseudoDef getPseudoDef();
}

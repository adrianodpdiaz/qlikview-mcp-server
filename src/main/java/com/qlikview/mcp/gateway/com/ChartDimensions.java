package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;
import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link ChartProperties#getDimensions()}. A QlikView collection object - indexed
 * access via {@code Item(i)} up to {@code Count}, mirroring {@code qlikview-worker.ps1}'s
 * {@code getObject} case.
 */
public interface ChartDimensions {

    @ComProperty
    int getCount();

    @ComMethod
    ChartDimension item(int index);
}

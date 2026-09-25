package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link SheetObject#getProperties()} for a chart-type object. Accessing either
 * property on a non-chart object throws via COM - callers must catch and treat that as "no
 * dimensions/expressions" rather than a real failure.
 */
public interface ChartProperties {

    @ComProperty
    ChartDimensions getDimensions();

    @ComProperty
    ChartExpressions getExpressions();
}

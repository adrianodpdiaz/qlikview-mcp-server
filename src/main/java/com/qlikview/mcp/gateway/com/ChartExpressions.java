package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;
import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link ChartProperties#getExpressions()}. A QlikView collection object - indexed
 * access via {@code Item(i)} up to {@code Count}.
 */
public interface ChartExpressions {

    @ComProperty
    int getCount();

    @ComMethod
    MainExpressionData item(int index);
}

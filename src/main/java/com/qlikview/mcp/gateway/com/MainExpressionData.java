package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;

/**
 * One item of {@link ChartExpressions} - itself a further collection accessed via
 * {@code .Item(0)}.
 */
public interface MainExpressionData {

    @ComMethod
    ExpressionDataWrapper item(int index);
}

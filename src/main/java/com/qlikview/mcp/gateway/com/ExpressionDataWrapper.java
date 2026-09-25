package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link MainExpressionData#item(int)}.
 */
public interface ExpressionDataWrapper {

    @ComProperty
    ExpressionDataHolder getData();
}

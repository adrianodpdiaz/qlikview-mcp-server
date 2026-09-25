package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link ExpressionDataWrapper#getData()}.
 */
public interface ExpressionDataHolder {

    @ComProperty
    ExpressionData getExpressionData();
}

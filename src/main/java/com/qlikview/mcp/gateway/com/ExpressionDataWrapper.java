package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link MainExpressionData#item(int)}. Mirrors {@code .Item(0).Data} in
 * {@code qlikview-worker.ps1}'s {@code getObject} case.
 */
public interface ExpressionDataWrapper {

    @ComProperty
    ExpressionDataHolder getData();
}

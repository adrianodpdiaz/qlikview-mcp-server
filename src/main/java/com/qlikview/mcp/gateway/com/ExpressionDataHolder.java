package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link ExpressionDataWrapper#getData()}. Mirrors {@code .Data.ExpressionData}
 * in {@code qlikview-worker.ps1}'s {@code getObject} case.
 */
public interface ExpressionDataHolder {

    @ComProperty
    ExpressionData getExpressionData();
}

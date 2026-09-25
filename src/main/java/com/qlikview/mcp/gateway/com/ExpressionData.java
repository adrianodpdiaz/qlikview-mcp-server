package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link ExpressionDataHolder#getExpressionData()}. Mirrors
 * {@code .ExpressionData.Definition} in {@code qlikview-worker.ps1}'s {@code getObject} case.
 */
public interface ExpressionData {

    @ComProperty
    Definition getDefinition();
}

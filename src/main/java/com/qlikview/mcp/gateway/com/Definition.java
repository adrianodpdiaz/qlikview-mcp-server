package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link ExpressionData#getDefinition()}. Mirrors {@code .Definition.v} in
 * {@code qlikview-worker.ps1}'s {@code getObject} case - {@code v} is the expression's actual
 * definition text (e.g. {@code Sum(SalesAmount)}).
 */
public interface Definition {

    @ComProperty(name = "v")
    String getV();
}

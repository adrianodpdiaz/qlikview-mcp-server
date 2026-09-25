package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link Sheet#getProperties()}. Mirrors {@code $sheet.GetProperties().Name} in
 * {@code qlikview-worker.ps1}'s {@code getSheets} case.
 */
public interface SheetProperties {

    @ComProperty
    String getName();
}

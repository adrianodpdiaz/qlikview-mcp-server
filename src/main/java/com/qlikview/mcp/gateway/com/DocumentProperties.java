package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link Document#getProperties()}. Mirrors
 * {@code $doc.GetProperties().Script}/{@code .FileName} in {@code qlikview-worker.ps1}.
 */
public interface DocumentProperties {

    @ComProperty
    String getScript();

    @ComProperty
    String getFileName();

}

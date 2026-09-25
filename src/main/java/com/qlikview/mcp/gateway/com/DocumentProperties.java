package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link Document#getProperties()}.
 */
public interface DocumentProperties {

    @ComProperty
    String getScript();

    @ComProperty
    String getFileName();

}

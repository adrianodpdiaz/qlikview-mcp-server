package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * One item of {@link VariableDescriptions}.
 */
public interface VariableDescription {

    @ComProperty
    String getName();

    @ComProperty
    String getRawValue();

    @ComProperty
    boolean getIsReserved();

}

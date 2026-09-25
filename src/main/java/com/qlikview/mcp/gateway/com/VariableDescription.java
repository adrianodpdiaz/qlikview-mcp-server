package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * One item of {@link VariableDescriptions}. Mirrors {@code $d.Name}/{@code .RawValue}/
 * {@code .IsReserved} in {@code qlikview-worker.ps1}'s {@code getVariables} case.
 */
public interface VariableDescription {

    @ComProperty
    String getName();

    @ComProperty
    String getRawValue();

    @ComProperty
    boolean getIsReserved();

}

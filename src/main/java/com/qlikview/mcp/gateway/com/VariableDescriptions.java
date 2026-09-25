package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;
import com.sun.jna.platform.win32.COM.util.annotation.ComProperty;

/**
 * Return type of {@link Document#getVariableDescriptions()}. A QlikView collection object -
 * indexed access via {@code Item(i)} up to {@code Count}, mirroring the {@code for} loop in
 * {@code qlikview-worker.ps1}'s {@code getVariables} case rather than any Java-side array/list
 * conversion (QlikView's own collections are not SAFEARRAYs).
 */
public interface VariableDescriptions {

    @ComProperty
    int getCount();

    @ComMethod
    VariableDescription item(int index);

}

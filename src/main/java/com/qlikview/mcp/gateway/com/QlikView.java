package com.qlikview.mcp.gateway.com;

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;
import com.sun.jna.platform.win32.COM.util.annotation.ComObject;

/**
 * The top-level {@code QlikTech.QlikView} automation object. Mirrors the calls
 * {@code qlikview-worker.ps1} made via {@code New-Object -ComObject QlikTech.QlikView}.
 * <p>
 * No dispatch id is declared on any method here (defaults to -1, "unknown"): JNA then resolves
 * each member by name via {@code IDispatch.GetIDsOfNames} at call time, the same late-bound
 * lookup PowerShell itself performs - QlikView's type library is not consulted for real DISPIDs.
 * {@code @ComInterface}/IID is not needed since no code here ever calls {@code QueryInterface}.
 */
@ComObject(progId = "QlikTech.QlikView")
public interface QlikView {

    @ComMethod
    Document activeDocument();

    @ComMethod
    Document openDoc(String fileName, String userName, String password, String serial);

}

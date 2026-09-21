<#
Test fixture: always returns a well-formed error response, so PowerShellQlikViewGateway's error-propagation path can be proven.
#>
[Console]::In.ReadToEnd() | Out-Null
[Console]::Out.Write('{"ok":false,"error":"simulated worker error"}')

<#
Test fixture: reads the JSON request and echoes back a canned success response,
so PowerShellQlikViewGateway's happy-path plumbing (write request, read response, parse JSON) can be proven without QlikView.
#>
$request = [Console]::In.ReadToEnd() | ConvertFrom-Json
[Console]::Out.Write('{"ok":true,"result":{"script":"fake script for ' + $request.operation + '"}}')

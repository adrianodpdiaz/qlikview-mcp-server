<#
Test fixture: returns a canned getSheets-shaped response, so
PowerShellQlikViewGateway.getSheets()'s JSON-to-record mapping can be proven
without needing a real QlikView instance.
#>
[Console]::In.ReadToEnd() | Out-Null
[Console]::Out.Write('{"ok":true,"result":{"sheets":[{"caption":"Main","objects":[{"objectId":"Document\\CS01","objectType":"7"},{"objectId":"Document\\CH03","objectType":"11"}]}]}}')

<#
Test fixture: returns a canned getObject-shaped response, so PowerShellQlikViewGateway.getObject()'s JSON-to-record 
mapping can be proven without needing a real QlikView instance.
#>
[Console]::In.ReadToEnd() | Out-Null
[Console]::Out.Write('{"ok":true,"result":{"objectId":"Document\\CH03","objectType":"11","dimensions":["CustomerName"],"expressions":["Sum(SalesAmount)"]}}')

<#
Test fixture: simulates a worker call that never returns, the way some real QlikView COM calls
do on certain failure paths, so PowerShellQlikViewGateway's timeout enforcement can be proven
without needing a real QlikView instance.
#>
[Console]::In.ReadToEnd() | Out-Null
Start-Sleep -Seconds 300

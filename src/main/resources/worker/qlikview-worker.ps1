<#
QlikView MCP gateway worker (one-shot process per call).

Reads a single JSON request from stdin:
  { "operation": "getScript" | "getVariables" | "getDataModel" | "getSheets" | "evaluate",
    "documentPath": "C:\...\document.qvw",
    "expression": "..." }   (evaluate only)

Writes a single JSON response to stdout:
  { "ok": true, "result": <operation-specific> }
  { "ok": false, "error": "message" }

No other output goes to stdout, by design. Diagnostic output, if any, must
go to stderr only, since the Java gateway treats stdout as the protocol
channel and would otherwise fail to parse the response.
#>

$ErrorActionPreference = 'Stop'

# Windows PowerShell's default console output encoding is the system codepage, not UTF-8, which
# corrupts any non-ASCII character (accented letters, currency symbols) written to stdout. The
# Java gateway reads this process's stdout as UTF-8, so the encodings must match.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Write-JsonResponse($obj) {
    $json = $obj | ConvertTo-Json -Depth 10 -Compress
    [Console]::Out.Write($json)
}

function Get-ActiveOrOpenDocument($qv, [string]$documentPath) {
    $doc = $qv.ActiveDocument()
    if ($doc -and ($doc.GetProperties().FileName -ieq $documentPath)) {
        return $doc
    }
    return $qv.OpenDoc($documentPath, "", "", "")
}

try {
    $requestJson = [Console]::In.ReadToEnd()
    $request = $requestJson | ConvertFrom-Json

    $qv = New-Object -ComObject QlikTech.QlikView
    $doc = Get-ActiveOrOpenDocument -qv $qv -documentPath $request.documentPath

    if ($null -eq $doc) {
        throw "Could not open or access document: $($request.documentPath)"
    }

    switch ($request.operation) {

        'getScript' {
            $script = $doc.GetProperties().Script
            Write-JsonResponse @{ ok = $true; result = @{ script = $script } }
        }

        'getVariables' {
            $descs = $doc.GetVariableDescriptions()
            $variables = @()
            for ($i = 0; $i -lt $descs.Count; $i++) {
                $d = $descs.Item($i)
                $variables += @{
                    name      = $d.Name
                    rawValue  = $d.RawValue
                    isSystem  = [bool]$d.IsReserved
                }
            }
            Write-JsonResponse @{ ok = $true; result = @{ variables = $variables } }
        }

        'getDataModel' {
            $tableCount = $doc.GetTableCount()
            $tables = @()
            for ($i = 0; $i -lt $tableCount; $i++) {
                $tables += @{ name = $doc.GetTableName($i) }
            }

            $fieldDescs = $doc.GetFieldDescriptions()
            $fields = @()
            for ($i = 0; $i -lt $fieldDescs.Count; $i++) {
                $fd = $fieldDescs.Item($i)
                $srcTables = @()
                if ($fd.SrcTables) { $srcTables = @($fd.SrcTables) }
                $fields += @{
                    name       = $fd.Name
                    cardinal   = [int64]$fd.Cardinal
                    isSystem   = [bool]$fd.IsSystem
                    isNumeric  = [bool]$fd.IsNumeric
                    srcTables  = $srcTables
                }
            }

            Write-JsonResponse @{ ok = $true; result = @{ tables = $tables; fields = $fields } }
        }

        'getSheets' {
            $sheets = @($doc.GetSheetsAll())
            $sheetResults = @()
            foreach ($sheet in $sheets) {
                $caption = $sheet.GetProperties().Name
                $objects = @()
                foreach ($obj in @($sheet.GetSheetObjects())) {
                    $objects += @{
                        objectId   = $obj.GetObjectId()
                        objectType = "$($obj.GetObjectType())"
                    }
                }
                $sheetResults += @{ caption = $caption; objects = $objects }
            }
            Write-JsonResponse @{ ok = $true; result = @{ sheets = $sheetResults } }
        }

        'evaluate' {
            $result = $doc.Evaluate($request.expression)
            Write-JsonResponse @{ ok = $true; result = @{ value = "$result" } }
        }

        default {
            throw "Unknown operation: $($request.operation)"
        }
    }
}
catch {
    Write-JsonResponse @{ ok = $false; error = $_.Exception.Message }
    exit 1
}

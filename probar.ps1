$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
& .\compilar.ps1
# Java escribe avisos en la salida de errores (por ej. reintentos del outbox): no son errores del script.
$ErrorActionPreference = 'Continue'
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp out trotamundos.PruebaOutbox
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp out trotamundos.PruebaFlujo
exit $LASTEXITCODE

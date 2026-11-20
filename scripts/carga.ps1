$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
& (Join-Path $PSScriptRoot 'compilar.ps1')
Set-Location (Split-Path $PSScriptRoot -Parent)
# Java escribe avisos en la salida de errores: no son errores del script.
$ErrorActionPreference = 'Continue'
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp out trotamundos.PruebaCarga
exit $LASTEXITCODE

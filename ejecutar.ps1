$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
& .\compilar.ps1
java "-Dstdout.encoding=UTF-8" -cp out trotamundos.App

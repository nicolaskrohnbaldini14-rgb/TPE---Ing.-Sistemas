$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path out) { Remove-Item out -Recurse -Force }
$fuentes = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -d out $fuentes
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Compilado en la carpeta out/"

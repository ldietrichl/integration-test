param([string]$Work = '')
& (Join-Path $PSScriptRoot 'start.ps1') -Profile compatible -Work $Work -Pull

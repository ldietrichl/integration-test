#requires -Version 5.1
<#
.SYNOPSIS
Preview or remove known generated files from an integration-test project.
.DESCRIPTION
Default: read-only preview. -Apply enables deletion; -WhatIf still prevents it.
Only fixed generated paths are eligible. Source, properties, credentials, stores,
fixture recovery and exact Ignite runtimes are preserved. No stand calls are made.
Stop Gradle/IDE test runs before applying. Reports require -IncludeReports.
.EXAMPLE
.\Clean-IntegrationTest.ps1 -ProjectRoot C:\Work\IdeaProjects\integration-test
.EXAMPLE
.\Clean-IntegrationTest.ps1 -ProjectRoot C:\Work\IdeaProjects\integration-test -Apply
#>
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ProjectRoot,
    [switch]$Apply,
    [switch]$IncludeReports,
    [switch]$IncludeProjectCache,
    # Installer-only guard: a file must match a verified backup before deletion.
    [string]$ExpectedSnapshot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-PlainAncestors {
    param([string]$Path)
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    while ($null -ne $item) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'A selected path or its ancestor is a reparse point. No linked directory is allowed.'
        }
        if ($item -is [IO.FileInfo]) { $item = $item.Directory }
        else { $item = $item.Parent }
    }
}

$resolved = Resolve-Path -LiteralPath $ProjectRoot -ErrorAction Stop
if ($resolved.Provider.Name -ne 'FileSystem') { throw 'ProjectRoot must be a filesystem directory.' }
$rootItem = Get-Item -LiteralPath $resolved.ProviderPath -Force
if (-not $rootItem.PSIsContainer) { throw 'ProjectRoot must be a directory.' }
$root = [IO.Path]::GetFullPath($rootItem.FullName).TrimEnd([char[]]'\/')
if ($root -eq [IO.Path]::GetPathRoot($root).TrimEnd([char[]]'\/')) {
    throw 'A drive/share root cannot be used as ProjectRoot.'
}
Assert-PlainAncestors $root
$hasBuild = (Test-Path -LiteralPath (Join-Path $root 'build.gradle.kts') -PathType Leaf) -or
    (Test-Path -LiteralPath (Join-Path $root 'build.gradle') -PathType Leaf)
$hasSettings = (Test-Path -LiteralPath (Join-Path $root 'settings.gradle.kts') -PathType Leaf) -or
    (Test-Path -LiteralPath (Join-Path $root 'settings.gradle') -PathType Leaf)
if (-not $hasBuild -or -not $hasSettings -or
    -not (Test-Path -LiteralPath (Join-Path $root 'src') -PathType Container) -or
    -not (Test-Path -LiteralPath (Join-Path $root 'gradlew.bat') -PathType Leaf)) {
    throw 'ProjectRoot does not contain the expected Gradle project, src and gradlew.bat.'
}
$rootPrefix = $root + [IO.Path]::DirectorySeparatorChar

function Assert-Contained {
    param([string]$Path)
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Cleanup target is outside the selected project.'
    }
}

function Test-ProtectedItem {
    param([IO.FileSystemInfo]$Item)
    if ($Item -is [IO.DirectoryInfo]) {
        return $Item.Name -match '^(?i:src|\.git|\.idea|\.vscode|gradle|tools|regression-fixtures|runtime|corporate-runtimes|corporate-ignite-runtimes|corporate-ignite-libraries|libs-offline|lib)$'
    }
    # A generated resources copy may contain a store/secret or recovery metadata.
    # Preserve the entire candidate tree in that case; do not guess redundancy.
    return $Item.Name -match '^(?i:secure.*\.properties|gradle.*local.*\.properties|\.env(?:\..*)?|ownership\.json|fixture-manifest\.json|.*lease.*\.json|client-libraries\.lock\.json|dependency-resolution\.json)$' -or
        $Item.Extension -match '^(?i:\.p12|\.pfx|\.jks|\.keystore|\.truststore|\.pem|\.key|\.crt|\.cer|\.der|\.rar)$'
}

function Read-CandidateTree {
    param([string]$RelativePath, [string]$Category)
    $path = [IO.Path]::GetFullPath((Join-Path $root $RelativePath))
    Assert-Contained $path
    if (-not (Test-Path -LiteralPath $path)) {
        return [pscustomobject]@{ Path = $RelativePath; Category = $Category; State = 'Absent'; Files = 0; Bytes = [long]0; Items = @(); Directories = @() }
    }
    Assert-PlainAncestors $path
    $top = Get-Item -LiteralPath $path -Force
    if (-not $top.PSIsContainer) { throw "Expected a generated directory: $RelativePath" }
    $stack = New-Object 'System.Collections.Generic.Stack[System.IO.DirectoryInfo]'
    $files = New-Object 'System.Collections.Generic.List[System.IO.FileInfo]'
    $dirs = New-Object 'System.Collections.Generic.List[System.IO.DirectoryInfo]'
    $stack.Push($top)
    $protected = $false
    [long]$bytes = 0
    while ($stack.Count -gt 0) {
        $dir = $stack.Pop()
        Assert-Contained $dir.FullName
        Assert-PlainAncestors $dir.FullName
        $dirs.Add($dir)
        if (Test-ProtectedItem $dir) { $protected = $true }
        # Do not use -Recurse: inspect each link before considering its contents.
        foreach ($child in @(Get-ChildItem -LiteralPath $dir.FullName -Force)) {
            Assert-Contained $child.FullName
            if (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Reparse point found inside $RelativePath. Cleanup refused."
            }
            if (Test-ProtectedItem $child) { $protected = $true }
            if ($child.PSIsContainer) { $stack.Push($child) }
            else { $files.Add($child); $bytes += $child.Length }
        }
    }
    $state = 'Eligible'
    if ($protected) { $state = 'PreservedProtectedContent' }
    [pscustomobject]@{ Path = $RelativePath; Category = $Category; State = $state; Files = $files.Count; Bytes = $bytes; Items = $files.ToArray(); Directories = $dirs.ToArray() }
}

$targets = @(
    'build/classes', 'build/generated', 'build/tmp', 'build/resources',
    'build/libs', 'build/distributions', 'build/scripts'
)
$reportTargets = @(
    'build/reports', 'build/test-results', 'build/allure-results', 'build/allure-report',
    'build/logs', 'build/regression-results',
    'allure-results', 'allure-report', 'regression-results'
)
# TestOps lock files live beside each environment directory. Keep those lock files.
foreach ($environment in @('dev', 'ift', 'ift-dm', 'lt', 'local')) {
    $reportTargets += ('build/testops-results/' + $environment)
    $reportTargets += ('testops-results/' + $environment)
}
$plan = New-Object 'System.Collections.Generic.List[object]'
# Complete preflight before deleting anything. A link or unreadable path aborts the run.
foreach ($path in $targets) { $plan.Add((Read-CandidateTree $path 'Generated')) }
if ($IncludeReports) {
    foreach ($path in $reportTargets) { $plan.Add((Read-CandidateTree $path 'Reports')) }
}
if ($IncludeProjectCache) { $plan.Add((Read-CandidateTree '.gradle' 'ProjectCache')) }

$visible = @($plan | Where-Object { $_.State -ne 'Absent' })
if ($visible.Count -eq 0) { Write-Host 'No eligible generated directories were found.'; return }
$visible | Select-Object Path, Category, State, Files, @{Name='MiB'; Expression={[math]::Round($_.Bytes / 1MB, 3)}} | Format-Table -AutoSize | Out-Host
$eligible = @($visible | Where-Object State -eq 'Eligible')
$expected = $null
if (-not [string]::IsNullOrWhiteSpace($ExpectedSnapshot)) {
    Assert-PlainAncestors $ExpectedSnapshot
    $snapshot = Get-Content -LiteralPath $ExpectedSnapshot -Raw | ConvertFrom-Json
    $expected = @{}
    foreach ($record in $snapshot.Files) {
        if ($expected.ContainsKey($record.Path)) { throw 'Duplicate path in expected cleanup snapshot.' }
        $expected[$record.Path] = $record.Sha256
    }
}
function Assert-BackedUpFile {
    param([IO.FileInfo]$File)
    if ($null -eq $expected) { return }
    $name = $File.FullName.Substring($root.Length + 1).Replace('\', '/')
    if (-not $expected.ContainsKey($name) -or
        (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected[$name]) {
        throw 'A cleanup file is new or changed since backup. No unbacked file will be deleted.'
    }
}
# Guard the complete initial plan, then each refreshed tree and each actual deletion.
if ($null -ne $expected) {
    $present = @{}
    foreach ($entry in $visible) {
        foreach ($file in $entry.Items) {
            $name = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
            $present[$name] = $true
            Assert-BackedUpFile $file
        }
    }
    if ($present.Count -ne $expected.Count) { throw 'Expected cleanup files are missing or the selection changed since backup.' }
}
foreach ($entry in $eligible) { foreach ($file in $entry.Items) { Assert-BackedUpFile $file } }
[long]$total = 0
foreach ($entry in $eligible) { $total += $entry.Bytes }
Write-Host ('Eligible: {0} directories, {1:N3} MiB. Fixtures, runtimes, source and credentials stay protected.' -f $eligible.Count, ($total / 1MB))
if (-not $Apply) {
    Write-Host 'PREVIEW ONLY: no files changed. Add -Apply to remove the listed Eligible directories.'
    return
}

$removed = 0
foreach ($entry in $eligible) {
    if (-not $PSCmdlet.ShouldProcess($entry.Path, 'Delete this generated directory and its listed files')) { continue }
    # Check again immediately before mutation. Do not delete a tree that acquired protected data.
    $fresh = Read-CandidateTree $entry.Path $entry.Category
    if ($fresh.State -eq 'Absent') { continue }
    if ($fresh.State -ne 'Eligible') { throw "Protected content appeared in $($entry.Path); stopped." }
    foreach ($file in $fresh.Items) { Assert-BackedUpFile $file }
    foreach ($file in $fresh.Items) {
        Assert-Contained $file.FullName
        Assert-PlainAncestors $file.FullName
        $now = Get-Item -LiteralPath $file.FullName -Force
        if ($now.PSIsContainer -or (Test-ProtectedItem $now)) { throw 'A cleanup file changed into a protected item; stopped.' }
        Assert-BackedUpFile $now
        Remove-Item -LiteralPath $now.FullName -Force -Confirm:$false -ErrorAction Stop
    }
    foreach ($dir in @($fresh.Directories | Sort-Object { $_.FullName.Length } -Descending)) {
        Assert-Contained $dir.FullName
        Assert-PlainAncestors $dir.FullName
        # Non-recursive delete refuses a directory that gained new files during cleanup.
        [IO.Directory]::Delete($dir.FullName, $false)
    }
    $removed++
    Write-Host ('Removed: ' + $entry.Path)
}
Write-Host ('Finished. Removed directories: ' + $removed)

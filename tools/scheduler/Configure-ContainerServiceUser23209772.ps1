[CmdletBinding()]
param(
    [string]$Project = 'C:\Work\IdeaProjects\integration-test',
    [string]$Kubeconfig = 'C:\Work\private\openshift-service-access\admin-rbac\kubeconfig-23209772',
    [string]$GeneratedProperties = 'C:\Work\private\openshift-service-access\dev-ci07963639-dev-terra000003-abtm-back\container-service-client\container-service-user-23209772.generated.properties',
    [string]$OcExecutable = 'C:\Work\utils\oc.exe',
    [string]$Namespace = 'ci07963639-dev-terra000003-abtm-back',
    [string]$ExpectedUser = '23209772'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

function Require-File([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Name is missing: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Invoke-OcText([string[]]$Arguments) {
    $result = & $script:Oc @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'OpenShift command failed; credential-bearing output is suppressed.'
    }
    return (($result | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

function Test-Permission([string]$Verb, [string]$Resource) {
    $answer = Invoke-OcText @('--kubeconfig', $script:KubeconfigPath, '--context', $script:Context,
        '-n', $script:NamespaceValue, 'auth', 'can-i', $Verb, $Resource)
    if ($answer -ne 'yes') {
        throw "Required permission is missing: $Verb $Resource"
    }
    Write-Host ("Permission {0} {1}=yes" -f $Verb, $Resource)
}

function Set-Property([System.Collections.Generic.List[string]]$Lines, [string]$Key, [string]$Value) {
    $replacement = "$Key=$Value"
    $found = $false
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        if ($Lines[$index] -match ('^\s*' + [regex]::Escape($Key) + '\s*=')) {
            if (-not $found) {
                $Lines[$index] = $replacement
                $found = $true
            } else {
                $Lines.RemoveAt($index)
                $index--
            }
        }
    }
    if (-not $found) { $Lines.Add($replacement) }
}

$script:Oc = Require-File $OcExecutable 'oc executable'
$script:KubeconfigPath = Require-File $Kubeconfig 'User kubeconfig'
$projectPath = (Resolve-Path -LiteralPath $Project).Path
$script:NamespaceValue = $Namespace

$script:Context = Invoke-OcText @('--kubeconfig', $script:KubeconfigPath, 'config', 'current-context')
if ([string]::IsNullOrWhiteSpace($script:Context)) { throw 'Kubeconfig current-context is empty.' }

$selectedUser = Invoke-OcText @('--kubeconfig', $script:KubeconfigPath, '--context', $script:Context,
    'config', 'view', '--minify', '-o', 'jsonpath={.contexts[0].context.user}')
if (-not ($selectedUser -eq $ExpectedUser -or $selectedUser.StartsWith($ExpectedUser + '/') -or
        $selectedUser.EndsWith('/' + $ExpectedUser))) {
    throw 'The active kubeconfig context does not belong to the approved user.'
}

$token = Invoke-OcText @('--kubeconfig', $script:KubeconfigPath, '--context', $script:Context,
    'config', 'view', '--minify', '--raw', '-o', 'jsonpath={.users[0].user.token}')
if ([string]::IsNullOrWhiteSpace($token)) { throw 'TokenPresent=false. Refresh the user kubeconfig before installation.' }
$token = $null

$whoAmI = Invoke-OcText @('--kubeconfig', $script:KubeconfigPath, '--context', $script:Context, 'whoami')
if ($whoAmI -ne $ExpectedUser) { throw 'The API authenticated a different OpenShift user.' }
Write-Host 'UserIdentityMatched=true'
Write-Host 'TokenPresent=true'

Test-Permission 'get' 'pods'
Test-Permission 'list' 'pods'
Test-Permission 'delete' 'pods'
Test-Permission 'get' 'pods/log'
Test-Permission 'create' 'pods/portforward'
Test-Permission 'get' 'configmaps'
Test-Permission 'patch' 'configmaps'
Test-Permission 'get' 'deployments.apps'

$generatedDirectory = Split-Path -Parent $GeneratedProperties
New-Item -ItemType Directory -Path $generatedDirectory -Force | Out-Null
$normalizedKubeconfig = $script:KubeconfigPath.Replace('\', '/')
$generated = @(
    '# Generated locally for DEV ContainerService. Contains paths/context only, never a token.'
    "SECURE_OPENSHIFT_DEV_KUBECONFIG_PATH=$normalizedKubeconfig"
    "SECURE_OPENSHIFT_DEV_CONTEXT=$($script:Context)"
)
[System.IO.File]::WriteAllLines($GeneratedProperties, $generated, [System.Text.UTF8Encoding]::new($false))

$secureOverride = Join-Path $projectPath 'secure.local.override.properties'
$lines = [System.Collections.Generic.List[string]]::new()
if (Test-Path -LiteralPath $secureOverride -PathType Leaf) {
    Get-Content -LiteralPath $secureOverride | ForEach-Object { $lines.Add($_) }
    $backup = "$secureOverride.container-service-user-23209772.$(Get-Date -Format 'yyyyMMdd-HHmmss').bak"
    Copy-Item -LiteralPath $secureOverride -Destination $backup
} else {
    $lines.Add('# Local credentials and private paths. Never commit this file.')
}
Set-Property $lines 'SECURE_OPENSHIFT_DEV_KUBECONFIG_PATH' $normalizedKubeconfig
Set-Property $lines 'SECURE_OPENSHIFT_DEV_CONTEXT' $script:Context
[System.IO.File]::WriteAllLines($secureOverride, $lines, [System.Text.UTF8Encoding]::new($false))

Write-Host 'ContainerServiceAuthMode=kubeconfig'
Write-Host 'ContainerServiceExpectedUser=23209772'
Write-Host 'ContainerServicePortForwardRequired=true'
Write-Host 'GeneratedPropertiesUpdated=true'
Write-Host 'SecureOverrideUpdated=true'
Write-Host 'No token or kubeconfig content was copied into the project.'

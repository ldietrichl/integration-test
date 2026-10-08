#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Kubeconfig,
    [Parameter(Mandatory = $true)][string]$Context,
    [string]$TokenFile,
    [string]$OcExecutable = 'C:/Work/utils/oc.exe',
    [string]$ApiServer = 'https://api.dev-terra000003-ids.ocp.delta.sbrf.ru:6443',
    [string]$ExpectedUser = '23209772'
)

$ErrorActionPreference = 'Stop'
$resolved = (Resolve-Path -LiteralPath $Kubeconfig).ProviderPath
if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
    throw 'Specify one existing kubeconfig file, not a directory or a merged list.'
}
if (-not (Test-Path -LiteralPath $OcExecutable -PathType Leaf)) {
    throw 'Specify the exact approved oc.exe path with -OcExecutable.'
}
$oc = (Resolve-Path -LiteralPath $OcExecutable).ProviderPath
$origin = [Uri]$ApiServer
if ($origin.Scheme -cne 'https' -or -not $origin.Host -or $origin.UserInfo -or
    $origin.Query -or $origin.Fragment -or $origin.AbsolutePath -ne '/') {
    throw 'ApiServer must be the approved HTTPS API origin without credentials.'
}
if ([string]::IsNullOrWhiteSpace($ExpectedUser)) {
    throw 'ExpectedUser is required; do not refresh a profile for an unknown identity.'
}

function Invoke-PrivateOc {
    param([string]$Phase, [string[]]$OcArguments)
    # Native errors can contain credential material. Never echo commands or their raw errors.
    $ErrorActionPreference = 'Continue'
    $output = @(& $oc @OcArguments 2>$null)
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        throw ($Phase + ' failed (oc exit ' + $code + '). Details are suppressed. Check access on the VARM.')
    }
    return ($output -join [Environment]::NewLine)
}

# config view without --raw masks credentials. Only select the context/user/cluster metadata.
$common = @('--kubeconfig', $resolved, '--context', $Context, '--request-timeout=15s')
$metadataJson = Invoke-PrivateOc -Phase 'Read selected kubeconfig metadata' -OcArguments (
    $common + @('config', 'view', '--minify', '-o', 'json'))
try { $metadata = $metadataJson | ConvertFrom-Json }
catch { throw 'Cannot parse selected kubeconfig metadata; no credentials were changed.' }
if (@($metadata.contexts).Count -ne 1 -or @($metadata.clusters).Count -ne 1 -or
    @($metadata.users).Count -ne 1) {
    throw 'The selected context must resolve to exactly one cluster and user.'
}
$selected = @($metadata.contexts)[0]
$cluster = @($metadata.clusters)[0]
$identity = @($metadata.users)[0]
if ($selected.name -cne $Context -or $selected.context.cluster -cne $cluster.name -or
    $selected.context.user -cne $identity.name) {
    throw 'The selected context/user/cluster mapping is inconsistent.'
}
if (([string]$cluster.cluster.server).TrimEnd('/') -cne $ApiServer.TrimEnd('/')) {
    throw 'The selected context does not belong to the approved API server.'
}
if ($cluster.cluster.'insecure-skip-tls-verify' -eq $true) {
    throw 'TLS verification is disabled in this profile. Restore the approved CA; do not bypass TLS.'
}
if ([string]::IsNullOrWhiteSpace([string]$cluster.cluster.'certificate-authority-data') -and
    [string]::IsNullOrWhiteSpace([string]$cluster.cluster.'certificate-authority')) {
    throw 'The explicit approved cluster CA is missing; no credentials were changed.'
}
foreach ($unsupported in @('exec', 'auth-provider', 'username', 'password',
        'client-certificate', 'client-certificate-data', 'client-key', 'client-key-data')) {
    if ($null -ne $identity.user.$unsupported) {
        throw 'This helper supports an existing bearer-token profile only; use the approved flow for other authentication types.'
    }
}
if ([string]::IsNullOrWhiteSpace([string]$identity.name)) {
    throw 'The selected kubeconfig user entry has no name.'
}

Write-Host ('Selected kubeconfig: ' + $resolved)
Write-Host ('Selected context: ' + $Context)
Write-Host ('Expected API: ' + $ApiServer)
Write-Host ('Expected identity: ' + $ExpectedUser)
Write-Host 'CA, cluster, context and namespace entries will not be replaced.'
Write-Host 'The token is passed to oc as a process argument. Do not use command/process tracing.'
if ([string]::IsNullOrWhiteSpace($TokenFile)) {
    $secureToken = Read-Host 'Paste a fresh OpenShift token (hidden; do not send it to chat)' -AsSecureString
} else {
    $tokenPath = (Resolve-Path -LiteralPath $TokenFile).ProviderPath
    if (-not (Test-Path -LiteralPath $tokenPath -PathType Leaf)) {
        throw 'TokenFile must be an existing private text file.'
    }
    # Do not print, attach, copy or log this file. It is deliberately outside the shared patch.
    $secureToken = ConvertTo-SecureString -String ([IO.File]::ReadAllText($tokenPath).Trim()) -AsPlainText -Force
}
$pointer = [IntPtr]::Zero
$plainToken = $null
try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
    $plainToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()
    if ([string]::IsNullOrWhiteSpace($plainToken) -or $plainToken -match '\s') {
        throw 'Paste only the token, not an oc login command.'
    }
    # Probe before changing the stored credential. Existing CA and context are used.
    $actualUser = (Invoke-PrivateOc -Phase 'Authenticate fresh token' -OcArguments (
        $common + @('--token', $plainToken, 'whoami'))).Trim()
    if ($actualUser -cne $ExpectedUser) {
        throw 'The fresh token belongs to a different identity. No credentials were changed.'
    }
    # Merge only the selected user credential. Do not run login or set-cluster here.
    $null = Invoke-PrivateOc -Phase 'Update selected token entry' -OcArguments (
        @('--kubeconfig', $resolved, 'config', 'set-credentials', [string]$identity.name,
          '--token', $plainToken))
    Write-Host 'Token updated in the selected kubeconfig. Cluster CA and context entries were retained.'
    Write-Host 'The kubeconfig is a secret file: do not add it to the project, patch or Allure.'
} finally {
    $plainToken = $null
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    if ($null -ne $secureToken) { $secureToken.Dispose() }
}

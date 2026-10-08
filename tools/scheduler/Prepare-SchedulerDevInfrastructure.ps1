#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Project = 'C:\Work\IdeaProjects\integration-test',
    [string]$BootstrapKubeconfig = "$env:USERPROFILE\.kube\config",
    [string]$BootstrapContext = '',
    [string[]]$ApprovedCaSha256 = @(),
    [string]$OcExecutable = 'C:/Work/utils/oc.exe',
    [string]$ApiServer = 'https://api.dev-terra000003-ids.ocp.delta.sbrf.ru:6443',
    [string]$IngressHost = 'ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru',
    [int]$IngressPort = 443,
    [string]$Namespace = 'ci07963639-dev-terra000003-abtm-back',
    [string]$Context = 'container-service-service-account',
    [string]$ServiceAccount = 'argocd',
    [string]$ServiceAccountSecret = 'argocd-token-4nxtg'
)

$ErrorActionPreference = 'Stop'

function Resolve-ExistingFile {
    param([string]$Path, [string]$Kind)
    if ([string]::IsNullOrWhiteSpace($Path)) { throw "$Kind path is empty" }
    $candidate = [IO.Path]::GetFullPath($Path.Replace('/', '\'))
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "$Kind file does not exist: $candidate"
    }
    return $candidate
}

function Resolve-OutputFile {
    param([string]$Path, [string]$Kind)
    if ([string]::IsNullOrWhiteSpace($Path)) { throw "$Kind path is empty" }
    $candidate = [IO.Path]::GetFullPath($Path.Replace('/', '\'))
    $parent = Split-Path -Parent $candidate
    if ([string]::IsNullOrWhiteSpace($parent)) { throw "$Kind must have a parent directory" }
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    return $candidate
}

function Read-Properties {
    param([string]$Path)
    $values = [ordered]@{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { return }
        $values[$line.Substring(0, $separator).Trim()] = $line.Substring($separator + 1)
    }
    return $values
}

function Require-Property {
    param($Properties, [string]$Name)
    $value = [string]$Properties[$Name]
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Contains('${') -or $value.StartsWith('<SET_ME_')) {
        throw "Required secure property is missing or unresolved: $Name"
    }
    return $value
}

function Find-Tool {
    param([string]$Name)
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaCandidate = Join-Path $env:JAVA_HOME ('bin\' + $Name + '.exe')
        if (Test-Path -LiteralPath $javaCandidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $javaCandidate).ProviderPath
        }
    }
    $command = Get-Command ($Name + '.exe') -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) { $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1 }
    if ($null -eq $command) { throw "$Name is required but was not found in JAVA_HOME or PATH" }
    return $command.Source
}

function Invoke-PrivateOc {
    param([string]$Phase, [string[]]$Arguments)
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $script:Oc @Arguments 2>$null)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    if ($code -ne 0) {
        throw "$Phase failed (oc exit $code). Raw output is suppressed because it may contain credentials."
    }
    return ($output -join [Environment]::NewLine)
}

function Invoke-PrivateKeytool {
    param([string]$Phase, [string[]]$Arguments)
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $null = @(& $script:Keytool @Arguments 2>$null)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    if ($code -ne 0) { throw "$Phase failed (keytool exit $code)" }
}

function Set-PrivateFileAcl {
    param([string]$Path)
    try {
        $acl = New-Object Security.AccessControl.FileSecurity
        $acl.SetAccessRuleProtection($true, $false)
        $rights = [Security.AccessControl.FileSystemRights]::FullControl
        $allow = [Security.AccessControl.AccessControlType]::Allow
        $identities = @(
            [Security.Principal.WindowsIdentity]::GetCurrent().User,
            (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')),
            (New-Object Security.Principal.SecurityIdentifier('S-1-5-32-544'))
        )
        foreach ($identity in $identities) {
            $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, $rights, $allow)
            $acl.AddAccessRule($rule)
        }
        [IO.File]::SetAccessControl($Path, $acl)
    } catch {
        throw 'Cannot restrict kubeconfig ACL to the current user, SYSTEM and Administrators'
    }
}

function Get-Sha256 {
    param([Security.Cryptography.X509Certificates.X509Certificate2]$Certificate)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Certificate.RawData))).Replace('-', '') }
    finally { $sha.Dispose() }
}

function Test-CertificateAuthority {
    param([Security.Cryptography.X509Certificates.X509Certificate2]$Certificate)
    foreach ($extension in $Certificate.Extensions) {
        if ($extension.Oid.Value -ne '2.5.29.19') { continue }
        $basic = New-Object Security.Cryptography.X509Certificates.X509BasicConstraintsExtension
        $basic.CopyFrom($extension)
        return $basic.CertificateAuthority
    }
    return $false
}

function Add-ObservedCertificate {
    param($Capture, [Security.Cryptography.X509Certificates.X509Certificate]$Certificate)
    if ($null -eq $Certificate) { return }
    $rawCertificate = $Certificate.Export(
        [Security.Cryptography.X509Certificates.X509ContentType]::Cert)
    $copy = [Security.Cryptography.X509Certificates.X509Certificate2]::new($rawCertificate)
    $fingerprint = Get-Sha256 $copy
    if (-not $Capture.ByFingerprint.ContainsKey($fingerprint)) {
        $Capture.ByFingerprint[$fingerprint] = $copy
    } else {
        $copy.Dispose()
    }
}

function Complete-IngressChainFromAia {
    param($Capture)
    $before = $Capture.ByFingerprint.Count
    $leaf = @($Capture.ByFingerprint.Values |
        Where-Object { -not (Test-CertificateAuthority $_) } |
        Sort-Object NotBefore -Descending |
        Select-Object -First 1)
    if ($leaf.Count -eq 0) {
        $Capture | Add-Member NoteProperty AiaCompletion 'no-leaf-certificate' -Force
        return $Capture
    }
    $chain = New-Object Security.Cryptography.X509Certificates.X509Chain
    try {
        $chain.ChainPolicy.RevocationMode = [Security.Cryptography.X509Certificates.X509RevocationMode]::NoCheck
        $chain.ChainPolicy.VerificationFlags = [Security.Cryptography.X509Certificates.X509VerificationFlags]::AllowUnknownCertificateAuthority
        $chain.ChainPolicy.UrlRetrievalTimeout = [TimeSpan]::FromSeconds(20)
        foreach ($certificate in $Capture.ByFingerprint.Values) {
            [void]$chain.ChainPolicy.ExtraStore.Add($certificate)
        }
        [void]$chain.Build($leaf[0])
        foreach ($element in $chain.ChainElements) {
            Add-ObservedCertificate -Capture $Capture -Certificate $element.Certificate
        }
    } finally {
        $chain.Dispose()
    }
    $added = $Capture.ByFingerprint.Count - $before
    $status = if ($added -gt 0) { 'issuer-certificates-added' } else { 'no-issuer-resolved' }
    $Capture | Add-Member NoteProperty AiaCompletion $status -Force
    Write-Host ('IngressAiaCompletion=' + $status)
    Write-Host ('IngressAiaCertificatesAdded=' + $added)
    return $Capture
}

function Get-IngressCertificateChain {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$ClientStore,
        [string]$ClientPassword
    )
    $capture = [pscustomobject]@{
        Errors = [Net.Security.SslPolicyErrors]::RemoteCertificateNotAvailable
        ByFingerprint = @{}
        CollectionHandshake = 'not-started'
    }
    $callback = [Net.Security.RemoteCertificateValidationCallback]{
        param($Sender, $Certificate, $Chain, $SslPolicyErrors)
        $capture.Errors = $SslPolicyErrors
        Add-ObservedCertificate -Capture $capture -Certificate $Certificate
        if ($null -ne $Chain) {
            foreach ($element in $Chain.ChainElements) {
                Add-ObservedCertificate -Capture $capture -Certificate $element.Certificate
            }
        }
        # Collection may continue, but an untrusted chain is never accepted for truststore creation below.
        return $true
    }
    $tcp = New-Object Net.Sockets.TcpClient
    $ssl = $null
    try {
        $connect = $tcp.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(15000)) { throw 'Ingress TCP connection timed out' }
        $tcp.EndConnect($connect)
        $tcp.ReceiveTimeout = 15000
        $tcp.SendTimeout = 15000
        $ssl = New-Object Net.Security.SslStream($tcp.GetStream(), $false, $callback)
        $collection = New-Object Security.Cryptography.X509Certificates.X509CertificateCollection
        $ssl.AuthenticateAsClient($HostName, $collection,
            [Security.Authentication.SslProtocols]::Tls12, $false)
        $capture.CollectionHandshake = 'completed-without-client-certificate'
    } catch {
        if ($capture.ByFingerprint.Count -eq 0) { throw }
        $capture.CollectionHandshake = 'server-chain-captured-before-client-auth'
    } finally {
        if ($null -ne $ssl) { $ssl.Dispose() }
        $tcp.Close()
    }
    if ($capture.ByFingerprint.Count -eq 0) { throw 'Ingress did not present a server certificate' }
    return $capture
}

function Write-ChainEvidence {
    param($Capture, [string]$Directory, [string]$HostName, [int]$Port)
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $items = @()
    foreach ($entry in $Capture.ByFingerprint.GetEnumerator() | Sort-Object Name) {
        $certificate = $entry.Value
        $fingerprint = $entry.Key
        $isCa = Test-CertificateAuthority $certificate
        $path = Join-Path $Directory ("certificate-$fingerprint.cer")
        [IO.File]::WriteAllBytes($path, $certificate.Export(
                [Security.Cryptography.X509Certificates.X509ContentType]::Cert))
        $items += [pscustomobject]@{
            sha256 = $fingerprint
            subject = $certificate.Subject
            issuer = $certificate.Issuer
            notBeforeUtc = $certificate.NotBefore.ToUniversalTime().ToString('o')
            notAfterUtc = $certificate.NotAfter.ToUniversalTime().ToString('o')
            certificateAuthority = $isCa
            file = [IO.Path]::GetFileName($path)
        }
    }
    $report = [ordered]@{
        host = $HostName
        port = $Port
        windowsTlsPolicyErrors = [string]$Capture.Errors
        windowsTlsValidated = ($Capture.Errors -eq [Net.Security.SslPolicyErrors]::None)
        collectionHandshake = $Capture.CollectionHandshake
        aiaCompletion = $Capture.AiaCompletion
        certificates = $items
    }
    [IO.File]::WriteAllText((Join-Path $Directory 'chain-report.json'),
        ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    return $items
}

function New-IngressTruststore {
    param(
        $Capture,
        [object[]]$Evidence,
        [string]$Directory,
        [string]$Target,
        [string]$Password,
        [string[]]$ApprovedFingerprints
    )
    if (($Capture.Errors -band [Net.Security.SslPolicyErrors]::RemoteCertificateNameMismatch) -ne 0 -or
        ($Capture.Errors -band [Net.Security.SslPolicyErrors]::RemoteCertificateNotAvailable) -ne 0) {
        throw 'Ingress certificate name/availability validation failed; fingerprints cannot override this error'
    }
    $approved = @{}
    foreach ($value in $ApprovedFingerprints) {
        $normalized = ([string]$value).Replace(':', '').Replace('-', '').Trim().ToUpperInvariant()
        if ($normalized -notmatch '^[0-9A-F]{64}$') { throw 'ApprovedCaSha256 contains an invalid SHA-256 fingerprint' }
        $approved[$normalized] = $true
    }
    $windowsApproved = $Capture.Errors -eq [Net.Security.SslPolicyErrors]::None
    $selected = @($Evidence | Where-Object {
        $_.certificateAuthority -and ($windowsApproved -or $approved.ContainsKey($_.sha256))
    })
    if (-not $windowsApproved) {
        if ($approved.Count -eq 0) {
            throw "CA_APPROVAL_REQUIRED: Windows did not validate the ingress chain. Review $Directory\chain-report.json and rerun with -ApprovedCaSha256 after corporate PKI confirmation."
        }
        foreach ($fingerprint in $approved.Keys) {
            if (-not ($Evidence | Where-Object { $_.certificateAuthority -and $_.sha256 -eq $fingerprint })) {
                throw "Approved CA fingerprint was not observed from this ingress host: $fingerprint"
            }
        }
    }
    if ($selected.Count -eq 0) { throw 'No approved, currently valid X.509 CA certificate was observed' }
    $now = [DateTime]::UtcNow
    foreach ($item in $selected) {
        if ([DateTime]::Parse($item.notBeforeUtc).ToUniversalTime() -gt $now -or
            [DateTime]::Parse($item.notAfterUtc).ToUniversalTime() -lt $now) {
            throw "Approved CA certificate is not currently valid: $($item.sha256)"
        }
        $certificatePath = Resolve-ExistingFile (Join-Path $Directory $item.file) 'Observed X.509 CA'
        $validated = New-Object Security.Cryptography.X509Certificates.X509Certificate2($certificatePath)
        try {
            if (-not (Test-CertificateAuthority $validated)) { throw 'Refusing to import a non-CA certificate' }
        } finally { $validated.Dispose() }
    }

    $tempStore = "$Target.new-$([Guid]::NewGuid().ToString('N'))"
    $env:SCHEDULER_TRUSTSTORE_PASSWORD = $Password
    try {
        foreach ($item in $selected) {
            $certificatePath = Join-Path $Directory $item.file
            $alias = 'ingress-ca-' + $item.sha256.Substring(0, 16).ToLowerInvariant()
            $importArguments = @('-importcert', '-noprompt', '-trustcacerts', '-alias', $alias,
                '-file', $certificatePath, '-keystore', $tempStore, '-storetype', 'PKCS12',
                '-storepass:env', 'SCHEDULER_TRUSTSTORE_PASSWORD')
            Invoke-PrivateKeytool 'Import validated ingress CA certificate' $importArguments
        }
        $listArguments = @('-list', '-keystore', $tempStore, '-storetype', 'PKCS12',
            '-storepass:env', 'SCHEDULER_TRUSTSTORE_PASSWORD')
        Invoke-PrivateKeytool 'List newly created ingress truststore' $listArguments
        if (Test-Path -LiteralPath $Target -PathType Leaf) {
            Copy-Item -LiteralPath $Target -Destination ($Target + '.before-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) -Force
        }
        Move-Item -LiteralPath $tempStore -Destination $Target -Force
    } finally {
        Remove-Item Env:SCHEDULER_TRUSTSTORE_PASSWORD -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $tempStore -PathType Leaf) { Remove-Item -LiteralPath $tempStore -Force }
    }
    Write-Host ('IngressCaCount=' + $selected.Count)
    Write-Host 'KeytoolListReady=true'
}

function Assert-X509CertificateBundle {
    param([byte[]]$Bytes, [string]$Source)
    if ($null -eq $Bytes -or $Bytes.Length -eq 0) { throw "$Source is empty" }
    $text = [Text.Encoding]::ASCII.GetString($Bytes)
    $matches = [regex]::Matches(
        $text,
        '-----BEGIN CERTIFICATE-----(?<body>.*?)-----END CERTIFICATE-----',
        [Text.RegularExpressions.RegexOptions]::Singleline)
    try {
        if ($matches.Count -eq 0) {
            $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2 -ArgumentList @(,$Bytes)
            $certificate.Dispose()
            return
        }
        foreach ($match in $matches) {
            $body = $match.Groups['body'].Value -replace '\s', ''
            $der = [Convert]::FromBase64String($body)
            $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2 -ArgumentList @(,$der)
            $certificate.Dispose()
        }
    } catch {
        throw "$Source is not a valid X.509 certificate bundle"
    }
}

function New-ServiceAccountKubeconfig {
    param(
        [string]$Target,
        [string]$Bootstrap,
        [string]$BootstrapContextName
    )
    $bootstrapArgs = @('--kubeconfig', $Bootstrap, '--request-timeout=15s')
    if (-not [string]::IsNullOrWhiteSpace($BootstrapContextName)) {
        $bootstrapArgs += @('--context', $BootstrapContextName)
    }
    $actualBootstrapUser = (Invoke-PrivateOc 'Bootstrap OpenShift authentication' ($bootstrapArgs + @('whoami'))).Trim()
    if ([string]::IsNullOrWhiteSpace($actualBootstrapUser)) {
        throw 'BOOTSTRAP_OC_AUTH_REQUIRED: authenticate oc interactively before running this script'
    }
    $sourceJson = Invoke-PrivateOc 'Read bootstrap cluster metadata' (
        $bootstrapArgs + @('config', 'view', '--raw', '--flatten', '--minify', '-o', 'json'))
    try { $source = $sourceJson | ConvertFrom-Json }
    catch { throw 'Cannot parse bootstrap kubeconfig metadata' }
    if (@($source.clusters).Count -ne 1) { throw 'Bootstrap context must select exactly one cluster' }
    $sourceCluster = @($source.clusters)[0].cluster
    if (([string]$sourceCluster.server).TrimEnd('/') -cne $script:ApiServerValue.TrimEnd('/')) {
        throw 'Bootstrap context points to a different OpenShift API server'
    }
    if ($sourceCluster.'insecure-skip-tls-verify' -eq $true) {
        throw 'Bootstrap kubeconfig disables API TLS verification'
    }
    $caData = [string]$sourceCluster.'certificate-authority-data'
    if ([string]::IsNullOrWhiteSpace($caData)) {
        $caPath = [string]$sourceCluster.'certificate-authority'
        if (-not [string]::IsNullOrWhiteSpace($caPath)) {
            if (-not [IO.Path]::IsPathRooted($caPath)) {
                $caPath = Join-Path (Split-Path -Parent $Bootstrap) $caPath
            }
            $caPath = Resolve-ExistingFile $caPath 'Bootstrap cluster CA'
            $caData = [Convert]::ToBase64String([IO.File]::ReadAllBytes($caPath))
        }
    }

    $secretJson = Invoke-PrivateOc 'Read configured ServiceAccount token secret' (
        $bootstrapArgs + @('-n', $script:NamespaceValue, 'get', 'secret', $script:ServiceAccountSecretValue, '-o', 'json'))
    try { $secret = $secretJson | ConvertFrom-Json }
    catch { throw 'Cannot parse the configured ServiceAccount token secret' }
    if ([string]$secret.type -cne 'kubernetes.io/service-account-token') {
        throw 'Configured secret is not a kubernetes.io/service-account-token secret'
    }
    $secretServiceAccount = [string]$secret.metadata.annotations.'kubernetes.io/service-account.name'
    if ($secretServiceAccount -cne $script:ServiceAccountValue) {
        throw 'Configured token secret belongs to another ServiceAccount'
    }
    $caSource = 'bootstrap-kubeconfig'
    if ([string]::IsNullOrWhiteSpace($caData)) {
        $caData = [string]$secret.data.'ca.crt'
        $caSource = 'service-account-secret'
    }
    if ([string]::IsNullOrWhiteSpace($caData)) {
        throw 'BOOTSTRAP_CLUSTER_CA_MISSING: neither the flattened kubeconfig nor the ServiceAccount secret contains a cluster CA'
    }
    try { $caBytes = [Convert]::FromBase64String($caData) }
    catch { throw 'Bootstrap cluster CA data is not valid Base64' }
    Assert-X509CertificateBundle $caBytes 'Bootstrap cluster CA'
    Write-Host ('BootstrapClusterCaSource=' + $caSource)
    $encodedToken = [string]$secret.data.token
    if ([string]::IsNullOrWhiteSpace($encodedToken)) { throw 'Configured ServiceAccount secret has no token data' }
    $tokenBytes = [Convert]::FromBase64String($encodedToken)
    $token = [Text.Encoding]::UTF8.GetString($tokenBytes).Trim()
    [Array]::Clear($tokenBytes, 0, $tokenBytes.Length)
    if ([string]::IsNullOrWhiteSpace($token) -or $token -match '\s') {
        $token = $null
        throw 'Decoded ServiceAccount token is empty or malformed'
    }

    $clusterName = 'dev-terra000003-service-account-cluster'
    $userName = "system:serviceaccount:$($script:NamespaceValue):$($script:ServiceAccountValue)"
    $config = [ordered]@{
        apiVersion = 'v1'
        kind = 'Config'
        clusters = @([ordered]@{
            name = $clusterName
            cluster = [ordered]@{
                server = $script:ApiServerValue
                'certificate-authority-data' = $caData
                'insecure-skip-tls-verify' = $false
            }
        })
        users = @([ordered]@{ name = $userName; user = [ordered]@{ token = $token } })
        contexts = @([ordered]@{
            name = $script:ContextValue
            context = [ordered]@{
                cluster = $clusterName
                namespace = $script:NamespaceValue
                user = $userName
            }
        })
        'current-context' = $script:ContextValue
    }
    $temp = "$Target.new-$([Guid]::NewGuid().ToString('N'))"
    try {
        [IO.File]::WriteAllText($temp, ($config | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
        $parsed = [IO.File]::ReadAllText($temp) | ConvertFrom-Json
        $active = @($parsed.contexts | Where-Object name -eq $parsed.'current-context')[0]
        $activeUser = @($parsed.users | Where-Object name -eq $active.context.user)[0]
        $tokenPresent = -not [string]::IsNullOrWhiteSpace([string]$activeUser.user.token)
        if (-not $tokenPresent) { throw 'Generated kubeconfig active user has no token' }
        if (Test-Path -LiteralPath $Target -PathType Leaf) {
            Copy-Item -LiteralPath $Target -Destination ($Target + '.before-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) -Force
        }
        Move-Item -LiteralPath $temp -Destination $Target -Force
        Set-PrivateFileAcl $Target
    } finally {
        $token = $null
        $encodedToken = $null
        $secretJson = $null
        $sourceJson = $null
        if (Test-Path -LiteralPath $temp -PathType Leaf) { Remove-Item -LiteralPath $temp -Force }
    }

    $targetArgs = @('--kubeconfig', $Target, '--context', $script:ContextValue,
        '--request-timeout=15s', '-n', $script:NamespaceValue)
    $identity = (Invoke-PrivateOc 'Validate generated ServiceAccount kubeconfig' ($targetArgs + @('whoami'))).Trim()
    if ($identity -cne $userName) { throw 'Generated kubeconfig authenticated as an unexpected identity' }
    $permissions = @(
        @('get', 'pods'), @('list', 'pods'), @('delete', 'pods'), @('get', 'pods/log'),
        @('get', 'deployments.apps'), @('list', 'deployments.apps'),
        @('get', 'configmaps'), @('list', 'configmaps'), @('patch', 'configmaps')
    )
    $denied = @()
    foreach ($permission in $permissions) {
        $answer = (Invoke-PrivateOc 'Validate ServiceAccount RBAC' (
            $targetArgs + @('auth', 'can-i', $permission[0], $permission[1]))).Trim()
        if ($answer -cne 'yes') { $denied += ($permission -join ' ') }
    }
    Write-Host 'TokenPresent=true'
    if ($denied.Count -gt 0) {
        Write-Host 'ContainerServiceAuthReady=false'
        throw ('ServiceAccount lacks required ContainerService permissions: ' + ($denied -join ', '))
    }
    Write-Host 'ContainerServiceAuthReady=true'
}

function Get-JavaIngressCertificateChain {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$ClientStore,
        [string]$ClientPassword,
        [string]$WorkingDirectory
    )
    $sourcePath = Join-Path $WorkingDirectory 'CollectIngressChain.java'
    $outputDirectory = Join-Path $WorkingDirectory 'collected-ingress-chain'
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $source = @'
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

public final class CollectIngressChain {
    public static void main(String[] args) throws Exception {
        char[] password = requiredEnv("SCHEDULER_KEYSTORE_PASSWORD").toCharArray();
        String clientStorePath = requiredEnv("SCHEDULER_KEYSTORE_PATH");
        Path output = java.nio.file.Paths.get(args[2]);
        try {
            KeyStore client = KeyStore.getInstance("PKCS12");
            try (FileInputStream input = new FileInputStream(clientStorePath)) {
                client.load(input, password);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(client, password);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509ExtendedTrustManager system = null;
            for (TrustManager manager : tmf.getTrustManagers()) {
                if (manager instanceof X509ExtendedTrustManager) {
                    system = (X509ExtendedTrustManager) manager;
                    break;
                }
            }
            if (system == null) throw new IllegalStateException("No extended system trust manager is available");
            CapturingTrustManager capture = new CapturingTrustManager(system);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), new TrustManager[] { capture }, null);
            int port = Integer.parseInt(args[1]);
            try (Socket plain = new Socket()) {
                plain.connect(new InetSocketAddress(args[0], port), 15000);
                try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                        .createSocket(plain, args[0], port, true)) {
                    socket.setSoTimeout(15000);
                    SSLParameters parameters = socket.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    socket.setSSLParameters(parameters);
                    try { socket.startHandshake(); } catch (Exception ignored) { }
                }
            }
            X509Certificate[] chain = capture.chain();
            if (chain == null || chain.length == 0) {
                throw new IllegalStateException("Ingress did not present a server certificate chain");
            }
            Files.createDirectories(output);
            Map<String, X509Certificate> unique = new LinkedHashMap<>();
            for (X509Certificate certificate : chain) {
                String fingerprint = hex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
                unique.putIfAbsent(fingerprint, certificate);
            }
            for (Map.Entry<String, X509Certificate> entry : unique.entrySet()) {
                Files.write(output.resolve("certificate-" + entry.getKey() + ".cer"),
                        entry.getValue().getEncoded());
            }
            System.out.println("JavaSystemTlsValidated=" + capture.systemValidated());
            System.out.println("JavaIngressChainCollected=true");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) throw new IllegalStateException("Missing secret environment variable");
        return value;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02X", value));
        return result.toString();
    }

    private static final class CapturingTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        private volatile X509Certificate[] serverChain;
        private volatile boolean validated;

        private CapturingTrustManager(X509ExtendedTrustManager delegate) { this.delegate = delegate; }
        private void remember(X509Certificate[] chain) {
            if (chain != null) serverChain = chain.clone();
        }
        private void validate(X509Certificate[] chain, String authType, Socket socket) {
            remember(chain);
            try { delegate.checkServerTrusted(chain, authType, socket); validated = true; }
            catch (CertificateException ignored) { validated = false; }
        }
        private void validate(X509Certificate[] chain, String authType, SSLEngine engine) {
            remember(chain);
            try { delegate.checkServerTrusted(chain, authType, engine); validated = true; }
            catch (CertificateException ignored) { validated = false; }
        }
        private void validate(X509Certificate[] chain, String authType) {
            remember(chain);
            try { delegate.checkServerTrusted(chain, authType); }
            catch (CertificateException ignored) { }
            validated = false;
        }
        private X509Certificate[] chain() { return serverChain == null ? null : serverChain.clone(); }
        private boolean systemValidated() { return validated; }
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType) { validate(chain, authType); }
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, socket);
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            validate(chain, authType, socket);
        }
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, engine);
        }
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            validate(chain, authType, engine);
        }
        public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
    }
}
'@
    [IO.File]::WriteAllText($sourcePath, $source, [Text.UTF8Encoding]::new($false))
    $null = @(& $script:Javac '-encoding' 'UTF-8' '-d' $WorkingDirectory $sourcePath 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot compile the isolated Java ingress chain collector' }
    $env:SCHEDULER_KEYSTORE_PATH = $ClientStore
    $env:SCHEDULER_KEYSTORE_PASSWORD = $ClientPassword
    try {
        $javaArguments = @('-cp', $WorkingDirectory, 'CollectIngressChain', $HostName,
            ([string]$Port), $outputDirectory)
        $output = @(& $script:Java @javaArguments 2>&1)
        $joined = $output -join "`n"
        if ($LASTEXITCODE -ne 0 -or $joined -notmatch 'JavaIngressChainCollected=true') {
            throw 'JAVA_INGRESS_CHAIN_COLLECTION_FAILED: inspect client PKCS12 compatibility and ingress TLS policy'
        }
        $validated = $joined -match 'JavaSystemTlsValidated=true'
    } finally {
        Remove-Item Env:SCHEDULER_KEYSTORE_PATH -ErrorAction SilentlyContinue
        Remove-Item Env:SCHEDULER_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    }
    $capture = [pscustomobject]@{
        Errors = if ($validated) { [Net.Security.SslPolicyErrors]::None } else {
            [Net.Security.SslPolicyErrors]::RemoteCertificateChainErrors
        }
        ByFingerprint = @{}
        CollectionHandshake = 'java-client-certificate'
    }
    foreach ($file in Get-ChildItem -LiteralPath $outputDirectory -Filter '*.cer' -File) {
        $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2($file.FullName)
        $fingerprint = Get-Sha256 $certificate
        if (-not $capture.ByFingerprint.ContainsKey($fingerprint)) {
            $capture.ByFingerprint[$fingerprint] = $certificate
        } else {
            $certificate.Dispose()
        }
    }
    if ($capture.ByFingerprint.Count -eq 0) { throw 'Java collector produced no X.509 certificates' }
    Write-Host 'JavaIngressChainCollected=true'
    return $capture
}

function Test-JavaMtlsHandshake {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$Truststore,
        [string]$TruststorePassword,
        [string]$ClientStore,
        [string]$ClientPassword,
        [string]$WorkingDirectory
    )
    $sourcePath = Join-Path $WorkingDirectory 'VerifyIngressMtls.java'
    $source = @'
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public final class VerifyIngressMtls {
    public static void main(String[] args) throws Exception {
        char[] trustPassword = requiredEnv("SCHEDULER_TRUSTSTORE_PASSWORD").toCharArray();
        char[] clientPassword = requiredEnv("SCHEDULER_KEYSTORE_PASSWORD").toCharArray();
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (FileInputStream input = new FileInputStream(args[2])) { trust.load(input, trustPassword); }
        KeyStore client = KeyStore.getInstance("PKCS12");
        try (FileInputStream input = new FileInputStream(args[3])) { client.load(input, clientPassword); }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(client, clientPassword);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(args[0], Integer.parseInt(args[1])), 15000);
            socket.setSoTimeout(15000);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            System.out.println("MtlsHandshakeReady=true");
        } finally {
            java.util.Arrays.fill(trustPassword, '\0');
            java.util.Arrays.fill(clientPassword, '\0');
        }
    }
    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) throw new IllegalStateException("Missing secret environment variable");
        return value;
    }
}
'@
    [IO.File]::WriteAllText($sourcePath, $source, [Text.UTF8Encoding]::new($false))
    $null = @(& $script:Javac '-encoding' 'UTF-8' '-d' $WorkingDirectory $sourcePath 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot compile the isolated Java mTLS verifier' }
    $env:SCHEDULER_TRUSTSTORE_PASSWORD = $TruststorePassword
    $env:SCHEDULER_KEYSTORE_PASSWORD = $ClientPassword
    try {
        $javaArguments = @('-cp', $WorkingDirectory, 'VerifyIngressMtls', $HostName,
            ([string]$Port), $Truststore, $ClientStore)
        $output = @(& $script:Java @javaArguments 2>&1)
        if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch 'MtlsHandshakeReady=true') {
            throw 'JAVA_MTLS_HANDSHAKE_FAILED: inspect the ingress chain, client certificate validity and route policy'
        }
    } finally {
        Remove-Item Env:SCHEDULER_TRUSTSTORE_PASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:SCHEDULER_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    }
    Write-Host 'MtlsHandshakeReady=true'
}

$Project = [IO.Path]::GetFullPath($Project)
$secureFile = Resolve-ExistingFile (Join-Path $Project 'secure.local.override.properties') 'Secure override'
$properties = Read-Properties $secureFile
$targetKubeconfig = Resolve-OutputFile (Require-Property $properties 'SECURE_OPENSHIFT_DEV_KUBECONFIG_PATH') 'Target kubeconfig'
$clientStore = Resolve-ExistingFile (Require-Property $properties 'SECURE_MTLS_KEYSTORE_PATH') 'Client PKCS12'
$truststore = Resolve-OutputFile (Require-Property $properties 'SECURE_MTLS_TRUSTSTORE_PATH') 'Ingress truststore'
$clientPassword = if ($properties.Contains('SECURE_MTLS_CLIENT_PKCS12_PASSWORD')) {
    Require-Property $properties 'SECURE_MTLS_CLIENT_PKCS12_PASSWORD'
} else {
    Require-Property $properties 'SECURE_MTLS_KEYSTORE_PASSWORD'
}
$truststorePassword = Require-Property $properties 'SECURE_MTLS_TRUSTSTORE_PASSWORD'
$projectPrefix = $Project.TrimEnd('\') + '\'
foreach ($secretPath in @($targetKubeconfig, $clientStore, $truststore)) {
    if ($secretPath.StartsWith($projectPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Credential and certificate files must stay outside the project directory'
    }
}
$script:Oc = Resolve-ExistingFile $OcExecutable 'oc executable'
$script:Keytool = Find-Tool 'keytool'
$script:Javac = Find-Tool 'javac'
$script:Java = Find-Tool 'java'
$bootstrap = Resolve-ExistingFile $BootstrapKubeconfig 'Bootstrap kubeconfig'
$script:ApiServerValue = $ApiServer
$script:NamespaceValue = $Namespace
$script:ContextValue = $Context
$script:ServiceAccountValue = $ServiceAccount
$script:ServiceAccountSecretValue = $ServiceAccountSecret

$api = [Uri]$ApiServer
if ($api.Scheme -cne 'https' -or $api.UserInfo -or $api.Query -or $api.Fragment -or
    $api.AbsolutePath -ne '/' -or $api.Port -ne 6443) {
    throw 'ApiServer must be the approved HTTPS OpenShift API origin on port 6443'
}
if ($IngressHost -notmatch '^[a-z0-9.-]+$' -or $IngressHost -notlike 'ingress-v2.*.apps.dev-terra000003-ids.ocp.delta.sbrf.ru') {
    throw 'IngressHost is outside the approved DEV ingress-v2 DNS suffix'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidenceDirectory = Join-Path (Split-Path -Parent $truststore) ("ingress-chain-evidence\$stamp")
$workDirectory = Join-Path $env:TEMP ("scheduler-dev-bootstrap-$([Guid]::NewGuid().ToString('N'))")
New-Item -ItemType Directory -Path $workDirectory -Force | Out-Null
try {
    New-ServiceAccountKubeconfig -Target $targetKubeconfig -Bootstrap $bootstrap `
        -BootstrapContextName $BootstrapContext
    $capture = Get-JavaIngressCertificateChain -HostName $IngressHost -Port $IngressPort `
        -ClientStore $clientStore -ClientPassword $clientPassword -WorkingDirectory $workDirectory
    $capture = Complete-IngressChainFromAia -Capture $capture
    $evidence = @(Write-ChainEvidence -Capture $capture -Directory $evidenceDirectory `
        -HostName $IngressHost -Port $IngressPort)
    Write-Host ('WindowsTlsValidated=' + ($capture.Errors -eq [Net.Security.SslPolicyErrors]::None))
    Write-Host ('IngressChainEvidence=' + $evidenceDirectory)
    New-IngressTruststore -Capture $capture -Evidence $evidence -Directory $evidenceDirectory `
        -Target $truststore -Password $truststorePassword -ApprovedFingerprints $ApprovedCaSha256
    Test-JavaMtlsHandshake -HostName $IngressHost -Port $IngressPort -Truststore $truststore `
        -TruststorePassword $truststorePassword -ClientStore $clientStore `
        -ClientPassword $clientPassword -WorkingDirectory $workDirectory
    Write-Host 'SchedulerDevInfrastructureReady=true'
} finally {
    $clientPassword = $null
    $truststorePassword = $null
    if (Test-Path -LiteralPath $workDirectory -PathType Container) {
        Remove-Item -LiteralPath $workDirectory -Recurse -Force
    }
}

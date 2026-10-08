# Scheduler DEV infrastructure bootstrap

This procedure prepares the two local artifacts required by scheduler regression:

- a token-bearing ServiceAccount kubeconfig for `ContainerService`;
- a strict PKCS12 truststore for the actual `ingress-v2` server chain.

It does not disable TLS verification, does not modify service assertions and does not print tokens or passwords.

## Prerequisites

- `oc.exe` is authenticated with a bootstrap identity allowed to read the configured ServiceAccount token Secret;
- `JAVA_HOME` points to a JDK containing `keytool`, `javac` and `java`;
- `secure.local.override.properties` contains the external paths and passwords;
- the client PKCS12 already exists outside the project.

Required local values:

```properties
SECURE_OPENSHIFT_DEV_KUBECONFIG_PATH=C:/Work/private/openshift-service-access/dev-ci07963639-dev-terra000003-abtm-back/container-service-client/kubeconfig-service-account-token
SECURE_OPENSHIFT_DEV_SERVICE_ACCOUNT_SECRET=argocd-token-4nxtg
SECURE_MTLS_KEYSTORE_PATH=C:/Work/private/openshift-service-access/dev-ci07963639-dev-terra000003-abtm-back/mtls/mtls-client-auth.p12
SECURE_MTLS_TRUSTSTORE_PATH=C:/Work/private/openshift-service-access/dev-ci07963639-dev-terra000003-abtm-back/mtls/ingress-v2-truststore.p12
SECURE_MTLS_CLIENT_PKCS12_PASSWORD=<local password>
SECURE_MTLS_TRUSTSTORE_PASSWORD=<local password>
```

## Run

```powershell
Set-Location 'C:\Work\IdeaProjects\integration-test'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
Unblock-File -LiteralPath '.\tools\scheduler\Prepare-SchedulerDevInfrastructure.ps1'

& '.\tools\scheduler\Prepare-SchedulerDevInfrastructure.ps1' `
  -Project 'C:\Work\IdeaProjects\integration-test'
```

The TLS collector uses `SslStream` and the Windows trust store, not `openssl`. When Windows validates the exact ingress hostname and chain, only observed certificates with `BasicConstraints CA=true` are imported. The leaf certificate and arbitrary `*.pem` files are never imported.

If Windows cannot validate the chain, the script writes `chain-report.json` and DER certificates under the private mTLS directory and stops with `CA_APPROVAL_REQUIRED`. After corporate PKI independently confirms the CA SHA-256 fingerprints, rerun:

```powershell
& '.\tools\scheduler\Prepare-SchedulerDevInfrastructure.ps1' `
  -Project 'C:\Work\IdeaProjects\integration-test' `
  -ApprovedCaSha256 @('<approved fingerprint 1>', '<approved fingerprint 2>')
```

Do not approve the ingress leaf certificate. Do not reuse fingerprints previously collected from the OpenShift API host on port `6443`.

## Required success output

```text
TokenPresent=true
ContainerServiceAuthReady=true
KeytoolListReady=true
MtlsHandshakeReady=true
SchedulerDevInfrastructureReady=true
```

The final Java check loads the generated truststore and client PKCS12, enables HTTPS hostname verification and performs a real TLS handshake with `ingress-v2`.

## Real scheduler jobs

The code guard remains enabled. Run real-job regression only on the approved dedicated DEV stand and set this local property explicitly:

```properties
stand.dev.workloads.scheduler.regression.jobs.enabled=true
```

## Regression

```powershell
.\gradlew.bat schedulerRegression --rerun-tasks --no-daemon --console=plain
```

Infrastructure failures stop before the regression. Business and service assertions are unchanged and remain visible after successful authentication.

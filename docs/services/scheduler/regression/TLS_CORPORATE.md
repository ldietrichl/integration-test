# Scheduler: corporate TLS configuration (v3.2)

## What changed

Scheduler's framework-owned RestClient now receives an explicit trust configuration.
HTTPS validates both the server certificate and hostname. No relaxedHTTPSValidation,
trust-all manager or JVM-global SSL mutation is introduced. HTTP-only local runs
do not load TLS stores. DB access remains the existing framework explab client.

The inspected shared REST configurations use relaxedHTTPSValidation. Their success
does not demonstrate that the corporate JVM has a valid truststore. Scheduler does
not copy that bypass. The previous logs identify empty effective trust anchors,
but do not identify the corporate file or JVM property that caused the problem.

SSLConfig integration uses its documented trustStore(KeyStore) API and strict
default hostname verifier:
https://github.com/rest-assured/rest-assured/blob/rest-assured-5.3.0/rest-assured/src/main/java/io/restassured/config/SSLConfig.java

## Existing test.properties: merge settings, do not replace the file

Use an APPROVED corporate truststore containing trusted X.509 certificate entries
for the service's certificate chain. Do not import arbitrary certificates just to
make a test pass; obtain the store or verify CA fingerprints with your administrator.
Do not substitute the client private-key keystore for the truststore.

Example for dev (choose the real path and actual format):

```properties
env=dev
scheduler.dev.truststore=C:/QA/certs/corporate-truststore.jks
scheduler.dev.truststore.type=JKS
# Existing shared password is reused through CustomTestConfigScope:
truststore.pass=${SECURE_TRUSTSTORE_PASS}
```

SECURE_TRUSTSTORE_PASS is an example placeholder name. Reuse the existing approved
secret reference from your corporate test.properties rather than adding a plaintext
password. The real certificate store and secure overrides are NOT part of the patch.
For PKCS12 set type=PKCS12; for ift use scheduler.ift.* and env=ift.

Resolution order:

- Path: scheduler.<env>.truststore / scheduler.truststore, then shared truststore.path.
- Type: scheduler.<env>.truststore.type / scheduler.truststore.type, then shared
  truststore.type, then KeyStore.getDefaultType(). Set the actual type explicitly.
- Password for an explicit file: scheduler.<env>.truststore.password /
  scheduler.truststore.password, otherwise the EXISTING shared truststore.pass.
- If no path is configured: use default TrustManagerFactory in the TEST JVM, then
  attach its accepted issuers as the RestClient truststore. This uses the JVM's
  effective trust configuration; it does not automatically use Windows certificates.

This patch deliberately does not add TLS JVM-property forwarding in Gradle. For
consistent IDEA and Gradle behavior prefer the resource-file configuration above;
Gradle daemon JVM options are not automatically the test JVM's options.
An explicit incorrect or empty store fails immediately, without fallback to another
store. A non-empty store still must trust the actual server chain and hostname.

## Optional client certificate (mTLS)

Only when the endpoint requires it:

```properties
scheduler.dev.mtls.enabled=true
# Defaults to this existing project resource when omitted:
scheduler.dev.keystore=src/test/resources/keystore.p12
scheduler.dev.keystore.type=PKCS12
# Existing keystore.pass is reused; keep its corporate secure reference.
```

The existing scheduler.<env>.keystore.password override remains supported and
takes precedence over shared keystore.pass. The key password must equal the
keystore password. mTLS remains disabled unless explicitly enabled, as before.
Truststore and keystore solve different tasks: trusting the server vs authenticating
the client. A token is separate again and is not a remedy for a TLS handshake error.

## Execution and diagnostics

Working directory must be the integration-test project root when using relative
paths. No passwords, private keys or certificate contents are attached to Allure by
the new helper. Store loading errors report category and exception class only.
Diagnostics identify missing/unreadable files, unresolved passwords, invalid
format/password, no trusted certificate entries or an unusable mTLS key.
TLS configuration is evaluated at RestClient configuration time, so it can surface
during framework initialization even before an HTTP step.

Using corporate Gradle 8.4, first compile testClasses, then run only SCH-135:

```powershell
& $gradle schedulerServiceRegression --tests '*SchedulerApiRegressionFlowTest.sch135' --console=plain
```

SCH-135 does not need fixture write permissions. Do not enable fixtures or claim
jobs are paused to diagnose HTTPS. The helper cannot supply missing corporate CA
certificates: setting the actual truststore remains an installation prerequisite.

This revision has not been compiled or executed locally. No corporate connection
was made. The previous successful DB preflight does not validate HTTPS or this patch.

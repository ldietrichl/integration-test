# EXPLAB-2974 fixed fixture client

This directory contains a test fixture client, not a data-operator application or a service stub.

- `LinksCacheTool.java`: ownership checks, read-only probe, metadata installation, validation and cleanup.
- `LinksCacheSchema.java`: fixed Ignite BinaryObject metadata contract, checked before fixture writes.
- `client-libraries.lock.json`: exact filenames and SHA256 hashes of the ten isolated client libraries.
- `lib/`: runtime libraries, included in the corporate offline package. Their bundled META-INF licenses/notices are retained.
- `pom.xml`: dependency coordinates for maintainers. Maven is not required to run the corporate tests.

The client uses Apache Ignite 2.18.0 and Jackson 2.17.2. It never loads the deployed service JAR. Updating the service while preserving the storage contract does not require updating this client. A storage contract or wire protocol change requires explicit compatibility work and a new verified bundle.

The build dependencies can be restored by a maintainer with `mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=lib`. After an intentional dependency update, regenerate the lock, verify hashes, rerun storage/cleanup checks and all endpoint scenarios, then package the full directory. Runtime code does not download dependencies or discover arbitrary libraries from Maven/Gradle caches.

See `docs/services/data-operator/EXPLAB_2974/CORPORATE_STATIC_CLIENT.md` for configuration, diagnostics and recovery. No credentials belong in this directory.

## Corporate dependencies from the service project

`pom-corporate.xml` declares the Ignite-related dependencies from data-operator commit
`06fd6acb646`: `com.sbt.ignite:ignite-core/ignite-indexing:17.6.0`,
`com.sbt.security.ignite:security-core/security-ldap:17.6.0`, and `javax.cache:cache-api:1.1.1`.
Jackson and transitive version constraints use the service's Spring Boot 3.5.8 and
Spring Cloud 2025.0.1 BOMs. This is the client subset, not the entire application.

On the corporate workstation, use the existing Gradle wrapper with
`-I tools/data-operator-explab-2974/corporate-client.init.gradle prepareExplab2974CorporateClient`.
The task reuses the project's repositories and credentials, resolves a separate
configuration, and compiles the existing helper against the resolved libraries.
It writes an immutable bundle under `corporate-runtimes/<runtime-sha256>` and publishes
`corporate-runtimes/latest-runtime.txt` only on success. Main/test dependency graphs,
existing libraries, secrets and fixture manifests are not modified.

Select the generated path using `links.fixture.<env>.runtime.directory` or its
environment-variable equivalent before running the connection diagnostic. Instructions
and known limits: `docs/services/data-operator/EXPLAB_2974/CORPORATE_CLIENT_SETUP.md`.
Corporate resolution, compilation and tests have not been run locally, as requested.

The corporate preparation task permits the observed `BinaryObject.class` overlap between
`com.sbt.ignite:ignite-core` and `ignite-binary-api` of the configured version. The core
JAR precedes the API JAR, following direct dependency order; compilation and execution
share this order. Required-class providers and byte hashes are recorded in
`build/explab-2974-client-compile/dependency-diagnostics.json` and the generated lock.
Neither JAR is removed. This classpath policy still needs compilation and a read-only
connection probe on the corporate workstation; it does not prove full binary compatibility.

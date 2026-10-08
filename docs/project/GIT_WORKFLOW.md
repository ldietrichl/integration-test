# Git workflow for functional tests

This repository is maintained with three local branches:

- `main` - clean local baseline and shared history root.
- `local/run` - local execution work: IDE tweaks, experiments, local-only checks, temporary run helpers.
- `corporate/env` - corporate-safe branch for patch preparation, push, merge request, and TestOps-compatible reporting changes.

## Rules

Do not commit local secrets to any branch. Working secret values belong only in the ignored `secure.local.override.properties`. The tracked `secure.local.properties` is a template, not a source of runtime credentials. Certificates and keystores also remain ignored. See [Project configuration](CONFIGURATION.md).

Connection settings belong in the corresponding tracked resource files: `test.properties` for the environment, REST and scenario parameters; `kafka-consumers.properties` and `kafka-producers.properties` for native SDK profiles; `database.properties` for `db.<env>.<name>.*`; `ignite.properties` for the project Ignite adapter. Keep secrets as `${...}` references. Environment variables, JVM properties and `gradle.local.properties` are no longer sources of connection settings or secrets.

Runtime JVM options still serve their normal purpose. In particular, Gradle may pass non-secret fixture `enabled`, `output.directory` and `run-id` flags for a run. These flags do not replace connection profiles or credentials; generated fixture, Allure and log output paths must stay inside `build`.

Ignored local files include:

```text
secure.local.override.properties
*.p12
*.pfx
*.jks
*.keystore
*.truststore
*.pem
*.key
*.crt
*.cer
*.der
allure-results/
build/
```

## Daily Work

Local execution:

```powershell
git switch local/run
git status --short
.\gradlew.bat propertyLayoutTest --no-daemon
.\gradlew.bat test --no-daemon
```

Bypass/TestOps registration run:

```powershell
git switch corporate/env
git status --short
.\gradlew.bat bypassTests --no-daemon
```

Select `env=ift` in `src/test/resources/test.properties` before either run. `-Denv`, `-Penv` and `ENV` do not change that selection. Run `clean` separately before a new independent cycle only after saving required results and completing fixture recovery: all generated regression fixtures/results and TestOps bundles are now under `build` and are removed by `clean`. Static source fixtures in `src/test/resources` are retained.

Before pushing the corporate branch:

```powershell
git switch corporate/env
git status --short
git diff --stat
git diff --cached --stat
```

If a local change from `local/run` is needed in the corporate branch, prefer a small commit on `local/run` and cherry-pick it:

```powershell
git switch corporate/env
git cherry-pick <commit-sha>
```

Commit only source/config/reporting files that are safe for the repository. Never add ignored local runtime files with `git add -f` unless the file has been reviewed and is intentionally non-secret.

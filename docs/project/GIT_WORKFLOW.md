# Git workflow for functional tests

This repository is maintained with three local branches:

- `main` - clean local baseline and shared history root.
- `local/run` - local execution work: IDE tweaks, experiments, local-only checks, temporary run helpers.
- `corporate/env` - corporate-safe branch for patch preparation, push, merge request, and TestOps-compatible reporting changes.

## Rules

Do not commit local secrets to any branch. Local passwords, certificates, keystores, and real environment properties stay only in ignored files.

Ignored local files include:

```text
gradle.local.properties
gradle.*.local.properties
src/test/resources/test.properties
src/test/resources/database.properties
src/test/resources/container-service.properties
src/test/resources/perfeccionista.properties
src/test/resources/kafka-consumers.properties
src/test/resources/kafka-producers.properties
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
.\gradlew.bat clean test --no-daemon -Denv=ift -Dallure.testStage=ift
```

Bypass/TestOps registration run:

```powershell
git switch corporate/env
git status --short
.\gradlew.bat clean bypassTests --no-daemon -Denv=ift -Dallure.testStage=ift
```

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

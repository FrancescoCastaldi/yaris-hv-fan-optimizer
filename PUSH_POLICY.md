# Git Push & Release Policy

This document defines authorization and procedures for executing `commit`, `tag`, and `push` operations within this repository. It complements versioning directives established in `AGENTS.md`.

## 1. Core Mandate: Explicit Human Authorization Required
Autonomous agents working on this repository **must never** execute `git commit`, `git tag`, or `git push` without explicit instruction.
- Agents are permitted to prepare changes end-to-end (code, unit tests, version codes, changelog, web documentation, and APK artifacts) and stage them uncommitted.
- Explicit confirmation must be obtained prior to publishing or committing.
- General user requests (e.g. "fix this", "update docs", "compile") do not imply push permission.

## 2. Standard Modification Pipeline
1. Root cause analysis (log analysis, code inspection, protocol traces).
2. Code modification accompanied by automated unit/integration tests.
3. Version increment (`versionCode` / `versionName` in `app/build.gradle.kts`) following Semantic Versioning.
4. Execute `gradle testDebugUnitTest` — build aborts on any test failure.
5. Execute `gradle assembleRelease` and synchronize APK binaries across project root and `docs/`.
6. Synchronize version numbers across: `build_apk.bat`, `.github/workflows/deploy.yml`, `docs/index.html`, `docs/404.html`, `docs/preview.html`, `docs/simulator.js`, `README.md`, and `CHANGELOG.md`.
7. **Stop and Report.** Present a concise summary to the user. Proceed with commit/tag/push only upon explicit command.

## 3. Authorization Protocol
Only the repository owner (`FrancescoCastaldi`) holds authority to approve commits, tags, and pushes to `main`. Authorization must be granted in the active session via unequivocal directives (e.g. "commit and push", "publish release").

## 4. Branch Protection
The `main` branch enforces `allow_force_pushes: false` and `allow_deletions: false`. Autonomous agents must not attempt to circumvent branch protection rules.

## 5. Local Build Environment
Local compilation and test runs rely on the toolchain defined in `build_apk.bat`:
- `JAVA_HOME=D:\Tools\jdk-21\jdk-21`
- `ANDROID_HOME=D:\Tools\android-sdk`
- Gradle 8.7 at `D:\Tools\gradle\gradle-8.7`

Do not generate untracked wrapper scripts (`gradlew` / `gradlew.bat`) that introduce environment discrepancies.

## 6. Continuous Integration (GitHub Actions)
The workflow `.github/workflows/deploy.yml` builds and deploys to GitHub Pages on every push to `main`. Because pushes require human approval, CI deployment remains under direct human control. Do not trigger CI runs via manual `workflow_dispatch` without authorization.

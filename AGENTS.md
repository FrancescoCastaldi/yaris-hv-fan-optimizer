# Repository Directives & Continuous Deployment Rules

## 📦 Golden Rule: Single Latest-Version APK & Strict Version Monotonicity
For every modification, fix, push, or release:
1. **Mandatory Non-Recycled Version Increment**:
   - **NEVER** reuse a previous version or git tag.
   - ALWAYS increment `versionName` using standard semantic versioning `num.num.num` (e.g., `3.0.4` -> `3.0.5`) and increment `versionCode` in `app/build.gradle.kts`.
2. **Strictly 1 Single APK per Release (Root + Docs)**:
   - Root directory contains **ONLY ONE** APK binary: `YarisHvFanControl-vX.Y.Z.apk`.
   - **NEVER** duplicate APKs with rolling filenames (e.g. `YarisHvFanControl.apk`).
   - Prior to building a new release, remove previous version APKs.
   - The `docs/` directory tracks **only** the active APK (`docs/YarisHvFanControl-vX.Y.Z.apk`): GitHub Pages deploys directly from `main` + `/docs`, so direct downloads must be live immediately.
3. **Synchronize All Web & Deployment References**:
   - `docs/index.html`, `docs/404.html`, and `docs/preview.html` (header, hero badge, download button, changelog, sticky mobile bar).
   - `build_apk.bat` and `.github/workflows/deploy.yml`.
   - `README.md` and `CHANGELOG.md`.
4. **Verification & Delivery Workflow**:
   - `gradle testDebugUnitTest` prior to packaging.
   - `build_apk.bat` to compile and RSA-sign both application and sniffer APKs.
   - Commit, create unique git tag `vX.Y.Z`, and `git push origin main --tags` **only after explicit human confirmation** (Rule 5).
5. **Strict No-Auto-Push Policy**:
   - AI agents MUST prepare code, tests, versions, changelogs, web portal files, and APKs, but **NEVER** execute `git commit`, `git tag`, or `git push` without explicit authorization in the current conversation (e.g., "commit and push", "publish release").
   - Generic prompts such as "fix this" or "compile" do not grant push permission.
   - Review `PUSH_POLICY.md` for complete toolchain and governance details.

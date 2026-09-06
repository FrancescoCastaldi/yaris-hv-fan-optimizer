# Pending Tasks - Continue Tomorrow

## Goal
Run unit tests and build signed APK for version 2.9.7 (versionCode 22), then commit, tag v2.9.7, and push.

## Execution Path

### 1. Run Unit Tests
```powershell
$env:JAVA_HOME = "D:\Tools\jdk-21\jdk-21"
$env:ANDROID_HOME = "D:\Tools\android-sdk"
& "D:\Tools\gradle\gradle-8.7\bin\gradle.bat" testDebugUnitTest
```
Note: The original gradle.bat has a bug with forward slashes in `set JAVA_EXE=%JAVA_HOME%/bin/java.exe`. If it fails with "JAVA_HOME is set to an invalid directory", either:
- Fix the gradle.bat to use backslashes: replace `/bin/java.exe` with `\bin\java.exe`
- Or generate a gradle wrapper with `gradle wrapper` and use the wrapper.

### 2. Build APK
```powershell
.\build_apk.bat
```
This runs `gradle assembleRelease` and copies the APK to:
- `YarisHvFanControl.apk` (rolling)
- `YarisHvFanControl-v2.9.7.apk`
- `docs/YarisHvFanControl.apk`
- `docs/YarisHvFanControl-v2.9.7.apk`

### 3. Commit and Tag
```bash
git add -A
git commit -m "feat(v2.9.7): 3K Motorsport HD carbon fiber weave & cockpit integration"
git tag v2.9.7
git push origin main --tags
```

### Current Status
- Modified files: CHANGELOG.md, README.md, app/build.gradle.kts (versionCode=22, versionName="2.9.7"), UI files, docs/, build_apk.bat
- Untracked: YarisHvFanControl-v2.9.7.apk, CarbonWeaveUnitTest.kt, carbon-pattern-3k.svg
- All changes are unstaged.

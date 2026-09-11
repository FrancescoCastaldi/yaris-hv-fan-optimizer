@echo off
setlocal enabledelayedexpansion

REM Environment Configuration with local fallback
if not defined JAVA_HOME (
    if exist "D:\Tools\jdk-21\jdk-21" set "JAVA_HOME=D:\Tools\jdk-21\jdk-21"
)
if not defined ANDROID_HOME (
    if exist "D:\Tools\android-sdk" set "ANDROID_HOME=D:\Tools\android-sdk"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if defined ANDROID_HOME set "PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%"

set GRADLE_OPTS=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8

cd /d "%~dp0"

echo ========================================================
echo   COMPILAZIONE APK RELEASE (YARIS HV AND OBD BRIDGE)
echo ========================================================

REM Find gradle executable
set "GRADLE_CMD=gradle"
if exist "D:\Tools\gradle\gradle-8.7\bin\gradle.bat" (
    set "GRADLE_CMD=D:\Tools\gradle\gradle-8.7\bin\gradle.bat"
)

echo [1/3] Compilazione APK Release con certificato RSA...
call %GRADLE_CMD% assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo [2/3] Sincronizzazione APK release - root e docs...
    del /Q "YarisHvFanControl*.apk" 2>nul
    del /Q "docs\YarisHvFanControl*.apk" 2>nul
    del /Q "YarisObdBridge*.apk" 2>nul
    del /Q "docs\YarisObdBridge*.apk" 2>nul

    echo Creazione copia locale: YarisHvFanControl-v3.1.0.apk
    copy /Y "app\build\outputs\apk\release\app-release.apk" "YarisHvFanControl-v3.1.0.apk"

    echo Copia nella cartella docs per GitHub Pages...
    copy /Y "app\build\outputs\apk\release\app-release.apk" "docs\YarisHvFanControl-v3.1.0.apk"

    copy /Y "sniffer\build\outputs\apk\release\sniffer-release.apk" "YarisObdBridge-v1.0.0.apk"
    copy /Y "sniffer\build\outputs\apk\release\sniffer-release.apk" "docs\YarisObdBridge-v1.0.0.apk"
    
    echo [3/3] File APK aggiornati pronti in root e docs!
    echo ========================================================
    echo   BUILD COMPLETATA CON SUCCESSO!
    echo   1. YarisHvFanControl-v3.1.0.apk
    echo   2. YarisObdBridge-v1.0.0.apk
    echo ========================================================
) else (
    echo [ERRORE] Compilazione fallita!
    exit /b 1
)

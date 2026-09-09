@echo off
setlocal enabledelayedexpansion
set JAVA_HOME=D:\Tools\jdk-21\jdk-21
set ANDROID_HOME=D:\Tools\android-sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%
set GRADLE_OPTS=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8

cd /d D:\Sviluppo\yaris-hv-fan-android

echo ========================================================
echo   COMPILAZIONE APK RELEASE (YARIS HV AND OBD BRIDGE)
echo ========================================================

echo [1/3] Compilazione APK Release con certificato RSA...
call D:\Tools\gradle\gradle-8.7\bin\gradle.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo [2/3] Sincronizzazione APK release - root e docs...
    del /Q "YarisHvFanControl*.apk" 2>nul
    del /Q "docs\YarisHvFanControl*.apk" 2>nul
    del /Q "YarisObdBridge*.apk" 2>nul
    del /Q "docs\YarisObdBridge*.apk" 2>nul

    echo Creazione copia locale: YarisHvFanControl-v3.0.0.apk
    copy /Y "app\build\outputs\apk\release\app-release.apk" "YarisHvFanControl-v3.0.0.apk"

    echo Copia nella cartella docs per GitHub Pages...
    copy /Y "app\build\outputs\apk\release\app-release.apk" "docs\YarisHvFanControl-v3.0.0.apk"

    copy /Y "sniffer\build\outputs\apk\release\sniffer-release.apk" "YarisObdBridge-v1.0.0.apk"
    copy /Y "sniffer\build\outputs\apk\release\sniffer-release.apk" "docs\YarisObdBridge-v1.0.0.apk"
    
    echo [3/3] File APK aggiornati pronti in root e docs!
    echo ========================================================
    echo   BUILD COMPLETATA CON SUCCESSO!
    echo   1. YarisHvFanControl-v3.0.0.apk
    echo   2. YarisObdBridge-v1.0.0.apk
    echo ========================================================
) else (
    echo [ERRORE] Compilazione fallita!
    exit /b 1
)

@echo off
setlocal enabledelayedexpansion
set JAVA_HOME=D:\Tools\jdk-21\jdk-21
set ANDROID_HOME=D:\Tools\android-sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%
set GRADLE_OPTS=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8

cd /d D:\Sviluppo\yaris-hv-fan-android

echo ========================================================
echo   COMPILAZIONE APK CON ETICHETTA VERSIONE DINAMICA
echo ========================================================

echo [1/3] Compilazione APK Release con certificato RSA...
call D:\Tools\gradle\gradle-8.7\bin\gradle.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo [2/3] Sincronizzazione APK release unico - root e docs...
    del /Q "YarisHvFanControl*.apk" 2>nul
    del /Q "docs\YarisHvFanControl*.apk" 2>nul
    copy /Y "app\build\outputs\apk\release\app-release.apk" "YarisHvFanControl-v2.9.16.apk"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "docs\YarisHvFanControl-v2.9.16.apk"
    
    echo [3/3] Singolo file APK aggiornato pronto in root e docs!
    echo ========================================================
    echo   BUILD COMPLETATA CON SUCCESSO!
    echo   Singolo APK v2.9.16 generato: YarisHvFanControl-v2.9.16.apk
    echo ========================================================
) else (
    echo [ERRORE] Compilazione fallita!
    exit /b 1
)

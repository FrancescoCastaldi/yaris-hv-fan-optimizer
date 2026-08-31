@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
set ANDROID_HOME=D:\Tools\android-sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%
set GRADLE_OPTS=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8

cd /d D:\Sviluppo\yaris-hv-fan-android
echo [1/3] Compilazione APK Release con certificato RSA...
call D:\Tools\gradle\gradle-8.7\bin\gradle.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo [2/3] Aggiornamento APK locale...
    copy /Y "app\build\outputs\apk\release\app-release.apk" "YarisHvFanControl.apk"
    echo [3/3] Aggiornamento APK per il sito web (docs/)...
    copy /Y "app\build\outputs\apk\release\app-release.apk" "docs\YarisHvFanControl.apk"
    echo ========================================================
    echo   BUILD COMPLETATA CON SUCCESSO!
    echo   Il sito web scarichera' sempre la versione piu' recente!
    echo ========================================================
) else (
    echo [ERRORE] Compilazione fallita!
)

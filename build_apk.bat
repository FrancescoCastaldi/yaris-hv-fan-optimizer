@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
set ANDROID_HOME=D:\android-sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%
set GRADLE_OPTS=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8

cd /d D:\yaris-hv-fan-android
call D:\gradle\gradle-8.7\bin\gradle.bat assembleRelease

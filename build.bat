@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "JAVA_HOME=C:\Users\19175\scoop\apps\temurin17-jdk\current"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [EchoStep] Temurin 17 was not found at %JAVA_HOME%.
  echo Install Temurin 17 or edit JAVA_HOME in this file.
  pause
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat %*

@echo off
setlocal
set GRADLE_VERSION=9.4.1
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set BOOTSTRAP_PARENT=%GRADLE_USER_HOME%\canasta-bootstrap
set BOOTSTRAP_DIR=%BOOTSTRAP_PARENT%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%BOOTSTRAP_DIR%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
  set ARCHIVE=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ARCHIVE%'"
  if errorlevel 1 exit /b 1
  if exist "%BOOTSTRAP_DIR%" rmdir /s /q "%BOOTSTRAP_DIR%"
  if not exist "%BOOTSTRAP_PARENT%" mkdir "%BOOTSTRAP_PARENT%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ARCHIVE%' '%BOOTSTRAP_PARENT%'"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" -p "%~dp0" %*
endlocal

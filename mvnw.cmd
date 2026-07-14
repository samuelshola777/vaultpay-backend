@echo off
setlocal enabledelayedexpansion
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

set MAVEN_VERSION=3.9.11
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%
set ARCHIVE=%TEMP%\apache-maven-%MAVEN_VERSION%.zip
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%ARCHIVE%'; Expand-Archive -Force '%ARCHIVE%' '%MAVEN_HOME%'; Move-Item -Force '%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\*' '%MAVEN_HOME%'; Remove-Item -Recurse -Force '%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%'; Remove-Item -Force '%ARCHIVE%'"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%

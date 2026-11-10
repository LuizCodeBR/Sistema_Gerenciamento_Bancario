@echo off
setlocal
for %%I in ("%~dp0..") do set "BANK_ROOT=%%~fI"
set "MAVEN_USER_HOME=%BANK_ROOT%\.cache\maven"
set "TEMP=%BANK_ROOT%\.cache\tmp"
set "TMP=%BANK_ROOT%\.cache\tmp"
if not exist "%TEMP%" mkdir "%TEMP%"
call "%BANK_ROOT%\mvnw.cmd" "-Dmaven.repo.local=%BANK_ROOT%\.cache\repository" -f "%BANK_ROOT%\pom.xml" spring-boot:run "-Dspring-boot.run.profiles=demo"
exit /b %errorlevel%

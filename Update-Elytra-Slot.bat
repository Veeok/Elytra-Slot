@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Put this file in the Minecraft instance's mods folder, close Minecraft, then run it.
rem You may instead drag the mods folder onto this file.
set "JAR_NAME=elytraslot-fabric-26.1.2-2.0.1.jar"
set "JAR_URL=https://github.com/Veeok/Elytra-Slot/releases/download/v2.0.1/elytraslot-fabric-26.1.2-2.0.1.jar"
set "EXPECTED_SHA256=3B07C214F9E0CF3AAD02E657BB2CBFC2C37274AF982C3968B79380A3CEC3E5EA"
set "SCRIPT_DIR=%~dp0"
set "MODS_DIR=%SCRIPT_DIR%"

if not "%~1"=="" set "MODS_DIR=%~f1"
if "%~1"=="" if exist "%SCRIPT_DIR%mods\elytraslot-fabric-*.jar" set "MODS_DIR=%SCRIPT_DIR%mods"

if not exist "%MODS_DIR%\elytraslot-fabric-*.jar" goto :missing_mod

set "TEMP_JAR=%MODS_DIR%\%JAR_NAME%.download"
set "NEW_JAR=%MODS_DIR%\%JAR_NAME%"
set "ELYTRASLOT_URL=%JAR_URL%"
set "ELYTRASLOT_TEMP=%TEMP_JAR%"

echo.
echo Updating Elytra Slot in:
echo %MODS_DIR%
echo.
echo Downloading version 2.0.1...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'Stop'; Invoke-WebRequest -Uri $env:ELYTRASLOT_URL -OutFile $env:ELYTRASLOT_TEMP"
if errorlevel 1 goto :download_failed

for /f "skip=1 delims=" %%H in ('certutil -hashfile "%TEMP_JAR%" SHA256') do (
    set "ACTUAL_SHA256=%%H"
    goto :hash_ready
)
:hash_ready
set "ACTUAL_SHA256=%ACTUAL_SHA256: =%"
if /I not "%ACTUAL_SHA256%"=="%EXPECTED_SHA256%" goto :checksum_failed

for /f "usebackq delims=" %%T in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Date -Format yyyyMMdd-HHmmss"`) do set "STAMP=%%T"
for %%F in ("%MODS_DIR%\elytraslot-fabric-*.jar") do (
    if exist "%%~fF" move /Y "%%~fF" "%%~fF.bak-%STAMP%" >nul
    if errorlevel 1 goto :backup_failed
)

move /Y "%TEMP_JAR%" "%NEW_JAR%" >nul
if errorlevel 1 goto :install_failed

echo.
echo Elytra Slot 2.0.1 is installed successfully.
echo Start Minecraft after the server has also been updated.
pause
exit /b 0

:missing_mod
echo.
echo No installed Elytra Slot jar was found.
echo Put this file in the Minecraft instance's mods folder, then run it again.
pause
exit /b 1

:download_failed
echo.
echo Download failed. Check the internet connection and try again.
pause
exit /b 1

:checksum_failed
del /q "%TEMP_JAR%" >nul 2>&1
echo.
echo Download verification failed. The installed mod was not changed.
pause
exit /b 1

:backup_failed
del /q "%TEMP_JAR%" >nul 2>&1
echo.
echo Could not replace the existing jar. Close Minecraft and the server, then try again.
pause
exit /b 1

:install_failed
echo.
echo Could not install the new jar. Your previous jar backups were kept.
pause
exit /b 1

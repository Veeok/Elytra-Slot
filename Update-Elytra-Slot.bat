@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Put this file in the Minecraft instance's mods folder, close Minecraft, then run it.
rem You may instead drag the mods folder onto this file.
set "JAR_NAME=elytraslot-fabric-26.1.2-2.0.2.jar"
set "JAR_URL=https://github.com/Veeok/Elytra-Slot/releases/download/v2.0.2/elytraslot-fabric-26.1.2-2.0.2.jar"
set "EXPECTED_SHA256=F67329C42381898497F3DA98D811638962895ACE8B05B8899E5881724A0131FB"
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
echo Downloading version 2.0.2...
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
echo Elytra Slot 2.0.2 is installed successfully.
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
call :restore_backups
echo.
echo Could not replace the existing jar. Close Minecraft and the server, then try again.
pause
exit /b 1

:install_failed
call :restore_backups
echo.
echo Could not install the new jar. The previous jar was restored.
pause
exit /b 1

:restore_backups
for %%F in ("%MODS_DIR%\elytraslot-fabric-*.jar.bak-%STAMP%") do (
    if exist "%%~fF" move /Y "%%~fF" "%%~dpnF" >nul
)
exit /b 0

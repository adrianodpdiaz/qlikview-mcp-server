@echo off
setlocal enabledelayedexpansion

rem Launches the QlikView MCP server jar, using a Java 21+ runtime it finds on this machine
rem (JAVA_HOME, then "java" on PATH), or downloading a private copy into ".\jre" if neither
rem qualifies. Requires curl.exe and tar.exe, both built into Windows since Windows 10 1803 /
rem Windows Server 2019 - no PowerShell, no separate installer.

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%qlikview-mcp-server.jar"
set "LOCAL_JRE_JAVA=%SCRIPT_DIR%jre\bin\java.exe"

if not exist "%JAR_PATH%" (
    echo [run-server] qlikview-mcp-server.jar not found next to this script: %JAR_PATH%
    exit /b 1
)

set "JAVA_EXE="

if exist "%LOCAL_JRE_JAVA%" (
    call :check_java21 "%LOCAL_JRE_JAVA%"
    if "!JAVA21_OK!"=="1" set "JAVA_EXE=%LOCAL_JRE_JAVA%"
)

if not defined JAVA_EXE if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        call :check_java21 "%JAVA_HOME%\bin\java.exe"
        if "!JAVA21_OK!"=="1" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)

if not defined JAVA_EXE (
    where java >nul 2>&1
    if not errorlevel 1 (
        call :check_java21 "java"
        if "!JAVA21_OK!"=="1" set "JAVA_EXE=java"
    )
)

if not defined JAVA_EXE (
    call :download_jre
    if not defined JAVA_EXE (
        echo [run-server] Could not find or download a Java 21+ runtime. Install Java 21 yourself
        echo [run-server] and either put it on PATH, set JAVA_HOME, or re-run this script.
        exit /b 1
    )
)

"%JAVA_EXE%" -jar "%JAR_PATH%" %*
exit /b %errorlevel%

:check_java21
rem Sets JAVA21_OK=1 if the given java.exe reports major version 21 or higher, else 0.
rem Writes "-version" output to a temp file rather than piping directly, because a quoted path
rem containing spaces (e.g. "C:\Program Files\Java\jdk-21\bin\java.exe") breaks a for /f command
rem string that both quotes the executable and pipes its output in the same expression.
set "JAVA21_OK=0"
set "VERSION_TMP=%TEMP%\qlikview-mcp-java-version-%RANDOM%.txt"
call "%~1" -version >"%VERSION_TMP%" 2>&1
for /f "tokens=3" %%v in ('findstr /i "version" "%VERSION_TMP%"') do set "RAW_VERSION=%%~v"
del /q "%VERSION_TMP%" 2>nul
set "RAW_VERSION=%RAW_VERSION:"=%"
for /f "delims=. tokens=1" %%m in ("%RAW_VERSION%") do set "MAJOR_VERSION=%%m"
if defined MAJOR_VERSION (
    if %MAJOR_VERSION% geq 21 set "JAVA21_OK=1"
)
exit /b 0

:download_jre
echo [run-server] No Java 21+ runtime found. Downloading a private copy into ".\jre" ...

rem Called by full path (not through PATH) so a different curl/tar that some other installed tool
rem (Git Bash, WSL, ...) puts earlier on PATH can never be picked up by mistake - both ship in
rem every Windows installation since Windows 10 1803 / Windows Server 2019.
set "CURL_EXE=%SystemRoot%\System32\curl.exe"
set "TAR_EXE=%SystemRoot%\System32\tar.exe"

if not exist "%CURL_EXE%" (
    echo [run-server] %CURL_EXE% not found; cannot download Java automatically. Install Java 21
    echo [run-server] yourself, or update Windows ^(curl.exe ships from Windows 10 1803 onward^).
    exit /b 0
)
if not exist "%TAR_EXE%" (
    echo [run-server] %TAR_EXE% not found; cannot extract the downloaded Java. Install Java 21
    echo [run-server] yourself, or update Windows ^(tar.exe ships from Windows 10 1803 onward^).
    exit /b 0
)

set "JRE_ZIP=%SCRIPT_DIR%jre-download.zip"
set "JRE_URL=https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"

"%CURL_EXE%" -L -f -o "%JRE_ZIP%" "%JRE_URL%"
if errorlevel 1 (
    echo [run-server] Download failed. Check your internet connection and try again, or install
    echo [run-server] Java 21 yourself.
    if exist "%JRE_ZIP%" del /q "%JRE_ZIP%"
    exit /b 0
)

if exist "%SCRIPT_DIR%jre" rmdir /s /q "%SCRIPT_DIR%jre"
mkdir "%SCRIPT_DIR%jre-extract"
"%TAR_EXE%" -xf "%JRE_ZIP%" -C "%SCRIPT_DIR%jre-extract"
if errorlevel 1 (
    echo [run-server] Extracting the downloaded Java failed.
    del /q "%JRE_ZIP%"
    exit /b 0
)
del /q "%JRE_ZIP%"

rem Adoptium zips contain one top-level folder (e.g. jdk-21.0.12+7-jre); move its contents up
rem so java.exe always lands at .\jre\bin\java.exe regardless of the exact version folder name.
for /d %%d in ("%SCRIPT_DIR%jre-extract\*") do (
    move "%%d" "%SCRIPT_DIR%jre" >nul
)
rmdir "%SCRIPT_DIR%jre-extract" 2>nul

if exist "%LOCAL_JRE_JAVA%" (
    echo [run-server] Java 21 downloaded to .\jre
    set "JAVA_EXE=%LOCAL_JRE_JAVA%"
) else (
    echo [run-server] Extraction did not produce a usable jre\bin\java.exe.
)
exit /b 0

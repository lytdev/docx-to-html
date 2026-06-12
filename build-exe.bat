@echo off
chcp 65001 >nul 2>&1

set "JDK21_HOME=C:\DevRepo\jdk\dragonwell-21.0.6.0.6+7-GA"
set "JPACKAGE=%JDK21_HOME%\bin\jpackage.exe"
set "MVN=C:\DevRepo\maven\apache-maven-3.8.8\bin\mvn.cmd"

echo ============================================
echo   docx2html exe build
echo   jpackage: %JPACKAGE%
echo   mvn: %MVN%
echo ============================================
echo.

if exist "target\dist\docx2html" (
    echo Cleaning previous build...
    rmdir /s /q "target\dist\docx2html"
)

"%MVN%" -Pnative package -Djpackage.path="%JPACKAGE%" %*
if errorlevel 1 (
    echo.
    echo [FAILED] Build error
    exit /b 1
)

echo.
echo [OK] Output: target\dist\docx2html\docx2html.exe

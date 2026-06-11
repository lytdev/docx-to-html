@echo off
chcp 65001 >nul 2>&1
setlocal

set JAVA_HOME=C:\DevRepo\jdk\dragonwell-21.0.6.0.6+7-GA
set MAVEN_CMD=

where mvn >nul 2>&1
if %errorlevel%==0 (
    set MAVEN_CMD=mvn
) else if exist C:\DevRepo\maven\apache-maven-3.8.8\bin\mvn.cmd (
    set MAVEN_CMD=C:\DevRepo\maven\apache-maven-3.8.8\bin\mvn.cmd
) else (
    echo [错误] 未找到 Maven，请确认 mvn 已加入 PATH 或安装在 C:\DevRepo\maven\apache-maven-3.8.8
    goto :fail
)

echo ============================================
echo   docx2html exe 构建
echo   JAVA_HOME: %JAVA_HOME%
echo ============================================
echo.

"%MAVEN_CMD%" -Pnative package %*
if %errorlevel% neq 0 (
    echo.
    echo [失败] 构建出错
    goto :fail
)

echo.
echo [成功] 输出目录: target\dist\docx2html\
echo        可执行文件: target\dist\docx2html\docx2html.exe
goto :end

:fail
endlocal
exit /b 1

:end
endlocal

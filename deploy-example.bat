@echo off
chcp 65001 >nul 2>&1

set JAVA_HOME=C:\DevRepo\OpenJdk\dragonwell-21.0.11

set MVN=C:\DevRepo\maven\apache-maven-3.8.8\bin\mvn.cmd

set MAVEN_GPG_PASSPHRASE=你的密码

echo ============================================
echo   docx2html release
echo   mvn: %MVN%
echo ============================================
echo.
mvn clean verify -Prelease
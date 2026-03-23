@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%target\latin-chat-1.0.0.jar"
set "LIB_DIR=%SCRIPT_DIR%target\lib"

where java >nul 2>nul
if errorlevel 1 (
  echo Java is not installed. Install Java 17+ and try again.
  exit /b 1
)

if not exist "%JAR_PATH%" (
  echo Jar not found: %JAR_PATH%
  echo Run: mvn -DskipTests clean package
  exit /b 1
)

if not exist "%LIB_DIR%" (
  echo Dependency directory not found: %LIB_DIR%
  echo Run: mvn -DskipTests clean package
  exit /b 1
)

java -cp "%JAR_PATH%;%LIB_DIR%\*" com.passivlingo.latinchat.AppLauncher

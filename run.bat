@echo off
setlocal
echo ==================================================================
echo   NALAM NAGAR EMERGENCY OPERATIONS - WEB SERVER LAUNCHER
echo ==================================================================

REM Locate working JDK compiler and runtime
if exist "E:\PROJECTS\bin\javac.exe" (
    set "JAVAC_EXE=E:\PROJECTS\bin\javac.exe"
    set "JAVA_EXE=E:\PROJECTS\bin\java.exe"
) else if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVAC_EXE=javac"
    set "JAVA_EXE=java"
)

echo Compiling Java backend and database drivers...
if not exist bin mkdir bin

"%JAVAC_EXE%" -cp "lib/*" -d bin -sourcepath src src/WebServer.java src/Main.java src/DbTest.java
if %errorlevel% neq 0 (
    echo [ERROR] Java compilation failed. Please verify JDBC JAR files in lib/
    pause
    exit /b %errorlevel%
)
echo.
echo Compilation successful.

REM Free port 8080 if previously left open
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do taskkill /f /pid %%a >nul 2>&1

echo Starting web server on http://localhost:8080/ ...
echo Press Ctrl+C in this window to stop the server anytime.
echo.
"%JAVA_EXE%" -cp "bin;lib/*" WebServer
pause

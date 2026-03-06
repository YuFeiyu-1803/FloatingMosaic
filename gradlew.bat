@rem Gradle wrapper for Windows
@if "%DEBUG%"=="" @echo off

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Find java.exe
set JAVA_EXE=java.exe
if "%JAVA_HOME%"=="" goto execute
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

:execute
"%JAVA_EXE%" -Dorg.gradle.appname=%APP_BASE_NAME% -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*

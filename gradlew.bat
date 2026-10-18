@ECHO OFF
SETLOCAL

SET "APP_HOME=%~dp0"
SET "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
SET "WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Bootstrapping Gradle wrapper...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path '%APP_HOME%gradle\wrapper' | Out-Null; Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
  IF ERRORLEVEL 1 EXIT /B 1
)

java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
ENDLOCAL

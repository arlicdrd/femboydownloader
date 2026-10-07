@echo off
REM Minimal Gradle Wrapper bootstrap for Windows.
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
) else (
  echo No system gradle found. Install Gradle 8.7 or open this project in Android Studio to generate the wrapper jar.
  exit /b 1
)

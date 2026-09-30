@echo off
chcp 65001 >nul
title 客脉图 - 构建并安装到模拟器

set "WS=%~dp0.."
set "SDK=C:\Users\25007\codex-toolchains\android-sdk"
set "JDK=C:\Users\25007\codex-toolchains\jdk\jdk-21.0.12.1+1"
set "GRADLE=C:\Users\25007\codex-toolchains\gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat"

set "JAVA_HOME=%JDK%"
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_USER_HOME=%WS%\.android"
set "ANDROID_AVD_HOME=%WS%\.android\avd"
set "ANDROID_EMULATOR_HOME=%WS%\.android"
set "ANDROID_PREFS_ROOT=%WS%\.android"
set "GRADLE_USER_HOME=%WS%\.gradle-home"
set "GRADLE_RO_DEP_CACHE=C:\Users\25007\codex-toolchains\gradle"
set "PATH=%JDK%\bin;%PATH%"

echo.
echo   [1/3] 正在编译「客脉图」...
echo.
call "%GRADLE%" -p "%WS%\KemaiTu" :app:assembleDebug --offline -q
if errorlevel 1 (
  echo.
  echo   [x] 编译失败，请把上面的错误发给开发者。
  pause
  exit /b 1
)

echo   [2/3] 等待模拟器就绪...
"%SDK%\platform-tools\adb.exe" wait-for-device
:waitboot
for /f "tokens=*" %%i in ('"%SDK%\platform-tools\adb.exe" shell getprop sys.boot_completed 2^>nul') do set BOOT=%%i
if not "%BOOT%"=="1" (
  timeout /t 3 >nul
  goto waitboot
)

echo   [3/3] 安装并启动...
"%SDK%\platform-tools\adb.exe" install -r "%WS%\KemaiTu\app\build\outputs\apk\debug\app-debug.apk"
"%SDK%\platform-tools\adb.exe" shell am start -n com.kemai.app/.MainActivity

echo.
echo   完成！应用已在模拟器中启动。
echo.
pause

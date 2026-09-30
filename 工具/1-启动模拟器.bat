@echo off
chcp 65001 >nul
title 客脉图 - 启动模拟器

set "WS=%~dp0.."
set "SDK=C:\Users\25007\codex-toolchains\android-sdk"
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_USER_HOME=%WS%\.android"
set "ANDROID_AVD_HOME=%WS%\.android\avd"
set "ANDROID_EMULATOR_HOME=%WS%\.android"
set "ANDROID_PREFS_ROOT=%WS%\.android"

echo.
echo   正在启动「客脉图」模拟器（Pixel 7 / Android 16）...
echo   首次启动约需 1-2 分钟，请耐心等待手机画面出现。
echo.
echo   模拟器里的操作方式和真机一致：鼠标点击=手指点击，
echo   拖动=滑动，电脑键盘可直接输入文字。
echo.

start "" "%SDK%\emulator\emulator.exe" -avd KemaiTu -no-boot-anim -no-snapshot-save

echo   模拟器已在后台启动，本窗口可以关闭。
timeout /t 5 >nul

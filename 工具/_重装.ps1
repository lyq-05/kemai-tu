# 客脉图：一键重新构建 + 安装到模拟器 + 启动
# 用法（在「工具」目录下）： powershell -ExecutionPolicy Bypass -File _重装.ps1
# 说明：这个脚本在我（开发方）的机器上验证通过；你自己打包时用 2-构建并安装.bat 即可。

$ws  = "D:\25007\deepseek-workspace\Application_development_1"
$sdk = "C:\Users\25007\codex-toolchains\android-sdk"

$env:JAVA_HOME             = "C:\Users\25007\codex-toolchains\jdk\jdk-21.0.12.1+1"
$env:ANDROID_HOME          = $sdk
$env:ANDROID_SDK_ROOT      = $sdk
$env:ANDROID_USER_HOME     = "$ws\.android"
$env:ANDROID_AVD_HOME      = "$ws\.android\avd"
$env:ANDROID_EMULATOR_HOME = "$ws\.android"
$env:ANDROID_PREFS_ROOT    = "$ws\.android"
$env:GRADLE_USER_HOME      = "C:\Users\25007\codex-toolchains\gradle"

$gradle = "C:\Users\25007\codex-toolchains\gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat"
$adb    = "$sdk\platform-tools\adb.exe"
$apk    = "$ws\KemaiTu\app\build\outputs\apk\debug\app-debug.apk"

function Show-Wrapped([string]$text, [string]$color = "Red") {
    for ($k = 0; $k -lt $text.Length; $k += 76) {
        Write-Host ("  " + $text.Substring($k, [Math]::Min(76, $text.Length - $k))) -ForegroundColor $color
    }
}

Write-Host "[1/3] 编译..." -ForegroundColor Cyan
$out = & $gradle -p "$ws\KemaiTu" :app:assembleDebug --offline --console=plain 2>&1 | Out-String
$lines = $out -split "`r?`n"
foreach ($l in $lines) {
    if ($l -match "^e: ")          { Show-Wrapped $l "Red" }
    elseif ($l -match "BUILD SUCCESSFUL") { Show-Wrapped $l "Green" }
    elseif ($l -match "BUILD FAILED|FAILURE:") { Show-Wrapped $l "Red" }
}
if ($LASTEXITCODE -ne 0) { Write-Host "编译失败" -ForegroundColor Red; exit 1 }

Write-Host "[2/3] 安装..." -ForegroundColor Cyan
& $adb install -r $apk | Out-Host

Write-Host "[3/3] 启动..." -ForegroundColor Cyan
& $adb shell am start -n com.kemai.app/.MainActivity | Out-Null
Write-Host "完成" -ForegroundColor Green

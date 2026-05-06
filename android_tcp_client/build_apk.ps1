$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Sdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($Sdk)) {
    $Sdk = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrWhiteSpace($Sdk)) {
    $Sdk = "E:\Android_Studio_SDK"
}

$BuildTools = Join-Path $Sdk "build-tools\36.1.0"
$PlatformJar = Join-Path $Sdk "platforms\android-36\android.jar"
$Aapt = Join-Path $BuildTools "aapt.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$Zipalign = Join-Path $BuildTools "zipalign.exe"
$Apksigner = Join-Path $BuildTools "apksigner.bat"

foreach ($Tool in @($Aapt, $D8, $Zipalign, $Apksigner, $PlatformJar)) {
    if (!(Test-Path $Tool)) {
        throw "缺少 Android 构建工具: $Tool"
    }
}

$Build = Join-Path $Root "build"
$Gen = Join-Path $Build "gen"
$Classes = Join-Path $Build "classes"
$Dex = Join-Path $Build "dex"
$ClassesJar = Join-Path $Build "classes.jar"
$UnsignedApk = Join-Path $Build "unsigned.apk"
$AlignedApk = Join-Path $Build "aligned.apk"
$SignedApk = Join-Path $Build "TCP调试助手.apk"
$Keystore = Join-Path $Build "debug.keystore"

function Assert-LastCommand($Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step 失败，退出码: $LASTEXITCODE"
    }
}

New-Item -ItemType Directory -Force -Path $Build | Out-Null
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $Gen, $Classes, $Dex
New-Item -ItemType Directory -Force -Path $Gen, $Classes, $Dex | Out-Null
Remove-Item -Force -ErrorAction SilentlyContinue $ClassesJar, $UnsignedApk, $AlignedApk, $SignedApk

& $Aapt package -f -m -J $Gen -M (Join-Path $Root "AndroidManifest.xml") -S (Join-Path $Root "res") -I $PlatformJar
Assert-LastCommand "生成 R.java"

$JavaFiles = @()
$JavaFiles += Get-ChildItem -Recurse -Filter *.java (Join-Path $Root "src") | ForEach-Object { $_.FullName }
$JavaFiles += Get-ChildItem -Recurse -Filter *.java $Gen | ForEach-Object { $_.FullName }

& javac -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath $PlatformJar -d $Classes $JavaFiles
Assert-LastCommand "Java 编译"

& jar cf $ClassesJar -C $Classes .
Assert-LastCommand "打包 classes.jar"

& $D8 --lib $PlatformJar --output $Dex $ClassesJar
Assert-LastCommand "生成 classes.dex"

& $Aapt package -f -M (Join-Path $Root "AndroidManifest.xml") -S (Join-Path $Root "res") -I $PlatformJar -F $UnsignedApk
Assert-LastCommand "生成未签名 APK"

Push-Location $Dex
try {
    & $Aapt add -f $UnsignedApk "classes.dex"
    Assert-LastCommand "写入 classes.dex"
} finally {
    Pop-Location
}

& $Zipalign -f -p 4 $UnsignedApk $AlignedApk
Assert-LastCommand "zipalign"

if (!(Test-Path $Keystore)) {
    & keytool -genkeypair `
        -keystore $Keystore `
        -storepass android `
        -keypass android `
        -alias androiddebugkey `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Android Debug,O=Practice,C=CN"
    Assert-LastCommand "生成签名证书"
}

& $Apksigner sign `
    --ks $Keystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $SignedApk `
    $AlignedApk
Assert-LastCommand "APK 签名"

& $Apksigner verify --verbose $SignedApk
Assert-LastCommand "APK 签名验证"

Write-Host "构建成功: $SignedApk"

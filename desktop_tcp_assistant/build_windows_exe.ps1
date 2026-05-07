$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $Root
try {
    $ProcessName = "TCP调试助手桌面版"
    if (Get-Process -Name $ProcessName -ErrorAction SilentlyContinue) {
        throw "请先关闭正在运行的 $ProcessName.exe 后再打包"
    }

    python -m PyInstaller `
        --noconfirm `
        --clean `
        --windowed `
        --onefile `
        --name "TCP调试助手桌面版" `
        "tcp_assistant.py"
    if ($LASTEXITCODE -ne 0) {
        throw "PyInstaller 打包失败，退出码: $LASTEXITCODE"
    }

    Write-Host "构建成功: $Root\dist\TCP调试助手桌面版.exe"
} finally {
    Pop-Location
}

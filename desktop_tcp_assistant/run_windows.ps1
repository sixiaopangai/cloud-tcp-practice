$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $Root
try {
    python .\tcp_assistant.py
} finally {
    Pop-Location
}

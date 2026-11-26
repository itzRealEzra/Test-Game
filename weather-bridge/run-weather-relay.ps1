$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$VenvPython = Join-Path $ScriptDir ".venv\Scripts\python.exe"

if (-not (Test-Path -LiteralPath $VenvPython)) {
    python -m venv (Join-Path $ScriptDir ".venv")
    & $VenvPython -m pip install --upgrade pip
    & $VenvPython -m pip install -r (Join-Path $ScriptDir "requirements.txt")
}

if (-not (Test-Path -LiteralPath (Join-Path $ScriptDir ".env"))) {
    Copy-Item -LiteralPath (Join-Path $ScriptDir ".env.example") `
        -Destination (Join-Path $ScriptDir ".env")
    Write-Host "Created weather-bridge/.env from the template. Fill in the Discord IDs and bot token, then restart this script."
}

& $VenvPython (Join-Path $ScriptDir "main.py")

[CmdletBinding()]
param([ValidateSet('Bootstrap', 'Run', 'Quality', 'Models')][string]$Action = 'Bootstrap')

$ErrorActionPreference = 'Stop'
$blameraRoot = Split-Path $PSScriptRoot -Parent
$blameraUvDir = Join-Path $blameraRoot '.tools\uv\0.13.5'
$blameraUv = Join-Path $blameraUvDir 'uv.exe'
$env:UV_CACHE_DIR = Join-Path $blameraRoot '.tools\uv-cache'
$env:UV_PYTHON_INSTALL_DIR = Join-Path $blameraRoot '.tools\python'
$env:UV_PYTHON_BIN_DIR = Join-Path $blameraRoot '.tools\python-bin'
$env:UV_PYTHON_INSTALL_BIN = '0'
$env:UV_PYTHON_NO_REGISTRY = '1'

if ($Action -eq 'Bootstrap') {
    New-Item -ItemType Directory -Force $blameraUvDir | Out-Null
    $blameraArchive = Join-Path $blameraUvDir 'uv.zip'
    if (-not (Test-Path -LiteralPath $blameraArchive)) {
        Invoke-WebRequest 'https://github.com/astral-sh/uv/releases/download/0.13.5/uv-x86_64-pc-windows-msvc.zip' -OutFile $blameraArchive
    }
    $blameraExpected = 'a38b8d1902b7cfbc76b423ba1bb456f623f42064cdc72628b6075b89afd1019a'
    if ((Get-FileHash $blameraArchive -Algorithm SHA256).Hash -ne $blameraExpected) {
        throw 'Archive uv differente : arret, aucun binaire execute.'
    }
    Expand-Archive -LiteralPath $blameraArchive -DestinationPath $blameraUvDir -Force
    & $blameraUv python install 3.12.14 --no-registry --no-bin
    if ($LASTEXITCODE -ne 0) { throw 'Installation Python autonome en echec.' }
    & $blameraUv sync --project (Join-Path $blameraRoot 'pc') --locked --python 3.12.14 --managed-python
    exit $LASTEXITCODE
}

$blameraPython = Join-Path $blameraRoot 'pc\.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $blameraPython)) { throw 'Executer scripts/pc.ps1 Bootstrap avant cette commande.' }
if ($Action -eq 'Quality') {
    & (Join-Path $PSScriptRoot 'quality.ps1') -PythonExe $blameraPython
    exit $LASTEXITCODE
}
Push-Location (Join-Path $blameraRoot 'pc')
$blameraPreviousPythonPath = $env:PYTHONPATH
try {
    $blameraSourcePath = Join-Path $blameraRoot 'pc\src'
    $env:PYTHONPATH = if ([string]::IsNullOrEmpty($blameraPreviousPythonPath)) { $blameraSourcePath } else { "$blameraSourcePath$([IO.Path]::PathSeparator)$blameraPreviousPythonPath" }
    if ($Action -eq 'Models') {
        & $blameraPython -m blamera_pc.face_models --download
    } else {
        & $blameraPython -m streamlit run src/blamera_pc/app.py
    }
    $blameraExit = $LASTEXITCODE
} finally {
    $env:PYTHONPATH = $blameraPreviousPythonPath
    Pop-Location
}
exit $blameraExit

[CmdletBinding()]
param(
    [string]$PythonExe = $env:BLAMERA_PYTHON,
    [string]$BaseRef = $env:QUALITY_BASE_REF
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($PythonExe)) {
    $blameraPcPython = Join-Path (Split-Path $PSScriptRoot -Parent) 'pc\.venv\Scripts\python.exe'
    $PythonExe = if (Test-Path -LiteralPath $blameraPcPython) { $blameraPcPython } else { 'python' }
}
try {
    & $PythonExe -c 'import sys; assert sys.version_info >= (3, 12), "Python 3.12+ required"'
    if ($LASTEXITCODE -ne 0) { throw 'Version Python incompatible' }
} catch {
    throw 'Indiquer un Python 3.12+ fonctionnel avec -PythonExe ou BLAMERA_PYTHON ; un alias WindowsApps ne suffit pas.'
}

$blameraArguments = @((Join-Path $PSScriptRoot 'quality.py'))
if (-not [string]::IsNullOrWhiteSpace($BaseRef)) { $blameraArguments += @('--base-ref', $BaseRef) }
& $PythonExe @blameraArguments
exit $LASTEXITCODE

[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments
)

$ErrorActionPreference = 'Stop'
$blameraRoot = Split-Path -Parent $PSScriptRoot
$blameraJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
$blameraSdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'

if (-not (Test-Path -LiteralPath "$blameraJdk\bin\java.exe")) {
    throw "JDK introuvable : $blameraJdk"
}
if (-not (Test-Path -LiteralPath "$blameraSdk\platforms\android-36")) {
    throw "SDK Android 36 introuvable : $blameraSdk"
}

$env:JAVA_HOME = $blameraJdk
$env:ANDROID_HOME = $blameraSdk
$env:GRADLE_USER_HOME = Join-Path $blameraRoot '.gradle-user-home'

Push-Location $blameraRoot
try {
    & '.\gradlew.bat' @GradleArguments
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

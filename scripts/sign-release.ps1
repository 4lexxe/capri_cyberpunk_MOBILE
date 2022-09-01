param(
    [Parameter(Mandatory)][string]$InputApk,
    [Parameter(Mandatory)][string]$OutputApk,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$LegacyStorePassword = 'android'
)
$ErrorActionPreference = 'Stop'
$releaseRoot = Split-Path -Parent $PSScriptRoot
$properties = @{}
Get-Content -LiteralPath (Join-Path $releaseRoot 'signing.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $properties[$Matches[1]] = $Matches[2] }
}
if (!$SdkPath) { throw 'Indica -SdkPath con el Android SDK.' }
$signer = Join-Path $SdkPath 'build-tools/35.0.0/apksigner.bat'
$legacy = Join-Path $releaseRoot 'signing/capri-legacy.keystore'
$lineage = Join-Path $releaseRoot 'signing.lineage'
if (!(Test-Path -LiteralPath $legacy)) { throw 'Falta la clave anterior en signing/capri-legacy.keystore. No generes otra: debe ser la que firmó las versiones de prueba.' }
if (!(Test-Path -LiteralPath $lineage)) { throw 'Falta signing.lineage, el historial público de rotación de certificados.' }
$inputPath = (Resolve-Path -LiteralPath $InputApk).Path
$outputPath = [IO.Path]::GetFullPath($OutputApk)
if ($inputPath -eq $outputPath) { throw 'Usa un archivo de salida distinto para conservar el original.' }
$env:CAPRI_SIGN_STORE_PASS = $properties['storePassword']
$env:CAPRI_SIGN_KEY_PASS = $properties['keyPassword']
$env:CAPRI_LEGACY_SIGN_PASS = $LegacyStorePassword
try {
    & $signer sign --ks $legacy --ks-key-alias androiddebugkey --ks-pass env:CAPRI_LEGACY_SIGN_PASS --key-pass env:CAPRI_LEGACY_SIGN_PASS --next-signer --ks (Join-Path $releaseRoot $properties['storeFile']) --ks-key-alias $properties['keyAlias'] --ks-pass env:CAPRI_SIGN_STORE_PASS --key-pass env:CAPRI_SIGN_KEY_PASS --lineage $lineage --rotation-min-sdk-version 28 --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $outputPath $inputPath
    if ($LASTEXITCODE -ne 0) { throw 'Falló la firma con migración.' }
    & $signer verify --verbose $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'La firma del APK no pasó la verificación.' }
} finally {
    Remove-Item Env:CAPRI_SIGN_STORE_PASS, Env:CAPRI_SIGN_KEY_PASS, Env:CAPRI_LEGACY_SIGN_PASS
    $properties = $null
}

param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$signingRoot = Split-Path -Parent $PSScriptRoot
$signingFolder = Join-Path $signingRoot 'signing'
$signingProperties = Join-Path $signingRoot 'signing.properties'
if ((Test-Path -LiteralPath $signingProperties) -or (Test-Path -LiteralPath (Join-Path $signingFolder 'capri-release.p12'))) {
    throw 'La firma ya existe. No se reemplaza una clave de distribución.'
}
if (!$JavaHome) { throw 'Indica -JavaHome con JDK 17 o posterior.' }
New-Item -ItemType Directory -Force -Path $signingFolder | Out-Null
$signingRandom = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($signingRandom)
$signingPassword = [Convert]::ToBase64String($signingRandom)
$env:CAPRI_LOCAL_SIGNING_PASSWORD = $signingPassword
try {
    & (Join-Path $JavaHome 'bin/keytool.exe') -genkeypair -alias capri-release -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12 -keystore (Join-Path $signingFolder 'capri-release.p12') -storepass:env CAPRI_LOCAL_SIGNING_PASSWORD -keypass:env CAPRI_LOCAL_SIGNING_PASSWORD -dname 'CN=Capri Android, OU=Distribution, O=Capri Project'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la firma.' }
    @("storeFile=signing/capri-release.p12", "storePassword=$signingPassword", 'keyAlias=capri-release', "keyPassword=$signingPassword") | Set-Content -LiteralPath $signingProperties -Encoding utf8NoBOM
    Write-Output 'Firma local creada. Guarda signing/ y signing.properties en una copia privada; nunca los publiques.'
} finally { Remove-Item Env:CAPRI_LOCAL_SIGNING_PASSWORD; $signingPassword = $null }

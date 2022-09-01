param(
    [string]$JavaHome,
    [string]$SdkPath,
    [string]$NdkPath,
    [string]$CmakePath
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    $settingsUpdates = @{}
    if ($SdkPath) { $settingsUpdates['sdk.dir'] = $SdkPath }
    if ($NdkPath) { $settingsUpdates['ndk.dir'] = $NdkPath }
    if ($CmakePath) { $settingsUpdates['cmake.dir'] = $CmakePath }
    if ($settingsUpdates.Count -gt 0) {
        $localLines = if (Test-Path -LiteralPath 'local.properties') { @(Get-Content -LiteralPath 'local.properties') } else { @() }
        foreach ($settingName in $settingsUpdates.Keys) {
            $resolvedSetting = (Resolve-Path -LiteralPath $settingsUpdates[$settingName]).Path.Replace('\', '/').Replace(':', '\:')
            $pattern = '^' + [regex]::Escape($settingName) + '='
            $localLines = @($localLines | Where-Object { $_ -notmatch $pattern }) + "$settingName=$resolvedSetting"
        }
        Set-Content -LiteralPath 'local.properties' -Value $localLines -Encoding utf8
    }
    & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o una verificación. Revisá la salida de Gradle.' }
    New-Item -ItemType Directory -Force -Path 'artifacts' | Out-Null
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination 'artifacts/Capri-Android-1.0.0-debug.apk'
    Get-FileHash -LiteralPath 'artifacts/Capri-Android-1.0.0-debug.apk' -Algorithm SHA256
} finally { Pop-Location }


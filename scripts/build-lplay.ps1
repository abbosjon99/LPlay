<#
.SYNOPSIS
  Builds the signed LPlay release APK (CarLink for the Leapmotor C01) with a keystore you point at.

.DESCRIPTION
  Wraps `gradlew :app:assembleRelease`. Signing values are handed to Gradle through environment variables of this
  process only (ANDROID_KEYSTORE_PATH / _PASSWORD, ANDROID_KEY_ALIAS / _PASSWORD, see docs/BUILDING.md); they are read
  from -KeystoreInfoFile (an `alias=...` / `password=...` file) and are never printed.

  The CarPlay accessory identity is NOT part of the source. Without -AuthAssetsDir the APK builds, but it cannot start
  CarPlay ("unable to load CarPlay authentication"). See docs/LPLAY.md.

  Windows quirk: the Android aidl tool copies its command line into a generated Java comment, and a path that contains
  "\u" (for example C:\Users\...) becomes an illegal unicode escape for the Android Auto engine's annotation processing.
  When the checkout, the Gradle home or the temp folder path contains one, the script maps a spare drive letter to the
  checkout's parent folder for the build (subst) and removes it afterwards. -NoSubst turns that off.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\build-lplay.ps1 `
    -Keystore C:\keys\signing-key.jks -KeystoreInfoFile C:\keys\signing-key.txt -RunTests
#>
param(
    [Parameter(Mandatory = $true)][string]$Keystore,
    [Parameter(Mandatory = $true)][string]$KeystoreInfoFile,
    [string]$AuthAssetsDir = "",
    [string]$Jdk = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [switch]$RunTests,
    [switch]$NoSubst,
    [string]$Output = ""
)

$ErrorActionPreference = "Stop"
# $PSScriptRoot is empty in a parameter default under Windows PowerShell 5.1, so it is resolved here.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = (Resolve-Path (Join-Path $scriptDir "..")).Path
if (-not $Output) { $Output = Join-Path $root "dist" }
if (-not $Jdk -or -not (Test-Path (Join-Path $Jdk "bin\java.exe"))) { throw "Set -Jdk or JAVA_HOME to a JDK 25 directory." }
if (-not $AndroidSdk -or -not (Test-Path $AndroidSdk)) { throw "Set -AndroidSdk or ANDROID_HOME to the Android SDK directory." }
if (-not (Test-Path $Keystore)) { throw "Keystore not found: $Keystore" }
if (-not (Test-Path $KeystoreInfoFile)) { throw "Keystore info file not found: $KeystoreInfoFile" }

$info = Get-Content $KeystoreInfoFile | ConvertFrom-StringData
if (-not $info.alias -or -not $info.password) { throw "The keystore info file needs alias= and password= lines." }

$env:JAVA_HOME = $Jdk
$env:ANDROID_HOME = $AndroidSdk
$env:Path = "$Jdk\bin;$env:Path"
$env:ANDROID_KEYSTORE_PATH = (Resolve-Path $Keystore).Path
$env:ANDROID_KEY_ALIAS = $info.alias
$env:ANDROID_KEYSTORE_PASSWORD = $info.password
$env:ANDROID_KEY_PASSWORD = $info.password
if ($AuthAssetsDir) {
    foreach ($name in "identity.pk8", "certificate.p7b") {
        if (-not (Test-Path (Join-Path $AuthAssetsDir "offline-mfi\$name"))) { throw "Missing $AuthAssetsDir\offline-mfi\$name" }
    }
    $env:DIPLAY_AUTH_ASSETS_DIR = (Resolve-Path $AuthAssetsDir).Path
} else {
    Remove-Item Env:DIPLAY_AUTH_ASSETS_DIR -ErrorAction SilentlyContinue
    Write-Warning "No -AuthAssetsDir: this APK has no CarPlay accessory identity and cannot start CarPlay."
}

# The build runs from a drive-letter alias when a path would put "\u" into aidl's generated comment.
$drive = $null
$work = $root
if (-not $NoSubst -and ($root -match '\\[uU]' -or "$env:GRADLE_USER_HOME" -match '\\[uU]' -or "$env:TEMP" -match '\\[uU]')) {
    $parent = Split-Path -Parent $root
    $letter = ('W', 'V', 'U', 'T', 'S', 'R', 'Q', 'P' | Where-Object { -not (Test-Path "$($_):\") } | Select-Object -First 1)
    if (-not $letter) { throw "No spare drive letter for the build alias; free one or pass -NoSubst and build from a path without \u." }
    & subst "$($letter):" $parent
    $drive = "$($letter):"
    $work = "$drive\$(Split-Path -Leaf $root)"
    # A Gradle home or temp folder that is already outside C:\Users keeps its caches; the others move to the alias.
    if (-not $env:GRADLE_USER_HOME -or $env:GRADLE_USER_HOME -match '\\[uU]') { $env:GRADLE_USER_HOME = "$drive\lplay-gradle-home" }
    if (-not $env:TEMP -or $env:TEMP -match '\\[uU]') {
        New-Item -ItemType Directory -Force "$drive\lplay-tmp" | Out-Null
        $env:TEMP = "$drive\lplay-tmp"; $env:TMP = "$drive\lplay-tmp"
    }
    Write-Host "Building from $work (drive alias $drive for $parent)."
}

Push-Location $work
try {
    $tasks = @()
    if ($RunTests) { $tasks += ":lplay-cluster:testDebugUnitTest", ":carlink-core:testDebugUnitTest", ":app:testDebugUnitTest" }
    $tasks += ":app:assembleRelease"
    & "$work\gradlew.bat" --console=plain @tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }

    $apk = Join-Path $work "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path $apk)) { throw "Expected APK not found: $apk" }
    $version = (Select-String -Path (Join-Path $work "app\build.gradle.kts") -Pattern 'versionName = "([^"]+)"' | Select-Object -First 1).Matches[0].Groups[1].Value
    New-Item -ItemType Directory -Force $Output | Out-Null
    $final = Join-Path (Resolve-Path $Output).Path "LPlay-$version.apk"
    Copy-Item $apk $final -Force

    $apksigner = Get-ChildItem (Join-Path $AndroidSdk "build-tools") -Recurse -Filter apksigner.bat |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($apksigner) {
        # --print-certs shows only certificate digests, never key material.
        & $apksigner.FullName verify --verbose --print-certs $final | Select-String -Pattern "Verifies|Verified using|certificate SHA-256 digest|Number of signers"
    }
    Write-Host "APK:    $final"
    Write-Host ("SHA256: " + (Get-FileHash $final -Algorithm SHA256).Hash)
} finally {
    Pop-Location
    Remove-Item Env:ANDROID_KEYSTORE_PASSWORD, Env:ANDROID_KEY_PASSWORD, Env:ANDROID_KEY_ALIAS, Env:ANDROID_KEYSTORE_PATH -ErrorAction SilentlyContinue
    if ($drive) { & subst $drive /d | Out-Null }
}

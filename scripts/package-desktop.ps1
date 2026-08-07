# ============================================================
#  Show desktop packaging (Windows + jpackage)
#  Run from repo root:
#    .\scripts\package-desktop.ps1
#    .\scripts\package-desktop.bat
#
#  - Build machine: needs JDK 17+ (jpackage/jlink) + Maven
#  - Target machine after install: NO JDK required
#    (runtime is bundled inside the installer)
# ============================================================

$ErrorActionPreference = "Stop"
# Native tools (java/mvn/jpackage) often write to stderr; do not treat as terminating.
$PSNativeCommandUseErrorActionPreference = $false
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# Repo root (parent of scripts/)
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# -------------------- Config (edit as needed) --------------------
# SKU：本地买断版（SQLite，无云 MySQL / 无 SaaS / 无小程序）
$AppName        = "Show"
$AppVersion     = "1.0.0"
$AppVendor      = "DDMO"
$AppDescription = "Show local buyout edition - SQLite, offline, no mini-program"
$MainJar        = "ddmo-1.0.0.jar"
$MainClass      = "org.springframework.boot.loader.JarLauncher"
# Package type: msi (recommended) or exe (exe does not need WiX)
$PackageType    = "msi"
# Icon at project root (optional)
$IconPath       = "logo.ico"
$DistDir        = "dist"
$StagingDir     = "build\jpackage-input"
$AppImageDir    = "build\app-image"
$RuntimeDir     = "build\runtime"
# -----------------------------------------------------------------

Set-Location -LiteralPath $ProjectRoot
$Host.UI.RawUI.WindowTitle = "Show desktop package - $AppName $AppVersion"

function Write-Step([string]$msg) { Write-Host $msg -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host $msg -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host $msg -ForegroundColor Yellow }
function Write-Err([string]$msg)  { Write-Host $msg -ForegroundColor Red }

function Test-Cmd([string]$name) {
    return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

function Get-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "jmods"))) {
        return $env:JAVA_HOME
    }
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) { return $null }
    # .../bin/java.exe -> JDK root
    $home = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
    if (Test-Path (Join-Path $home "jmods")) { return $home }
    # Sometimes java is a shim; prefer JAVA_HOME
    return $null
}

Write-Host ""
Write-Host "============================================"
Write-Host "  $AppName LOCAL buyout packaging"
Write-Host "  version: $AppVersion  type: $PackageType"
Write-Host "  project: $ProjectRoot"
Write-Host "  data: SQLite only (no cloud MySQL / no SaaS)"
Write-Host "  target PCs: NO JDK needed (bundled runtime)"
Write-Host "============================================"
Write-Host ""

# ---------- PATH helpers (WiX may be installed but shell not refreshed) ----------
$wixBin = "${env:ProgramFiles(x86)}\WiX Toolset v3.14\bin"
if (Test-Path $wixBin) {
    if ($env:Path -notlike "*$wixBin*") {
        $env:Path = "$wixBin;$env:Path"
    }
}

# ---------- Environment checks ----------
if (-not (Test-Cmd "java")) {
    Write-Err "[ERROR] java not found. Build machine needs JDK 17+ (PATH / JAVA_HOME)."
    exit 1
}
if (-not (Test-Cmd "jpackage")) {
    Write-Err "[ERROR] jpackage not found. Use a full JDK 17+ (not JRE)."
    exit 1
}
if (-not (Test-Cmd "jlink")) {
    Write-Err "[ERROR] jlink not found. Use a full JDK 17+ (not JRE)."
    exit 1
}
if (-not (Test-Cmd "mvn")) {
    Write-Err "[ERROR] mvn not found. Install Maven and set PATH / MAVEN_HOME."
    exit 1
}

$jdkHome = Get-JdkHome
if (-not $jdkHome) {
    Write-Err "[ERROR] Cannot find JDK root with jmods/."
    Write-Err "        Set JAVA_HOME to a full JDK 17+ install, e.g. C:\Program Files\Java\jdk-17"
    exit 1
}
$jmods = Join-Path $jdkHome "jmods"
Write-Ok "[OK] JDK for bundling: $jdkHome"

if ($PackageType -eq "msi") {
    if (-not (Test-Cmd "candle")) {
        Write-Warn "[WARN] WiX Toolset (candle/light) not in PATH."
        Write-Warn "       MSI requires WiX 3.x. Or set PackageType = 'exe'."
        Write-Host ""
    } else {
        Write-Ok "[OK] WiX candle found: $((Get-Command candle).Source)"
    }
}

Write-Step "[check] Java / jpackage / jlink / Maven available"
cmd /c "java -version 2>&1" | Select-Object -First 1 | ForEach-Object { Write-Host $_ }
Write-Host ""

# ---------- 1. Maven package ----------
Write-Step "[1/4] mvn clean package -DskipTests ..."
$mvnExit = 0
try {
    & mvn clean package -DskipTests
    $mvnExit = $LASTEXITCODE
} catch {
    Write-Err "[ERROR] Maven package failed: $_"
    exit 1
}
if ($mvnExit -ne 0) {
    Write-Err "[ERROR] Maven package failed. Exit code: $mvnExit"
    exit 1
}

$jarPath = Join-Path "target" $MainJar
if (-not (Test-Path -LiteralPath $jarPath)) {
    Write-Err "[ERROR] Main jar not found: $jarPath"
    exit 1
}
Write-Ok "[1/4] Maven done: $jarPath"
Write-Host ""

# ---------- 2. Staging (only main jar) ----------
Write-Step "[2/4] Prepare jpackage input dir..."
if (Test-Path -LiteralPath $StagingDir) {
    Remove-Item -LiteralPath $StagingDir -Recurse -Force
}
New-Item -ItemType Directory -Path $StagingDir -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $StagingDir $MainJar) -Force

if (-not (Test-Path -LiteralPath $DistDir)) {
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
}

# ---------- 3. jlink: custom runtime (bundled into installer, no system JDK needed) ----------
Write-Step "[3/4] jlink create bundled runtime (self-contained, no JDK on target)..."
if (Test-Path -LiteralPath $RuntimeDir) {
    Remove-Item -LiteralPath $RuntimeDir -Recurse -Force
}

# Modules for Spring Boot Web + JDBC + AWT Desktop/Tray + crypto/charsets
$modules = @(
    "java.base",
    "java.desktop",
    "java.logging",
    "java.management",
    "java.naming",
    "java.prefs",
    "java.scripting",
    "java.security.jgss",
    "java.security.sasl",
    "java.sql",
    "java.transaction.xa",
    "java.xml",
    "java.datatransfer",
    "java.instrument",
    "java.net.http",
    "java.rmi",
    "jdk.crypto.ec",
    "jdk.crypto.cryptoki",
    "jdk.httpserver",
    "jdk.unsupported",
    "jdk.unsupported.desktop",
    "jdk.zipfs",
    "jdk.charsets",
    "jdk.localedata",
    "jdk.management",
    "jdk.crypto.mscapi"
) -join ","

$jlinkArgs = @(
    "--module-path", $jmods,
    "--add-modules", $modules,
    "--strip-debug",
    "--no-header-files",
    "--no-man-pages",
    "--compress=2",
    "--output", $RuntimeDir
)

& jlink @jlinkArgs
if ($LASTEXITCODE -ne 0) {
    Write-Err "[ERROR] jlink failed. Ensure JAVA_HOME points to full JDK 17+."
    exit 1
}

$bundledJava = Join-Path $RuntimeDir "bin\java.exe"
if (-not (Test-Path -LiteralPath $bundledJava)) {
    Write-Err "[ERROR] Bundled runtime missing java.exe: $bundledJava"
    exit 1
}
Write-Ok "[3/4] Runtime ready: $RuntimeDir"
Write-Host ""

# ---------- 4. jpackage with --runtime-image (embeds runtime in MSI/EXE) ----------
Write-Step "[4/4] jpackage -> $PackageType (with embedded runtime)..."
Write-Host "Tip: set PackageType = 'exe' in this script if you want an exe instead of msi."
Write-Host ""

if (Test-Path -LiteralPath $AppImageDir) {
    Remove-Item -LiteralPath $AppImageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $AppImageDir -Force | Out-Null

# Remove previous installer of same name if present (avoid file lock confusion)
Get-ChildItem -Path $DistDir -Filter "$AppName*.$PackageType" -ErrorAction SilentlyContinue |
    ForEach-Object {
        try { Remove-Item $_.FullName -Force } catch { Write-Warn "Could not remove old $($_.Name), close it if locked." }
    }

$jpackageArgs = @(
    "--type", $PackageType,
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", $AppVendor,
    "--description", $AppDescription,
    "--input", $StagingDir,
    "--main-jar", $MainJar,
    "--main-class", $MainClass,
    "--runtime-image", $RuntimeDir,
    "--dest", $DistDir,
    # 桌面壳；产品选型由首次「安装向导」写入 ~/.show/install.properties
    "--java-options", "-Ddesktop.mode=true",
    # Cap heap so a second short-lived handoff process can still start JVM
    "--java-options", "-Xms64m",
    "--java-options", "-Xmx512m",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--win-shortcut",
    "--win-menu",
    "--win-dir-chooser"
)

if (Test-Path -LiteralPath $IconPath) {
    Write-Host "[info] icon: $IconPath"
    $jpackageArgs += @("--icon", $IconPath)
} else {
    Write-Warn "[WARN] icon not found: $IconPath (using default)"
}

$jpExit = 0
try {
    & jpackage @jpackageArgs
    $jpExit = $LASTEXITCODE
} catch {
    Write-Err "[ERROR] jpackage failed: $_"
    exit 1
}
if ($jpExit -ne 0) {
    Write-Host ""
    Write-Err "[ERROR] jpackage failed."
    Write-Err "  Common causes:"
    Write-Err "    - MSI without WiX Toolset 3.x"
    Write-Err "    - dist installer is locked; close and retry"
    Write-Err "    - incomplete JDK (need full JDK 17+ with jlink)"
    Write-Err "  Try PackageType = 'exe' (no WiX)."
    exit 1
}

Write-Host ""
Write-Ok "============================================"
Write-Ok "  SUCCESS - self-contained installer"
$outFull = (Resolve-Path $DistDir).Path
Write-Ok "  Output: $outFull"
Get-ChildItem -Path $DistDir -Filter "*.$PackageType" -ErrorAction SilentlyContinue |
    ForEach-Object {
        $mb = [math]::Round($_.Length / 1MB, 1)
        Write-Host "  - $($_.Name)  ($mb MB)"
    }
Write-Host ""
Write-Ok "  SKU: LOCAL buyout (SQLite). Not SaaS / no mini-program."
Write-Ok "  End-user PC: install MSI/EXE only. JDK is NOT required."
Write-Ok "  SaaS cloud edition: use docker compose (MySQL), not this MSI."
Write-Ok "  Build PC only: needs JDK 17+ / Maven / (WiX for MSI)."
Write-Ok "============================================"
Write-Host ""

# Open dist folder
Start-Process explorer.exe -ArgumentList $outFull

Write-Host "Done."
exit 0

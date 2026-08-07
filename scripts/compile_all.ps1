# ============================================================
#  Show Web build: frontend (Vite) + Spring Boot fat jar
#  Run from repo root:
#    .\scripts\compile_all.ps1
# ============================================================

$ErrorActionPreference = "Stop"

# Repo root (parent of scripts/)
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location -LiteralPath $ProjectRoot

Write-Host "============================================"
Write-Host "   Show Web Build (frontend + Spring Boot)"
Write-Host "   project: $ProjectRoot"
Write-Host "============================================"

$Host.UI.RawUI.WindowTitle = "Show Build"

if ($env:JAVA_HOME) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
if ($env:MAVEN_HOME) {
    $env:PATH = "$env:MAVEN_HOME\bin;$env:PATH"
}
if ($env:NODE_HOME) {
    $env:PATH = "$env:NODE_HOME;$env:PATH"
}

Write-Host "`n[1/2] Building Frontend..."
Set-Location (Join-Path $ProjectRoot "frontend")
npm install
if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
npm run build
if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }
Set-Location -LiteralPath $ProjectRoot

Write-Host "`n[2/2] Building Backend (fat jar)..."
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

Write-Host "`n============================================"
Write-Host "[OK] Build Complete!"
Write-Host "  Jar: target\ddmo-1.0.0.jar"
Write-Host "  Run: java -jar target\ddmo-1.0.0.jar"
Write-Host "  Open: http://localhost:8080"
Write-Host "============================================"

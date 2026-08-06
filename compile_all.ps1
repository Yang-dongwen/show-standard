$ErrorActionPreference = "Stop"
Write-Host "============================================"
Write-Host "   Show Web Build (frontend + Spring Boot)"
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
Set-Location frontend
npm install
npm run build
Set-Location ..

Write-Host "`n[2/2] Building Backend (fat jar)..."
mvn clean package -DskipTests

Write-Host "`n============================================"
Write-Host "[OK] Build Complete!"
Write-Host "  Jar: target\ddmo-1.0.0.jar"
Write-Host "  Run: java -jar target\ddmo-1.0.0.jar"
Write-Host "  Open: http://localhost:8080"
Write-Host "============================================"

<#
.SYNOPSIS
  本机打包代码 → scp → 远端 server-deploy（不覆盖服务器密钥）

.NOTES
  路径:
    服务器密钥  deploy/env/app.env（远端已有则保留）
    compose     deploy/stack/compose.lite.yml
  默认入口: http://<HostName>:8090/
#>
param(
  [string]$HostName = "13.201.82.24",
  [string]$User = "ubuntu",
  [string]$PemPath = "$env:USERPROFILE\Downloads\aws_common\dw-yindu.pem",
  [string]$RemoteAppDir = "~/show-standard",
  [string]$ComposeFile = "deploy/stack/compose.lite.yml",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Set-Location $RepoRoot

if (-not (Test-Path $PemPath)) { throw "PEM not found: $PemPath" }

$remote = "${User}@${HostName}"
$ssh = @("-i", $PemPath, "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=20")
$stage = Join-Path $env:TEMP "show-standard-deploy-stage"
$tgz = Join-Path $env:TEMP "show-standard-deploy.tgz"

Write-Host "==> stage code" -ForegroundColor Cyan
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Path $stage | Out-Null

# robocopy: exit 0-7 success. Force exit 0 so $ErrorActionPreference=Stop does not abort.
# Prefer cmd.exe so PS NativeCommandErrorAction does not treat exit 1 as terminating.
function Copy-StageDir {
  param(
    [string]$Source,
    [string]$Destination,
    [string]$ExtraArgs = ""
  )
  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  $cmd = "robocopy `"$Source`" `"$Destination`" /E $ExtraArgs /NFL /NDL /NJH /NJS /nc /ns /np & if %ERRORLEVEL% GEQ 8 exit 1 else exit 0"
  $p = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $cmd) -Wait -PassThru -NoNewWindow
  if ($p.ExitCode -ne 0) { throw "robocopy failed exit=$($p.ExitCode) src=$Source" }
}

# dirs: deploy / frontends / src; exclude node_modules, target, dist, local secrets
$dirs = @("deploy", "frontend", "frontend-saas", "src", "docker", "docs")
foreach ($d in $dirs) {
  $src = Join-Path $RepoRoot $d
  if (-not (Test-Path $src)) { Write-Host "    skip missing $d"; continue }
  $dst = Join-Path $stage $d
  if ($d -eq "frontend" -or $d -eq "frontend-saas") {
    Copy-StageDir -Source $src -Destination $dst -ExtraArgs "/XD node_modules dist .vite"
  } elseif ($d -eq "src") {
    Copy-StageDir -Source $src -Destination $dst -ExtraArgs "/XD target"
  } elseif ($d -eq "deploy") {
    Copy-StageDir -Source $src -Destination $dst -ExtraArgs "/XF app.env .env SECRETS_INVENTORY.env .admin-once.txt app.env.local"
  } else {
    Copy-StageDir -Source $src -Destination $dst -ExtraArgs "/XD node_modules target dist"
  }
  $cnt = @(Get-ChildItem -LiteralPath $dst -Recurse -File -ErrorAction SilentlyContinue).Count
  Write-Host "    staged $d files=$cnt"
}

foreach ($f in @("pom.xml", "Dockerfile", ".gitignore", "README.md")) {
  $p = Join-Path $RepoRoot $f
  if (Test-Path $p) { Copy-Item $p $stage -Force }
}

if (Test-Path $tgz) { Remove-Item $tgz -Force }
Write-Host "==> tar" -ForegroundColor Cyan
tar -czf $tgz -C $stage .
Write-Host "    $([math]::Round((Get-Item $tgz).Length / 1MB, 2)) MB"

Write-Host "==> scp package" -ForegroundColor Cyan
& scp @ssh $tgz "${remote}:/tmp/show-standard-deploy.tgz"
if ($LASTEXITCODE -ne 0) { throw "scp failed" }

# Remote bash: ASCII-only (avoid encoding/newline corruption under PowerShell)
# Expand RemoteAppDir tilde client-side so quoted remote paths are absolute.
$remoteAppResolved = $RemoteAppDir
if ($remoteAppResolved.StartsWith("~/")) {
  $remoteAppResolved = "/home/$User/" + $remoteAppResolved.Substring(2)
} elseif ($remoteAppResolved -eq "~") {
  $remoteAppResolved = "/home/$User"
}

$remoteLines = @(
  "set -euo pipefail",
  "APP_DIR=$remoteAppResolved",
  "ENV_REL=deploy/env/app.env",
  'mkdir -p "$APP_DIR"',
  'ENV_BAK=$(mktemp)',
  '# backup existing secrets if present',
  'if [ -f "$APP_DIR/$ENV_REL" ]; then cp -a "$APP_DIR/$ENV_REL" "$ENV_BAK"',
  'elif [ -f "$APP_DIR/deploy/app.env" ]; then cp -a "$APP_DIR/deploy/app.env" "$ENV_BAK"',
  'elif [ -f "$APP_DIR/deploy/.env" ]; then cp -a "$APP_DIR/deploy/.env" "$ENV_BAK"',
  'else : > "$ENV_BAK"',
  "fi",
  'tar -xzf /tmp/show-standard-deploy.tgz -C "$APP_DIR"',
  'mkdir -p "$APP_DIR/deploy/env"',
  'if [ -s "$ENV_BAK" ]; then',
  '  cp -a "$ENV_BAK" "$APP_DIR/$ENV_REL"',
  '  chmod 600 "$APP_DIR/$ENV_REL"',
  "fi",
  'rm -f "$ENV_BAK" /tmp/show-standard-deploy.tgz',
  'chmod +x "$APP_DIR/deploy/scripts/"*.sh 2>/dev/null || true',
  'mkdir -p "$HOME/bin"',
  'ln -sfn "$APP_DIR/deploy/scripts/server-deploy.sh" "$HOME/bin/ss-deploy"',
  'ln -sfn "$APP_DIR/deploy/scripts/up.sh" "$HOME/bin/ss-up"'
)
if (-not $SkipBuild) {
  $remoteLines += "export APP_DIR COMPOSE_FILE=$ComposeFile SKIP_GIT=1"
  $remoteLines += 'bash "$APP_DIR/deploy/scripts/server-deploy.sh"'
} else {
  $remoteLines += "echo 'SkipBuild: code only'"
}
$remoteScript = ($remoteLines -join "`n") + "`n"

Write-Host "==> remote extract + deploy" -ForegroundColor Cyan
$tmpSh = Join-Path $env:TEMP "ss-remote-deploy.sh"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($tmpSh, $remoteScript, $utf8NoBom)
& scp @ssh $tmpSh "${remote}:/tmp/ss-remote-deploy.sh"
& ssh @ssh $remote "bash /tmp/ss-remote-deploy.sh && rm -f /tmp/ss-remote-deploy.sh"
if ($LASTEXITCODE -ne 0) { throw "remote deploy failed" }

Write-Host ""
Write-Host "OK. http://${HostName}:8090/  (SaaS: http://${HostName}:8090/saas/)" -ForegroundColor Green

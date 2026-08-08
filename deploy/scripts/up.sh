#!/usr/bin/env bash
# 仅重建/拉起容器（不 git pull）
# 用法（仓库根）: bash deploy/scripts/up.sh
# 与 server-deploy 相同的 compose + 绝对 env_file override（service=app）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
ROOT="$(pwd -P)"

ENV_REL="deploy/env/app.env"
COMPOSE_REL="${COMPOSE_FILE:-deploy/stack/compose.lite.yml}"

if [[ ! -f "$ENV_REL" ]]; then
  if [[ -f deploy/app.env ]]; then
    mkdir -p deploy/env
    cp -a deploy/app.env "$ENV_REL"
    echo "==> migrated deploy/app.env -> $ENV_REL"
  elif [[ -f deploy/.env ]]; then
    mkdir -p deploy/env
    cp -a deploy/.env "$ENV_REL"
    echo "==> migrated deploy/.env -> $ENV_REL"
  else
    echo "缺少 $ENV_REL ，请先: cp deploy/env/app.env.example deploy/env/app.env" >&2
    exit 1
  fi
fi

if [[ ! -f "$COMPOSE_REL" ]]; then
  echo "缺少 $COMPOSE_REL" >&2
  exit 1
fi

# 占位符拦截（与 server-deploy 一致）
if grep -qE 'YOUR_RDS_ENDPOINT|CHANGE_ME' "$ENV_REL" 2>/dev/null; then
  echo "ERROR: app.env 仍含占位符 YOUR_RDS_ENDPOINT 或 CHANGE_ME" >&2
  exit 1
fi

chmod 600 "$ENV_REL" 2>/dev/null || true
sed -i 's/\r$//' "$ENV_REL" 2>/dev/null || true

ENV_FILE="$ROOT/$ENV_REL"
OVERRIDE=$(mktemp /tmp/ss-env.override.XXXXXX.yml)
trap 'rm -f "$OVERRIDE"' EXIT
cat > "$OVERRIDE" <<EOF
services:
  app:
    env_file:
      - ${ENV_FILE}
EOF

docker compose \
  -f "$COMPOSE_REL" \
  -f "$OVERRIDE" \
  --env-file "$ENV_REL" \
  up -d --build "$@"

echo ""
echo "已启动。浏览器: http://$(curl -s --connect-timeout 2 ifconfig.me 2>/dev/null || echo '<EC2公网IP>'):8090/"
echo "SaaS 管理端: /saas/"
echo "日志: docker compose -f $COMPOSE_REL logs -f app"

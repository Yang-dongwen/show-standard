#!/usr/bin/env bash
# 服务器部署：可选 git pull → docker compose 重建
# 路径（相对 APP_DIR / 仓库根）：
#   密钥    deploy/env/app.env
#   compose deploy/stack/compose.lite.yml
# 服务名: app（compose override 的 env_file 目标）
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/show-standard}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/stack/compose.lite.yml}"
ENV_REL="deploy/env/app.env"
REF="${REF:-main}"
SKIP_GIT="${SKIP_GIT:-0}"

cd "$APP_DIR"
APP_DIR="$(pwd -P)"

# ---------- 密钥：标准路径 + 旧路径一次性迁移 ----------
mkdir -p deploy/env
if [[ -n "${ENV_FILE:-}" && -f "$ENV_FILE" ]]; then
  :
elif [[ -f "$ENV_REL" ]]; then
  ENV_FILE="$APP_DIR/$ENV_REL"
elif [[ -f deploy/app.env ]]; then
  echo "==> migrate deploy/app.env -> $ENV_REL"
  cp -a deploy/app.env "$ENV_REL"
  ENV_FILE="$APP_DIR/$ENV_REL"
elif [[ -f deploy/.env ]]; then
  echo "==> migrate deploy/.env -> $ENV_REL"
  cp -a deploy/.env "$ENV_REL"
  ENV_FILE="$APP_DIR/$ENV_REL"
else
  echo "ERROR: 缺少 $ENV_REL（真实密钥，不提交）" >&2
  echo "  模板: cp deploy/env/app.env.example deploy/env/app.env" >&2
  echo "  同步: 本机 pwsh deploy/scripts/sync-env-local.ps1" >&2
  exit 1
fi

if [[ "$ENV_FILE" != "$APP_DIR/$ENV_REL" ]]; then
  cp -f "$ENV_FILE" "$APP_DIR/$ENV_REL"
fi
chmod 600 "$APP_DIR/$ENV_REL" 2>/dev/null || true
sed -i 's/\r$//' "$APP_DIR/$ENV_REL" 2>/dev/null || true
ENV_FILE="$APP_DIR/$ENV_REL"

if grep -qE 'YOUR_RDS_ENDPOINT|CHANGE_ME' "$ENV_FILE" 2>/dev/null; then
  echo "ERROR: app.env 仍含占位符 YOUR_RDS_ENDPOINT 或 CHANGE_ME" >&2
  echo "  请填写真实 RDS 与密钥后再部署" >&2
  exit 1
fi

# ---------- git ----------
if [[ "$SKIP_GIT" != "1" ]]; then
  if [[ ! -d "$APP_DIR/.git" ]]; then
    echo "ERROR: 不是 git 仓库。先 bootstrap-git 或用 deploy-local.ps1" >&2
    exit 1
  fi
  echo "==> git fetch/reset ($REF)"
  ENV_BAK=$(mktemp)
  cp -a "$ENV_FILE" "$ENV_BAK"
  git fetch --prune origin
  git checkout -f "$REF" 2>/dev/null || git checkout -f -B "$REF" "origin/$REF"
  git reset --hard "origin/$REF"
  git clean -fd \
    -e deploy/env/app.env \
    -e deploy/env/.env \
    -e deploy/env/app.env.local \
    -e deploy/env/SECRETS_INVENTORY.env \
    -e deploy/app.env \
    -e deploy/.env
  mkdir -p deploy/env
  cp -a "$ENV_BAK" "$ENV_REL"
  chmod 600 "$ENV_REL"
  rm -f "$ENV_BAK"
  ENV_FILE="$APP_DIR/$ENV_REL"
  echo "commit=$(git rev-parse --short HEAD)"
else
  echo "==> SKIP_GIT=1"
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: docker 不可用" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: 找不到 compose 文件: $COMPOSE_FILE" >&2
  exit 1
fi

HOST_DATA_ROOT="${HOST_DATA_ROOT:-/data/show-standard}"
echo "==> ensure $HOST_DATA_ROOT"
sudo mkdir -p "$HOST_DATA_ROOT/logs" "$HOST_DATA_ROOT/data"
sudo chmod -R a+rwX "$HOST_DATA_ROOT" 2>/dev/null || true

# ---------- compose override：绝对路径 env_file（service 名必须为 app）----------
OVERRIDE=$(mktemp /tmp/ss-env.override.XXXXXX.yml)
trap 'rm -f "$OVERRIDE"' EXIT
cat > "$OVERRIDE" <<EOF
services:
  app:
    env_file:
      - ${ENV_FILE}
EOF
echo "==> override env_file=$ENV_FILE (service=app)"

echo "==> docker compose -f $COMPOSE_FILE -f $OVERRIDE up -d --build"
docker compose \
  -f "$COMPOSE_FILE" \
  -f "$OVERRIDE" \
  --env-file "$ENV_FILE" \
  up -d --build

echo "==> status"
docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE" --env-file "$ENV_FILE" ps

# 健康检查：compose 仅映射宿主机 APP_HOST_PORT→nginx:80（默认 8090）；
# app:8080 仅在 compose 网络内，宿主机 curl :80/:8080 会连不上。
APP_HOST_PORT="$(grep -E '^[[:space:]]*APP_HOST_PORT=' "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r' | tr -d '\"' | tr -d "'" || true)"
APP_HOST_PORT="${APP_HOST_PORT:-8090}"
echo "==> health check (host :${APP_HOST_PORT})"
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${APP_HOST_PORT}/" 2>/dev/null || echo 000)
  if [[ "$code" == "000" || "$code" == "000000" ]]; then
    # 兜底：若有人改 compose 直接发布 80/8080
    code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:80/ 2>/dev/null || echo 000)
  fi
  if [[ "$code" == "000" || "$code" == "000000" ]]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/ 2>/dev/null || echo 000)
  fi
  case "$code" in
    200|301|302|401|403|404)
      echo "health http=$code OK"
      break
      ;;
  esac
  if [[ "$i" -eq 15 ]]; then
    echo "WARN: health still http=$code after retries (container may still be starting)"
  else
    sleep 4
  fi
done
# 接入 auto-exchange Caddy 网络，供 show.dwcode.cloud 反代（别名 show-nginx）
AE_NET="${AE_CADDY_NETWORK:-auto-exchange-lite_appnet}"
NGINX_CNAME="$(docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE" --env-file "$ENV_FILE" ps -q nginx 2>/dev/null | head -1)"
if [[ -n "$NGINX_CNAME" ]] && docker network inspect "$AE_NET" >/dev/null 2>&1; then
  NGINX_NAME="$(docker inspect -f '{{.Name}}' "$NGINX_CNAME" 2>/dev/null | sed 's#^/##')"
  if [[ -n "$NGINX_NAME" ]]; then
    if docker network connect --alias show-nginx "$AE_NET" "$NGINX_NAME" 2>/dev/null; then
      echo "==> network: $NGINX_NAME -> $AE_NET (alias show-nginx)"
    else
      # 已连接时 connect 会失败，尝试确保 alias（disconnect+connect 过于危险，仅提示）
      echo "==> network: $NGINX_NAME already on $AE_NET or connect skipped"
      docker network connect --alias show-nginx "$AE_NET" "$NGINX_NAME" 2>/dev/null || true
    fi
  fi
else
  echo "==> skip Caddy network attach (no $AE_NET or nginx not running)"
fi

echo "DONE."

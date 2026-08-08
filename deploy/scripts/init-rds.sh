#!/usr/bin/env bash
# 在 EC2 上：连通 RDS 并创建空库（表结构由应用启动时 Flyway 自动迁移）
# 用法:
#   set -a; source deploy/env/app.env; set +a
#   bash deploy/scripts/init-rds.sh
#
# 说明:
#   · 仅 CREATE DATABASE（默认 show，utf8mb4）
#   · 不导入 schema dump
#   · 表结构 / 增量 SQL 由 Spring Boot 启动时 Flyway 执行
#     locations: classpath:db/migration/mysql
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

: "${RDS_HOST:?set RDS_HOST (or: set -a; source deploy/env/app.env; set +a)}"
: "${DB_USER:?set DB_USER}"
: "${DB_PASSWORD:?set DB_PASSWORD}"
RDS_PORT="${RDS_PORT:-3306}"
RDS_DATABASE="${RDS_DATABASE:-show}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "installing mysql client..."
  sudo apt-get update -y
  sudo apt-get install -y mysql-client
fi

echo "==> ping TCP ${RDS_HOST}:${RDS_PORT}"
timeout 5 bash -c "cat < /dev/null > /dev/tcp/${RDS_HOST}/${RDS_PORT}" \
  && echo "TCP OK" \
  || { echo "TCP FAIL: 检查 RDS 安全组是否放行 EC2、是否同 VPC"; exit 1; }

echo "==> CREATE DATABASE IF NOT EXISTS ${RDS_DATABASE}"
mysql -h "$RDS_HOST" -P "$RDS_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS \`${RDS_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

echo "==> current tables (may be empty; schema applied by Flyway on app start):"
mysql -h "$RDS_HOST" -P "$RDS_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$RDS_DATABASE" -e "SHOW TABLES;" || true

echo "DONE."
echo "说明: 表结构 / 增量 SQL 由 show-standard 应用启动时 Flyway 执行"
echo "  · locations: classpath:db/migration/mysql"
echo "  · 空库 → 跑 V1 基线 + 后续版本"
echo "  · 已有库无 flyway 历史 → baseline-on-migrate，再跑后续版本"
echo "启动: bash deploy/scripts/server-deploy.sh"
echo "  或: docker compose -f deploy/stack/compose.lite.yml --env-file deploy/env/app.env up -d --build"

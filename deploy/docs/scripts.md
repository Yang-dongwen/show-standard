# 脚本说明

目录地图 → [../README.md](../README.md)

## 推荐分工

```text
改代码     →  deploy-local.ps1  或  git push + server-deploy
改密钥     →  sync-env-local.ps1
新库初始化 →  init-rds.sh（一次）
服务器重建 →  server-deploy.sh / up.sh
```

**硬规则：** 代码 tarball / `deploy-local` **永不**打包或覆盖远程 `deploy/env/app.env`；密钥只走 `sync-env-local.ps1`。

---

## 本机（Windows）

| 脚本 | 作用 |
|------|------|
| **`deploy-local.ps1`** | stage 代码（排除 `app.env`）→ tar → scp → 远程解压并**保留**远程 `app.env` → `server-deploy.sh`（`SKIP_GIT=1`） |
| **`sync-env-local.ps1`** | 仅 scp `deploy/env/app.env` → 远程同路径；`chmod 600`；默认再触发 `server-deploy` |

```powershell
# 发代码（默认 Host / PEM 见脚本参数）
powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-local.ps1

# 跳过镜像构建（仅同步代码布局时，若脚本支持 -SkipBuild）
powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-local.ps1 -SkipBuild

# 同步密钥并重启
powershell -ExecutionPolicy Bypass -File deploy/scripts/sync-env-local.ps1

# 只上传密钥不重启
powershell -ExecutionPolicy Bypass -File deploy/scripts/sync-env-local.ps1 -NoRestart
```

### 常用参数（两脚本一致倾向）

| 参数 | 默认（约定） | 说明 |
|------|----------------|------|
| `HostName` | `13.201.82.24` | EC2 公网 IP（可改） |
| `User` | `ubuntu` | SSH 用户 |
| `PemPath` | `%USERPROFILE%\Downloads\aws_common\dw-yindu.pem` | 私钥路径 |
| `RemoteAppDir` | `~/show-standard` 或 `/home/ubuntu/show-standard` | 远程应用根 |
| `ComposeFile` | `deploy/stack/compose.lite.yml` | compose 相对路径 |
| `SkipBuild` | off | `deploy-local`：跳过远程 build（若实现） |
| `NoRestart` | off | `sync-env`：只 scp 不 deploy |

成功后访问：`http://<HostName>:8090/`（或你在 `app.env` 中的 `APP_HOST_PORT`）。

---

## 服务器（EC2）

| 脚本 | 作用 |
|------|------|
| **`server-deploy.sh`** | 校验 `app.env` → 可选 git pull（备份/恢复密钥）→ compose override（`services.app.env_file` 绝对路径）→ `up -d --build` → 探活 |
| **`up.sh`** | 仅用同一 compose/env 重建容器，**不** git pull |
| **`init-rds.sh`** | TCP 检查 RDS → `CREATE DATABASE IF NOT EXISTS show` utf8mb4 → `SHOW TABLES`（表由 Flyway 在 app 启动时创建） |

```bash
cd ~/show-standard
bash deploy/scripts/server-deploy.sh
bash deploy/scripts/up.sh

set -a; source deploy/env/app.env; set +a
bash deploy/scripts/init-rds.sh
```

### `server-deploy.sh` 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `APP_DIR` | `$HOME/show-standard` | 应用根（= 仓库根） |
| `COMPOSE_FILE` | `deploy/stack/compose.lite.yml` | 相对仓库根 |
| `ENV_REL` | `deploy/env/app.env` | 密钥相对路径 |
| `REF` | `main` | git ref（`SKIP_GIT!=1` 时） |
| `SKIP_GIT` | `0` | `1` = 不 pull（`deploy-local` / `sync-env` 常用） |

### 行为要点

1. **要求** `app.env` 存在；`chmod 600`；去掉 CRLF。  
2. 若内容仍含 `YOUR_RDS_ENDPOINT` 或 `CHANGE_ME` → **退出失败**（防误上线）。  
3. `SKIP_GIT!=1`：git fetch/reset 时备份并恢复 `app.env`；`git clean` 排除密钥文件。  
4. 确保 `HOST_DATA_ROOT`（默认 `/data/show-standard`）下 `data` / `logs`。  
5. 生成临时 compose override：`services.app.env_file` = **绝对路径**（服务名必须是 **`app`**）。  
6. `docker compose -f COMPOSE -f OVERRIDE --env-file ENV_FILE up -d --build`。  
7. 探活：`curl` `127.0.0.1:80` 或 `:8080`，接受 200/301/302/401/403/404。

### `init-rds.sh` 所需变量

| 变量 | 说明 |
|------|------|
| `RDS_HOST` | RDS 终端节点 |
| `DB_USER` / `DB_PASSWORD` | 管理账号 |
| `RDS_PORT` | 默认 `3306` |
| `RDS_DATABASE` | 默认 `show` |

文档约定：schema 不在此脚本维护；见 `src/main/resources/db/migration/mysql`。

---

## 路径统一

| 项 | 路径 |
|----|------|
| 密钥文件 | `deploy/env/app.env` |
| 生产 compose | `deploy/stack/compose.lite.yml` |
| 服务名 | `app`（nginx 反代目标） |
| 数据卷 | `/data/show-standard/data` → 容器 `/root/.show` |

---

## 快速决策

| 我想… | 用 |
|-------|-----|
| 改完代码上线（无 CI） | `deploy-local.ps1` |
| 改服务器环境变量 | 编辑本机 `app.env` → `sync-env-local.ps1` |
| 只重建容器 | `up.sh` 或 `server-deploy.sh` + `SKIP_GIT=1` |
| 新库初始化 | `init-rds.sh` |
| 确认密钥未进包 | 查 `deploy-local` 的 robocopy `/XF` 与远程 env 备份逻辑 |

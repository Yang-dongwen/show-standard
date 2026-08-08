# 代码与密钥分离（CI / 日常发版）

目录地图 → [../README.md](../README.md) · 脚本表 → [scripts.md](./scripts.md)

**原则：**

- **代码** → git 或 `deploy-local.ps1`  
- **密钥** → **仅** `sync-env-local.ps1`（本机 `deploy/env/app.env` → 服务器同路径）  
- **CI 永不存储 `app.env` 内容**；最多存主机/用户/SSH 私钥  

```text
代码  ──git push 或 deploy-local──►  服务器代码树 + docker build
密钥  ──sync-env-local.ps1──scp──►  deploy/env/app.env → compose env_file → 重启 app
```

---

## 发代码

### 方式 A：本机整包（当前主路径，无 Actions 时）

```powershell
powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-local.ps1
```

- 不上传本机 `app.env`  
- 远程解压时备份/恢复已有 `app.env`  
- 默认 `SKIP_GIT=1` 调用 `server-deploy.sh`  

### 方式 B：服务器 git

```powershell
git add .
git commit -m "your message"
git push origin main
```

服务器：

```bash
cd ~/show-standard
bash deploy/scripts/server-deploy.sh
# SKIP_GIT 未设时会 fetch/reset，并保护 app.env
```

---

## 同步密钥

```powershell
# 编辑本机 deploy/env/app.env 后
powershell -ExecutionPolicy Bypass -File deploy/scripts/sync-env-local.ps1

# 只上传不重启
powershell -ExecutionPolicy Bypass -File deploy/scripts/sync-env-local.ps1 -NoRestart
```

---

## 占位符拦截

`server-deploy.sh` 在启动 compose 前检查 `app.env`：

- 若仍含 **`YOUR_RDS_ENDPOINT`** 或 **`CHANGE_ME`** → **拒绝部署**  
- 目的：防止把 example 原样推上生产  

首次上线 checklist：

1. `cp deploy/env/app.env.example deploy/env/app.env`  
2. 填 RDS / JWT / AES / bootstrap 强密码  
3. `sync-env-local.ps1`  
4. `init-rds.sh`（一次）  
5. `deploy-local.ps1` 或 `server-deploy.sh`  

---

## 若以后加 GitHub Actions

### 允许的 Secrets（仅部署通道）

| Name | 值 |
|------|-----|
| `DEPLOY_HOST` | EC2 公网 IP |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_KEY` | 登录 PEM 全文 |
| `DEPLOY_APP_DIR` | `/home/ubuntu/show-standard`（可选） |

### 禁止

| 不要放进 GitHub Secrets / Variables |
|-------------------------------------|
| `app.env` 全文或片段 |
| `SPRING_DATASOURCE_PASSWORD`、`APP_JWT_SECRET`、`APP_JWT_TENANT_AES_KEY` |
| RDS 密码、微信 AppSecret、bootstrap 密码 |

Actions 流程建议：SSH → `cd $APP_DIR` → `bash deploy/scripts/server-deploy.sh`  
容器仍读取**服务器磁盘上**已存在的 `deploy/env/app.env`。

密钥变更流程永远是：本机改 `app.env` → `sync-env-local.ps1`，而不是改 Actions Secrets。

---

## 密钥约定

| 文件 | Git |
|------|-----|
| `deploy/env/app.env` | ❌ |
| `deploy/env/app.env.local` | ❌ |
| `deploy/env/app.env.example` | ✅ |
| `deploy/env/app.env.local.example` | ✅ |
| `src/main/resources/application-ec2.yml` | ✅（仅占位，无真实密码） |
| `DEPLOY_SSH_KEY` 等 | 仅部署用，非业务密钥 |

---

## 排障

| 现象 | 处理 |
|------|------|
| scp/ssh 失败 | PEM 路径、安全组 22、本机 IP |
| Actions SSH timeout | 放行 22 或改用 `deploy-local.ps1` |
| 部署提示占位符 | 改 `app.env` 去掉 `YOUR_RDS_ENDPOINT`/`CHANGE_ME` 后 sync |
| 构建 OOM | 加 swap / 升配 / 调低 `JAVA_OPTS` 内存峰值 |
| 改密钥未生效 | 是否 `-NoRestart`；`docker compose` 是否挂了正确 `env_file` |

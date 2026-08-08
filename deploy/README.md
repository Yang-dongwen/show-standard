# deploy/ 目录说明

show-standard **AWS EC2 + RDS** 部署相关**只看这里**。子目录按职责分开，避免密钥、镜像、脚本、文档混在一起。

```text
deploy/
├── README.md          ← 你在这里（目录地图 + 日常三件事 + 安全清单）
├── env/               ← 环境变量（模板可提交；真实密钥不提交）
├── stack/             ← Docker 镜像 / compose.lite / nginx
├── scripts/           ← 一键脚本（本机 Windows + 服务器 bash）
└── docs/              ← 详细文档（从零部署、脚本表、CI）
```

与 **auto-exchange** 可共用同一台 EC2，但必须使用**不同目录与端口**：

| 项目 | 远程目录 | 默认对外端口 |
|------|----------|--------------|
| auto-exchange | `~/auto-exchange` | 8088 |
| **show-standard** | **`~/show-standard`** | **8090** |

---

## 一眼看懂：哪个文件干什么

| 路径 | 作用 | 进 Git？ |
|------|------|----------|
| **`env/app.env.example`** | 服务器/EC2 变量**模板**（占位符） | ✅ |
| **`env/app.env`** | 真实密钥（RDS / JWT / AES / SaaS / 微信…） | ❌ |
| **`env/app.env.local.example`** | 本机 env 可选模板 | ✅ |
| **`env/app.env.local`** | 本机真实值（可选） | ❌ |
| **`stack/compose.lite.yml`** | **生产**：app + nginx（**无** MySQL 容器） | ✅ |
| **`stack/Dockerfile.app`** | 多阶段：前端 + SaaS 前端 + Spring Boot jar | ✅ |
| **`stack/nginx.conf`** | 反代 headers → `app:8080` | ✅ |
| **`scripts/*`** | 同步密钥 / 发版 / 建库 | ✅ |
| **`docs/setup.md`** | 从零建 EC2 + RDS | ✅ |
| **`docs/scripts.md`** | 每个脚本参数与场景 | ✅ |
| **`docs/cicd.md`** | 代码 / 密钥分离与 CI 约定 | ✅ |

Spring 业务配置**不在**本目录：

| 配置 | 位置 |
|------|------|
| **生产 EC2 profile** | `src/main/resources/application-ec2.yml`（仅 `${ENV}`，提交） |
| 本机开发 profile | `src/main/resources/application-cloud.yml`（开发默认，可含本机 MySQL） |
| 激活方式 | 容器内 `SPRING_PROFILES_ACTIVE=ec2` |

---

## 公网入口

| 入口 | 地址 |
|------|------|
| **推荐域名 HTTPS** | **https://show.dwcode.cloud/** · **/saas/** |
| 备用 IP | `http://<公网IP>:8090/` · `/saas/` |
| 容器内应用 | `app:8080`（经 nginx 反代） |

域名配置（DNSPod A 记录 + 共用 Caddy）：**[docs/domain-show-dwcode-cloud.md](./docs/domain-show-dwcode-cloud.md)**  
端口 `APP_HOST_PORT`（默认 **8090**）仅作备用直连。

---

## 日常三件事

### 1. 发代码

**路径 A（推荐，无 CI 时）**：本机打包上传（**不会**把本机 `app.env` 覆盖服务器密钥）

```powershell
powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-local.ps1
```

**路径 B**：服务器已是 git 仓库时

```powershell
git push origin main
# 服务器: bash deploy/scripts/server-deploy.sh
```

### 2. 改密钥 / 环境变量

```powershell
# 编辑本机 deploy/env/app.env 后：
powershell -ExecutionPolicy Bypass -File deploy/scripts/sync-env-local.ps1
```

**原则：** 密钥只走 `sync-env-local.ps1`；代码走 `deploy-local` / git，二者分离。

### 3. 一次性：RDS 建库

```bash
# 在 EC2 上（需已 source / 存在 app.env 中的 RDS_*）
set -a; source deploy/env/app.env; set +a
bash deploy/scripts/init-rds.sh
# 仅 CREATE DATABASE show；表结构由应用启动时 Flyway 迁移
```

---

## 脚本速查

| 脚本 | 场景 |
|------|------|
| `scripts/deploy-local.ps1` | 本机 stage + scp + 远程 `server-deploy` |
| `scripts/sync-env-local.ps1` | 本机 → EC2 **仅**同步 `env/app.env` |
| `scripts/server-deploy.sh` | 服务器：校验密钥 + compose build/up + 探活 |
| `scripts/init-rds.sh` | 一次性：RDS `CREATE DATABASE show` |
| `scripts/up.sh` | 服务器只重建容器（不 git pull） |

详情 → **[docs/scripts.md](./docs/scripts.md)** · 从零 → **[docs/setup.md](./docs/setup.md)** · CI → **[docs/cicd.md](./docs/cicd.md)**

---

## 服务器上标准路径

```text
~/show-standard/                    # RemoteAppDir
  deploy/env/app.env                # 唯一真实密钥文件（chmod 600）
  deploy/stack/compose.lite.yml     # 默认 compose
  deploy/stack/Dockerfile.app
/data/show-standard/data            # 挂到容器 /root/.show（secrets + backups）
```

---

## 生产启动命令（服务器）

```bash
cd ~/show-standard
bash deploy/scripts/server-deploy.sh
# 等价思路：绝对路径 env_file 注入 service app，避免相对路径找不到文件
```

访问：

- 门店：`http://<公网IP>:8090/`
- 运营台：`http://<公网IP>:8090/saas/`

---

## 安全清单（生产前勾选）

- [ ] `deploy/env/app.env` **未**进 git；仓库中只有 `app.env.example`
- [ ] RDS 3306 **不对** `0.0.0.0/0`，仅 EC2 安全组
- [ ] `SPRING_DATASOURCE_URL` 含 `useSSL=true`，且已替换 `YOUR_RDS_ENDPOINT`
- [ ] `APP_JWT_SECRET` / `APP_JWT_TENANT_AES_KEY` 已用 `openssl rand` 生成并固定（多实例共享）
- [ ] `APP_SECURITY_STRICT_CLOUD=true`
- [ ] 首启后将 `APP_SAAS_BOOTSTRAP_ENABLED=false`，并轮换 bootstrap 密码（勿用 `platform123`）
- [ ] `APP_WX_MINIAPP_MOCK=false`；正式小程序再开 `ENABLED` 并填 AppId/Secret
- [ ] `server-deploy` 会拦截仍含 `YOUR_RDS_ENDPOINT` / `CHANGE_ME` 的 env
- [ ] CI（若启用）Secrets **仅** `DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_SSH_KEY`，**永不**存 `app.env` 内容

---

## 脚本内路径约定

| 常量 | 值 |
|------|-----|
| 仓库根 / `APP_DIR` | `~/show-standard`（默认） |
| 密钥文件 | `deploy/env/app.env` |
| 生产 compose | `deploy/stack/compose.lite.yml` |
| build context | 仓库根（相对 `stack/` 为 `../..`） |
| 默认对外端口 | `APP_HOST_PORT=8090` |
| 数据卷 | `${HOST_DATA_ROOT:-/data/show-standard}/data` → `/root/.show` |

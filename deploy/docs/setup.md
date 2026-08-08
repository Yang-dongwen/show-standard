# 从零部署 EC2 + RDS（show-standard）

更细的日常操作见 [cicd.md](./cicd.md)；目录地图见 [../README.md](../README.md)。

## 架构

```text
浏览器 ──:8090──► EC2
                   ├── nginx :80（容器）→ app:8080
                   └── app（Spring Boot fat-jar，profile=ec2）
                              │
                         AWS RDS MySQL（库名 show）
```

- MySQL **只用 RDS**，`compose.lite.yml` 内**不**起 MySQL。
- 静态资源：C 端 `/` + SaaS `/saas/` 已打进 jar（多阶段 Dockerfile）。
- Flyway：`classpath:db/migration/mysql`，应用启动时自动迁移（`init-rds.sh` 只建库）。

## 准备

| 项 | 说明 |
|----|------|
| EC2 | 与 RDS 同区域 / VPC；建议 Ubuntu 22.04+ |
| 安全组（EC2） | **22** 仅你的 IP；**8090** 公网（或 80，按你的入口） |
| RDS | MySQL 8；安全组 **3306 仅来自 EC2 安全组**（禁止 `0.0.0.0/0`） |
| PEM | SSH 登录密钥（本机脚本默认示例路径见 [scripts.md](./scripts.md)） |
| 密钥文件 | `cp deploy/env/app.env.example deploy/env/app.env` 后填写 |

若与 **auto-exchange** 共用同一 EC2：

- 代码目录：`~/show-standard`（不要与 `~/auto-exchange` 混用）
- 端口：默认 **8090**（auto-exchange 常用 8088）

## 推荐顺序

1. **创建 RDS**（同 VPC、优先 Private）→ 记下 Endpoint  
2. **创建 EC2** + 安装 Docker / Compose + 可选 swap  
3. 把代码放到 `~/show-standard`  
   - 本机：`deploy/scripts/deploy-local.ps1`  
   - 或：`git clone` 后 `server-deploy.sh`  
4. 本机填好 `deploy/env/app.env`，再：

   ```powershell
   powershell -ExecutionPolicy Bypass -File deploy/scripts/sync-env-local.ps1 -NoRestart
   ```

5. 在 EC2 建库：

   ```bash
   cd ~/show-standard
   set -a; source deploy/env/app.env; set +a
   bash deploy/scripts/init-rds.sh
   ```

   仅 `CREATE DATABASE IF NOT EXISTS show`（utf8mb4）；**无** schema dump。  
6. 部署应用：

   ```bash
   bash deploy/scripts/server-deploy.sh
   # 或本机再次 deploy-local.ps1（会 SKIP_GIT 调 server-deploy）
   ```

7. 浏览器验收：  
   - 门店 `http://公网IP:8090/`  
   - 运营台 `http://公网IP:8090/saas/`  
8. 首启 bootstrap 成功后：改 `APP_SAAS_BOOTSTRAP_ENABLED=false`，换强密码，再 `sync-env-local.ps1`  

## EC2 上安装 Docker（概要）

```bash
# Ubuntu 示例（以官方文档为准）
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker ubuntu
# 重新登录后 docker 无需 sudo
```

数据目录（`server-deploy` 也会确保）：

```bash
sudo mkdir -p /data/show-standard/data /data/show-standard/logs
sudo chown -R ubuntu:ubuntu /data/show-standard
```

## 安全清单

- [ ] RDS 3306 不对 `0.0.0.0/0`，只放行 EC2 安全组  
- [ ] SSH 22 仅自己 IP（或受控范围）  
- [ ] `APP_JWT_SECRET` / `APP_JWT_TENANT_AES_KEY` 已用随机串生成并固定  
- [ ] `APP_SAAS_BOOTSTRAP_PASSWORD` 非 `CHANGE_ME` / 非 `platform123`  
- [ ] 验收后 `APP_SAAS_BOOTSTRAP_ENABLED=false`  
- [ ] `APP_SECURITY_STRICT_CLOUD=true`  
- [ ] `deploy/env/app.env` 未进 git；`chmod 600`  
- [ ] `app.env` 中无 `YOUR_RDS_ENDPOINT` / `CHANGE_ME`（否则 `server-deploy` 会拒绝）  
- [ ] 微信生产：`APP_WX_MINIAPP_MOCK=false`；正式环境需 HTTPS（后续 LB/证书）  

## 探活与排障

| 现象 | 处理 |
|------|------|
| 浏览器连不上 8090 | 安全组、`APP_HOST_PORT`、`docker compose ps` |
| 应用起不来 / 连库失败 | RDS 安全组、Endpoint、账号密码、`useSSL` |
| 部署被拒 | `app.env` 仍含占位符 → 改完 `sync-env-local` |
| JWT 各实例不一致 | 在 `app.env` 固定 `APP_JWT_*` 并同步；volume `/root/.show` |
| 表不存在 | 确认 Flyway 日志；`init-rds` 只建库不建表 |

本机开发仍用 **cloud** profile + 本地 MySQL / 根目录 `docker-compose.yml`，见 [SaaS云版.md](../../docs/运行/SaaS云版.md)。生产只用 **ec2** + RDS + `deploy/env/app.env`。

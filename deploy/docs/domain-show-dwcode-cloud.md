# 域名 show.dwcode.cloud 配置说明

## 当前状态（已可用 · 2026-08-08 验收）

| 项 | 值 |
|----|-----|
| 域名 | **https://show.dwcode.cloud** |
| DNS | DNSPod → A `show` → **13.201.82.24** |
| 证书 | Let's Encrypt（Caddy 自动，约 90 天续期） |
| 门店 C 端 | https://show.dwcode.cloud/ |
| SaaS 运营台 | https://show.dwcode.cloud/saas/ |
| 备用 | http://13.201.82.24:8090/（无域名证书） |

已验收：`/` / `/saas/` HTTP 200；SaaS 登录 API 200；证书 CN=`show.dwcode.cloud`。

---

## 结论（新环境要做什么）

| 位置 | 是否必须 | 做什么 |
|------|----------|--------|
| **腾讯云 DNSPod** | ✅ 必须 | 加一条 **A 记录**：主机 `show` → `13.201.82.24` |
| **AWS 安全组** | 一般已有 | 放行 **80 / 443**（与 dwcode.cloud 相同，Caddy 共用） |
| **EC2 本机** | ✅ 已脚本化 | Caddy 反代 + 把 show nginx 接入 Caddy 所在 Docker 网络 |
| **show-standard 自己占 80/443** | ❌ 不要 | 80/443 已由 auto-exchange 的 Caddy 占用 |

**不是**「只加 DNS 就完事」：DNS 只解析到机器；HTTPS 与路由靠 **Caddy**（容器 `auto-exchange-lite-caddy-1`）。

---

## 架构

```text
浏览器  https://show.dwcode.cloud
        │ :443
        ▼
Caddy（auto-exchange compose，80/443，自动 HTTPS）
        │ reverse_proxy show-nginx:80
        ▼
show-standard nginx :80  →  app :8080（Spring profile=ec2 → RDS）
```

与主站关系：

| 域名 | 后端 |
|------|------|
| `dwcode.cloud` | auto-exchange web |
| `pdf.dwcode.cloud` | Stirling-PDF |
| **`show.dwcode.cloud`** | **show-standard** |

备用（不经证书）：`http://13.201.82.24:8090/`

---

## 1. 腾讯云 DNSPod（域名侧）

登录 [DNSPod / 腾讯云 DNS](https://console.cloud.tencent.com/cns) → 域名 `dwcode.cloud` → 添加记录：

| 主机记录 | 记录类型 | 记录值 | TTL |
|----------|----------|--------|-----|
| `show` | **A** | `13.201.82.24` | 600 |

说明：

- 完整域名为 `show.dwcode.cloud`
- **不要**开 Cloudflare 橙云代理（国内解析/证书易出问题）；DNSPod 直指 EC2 IP 即可
- 生效后：`nslookup show.dwcode.cloud` 应指向 `13.201.82.24`

---

## 2. 服务器侧（与 auto-exchange 共用 Caddy）

### 2.1 Caddyfile

已在 auto-exchange 增加站点块（仓库）：

`auto-exchange/deploy/stack/Caddyfile` → `show.dwcode.cloud` → `show-nginx:80`

### 2.2 把 show nginx 挂到 Caddy 网络

```bash
# 容器名以 docker ps 为准，一般是 show-standard-lite-nginx-1
docker network connect --alias show-nginx auto-exchange-lite_appnet show-standard-lite-nginx-1
```

`server-deploy.sh` / `up.sh` 会在部署后尝试自动 `network connect`（幂等）。

### 2.3 重载 Caddy

```bash
cd ~/auto-exchange
docker compose -f deploy/stack/compose.lite.yml --env-file deploy/env/app.env up -d caddy
# 或
docker exec auto-exchange-lite-caddy-1 caddy reload --config /etc/caddy/Caddyfile
```

证书：Let's Encrypt 由 Caddy 自动申请（需 DNS 已指向本机且 80 可达）。

---

## 3. AWS 安全组

| 端口 | 用途 |
|------|------|
| 80 | HTTP → HTTPS / ACME 校验 |
| 443 | HTTPS |
| 8090 | 可选备用（直连 nginx，无域名证书） |
| 22 | SSH |

RDS 3306 仍只对 EC2 安全组开放。

---

## 4. 验证

```bash
# DNS
nslookup show.dwcode.cloud

# 证书 + 站点
curl -sI https://show.dwcode.cloud/ | head -5
curl -s -o /dev/null -w "%{http_code}\n" https://show.dwcode.cloud/
curl -s -o /dev/null -w "%{http_code}\n" https://show.dwcode.cloud/saas/
curl -s https://show.dwcode.cloud/api/install/status
```

期望：`200`；install status 中 `cloudServer=true`、`edition=saas`、`needsSetup=false`。

---

## 5. 常见问题

| 现象 | 处理 |
|------|------|
| DNS 未生效 | 等 TTL；本机 `nslookup` 确认 A 记录 |
| Caddy 证书失败 | 确认 80 对公网开放、DNS 已指向本机、无 Cloudflare 橙云 |
| 502 Bad Gateway | `docker network connect` 是否成功；`docker exec caddy ping show-nginx` |
| 仍见安装向导 | 旧前端缓存：强刷；或未部署含 cloudServer 修复的版本 |

---

## 产品说明（与域名无关）

| 交付形态 | 安装向导「本地 / SaaS」 |
|----------|-------------------------|
| **云端** `show.dwcode.cloud` / EC2 | **无向导**，固定 SaaS + RDS |
| **MSI 买断客户端** | 首次启动才有本地选型 |

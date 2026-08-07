# 云版 Docker 部署（SaaS 订阅）

> 产品线 **B · SaaS 云版**（云端 **MySQL** + SaaS 运营台 + C 端云 API + 商家小程序）。  
> **不是** 本地买断 MSI；客户若只要本机 SQLite，见 [桌面客户端打包.md](./桌面客户端打包.md)。  
> 选型说明：[产品双轨说明.md](./产品双轨说明.md)。

## 1. 架构

```text
浏览器 C 端 / SaaS 运营台  ──┐
商家微信小程序 ──────────────┼──► Nginx(可选 HTTPS) ──► show-app:8080
                             │         │
                             │         └── spring.profiles.active=cloud
                             └── show-db:3306 MySQL 8
```

| 组件 | 说明 |
|------|------|
| `show-app` | Spring Boot，cloud profile |
| `show-db` | MySQL 8（本阶段本地/容器替代云库） |
| `nginx` | 可选，公网 80/443 |

## 2. 部署前检查

- [ ] Docker / Docker Compose  
- [ ] 端口 8080（或仅 80/443）  
- [ ] （小程序）域名 + **HTTPS**  
- [ ] （小程序）request 合法域名  
- [ ] 正式 `app-id` / `app-secret`，`mock: false`  
- [ ] 生产建议 `app.security.strict-cloud=true`、`app.saas.bootstrap-enabled=false`  
- [ ] 修改默认 SaaS 引导密码（勿使用 `platform123`）  
- [ ] 前端已构建进镜像/jar  

详见 [项目风险与改进.md](./项目风险与改进.md)。

## 3. 构建前端

```powershell
cd frontend
npm install
npm run build
cd ..\frontend-saas
npm install
npm run build
cd ..
```

## 4. 启动

```powershell
docker compose up -d --build
```

| 访问 | URL |
|------|-----|
| C 端 | http://服务器IP:8080/ |
| SaaS | http://服务器IP:8080/saas/ |

示例：SaaS `platform` / `platform123`；开店邀请码见配置（如 `WELCOME`）。

Compose 注入：`SPRING_PROFILES_ACTIVE=cloud` 与 **MySQL** 数据源。微信配置优先改 `application-cloud.yml` 后重建镜像。

### 本机 MySQL 联调（不用 Docker 库）

1. 安装 MySQL 8，建库用户（见 `docker/mysql-init/README.md`）  
2. 改 `application-cloud.yml` 中 URL/账号（默认 `127.0.0.1:3306/show`）  
3. 启动：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=cloud"
```

## 5. 商家小程序对接

1. `miniprogram-biz/utils/config.js` 的 `baseUrl` 改为 `https://你的域名`  
2. 微信开发者工具导入 `miniprogram-biz/`  
3. 正式环境合法域名只填 https 域名  

详见 [商家小程序.md](./商家小程序.md)。

## 6. 运维

```powershell
docker compose logs -f app
docker compose down
# 清库（危险）
docker compose down -v
```

备份：对 MySQL 数据卷 `mysqldump`。生产 HTTPS 可用 compose 内 nginx 或云负载均衡（见 `docker/nginx.conf`）。

## 7. 与本地版对照

| | 本地版 | Docker / 云版 |
|--|--------|----------------|
| 命令 | `java -jar` / 安装包 / 默认 run | `docker compose up` 或 `profiles=cloud` |
| 数据 | **SQLite** `~/.show/show.db` | **MySQL** 卷 / 本机实例 |
| 小程序 | 不启用 | 可启用 |
| SaaS | 可忽略 | 多店开通必需 |
| 文件备份 | C 端内置 `.db` 备份 | `mysqldump` |

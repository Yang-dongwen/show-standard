# SaaS 云版（前后端 + 门店客户端）

一条链路：**MySQL + Java 后端 + 门店网页 + SaaS 运营台**。  
商家小程序 / uni-app 都挂在这条链上，但步骤写在单独文档。

---

## 架构一句话

```text
门店网页 (frontend)  ──┐
运营台网页 (frontend-saas) ──┼──► Spring Boot :8080 ──► MySQL
商家端（另文）       ──┘         /api/** 与 /api/saas/**
```

| 入口 | 地址 |
|------|------|
| 门店 C 端 | http://localhost:8080/ |
| SaaS 运营台 | http://localhost:8080/saas/ |
| C 端 API | `/api/**` |
| 运营 API | `/api/saas/**` |

开发默认账号（运营台）：**platform / platform123**

---

## 方式 A：本机开发（最常用）

### 1. 准备 MySQL

- MySQL 8，能连 `127.0.0.1:3306`
- 账号密码改这里即可：`src/main/resources/application-cloud.yml`  
  当前示例常见为 `root` / `123456`，库名 `show`（可自动建库）

### 2. 构建前端（首次或页面有改）

```powershell
# 门店
cd frontend
npm install
npm run build
cd ..

# 运营台
cd frontend-saas
npm install
npm run build
cd ..
```

### 3. 启动后端

```powershell
# 默认就是 cloud + MySQL（见 application.yml）
mvn spring-boot:run -DskipTests
```

或：

```powershell
mvn -DskipTests package
java -jar target\ddmo-1.0.0.jar --spring.profiles.active=cloud
```

### 4. 打开页面

1. 门店：http://localhost:8080/  
   - 首次可能进安装向导 → 选 **SaaS 云版**，填 MySQL  
   - 或已有 `~/.show/install.properties` 且为 cloud 则直接登录  
2. 运营台：http://localhost:8080/saas/ → `platform` / `platform123`  
3. 用运营台 **邀请码** 开店，或已有店长账号登录门店

### 5. 前端热更新（可选）

```powershell
# 终端 1
mvn spring-boot:run -DskipTests

# 终端 2 门店
cd frontend
npm run dev
# http://localhost:3000

# 终端 3 运营台
cd frontend-saas
npm run dev
# http://localhost:3001/saas/
```

---

## 方式 B：Docker 一键

前提：已装 Docker Desktop / Compose。

```powershell
# 建议先构建前端再打镜像（页面才是新的）
cd frontend
npm install
npm run build
cd ..\frontend-saas
npm install
npm run build
cd ..

docker compose up -d --build
```

- 门店：http://localhost:8080/  
- 运营台：http://localhost:8080/saas/  
- 停服务：`docker compose down`

数据库账号以 `docker-compose.yml` 为准（常见库 `show`）。

---

## 服务器部署（简版）

1. 服务器装 **JDK 17**、**MySQL**，或只装 Docker 用上面 Compose。  
2. 上传 / 构建 `ddmo-*.jar`，前端已 `npm run build` 打进 jar 或静态资源。  
3. 启动示例：

```bash
java -Xms256m -Xmx512m -jar ddmo-1.0.0.jar --spring.profiles.active=cloud
```

4. 前面加 **Nginx HTTPS**（微信小程序正式环境必须 HTTPS）。  
5. 生产建议：
   - 改掉默认 `platform123`
   - 配置 `app.security.strict-cloud=true`
   - 关闭或限制 bootstrap 建管理员
   - 定期备份 MySQL

---

## 接商家端

| 要什么 | 文档 |
|--------|------|
| uni-app（推荐） | [uni-app.md](./uni-app.md) |
| 微信原生小程序 | [微信小程序.md](./微信小程序.md) |

**必须先把本文的云版后端跑通。**

---

## 常见问题

| 现象 | 处理 |
|------|------|
| 连不上库 | MySQL 没起；改 `application-cloud.yml` 账号 |
| `/saas/` 打不开 | 不是 cloud 模式，或未 build `frontend-saas` |
| 登录失败 | 运营台用 platform；门店用店长账号；可用运营台重置店长密码 |
| 和买断数据混了 | 清/换 `install.properties`，确认 profile 是 cloud |

买断单机、无 SaaS → 见 [买断客户端.md](./买断客户端.md)。

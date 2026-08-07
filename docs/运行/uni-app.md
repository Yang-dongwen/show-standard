# uni-app 商家端（微信小程序 / H5）

目录：`frontend-uniapp/`  
一套 Vue3 代码，主要出 **微信小程序**，同时可跑 **H5**；App 仅配置预留。

**前提：后端必须是 SaaS 云版**（见 [SaaS云版.md](./SaaS云版.md)）。买断 SQLite **不能**用。

---

## 1. 安装依赖

```powershell
cd frontend-uniapp
npm install
```

---

## 2. 本地：微信小程序

1. 先启动云版后端（本机 8080 可访问）。  
2. 编译：

```powershell
cd frontend-uniapp
npm run dev:mp-weixin
```

3. 打开 **微信开发者工具** → 导入项目  
   - 目录选：`frontend-uniapp/dist/dev/mp-weixin`（**不是**源码根目录）  
4. 详情 → 本地设置 → 勾选：**不校验合法域名、web-view、TLS…**  
5. 用云版店长账号登录（与门店网页同一账号）。

### baseUrl

文件：`frontend-uniapp/src/utils/config.js`

| 场景 | 怎么设 |
|------|--------|
| 模拟器 | 非 H5 默认 `http://127.0.0.1:8080` |
| 真机调试 | 改成电脑局域网 IP，例如 `http://192.168.1.8:8080`；或运行时 `uni.setStorageSync('baseUrlOverride', 'http://...')` |
| 正式上线 | `USE_PROD = true`，`PROD_BASE = 'https://你的域名'` |

微信 AppID：`src/manifest.json` → `mp-weixin.appid`（开发可用测试号 / touristappid）。

### 生产包

```powershell
npm run build:mp-weixin
# 上传目录：dist/build/mp-weixin
```

正式环境还要：微信公众平台配置 **request 合法域名**（HTTPS），后端 `app.wx.miniapp.mock=false` 并填真实 appId/secret。

---

## 3. 本地：H5

```powershell
cd frontend-uniapp
npm run dev:h5
```

浏览器打开：**http://localhost:5173**

H5 开发时 `baseUrl` 为空字符串，请求走同源 `/api`，由 Vite **代理到 8080**，避免跨域。

生产 H5：

```powershell
npm run build:h5
# 产物 dist/build/h5 ，挂到 Nginx，建议与 API 同域反代
```

---

## 4. App（预留）

```powershell
npm run build:app
```

仅生成 App 资源；正式 Android 包需 HBuilderX 等工具，本仓库不强制交付。

---

## 5. 功能范围

与门店网页同一套 API：登录（含微信绑定）、首页、会员、收银、员工、服务、报表、门店。

| 能力 | 说明 |
|------|------|
| 请求 | `uni.request` → `/api/**`，Header `Authorization: Bearer <token>` |
| 401 | 清登录态并回登录页 |

---

## 6. 可选：接口冒烟

后端 cloud 已启动、有店长账号时，在仓库根目录：

```powershell
python scripts/smoke_login_customer_tx.py
```

（脚本内默认账号可改；也可用运营台重置店长密码。）

---

## 7. 常见问题

| 现象 | 处理 |
|------|------|
| 白屏 / 找不到页面 | 开发者工具导入的是 `dist/dev/mp-weixin`，不是 `frontend-uniapp` 根目录 |
| 网络失败 | 后端是否 cloud 已启动；baseUrl 是否对 |
| 真机连不上 | 不要用 `127.0.0.1`，用电脑局域网 IP |
| H5 CORS | 用 `npm run dev:h5`，不要让浏览器跨域直打 8080 |
| 想用原生小程序 | 见 [微信小程序.md](./微信小程序.md) |

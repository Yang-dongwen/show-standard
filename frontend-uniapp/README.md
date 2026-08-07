# Show 商家端 uni-app

Vue3 + Vite。主入口 **微信小程序**，同时支持 **H5**；App 仅配置预留。

**必须连 SaaS 云版 API**，不接买断 SQLite。

## 怎么跑 / 部署

请直接看：

**[docs/运行/uni-app.md](../docs/运行/uni-app.md)**

云版后端：[docs/运行/SaaS云版.md](../docs/运行/SaaS云版.md)

## 常用命令

```powershell
cd frontend-uniapp
npm install
npm run dev:mp-weixin   # 导入 dist/dev/mp-weixin
npm run dev:h5          # http://localhost:5173
npm run build:mp-weixin
npm run build:h5
```

| 配置 | 文件 |
|------|------|
| baseUrl | `src/utils/config.js` |
| 微信 appid | `src/manifest.json` |
| H5 代理 | `vite.config.js`（`/api` → 8080） |

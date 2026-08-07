# Show 商家小程序 V1

**产品约束：** 必须使用 **云版（SaaS）** API；本地安装包数据在本机，**不接小程序**。  
详见 `docs/产品双轨说明.md`、`docs/商家小程序.md`。

## 功能

与 C 端 Web 对齐（店长 JWT + `/api/**`）：

- 登录、经营总览  
- 会员、收银（充值/消费）  
- 员工、服务、报表、门店资料  

## 开发

1. 启动后端（云配置或本机开发）：`mvn spring-boot:run`  
2. 修改 `utils/config.js` 的 `baseUrl`  
3. 微信开发者工具 → 导入本目录  
4. 详情 → 本地设置 → **不校验合法域名**  

正式环境：`baseUrl` 改为 `https://你的域名`，并在微信公众平台配置 request 合法域名。

## 账号

与 C 端同一店长账号（云版经 SaaS 邀请码开店后的账号）。

## 微信绑定登录

1. 点 **微信一键登录** → 后端 `POST /api/auth/wx-login`  
2. **首次**：返回 `bindRequired` + `preToken` → 输入店长账号密码 → `POST /api/auth/wx-bind`  
3. **之后**：同一微信 `wx-login` 直接进首页  

后端 `app.wx.miniapp.mock=true` 时不调微信服务器，便于开发者工具联调。  
正式环境：`mock=false` + 真实 appId/secret，见 `docs/云版Docker部署.md`。

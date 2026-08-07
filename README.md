# Show · 理发店会员管理系统

面向中小理发店的轻量会员与收银系统。  
两条交付线：**本地买断（SQLite）** · **SaaS 云版（MySQL）**；云版可再接 **商家小程序 / uni-app**。

![Java](https://img.shields.io/badge/Java-17-2f6f4f)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-2f6f4f)
![Vue](https://img.shields.io/badge/Vue-3.4-2f6f4f)
![License](https://img.shields.io/badge/License-MIT-2f6f4f)

---

## 怎么运行 / 部署？

**只看这一目录（步骤短、按形态分开）：**

### → [docs/运行/](./docs/运行/)

| 文档 | 内容 |
|------|------|
| [运行说明（目录）](./docs/运行/README.md) | 怎么选文档 |
| [买断客户端](./docs/运行/买断客户端.md) | 单机 SQLite，与 SaaS 无关；含 MSI 打包 |
| [SaaS 云版](./docs/运行/SaaS云版.md) | MySQL + 门店网页 + 运营台 + Docker |
| [uni-app](./docs/运行/uni-app.md) | 商家多端：微信小程序 / H5 |
| [微信小程序](./docs/运行/微信小程序.md) | 原生 `miniprogram-biz` |

---

## 产品一览

| 形态 | 数据 | 适合谁 |
|------|------|--------|
| **本地买断** | 本机 SQLite | 单店、可离线、无运营台/小程序 |
| **SaaS 云版** | MySQL | 多店、运营开店、小程序/uni-app |

- 门店网页：`frontend/` → 默认 http://localhost:8080  
- 运营台：`frontend-saas/` → http://localhost:8080/saas/  
- 商家 uni-app：`frontend-uniapp/`  
- 商家原生小程序：`miniprogram-biz/`  

---

## 客户端截图

![登录页](./docs/截图/01-login.png)

![经营总览](./docs/截图/02-dashboard.png)

---

## 极速体验（SaaS 本机）

```powershell
# 需本机 MySQL，账号见 src/main/resources/application-cloud.yml
cd frontend ; npm install ; npm run build ; cd ..
cd frontend-saas ; npm install ; npm run build ; cd ..
mvn spring-boot:run -DskipTests
```

- 门店：http://localhost:8080  
- 运营台：http://localhost:8080/saas/ （`platform` / `platform123`）

买断 SQLite：

```powershell
cd frontend ; npm install ; npm run build ; cd ..
mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=desktop"
```

细节仍以 [docs/运行/](./docs/运行/) 为准。

---

## 目录结构

```text
show-standard/
├── frontend/              # 门店 C 端
├── frontend-saas/         # SaaS 运营台
├── frontend-uniapp/       # 商家 uni-app
├── miniprogram-biz/       # 商家微信原生小程序
├── src/                   # Java 后端
├── scripts/               # 打包与冒烟脚本
├── docker-compose.yml
├── docs/
│   ├── 运行/              # ★ 运行与部署（主文档）
│   └── 截图/
└── README.md
```

---

## 测试

```powershell
mvn test
```

商家 API 冒烟（云版后端已启动）：

```powershell
python scripts/smoke_login_customer_tx.py
```

---

## License

MIT

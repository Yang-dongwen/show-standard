# scripts/

在仓库**根目录**执行。

| 脚本 | 作用 |
|------|------|
| `compile_all.ps1` | 门店前端 build + `mvn package` → `target/ddmo-1.0.0.jar` |
| `package-desktop.bat` / `package-desktop.ps1` | 买断版 Windows 安装包 |
| `smoke_login_customer_tx.py` | 登录/会员/收银 API 冒烟（需云版后端） |

## 用法

```powershell
.\scripts\compile_all.ps1
.\scripts\package-desktop.bat

python scripts\smoke_login_customer_tx.py
```

运行与部署说明：**[docs/运行/](../docs/运行/)**  
- 买断打包细节 → [买断客户端.md](../docs/运行/买断客户端.md)  
- 云版 / Docker → [SaaS云版.md](../docs/运行/SaaS云版.md)  

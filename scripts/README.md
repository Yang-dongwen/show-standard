# scripts/

构建与打包脚本集中目录。均以**仓库根目录**为工作目录解析路径，可在任意 cwd 下通过本目录内的路径调用。

| 脚本 | 作用 |
|------|------|
| `compile_all.ps1` | 店长端前端 `npm run build` + `mvn clean package` → `target\ddmo-1.0.0.jar` |
| `package-desktop.bat` | 桌面买断版 MSI/EXE 启动器（纯 ASCII） |
| `package-desktop.ps1` | jpackage 打包逻辑（改版本 / msi·exe 在此） |

## 用法（在仓库根目录）

```powershell
# 前端 + fat jar
.\scripts\compile_all.ps1
java -jar target\ddmo-1.0.0.jar

# 桌面安装包（建议先 compile_all）
.\scripts\compile_all.ps1
.\scripts\package-desktop.bat
```

产物：

- Web：`target\ddmo-1.0.0.jar`
- 桌面：`dist\Show-*.msi`（或 exe）

更多说明见 `docs/桌面客户端打包.md`、`docs/开发与运行.md`。

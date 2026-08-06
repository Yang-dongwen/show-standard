# 桌面客户端打包方案

在 **不影响原有 Web 功能** 的前提下，将本 Spring Boot 项目打成 Windows 可安装程序（优先 MSI）。

## 核心原则

1. 对原有 Web 代码改动尽量小。
2. 正常启动（`mvn spring-boot:run` / `java -jar`）行为与原来一致：不自动开浏览器、不强制改 headless。
3. 通过根目录脚本 `package-desktop.bat`，更新代码后执行一次即可生成安装包。

---

## 一、代码改动（可开关，默认关闭）

仅改动主启动类 `com.ddmo.app.DdmoApplication`。

| 开关 | 行为 |
|------|------|
| 默认 / 未设置 | 与纯 Web 一致 |
| `-Ddesktop.mode=true` | `headless=false`；单实例锁；系统托盘（右键退出）；就绪后打开默认浏览器 |

端口从 `Environment` 动态读取（优先 `local.server.port`，回退 `server.port`），不写死。

桌面安装包由 jpackage 通过 `--java-options "-Ddesktop.mode=true"` 注入该开关。

---

## 二、打包脚本

| 文件 | 说明 |
|------|------|
| `package-desktop.bat` | 纯 ASCII 启动器（双击即可） |
| `package-desktop.ps1` | 实际打包逻辑（配置区在此修改） |

> **说明：** 旧版把中文写在 `.bat` 里，在中文 Windows 的 `cmd` 下会因编码错乱导致整行被拆成非法命令。现已改为 bat 调 PowerShell，避免该问题。

### 配置区（`package-desktop.ps1` 顶部，可改）

| 变量 | 当前值 | 说明 |
|------|--------|------|
| `$AppName` | Show | 应用名 / 快捷方式名 |
| `$AppVersion` | 1.0.0 | 版本（与 pom 对齐） |
| `$AppVendor` | DDMO | 厂商 |
| `$MainJar` | ddmo-1.0.0.jar | 主 jar（与 `pom` artifactId-version 一致） |
| `$MainClass` | `org.springframework.boot.loader.JarLauncher` | Spring Boot fat jar 启动器 |
| `$PackageType` | msi | 改为 `exe` 则生成 exe（**不依赖 WiX**） |
| `$IconPath` | logo.ico | 项目根目录图标 |
| `$DistDir` | dist | 安装包输出目录 |

### 脚本流程

1. 检查 `java` / `jpackage` / `mvn`（MSI 时提示 WiX）
2. `mvn clean package -DskipTests`
3. 将主 jar 复制到 staging 目录（避免把整个 `target/` 打进安装包）
4. `jpackage --type msi ... --java-options "-Ddesktop.mode=true" --win-shortcut --win-menu --win-dir-chooser`
5. 成功后自动打开 `dist` 目录

### 改成 exe

编辑 `package-desktop.ps1` 配置区：

```powershell
$PackageType = "exe"
```

---

## 三、使用前准备

### 谁需要装 JDK？

| 角色 | 是否需要 JDK | 说明 |
|------|----------------|------|
| **打包/开发机** | **需要** JDK 17+（完整 JDK） | 编译、`jlink`、`jpackage` |
| **最终用户电脑** | **不需要** | 安装包内已嵌入 `runtime`（精简 JRE），安装后直接运行 `Show.exe` |

安装目录大致结构（自包含）：

```
Show/
  Show.exe          # 启动器
  app/              # 业务 jar
  runtime/          # 内嵌 Java 运行时（用户无需再装 JDK）
```

### 打包机工具

| 工具 | 要求 | 用途 |
|------|------|------|
| **JDK 17+**（完整 JDK，非 JRE） | 打包机必须 | 编译 + `jlink` + `jpackage` |
| **Maven 3.8+** | 打包机必须 | `mvn package` |
| **WiX Toolset 3.x** | 仅 MSI 需要 | jpackage 生成 `.msi` |
| **logo.ico** | 可选 | 安装包/快捷方式图标（项目根已有） |
| 前端静态资源 | 建议先构建 | 安装包内嵌的是 `static/`；发版前请 `npm run build` 或 `.\compile_all.ps1` |

### WiX 安装提示

1. 下载 [WiX Toolset 3.x](https://github.com/wixtoolset/wix3/releases)
2. 安装后将 WiX 的 `bin` 目录加入系统 `PATH`（能执行 `candle` / `light`）
3. 新开终端验证：`candle -?`

不想装 WiX：把 `PACKAGE_TYPE` 设为 `exe`。

### 环境验证

```powershell
java -version
jpackage --version
mvn -version
```

---

## 四、使用流程

### 日常 Web 开发（不受影响）

```powershell
# 后端
mvn spring-boot:run

# 或 fat jar
java -jar target\ddmo-1.0.0.jar
```

打开 `http://localhost:8080`，**不会**自动弹浏览器。

### 本地验证桌面开关

```powershell
java -Ddesktop.mode=true -jar target\ddmo-1.0.0.jar
```

启动完成后应自动打开默认浏览器。

### 发版：打 Windows 安装包

1. （推荐）先保证前端已构建进 `static`：

   ```powershell
   .\compile_all.ps1
   ```

   或只打桌面包时脚本会再跑一遍 `mvn clean package`（**不会**自动 `npm run build`；前端有改动请先构建）。

2. 双击或执行：

   ```powershell
   .\package-desktop.bat
   ```

3. 成功后打开 `dist\`，得到例如 `Show-1.0.0.msi`。

4. 在目标机器安装 → 开始菜单 / 桌面快捷方式启动 → 自动开浏览器访问本机服务。

### 版本号变更

同步修改：

- `pom.xml` 的 `<version>`
- `package-desktop.ps1` 的 `$AppVersion`、`$MainJar`

---

## 五、常见问题

### Q1：桌面模式如何打开界面？

本系统 UI 是 **Web 前端**（Vue 静态页 + 本地 Spring Boot）。桌面模式会优先用 **Edge / Chrome 应用模式**（`--app=http://localhost:端口`）打开：

- 独立窗口，一般无地址栏、无标签栏
- 更接近桌面客户端体验

若本机没有 Edge/Chrome，再回退系统默认浏览器。托盘 **Open UI** 同样走应用模式。

### Q2：第一次能开，再开报 Failed to launch JVM

常见原因：**第一次进程仍在后台**（关掉浏览器 ≠ 退出程序）。再次双击会再起一个 JVM，内存不足时 jpackage 启动器就会报 `Failed to launch JVM`。

当前版本已处理：

1. **单实例**：若服务已在跑，再次打开只唤醒浏览器并立刻退出，不再重复启动 Spring。
2. **系统托盘**：任务栏右下角托盘图标 → 右键 **Exit Show** 才是真正退出。
3. 安装包限制堆内存（`-Xmx512m`），降低二次启动失败概率。

若仍失败：

```powershell
# 结束残留进程后重试
Get-Process Show -ErrorAction SilentlyContinue | Stop-Process -Force
```

日志：`%USERPROFILE%\.show\desktop.log`

### Q3：启动后有 / 没有黑窗口（控制台）

- 当前脚本**未**加 `--win-console`：安装后一般**无**控制台黑窗（适合给店员用）。
- 若调试需要看日志，在 `package-desktop.ps1` 的 jpackage 参数中增加 `"--win-console"`。

### Q4：端口被占用（8080）

- 改 `src/main/resources/application.yml` 的 `server.port`
- 或运行时：`java -Ddesktop.mode=true -jar ... --server.port=8081`
- 若要在安装包内固定，可再加：`--java-options "-Ddesktop.mode=true" --java-options "-Dserver.port=8081"`（在 bat 里追加一行 `--java-options`）

### Q5：jpackage 失败 / 找不到 WiX

- MSI 必须装 WiX 3.x 且 `candle` 在 PATH 中
- 临时改为 `$PackageType = "exe"`
- 确认是 JDK 的 `jpackage`，不是残缺运行时

### Q6：安装后打不开页面 / 空白

- 确认打包前已把前端 build 进 `src/main/resources/static/`
- 手动访问 `http://localhost:8080` 看服务是否起来
- 看 `%USERPROFILE%\.show\desktop.log` 或任务管理器中是否有 `Show` 进程

### Q7：正常 `mvn spring-boot:run` 却自动开浏览器

检查是否误设了环境/系统属性 `desktop.mode=true`。默认应为关闭。

### Q8：dist 里旧安装包删不掉 / 打包报占用

关闭已打开的安装包文件或安装程序，删掉 `dist` 中旧文件后重试。

---

## 六、与纯 Web 构建的关系

| 脚本 | 作用 |
|------|------|
| `compile_all.ps1` | 前端 build + fat jar（Web 运行） |
| `package-desktop.bat` | Maven 打包 + jpackage 安装包（桌面分发） |

建议发桌面版流程：`compile_all.ps1`（保证最新前端）→ `package-desktop.bat`。

# scp-facility-items（Melon's Tools）

Minecraft **Forge 1.20.1** 的 SCP 设施玩法 mod：门禁身份卡、异常文档图鉴、部门权限与任务、
QTE 小游戏、设施终端 / 个人终端（手机）UI、设施资金与采购流程。

- **modid**：`melonstools`
- **显示名**：Melon's Tools
- **平台**：Forge 1.20.1（`47.4.20`）· Java 17 · ForgeGradle 6 · Gradle 8.8
- **映射**：`official` / `1.20.1`
- **许可**：All Rights Reserved

---

## 功能模块

| 包 | 内容 |
|---|---|
| `idcard` | 身份卡与门禁：三种读卡器方块（普通 / 检查站 / 上锁）、读卡器配对、身份卡编辑界面、HUD 常驻显示与 Curios 饰品渲染、Lightman's Currency 银行卡、SecurityCraft 联动 |
| `anomaly` | 异常文档图鉴（codex）、报告归档、路径消毒 |
| `qte` | QTE 框架：方块绑定、红石发射器、选择器与服务端 tick 逻辑 |
| `client/gui` | 10 种 QTE 小游戏界面（平衡、校准、充能释放、密码、技能检定、钓鱼条、连打、记忆、序列、接线）+ 绑定器 / 终端 / 手机界面 |
| `item` | 手机、QTE 绑定器、异常磁场抑制器 |
| `block` | 设施终端方块、QTE 红石发射器方块及其 BlockEntity |
| `network` | 23 个网络包：终端 action、手机数据同步、聊天室、好友、排行榜、任务、报告、QTE 流程 |
| `data` | 21 个 `SavedData` 持久化：部门申请 / 任务、设施资金、设施付款、采购、工资、操作审计、公告、排行榜、研究文档、增援、生命警报、待发卡队列等 |
| `department` | 部门权限策略（身份来自 ID 卡，前端不判权） |
| `capability` | 手机 capability 挂载 |
| `compat` / `config` | Curios 联动、QTE 服务端配置 |

### 架构约定

**Java 是业务、权限、校验、持久化、审计的唯一可信源**；前端 JS/CSS 只负责渲染服务端下发的
JSON、收集表单并回发 action，不重建状态源、不计算余额与权限。

---

## UI 工作流

终端与手机的界面用 **ApricityUI（HTML/CSS/JS）** 实现：

```
ui-prototype/terminal/index.html + terminal.css + terminal.js
        │  ui-prototype/terminal/merge_terminal.ps1 （内联 CSS/JS，输出 index.final.html）
        ▼
src/main/resources/assets/apricityui/apricity/melonstools/terminal/index.html   ← 随 jar 打包
```

手机界面同理（`ui-prototype/phone/`）。**改 UI 请改 `ui-prototype/` 下的源码再重新合并**，
不要直接编辑 `resources/` 里的烘焙产物。

---

## 构建

### 1. 环境

- JDK 17
  `gradle.properties` 里写死了 `org.gradle.java.home=C:\Program Files\Java\jdk-17`，
  换机器请改成自己的 JDK 17 路径（或删掉该行，交给 foojay toolchain 自动处理）。
- 首次构建需联网下载 Forge MDK 与映射，耗时较长。

### 2. 依赖

**无需任何手动准备**：全部依赖（包括 ApricityUI / SecurityCraft / Lightman's Currency）
均由 Gradle 从公开 maven 自动解析，仓库不携带任何第三方二进制文件。

### 3. 命令

```bash
./gradlew build          # 构建，产物在 build/libs/melonstools-<version>.jar
./gradlew runClient      # 启动客户端（工作目录 run/）
./gradlew runServer      # 启动服务端（--nogui）
./gradlew runData        # 数据生成
```

---

## 目录结构

```
build.gradle                     ForgeGradle 6 构建脚本
gradle.properties                版本 / 映射 / mod 元数据
src/main/java/com/example/melonstools/   业务源码（129 个 .java）
src/main/resources/              资源：assets、data、META-INF/mods.toml
src/test/java/                   骨架测试
ui-prototype/                    UI 可编辑源码（HTML/CSS/JS）
docs/                            示例数据
libs/                            本地 jar 回退位（整目录已被 .gitignore 忽略）
```

---

## 依赖

全部走公开 maven，`./gradlew build` 开箱即用：

| 依赖 | 坐标 | 仓库 | 性质 |
|---|---|---|---|
| Forge | `net.minecraftforge:forge:1.20.1-47.4.20` | maven.minecraftforge.net | 平台 |
| ApricityUI | `maven.modrinth:apricityui:1.2.3` | Modrinth | **硬前置**（UI 屏幕继承其 `ApricityScreen`） |
| SecurityCraft | `maven.modrinth:security-craft:v1.10.2.1` | Modrinth | **硬前置**（方块/方块实体继承其 `OwnableBlock` / `LinkableBlockEntity`） |
| Lightman's Currency | `maven.modrinth:lightmans-currency:1.20.1-2.3.0.4g` | Modrinth | 编译期依赖 |
| Curios API | `top.theillusivec4.curios:curios-forge:5.9.1+1.20.1` | maven.theillusivec4.top | `compileOnly` + `runtimeOnly` |
| GeckoLib | `software.bernie.geckolib:geckolib-forge-1.20.1:4.4.4` | GeckoLib Cloudsmith | 编译期依赖 |
| JNA | `net.java.dev.jna:jna:5.12.1` | Maven Central | `compileOnly`（仅客户端 IME） |

> **构建期坑位备忘**：Modrinth 仓库**不要**用 `exclusiveContent` 包裹。
> `exclusiveContent` 会把 `maven.modrinth` 组对其它所有仓库屏蔽，连 ForgeGradle 自己的
> deobf 仓库一起屏蔽，导致 `fg.deobf` 解析 `*_mapped_official_1.20.1` 时报
> `Could not find maven.modrinth:xxx`。用普通 `maven { url "https://api.modrinth.com/maven" }` 即可。

> 若 Modrinth maven 不可达，可在 `libs/` 放同名 jar 并临时改回
> `flatDir { dir 'libs' }` + `fg.deobf("blank:<name>:<version>")` 作为本地回退。
> `libs/` 已在 `.gitignore` 中整目录忽略。

---

## 备注

- `melons.tools` 的 AUI 字体光栅化：主类构造里设置了
  `apricityui.fontRaster.targetPhysical=true`，必须在 AUI 首次绘制文字前生效，
  否则大字号（主页水印、时钟）会模糊。
- 服务端**不**打包 JNA：Forge 1.20.1 的 jar-in-jar 库无法标记 side，内嵌 JNA 会导致
  专用服务端在 modlauncher 阶段 JVM 级卡死。JNA 仅客户端 `Class.forName` 探测使用。

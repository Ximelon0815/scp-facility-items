# libs/ — 本地依赖 jar（不随仓库分发）

本目录下的 jar **全部是第三方已发布的成品**，出于体积与再分发考虑不进仓库，
已在 `.gitignore` 中排除。要完整构建本项目，请按下表自行下载后放进 `libs/`，
文件名需与 `build.gradle` 里的 `flatDir` 引用**完全一致**。

| 文件名 | 用途 | 获取途径 |
|---|---|---|
| `apricityui-1.2.3.jar` | ApricityUI (AUI) — HTML/CSS/JS 游戏内 UI 框架 | 作者发布页 / 官方渠道 |
| `securitycraft-1.20.1-v1.10.2.1.jar` | SecurityCraft 1.20.1 — 门禁与安防联动 | CurseForge / Modrinth |
| `lightmanscurrency-1.20.1-2.3.0.4g.jar` | Lightman's Currency 1.20.1 — 设施资金与账户 | CurseForge / Modrinth |
| `jna-5.12.1.jar` | JNA — 仅客户端 IME 中文输入用，`compileOnly` | Maven Central |

> `apricityui.zip` 是 AUI 的发行压缩包，仅作留档，同样不进仓库。

## build.gradle 中对应的引用

```groovy
flatDir { dir 'libs' }

implementation fg.deobf("blank:apricityui:1.2.3")
implementation fg.deobf("blank:securitycraft-1.20.1:v1.10.2.1")
implementation fg.deobf("blank:lightmanscurrency-1.20.1:2.3.0.4g")
compileOnly files('libs/jna-5.12.1.jar')
```

## 无需手动下载的依赖

以下依赖走公开 maven，Gradle 会自动拉取：

- Forge `1.20.1-47.4.20` — `maven.minecraftforge.net`
- Curios `5.9.1+1.20.1` — `maven.theillusivec4.top`
- GeckoLib `4.4.4` — `dl.cloudsmith.io/public/geckolib3/geckolib/maven`

## 注意

`libs/` 下的 SecurityCraft / Lightman's Currency 是**编译期依赖**，
若缺失，Gradle 会在依赖解析阶段直接失败（`Could not find blank:xxx`），
表现为 `libs` 本地仓库里找不到对应 artifact。

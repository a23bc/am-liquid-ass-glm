# AMLiquidAss

> 真·液态玻璃 Xposed 模块，应用于 Apple Music 手机端底栏与 mini player。
>
> 以 [AM-plus-plus](https://github.com/Zennmn/AM-plus-plus) 的 HookEntry 基础设施为骨架，
> 用 [AndroidLiquidGlass / backdrop](https://github.com/Kyant0/AndroidLiquidGlass)（作者 Kyant0）的
> AGSL 着色器重新实现液态玻璃效果，替换掉 AM-plus-plus 原本基于 BlurView 的伪液态玻璃。

## 设计要点

- **包名（applicationId + namespace）**：`com.example.amliquidass`
- **保留**：完整的 `HookEntry` + libxposed 102 入口、`ModernXposedRuntime`、`EmbeddedBootstrap`、`LayoutInflationRegistry`、`FeatureInstallation` 框架、设置 host/controller 接口（已裁剪为存根）
- **删除**：AM-plus-plus 原有所有非液态玻璃功能（歌词、字体、CJK 卡拉OK、双栏、DPI 重写、Title 纠错、Editorial Video、自定义歌词、HLE 元数据子系统等约 60 个 .kt 文件）
- **替换**：原 `PhoneLiquidGlassFeature.kt`（812 行 BlurView 实现）→ 全新实现（约 200 行），用 Android 原生 `RenderEffect.createRuntimeShaderEffect` + `RuntimeShader` 跑 backdrop 的 `RoundedRectRefractionWithDispersionShaderString` 着色器

## 真·液态玻璃实现

`backdrop` 库原本是 Compose Multiplatform 组件库。Apple Music 不是 Compose app，所以本模块没有走 ComposeView 包装路线，而是直接：

1. 把 backdrop 的 AGSL 着色器字符串 vendor 进 `LiquidGlassShaders.kt`（Apache-2.0，与 upstream 同 license）
2. 在 `LiquidGlassEffect.kt` 用 Android 原生 `android.graphics.RuntimeShader` 加载着色器
3. 用 `RenderEffect.createRuntimeShaderEffect(shader, "content")` 生成 RenderEffect
4. 链上一层 `RenderEffect.createBlurEffect` 作为底色模糊
5. 通过 `view.setRenderEffect(...)` 直接作用到目标 View

要求 Android 13（API 33）及以上。低于 33 自动跳过（不会闪退）。

## 文件结构

```
app/src/main/
├── AndroidManifest.xml
├── resources/META-INF/xposed/        # libxposed 102 入口清单
│   ├── java_init.list                  # 指向 com.example.amliquidass.hook.HookEntry
│   ├── module.prop
│   └── scope.list                       # com.apple.android.music
├── res/
│   ├── drawable/ic_module.xml          # 模块图标
│   └── values/{strings.xml,styles.xml,ids.xml}
└── java/com/example/amliquidass/
    ├── ModuleConstants.kt
    ├── CurrentSongDetails.kt
    ├── hook/
    │   ├── HookEntry.kt                 # libxposed 入口（保留）
    │   ├── FeatureInstallation.kt        # 只注册 PhoneLiquidGlassFeature
    │   ├── EmbeddedBootstrap.kt         # 版本检查
    │   ├── ModernXposedRuntime.kt       # libxposed wrapper
    │   ├── LayoutInflationRegistry.kt   # 通用布局 hook 注册表
    │   ├── PhoneLiquidGlassFeature.kt   # ← 全新，基于 backdrop AGSL 着色器
    │   ├── LiquidGlassEffect.kt         # ← 新增，RenderEffect 应用器
    │   ├── LiquidGlassShaders.kt        # ← 新增，AGSL 着色器字符串
    │   ├── TargetAdaptation.kt          # 裁剪为 shim
    │   ├── TargetSymbols.kt             # 裁剪为只剩 TargetBuild
    │   └── AppleMusicCurrentSongIdentityTarget.kt  # 裁剪为只剩 CurrentSongIdentityCache
    ├── config/
    │   ├── ModuleSettingsSchema.kt      # 只保留 phone_liquid_glass + schema_version
    │   ├── TargetConfigClient.kt
    │   ├── EmbeddedConfigurationSession.kt
    │   ├── EmbeddedConfigurationMigration.kt
    │   └── HostPrivateEmbeddedStorage.kt
    ├── model/ModuleModels.kt
    └── ui/
        ├── EmbeddedSettingsHost.kt      # 裁剪为 no-op 存根
        └── EmbeddedRuntimeSettingsController.kt
```

## 工具链

- AGP `8.7.0`、Gradle `8.9`、Kotlin `1.9.24`、JDK 17
- `compileSdk = 35`、`buildToolsVersion = 35.0.0`、`targetSdk = 35`、`minSdk = 26`
- libxposed API/Service `102.0.0`（compileOnly）

> 已从 AM-plus-plus 的 canary 工具链（AGP 9.2.1 / Gradle 9.5.1 / compileSdk 37）降级到稳定版，CI 现成可用。

## 本地构建

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

release 默认用 debug 签名兜底（开箱即装）。要正式发布，在仓库根目录放 `keystore.properties` 或设置环境变量 `AMLIQUIDASS_RELEASE_*`。

## 在云端构建

仓库已自带 `.github/workflows/build-apk.yml`：

- push / PR / 手动触发均可
- 自动装 JDK 17 + Android SDK（platform-35 / build-tools;35.0.0）
- 跑 `:app:assembleRelease`
- 上传 artifact 名 `amliquidass-release-apk`，保留 30 天

## 安装与启用

1. 装好上面构建出来的 APK
2. 在 LSPosed（或同类 Xposed 框架）里启用「AMLiquidAss」模块
3. 作用域勾选「Apple Music」
4. 强杀 Apple Music 重新打开，底栏与 mini player 就会带液态玻璃效果

## 致谢

- [Zennmn/AM-plus-plus](https://github.com/Zennmn/AM-plus-plus) — Xposed 模块骨架与 Apple Music 目标适配知识
- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — 真·液态玻璃 AGSL 着色器实现
- License: Apache 2.0

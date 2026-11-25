# 进程杀手 · ProcessKiller

> 旧机救星：统一监控并治理后台进程、相互唤醒与后台自启，降低发热卡顿，兼顾省电。

`进程杀手` 是一款面向低配/老旧 Android 设备的后台进程治理工具。它通过 **Shizuku（shell 权限）** 或 **无线 ADB（直连本机 adbd）** 调用 `am / pm / dumpsys / ps / top` 等系统命令，对用户应用与系统应用进行统一的进程监控、规则清理与冻结管理。

- 应用名：进程杀手
- 包名：`com.w2018.procmanager`
- minSdk 26 / targetSdk 35
- 界面：Jetpack Compose 单 Activity，控件按屏宽等比缩放，适配不同分辨率

## ✨ 功能

- **权限通道**：优先 Shizuku，未就绪自动降级无线 ADB；直连本机扫描常用 ADB 端口，连接带超时与授权引导、状态诊断。
- **进程监控**：采集进程、内存、CPU、运行时长、自启动状态；支持系统应用。
- **规则引擎 + 看门狗**：内存 / 后台时长 / CPU 阈值，立即停止、关联唤醒拦截；冷却防抖；事件驱动（无障碍）+ 轮询兜底。
- **快捷操作**：一键清理（二次确认）、立即扫描（规则命中弹窗，逐项/全部清理）、冻结管理（已冻结/全部应用，点击跳系统设置，可冻结/解除）、暂停守护。
- **通知与报告**：前台常驻通知显示守护状态并提供暂停/一键清理；今日守护报告含清理次数、释放内存、监控应用、进程总数。
- **五页界面**：概览 / 应用 / 规则 / 日志 / 设置；深浅主题（跟随系统 / 深色 / 浅色）；自绘 Toast 与统一二次确认弹窗。
- **应用管理**：真实应用图标、搜索、多维筛选、按内存/CPU 排序、单应用独立规则。
- **省电策略**：非精确周期扫描、扫描瞬间短时 WakeLock、ADB 频率上限、锁屏/充电降频、采集无变化跳过。

## 🧱 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Shizuku API 13.x（`dev.rikka.shizuku`）
- dadb（纯 Kotlin ADB 客户端，直连 adbd）
- 前台服务 + 无障碍服务 + 通知栏
- 构建：Gradle（Kotlin DSL）+ AGP 8.7 + JDK 17

## 🚀 构建

```bash
# 需要 JDK 17 与 Android SDK(platform 35 / build-tools 35)
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

签名：在项目根 `local.properties` 配置（该文件不入库）：

```
release.storeFile=/abs/path/proc-release.jks
release.storePassword=****
release.keyAlias=proc
release.keyPassword=****
```

未配置签名时自动回退 `debug` 签名以便本地出包。

## 🤖 CI / 发布

`.github/workflows/android.yml`：

- `push` 到 `master` 或手动触发 → 构建 APK 并上传为工作流产物（Artifact）；
- 推送 `v*` 标签 → 自动构建并发布到 GitHub Releases 并附带 APK。

CI 通过仓库 Secrets 注入签名：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

## 📱 使用

1. 安装并打开应用；
2. 进入 **设置 → 权限通道**：Shizuku 已装并授权会自动接管；否则开启「无线调试」后点「无线 ADB → 连接」（首次需在弹窗允许 USB 调试）；
3. 打开 **守护服务**，按需调整规则与省电策略；
4. 使用「一键清理 / 立即扫描 / 冻结管理」治理后台进程。

> 无障碍服务用于前台切换事件驱动与保活；未开启时降级为纯轮询。

## 📄 许可

本项目为开源项目，仓库：<https://github.com/w2018/adb-process-killer>

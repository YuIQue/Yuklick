# Yuklick

基于 [Klick’r / Smart AutoClicker](https://github.com/Nain57/Smart-AutoClicker) 改进的 Android 自动化工具，面向顺序操作流程、条件判断和示教录制。

这是独立维护的衍生项目，不是 Klick’r 官方发行版，也不代表上游作者。上游主要作者为 Kevin Buzeau（Nain57）。本项目保留上游版权，按 **GPL-3.0-or-later** 发布。完整条款见 [LICENSE](LICENSE)，第三方来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 功能

- **操作流程**：一个场景可以包含多个事件，流程按动作顺序执行。
- **动作条件**：单击、手势、等待、输入文本、系统操作、计数器、事件状态、通知和 Intent 均可配置执行条件，支持多条件 AND / OR。
- **计数器 IF 判断**：比较计数器与数值或另一计数器，支持 `>`、`>=`、`=`、`<=`、`<`。条件满足后执行当前动作，不满足时等待，可暂停。
- **示教录制**：拦截并记录触摸、显示轨迹后延迟复现，按示教操作间隔记录等待时间。
- **两种编辑方式**：动作列表及悬浮可视化编辑；列表内可直接进入示教。
- **动作组**：批量选择、删除、组合，展开或收起，整组排序和复制。
- **文字识别**：内置简体中文及拉丁字母模型，其他字母表按需下载。
- **调试与试运行**：查看条件检测结果，试执行单个动作或从当前动作执行至末尾。
- **导入导出**：通过 ZIP 迁移场景及图像条件。

修改记录和已知限制见 [CHANGELOG.md](CHANGELOG.md)。

## 安装与使用

支持 Android 7.0（API 24）及以上。当前维护和构建目标仅为 **arm64-v8a**。仓库不包含 APK、SDK 或签名私钥；可以自行构建，或使用本仓库 Releases 中与源码版本对应的安装包（如果已提供）。

1. 按需授予无障碍服务、悬浮窗和屏幕捕获权限。
2. 创建场景、事件和动作，为需要等待的步骤设置图像、文字或计数器条件。
3. 在动作列表点击“悬浮编辑 / 示教录制”，进入可视化编辑器。
4. 用测试场景确认坐标、条件和等待时间，再运行实际流程。
5. 升级前通过导出功能备份场景。

开发安装包仍沿用上游命名空间，FDroid Debug 包名为 `com.buzbuz.smartautoclicker.debug`。不同签名的同包名 APK 无法直接覆盖安装；稳定发行前需规划独立包名、签名及数据迁移。不要直接删除尚未备份的数据。

## 构建

使用 JDK 21 和仓库提供的 Gradle Wrapper：

```sh
./gradlew :smartautoclicker:assembleFDroidDebug
```

Windows 使用 `gradlew.bat`。输出：`smartautoclicker/build/outputs/yuklick/Yuklick.apk`。完整环境和测试说明见 [BUILD.md](documentation/BUILD.md)。

## 仓库结构

```text
smartautoclicker/   应用入口、场景列表和服务
core/              识别、执行、数据、权限及公共界面
feature/           场景编辑、备份和调试等功能
build-logic/       Gradle 约定插件
gradle/            Wrapper 和依赖版本
scripts/           模型转换与公开发布检查
documentation/     构建、隐私、发布说明及许可证副本
.github/           构建工作流和协作模板
```

部分上游模块仍作为构建依赖保留；隐藏界面入口不代表模块可以直接删除。上游 Play Store 变体不属于当前交付范围。

## 隐私与反馈

识别在设备上执行。场景、识别图像、录制内容和导出备份可能包含个人信息，不要直接附到公开 Issue。语言模型下载会连接外部服务器，见 [隐私说明](documentation/PRIVACY.md)。

请在当前仓库报告问题，提供版本、Android 版本、复现步骤及脱敏截图。衍生版本的问题由本仓库维护者处理。

## 许可证与再分发

保留版权、许可证及修改说明。分发 APK 时同时提供与其对应的可构建源码、构建脚本和许可证，不能仅发布 APK。第三方组件保留各自许可。本软件按许可证约定不提供担保。

发布流程见 [RELEASING.md](documentation/RELEASING.md)。

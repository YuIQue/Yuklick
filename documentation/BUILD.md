# 构建与测试

## 环境

版本以 `gradle/libs.versions.toml` 和 `gradle/wrapper/gradle-wrapper.properties` 为准。当前配置：

| 工具 | 版本 |
|---|---|
| JDK | 21 |
| Gradle Wrapper | 9.5.1 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.4.21 |
| Android SDK Platform | 37 |
| NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| 最低 Android API | 24 |
| 交付 ABI | arm64-v8a |

安装 Android SDK 并自行接受相应协议。设置 `JAVA_HOME` 和 `ANDROID_HOME`，或在本机创建不提交的 `local.properties`。安装对应 SDK Platform、构建工具、NDK 和 CMake。首次构建需要访问 Gradle、Google Maven、Maven Central 及 GitHub；OpenCV 和 ncnn 源码由构建脚本下载。不要使用未随源码提供的本机脚本作为唯一构建入口。

## Debug APK

Linux / macOS：

```sh
chmod +x gradlew
./gradlew :smartautoclicker:assembleFDroidDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :smartautoclicker:assembleFDroidDebug
```

输出：`smartautoclicker/build/outputs/yuklick/Yuklick.apk`。此任务仅打包 arm64-v8a，使用开发环境的 Debug 签名。不同机器的 Debug 签名可能不同。源码中未包含发布私钥。

## 测试

```sh
./gradlew :core:smart:processing:testFDroidDebugUnitTest --tests '*ScenarioProcessorTests'
./gradlew :core:smart:domain:testFDroidDebugUnitTest --tests '*CounterIfConditionTest'
./gradlew :feature:smart-config:testFDroidDebugUnitTest --tests '*ActionGroupCopyTest' --tests '*ActionGroupOrderTest' --tests '*ActionExecutionConditionDialogsTest'
```

这些测试验证部分核心行为，不替代设备测试。人工回归应覆盖暂停、条件等待、示教触摸与滑动、整组排序与复制、断网中文识别、导入导出及重新进入应用后的悬浮窗。

## 发布与故障定位

正式发布使用自己的签名，通过本机环境或 GitHub Secrets 注入凭据；不要把密钥、签名密码写入仓库。保留与 APK 一致的源码版本，按 [发布说明](RELEASING.md) 校验。

若构建失败，先核对 JDK、SDK 和依赖版本及网络。Windows 路径、工具缓存及设备日志属于本机信息，不应直接提交。仓库包含 `.github/workflows/build.yml` 手动构建工作流；其在新仓库中的成功状态需要实际运行确认。

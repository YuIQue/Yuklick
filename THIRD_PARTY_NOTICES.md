# 来源、版权与第三方组件

## 上游

Yuklick 是 [Klick’r / Smart AutoClicker](https://github.com/Nain57/Smart-AutoClicker) 的修改版本，基于 4.1.0 开发树提交 `4b2d50ca2`。上游主要作者为 Kevin Buzeau（Nain57）及原项目贡献者。

源文件中的原始版权声明继续有效，不以 Yuklick 名称替代。许可证为 GNU GPL 第 3 版或更新版本（GPL-3.0-or-later），全文见 [LICENSE](LICENSE)。修改日期和范围见 [CHANGELOG.md](CHANGELOG.md)。源码修改标识不表示拥有上游全部版权。

原项目名称、商标和商店页面归各权利人所有。引用上游仅用于说明来源，不表示官方认可或关联。

## 识别组件和模型

| 组件 | 来源 / 版本 | 许可 |
|---|---|---|
| OpenCV | [opencv/opencv](https://github.com/opencv/opencv)，4.12.0 | Apache-2.0，见 `documentation/licenses/OpenCV-LICENSE.txt`；内含组件可能有独立条款 |
| ncnn | [Tencent/ncnn](https://github.com/Tencent/ncnn)，20260113 | BSD-3-Clause 及附带声明，见 `documentation/licenses/ncnn-LICENSE.txt` |
| OCR 模型及字典 | [PaddlePaddle/PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR)，经上游转换为 NCNN | PaddleOCR 的 Apache-2.0 声明见 `documentation/licenses/PaddleOCR-LICENSE.txt` |

模型原始下载地址和版本记录于 `scripts/ocr-models/downloader/models.yaml`，转换流程保留于 `scripts/ocr-models/`。本项目捆绑检测、拉丁字母和简体中文模型；简体中文使用上游仓库已有转换产物，没有将其标为 Yuklick 新训练模型。更换模型时需核对具体模型的来源、许可和配套字典。

## 其他依赖

AndroidX、Kotlin、协程、Material Components、Dagger/Hilt、Lottie、Room、Protocol Buffers 等通过 Gradle 获取。实际坐标及版本见 `gradle/libs.versions.toml` 和各模块构建文件。第三方依赖保留各自版权和许可，不因本项目 GPL 声明而被重新许可。

依赖在构建时解析。发布二进制前应核对实际依赖中的许可证及 NOTICE，并随发行包保留适用声明。本文件列出主要来源，不替代完整的第三方依赖审计。

## 再分发

保留 GPL 原文、源文件声明、依赖许可和修改说明。发行 APK 时同步提供对应源码和构建方法，见 [RELEASING.md](documentation/RELEASING.md) 及 [GPL 正文](https://www.gnu.org/licenses/gpl-3.0.html)。

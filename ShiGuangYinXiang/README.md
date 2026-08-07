# 时光印象 (ShiGuangYinXiang)

时光印象，把时间印回照片。

## 项目简介

一张照片，除了像素，还悄悄藏着一串时间与坐标。
「时光印象」读取本地照片的 EXIF 信息（拍摄时间、经纬度等），把时间以水印的形式回显到照片上，让每一帧时光重新显影。

> 时光印象 —— 记录瞬间，也印下时间。

## 目录结构

```
ShiGuangYinXiang/
├── Android/      # 安卓端
├── iOS/          # iOS 端
└── HarmonyOS/    # 鸿蒙端
```

## 核心功能（初版）

1. **读取本地图片** —— 从相册 / 文件中选择一张本地图片。
2. **解析图片附带信息** —— 提取 EXIF 元数据，包括但不限于：
   - 拍摄时间（如 `2026-08-06 14:32:00`）
   - 经纬度（如 `N 31°23'45" E 121°28'11"`）
3. **时间水印回显** —— 将拍摄时间以水印形式渲染回照片上，可导出保存。

## 规划

- [x] 读取本地图片
- [x] EXIF 信息解析（拍摄时间、经纬度）
- [x] 时间水印渲染与导出（Android）
- [ ] 多端（iOS / HarmonyOS）实现

## 开发进度

- **Android 初版已完成**（`Android/` 目录）：选图 → 解析 EXIF → 勾选水印项 → 实时预览 → 保存到相册。水印内容由用户勾选决定。
- iOS、HarmonyOS 尚未开始。

## Android 端运行

环境要求：Android Studio 2025.2.3+（内置 JDK 17），首次构建需联网下载依赖。

1. 用 Android Studio 打开 `Android/` 目录，等待 Gradle Sync。
2. 若提示缺少 `gradle-wrapper.jar`：在 Android Studio 里 `Build → Sync Project`，或任选其一补齐：
   - 任意装有 Gradle 的机器上运行 `gradle wrapper --gradle-version 9.1.0`；
   - 从 <https://raw.githubusercontent.com/gradle/gradle/v9.1.0/gradle/wrapper/gradle-wrapper.jar> 下载后放入 `gradle/wrapper/`。
3. 按 IDE 提示安装 SDK Platform 36 与 Build Tools。
4. 运行到模拟器或真机，选一张带 GPS 的照片即可体验。

技术要点：AGP 9（内置 Kotlin）+ Compose；Photo Picker 免权限选图；androidx.exifinterface 解析 EXIF；Canvas 渲染水印；MediaStore 保存到相册「时光印象」目录。

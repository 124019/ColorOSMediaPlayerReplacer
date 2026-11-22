> 请务必完整看完`README.md`，否则你不应该继续使用此程序

##  ColorOS Media Player Replacer

这是一个用于取代 **ColorOS ROM** 中自带的 **媒体播放器** ( `package:com.coloros.video` ) 的`LSPosed`模块

以下为了减少歧义，使用 `com.coloros.video` 名称指代这个系统媒体播放器

> 起因是我更改了系统的默认视频打开方式，但是大份ColorOS依旧用 `com.coloros.video` 打开我的视频，而且删掉或者禁用(Disable)它以后每次都要手动选择打开方式（不能设为永久，设置里也不行），使我非常不高兴，于是就有了这个项目。

#### 实现方式简介

使用了保留 `com.coloros.video` 作为跳板的方式，将所有试图发送至它的 **视频或音频** 全部转发到用户在系统中手动指定的视频或音频播放器，本应用使用 `Hook` ，理论上不会有很大性能开销

#### 依赖 & 环境

- **LSPosed** ( `ROOT` )

理论上只有LSPosed就能运行此程序，但是我依然要声明此仓库 **只在此环境下调试并验证可用，不做后续的任何担保**（~~好厚的叠甲~~）：
- **ROM** `ColorOS 17.0 (CN01)`
- **Device** `Oneplus Pad 2 Pro ( OPD2413 )`
- **KernelSU** `3.3.1(32680-5)`
- **Android** `Android 17`
- **Kernel** `android15-8-gc4`
- **LSPosed** `2.2.1(7912)`

#### GUI

本项目不提供任何UI，请保证你严格按照 `README.md` 要求的步骤执行以最大程度保证可用性

#### 使用说明

###### 安装前

- **确保** 已经 **完整卸载** *OPPO视频* & *OPPO音乐*
- **确保** 目标应用 `com.coloros.video`，即 `设置-应用列表（显示系统应用）` 中的 `媒体播放器 (Media Player)` 未被卸载并处于 `Enable(启用)` 状态
- **不建议** 系统默认播放器为空

###### 安装

- **ADB (推荐)** `adb install -r (path of the apk)`
- **直接安装**

###### 安装后

**LSPosed作用域** `com.coloros.video`

> 安装以后建议重启系统，或者至少应该重启（强行停止）com.coloros.video

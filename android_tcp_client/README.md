# TCP调试助手

这是从零开发的 Android TCP 调试助手，不基于第三方 APK 修改，因此没有广告 SDK、没有定位/存储/读取手机状态等多余权限。

## 功能

- TCP Client：连接云服务器，发送和接收文本
- TCP Server：监听本机端口，接收客户端连接
- 快捷发送：`openled`、`closeled`
- 普通文本发送：例如 `hello`
- 接收日志实时显示
- 地址和端口默认留空，由用户自行填写
- 右上角 `?` 提供使用教程和 GitHub 作者链接
- 右上角 `⋮` 提供切换模式、连接、断开、清空日志和快捷发送入口

## 构建

在 Windows PowerShell 中进入本目录：

```powershell
.\build_apk.ps1
```

构建成功后 APK 输出到：

```text
build\TCP调试助手.apk
```

也可以直接从 GitHub Releases 下载已构建 APK。

## 安装

手机连接电脑并打开 USB 调试后：

```powershell
adb install -r .\build\TCP调试助手.apk
```

# TCP调试助手

这是从零开发的 Android TCP 调试助手，不基于第三方 APK 修改，因此没有广告 SDK、没有定位/存储/读取手机状态等多余权限。

## 功能

- TCP Client：连接云服务器，发送和接收文本
- TCP Server：监听本机端口，接收客户端连接
- 快捷发送：`openled`、`closeled`
- 普通文本发送：例如 `hello`
- 接收日志实时显示
- 收到 `LED:ON` 自动打开手电筒，收到 `LED:OFF` 自动关闭手电筒
- 右上角 `⋮` 的灯光控制可设置 `LED:ON` 后常亮或频闪
- 频闪默认亮 500ms、灭 500ms，支持自定义亮灯/灭灯时间
- 收到 `STROBE:ON:500:500` 按指定频率频闪，收到 `STROBE:OFF` 停止频闪并关灯
- 三点菜单的“本机自测”会在手机内部临时监听 `127.0.0.1:端口`，再由内部客户端连接并收发测试数据
- 地址和端口默认留空，由用户自行填写
- 右上角 `?` 提供使用教程和项目地址
- 右上角 `⋮` 提供切换模式、连接、断开、清空日志、快捷发送、灯光控制和本机自测入口

项目地址：<https://github.com/sixiaopangai/cloud-tcp-practice>

## 权限

- `android.permission.INTERNET`：用于 TCP 网络通信
- `android.permission.CAMERA`：仅用于调用系统手电筒能力

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

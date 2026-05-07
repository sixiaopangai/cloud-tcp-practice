# APK 说明

## 生成结果

本仓库默认不提交 APK 构建产物。需要时进入 `android_tcp_client` 目录执行构建脚本：

```powershell
.\build_apk.ps1
```

构建完成后输出到 `android_tcp_client/build/TCP调试助手.apk`。

包名：

```text
cn.edu.practice.tcpclient
```

应用名称：

```text
TCP调试助手
```

项目地址：

```text
https://github.com/sixiaopangai/cloud-tcp-practice
```

## 与参考 APK 的区别

本 APK 是重新开发的，不是修改原 APK。

参考 APK 包含较多权限，例如读取手机状态、定位、存储、网络状态、安装包更新等。新 APK 只保留网络通信和手电筒联动必须的权限：

```text
android.permission.INTERNET
android.permission.CAMERA
```

因此不会包含广告 SDK，也不会请求定位、存储、读取手机状态等权限。相机权限仅用于控制手电筒。

## 功能

- TCP Client：连接云服务器 `<SERVER_PUBLIC_IP>:9999`
- TCP Server：监听手机本机端口
- 文本发送和接收日志
- 收到 `LED:ON` 自动打开手电筒
- 收到 `LED:OFF` 自动关闭手电筒
- 右上角 `⋮` 的“灯光控制”可设置 `LED:ON` 后常亮或频闪
- 收到 `STROBE:ON:500:500` 按指定频率频闪
- 收到 `STROBE:OFF` 停止频闪并关闭手电筒
- 右上角 `⋮` 的“本机自测”可在手机内部临时监听 `127.0.0.1:9999` 并由内部客户端连接验证
- 快捷发送 `openled`
- 快捷发送 `closeled`
- 快捷发送 `hello`

## 安装方式

手机打开“允许安装未知来源应用”后，直接安装：

```text
TCP调试助手.apk
```

如果使用 ADB：

```powershell
adb install -r .\TCP调试助手.apk
```

## 验证结果

构建时已通过签名验证：

```text
Verified using v1 scheme: true
Verified using v2 scheme: true
Verified using v3 scheme: true
```

包内已包含：

```text
AndroidManifest.xml
resources.arsc
classes.dex
```

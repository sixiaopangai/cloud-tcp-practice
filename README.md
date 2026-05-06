# Cloud TCP Practice

一个用于计算机网络/云服务器实训的 TCP 数据转发项目。项目包含云端多线程转发服务器、Ubuntu 设备端模拟程序，以及一个从零开发的无广告 Android TCP 调试助手。

## 功能概览

- 云服务器监听 TCP 端口，接收任意客户端数据并转发给其他在线客户端
- Ubuntu 设备端接收 `openled` / `closeled` 指令，模拟 LED 开关并回传状态
- Android TCP 调试助手支持 TCP Client、TCP Server、文本收发、快捷指令、使用教程和手电筒联动
- Android APK 申请 `android.permission.INTERNET` 和 `android.permission.CAMERA` 权限；相机权限仅用于控制手电筒
- 源码不包含个人服务器公网 IP，部署时通过运行参数或界面输入

## 目录结构

| 路径 | 说明 |
| --- | --- |
| `cloud_server/pth_server.c` | 云服务器端 TCP 多线程转发程序 |
| `device_client/client.c` | Ubuntu 设备端模拟程序 |
| `android_tcp_client/` | Android TCP 调试助手源码和无 Gradle 构建脚本 |
| `docs/提交说明.md` | 实训启动顺序、截图清单和常见问题 |
| `docs/APK说明.md` | Android APK 功能、权限和安装说明 |

## 网络拓扑

```text
Android TCP调试助手 / PC TCP Client
        |
        |  openled / closeled / text
        v
云服务器 pth_server.c  <---->  Ubuntu device_client/client.c
        ^
        |  LED:ON / LED:OFF
        |
Android TCP调试助手 / PC TCP Client
```

## 快速开始

### 1. 云服务器端

在云服务器上开放一个 TCP 端口，例如 `9999`，然后编译运行：

```bash
cd cloud_server
gcc pth_server.c -o server -lpthread
./server
```

后台运行可使用：

```bash
nohup ./server > server.log 2>&1 &
```

### 2. Ubuntu 设备端

把 `<SERVER_PUBLIC_IP>` 替换为自己的云服务器公网 IP：

```bash
cd device_client
gcc client.c -o client -lpthread
./client <SERVER_PUBLIC_IP> 9999
```

### 3. Android 调试助手

可在 GitHub Releases 下载 APK，或自行构建：

```powershell
cd android_tcp_client
.\build_apk.ps1
```

构建输出：

```text
android_tcp_client/build/TCP调试助手.apk
```

安装后以 TCP Client 模式连接：

```text
地址：<SERVER_PUBLIC_IP>
端口：9999
```

## 测试指令

发送：

```text
openled
```

预期设备端输出 LED 打开，并回传：

```text
LED:ON
```

Android 调试助手收到 `LED:ON` 后会尝试打开手机手电筒。

发送：

```text
closeled
```

预期设备端输出 LED 关闭，并回传：

```text
LED:OFF
```

Android 调试助手收到 `LED:OFF` 后会尝试关闭手机手电筒。

## 隐私说明

本仓库公开版本不会提交：

- 个人云服务器公网 IP
- 私钥、密钥对、登录凭据
- 老师提供的第三方 APK/EXE
- 本地截图、压缩包和构建缓存
- Android APK 构建产物

如果你 fork 或二次开发，请不要把自己的服务器 IP、密钥或账号信息写进公开提交。

## License

本项目基于 [MIT License](LICENSE) 开源。

## 作者

改名楠<br>
GitHub: [sixiaopangai](https://github.com/sixiaopangai)

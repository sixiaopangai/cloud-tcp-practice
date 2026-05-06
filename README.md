# 计算机实训大作业完整工程

本目录是一个基于云服务器的 TCP 数据转发实训工程，包含云端转发服务器、Ubuntu 设备端和 Android TCP 调试助手。

## 目录说明

| 路径 | 内容 |
| --- | --- |
| `cloud_server/pth_server.c` | 云服务器上运行的 TCP 多线程转发服务器 |
| `device_client/client.c` | Ubuntu 虚拟机中运行的设备端程序，接收 `openled` / `closeled` 并回传状态 |
| `android_tcp_client/` | 无广告 Android TCP 调试助手源码和构建脚本 |
| `docs/提交说明.md` | 实验步骤、截图清单和排错说明 |
| `docs/APK说明.md` | 自研无广告 TCP 调试助手的功能和安装说明 |

## 网络配置

不要把自己的真实公网 IP 写进源码或提交到公开仓库。运行时统一使用占位符：

```text
<SERVER_PUBLIC_IP>
```

默认端口示例：`9999`

部署时需要在云服务器防火墙中开放对应 TCP 端口。

## 云服务器编译运行

```bash
gcc pth_server.c -o server -lpthread
./server
```

后台运行：

```bash
nohup ./server > server.log 2>&1 &
```

## Ubuntu 设备端编译运行

```bash
gcc client.c -o client -lpthread
./client <SERVER_PUBLIC_IP> 9999
```

## 测试指令

手机或 PC TCP Client 连接 `<SERVER_PUBLIC_IP>:9999` 后发送：

```text
openled
closeled
```

预期结果：

- 发送 `openled`：Ubuntu 设备端打印 LED 打开，并回传 `LED:ON`
- 发送 `closeled`：Ubuntu 设备端打印 LED 关闭，并回传 `LED:OFF`

## 已生成 APK

无广告 TCP 调试助手已打包到：

```text
android_tcp_client/build/TCP调试助手.apk
```

该 APK 只申请 `android.permission.INTERNET` 权限。

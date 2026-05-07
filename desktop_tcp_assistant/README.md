# TCP调试助手桌面版

这是重新开发的跨平台 TCP 调试助手，不基于第三方参考工具修改或反编译代码。Windows 和 Ubuntu 都可以直接运行 Python 版本，Windows 可额外打包成独立 `.exe`。

## 功能

- TCP Client：连接云服务器或其他 TCP Server
- TCP Server：监听本机端口，接收多个客户端连接
- 文本发送/接收，支持 UTF-8 中文
- HEX 发送/接收显示
- 定时发送，最小间隔 100ms
- 快捷指令：`openled`、`closeled`、`hello`
- 手电筒/频闪指令：`LED:ON`、`LED:OFF`、`STROBE:ON:500:500`、`STROBE:OFF`
- 日志清空和保存

## Windows 运行

```powershell
cd desktop_tcp_assistant
python .\tcp_assistant.py
```

## Ubuntu 运行

Ubuntu 需要安装 Tkinter：

```bash
sudo apt update
sudo apt install -y python3-tk
cd desktop_tcp_assistant
python3 tcp_assistant.py
```

## Windows 打包 EXE

先安装 PyInstaller：

```powershell
python -m pip install pyinstaller
```

然后执行：

```powershell
.\build_windows_exe.ps1
```

输出文件：

```text
dist\TCP调试助手桌面版.exe
```

## 本次大作业用法

1. 云服务器运行 `cloud_server/pth_server.c`。
2. Ubuntu 设备端运行 `device_client/client.c`。
3. 桌面版选择 `TCP Client`，地址填写云服务器公网 IP，端口填写 `9999`。
4. 点击 `连接/监听`。
5. 发送 `openled` 或 `closeled`，观察 Ubuntu 端回传 `LED:ON` / `LED:OFF`。
6. 如果手机端也连接同一云服务器，手机会收到状态并联动手电筒或频闪。

## 说明

- 本工具不包含广告、更新检查或第三方联网 SDK。
- 本仓库不会写入个人云服务器 IP，公开提交前请不要把真实 IP 写进文档或源码。

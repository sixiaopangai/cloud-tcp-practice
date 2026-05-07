"""Cross-platform TCP debugging assistant.

Runs on Windows and Ubuntu with the Python standard library. On Windows it can
be packaged as an exe with PyInstaller.
"""

from __future__ import annotations

import queue
import socket
import threading
import time
import tkinter as tk
from datetime import datetime
from tkinter import filedialog, messagebox, ttk
from typing import List, Optional, Tuple

from tcp_core import QUICK_MESSAGES, build_endpoint, decode_payload, encode_payload, parse_port


APP_NAME = "TCP调试助手桌面版"
VERSION = "1.0.0"


class TcpAssistantApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title(f"{APP_NAME} {VERSION}")
        self.geometry("960x680")
        self.minsize(820, 560)

        self.mode_var = tk.StringVar(value="TCP Client")
        self.host_var = tk.StringVar(value="")
        self.port_var = tk.StringVar(value="9999")
        self.hex_send_var = tk.BooleanVar(value=False)
        self.hex_recv_var = tk.BooleanVar(value=False)
        self.auto_newline_var = tk.BooleanVar(value=False)
        self.timed_send_var = tk.BooleanVar(value=False)
        self.interval_var = tk.StringVar(value="1000")
        self.status_var = tk.StringVar(value="未连接")
        self.send_text_var = tk.StringVar(value="")

        self.client_socket: Optional[socket.socket] = None
        self.server_socket: Optional[socket.socket] = None
        self.server_clients: List[socket.socket] = []
        self.running = False
        self.ui_queue: "queue.Queue[Tuple[str, str]]" = queue.Queue()
        self.timed_job: Optional[str] = None

        self._build_ui()
        self._poll_ui_queue()
        self.protocol("WM_DELETE_WINDOW", self.on_close)

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(2, weight=1)

        header = ttk.Frame(self, padding=(12, 10))
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(1, weight=1)

        ttk.Label(header, text=APP_NAME, font=("", 16, "bold")).grid(row=0, column=0, sticky="w")
        ttk.Label(header, textvariable=self.status_var).grid(row=0, column=1, sticky="e")

        controls = ttk.LabelFrame(self, text="连接设置", padding=10)
        controls.grid(row=1, column=0, sticky="ew", padx=12, pady=(0, 8))
        for index in range(9):
            controls.columnconfigure(index, weight=1 if index in (3, 5) else 0)

        ttk.Label(controls, text="模式").grid(row=0, column=0, sticky="w")
        self.mode_combo = ttk.Combobox(
            controls,
            textvariable=self.mode_var,
            values=("TCP Client", "TCP Server"),
            width=14,
            state="readonly",
        )
        self.mode_combo.grid(row=0, column=1, padx=(6, 12), sticky="w")
        self.mode_combo.bind("<<ComboboxSelected>>", lambda _event: self._on_mode_change())

        ttk.Label(controls, text="地址").grid(row=0, column=2, sticky="w")
        self.host_entry = ttk.Entry(controls, textvariable=self.host_var)
        self.host_entry.grid(row=0, column=3, padx=(6, 12), sticky="ew")

        ttk.Label(controls, text="端口").grid(row=0, column=4, sticky="w")
        self.port_entry = ttk.Entry(controls, textvariable=self.port_var, width=10)
        self.port_entry.grid(row=0, column=5, padx=(6, 12), sticky="ew")

        self.connect_button = ttk.Button(controls, text="连接/监听", command=self.connect_or_listen)
        self.connect_button.grid(row=0, column=6, padx=(0, 8))
        self.disconnect_button = ttk.Button(controls, text="断开", command=self.disconnect)
        self.disconnect_button.grid(row=0, column=7, padx=(0, 8))
        ttk.Button(controls, text="清空日志", command=self.clear_log).grid(row=0, column=8)

        body = ttk.PanedWindow(self, orient=tk.HORIZONTAL)
        body.grid(row=2, column=0, sticky="nsew", padx=12, pady=(0, 8))

        log_frame = ttk.LabelFrame(body, text="收发日志", padding=8)
        log_frame.rowconfigure(0, weight=1)
        log_frame.columnconfigure(0, weight=1)
        self.log_text = tk.Text(log_frame, wrap="word", state="disabled", font=("Consolas", 10))
        self.log_text.grid(row=0, column=0, sticky="nsew")
        log_scrollbar = ttk.Scrollbar(log_frame, command=self.log_text.yview)
        log_scrollbar.grid(row=0, column=1, sticky="ns")
        self.log_text.configure(yscrollcommand=log_scrollbar.set)
        body.add(log_frame, weight=4)

        side = ttk.Frame(body, padding=(10, 0, 0, 0))
        side.columnconfigure(0, weight=1)
        body.add(side, weight=1)

        quick_frame = ttk.LabelFrame(side, text="快捷指令", padding=8)
        quick_frame.grid(row=0, column=0, sticky="ew")
        quick_frame.columnconfigure(0, weight=1)
        for index, message in enumerate(QUICK_MESSAGES):
            ttk.Button(
                quick_frame,
                text=message,
                command=lambda value=message: self.send_message(value),
            ).grid(row=index, column=0, sticky="ew", pady=2)

        options = ttk.LabelFrame(side, text="选项", padding=8)
        options.grid(row=1, column=0, sticky="ew", pady=(8, 0))
        ttk.Checkbutton(options, text="发送 HEX", variable=self.hex_send_var).grid(row=0, column=0, sticky="w")
        ttk.Checkbutton(options, text="接收 HEX", variable=self.hex_recv_var).grid(row=1, column=0, sticky="w")
        ttk.Checkbutton(options, text="发送时追加换行", variable=self.auto_newline_var).grid(row=2, column=0, sticky="w")

        timed_row = ttk.Frame(options)
        timed_row.grid(row=3, column=0, sticky="ew", pady=(6, 0))
        ttk.Checkbutton(
            timed_row,
            text="定时发送",
            variable=self.timed_send_var,
            command=self._toggle_timed_send,
        ).pack(side=tk.LEFT)
        ttk.Entry(timed_row, textvariable=self.interval_var, width=8).pack(side=tk.LEFT, padx=(8, 4))
        ttk.Label(timed_row, text="ms").pack(side=tk.LEFT)

        bottom = ttk.LabelFrame(self, text="发送区", padding=10)
        bottom.grid(row=3, column=0, sticky="ew", padx=12, pady=(0, 12))
        bottom.columnconfigure(0, weight=1)
        self.send_entry = ttk.Entry(bottom, textvariable=self.send_text_var)
        self.send_entry.grid(row=0, column=0, sticky="ew", padx=(0, 8))
        self.send_entry.bind("<Return>", lambda _event: self.send_current_message())
        ttk.Button(bottom, text="发送", command=self.send_current_message).grid(row=0, column=1, padx=(0, 8))
        ttk.Button(bottom, text="保存日志", command=self.save_log).grid(row=0, column=2)

        self._on_mode_change()

    def _on_mode_change(self) -> None:
        if self.running:
            self.disconnect()

        if self.mode_var.get() == "TCP Server":
            self.host_entry.configure(state="disabled")
            if not self.host_var.get().strip():
                self.host_var.set("0.0.0.0")
        else:
            self.host_entry.configure(state="normal")
            if self.host_var.get().strip() == "0.0.0.0":
                self.host_var.set("")

    def connect_or_listen(self) -> None:
        if self.running:
            messagebox.showinfo(APP_NAME, "已经连接或正在监听")
            return

        try:
            port = parse_port(self.port_var.get())
            if self.mode_var.get() == "TCP Client":
                host = self.host_var.get().strip()
                build_endpoint(host, port)
                self._start_client(host, port)
            else:
                self._start_server(port)
        except ValueError as exc:
            messagebox.showwarning(APP_NAME, str(exc))

    def _start_client(self, host: str, port: int) -> None:
        self.running = True
        self.status_var.set(f"正在连接 {build_endpoint(host, port)}")
        threading.Thread(target=self._client_worker, args=(host, port), daemon=True).start()

    def _client_worker(self, host: str, port: int) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            sock.settimeout(5)
            sock.connect((host, port))
            sock.settimeout(None)
            self.client_socket = sock
            self._post_log(f"已连接 {build_endpoint(host, port)}")
            self._post_status(f"已连接 {build_endpoint(host, port)}")
            self._read_socket(sock, "服务器")
        except OSError as exc:
            self._post_log(f"连接失败: {exc}")
        finally:
            self._close_socket(sock)
            if self.client_socket is sock:
                self.client_socket = None
            self._post_disconnected()

    def _start_server(self, port: int) -> None:
        self.running = True
        self.status_var.set(f"正在监听 0.0.0.0:{port}")
        threading.Thread(target=self._server_worker, args=(port,), daemon=True).start()

    def _server_worker(self, port: int) -> None:
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server.bind(("0.0.0.0", port))
            server.listen(16)
            self.server_socket = server
            self._post_log(f"TCP Server 已启动: 0.0.0.0:{port}")
            self._post_status(f"正在监听 0.0.0.0:{port}")
            while self.running:
                client, address = server.accept()
                self.server_clients.append(client)
                self._post_log(f"客户端接入: {address[0]}:{address[1]}")
                threading.Thread(
                    target=self._server_client_worker,
                    args=(client, f"{address[0]}:{address[1]}"),
                    daemon=True,
                ).start()
        except OSError as exc:
            if self.running:
                self._post_log(f"监听失败: {exc}")
        finally:
            self._close_socket(server)
            if self.server_socket is server:
                self.server_socket = None
            self._post_disconnected()

    def _server_client_worker(self, sock: socket.socket, label: str) -> None:
        try:
            self._read_socket(sock, label)
        finally:
            if sock in self.server_clients:
                self.server_clients.remove(sock)
            self._close_socket(sock)
            self._post_log(f"客户端断开: {label}")

    def _read_socket(self, sock: socket.socket, label: str) -> None:
        while self.running:
            try:
                data = sock.recv(4096)
                if not data:
                    break
                self._post_log(f"<- {label}: {decode_payload(data, self.hex_recv_var.get())}")
            except OSError as exc:
                if self.running:
                    self._post_log(f"接收中断: {exc}")
                break

    def send_current_message(self) -> None:
        self.send_message(self.send_text_var.get())

    def send_message(self, message: str) -> None:
        if not self.running:
            messagebox.showwarning(APP_NAME, "请先连接服务器或启动监听")
            return
        if self.auto_newline_var.get():
            message += "\n"
        try:
            payload = encode_payload(message, self.hex_send_var.get())
        except ValueError as exc:
            messagebox.showwarning(APP_NAME, str(exc))
            return
        if not payload:
            return

        if self.mode_var.get() == "TCP Client":
            target = self.client_socket
            if target is None:
                self._append_log("发送失败: 未连接服务器")
                return
            self._send_to_socket(target, payload)
        else:
            if not self.server_clients:
                self._append_log("发送失败: 当前没有客户端连接")
                return
            for client in list(self.server_clients):
                self._send_to_socket(client, payload)

        self._append_log(f"-> {decode_payload(payload, self.hex_send_var.get())}")

    def _send_to_socket(self, sock: socket.socket, payload: bytes) -> None:
        try:
            sock.sendall(payload)
        except OSError as exc:
            self._append_log(f"发送失败: {exc}")
            self._close_socket(sock)

    def _toggle_timed_send(self) -> None:
        if self.timed_send_var.get():
            self._run_timed_send()
        elif self.timed_job is not None:
            self.after_cancel(self.timed_job)
            self.timed_job = None

    def _run_timed_send(self) -> None:
        if not self.timed_send_var.get():
            self.timed_job = None
            return

        try:
            interval = int(self.interval_var.get().strip())
            if interval < 100:
                raise ValueError
        except ValueError:
            self.timed_send_var.set(False)
            messagebox.showwarning(APP_NAME, "定时发送间隔至少 100ms")
            return

        if self.running and self.send_text_var.get():
            self.send_current_message()
        self.timed_job = self.after(interval, self._run_timed_send)

    def disconnect(self) -> None:
        self.running = False
        self._close_socket(self.client_socket)
        self.client_socket = None
        self._close_socket(self.server_socket)
        self.server_socket = None
        for client in list(self.server_clients):
            self._close_socket(client)
        self.server_clients.clear()
        self.status_var.set("未连接")
        self._append_log("已断开")

    def clear_log(self) -> None:
        self.log_text.configure(state="normal")
        self.log_text.delete("1.0", tk.END)
        self.log_text.configure(state="disabled")

    def save_log(self) -> None:
        path = filedialog.asksaveasfilename(
            title="保存日志",
            defaultextension=".txt",
            filetypes=(("Text files", "*.txt"), ("All files", "*.*")),
        )
        if not path:
            return
        with open(path, "w", encoding="utf-8") as file:
            file.write(self.log_text.get("1.0", tk.END))

    def _append_log(self, message: str) -> None:
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_text.configure(state="normal")
        self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
        self.log_text.see(tk.END)
        self.log_text.configure(state="disabled")

    def _post_log(self, message: str) -> None:
        self.ui_queue.put(("log", message))

    def _post_status(self, message: str) -> None:
        self.ui_queue.put(("status", message))

    def _post_disconnected(self) -> None:
        self.ui_queue.put(("disconnected", "未连接"))

    def _poll_ui_queue(self) -> None:
        try:
            while True:
                kind, message = self.ui_queue.get_nowait()
                if kind == "log":
                    self._append_log(message)
                elif kind == "status":
                    self.status_var.set(message)
                elif kind == "disconnected":
                    self.running = False
                    self.status_var.set(message)
        except queue.Empty:
            pass
        self.after(100, self._poll_ui_queue)

    def on_close(self) -> None:
        self.disconnect()
        self.destroy()

    @staticmethod
    def _close_socket(sock: Optional[socket.socket]) -> None:
        if sock is None:
            return
        try:
            sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        try:
            sock.close()
        except OSError:
            pass


def main() -> None:
    app = TcpAssistantApp()
    app.mainloop()


if __name__ == "__main__":
    main()

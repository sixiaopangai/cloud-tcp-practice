"""Shared helpers for the cross-platform TCP assistant."""

from __future__ import annotations

import binascii

QUICK_MESSAGES = (
    "openled",
    "closeled",
    "hello",
    "LED:ON",
    "LED:OFF",
    "STROBE:ON:500:500",
    "STROBE:OFF",
)


def parse_port(value: str) -> int:
    """Parse a TCP port string and validate the 1-65535 range."""
    try:
        port = int(value.strip())
    except (AttributeError, ValueError) as exc:
        raise ValueError("端口必须是 1-65535 的整数") from exc

    if not 1 <= port <= 65535:
        raise ValueError("端口必须是 1-65535 的整数")
    return port


def build_endpoint(host: str, port: int) -> str:
    """Return a human-readable host:port endpoint label."""
    host = host.strip()
    if not host:
        raise ValueError("地址不能为空")
    return f"{host}:{port}"


def encode_payload(text: str, hex_mode: bool) -> bytes:
    """Encode text as UTF-8 or space-tolerant hexadecimal bytes."""
    if not hex_mode:
        return text.encode("utf-8")

    compact = "".join(text.split())
    if len(compact) == 0:
        return b""
    if len(compact) % 2 != 0:
        raise ValueError("HEX 内容长度必须是偶数")
    try:
        return binascii.unhexlify(compact)
    except binascii.Error as exc:
        raise ValueError("HEX 内容只能包含 0-9、A-F") from exc


def decode_payload(data: bytes, hex_mode: bool) -> str:
    """Decode bytes as UTF-8 text or uppercase hexadecimal."""
    if hex_mode:
        return data.hex(" ").upper()
    return data.decode("utf-8", errors="replace")

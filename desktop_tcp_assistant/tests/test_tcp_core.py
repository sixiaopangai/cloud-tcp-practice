import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tcp_core import (
    QUICK_MESSAGES,
    build_endpoint,
    decode_payload,
    encode_payload,
    parse_port,
)


class TcpCoreTest(unittest.TestCase):
    def test_parse_port_accepts_valid_range(self):
        self.assertEqual(9999, parse_port("9999"))
        self.assertEqual(1, parse_port("1"))
        self.assertEqual(65535, parse_port("65535"))

    def test_parse_port_rejects_invalid_values(self):
        for value in ("", "0", "65536", "abc"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    parse_port(value)

    def test_build_endpoint_uses_host_and_port(self):
        self.assertEqual("127.0.0.1:9999", build_endpoint("127.0.0.1", 9999))

    def test_quick_messages_cover_assignment_and_flashlight(self):
        self.assertIn("openled", QUICK_MESSAGES)
        self.assertIn("closeled", QUICK_MESSAGES)
        self.assertIn("STROBE:ON:500:500", QUICK_MESSAGES)
        self.assertIn("STROBE:OFF", QUICK_MESSAGES)

    def test_text_payload_uses_utf8(self):
        self.assertEqual("hello".encode("utf-8"), encode_payload("hello", False))
        self.assertEqual("你好", decode_payload("你好".encode("utf-8"), False))

    def test_hex_payload_ignores_spaces(self):
        self.assertEqual(b"ABC", encode_payload("41 42 43", True))
        self.assertEqual("41 42 43", decode_payload(b"ABC", True))

    def test_hex_payload_rejects_invalid_input(self):
        for value in ("4", "GG"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    encode_payload(value, True)


if __name__ == "__main__":
    unittest.main()

"""RSA PKCS#1 v1.5 加密 —— CloudOS 登录口令的唯一实现（全仓禁止复制）。

为什么集中（2026-11-04 review）：原先 buildx / web / cloudos-auth 各有一份
（openssl 子进程 + 两份纯标准库拷贝），改一处漏两处是必然。此实现为纯标准库，
与 `openssl pkeyutl -encrypt -pkeyopt rsa_padding_mode:pkcs1` 同形：
密文恒 128 字节（1024-bit）/ base64 172 字符。

公钥为 SPKI DER 的 base64（登录页 JS `encryptRSA` 用的就是这段，216 字符）。
结构：SEQ{ SEQ{OID}, BITSTRING{ SEQ{n,e} } }。
"""
from __future__ import annotations

import base64
import secrets

from ..errors import EXIT_PRECONDITION, OpsError
from . import ui

EXPECTED_B64_LEN = 172  # 1024-bit → 128 字节 → base64 172 字符


def _der_read_len(buf: bytes, i: int) -> tuple[int, int]:
    """读 DER 长度字段，返回 (长度, 下一位置)。"""
    n = buf[i]
    i += 1
    if n & 0x80:
        count = n & 0x7F
        n = int.from_bytes(buf[i:i + count], "big")
        i += count
    return n, i


def parse_pubkey(pubkey_b64: str) -> tuple[int, int]:
    """SPKI DER base64 → (n, e)。公钥坏值时抛 OpsError(前置错)。"""
    try:
        der = base64.b64decode(pubkey_b64)
        assert der[0] == 0x30                      # 外层 SEQUENCE
        _, i = _der_read_len(der, 1)
        assert der[i] == 0x30                      # 算法 SEQUENCE（跳过）
        ln, i = _der_read_len(der, i + 1)
        i += ln
        assert der[i] == 0x03                      # BIT STRING
        ln, i = _der_read_len(der, i + 1)
        assert der[i] == 0x00                      # unused-bits 计数
        i += 1
        assert der[i] == 0x30                      # RSAPublicKey SEQUENCE
        _, i = _der_read_len(der, i + 1)
        assert der[i] == 0x02                      # INTEGER n
        ln, i = _der_read_len(der, i + 1)
        n = int.from_bytes(der[i:i + ln], "big")
        i += ln
        assert der[i] == 0x02                      # INTEGER e
        ln, i = _der_read_len(der, i + 1)
        e = int.from_bytes(der[i:i + ln], "big")
        return n, e
    except Exception as exc:  # noqa: BLE001 —— 坏值/换钥未抓，统一文案
        raise OpsError("RSA 加密失败（公钥解析不出 n/e，检查 CLOUDOS_RSA_PUBKEY_B64）",
                       EXIT_PRECONDITION) from exc


def rsa_encrypt(pubkey_b64: str, plaintext: bytes, *, warn_short: bool = True) -> str:
    """明文 → PKCS#1 v1.5 → base64（1024-bit 公钥输出恒 172 字符）。"""
    n, e = parse_pubkey(pubkey_b64)
    k = (n.bit_length() + 7) // 8          # 1024-bit → 128
    ps_len = k - 3 - len(plaintext)
    if ps_len < 8:
        raise OpsError("RSA 加密失败（明文过长，超过密钥可容纳长度）", EXIT_PRECONDITION)
    # PKCS#1 v1.5 填充：0x00 0x02 || 非零随机串 || 0x00 || M
    ps = bytearray()
    while len(ps) < ps_len:
        ps.extend(b for b in secrets.token_bytes(ps_len - len(ps)) if b != 0)
    em = b"\x00\x02" + bytes(ps) + b"\x00" + plaintext
    enc = pow(int.from_bytes(em, "big"), e, n).to_bytes(k, "big")
    out = base64.b64encode(enc).decode("ascii")
    if warn_short and len(out) != EXPECTED_B64_LEN:
        ui.log_warn(f"RSA 密文长度 {len(out)}（预期 {EXPECTED_B64_LEN}）——公钥可能非 1024 位")
    return out


def rsa_encrypt_str(pubkey_b64: str, plaintext: str) -> str:
    """字符串便捷入口（UTF-8）。"""
    return rsa_encrypt(pubkey_b64, plaintext.encode("utf-8"))

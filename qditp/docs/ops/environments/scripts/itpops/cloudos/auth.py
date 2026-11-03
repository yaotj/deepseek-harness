"""CloudOS 认证 —— lib/cloudos-auth.sh 的 Python 移植（RSA 加密 + 登录 + token 缓存）。

【认证模型（红岛 2026-10-23 实测；测试 2026-11-02 验证同构）】
  POST /api/sys/oapi/v1/double_factor/login
  Body: {"username","rsa":true,"password":"<RSA-1024 密文 base64>","domain":"default"}
  业务请求头：X-Auth-Token: <res.token>
  密码 MUST 真 RSA 加密（密文恒 128 字节 / 172 字符）。base64 明文会返回
  usrmgt.userOrPasswordError —— 看着像密码错，实际是没解密。

【RSA 公钥】1024 bit，PKCS#8 SubjectPublicKeyInfo base64（216 字符）。
  两套环境共用同一把（2026-11-02 实测：测试与红岛登录页 encryptRSA 常量逐字符一致）。
  加密走 openssl pkeyutl（PKCS#1 v1.5），与 bash 版同一工具、同一填充。

【token 缓存】${TMPDIR:-/tmp}/.cloudos-<env>.token，内容 `<时间戳> <token>`，
  TTL 默认 600s（CLOUDOS_TOKEN_TTL 可调）。新旧两版共用同一缓存文件。

【与 bash 版的差异】（详见包 docstring；本文件相关点）
  · bash `_cloudos_login_once` 把 python traceback（2>&1）当 die 消息；Python 版
    改为明确的 OpsError 文案（仅影响响应非 JSON 的极端错误路径）。
  · bash 的 `[[ -z "$CLOUDOS_PASSWORD" ]]`/公钥空检查在 `${VAR:-默认}` 之后属
    死代码（空值已回落默认）；Python 版保留同结构的防御检查（同样不可达）。
"""
from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass

from ..errors import EXIT_EXEC, EXIT_PRECONDITION, EXIT_TRANSPORT, OpsError

# CloudOS 登录公钥（1024 bit，PKCS#8 SubjectPublicKeyInfo base64，216 字符）
CLOUDOS_RSA_PUBKEY_SHARED = (
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCbPqaFZ0lgebihxCw5wzjkahPKr6ZI2JNTHbHDnNbOn3"
    "Cif6GgnMBr44MhQCRPQTF2FgcxBbn3u7eGcEPMT6OZqcLzcmiMCUQRJ2MUbFI+E1vI1f+iuCQ2w8XZhRoR"
    "rmb7wBA0L63gnSwXRkJD0baL5zqlQhpSKfSH0t3opAoahwIDAQAB"
)
# 历史常量名（红岛单环境时代），保留给外部引用
HONGDAO_RSA_PUBKEY_B64 = CLOUDOS_RSA_PUBKEY_SHARED

_PROFILE_DEFAULTS = {
    "hongdao": {
        "host": "172.120.209.151",
        "username": "itpbadmin",
        "password": "QDitp@123!@#",
    },
    "test": {
        "host": "172.20.217.210",
        "username": "admin",
        "password": "Qdmlc123!@#",
    },
}


@dataclass(frozen=True)
class CloudosConfig:
    """一次执行的 CloudOS 连接配置（env_init 后只读）。"""

    env: str
    scheme: str
    host: str
    username: str
    password: str
    domain: str
    timeout: int
    pubkey_b64: str
    base_url: str
    token_cache: str
    token_ttl: int


_CFG: CloudosConfig | None = None


def _env_or(key: str, default: str) -> str:
    # bash `${VAR:-默认}`：设为空也回落默认
    return os.environ.get(key) or default


def env_init(env_name: str) -> CloudosConfig:
    """应用环境 profile（幂等）。env_name = hongdao | test。"""
    global _CFG

    profile = _PROFILE_DEFAULTS.get(env_name)
    if profile is None:
        raise OpsError(
            f"未知 CLOUDOS_ENV={env_name}（仅 hongdao / test）", EXIT_PRECONDITION
        )

    scheme = _env_or("CLOUDOS_SCHEME", "https")
    domain = _env_or("CLOUDOS_DOMAIN", "default")
    timeout = int(_env_or("CLOUDOS_TIMEOUT", "30"))
    token_ttl = int(_env_or("CLOUDOS_TOKEN_TTL", "600"))
    host = _env_or("CLOUDOS_HOST", profile["host"])
    username = _env_or("CLOUDOS_USERNAME", profile["username"])
    password = _env_or("CLOUDOS_PASSWORD", profile["password"])
    pubkey_b64 = _env_or("CLOUDOS_RSA_PUBKEY_B64", CLOUDOS_RSA_PUBKEY_SHARED)

    # 以下两个防御检查在 `${VAR:-默认}` 语义下不可达（与 bash 同构保留）：
    if not pubkey_b64:
        raise OpsError(
            f"环境 {env_name} 的 RSA 公钥为空（被显式置空？）。\n"
            "  两套环境默认共用同一把公钥，NEVER 需要手工提供——把 CLOUDOS_RSA_PUBKEY_B64= 去掉即可。\n"
            "  平台升级换钥后重抓：登录页 F12 → Network → double_factor/login\n"
            "  → 前端 JS 搜 encryptRSA，取 1024-bit PKCS#8 base64（216 字符），\n"
            "   export CLOUDOS_RSA_PUBKEY_B64=<值> 后重试。",
            EXIT_PRECONDITION,
        )
    if not password:
        raise OpsError(
            f"CLOUDOS_PASSWORD 被显式置空（环境 {env_name}）。\n"
            "  仓库内已有该环境的现值，NEVER 需要手工提供——把 export CLOUDOS_PASSWORD= 去掉即可。",
            EXIT_PRECONDITION,
        )

    base_url = f"{scheme}://{host}"
    token_cache = _env_or(
        "TOKEN_CACHE",
        os.path.join(os.environ.get("TMPDIR") or "/tmp", f".cloudos-{env_name}.token"),
    )
    _CFG = CloudosConfig(
        env=env_name,
        scheme=scheme,
        host=host,
        username=username,
        password=password,
        domain=domain,
        timeout=timeout,
        pubkey_b64=pubkey_b64,
        base_url=base_url,
        token_cache=token_cache,
        token_ttl=token_ttl,
    )
    return _CFG


def cfg() -> CloudosConfig:
    if _CFG is None:
        raise OpsError("CloudOS 环境未初始化（域需先调用 cloudos.auth.env_init）", EXIT_PRECONDITION)
    return _CFG


def _write_pubkey_pem(path: str, pubkey_b64: str) -> None:
    with open(path, "w", encoding="ascii") as f:
        f.write(f"-----BEGIN PUBLIC KEY-----\n{pubkey_b64}\n-----END PUBLIC KEY-----\n")


def rsa_encrypt(plaintext: str) -> str:
    """明文 -> PKCS#1 v1.5 -> base64（密文恒 128 字节 / 172 字符）。"""
    c = cfg()
    # bash 用全局 PEM 文件 + trap 清理（子 shell trap 坑）；Python 用临时文件即用即删
    fd, pem_path = tempfile.mkstemp(prefix="cloudos-pub-", suffix=".pem")
    os.close(fd)
    try:
        _write_pubkey_pem(pem_path, c.pubkey_b64)
        try:
            p = subprocess.run(
                [
                    "openssl", "pkeyutl", "-encrypt", "-pubin", "-inkey", pem_path,
                    "-pkeyopt", "rsa_padding_mode:pkcs1",
                ],
                input=plaintext.encode("utf-8"),
                capture_output=True,
            )
        except OSError as exc:
            raise OpsError(f"RSA 加密失败（openssl 不可用？）：{exc}", EXIT_PRECONDITION) from exc
        if p.returncode != 0:
            raise OpsError("RSA 加密失败（openssl 不可用？）", EXIT_PRECONDITION)
        return base64.b64encode(p.stdout).decode("ascii")
    finally:
        try:
            os.unlink(pem_path)
        except OSError:
            pass


def pubkey_fingerprint() -> str:
    """公钥 DER 的 sha256 指纹（十六进制）。

    bash: openssl pkey -outform DER | openssl dgst -sha256 -r | cut -d' ' -f1。
    公钥 base64 本身就是 SPKI DER，故对 base64 解码直接取 sha256 等价。
    """
    c = cfg()
    try:
        der = base64.b64decode(c.pubkey_b64)
    except Exception:  # noqa: BLE001 —— 公钥坏时给空指纹（bash 对应 openssl 失败空输出）
        return ""
    import hashlib

    return hashlib.sha256(der).hexdigest()


def _login_once() -> str:
    """真发登录请求，成功返回 token；失败抛 OpsError。"""
    c = cfg()
    enc = rsa_encrypt(c.password)
    # 长度不对即公钥或填充不对；不拦死只告警（平台升级换公钥时仍值得先看真实报错）
    if len(enc) != 172:
        print(
            f"警告：RSA 密文长度 {len(enc)}（预期 172），公钥可能是 2048 位或填充方式不符",
            file=sys.stderr,
        )

    # 与 bash printf 同构的字节级载荷（键序一致、无多余空白）
    body = (
        '{"username":"%s","rsa":true,"password":"%s","domain":"%s"}'
        % (c.username, enc, c.domain)
    )

    from . import http as http_mod

    code, rbody = http_mod.post_login(body)

    if http_mod.is_net_fail(code):
        if c.env == "test":
            raise OpsError(
                f"连不上 {c.host} —— 测试网需能路由 172.20.x（办公网 en0 或公司内网）",
                EXIT_TRANSPORT,
            )
        raise OpsError(
            f"连不上 {c.host} —— 查网卡（红岛需插 K8s 控制区 172.120.2.x）",
            EXIT_TRANSPORT,
        )
    if code != "200":
        raise OpsError(f"登录 HTTP {code}：{rbody[:400]}", EXIT_EXEC)

    try:
        d = json.loads(rbody)
    except ValueError as exc:
        # bash 此处以 traceback 为 die 消息；Python 版给明确文案
        raise OpsError(f"登录未返回合法 JSON：{rbody[:300]}", EXIT_EXEC) from exc

    if not isinstance(d, dict) or d.get("status") is not True:
        msg = f"登录业务失败：code={d.get('code') if isinstance(d, dict) else None}" \
              f" msg={d.get('msg') if isinstance(d, dict) else None}"
        if isinstance(d, dict) and d.get("code") == "usrmgt.userOrPasswordError":
            msg += (
                "\n  排查顺序：① 账号是否为该环境的正确账号（红岛 itpbadmin / 测试 admin；\n"
                "             itpadmin 是 Harbor 推送账号，不是平台账号）\n"
                "           ② 密码是否正确\n"
                "           ③ 密码是否真的 RSA 加密（密文应 172 字符；base64 明文会走到这条错）\n"
                "           ④ domain 是否为 default（传空返回 svcmgt.internalServerError）"
            )
        raise OpsError(msg, EXIT_EXEC)

    token = (d.get("res") or {}).get("token") or ""
    if not token:
        raise OpsError(f"登录未返回 token：{rbody[:300]}", EXIT_EXEC)
    return token


def get_token(fresh: str = "") -> str:
    """取 token：优先缓存，过期/不存在则重登。fresh="--fresh" 强制重登。"""
    c = cfg()

    if fresh != "--fresh":
        token = _read_cache(c)
        if token:
            return token

    token = _login_once()

    try:
        os.makedirs(os.path.dirname(c.token_cache) or ".", exist_ok=True)
        fd = os.open(c.token_cache, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(f"{int(time.time())} {token}")
        os.chmod(c.token_cache, 0o600)
    except OSError:
        pass  # 缓存写失败不阻断（bash 同：写失败仍输出 token）
    return token


def _read_cache(c: CloudosConfig) -> str | None:
    try:
        with open(c.token_cache, encoding="utf-8") as f:
            line = f.readline().rstrip("\n")
    except OSError:
        return None
    if " " not in line:
        return None  # 旧格式（无时间戳前缀）一律当失效
    ts_s, cached = line.split(" ", 1)
    try:
        ts = int(ts_s)
    except ValueError:
        return None
    age = int(time.time()) - ts
    if 0 <= age < c.token_ttl and cached and " " not in cached:
        return cached
    return None

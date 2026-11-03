r"""itpops · buildx —— 红岛 CloudOS 镜像构建（buildx 端点，platform=linux/amd64）。

移植自 itp-ops.sh buildx（bash）+ lib/{core,env,cloudos-auth,cloudos-http}.sh。
独立入口（不经 itpops cli 分发）：`itp-ops.sh buildx …`；薄壳
itp-ops.sh buildx 转调本模块，命令面与旧 bash 逐字兼容。

【为什么不用 itp-ops.sh cloudos image build】（沿自 bash 头注，IT-024）
  旧命令走 /api/cloud/hcr/v1.0/images/build（红岛 404 / img.5）。红岛可用的构建
  通道是 Web 控制台「镜像构建」背后的 buildx 端点：
    POST /api/cloud/hcr/v1.0/images/file/upload   multipart 上传构建上下文
        fileFlag 走请求头（客户端自生成 uuid），响应 data.id
    POST /api/cloud/hcr/v1.0/images/buildx        body 含 dockerfile/imgName/
        version/imgType/type/fileFlag/project/platform/description
  平台 x86 节点 buildx 构建并推 Harbor（:443）；platform=linux/amd64 可指定架构。

【构建上下文形态（2026-10-28 实证变更）】
  直接上传 jar 文件本身（文件名 app.jar），NEVER 打 tar——tar 上下文现一律回
  img.5（status=false）。与 Web 端 el-upload 直传文件的形态对齐后恢复。

【坑/巧思（保留原因）】
  · 首行 FROM 按环境替换：共享 Dockerfile 模板只写红岛值，读取后只换**第一个**
    FROM 行（awk `!done && /^\s*FROM\s/`，丢弃原行其余内容——多阶段 AS 别名也
    会被丢，这是 bash 原行为）。基础镜像按环境取（环境事实，不是模块事实）：
    红岛 itpb/jdk21:1.0.0 / 测试 itp/jdk:25。
  · 默认 imgType uuid 20e68cab-… 是 hcr 内置「Default」，红岛与测试恰好相同；
    打别的分类用 --img-type 显式指定，NEVER 靠猜。
  · 轮询响应形态（2026-10-28 现查）：data 是 dict，列表在 data.images[]，版本
    字段叫 versions（多 tag 逗号串），NEVER 按 data[] + version 解析。
  · upload 响应成功时 data 为 ""（仅 ack）；fileFlag 客户端自生成，直接作 buildx
    引用（DATA_ID=$FILEFLAG）。
  · buildx 提交响应不做 status 校验（bash 原行为：提交后直接轮询产物）。
  · 「版本末位 MUST +0.0.1、NEVER 同 tag 覆盖」是使用纪律，脚本不代查
    （--check 可先看 tag 占用）。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 失败退出码细分：bash 一律 die→1、轮询超时→2；Python 按 errors.py 契约
     抛 OpsError——用法 2、前置 3（缺 jar/Dockerfile、无 FROM、非法 zip、
     非 fat jar）、传输 4（连不上平台）、执行 5（登录/upload 业务失败、轮询
     超时——bash 的 exit 2 与契约 USAGE 码冲突，归入 EXEC）。成功 0。
  2. -h/--help 新增：bash 无 help 选项（--help 会被当模块名走缺参用法错）；
     Python 在首参为 -h/--help 时打用法到 stderr（stdout 恒空，与 legacy 对
     diff 为空）、exit 0。用法文案沿用 bash ${1:?} 的原文。
  3. 选项缺值：bash（set -u）对 --jar/--dockerfile/--project/--img-type/
     --base-image 缺值直接 `$2: unbound variable` 退出（exit 1，shell 诊断噪声）；
     Python 报「{opt} 需要值」用法错（exit 2）。--env 缺值文案沿用 bash 原文
     「--env 需要值（hongdao|test）」。
  4. RSA 加密纯标准库实现（PKCS#1 v1.5，密文恒 128 字节 / base64 172 字符），
     不再调 openssl pkeyutl；登录/加密失败文案沿用 bash 原文。
  5. unzip CLI 校验 fat jar 改为 zipfile（纯标准库，同 cloudos 域先例），判定
     与报错文案一致（grep 的子串语义 = 条目名含 "BOOT-INF/classes"）。
  6. --check 的网络失败：bash 里 curl 失败也 exit 0（管道末端 json.tool 对空
     输入报错被吞）；Python 按契约报传输 4。
  7. 用法/错误文案里的 $0 固定为命令名 itp-ops.sh buildx（bash 是调用路径，
     随调用方式漂移）；bash ${var:?} 的「bash: 1: 」shell 诊断前缀不复刻，
     保留其 msg 正文。mktemp -d 的随机路径文本天然不同。
"""
from __future__ import annotations

import base64
import json
import os
import secrets
import shutil
import ssl
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from pathlib import Path

from .core import envx, ui
from .errors import (
    EXIT_EXEC,
    EXIT_OK,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

_CMD_NAME = "itp-ops.sh buildx"

# 默认 imgType：hcr 内置「Default」（红岛与测试该 uuid 恰好相同）。
IMG_TYPE_DEFAULT = "20e68cab-c634-468d-8d0c-56cb6096"

# 基础镜像按环境取（环境事实，不是模块事实）。
BASE_IMAGE_HONGDAO = "os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0"
BASE_IMAGE_TEST = "os-harbor-svc.default.svc.cloudos:443/itp/jdk:25"

_USAGE = (
    "用法: {_CMD} <module> <version> [--env hongdao|test] [--jar <jar>] "
    "[--dockerfile <df>] [--project <name>] [--img-type <uuid>] "
    "[--base-image <ref>] [--check]"
).replace("{_CMD}", _CMD_NAME)

CATALOG = {
    "name": "buildx",
    "summary": "镜像构建（CloudOS buildx 端点，platform=linux/amd64，jar 直传上下文；红岛/测试双环境，幂等、不设 --yes 门）",
    "default_env": "hongdao",
    "subcommands": [],
    "usage": (
        "buildx <module> <version> [--env hongdao|test] "
        "[--jar <jar>] [--dockerfile <df>] [--project <name>] "
        "[--img-type <uuid>] [--base-image <ref>] [--check]"
    ),
    "args": [
        {"name": "module", "required": True, "summary": "模块名（镜像名，如 route-sentinel）"},
        {"name": "version", "required": True, "summary": "版本 tag（MUST 末位 +0.0.1，NEVER 同 tag 覆盖）"},
        {"name": "--env", "required": False, "summary": "hongdao|test，默认 hongdao（显式后强制 profile 值）"},
        {"name": "--jar", "required": False, "summary": "jar 路径，默认 <repo>/<module>/target/<module>-<version>.jar"},
        {"name": "--dockerfile", "required": False, "summary": "Dockerfile 路径，默认 <repo>/<module>/Dockerfile（首行 FROM 按环境替换）"},
        {"name": "--project", "required": False, "summary": "Harbor 项目覆盖，默认取环境 profile（hongdao=itpb / test=itp）"},
        {"name": "--img-type", "required": False, "summary": "hcr 镜像类型 uuid，默认内置 Default"},
        {"name": "--base-image", "required": False, "summary": "基础镜像覆盖"},
        {"name": "--check", "required": False, "summary": "只读：只查现有镜像，不发写请求"},
    ],
    "writes": False,
    "example": "itp-ops.sh buildx route-sentinel 1.0.2 --env hongdao",
    "env_note": "默认 hongdao；打 CloudOS（登录/上传/构建/轮询），环境横幅含平台账号与基础镜像",
}


# ---------------------------------------------------------------- 输出小工具
def _err(text: str) -> None:
    """bash `printf … >&2` 等价。"""
    print(text, file=sys.stderr)


def _die_msg(msg: str) -> None:
    """bash die 等价（红色「错误：」前缀；OpsError 消息若已带前缀则不重复）。"""
    prefix = "\033[0;31m错误：\033[0m" if sys.stderr.isatty() else "错误："
    if msg.startswith("错误："):
        print(msg, file=sys.stderr)
    else:
        print(f"{prefix}{msg}", file=sys.stderr)


def _head_c(text: str, n: int) -> str:
    """bash `head -c N` 字节截断等价（UTF-8 处用替换符防裂字）。"""
    return text.encode("utf-8", "replace")[:n].decode("utf-8", "replace")


# ------------------------------------------------- CloudOS 认证（自包含实现）
# RSA 公钥（1024 bit，PKCS#8 SubjectPublicKeyInfo base64，216 字符）——
# 红岛与测试同一把（登录页 encryptRSA 内联常量，2026-11-02 复核逐字符一致）。
CLOUDOS_RSA_PUBKEY_SHARED = (
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCbPqaFZ0lgebihxCw5wzjkahPKr6ZI2JNTHbHDnNbOn3Cif6GgnMBr44MhQCRPQTF2FgcxBbn3u7eGcEPMT6OZqcLzcmiMCUQRJ2MUbFI+E1vI1f+iuCQ2w8XZhRoRrmb7wBA0L63gnSwXRkJD0baL5zqlQhpSKfSH0t3opAoahwIDAQAB"
)

# 环境 profile（cloudos-auth.sh 的 cloudos_env_init 对应）；环境变量可覆盖任何值。
_CLOUDOS_PROFILES = {
    "hongdao": {"host": "172.120.209.151", "username": "itpbadmin", "password": "QDitp@123!@#"},
    "test": {"host": "172.20.217.210", "username": "admin", "password": "Qdmlc123!@#"},
}

_TOKEN_TTL = 600  # 秒；平台侧实际更长，保守 10 分钟


class _CloudOS:
    """登录/token 缓存/请求通道（lib/cloudos-{auth,http}.sh 的语义等价，自包含）。

    不 import itpops.cloudos（并行开发隔离），需要的能力在此自给。
    """

    def __init__(self) -> None:
        self.scheme = os.environ.get("CLOUDOS_SCHEME", "https")
        self.domain = os.environ.get("CLOUDOS_DOMAIN", "default")
        self.timeout = int(os.environ.get("CLOUDOS_TIMEOUT", "30"))
        self.env_name = "hongdao"
        self.host = ""
        self.username = ""
        self.password = ""
        self.pubkey_b64 = ""
        self.base_url = ""
        self.token_cache = ""
        self.login_exit_code = EXIT_EXEC  # 最近一次登录失败的细分码

    def env_init(self, env_name: str) -> None:
        """应用环境 profile（幂等）。公钥/口令被显式置空即拒（绝不静默降级）。"""
        profile = _CLOUDOS_PROFILES.get(env_name)
        if profile is None:
            raise OpsError(f"未知 CLOUDOS_ENV={env_name}（仅 hongdao / test）", EXIT_USAGE)
        self.env_name = env_name
        self.host = os.environ.get("CLOUDOS_HOST") or profile["host"]
        self.username = os.environ.get("CLOUDOS_USERNAME") or profile["username"]
        self.password = os.environ.get("CLOUDOS_PASSWORD") or profile["password"]
        self.pubkey_b64 = (
            os.environ.get("CLOUDOS_RSA_PUBKEY_B64") or CLOUDOS_RSA_PUBKEY_SHARED
        )
        if not self.pubkey_b64:
            _err(f"错误：环境 {env_name} 的 RSA 公钥为空（被显式置空？）。")
            _err("  两套环境默认共用同一把公钥，NEVER 需要手工提供——把 CLOUDOS_RSA_PUBKEY_B64= 去掉即可。")
            _err("  平台升级换钥后重抓：登录页 F12 → Network → double_factor/login")
            _err("  → 前端 JS 搜 encryptRSA，取 1024-bit PKCS#8 base64（216 字符），")
            _err("   export CLOUDOS_RSA_PUBKEY_B64=<值> 后重试。")
            raise OpsError("CLOUDOS_RSA_PUBKEY_B64 被显式置空", EXIT_PRECONDITION)
        if not self.password:
            _err(f"错误：CLOUDOS_PASSWORD 被显式置空（环境 {env_name}）。")
            _err("  仓库内已有该环境的现值，NEVER 需要手工提供——把 export CLOUDOS_PASSWORD= 去掉即可。")
            raise OpsError("CLOUDOS_PASSWORD 被显式置空", EXIT_PRECONDITION)
        self.base_url = f"{self.scheme}://{self.host}"
        self.token_cache = os.environ.get(
            "TOKEN_CACHE", f"{os.environ.get('TMPDIR', '/tmp')}/.cloudos-{env_name}.token"
        )

    # ---- RSA（纯标准库：PKCS#1 v1.5，密文恒 128 字节 / base64 172 字符）----
    @staticmethod
    def _der_read_len(buf: bytes, i: int) -> tuple[int, int]:
        n = buf[i]
        i += 1
        if n & 0x80:
            count = n & 0x7F
            n = int.from_bytes(buf[i:i + count], "big")
            i += count
        return n, i

    def _rsa_parse_pubkey(self, pubkey_b64: str) -> tuple[int, int]:
        """SPKI DER → (n, e)。结构：SEQ{ SEQ{OID}, BITSTRING{ SEQ{n,e} } }。"""
        try:
            der = base64.b64decode(pubkey_b64)
            # 外层 SEQUENCE
            assert der[0] == 0x30
            _, i = self._der_read_len(der, 1)
            # 算法 SEQUENCE（跳过）
            assert der[i] == 0x30
            ln, i = self._der_read_len(der, i + 1)
            i += ln
            # BIT STRING（0x03），首字节是 unused-bits 计数
            assert der[i] == 0x03
            ln, i = self._der_read_len(der, i + 1)
            assert der[i] == 0x00
            i += 1
            # RSAPublicKey SEQUENCE{ n, e }
            assert der[i] == 0x30
            _, i = self._der_read_len(der, i + 1)
            assert der[i] == 0x02
            ln, i = self._der_read_len(der, i + 1)
            n = int.from_bytes(der[i:i + ln], "big")
            i += ln
            assert der[i] == 0x02
            ln, i = self._der_read_len(der, i + 1)
            e = int.from_bytes(der[i:i + ln], "big")
            return n, e
        except Exception as exc:  # noqa: BLE001 —— 公钥坏值/换钥未抓，统一文案
            raise OpsError("RSA 加密失败（openssl 不可用？）", EXIT_PRECONDITION) from exc

    def rsa_encrypt(self, plaintext: str) -> str:
        """明文 → PKCS#1 v1.5 密文 base64（与 openssl pkeyutl -encrypt 同形）。"""
        n, e = self._rsa_parse_pubkey(self.pubkey_b64)
        k = (n.bit_length() + 7) // 8  # 1024-bit → 128
        msg = plaintext.encode("utf-8")
        ps_len = k - 3 - len(msg)
        if ps_len < 8:
            raise OpsError("RSA 加密失败（openssl 不可用？）", EXIT_PRECONDITION)
        ps = bytearray()
        while len(ps) < ps_len:
            b = secrets.token_bytes(ps_len - len(ps))
            ps.extend(x for x in b if x != 0)
        em = b"\x00\x02" + bytes(ps) + b"\x00" + msg
        enc = pow(int.from_bytes(em, "big"), e, n).to_bytes(k, "big")
        out = base64.b64encode(enc).decode()
        if len(out) != 172:
            _err(
                f"警告：RSA 密文长度 {len(out)}（预期 172），公钥可能是 2048 位或填充方式不符"
            )
        return out

    # ---- 登录与 token ----
    def _login_once(self) -> str:
        """真发登录请求；成功返回 token。失败复刻 bash __cloudos_die 文案并抛错。"""
        try:
            enc = self.rsa_encrypt(self.password)
        except OpsError as exc:
            _die_msg(str(exc))
            raise OpsError("登录失败", EXIT_PRECONDITION) from None
        body = json.dumps(
            {"username": self.username, "rsa": True, "password": enc, "domain": self.domain},
            separators=(",", ":"),
        )
        code, rbody = _http(
            "POST",
            f"{self.base_url}/api/sys/oapi/v1/double_factor/login",
            data=body.encode(),
            headers={"Content-Type": "application/json", "Accept": "application/json"},
            timeout=self.timeout,
        )
        if code in (None, "000"):
            if self.env_name == "test":
                _die_msg("连不上 %s —— 测试网需能路由 172.20.x（办公网 en0 或公司内网）" % self.host)
            else:
                _die_msg("连不上 %s —— 查网卡（红岛需插 K8s 控制区 172.120.2.x）" % self.host)
            raise OpsError("登录失败", EXIT_TRANSPORT)
        if code != "200":
            _die_msg(f"登录 HTTP {code}：{_head_c(rbody, 400)}")
            raise OpsError("登录失败", EXIT_EXEC)
        try:
            d = json.loads(rbody)
        except ValueError:
            _die_msg(f"登录未返回 token：{_head_c(rbody, 300)}")
            raise OpsError("登录失败", EXIT_EXEC) from None
        if d.get("status") is not True:
            _die_msg(f"登录业务失败：code={d.get('code')} msg={d.get('msg')}")
            if d.get("code") == "usrmgt.userOrPasswordError":
                _err("  排查顺序：① 账号是否为该环境的正确账号（红岛 itpbadmin / 测试 admin；")
                _err("             itpadmin 是 Harbor 推送账号，不是平台账号）")
                _err("           ② 密码是否正确")
                _err("           ③ 密码是否真的 RSA 加密（密文应 172 字符；base64 明文会走到这条错）")
                _err("           ④ domain 是否为 default（传空返回 svcmgt.internalServerError）")
            raise OpsError("登录失败", EXIT_EXEC)
        token = (d.get("res") or {}).get("token") or ""
        if not token:
            _die_msg(f"登录未返回 token：{_head_c(rbody, 300)}")
            raise OpsError("登录失败", EXIT_EXEC)
        return token

    def get_token(self, fresh: bool = False) -> str | None:
        """取 token：优先缓存（TTL 600s），过期/缺失/fresh 则重登。失败打印原因后返回 None。"""
        if not fresh:
            try:
                raw = Path(self.token_cache).read_text()
                ts_s, _, cached = raw.partition(" ")
                age = time.time() - int(ts_s)
                # 旧格式（无时间戳前缀）会读成空或整串，一律当失效
                if 0 <= age < _TOKEN_TTL and cached and " " not in cached:
                    return cached
            except (OSError, ValueError):
                pass
        try:
            token = self._login_once()
        except OpsError as exc:
            self.login_exit_code = exc.exit_code
            return None
        try:
            cache = Path(self.token_cache)
            cache.parent.mkdir(parents=True, exist_ok=True)
            fd = os.open(str(cache), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            with os.fdopen(fd, "w") as fh:
                fh.write(f"{int(time.time())} {token}")
        except OSError:
            pass
        return token


def _http(
    method: str,
    url: str,
    *,
    data: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 30,
) -> tuple[str, str]:
    """curl -sSk 等价：返回 (http_code, body)；网络失败 → ("000", "")。"""
    req = urllib.request.Request(url, data=data, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    ctx = ssl._create_unverified_context()  # curl -k
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            return str(resp.status), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace") if exc.fp else ""
        return str(exc.code), body
    except (urllib.error.URLError, OSError, ValueError):
        return "000", ""


def _http_token(
    cos: _CloudOS,
    method: str,
    uri: str,
    *,
    data: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: int | None = None,
) -> tuple[str, str]:
    """带 X-Auth-Token 的请求（token 失效自动 --fresh 重登一次，同 cloudos_http 语义）。"""
    token = cos.get_token()
    if token is None:
        return "000", ""
    hdrs = {"X-Auth-Token": token}
    hdrs.update(headers or {})
    code, body = _http(
        method, f"{cos.base_url}{uri}", data=data, headers=hdrs,
        timeout=timeout or cos.timeout,
    )
    if code in ("401", "403"):
        token = cos.get_token(fresh=True)
        if token is None:
            return "000", ""
        hdrs["X-Auth-Token"] = token
        code, body = _http(
            method, f"{cos.base_url}{uri}", data=data, headers=hdrs,
            timeout=timeout or cos.timeout,
        )
    return code, body


def _multipart_file(field: str, filename: str, data: bytes) -> tuple[bytes, str]:
    """拼 multipart/form-data 单文件表单（curl -F "file=@f" 同形）。"""
    boundary = uuid.uuid4().hex
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; '
        f'filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n'
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    return body, boundary


# ---------------------------------------------------------------- Dockerfile/jar
def _replace_from(raw: str, base_image: str) -> tuple[str, str]:
    """awk 等价：替换**第一个** FROM 行为 `FROM <base>`，返回 (新文本, 文件内 FROM 值)。

    bash 的 awk 丢弃被替换行的其余内容（多阶段 `AS builder` 一并丢弃）——保留原行为。
    """
    from_in_file = ""
    lines = raw.splitlines(keepends=True)
    out: list[str] = []
    done = False
    for line in lines:
        stripped = line.lstrip(" \t")
        if not done and (stripped.startswith("FROM ") or stripped.startswith("FROM\t")):
            parts = stripped.split()
            if len(parts) > 1:
                from_in_file = parts[1]
            out.append(f"FROM {base_image}\n")
            done = True
        else:
            out.append(line)
    return "".join(out), from_in_file


def _check_fat_jar(jar: str) -> None:
    """fat jar 校验（unzip -l + grep BOOT-INF/classes 的 zipfile 等价）。"""
    try:
        with zipfile.ZipFile(jar) as zf:
            names = zf.namelist()
    except (zipfile.BadZipFile, OSError):
        raise OpsError(f"{jar} 不是合法 zip", EXIT_PRECONDITION) from None
    if not any("BOOT-INF/classes" in n for n in names):
        raise OpsError(
            f"{jar} 不是 spring-boot fat jar（无 BOOT-INF/classes），平台无法 java -jar 启动",
            EXIT_PRECONDITION,
        )


# ---------------------------------------------------------------- 查询/解析
def _image_versions_query(cos: _CloudOS, mod: str, project: str) -> str:
    return (
        f"/api/cloud/hcr/v1.0/image/versions?name={mod}"
        f"&harborProjectName={project}&page=1&size=20"
    )


def _parse_probe(resp_text: str, mod: str, ver: str) -> str:
    """轮询响应解析（bash 内联 python 等价）：命中 → "imgUrl|osAndArch|digest"，否则 ""。

    响应形态：data 是 dict，镜像列表在 data.images[]，版本字段 versions 是逗号串。
    """
    try:
        d = json.loads(resp_text)
    except ValueError:
        return ""
    data = d.get("data") or {}
    groups = (data.get("images") if isinstance(data, dict) else data) or []
    for grp in groups:
        if grp.get("imgName") != mod:
            continue
        tags = [t.strip() for t in str(grp.get("versions") or "").split(",")]
        if ver in tags:
            return "%s|%s|%s" % (grp.get("imgUrl"), grp.get("osAndArch"), grp.get("digest"))
    return ""


def _pretty_json(text: str) -> str:
    """python3 -m json.tool 等价（indent=4、ensure_ascii、末尾换行）。"""
    try:
        d = json.loads(text)
    except ValueError:
        return text
    return json.dumps(d, indent=4, ensure_ascii=True) + "\n"


# ---------------------------------------------------------------- 参数解析
def _parse_args(argv: list[str]) -> dict | None:
    """解析全部参数（独立入口自解析，含 --env）。-h/--help 返回 None（调用方打用法）。

    失败消息=Bash ${var:?} / die 的原文（main 统一加「错误：」前缀单行打印）。
    """
    args = list(argv)
    if args and args[0] in ("-h", "--help"):
        return None
    if not args:
        raise OpsError(_USAGE, EXIT_USAGE)
    if len(args) < 2:
        # bash ${2:?用法: $0 <module> <version> ...} 原样文案
        raise OpsError(f"用法: {_CMD_NAME} <module> <version> ...", EXIT_USAGE)

    mod, ver = args[0], args[1]
    rest = args[2:]
    opts = {
        "env": "hongdao",
        "jar": "",
        "dockerfile": "",
        "project": "",
        "img_type": IMG_TYPE_DEFAULT,
        "base_image": "",
        "check": False,
    }
    value_flags = {
        "--env": ("env", "--env 需要值（hongdao|test）"),
        "--jar": ("jar", "--jar 需要值"),
        "--dockerfile": ("dockerfile", "--dockerfile 需要值"),
        "--project": ("project", "--project 需要值"),
        "--img-type": ("img_type", "--img-type 需要值"),
        "--base-image": ("base_image", "--base-image 需要值"),
    }
    i = 0
    while i < len(rest):
        a = rest[i]
        if a == "--check":
            opts["check"] = True
            i += 1
        elif a in value_flags:
            key, errmsg = value_flags[a]
            if i + 1 >= len(rest):
                raise OpsError(errmsg, EXIT_USAGE)
            opts[key] = rest[i + 1]
            i += 2
        else:
            raise OpsError(f"未知参数：{a}", EXIT_USAGE)
    opts["mod"] = mod
    opts["ver"] = ver
    return opts


# ---------------------------------------------------------------- 主流程
def _do_check(cos: _CloudOS, mod: str, project: str) -> int:
    """--check 只读分支：查询现有镜像，美化 JSON 到 stdout。"""
    _err(f"[check] {mod} 现有镜像（project={project}）")
    code, body = _http_token(cos, "GET", _image_versions_query(cos, mod, project), timeout=30)
    if code in (None, "000"):
        raise OpsError("连不上镜像仓库（网络失败）", EXIT_TRANSPORT)
    if code != "200":
        raise OpsError(f"镜像查询 HTTP {code}：{_head_c(body, 300)}", EXIT_EXEC)
    ui.out(_pretty_json(body).rstrip("\n"))
    return EXIT_OK


def _do_build(cos: _CloudOS, opts: dict, env: envx.Env) -> int:
    mod, ver = opts["mod"], opts["ver"]
    project = opts["project"] or env.harbor_project
    base_image = opts["base_image"] or (
        BASE_IMAGE_TEST if env.name == "test" else BASE_IMAGE_HONGDAO
    )

    # 与 bash 相同的专用横幅（含平台账号/项目/imgType/基础镜像）
    _err(
        f"[环境] {env.label}（{cos.env_name} / {cos.host} / 账号 {cos.username}）  "
        f"Harbor项目={project}  imgType={opts['img_type']}"
    )
    _err(f"       基础镜像={base_image}")

    token = cos.get_token()
    if token is None:
        raise OpsError("登录失败", cos.login_exit_code)

    if opts["check"]:
        return _do_check(cos, mod, project)

    # ---- 准备 jar + Dockerfile ----
    root = _repo_root()
    jar = opts["jar"] or str(root / mod / "target" / f"{mod}-{ver}.jar")
    if not Path(jar).is_file():
        raise OpsError(f"找不到 jar：{jar}", EXIT_PRECONDITION)
    dockerfile = opts["dockerfile"] or str(root / mod / "Dockerfile")
    if not Path(dockerfile).is_file():
        raise OpsError(f"找不到 Dockerfile：{dockerfile}", EXIT_PRECONDITION)

    raw = Path(dockerfile).read_text()
    dockerfile_text, from_in_file = _replace_from(raw, base_image)
    if not from_in_file:
        raise OpsError(f"{dockerfile} 里没有 FROM 行，无法确定基础镜像", EXIT_PRECONDITION)
    if from_in_file != base_image:
        _err(f"   基础镜像替换：{from_in_file} → {base_image}")

    _check_fat_jar(jar)

    # ---- 构建上下文：直接上传 jar（文件名 MUST 为 app.jar，对齐 Dockerfile 的 COPY）----
    stage = Path(tempfile.mkdtemp())
    try:
        context_file = stage / "app.jar"
        shutil.copyfile(jar, context_file)
        _err(
            f"[1/4] 构建上下文文件：{context_file}"
            f"（{context_file.stat().st_size} bytes，直接上传不打 tar）"
        )

        # ---- 1) file/upload（fileFlag 客户端自生成）----
        fileflag = uuid.uuid4().hex
        _err(f"[2/4] file/upload（fileFlag={fileflag}）")
        data, boundary = _multipart_file("file", "app.jar", context_file.read_bytes())
        code, up = _http_token(
            cos, "POST",
            "/api/cloud/hcr/v1.0/images/file/upload",
            data=data,
            headers={
                "fileFlag": fileflag,
                "Content-Type": f"multipart/form-data; boundary={boundary}",
            },
            timeout=300,
        )
        _err(f"   upload 响应：{_head_c(up, 300)}")
        if code in (None, "000"):
            _err("upload 响应非 JSON：")
            raise OpsError("file/upload 失败", EXIT_TRANSPORT)
        try:
            upd = json.loads(up)
        except ValueError as exc:
            _err(f"upload 响应非 JSON：{exc}")
            raise OpsError("file/upload 失败", EXIT_EXEC) from None
        if upd.get("status") is not True:
            _err(f"upload 失败：code={upd.get('code')} msg={upd.get('msg')}")
            raise OpsError("file/upload 失败", EXIT_EXEC)
        data_id = fileflag
        _err(f"   ✓ 用客户端 fileFlag={data_id} 作为 buildx 引用")

        # ---- 2) buildx 提交 ----
        body = json.dumps(
            {
                "dockerfile": dockerfile_text,
                "imgName": mod,
                "version": ver,
                "imgType": opts["img_type"],
                "type": "private",
                "fileFlag": data_id,
                "project": project,
                "platform": "linux/amd64",
                "description": f"{mod} {ver} via cloudos buildx ({cos.env_name})",
            },
            ensure_ascii=False,
        )
        _err("[3/4] buildx 提交构建（platform=linux/amd64）")
        code, bx = _http_token(
            cos, "POST", "/api/cloud/hcr/v1.0/images/buildx",
            data=body.encode(),
            headers={"Content-Type": "application/json"},
            timeout=120,
        )
        _err(f"   buildx 响应：{_head_c(bx, 600)}")

        # ---- 3) 轮询确认产物（最多 120s）----
        _err(f"[4/4] 轮询镜像仓库（最多 120s），确认 {project}/{mod}:{ver} linux/amd64")
        ok = ""
        for i in range(1, 13):
            time.sleep(10)
            code, resp = _http_token(
                cos, "GET", _image_versions_query(cos, mod, project), timeout=30
            )
            line = _parse_probe(resp, mod, ver) if code not in (None, "000") else ""
            if line:
                ok = line
                _err(f"   ✓ 第 {i} 次探测命中：{line}")
                break
            _err(f"   … 第 {i} 次探测未命中，继续")
    finally:
        shutil.rmtree(stage, ignore_errors=True)

    if not ok:
        _err(
            f"   ⚠ 轮询超时：构建可能仍在进行或失败，"
            f"去 CloudOS 控制台「镜像」页核对 {project}/{mod}:{ver}"
        )
        raise OpsError("轮询超时", EXIT_EXEC)

    img_url, arch, digest = (ok.split("|", 2) + ["", "", ""])[:3]
    ui.out("")
    ui.out("==========================================")
    ui.out(f"✓ 构建完成：{img_url}")
    ui.out(f"  arch   = {arch}")
    ui.out(f"  digest = {digest}")
    ui.out(f"  回滚点 = {project}/{mod}:上一个 tag（Harbor 未删）")
    ui.out("==========================================")
    return EXIT_OK


def _repo_root() -> Path:
    """仓库根（<repo>/<module>/target 默认 jar 路径用）。"""
    from .core.execx import repo_root

    return repo_root()


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    # 复用入口的全局上下文（quiet/verbose/no-color/--json 不丢失）；独立运行时为默认 Ctx
    ctx = ui.context()
    ctx.command = argv
    try:
        opts = _parse_args(argv)
        if opts is None:
            _err(_USAGE)
            return EXIT_OK

        # bash：ITP_ENV="$ENV_NAME"; ITP_ENV_EXPLICIT=1 —— 总是显式环境
        ctx.env = opts["env"]
        ctx.env_explicit = True
        env = envx.init_env("hongdao")

        cos = _CloudOS()
        cos.env_init(env.name)
        return _do_build(cos, opts, env)
    except OpsError as exc:
        _die_msg(str(exc))
        return exc.exit_code


if __name__ == "__main__":
    sys.exit(main())

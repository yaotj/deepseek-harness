"""web 前端（CloudOS 应用包 web-frontend）一键发版 —— itpops 收编版。

本地出 zip → 登录 CloudOS → 上传 web 包 → 升级应用 → 核验 / 回滚。
原独立脚本 web_frontend_release.py 已收敛为 `itp-ops.sh web release`（原文见 git 历史）。

端点 2026-10-26 实测；上传字段集 2026-10-28 对齐平台「上传应用包」向导：
  登录  POST /api/sys/oapi/v1/double_factor/login   （口令 RSA-1024 PKCS#1 v1.5 加密）
  上传  POST /api/cloud/deploy/v1.0/packages        （multipart，pkgType=web 不是 jar）
  升级  PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config
        body 由 GET /apps/{uuid} 的 metadata 读现状后只改 version_id / version

用法（与旧脚本逐字兼容）：
  itp-ops.sh web release <版本> [--yes] [--skip-build] [--skip-upgrade]
                         [--app web-frontend] [--pkg web-frontend] [--app-uuid <u>]
                         [--tag itpb] [--description <包描述>] [--feature <版本特性>]
  itp-ops.sh web release --bump [--yes] ...   # 从 CloudOS 已部署版本按规则算下一版
  itp-ops.sh web release --debug [--yes] ...  # 调试升级，版本固定哨兵 0.0.0.1
  不带 --yes = dry-run（本地出 zip + 打印将发的请求，不碰生产）

【与旧版的差异（收编对齐契约，均有理由）】
  1. 版本算法统一走 core/version.py 真源（2026-11-02 裁定：末位 +1、段上限 99、
     进位；最高段溢出补 1）。旧脚本自带 next_version 有多重进位 bug：
     1.99.99 → 1.0.0.0（错），真源 → 2.0.0；99.99 新旧一致 → 1.0.0。
  2. RSA 加密从第三方 cryptography 换纯标准库 PKCS#1 v1.5（与 itpops/buildx/core.py
     同实现，密文恒 128 字节 / base64 172 字符）——去掉 pip 依赖，密文同形。
  3. 失败统一抛 OpsError（退出码：用法 2 / 前置 3 / 传输 4 / 执行 5），stderr
     错误行带统一「错误：」前缀（旧为裸文本）。
  4. 新增可选 --env hongdao|test（与 itp-ops 全局口径一致）；CLOUDOS_ENV 环境
     变量仍兼容，优先级：--env > CLOUDOS_ENV > hongdao。
  5. --yes 语义不变：缺省 dry-run（不是拒绝），一个字节都没动。
  6. 退出码细分（旧一律 exit 1）；web 目录改由仓库根推导（execx.repo_root），
     被移动位置时报前置错而非算错路径。
"""
from __future__ import annotations

import argparse
import base64
import json
import math
import os
import random
import re
import secrets
import ssl
import string
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

from ..core import ui
from ..core.runtime import repo_root
from ..core.version import DEBUG_VERSION, debug_version, next_version
from ..errors import (
    EXIT_EXEC,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

# --------------------------------------------------------------------------- 模块级状态
_TOKEN = None   # 登录 token 缓存（login() 幂等复用，避免 --bump 提前登录后重复登录）

# --------------------------------------------------------------------------- 配置
# 分片上传参数（对齐平台上传控件 vue 配置，2026-10-28 抓 upload.*.js 实测）
CHUNK_SIZE = 204_800_000   # 控件 chunkSize（204800000 字节 ≈ 195 MiB/片；小于此值仍是单请求）
CHUNK_RETRIES = 3         # 控件 maxChunkRetries

# 环境 profile（与 lib/cloudos-auth.sh 同源；口令默认值即仓库真源，2026-10-26 裁定，可用环境变量覆盖）
HONGDAO_PUBKEY_B64 = (
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCbPqaFZ0lgebihxCw5wzjkahPKr6ZI2JNTHbHDnNbOn3Cif6Ggn"
    "MBr44MhQCRPQTF2FgcxBbn3u7eGcEPMT6OZqcLzcmiMCUQRJ2MUbFI+E1vI1f+iuCQ2w8XZhRoRrmb7wBA0L63gn"
    "SwXRkJD0baL5zqlQhpSKfSH0t3opAoahwIDAQAB"
)


def _profile(env: str) -> dict:
    if env == "hongdao":
        return {
            "host": os.environ.get("CLOUDOS_HOST", "172.120.209.151"),
            "username": os.environ.get("CLOUDOS_USERNAME", "itpbadmin"),
            "password": os.environ.get("CLOUDOS_PASSWORD", "QDitp@123!@#"),
            "pubkey": os.environ.get("CLOUDOS_RSA_PUBKEY_B64", HONGDAO_PUBKEY_B64),
        }
    if env == "test":
        # 与 cloudos-auth.sh:67 同源：两平台（红岛/测试）登录公钥逐字符一致（2026-11-02 复核），
        # 默认复用同一把共享公钥 HONGDAO_PUBKEY_B64；仅平台升级换钥后 export CLOUDOS_RSA_PUBKEY_B64 覆盖。
        pub = os.environ.get("CLOUDOS_RSA_PUBKEY_B64", HONGDAO_PUBKEY_B64)
        return {
            "host": os.environ.get("CLOUDOS_HOST", "172.20.217.210"),
            "username": os.environ.get("CLOUDOS_USERNAME", "admin"),
            "password": os.environ.get("CLOUDOS_PASSWORD", "Qdmlc123!@#"),
            "pubkey": pub,
        }
    raise OpsError(f"未知 CLOUDOS_ENV={env}（仅 hongdao / test）", EXIT_PRECONDITION)


# --------------------------------------------------------------------------- 基础设施（纯 stdlib）
def _ssl_ctx() -> ssl.SSLContext:
    ctx = ssl.create_default_context()
    ctx.check_hostname = False          # 平台自签证书（等同 curl -k）
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def http(method: str, url: str, *, headers=None, body: bytes | None = None,
         token: str | None = None, timeout: int = 120) -> tuple[int, bytes]:
    """发 HTTP，返回 (状态码, 响应体)。网络不可达返回 (0, err)。"""
    ctx = _ssl_ctx()
    req_headers = dict(headers or {})
    if token:
        req_headers["X-Auth-Token"] = token
    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=timeout) as r:
            return r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except urllib.error.URLError as e:
        return 0, f"NETWORK_UNREACHABLE: {e.reason}".encode()


def http_json(method, url, *, body=None, token=None, ctype="application/json") -> tuple[int, dict]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = ctype
    code, raw = http(method, url, headers=headers, body=data, token=token)
    try:
        return code, json.loads(raw.decode() or "{}")
    except (ValueError, UnicodeDecodeError):
        return code, {"_raw": raw.decode(errors="replace")[:400]}


def _der_read_len(buf: bytes, i: int) -> tuple[int, int]:
    n = buf[i]
    i += 1
    if n & 0x80:
        count = n & 0x7F
        n = int.from_bytes(buf[i:i + count], "big")
        i += count
    return n, i


def _rsa_parse_pubkey(pubkey_b64: str) -> tuple[int, int]:
    """SPKI DER → (n, e)。结构：SEQ{ SEQ{OID}, BITSTRING{ SEQ{n,e} } }。"""
    try:
        der = base64.b64decode(pubkey_b64)
        assert der[0] == 0x30
        _, i = _der_read_len(der, 1)
        assert der[i] == 0x30
        ln, i = _der_read_len(der, i + 1)
        i += ln
        assert der[i] == 0x03
        ln, i = _der_read_len(der, i + 1)
        assert der[i] == 0x00
        i += 1
        assert der[i] == 0x30
        _, i = _der_read_len(der, i + 1)
        assert der[i] == 0x02
        ln, i = _der_read_len(der, i + 1)
        n = int.from_bytes(der[i:i + ln], "big")
        i += ln
        assert der[i] == 0x02
        ln, i = _der_read_len(der, i + 1)
        e = int.from_bytes(der[i:i + ln], "big")
        return n, e
    except Exception as exc:  # noqa: BLE001 —— 公钥坏值/换钥未抓，统一文案
        raise OpsError("RSA 加密失败（公钥解析不出 n/e）", EXIT_PRECONDITION) from exc


def rsa_encrypt(pubkey_b64: str, plaintext: bytes) -> str:
    """明文 → RSA PKCS#1 v1.5 → base64（128 字节 / 172 字符）。纯标准库实现。"""
    n, e = _rsa_parse_pubkey(pubkey_b64)
    k = (n.bit_length() + 7) // 8  # 1024-bit → 128
    ps_len = k - 3 - len(plaintext)
    if ps_len < 8:
        raise OpsError("RSA 加密失败（明文过长）", EXIT_PRECONDITION)
    ps = bytearray()
    while len(ps) < ps_len:
        b = secrets.token_bytes(ps_len - len(ps))
        ps.extend(x for x in b if x != 0)
    em = b"\x00\x02" + bytes(ps) + b"\x00" + plaintext
    enc = pow(int.from_bytes(em, "big"), e, n).to_bytes(k, "big")
    out = base64.b64encode(enc).decode()
    if len(out) != 172:
        print(f"警告：RSA 密文长度 {len(out)}（预期 172）——公钥可能非 1024 位", file=sys.stderr)
    return out


def gen_id() -> str:
    """客户端预生成 10 位短 ID（与应用包版本 ID 同格式）。"""
    alphabet = string.ascii_letters + string.digits
    return "".join(random.choice(alphabet) for _ in range(10))


def multipart_range(fields: dict, file_field: str, file_path: Path, start: int, end: int) -> tuple[bytes, dict]:
    """组 multipart body（表单字段 + 文件 part 只含 file[start:end]）。
    每片独立一个 part：--boundary\\r\\n 头\\r\\n\\r\\n 值\\r\\n；末条 --boundary--。
    单片时 start=0/end=size（旧行为）；分片时由 upload() 按 CHUNK_SIZE 切。"""
    boundary = "----qditp" + gen_id()
    parts = []
    for k, v in fields.items():
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n")
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; "
                 f"filename=\"{file_path.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
    body = "".join(parts).encode("utf-8") + file_path.read_bytes()[start:end] + f"\r\n--{boundary}--\r\n".encode()
    return body, {"Content-Type": f"multipart/form-data; boundary={boundary}"}


# --------------------------------------------------------------------------- 四段流程
def stage_build(version: str, web_dir: Path, skip: bool) -> Path:
    zip_path = web_dir / f"web-frontend-{version}.zip"
    if skip:
        if not zip_path.exists():
            print(f"1/4 --skip-build 且 {zip_path.name} 不存在：该版本若已在软件中心则跳过上传、不影响；否则上传会失败")
        else:
            print(f"1/4 复用已构建 {zip_path.name}（{zip_path.stat().st_size // 1024 // 1024}M）")
        return zip_path

    pkg = web_dir / "package.json"
    data = json.loads(pkg.read_text())
    if data.get("version") != version:
        data["version"] = version
        pkg.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
        print(f"1/4 package.json version → {version}")
    log_path = Path("/tmp/web_frontend_build.log")
    print(f"1/4 构建（npm run build:zip）…")
    r = subprocess.run(["npm", "run", "build:zip"], cwd=web_dir,
                       stdout=log_path.open("w"), stderr=subprocess.STDOUT)
    if r.returncode != 0 or not zip_path.exists():
        print(log_path.read_text()[-1500:], file=sys.stderr)
        raise OpsError(f"前端构建失败（日志 {log_path}）", EXIT_EXEC)
    with zipfile.ZipFile(zip_path) as zf:
        if "index.html" not in zf.namelist():
            raise OpsError("zip 内无根级 index.html（build:zip 产物异常）", EXIT_EXEC)
    print(f"   ✓ zip 根级 index.html 校验通过（{zip_path.stat().st_size // 1024 // 1024}M）")
    return zip_path


def login(profile: dict) -> str:
    global _TOKEN
    if _TOKEN:
        return _TOKEN
    host = profile["host"]
    enc = rsa_encrypt(profile["pubkey"], profile["password"].encode())
    base = f"https://{host}"
    code, d = http_json("POST", f"{base}/api/sys/oapi/v1/double_factor/login",
                        body={"username": profile["username"], "rsa": True,
                              "password": enc, "domain": os.environ.get("CLOUDOS_DOMAIN", "default")})
    if code == 0:
        raise OpsError(f"连不上 {host}（查 USB 网卡 en9 是否 active）", EXIT_TRANSPORT)
    if code != 200 or d.get("status") is not True:
        extra = ""
        if d.get("code") == "usrmgt.userOrPasswordError":
            extra = "\n  排查：账号/密码/是否真 RSA 加密(密文应 172 字符)/domain=default"
        raise OpsError(f"登录失败 HTTP {code}：{d.get('msg')}{extra}", EXIT_EXEC)
    token = (d.get("res") or {}).get("token")
    if not token:
        raise OpsError(f"登录未返回 token：{str(d)[:300]}", EXIT_EXEC)
    print(f"2/4 ✓ 登录成功（{host} / {profile['username']}，token {len(token)} 字符）")
    _TOKEN = token
    return token


def get_pkg_detail(token, base, pkg_name) -> dict:
    """UI「包详情」同款查询（selectOwn + 私有 visible=false）：返回包 dict（含 versions / tag_id / label / description / pkg_type）；不存在或取不到 → {}。"""
    code, d = http_json("GET", f"{base}/api/cloud/deploy/v1.0/packages?selectOwn=true&visible=false&name={pkg_name}",
                        token=token)
    if code != 200:
        return {}
    rows = d.get("data") or []
    return rows[0] if rows else {}


def get_version_id(token, base, pkg_name, version) -> str | None:
    for v in (get_pkg_detail(token, base, pkg_name).get("versions") or []):
        if v.get("version") == version:
            return v.get("id")
    return None


def resolve_tag_id(token, base, tag_name) -> str | None:
    """分类名 → tagId(uuid)，走 GET /tags（与包详情 tag_id 同域）。查不到返回 None（上传不附带 tagId，兼容旧行为）。"""
    if not tag_name:
        return None
    code, d = http_json("GET", f"{base}/api/cloud/deploy/v1.0/tags", token=token)
    if code != 200:
        return None
    for t in d.get("data") or []:
        if t.get("tag") == tag_name:
            return t.get("id")
    return None


def upload(token, base, pkg_name, version, zip_path, *, pkg_type="web", label="", description=None,
           feature=None, tag_id=None) -> None:
    """上传字段集对齐平台上传向导（2026-10-28 抓 upload.*.js）：
    id / pkgType / name / version / visible / label / description / feature / tagId + 分片 totalChunks / chunkNumber。
    包已存在时 prefill 语义＝向导 getDetail：label / description / tagId / pkgType 取包详情现值。"""
    if get_version_id(token, base, pkg_name, version):
        print(f"3/4 {pkg_name}:{version} 已存在（平台禁重复版本号，重传会被拒），跳过上传")
        return
    detail = get_pkg_detail(token, base, pkg_name)
    label = label or detail.get("label") or pkg_name
    if description is None:
        description = detail.get("description") or ""
    if feature is None:
        feature = ""
    if tag_id is None:
        tag_id = detail.get("tag_id") or ""
    pkg_type = detail.get("pkg_type") or pkg_type
    vid = gen_id()
    size = zip_path.stat().st_size
    total = max(1, math.ceil(size / CHUNK_SIZE))
    print(f"3/4 上传 {pkg_name}:{version}（pkgType={pkg_type}，tagId={tag_id or '<未附带>'}，"
          f"{size // 1024 // 1024} MiB / {total} 片）")
    for i in range(1, total + 1):
        start, end = (i - 1) * CHUNK_SIZE, min(i * CHUNK_SIZE, size)
        fields = {"totalChunks": str(total), "chunkNumber": str(i), "id": vid,
                  "pkgType": pkg_type, "name": pkg_name, "version": version,
                  "visible": "false", "label": label, "description": description,
                  "feature": feature, "tagId": tag_id}
        body, hdr = multipart_range(fields, "file", zip_path, start, end)
        err = None
        for attempt in range(1, CHUNK_RETRIES + 1):
            code, raw = http("POST", f"{base}/api/cloud/deploy/v1.0/packages", headers=hdr,
                            body=body, token=token, timeout=300)
            try:
                d = json.loads(raw.decode() or "{}")
            except ValueError:
                d = {}
            if code == 200 and d.get("status") is True:
                err = None
                break
            err = f"HTTP {code}: {str(raw)[:400]}"
            print(f"   第 {attempt}/{CHUNK_RETRIES} 次失败（{err}），2s 后重试")
            time.sleep(2)
        if err:
            raise OpsError(
                f"上传失败（{pkg_name}:{version} 第 {i}/{total} 片，重试 {CHUNK_RETRIES} 次）：{err}",
                EXIT_EXEC,
            )
        print(f"   … 第 {i}/{total} 片已传（{(end - start) // 1024 // 1024} MiB）")
    if get_version_id(token, base, pkg_name, version):
        print(f"   ✓ 回查确认 {pkg_name}:{version} 已落盘")


def resolve_app_uuid(token, base, app_name) -> str:
    code, d = http_json("GET", f"{base}/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200", token=token)
    if code != 200:
        raise OpsError(f"取应用列表失败 HTTP {code}", EXIT_EXEC)
    rows = d.get("data") or []
    hits = [a for a in rows if a.get("name") == app_name] or [a for a in rows if app_name in (a.get("name") or "")]
    if len(hits) != 1:
        raise OpsError(f"应用名不唯一/未找到：{app_name}（{len(hits)} 个）", EXIT_EXEC)
    return hits[0]["uuid"]


def app_state(token, base, app_uuid) -> dict:
    code, d = http_json("GET", f"{base}/api/cloud/app-mgmt/v1.0/apps/{app_uuid}", token=token)
    return (d.get("data") or {}) if code == 200 else {}


def build_upgrade_body(meta: dict, version_id: str, version: str) -> dict:
    img = (meta.get("image_metadata") or {}).get("metadata") or {}
    return {
        "version_id": version_id,
        "version": version,
        "strategy": meta.get("strategy", "rolling"),
        "env": meta.get("env") or {},
        "jvm_args": meta.get("jvm_args") or "",
        "logrotateCron": meta.get("logrotateCron"),
        "tomcat_version": meta.get("tomcat_version"),
        "is_skywalking": meta.get("is_skywalking", False),
        "enable_ssl": meta.get("enable_ssl", False),
        "nginx_version": meta.get("nginx_version"),
        "service_name": meta.get("service_name"),
        "affinity": img.get("affinity", {}),
        "pvcResource": img.get("pvcResource", {}),
        "xjarVersionPaths": {},
        "resources": img.get("resources", []),
    }


def upgrade(token, base, app_uuid, version, version_id) -> str:
    meta = app_state(token, base, app_uuid).get("metadata") or {}
    if not meta:
        raise OpsError("取应用详情 metadata 失败（应用不存在或无权限）", EXIT_EXEC)
    rollback_point = (app_state(token, base, app_uuid) or {}).get("version")
    body = build_upgrade_body(meta, version_id, version)
    code, d = http_json("PATCH", f"{base}/api/cloud/app-mgmt/v1.0/apps/{app_uuid}/advanced-config",
                        body=body, token=token)
    if code != 200 or d.get("status") is not True:
        raise OpsError(f"升级 PATCH 失败 HTTP {code}：{str(d)[:400]}", EXIT_EXEC)
    print(f"4/4 ✓ 升级请求已下发（ROLLBACK_POINT={rollback_point}）")

    st: dict = {}
    for i in range(1, 41):
        st = app_state(token, base, app_uuid)
        ok = st.get("version") == version and st.get("status") == "OK"
        inst = f"{st.get('instance_ok_num')}/{st.get('instance_num')}"
        if ok:
            print(f"   ✓ {app_uuid} 已到 {version}（status=OK，实例 {inst}，第 {i} 次探测）")
            return rollback_point
        print(f"   … 当前 {st.get('version')}/{st.get('status')}/{inst}（第 {i} 次探测）")
        time.sleep(3)
    print(f"   ⚠ 120s 未达 {version}（当前 {st.get('version')}/{st.get('status')}）——MUST 登平台核实")
    return rollback_point


# --------------------------------------------------------------------------- 入口
CATALOG = {
    "name": "release",
    "summary": "web 前端（CloudOS 应用包 web-frontend）一键发版：出 zip → 上传 → 升级 → 核验",
    "usage": "web release <版本|--bump|--debug> [--yes] [--skip-build] [--skip-upgrade] [--app …] [--pkg …] [--app-uuid …] [--tag …] [--description …] [--feature …] [--env hongdao|test]",
    "args": [
        {"name": "版本", "required": False},
        {"name": "--yes", "desc": "执行生产写（上传+升级）；缺省 dry-run"},
        {"name": "--bump", "desc": "从 CloudOS 已部署版本按规则算下一版"},
        {"name": "--debug", "desc": "调试升级，版本固定哨兵 0.0.0.1"},
        {"name": "--skip-build / --skip-upgrade", "desc": "跳过本地构建 / 只上传不升级"},
    ],
    "writes": True,
    "example": "itp-ops.sh web release 1.2.44 --yes",
    "env_note": "CloudOS 红岛/测试双平台（--env 或 CLOUDOS_ENV，默认 hongdao）",
}


def main(argv: list[str] | None = None) -> int:
    try:
        return _main(argv)
    except OpsError as exc:
        ui.log_error(f"错误：{exc}")
        return exc.exit_code


def _main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="itp-ops.sh web release", description="web 前端（CloudOS）一键发版")
    ap.add_argument("version", nargs="?", help="目标版本，如 1.2.44；与 --bump/--debug 互斥")
    ap.add_argument("--yes", action="store_true", help="执行生产写（上传+升级）；缺省 dry-run")
    ap.add_argument("--debug", action="store_true",
                    help="调试升级：版本固定哨兵 0.0.0.1（不占用发布序列）")
    ap.add_argument("--bump", action="store_true",
                    help="从 CloudOS 已部署版本按规则算下一版（末位+1，超99进位）；忽略位置版本号")
    ap.add_argument("--skip-build", action="store_true", help="复用已有 zip，跳过本地构建")
    ap.add_argument("--skip-upgrade", action="store_true", help="只上传不升级")
    ap.add_argument("--app", default=os.environ.get("WEB_APP_NAME", "web-frontend"))
    ap.add_argument("--pkg", default=os.environ.get("WEB_PKG_NAME", "web-frontend"))
    ap.add_argument("--app-uuid", default=os.environ.get("WEB_APP_UUID", ""))
    ap.add_argument("--tag", default=os.environ.get("WEB_PKG_TAG", "itpb"),
                    help="软件中心分类名（默认 itpb）；经 GET /tags 解析为 tagId(uuid)，解析不到则上传不附带")
    ap.add_argument("--description", default=os.environ.get("WEB_PKG_DESC"),
                    help="包描述；缺省 prefill（包已存在取详情现值、新建取空），传字符串则显式覆盖")
    ap.add_argument("--feature", default=os.environ.get("WEB_PKG_FEATURE"),
                    help="版本特性；缺省不填")
    ap.add_argument("--env", choices=["hongdao", "test"], default=None,
                    help="CloudOS 平台（默认取 CLOUDOS_ENV 环境变量，再缺省 hongdao）")
    args = ap.parse_args(argv)

    env = args.env or os.environ.get("CLOUDOS_ENV", "hongdao")
    profile = _profile(env)
    base = f"https://{profile['host']}"
    web_dir = repo_root() / "web"
    app_uuid = args.app_uuid

    # 解析目标版本：--debug 哨兵 / --bump 从已部署版本算下一版 / 否则用位置参数
    if args.debug:
        args.version = debug_version()
    elif args.bump:
        tok = login(profile)   # --bump 需先登录查已部署版本（login 幂等复用）
        app_uuid = app_uuid or resolve_app_uuid(tok, base, args.app)
        cur = (app_state(tok, base, app_uuid) or {}).get("version")
        if not cur:
            raise OpsError(
                f"--bump 无法取得 {args.app} 当前已部署版本（应用不存在或无权限）", EXIT_EXEC
            )
        args.version = next_version(cur)
        print(f"--bump：{args.app} 当前已部署 {cur} → 下一版本 {args.version}")
    elif not args.version:
        raise OpsError(
            "需提供版本号，或用 --bump（从已部署版本算下一版）/ --debug（哨兵 0.0.0.1）", EXIT_USAGE
        )

    print(f"\n== web 前端发版 {args.version}（env={env} / app={args.app} / pkg={args.pkg}）==")
    if not args.yes:
        print("dry-run（未带 --yes）：本地出 zip + 打印将发的请求，不做任何生产写\n")

    # 1 本地出 zip
    zip_path = stage_build(args.version, web_dir, args.skip_build)

    # 2 登录
    token = login(profile)

    # 3 上传
    tag_uuid = resolve_tag_id(token, base, args.tag)
    if args.yes:
        print(f"3/4 上传 web 包 {args.pkg}:{args.version}")
        upload(token, base, args.pkg, args.version, zip_path,
               description=args.description, feature=args.feature, tag_id=tag_uuid)
    else:
        desc = "<prefill 包详情现值>" if args.description is None else args.description
        feat = "<不填>" if args.feature is None else args.feature
        print(f"3/4 dry-run：将 POST {base}/api/cloud/deploy/v1.0/packages（本地 {zip_path.name}）")
        print(f"   表单字段：id=<10位预生成> pkgType=web name={args.pkg} version={args.version} "
              f"visible=false label={args.pkg} description={desc} feature={feat} "
              f"tagId={tag_uuid or f'<{args.tag} 未解析，不附带>'}"
              f" + 分片 totalChunks/chunkNumber（文件 >{CHUNK_SIZE // 1024 // 1024}MiB 时按 {CHUNK_SIZE} 字节切）")

    # 4 升级
    if args.skip_upgrade:
        print("4/4 跳过升级（--skip-upgrade）")
        ui.result({"version": args.version, "app": args.app, "pkg": args.pkg, "upgraded": False})
        return 0
    if not app_uuid:
        app_uuid = resolve_app_uuid(token, base, args.app)
        print(f"   应用 {args.app} uuid={app_uuid}")
    if not args.yes:
        vid = get_version_id(token, base, args.pkg, args.version)
        print(f"4/4 dry-run：将 PATCH {base}/api/cloud/app-mgmt/v1.0/apps/{app_uuid}/advanced-config")
        print(f"   version_id={vid or '<先上传后填>'} version={args.version}（body=应用详情 metadata 读现状改写）")
        print(f"   确认无误后：python3 {Path(__file__).name} {args.version} --yes")
        ui.result({"version": args.version, "app": args.app, "pkg": args.pkg, "dry_run": True})
        return 0

    vid = get_version_id(token, base, args.pkg, args.version)
    if not vid:
        raise OpsError(f"上传后仍查不到 {args.pkg}:{args.version} 的 version_id", EXIT_EXEC)
    print(f"4/4 升级 {args.app} → {args.version}")
    rollback = upgrade(token, base, app_uuid, args.version, vid)
    print(f"\n完成：{args.app} @ {args.version}（回滚＝python3 {Path(__file__).name} {rollback} --yes --skip-build）")
    ui.result({"version": args.version, "app": args.app, "pkg": args.pkg,
               "rollback_point": rollback, "upgraded": True})
    return 0


if __name__ == "__main__":
    sys.exit(main())

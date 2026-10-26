#!/usr/bin/env python3
# ============================================================================
# web 前端（CloudOS 应用包 web-frontend）一键发版 —— 纯 Python 版
#
# 本地出 zip → 登录 CloudOS → 上传 web 包 → 升级应用 → 核验 / 回滚
# 逻辑全在 Python：RSA 登录用 cryptography，HTTP 用 urllib，构建调 npm 子进程，
# JSON 用 json，zip 校验用 zipfile。取代原 web-frontend-release.sh。
#
# 依赖（本机）：python3 + cryptography（RSA）；构建步还要 node/npm。
# 端点 2026-10-26 实测：
#   登录  POST /api/sys/oapi/v1/double_factor/login   （口令 RSA-1024 PKCS#1 v1.5 加密）
#   上传  POST /api/cloud/deploy/v1.0/packages        （multipart，pkgType=web）
#   升级  PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config
#         body 由 GET /apps/{uuid} 的 metadata 读现状后只改 version_id / version
#
# 用法：
#   python3 web_frontend_release.py <版本> [--yes] [--skip-build] [--skip-upgrade]
#                          [--app web-frontend] [--pkg web-frontend] [--app-uuid <u>]
#   不带 --yes = dry-run（本地出 zip + 打印将发的请求，不碰生产）
# ============================================================================

import argparse
import base64
import json
import os
import random
import re
import ssl
import string
import subprocess
import sys
import textwrap
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

# --------------------------------------------------------------------------- 依赖检查
try:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import padding
except ImportError:
    sys.exit("缺 cryptography（RSA 登录要用）。装：pip3 install cryptography")

# --------------------------------------------------------------------------- 配置
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
        # 测试环境公钥未固化（本机长期不可达）；MUST 从测试登录页抓后 export CLOUDOS_RSA_PUBKEY_B64
        pub = os.environ.get("CLOUDOS_RSA_PUBKEY_B64", "")
        if not pub:
            sys.exit("测试环境 RSA 公钥未配置：export CLOUDOS_RSA_PUBKEY_B64=<1024-bit base64> 后重试")
        return {
            "host": os.environ.get("CLOUDOS_HOST", "172.20.217.210"),
            "username": os.environ.get("CLOUDOS_USERNAME", "admin"),
            "password": os.environ.get("CLOUDOS_PASSWORD", "Qdmlc123!@#"),
            "pubkey": pub,
        }
    sys.exit(f"未知 CLOUDOS_ENV={env}（仅 hongdao / test）")


# --------------------------------------------------------------------------- 基础设施（纯 stdlib + cryptography）
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


def rsa_encrypt(pubkey_b64: str, plaintext: bytes) -> str:
    """明文 → RSA-1024 PKCS#1 v1.5 → base64（128 字节 / 172 字符）。"""
    b64 = "\n".join(textwrap.wrap(pubkey_b64, 64))
    pem = f"-----BEGIN PUBLIC KEY-----\n{b64}\n-----END PUBLIC KEY-----\n".encode()
    pub = serialization.load_pem_public_key(pem)
    ct = pub.encrypt(plaintext, padding.PKCS1v15())
    enc = base64.b64encode(ct).decode()
    if len(enc) != 172:
        print(f"警告：RSA 密文长度 {len(enc)}（预期 172）——公钥可能非 1024 位", file=sys.stderr)
    return enc


def gen_id() -> str:
    """客户端预生成 10 位短 ID（与应用包版本 ID 同格式）。"""
    alphabet = string.ascii_letters + string.digits
    return "".join(random.choice(alphabet) for _ in range(10))


def multipart(fields: dict, file_field: str, file_path: Path) -> tuple[bytes, dict]:
    """组 multipart body（字段 + 一个文件）。每个 part：--boundary\r\n 头\r\n\r\n 值\r\n；末条 --boundary--。"""
    boundary = "----qditp" + gen_id()
    parts = []
    for k, v in fields.items():
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n")
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; "
                 f"filename=\"{file_path.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
    body = "".join(parts).encode("utf-8") + file_path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
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
        sys.exit(f"前端构建失败（日志 {log_path}）")
    with zipfile.ZipFile(zip_path) as zf:
        if "index.html" not in zf.namelist():
            sys.exit("zip 内无根级 index.html（build:zip 产物异常）")
    print(f"   ✓ zip 根级 index.html 校验通过（{zip_path.stat().st_size // 1024 // 1024}M）")
    return zip_path


def login(profile: dict) -> str:
    host = profile["host"]
    enc = rsa_encrypt(profile["pubkey"], profile["password"].encode())
    base = f"https://{host}"
    code, d = http_json("POST", f"{base}/api/sys/oapi/v1/double_factor/login",
                        body={"username": profile["username"], "rsa": True,
                              "password": enc, "domain": os.environ.get("CLOUDOS_DOMAIN", "default")})
    if code == 0:
        sys.exit(f"连不上 {host}（查 USB 网卡 en9 是否 active）")
    if code != 200 or d.get("status") is not True:
        extra = ""
        if d.get("code") == "usrmgt.userOrPasswordError":
            extra = "\n  排查：账号/密码/是否真 RSA 加密(密文应 172 字符)/domain=default"
        sys.exit(f"登录失败 HTTP {code}：{d.get('msg')}{extra}")
    token = (d.get("res") or {}).get("token")
    if not token:
        sys.exit(f"登录未返回 token：{str(d)[:300]}")
    print(f"2/4 ✓ 登录成功（{host} / {profile['username']}，token {len(token)} 字符）")
    return token


def get_version_id(token, base, pkg_name, version) -> str | None:
    code, d = http_json("GET", f"{base}/api/cloud/deploy/v1.0/packages?page=1&size=50&name={pkg_name}", token=token)
    if code != 200:
        return None
    for p in d.get("data") or []:
        for v in p.get("versions") or []:
            if v.get("version") == version:
                return v.get("id")
    return None


def upload(token, base, pkg_name, version, zip_path) -> None:
    if get_version_id(token, base, pkg_name, version):
        print(f"3/4 该版本已存在（{pkg_name}:{version}），跳过上传")
        return
    vid = gen_id()
    body, hdr = multipart(
        {"totalChunks": "1", "chunkNumber": "1", "id": vid, "pkgType": "web",
         "name": pkg_name, "version": version, "visible": "false", "label": pkg_name},
        "file", zip_path)
    code, raw = http("POST", f"{base}/api/cloud/deploy/v1.0/packages", headers=hdr,
                     body=body, token=token, timeout=300)
    d = json.loads(raw.decode() or "{}")
    if code != 200 or d.get("status") is not True:
        sys.exit(f"上传失败 HTTP {code}：{str(raw)[:400]}")
    print(f"3/4 ✓ 已上传 {pkg_name}:{version}（vid={vid}）")
    if get_version_id(token, base, pkg_name, version):
        print(f"   ✓ 回查确认 {pkg_name}:{version} 已落盘")


def resolve_app_uuid(token, base, app_name) -> str:
    code, d = http_json("GET", f"{base}/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200", token=token)
    if code != 200:
        sys.exit(f"取应用列表失败 HTTP {code}")
    rows = d.get("data") or []
    hits = [a for a in rows if a.get("name") == app_name] or [a for a in rows if app_name in (a.get("name") or "")]
    if len(hits) != 1:
        sys.exit(f"应用名不唯一/未找到：{app_name}（{len(hits)} 个）")
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
        sys.exit("取应用详情 metadata 失败（应用不存在或无权限）")
    rollback_point = (app_state(token, base, app_uuid) or {}).get("version")
    body = build_upgrade_body(meta, version_id, version)
    code, d = http_json("PATCH", f"{base}/api/cloud/app-mgmt/v1.0/apps/{app_uuid}/advanced-config",
                        body=body, token=token)
    if code != 200 or d.get("status") is not True:
        sys.exit(f"升级 PATCH 失败 HTTP {code}：{str(d)[:400]}")
    print(f"4/4 ✓ 升级请求已下发（ROLLBACK_POINT={rollback_point}）")

    import time
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
def main():
    ap = argparse.ArgumentParser(description="web 前端（CloudOS）一键发版")
    ap.add_argument("version", help="目标版本，如 1.2.44")
    ap.add_argument("--yes", action="store_true", help="执行生产写（上传+升级）；缺省 dry-run")
    ap.add_argument("--skip-build", action="store_true", help="复用已有 zip，跳过本地构建")
    ap.add_argument("--skip-upgrade", action="store_true", help="只上传不升级")
    ap.add_argument("--app", default=os.environ.get("WEB_APP_NAME", "web-frontend"))
    ap.add_argument("--pkg", default=os.environ.get("WEB_PKG_NAME", "web-frontend"))
    ap.add_argument("--app-uuid", default=os.environ.get("WEB_APP_UUID", ""))
    args = ap.parse_args()

    env = os.environ.get("CLOUDOS_ENV", "hongdao")
    profile = _profile(env)
    base = f"https://{profile['host']}"
    web_dir = Path(__file__).resolve().parents[4] / "web"
    app_uuid = args.app_uuid

    print(f"\n== web 前端发版 {args.version}（env={env} / app={args.app} / pkg={args.pkg}）==")
    if not args.yes:
        print("dry-run（未带 --yes）：本地出 zip + 打印将发的请求，不做任何生产写\n")

    # 1 本地出 zip
    zip_path = stage_build(args.version, web_dir, args.skip_build)

    # 2 登录
    token = login(profile)

    # 3 上传
    if args.yes:
        print(f"3/4 上传 web 包 {args.pkg}:{args.version}（pkgType=web）")
        upload(token, base, args.pkg, args.version, zip_path)
    else:
        print(f"3/4 dry-run：将 POST {base}/api/cloud/deploy/v1.0/packages（本地上传 {zip_path.name}）")

    # 4 升级
    if args.skip_upgrade:
        print("4/4 跳过升级（--skip-upgrade）")
        return
    if not app_uuid:
        app_uuid = resolve_app_uuid(token, base, args.app)
        print(f"   应用 {args.app} uuid={app_uuid}")
    if not args.yes:
        vid = get_version_id(token, base, args.pkg, args.version)
        print(f"4/4 dry-run：将 PATCH {base}/api/cloud/app-mgmt/v1.0/apps/{app_uuid}/advanced-config")
        print(f"   version_id={vid or '<先上传后填>'} version={args.version}（body=应用详情 metadata 读现状改写）")
        print(f"   确认无误后：python3 {Path(__file__).name} {args.version} --yes")
        return

    vid = get_version_id(token, base, args.pkg, args.version)
    if not vid:
        sys.exit(f"上传后仍查不到 {args.pkg}:{args.version} 的 version_id")
    print(f"4/4 升级 {args.app} → {args.version}")
    rollback = upgrade(token, base, app_uuid, args.version, vid)
    print(f"\n完成：{args.app} @ {args.version}（回滚＝python3 {Path(__file__).name} {rollback} --yes --skip-build）")


if __name__ == "__main__":
    main()

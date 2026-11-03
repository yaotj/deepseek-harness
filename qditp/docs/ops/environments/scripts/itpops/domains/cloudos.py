"""itpops · cloudos 域 —— 新华三 CloudOS（E7501 PaaS）平台运维。

移植自 domains/cloudos.sh（bash，1549 行）+ lib/cloudos-{auth,http,image,pkg}.sh。
命令面与旧 bash 逐字兼容：子命令名、参数形态、stdout 结果文本一致；help 文案
原样拷贝（`cloudos` / `cloudos help` / `cloudos -h` 输出与 legacy 字节相同）。

【旧 bash 的坑/巧思（保留原因）】
  · HARBOR_PROJECT 的历史错位：旧脚本 source 时 `HARBOR_PROJECT="${HARBOR_PROJECT:-itp}"`
    先定死，env.sh 的 `_env_apply` 非显式路径覆盖不掉 —— 于是不带 --env 跑
    image plan/build 时 project 显示 "itp"（即使打的是红岛）；显式 --env 才强制
    profile 值（hongdao→itpb）。为 stdout 字节兼容，本文件 _harbor_project()
    原样复刻该行为（env.sh 头注也记录了这一错配成因）。
  · app_spec_set 复验循环的 `if [[ "$i" -ge 20 ]]`：成功恰好发生在第 20 次探测时
    （break 后 i=20）会连带打印「复验超时」告警。原样保留（bash 行为如此）。
  · scale 平台接口真身是 `POST …/scale?replicas=N` + body `[]`（文档与实际不符，
    2026-10-23 实测）；deploy 的「当前镜像」以实例实跑为准（metadata 可能是旧值，
    IT-034）；advanced-config 的 env 是整份覆盖（漏一条=删一条，IT-020）；
    version_id 仅在 32/36 位 uuid 形态时带上（测试环境 metadata 无此字段，
    package_version_id 存的是镜像引用，塞进 varchar(36) 会 500）。
  · 「平台 Service 真源」：新增访问方式 MUST 走 svc create，手工 kubectl apply
    的对象重装即丢（IT-035）。
  · image build 走旧端点 /images/build（红岛/测试实测 404/img.5，IT-024），
    输出里的 WARN 原样保留，指引改用 itp-ops.sh buildx。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 失败退出码细分：bash 一律 exit 1（或 health/logs 的 return 1/2）；
     Python 按契约抛 OpsError——用法错 2、前置 3（health 不通过、缺 jar/Dockerfile、
     缺 IMG_TYPE_UUID）、传输 4（ssh/HTTP 连不上）、执行/业务拒绝 5、缺 --yes 6。
  2. 缺 --yes 的文案走 execx.require_yes 统一格式「这是写操作：…。确认无误请加 --yes」
     （svc create 与 bash 字节一致；act/scale/spec-set/image build 在 bash 用自定义
     die 文案，措辞略有出入，动作信息一致）。
  3. 写操作门新增：pod-delete / pkg-upload / raw 非 GET 在 bash 无 --yes 门，
     按「写操作一律 require_yes」补上（缺 --yes 本地即 exit 6）。
  4. image build 的 --yes 门提到 image_prepare 之前（bash 先查 jar 再看门）——
     让「缺 --yes」在本地无 jar 时也能得到 exit 6，而不是被 jar 缺失掩盖。
  5. app_logs 无实例时 bash 拿空 ns/pod 继续拼 URL、最终以「HTTP 失败」收场；
     Python 直接报「该应用没有实例」终止（少一次无意义请求）。
  6. 环境横幅按契约用 envx.banner（stderr，[环境] 行）；bash 的
     `== 环境：…（… / 账号 …）==` 行同时保留在 stderr（stdout 不受影响）。
  7. 未知命令：bash 打印 cloudos_usage 到 stdout 后 return 1；Python 同样打
     usage 到 stdout，但退出码按契约为 2（USAGE）。
  8. unzip CLI 校验 fat jar 改为 zipfile 模块（纯标准库），判定与报错文案一致。
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import zipfile

from ..cloudos import auth
from ..cloudos import http as http_mod
from ..cloudos import image as image_mod
from ..cloudos import pkg as pkg_mod
from ..core import envx, ui
from ..core.execx import require_yes, repo_root
from ..core.ui import context
from ..errors import (
    EXIT_EXEC,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

CONTAINER_API_PREFIX = "/api/cloud/container/v1.0"
APP_API = "/api/cloud/app-mgmt/v1.0"


# ---------------------------------------------------------------- 输出小工具
def _err(text: str) -> None:
    """bash `printf … >&2` 等价（提示/告警原样落 stderr）。"""
    print(text, file=sys.stderr)


def _info(text: str) -> None:
    """bash log_info 等价 —— 注意旧 core.sh 的 log_info 落 **stdout**，
    为 stdout 字节兼容这里也走 stdout（TTY 时绿色 [INFO]，与旧版一致）。"""
    if sys.stdout.isatty():
        print(f"\033[0;32m[INFO]\033[0m {text}")
    else:
        print(f"[INFO] {text}")


def _line(*parts: object) -> None:
    """bash 内联 python 的 print(a, b, …) 等价：空格连接后走 stdout。"""
    ui.out(" ".join(str(p) for p in parts))


# ---------------------------------------------------------------- 请求封装
def _req(method: str, uri: str, body: str = "") -> object:
    """cloudos_request 等价：成功返回解析结果，失败抛 OpsError（错误形态见 http.py）。"""
    return http_mod.request_json(method, uri, body)


def _req_json(method: str, uri: str, body: str = "") -> None:
    """cloudos_request + 打印美化 JSON（raw/act 等直出响应的命令用）。"""
    d = _req(method, uri, body)
    ui.out(http_mod.pretty(d))
    ui.result(d)


# ---------------------------------------------------------------- 参数解析
def resolve_app_uuid(key: str) -> str:
    """uuid 或应用名 → uuid（32 位 hex 直接过；名字精确匹配唯一，否则模糊唯一）。"""
    if re.fullmatch(r"[0-9a-f]{32}", key):
        return key
    try:
        d = _req("GET", f"{APP_API}/apps?page=1&pageSize=200")
        rows = (d.get("data") or []) if isinstance(d, dict) else []
        hits = [a for a in rows if a.get("name") == key]
        if not hits:
            hits = [a for a in rows if key in (a.get("name") or "")]
        if len(hits) == 1:
            return hits[0]["uuid"]
        if not hits:
            _err("未找到应用：%s" % key)
        else:
            _err("应用名不唯一，请用 uuid：%s" % ", ".join(h["name"] for h in hits))
    except OpsError:
        _err("解析应用失败：无法获取应用列表")
        raise OpsError(f"解析应用失败：{key}", EXIT_EXEC) from None
    raise OpsError(f"解析应用失败：{key}", EXIT_EXEC)


def _need(args: list[str], idx: int, msg: str) -> str:
    if idx < len(args) and args[idx]:
        return args[idx]
    raise OpsError(msg, EXIT_USAGE)


# ---------------------------------------------------------------- 帮助
def _help_text(env_label: str | None = None, host_label: str | None = None) -> str:
    """help 首行动态行。bash 里 help 在 env init 前展开（host 显示「未初始化」），
    未知命令路径在 init 后展开（host 显示真实值）——两处时机不同，如实复刻。"""
    if env_label is None:
        ctx = ui.context()
        env_label = (ctx.env if ctx.env_explicit else None) or os.environ.get("CLOUDOS_ENV") or "hongdao"
    if host_label is None:
        host_label = os.environ.get("CLOUDOS_HOST") or "未初始化"
    return _HELP_LINE1 % (env_label, host_label) + "\n" + _HELP_BODY


_HELP_LINE1 = "itp-ops.sh cloudos —— CloudOS 平台运维（当前环境：%s / %s）"
_HELP_BODY = """\

用法: itp-ops.sh [--env hongdao|test] cloudos <命令> [参数...]

认证与诊断
  health                         连通性自检（网卡/TCP/登录/业务），写操作前必跑
  auth-debug                     登录诊断（密文长度、公钥指纹、token）
  logout                         清本环境 token 缓存

企业应用（红岛 ns itpb / 测试 ns itp；对应控制台 /enterprise-apps/#/apply/app/appDetail/<uuid>）
  apps [status]                  应用列表；status 可选 OK/Error/Stopped...
  app       <名字|uuid>          应用详情（含 env 全量）
  env       <名字|uuid>          只打 env（排查 SERVICE_*_URL 是否指向 itp）
  svc       <名字|uuid>          访问方式与 NodePort 映射
  svc create <名字|uuid> <服务名> 新建访问方式（默认集群内网 ClusterIP 8080→8080）
                                  可选 --type NodePort --node-port N、--port、--container-port、
                                  --session-keep。**平台是 Service 真源**：手工 kubectl apply
                                  的同类对象重装即丢，新增访问方式 MUST 走本命令
  pods      <名字|uuid>          实例列表（podIP / ready / 重启次数）
  logs      <名字|uuid> [行数]   平台侧运行日志（默认 200 行；红岛该接口恒空，
                                 主力手段是 itp-ops.sh log tail <svc>）
  act       <名字|uuid> start|stop|restart --yes   生产写操作

副本数（异步，自动轮询到 ready）
  scale     <名字|uuid> <副本数> --yes            调单个；已是目标则跳过
  scale-all <副本数> [--yes] [--exclude a,b]      批量；不带 --yes 是预演

容器规格（requests=limits，改完会滚动重启 Pod）
  spec      [名字|uuid|all]      查看规格（默认 all）
  spec-set  <名字|uuid> [cpu] [mem] [--yes]       默认 4000m / 8192Mi（= 4C/8G）
  spec-all  [cpu] [mem] [--yes] [--exclude a,b]   批量对齐；不带 --yes 是预演

应用升级（镜像应用切 tag；不带 --yes = 只读预演，含目标 tag 存在性/arch 校验）
  deploy    <名字|uuid> <裸 tag | 镜像名:tag | 完整引用> [--app-version <v>] [--yes]
        读现状 → 校 Harbor tag（不存在/arch 不对即停，不发写请求）→ 拼 advanced-config
        body（env 全量原样回传）→ PATCH → 轮询实例切到新镜像。
        「当前镜像」以**实例实跑**为准（平台 metadata 可能是旧值，见 IT-034）；
        应用版本默认取目标 tag（两套号从此对齐，要别的用 --app-version 覆盖）。
        回滚 = 同一命令指回旧 tag。NEVER 同 tag 覆盖（脚本会拒）。
        包类型应用（metadata.container_image 为空）不适用，走控制台升级。

镜像构建（⚠ 旧端点路线，红岛/测试均实测走不通，见 IT-024）
  image probe                    只读探测（基址/镜像分类/已有 tag，仍可用于取 imgType）
  image plan  <模块> <版本>       dry-run，打印将提交的全部参数
  image build <模块> <版本> --yes 走 POST /images/build（历史端点；两套环境都应改用
                                 itp-ops.sh buildx —— buildx 端点才是通的）
        附加选项：--jar <p> --project <name> --img-type <uuid> --dockerfile <p>

镜像构建（实际可用，两个环境同一套）
  itp-ops.sh buildx <镜像名> <版本> [--env hongdao|test]
       [--jar <绝对路径>] [--dockerfile <共享模板>] [--check]
       平台 x86 节点 buildx 出镜像并推 Harbor；Harbor 项目与基础镜像按 --env 自动取
       （红岛 itpb/jdk21:1.0.0、测试 itp/jdk:25）。--check 为只读模式。

测试环境附加（--env test；前缀未经实测，见域头注）
  appgroups                      应用组列表
  pod-list [ns] / pod-get <pod> / pod-delete <pod>      容器云 pod（前缀待校准）
  svc-clusters / svc-cluster-nodes <uuid>               服务治理
  pkg-list [page] [size] / pkg-get <id> / pkg-upload <file> [name] [version]
                                 软件中心 jar 包

兜底
  raw <METHOD> <URI> [BODY]      任意 REST，例：raw GET /api/cloud/app-mgmt/v1.0/apps

示例
  itp-ops.sh cloudos health
  itp-ops.sh cloudos apps
  itp-ops.sh cloudos env fep-app
  itp-ops.sh cloudos scale web-admin 1 --yes
  itp-ops.sh cloudos deploy recon-server 1.0.28          # 只读预演
  itp-ops.sh cloudos image probe

环境变量覆盖
  CLOUDOS_HOST / CLOUDOS_USERNAME / CLOUDOS_PASSWORD / CLOUDOS_DOMAIN /
  CLOUDOS_TIMEOUT / CLOUDOS_RSA_PUBKEY_B64 / IMG_BASE_URL / TOKEN_CACHE"""


# ---------------------------------------------------------------- 认证与诊断
def _health() -> int:
    c = auth.cfg()
    env = envx.current()
    bad = 0

    ui.out(f"===== CloudOS 连通性自检（{c.host}）=====")
    ui.out("")

    # 1) 本机网卡（en9 是红岛 USB 网卡；测试环境走办公网/内网，不看它）
    if env.name == "hongdao":
        r = subprocess.run(["ifconfig", "en9"], capture_output=True, text=True)
        if r.returncode == 0:
            if "status: active" in r.stdout:
                m = re.search(r"inet (\S+)", r.stdout)
                ui.out(f"  [1/3] en9: active  ({m.group(1) if m else '无IP'})")
            else:
                ui.out("  [1/3] en9: **inactive** —— USB 网卡未插好/网线断，这就是根因")
                bad = 1
        else:
            ui.out("  [1/3] en9: 不存在（可能走别的网卡，继续测）")
    else:
        ui.out("  [1/3] en9: 跳过（非红岛，测试环境走办公网/内网）")

    # 2) TCP 443
    if http_mod.check_ports(c.host, ["443"]):
        ui.out(f"  [2/3] TCP {c.host}:443 通")
    else:
        if env.name == "hongdao":
            ui.out(f"  [2/3] TCP {c.host}:443 **不通**（172.120 段路由是否还指向可用接口？）")
        else:
            ui.out(f"  [2/3] TCP {c.host}:443 **不通**（测试网 172.20 段是否可达？）")
        bad = 1

    # 3) 登录 + 一次业务 GET
    if bad == 0:
        t0 = int(time.time())
        try:
            token = auth.get_token("--fresh")
        except OpsError:
            token = ""
        if token:
            t1 = int(time.time())
            ui.out(f"  [3/3] 登录成功（{t1 - t0}s，token {len(token)} 字符）")
            t0 = int(time.time())
            try:
                _req("GET", f"{APP_API}/apps?page=1&pageSize=1")
                t1 = int(time.time())
                ui.out(f"        业务 API 正常（{t1 - t0}s）")
            except OpsError:
                ui.out("        业务 API **失败**（登录通了但 app-mgmt 无响应）")
                bad = 1
        else:
            ui.out("  [3/3] **登录失败** —— 账号/密码/RSA 公钥，先跑 auth-debug")
            bad = 1
    else:
        ui.out("  [3/3] 跳过（链路不通，先修网络）")

    ui.out("")
    if bad == 0:
        ui.out("结论：可以操作")
        ui.result({"host": c.host, "env": env.name, "ok": True})
        return 0
    ui.out("结论：**不能操作**，先按上面提示排障")
    raise OpsError("cloudos health 不通过，先按上面提示排障", EXIT_PRECONDITION)


def _auth_debug() -> int:
    c = auth.cfg()
    enc = auth.rsa_encrypt(c.password)

    ui.out("===== 登录请求 =====")
    ui.out(f"URL:      {c.base_url}/api/sys/oapi/v1/double_factor/login")
    ui.out(f"账号:     {c.username}  (domain={c.domain})")
    ui.out("密码模式: RSA / PKCS#1 v1.5 / 1024-bit")
    ui.out(f"密文长度: {len(enc)} 字符（128 字节；不是 172 说明公钥或填充不对）")
    ui.out(f"公钥指纹: {auth.pubkey_fingerprint()}")
    ui.out("")

    body = (
        '{"username":"%s","rsa":true,"password":"%s","domain":"%s"}'
        % (c.username, enc, c.domain)
    )
    code, rbody = http_mod.post_login(body)
    ui.out(f"HTTP: {code}")
    try:
        d = json.loads(rbody)
        ui.out("status=%s code=%s msg=%s" % (d.get("status"), d.get("code"), d.get("msg")))
        if d.get("status") is True:
            t = d["res"]["token"]
            ui.out("token=" + t)
            ui.out("token前缀=%s 长度=%d" % (t[:10], len(t)))
    except (ValueError, KeyError, TypeError):
        pass
    ui.out("")
    ui.out("返回码对照：")
    ui.out("  status=true                 => 通过，可用 apps / app <name> 继续")
    ui.out("  usrmgt.userOrPasswordError  => 账号或密码错（先确认 itpbadmin，再确认 RSA 加密）")
    ui.out("  svcmgt.internalServerError  => domain 传空导致，domain MUST 为 default")
    ui.result({"http": code, "base_url": c.base_url, "username": c.username, "rsa_len": len(enc)})
    return 0


def _logout() -> int:
    c = auth.cfg()
    try:
        os.unlink(c.token_cache)
    except OSError:
        pass
    ui.out(f"已清 token 缓存 {c.token_cache}")
    ui.result({"token_cache": c.token_cache})
    return 0


# ---------------------------------------------------------------- 企业应用
def _apps(status_filter: str = "") -> int:
    uri = f"{APP_API}/apps?page=1&pageSize=200"
    if status_filter:
        uri = f"{uri}&status={status_filter}"
    d = _req("GET", uri)
    rows = (d.get("data") or []) if isinstance(d, dict) else []

    ui.out("共 %d 个应用" % len(rows))
    ui.out("%-22s %-34s %-10s %-7s %-8s %s" % ("NAME", "UUID", "STATUS", "INST", "VERSION", "NAMESPACE"))
    ui.out("-" * 108)
    lines = []
    for a in sorted(rows, key=lambda x: (x.get("name") or "")):
        inst = "%s/%s" % (a.get("instance_num"), a.get("instance_ok_num"))
        lines.append("%-22s %-34s %-10s %-7s %-8s %s" % (
            (a.get("name") or "")[:22], a.get("uuid") or "",
            a.get("status") or "", inst, (a.get("version") or "")[:8], a.get("namespace") or ""))
    if lines:
        ui.out("\n".join(lines))
    ui.result({"count": len(rows), "apps": rows})
    return 0


def _app_detail(key: str) -> int:
    uuid = resolve_app_uuid(key)
    d = _req("GET", f"{APP_API}/apps/{uuid}")
    app = (d.get("data") or {}) if isinstance(d, dict) else {}
    meta = app.get("metadata") or {}
    env = meta.get("env") or {}

    _line("name         :", app.get("name"))
    _line("uuid         :", app.get("uuid"))
    _line("namespace    :", app.get("namespace"))
    _line("status       :", app.get("status"))
    _line("version      :", app.get("version"))
    _line("vip          :", app.get("vip"))
    _line("service_name :", meta.get("service_name"))
    _line("cluster      :", app.get("cluster_name") or app.get("cluster_id"))
    _line("replicas     :", app.get("instance_num"), "ok=", app.get("instance_ok_num"))
    _line("created_at   :", app.get("created_at"))
    _line("description  :", app.get("description"))
    ui.out("")
    if env:
        ui.out("--- env（%d 项，按 key 排序）---" % len(env))
        ui.out("\n".join("%s=%s" % (k, env[k]) for k in sorted(env)))
    ui.result({"app": app, "env": env})
    return 0


def _app_env(key: str) -> int:
    uuid = resolve_app_uuid(key)
    d = _req("GET", f"{APP_API}/apps/{uuid}")
    app = (d.get("data") or {}) if isinstance(d, dict) else {}
    env = (app.get("metadata") or {}).get("env") or {}
    ui.out("# %s / %s / ns=%s / v=%s" % (
        app.get("name"), app.get("uuid"), app.get("namespace"), app.get("version")))
    ui.out("\n".join("%s=%s" % (k, env[k]) for k in sorted(env)))
    ui.result({"name": app.get("name"), "uuid": app.get("uuid"), "env": env})
    return 0


def _app_service(key: str) -> int:
    uuid = resolve_app_uuid(key)
    d = _req("GET", f"{APP_API}/apps/{uuid}/service")
    data = (d.get("data") or {}) if isinstance(d, dict) else {}
    _line("vip:", data.get("vip"))
    lines = []
    for s in data.get("services") or []:
        lines.append("service: %s  type=%s" % (s.get("service_name"), s.get("type")))
        lines.append("  %-18s %-8s %-16s %-12s %s" % ("PORT_NAME", "PROTO", "CONTAINER_PORT", "CLUSTER", "NODEPORT"))
        for p in s.get("port_map_list") or []:
            lines.append("  %-18s %-8s %-16s %-12s %s" % (
                p.get("name"), p.get("protocol"), p.get("container_port"),
                p.get("cluster_port"), p.get("node_port")))
    if lines:
        ui.out("\n".join(lines))
    ui.result(data)
    return 0


_SVC_CREATE_USAGE = (
    "用法：cloudos svc create <应用名|uuid> <service_name> "
    "[--type ClusterIP|NodePort] [--port 8080] [--container-port 8080] "
    "[--node-port N] [--session-keep] [--yes]"
)


def _svc_create(argv: list[str]) -> int:
    if len(argv) < 2 or not argv[0] or not argv[1]:
        raise OpsError(_SVC_CREATE_USAGE, EXIT_USAGE)
    key, name = argv[0], argv[1]

    typ = "ClusterIP"
    sport = "8080"
    cport = "8080"
    nport = ""
    keep = "false"
    i = 2
    while i < len(argv):
        a = argv[i]
        if a == "--type":
            typ = _need(argv, i + 1, "--type 缺值"); i += 2
        elif a == "--port":
            sport = _need(argv, i + 1, "--port 缺值"); i += 2
        elif a == "--container-port":
            cport = _need(argv, i + 1, "--container-port 缺值"); i += 2
        elif a == "--node-port":
            nport = _need(argv, i + 1, "--node-port 缺值"); i += 2
        elif a == "--session-keep":
            keep = "true"; i += 1
        else:
            raise OpsError(f"未知参数 {a}", EXIT_USAGE)

    if typ not in ("ClusterIP", "NodePort"):
        raise OpsError(f"type 只能是 ClusterIP | NodePort（收到 {typ}）", EXIT_USAGE)
    if typ == "NodePort" and not nport:
        raise OpsError("NodePort 类型必须给 --node-port（节点端口 30000-32767）", EXIT_USAGE)

    require_yes(f"在应用 {key} 上创建服务 {name}（{typ} {sport} → {cport}）")

    uuid = resolve_app_uuid(key)

    # 同名已存在就停：POST 会建出第二个同名对象，平台列表里两条同名后极难分辨
    d = _req("GET", f"{APP_API}/apps/{uuid}/service")
    services = ((d.get("data") or {}) if isinstance(d, dict) else {}).get("services") or []
    hit = [s.get("type") for s in services if s.get("service_name") == name]
    if hit:
        raise OpsError(
            f"服务 {name} 已存在（type={hit[0]}），未发起创建；改配置请走平台控制台",
            EXIT_EXEC,
        )

    pm: dict = {"name": "http", "protocol": "TCP", "port": sport,
                "cluster_port": sport, "container_port": cport}
    if nport:
        pm["node_port"] = nport
    body = json.dumps(
        {"type": typ, "service_name": name,
         "session_keep": keep == "true", "port_map_list": [pm]},
        ensure_ascii=False,
    )

    _err(f"即将在 {key}（{uuid}）上创建：")
    _err(f"  {body}")
    _req("POST", f"{APP_API}/apps/{uuid}/service", body)
    ui.out(f"已提交。核对：itp-ops.sh cloudos svc {key} 应出现 {name}（type={typ}）")
    ui.result({"uuid": uuid, "service_name": name, "type": typ, "body": json.loads(body)})
    return 0


def _app_pods(key: str) -> int:
    uuid = resolve_app_uuid(key)
    d = _req("GET", f"{APP_API}/apps/{uuid}/instances")
    rows = (d.get("data") or []) if isinstance(d, dict) else []
    ui.out("%-42s %-6s %-16s %-6s %s" % ("POD", "READY", "POD_IP", "RESTART", "CONTAINERS"))
    ui.out("-" * 110)
    lines = []
    for p in rows:
        cs = ",".join(c.get("name") or "" for c in (p.get("containers") or []))
        lines.append("%-42s %-6s %-16s %-6s %s" % (
            p.get("name"), p.get("ready"), p.get("podIP"),
            p.get("restartTime"), cs))
    if lines:
        ui.out("\n".join(lines))
    ui.result({"count": len(rows), "instances": rows})
    return 0


def _app_logs(key: str, lines_arg: str = "200") -> int:
    uuid = resolve_app_uuid(key)
    d = _req("GET", f"{APP_API}/apps/{uuid}/instances")
    rows = (d.get("data") or []) if isinstance(d, dict) else []
    if not rows:
        # bash 此处只打提示后拿空值继续；Python 直接终止（见模块 docstring 差异 §5）
        _err("该应用没有实例")
        raise OpsError("该应用没有实例", EXIT_EXEC)
    pick = next((p for p in rows if p.get("ready")), rows[0])
    cs = pick.get("containers") or []
    ns = pick.get("namespace") or ""
    pod = pick.get("name") or ""
    container = (cs[0].get("name") if cs else "") or ""

    _err(f"# ns={ns} pod={pod} container={container} tail={lines_arg}")

    raw = http_mod.raw_get(
        f"{APP_API}/namespaces/{ns}/pods/{pod}/log"
        f"?container={container}&tailingLines={lines_arg}&sinceSeconds=86400"
    )
    if raw is None:
        raise OpsError(f"HTTP 失败 GET pod log（{ns}/{pod}）", EXIT_TRANSPORT)

    # 日志接口可能给 JSON（{"data":"<文本>"}）也可能给纯文本，两种都兜住
    try:
        parsed = json.loads(raw)
        text = (parsed.get("data") or "") if isinstance(parsed, dict) else raw
    except ValueError:
        text = raw
    text = text.rstrip("\n")

    if not re.sub(r"[\n\t ]", "", text):
        _err("（平台返回空日志 —— 红岛该接口 2026-10-23 实测恒为空，请改用 VictoriaLogs 或 itp-ops.sh log tail）")
        raise OpsError("平台返回空日志", EXIT_EXEC)
    ui.out(text)
    ui.result({"ns": ns, "pod": pod, "container": container, "tail": lines_arg, "log": text})
    return 0


def _app_action(key: str, action: str) -> int:
    if action not in ("start", "stop", "restart"):
        raise OpsError(f"action 只能是 start|stop|restart（收到 {action}）", EXIT_USAGE)

    require_yes(f"app-act {key} {action}")

    uuid = resolve_app_uuid(key)
    _err(f"即将对 {key}（{uuid}）执行 {action} ...")
    d = _req("PUT", f"{APP_API}/apps/{uuid}", '{"action":"%s"}' % action)
    ui.out(http_mod.pretty(d))
    ui.result({"uuid": uuid, "key": key, "action": action, "response": d})
    return 0


# ---------------------------------------------------------------- 容器规格
def _cpu_milli(v: str) -> int:
    v = str(v).strip()
    return int(float(v[:-1])) if v.endswith("m") else int(float(v) * 1000)


def _mem_mi(v: str) -> int:
    v = str(v).strip()
    for u, f in (("Ki", 1 / 1024), ("Mi", 1.0), ("Gi", 1024.0), ("Ti", 1048576.0)):
        if v.endswith(u):
            return int(float(v[: -len(u)]) * f)
    return int(float(v) / 1024 / 1024)


def _app_spec(uuid: str) -> str:
    """输出 "容器名|reqCpu|reqMem|limCpu|limMem"。"""
    d = _req("GET", f"{APP_API}/apps/{uuid}/resources")
    data = (d.get("data") or []) if isinstance(d, dict) else []
    c = data[0] if data else {}
    r = c.get("resources") or {}
    q = r.get("requests") or {}
    l = r.get("limits") or {}
    return "%s|%s|%s|%s|%s" % (c.get("name"), q.get("cpu"), q.get("memory"), l.get("cpu"), l.get("memory"))


def _spec_fields(line: str) -> tuple[str, str, str, str, str]:
    parts = (line.split("|") + [""] * 5)[:5]
    return parts[0], parts[1], parts[2], parts[3], parts[4]


def _spec_matches(req_cpu: str, req_mem: str, lim_cpu: str, lim_mem: str, cpu: str, mem: str) -> bool:
    try:
        return (
            _cpu_milli(req_cpu) == _cpu_milli(cpu)
            and _mem_mi(req_mem) == _mem_mi(mem)
            and _cpu_milli(lim_cpu) == _cpu_milli(cpu)
            and _mem_mi(lim_mem) == _mem_mi(mem)
        )
    except ValueError:
        return False


def _app_spec_show(key: str) -> int:
    if key != "all":
        uuid = resolve_app_uuid(key)
        cname, rc, rm, lc, lm = _spec_fields(_app_spec(uuid))
        ui.out("%-22s req=%s/%s  limit=%s/%s" % (cname, rc, rm, lc, lm))
        ui.result({"name": cname, "requests": {"cpu": rc, "memory": rm}, "limits": {"cpu": lc, "memory": lm}})
        return 0

    d = _req("GET", f"{APP_API}/apps?page=1&pageSize=200")
    rows = (d.get("data") or []) if isinstance(d, dict) else []
    lines = []
    result_rows = []
    for a in sorted(rows, key=lambda x: x.get("name") or ""):
        name = a.get("name")
        _cname, rc, rm, lc, lm = _spec_fields(_app_spec(a.get("uuid")))
        lines.append("%-22s req=%-9s %-10s limit=%-9s %s" % (name, rc, rm, lc, lm))
        result_rows.append({"name": name, "uuid": a.get("uuid"),
                            "requests": {"cpu": rc, "memory": rm},
                            "limits": {"cpu": lc, "memory": lm}})
    if lines:
        ui.out("\n".join(lines))
    ui.result({"count": len(result_rows), "specs": result_rows})
    return 0


def _app_spec_set(key: str, cpu: str = "4000m", mem: str = "8192Mi") -> int:
    uuid = resolve_app_uuid(key)
    cname, cur_req_cpu, cur_req_mem, cur_lim_cpu, cur_lim_mem = _spec_fields(_app_spec(uuid))
    if not cname:
        raise OpsError(f"取不到容器名（{key}）", EXIT_EXEC)

    _err("%s: 容器=%s  当前 req=%s/%s limit=%s/%s" % (
        key, cname, cur_req_cpu, cur_req_mem, cur_lim_cpu, cur_lim_mem))

    # 已是目标值就跳过（按数值比较，平台回显 4000.0m 与目标 4000m 等价）
    if _spec_matches(cur_req_cpu, cur_req_mem, cur_lim_cpu, cur_lim_mem, cpu, mem):
        ui.out("%s 已是 req=limit=%s/%s，跳过" % (key, cpu, mem))
        ui.result({"key": key, "uuid": uuid, "skipped": True})
        return 0

    require_yes("%s 改为 req=limit=%s/%s（会滚动重启 Pod）" % (key, cpu, mem))

    body = (
        '[{"name":"%s","resources":{"requests":{"cpu":"%s","memory":"%s"},'
        '"limits":{"cpu":"%s","memory":"%s"}}}]' % (cname, cpu, mem, cpu, mem)
    )
    _err("%s：下发规格 req=limit=%s/%s ..." % (key, cpu, mem))
    _req("PUT", f"{APP_API}/apps/{uuid}/resources", body)

    # 复验：平台 resources 接口异步生效（2026-10-26 红岛实测），MUST 轮询到目标值
    after = ""
    i = 0
    verified = False
    for i in range(1, 21):
        after = _app_spec(uuid)
        _c, arc, arm, alc, alm = _spec_fields(after)
        if _spec_matches(arc, arm, alc, alm, cpu, mem):
            _err("  ✓ 复验通过（第 %d 次探测）: req=%s/%s limit=%s/%s" % (i, arc, arm, alc, alm))
            verified = True
            break
        time.sleep(5)
    # bash 奇特处保留：恰好第 20 次探测成功（break 后 i=20）也会打下面这行
    if i >= 20:
        _c, arc, arm, alc, alm = _spec_fields(after)
        _err("  ⚠ 复验超时 100s，平台仍报 req=%s/%s limit=%s/%s —— MUST 用 kubectl 核实后再下结论"
             % (arc, arm, alc, alm))
    _err("  ⚠ 规格变更会滚动重建 Pod，请用 pods 命令确认新副本 ready 后再动下一个")
    ui.result({"key": key, "uuid": uuid, "cpu": cpu, "memory": mem, "verified": verified})
    return 0


def _spec_all(argv: list[str]) -> int:
    cpu = argv[0] if len(argv) >= 1 else "4000m"
    mem = argv[1] if len(argv) >= 2 else "8192Mi"
    rest = argv[2:]
    exclude = ""
    i = 0
    while i < len(rest):
        if rest[i] == "--exclude":
            i += 1
            exclude = rest[i] if i < len(rest) else ""
        else:
            raise OpsError(f"未知参数 {rest[i]}", EXIT_USAGE)
        i += 1

    ui.out("目标规格：req=limit=%s/%s  排除：%s" % (cpu, mem, exclude or "无"))
    ui.out("")

    d = _req("GET", f"{APP_API}/apps?page=1&pageSize=200")
    rows = (d.get("data") or []) if isinstance(d, dict) else []
    todo: list[str] = []
    skip = 0
    out_lines = []
    for a in sorted(rows, key=lambda x: x.get("name") or ""):
        name = a.get("name") or ""
        if not name:
            continue
        if exclude and f",{exclude},".find(f",{name},") != -1:
            out_lines.append("  - %s（已排除）" % name)
            continue
        _cname, rc, rm, lc, lm = _spec_fields(_app_spec(a.get("uuid")))
        if _spec_matches(rc, rm, lc, lm, cpu, mem):
            skip += 1
            continue
        out_lines.append("  → %-22s req=%-9s %-9s limit=%-9s %s" % (name, rc, rm, lc, lm))
        todo.append(name)
    if out_lines:
        ui.out("\n".join(out_lines))

    ui.out("")
    ui.out("待改 %d 个，已是目标 %d 个" % (len(todo), skip))

    if not todo:
        ui.result({"todo": 0, "skip": skip})
        return 0
    if not context().yes:
        ui.out("以上为预演（dry-run）。确认执行请追加 --yes")
        ui.result({"todo": len(todo), "skip": skip, "targets": todo, "dry_run": True})
        return 0

    fail = 0
    for n in todo:
        # bash：app_spec_set 内 die 会终止整个脚本 —— OpsError 直接上抛；
        # 仅软失败（返回非 0）计数
        if _app_spec_set(n, cpu, mem) != 0:
            fail += 1
    ui.out("")
    ui.out("全部完成" if fail == 0 else "⚠ %d 个失败，MUST 逐个核实" % fail)
    ui.result({"todo": len(todo), "fail": fail, "targets": todo})
    return 0


# ---------------------------------------------------------------- 副本数
def _app_inst(uuid: str) -> tuple[str, str, str]:
    """读回 "实例数 ready数 状态"。"""
    d = _req("GET", f"{APP_API}/apps/{uuid}")
    app = (d.get("data") or {}) if isinstance(d, dict) else {}
    return (
        str(app.get("instance_num")),
        str(app.get("instance_ok_num")),
        str(app.get("status")),
    )


def _wait_replicas(uuid: str, target: str) -> bool:
    """轮询到目标副本数且全部 ready（最多 180s）。"""
    cur = ok = st = ""
    for i in range(1, 61):
        try:
            cur, ok, st = _app_inst(uuid)
        except OpsError:
            cur = ok = st = ""
        if cur == target and ok == target and st == "OK":
            _err("  ✓ %s/%s ready, status=%s（第 %d 次探测）" % (ok, target, st, i))
            return True
        _err("  … %s/%s ready, status=%s（第 %d 次探测）" % (cur or "?", ok or "?", st or "?", i))
        time.sleep(3)
    _err("  ⚠ 等待超时 180s，当前 %s/%s status=%s —— MUST 登平台/查 kubectl 核实" % (cur, ok, st))
    return False


def _app_scale(key: str, target: str) -> int:
    if not re.fullmatch(r"[0-9]+", target or ""):
        raise OpsError("副本数必须是正整数（收到 '%s'）" % target, EXIT_USAGE)
    if not (1 <= int(target) <= 20):
        raise OpsError("副本数应在 1..20（收到 %s）" % target, EXIT_USAGE)

    uuid = resolve_app_uuid(key)
    cur, ok, st = _app_inst(uuid)

    if cur == target:
        ui.out("%s 已是 %s 实例（%s ready, status=%s），跳过" % (key, target, ok, st))
        ui.result({"key": key, "uuid": uuid, "target": target, "skipped": True})
        return 0

    require_yes("%s %s → %s 实例" % (key, cur, target))

    _err("%s（%s）：%s → %s 实例，已下发 ..." % (key, uuid, cur, target))
    # 平台实际行为与 API 文档不符（2026-10-23 实测）：replicas 走 query 参数，
    # body 必须是 JSON 数组（平台按 McpPlaintext 列表反序列化）
    _req("POST", f"{APP_API}/apps/{uuid}/scale?replicas={target}", "[]")

    reached = _wait_replicas(uuid, target)
    if not reached:
        return 1
    return 0


def _scale_all(argv: list[str]) -> int:
    target = argv[0] if argv else ""
    rest = argv[1:]
    exclude = ""
    i = 0
    while i < len(rest):
        if rest[i] == "--exclude":
            i += 1
            exclude = rest[i] if i < len(rest) else ""
        else:
            raise OpsError(f"未知参数 {rest[i]}", EXIT_USAGE)
        i += 1

    if not re.fullmatch(r"[0-9]+", target or ""):
        raise OpsError("副本数必须是正整数（收到 '%s'）" % target, EXIT_USAGE)
    if not (1 <= int(target) <= 20):
        raise OpsError("副本数应在 1..20", EXIT_USAGE)

    d = _req("GET", f"{APP_API}/apps?page=1&pageSize=200")
    rows = (d.get("data") or []) if isinstance(d, dict) else []
    excl = {x for x in (exclude or "").split(",") if x}
    todo = [a for a in sorted(rows, key=lambda x: x.get("name") or "")
            if (a.get("name") not in excl) and a.get("instance_num") != int(target)]

    if not todo:
        ui.out("全部应用已是 %s 实例，无需调整" % target)
        ui.result({"todo": 0, "target": target})
        return 0

    ui.out("待调整（→ %s 实例）：" % target)
    plan = []
    for a in todo:
        plan.append((a.get("name"), a.get("uuid"),
                     a.get("instance_num"), a.get("instance_ok_num"), a.get("status")))
        ui.out("  %-22s %-34s %s/%s → %s  status=%s" % (
            a.get("name"), a.get("uuid"), a.get("instance_num"),
            a.get("instance_ok_num"), target, a.get("status")))
    ui.out("共 %d 个应用" % len(plan))

    if not context().yes:
        ui.out("")
        ui.out("以上为预演（dry-run）。确认执行请追加 --yes")
        ui.result({"target": target, "count": len(plan), "dry_run": True,
                   "apps": [{"name": n, "uuid": u} for n, u, _c, _o, _s in plan]})
        return 0

    fail = 0
    for name, uuid, _c, _o, _s in plan:
        # bash：app_scale 内 die 会终止整个脚本 —— OpsError 直接上抛；
        # 仅软失败（等待超时，返回 1）计数
        if _app_scale(str(uuid), target) != 0:
            fail += 1
    ui.out("")
    ui.out("全部完成" if fail == 0 else "⚠ %d 个应用未达目标，MUST 逐个核实" % fail)
    ui.result({"target": target, "count": len(plan), "fail": fail})
    # bash 语义：批量命令失败计数只进 ⚠ 文案，函数本身仍返回 0
    return 0


# ---------------------------------------------------------------- 应用升级
def _app_running_image(uuid: str) -> str:
    """读「实例实际运行镜像」。返回镜像引用（无实例返回 ""）。

    多镜像并存（滚动中/灰度）时取出现次数最多的那条，并向 stderr 告警。
    """
    d = _req("GET", f"{APP_API}/apps/{uuid}/instances")
    rows = (d.get("data") or []) if isinstance(d, dict) else []
    counts: dict[str, int] = {}
    for p in rows:
        for c in (p.get("containers") or []):
            n = c.get("name") or ""
            if not n or n.startswith("istio-"):
                continue
            img = c.get("image") or ""
            if not img:
                continue
            counts[img] = counts.get(img, 0) + 1
    if not counts:
        return ""
    img = max(counts, key=lambda k: (counts[k], k))
    if len(counts) > 1:
        _err("  ⚠ 实例镜像不唯一（%d 种）：%s" % (
            len(counts),
            ", ".join("%s×%d" % (k.rsplit(":", 1)[-1], v) for k, v in sorted(counts.items()))))
    return img


class _PlanError(Exception):
    """_deploy_plan 的校验失败（stderr 已打具体原因）。"""


def _deploy_plan(key: str, image_arg: str, version_override: str, running_img: str) -> tuple[dict, dict]:
    """读现状，产出 (plan, body)。失败抛 _PlanError（具体原因已落 stderr）。"""
    uuid = resolve_app_uuid(key)
    d = _req("GET", f"{APP_API}/apps/{uuid}")
    app = (d.get("data") or {}) if isinstance(d, dict) else {}
    m = app.get("metadata") or {}

    meta_img = m.get("container_image") or ""
    if not meta_img:
        _err("该应用 metadata.container_image 为空 —— 不是镜像应用，本命令不适用；"
             "包类型应用请先向应用仓库上传新包再走控制台升级")
        raise _PlanError()

    # 基线：实例实际运行为准（见模块 docstring），查不到才退回平台记录
    cur_img = running_img or meta_img
    cur_src = "实例实际运行" if running_img else "平台记录 metadata"
    ptype = app.get("package_type") or m.get("package_type") or ""

    repo, _, cur_tag = cur_img.rpartition(":")
    parts = repo.split("/")
    if len(parts) != 3:
        _err("当前 container_image 不是 <registry>/<project>/<name>:<tag> 形态，"
             "无法安全推导项目名：%s" % cur_img)
        raise _PlanError()
    registry, project, img_name = parts

    if "/" in image_arg:
        new_img = image_arg
    elif ":" in image_arg:
        nm, tg = image_arg.split(":", 1)
        if nm != img_name:
            _err("镜像名 %s 与该应用当前镜像 %s 不一致。要换镜像名 MUST 传完整引用 "
                 "<registry>/<project>/<name>:<tag>，否则会把包体打到别的镜像上" % (nm, img_name))
            raise _PlanError()
        new_img = "%s:%s" % (repo, tg)
    else:
        new_img = "%s:%s" % (repo, image_arg)

    new_repo, _, new_tag = new_img.rpartition(":")
    new_parts = new_repo.split("/")
    if len(new_parts) != 3:
        _err("目标镜像引用形态不对（应含 registry/project/name）：%s" % new_img)
        raise _PlanError()
    new_registry, new_project, name = new_parts
    if (new_registry, new_project) != (registry, project):
        _err("目标镜像的 registry/项目（%s/%s）与线上（%s/%s）不同 —— "
             "跨仓库/跨项目切换请人工核对后手填完整引用"
             % (new_registry, new_project, registry, project))
        raise _PlanError()

    if new_tag == cur_tag:
        _err("NEVER 同 tag 覆盖：目标 tag %s 与**当前实际运行**的 tag 相同（依据：%s）。"
             "请先 bump 版本号重新构建镜像" % (cur_tag, cur_src))
        raise _PlanError()

    cur_ver = app.get("version") or ""
    # 版本口径 = 目标镜像 tag（用户 2026-11-02 裁定）；--app-version 可覆盖
    new_ver = version_override if version_override else new_tag
    if new_ver == cur_ver:
        _err("目标应用版本 %s 等于平台当前 version（平台校验要求不等）。"
             "改 tag 后重试，或用 --app-version 显式指定" % cur_ver)
        raise _PlanError()

    env = m.get("env") or {}
    body: dict = {
        "version": new_ver,
        "strategy": m.get("strategy") or "rolling",
        "container_name": m.get("container_name"),
        "container_image": new_img,
        "env": env,
        "is_skywalking": bool(m.get("is_skywalking") or False),
    }
    # version_id 只在平台确实有、且形态是 uuid 时才带（测试环境坑见 docstring）
    vid = (m.get("version_id") or "").strip()
    if vid and not re.fullmatch(r"[0-9a-fA-F]{32}|[0-9a-fA-F-]{36}", vid):
        _err("  ⚠ metadata.version_id=%r 不是 uuid 形态，已省略该键" % vid[:60])
        vid = ""
    if vid:
        body["version_id"] = vid
    if m.get("service_name"):
        body["service_name"] = m["service_name"]

    plan = {
        "uuid": app.get("uuid"), "app_name": app.get("name"), "package_type": ptype,
        "cur_img": cur_img, "cur_meta_img": meta_img, "cur_src": cur_src,
        "cur_tag": cur_tag, "new_img": new_img, "new_tag": new_tag,
        "image_name": name, "project": project, "registry": registry,
        "container_name": m.get("container_name") or "",
        "cur_ver": cur_ver, "new_ver": new_ver, "version_id": vid,
        "env_n": len(env), "strategy": body["strategy"],
        "replicas": app.get("instance_num"),
    }
    return plan, body


def _deploy_check_image(name: str, tag: str, new_img: str, project: str) -> tuple[str, str]:
    """目标 tag 在 Harbor 里是否真的存在、架构对不对。失败抛 OpsError。"""
    d = _req("GET", f"/api/cloud/hcr/v1.0/image/versions?name={name}&harborProjectName={project}")
    rows = (((d.get("data") or {}) if isinstance(d, dict) else {}).get("images")) or []
    hit = [r for r in rows if r.get("imgUrl") == new_img]
    if not hit:
        _err("Harbor 项目 %s 里没有 %s:%s —— 镜像还没构建（或名字/项目写错）。"
             "MUST 先用 itp-ops.sh buildx --env %s 产出该 tag，NEVER 升级不存在的 tag"
             % (project, name, tag, envx.current().name))
        raise OpsError("目标镜像校验未通过，已停止（未发起任何写操作）", EXIT_PRECONDITION)
    r = hit[0]
    if (r.get("osAndArch") or "") != "linux/amd64":
        _err("⚠ %s:%s 的 osAndArch=%s，不是 linux/amd64 —— 节点是 x86，拉了也起不来"
             % (name, tag, r.get("osAndArch")))
        raise OpsError("目标镜像校验未通过，已停止（未发起任何写操作）", EXIT_PRECONDITION)
    return str(r.get("digest") or ""), str(r.get("createTime") or "")


def _deploy_wait(uuid: str, new_img: str, container: str, replicas: str) -> bool:
    """升级后轮询：每个 pod 的业务容器都换到新 tag 且 ready。"""
    tot = on_new = ready = "0"
    for i in range(1, 21):
        try:
            d = _req("GET", f"{APP_API}/apps/{uuid}/instances")
            rows = (d.get("data") or []) if isinstance(d, dict) else []
            tot = str(len(rows))
            on = rd = 0
            for p in rows:
                for c in (p.get("containers") or []):
                    if c.get("name") != container:
                        continue
                    if c.get("image") == new_img:
                        on += 1
                        if c.get("ready"):
                            rd += 1
            on_new, ready = str(on), str(rd)
        except OpsError:
            tot = on_new = ready = "0"
        _err("  [%d/20] pod=%s 新镜像=%s ready=%s（目标 %s）" % (i, tot, on_new, ready, replicas))
        if on_new == str(replicas) and ready == str(replicas):
            ui.out("已收敛：%s 个实例全部运行在 %s" % (replicas, new_img))
            return True
        time.sleep(15)
    _err("⚠ 300s 内未收敛（pod=%s 新镜像=%s ready=%s）。" % (tot or "?", on_new or "?", ready or "?"))
    _err("  下一步：cloudos pods <app> 看重启次数；平台侧镜像拉取失败会停在旧 ReplicaSet")
    return False


def _app_deploy(argv: list[str]) -> int:
    if len(argv) < 2 or not argv[0] or not argv[1]:
        raise OpsError(
            "用法：cloudos deploy <名字|uuid> <镜像名:tag 或完整引用> [--app-version <v>] [--yes]",
            EXIT_USAGE,
        )
    key, image_arg = argv[0], argv[1]
    version_override = ""
    i = 2
    while i < len(argv):
        if argv[i] == "--app-version":
            version_override = _need(argv, i + 1, "--app-version 缺值")
            i += 2
        else:
            raise OpsError(f"未知参数 {argv[i]}", EXIT_USAGE)

    app_uuid = resolve_app_uuid(key)

    # 先取「实例实际运行镜像」——它是基线真源（见模块 docstring）
    try:
        running = _app_running_image(app_uuid)
    except OpsError:
        running = ""

    try:
        plan, body = _deploy_plan(key, image_arg, version_override, running)
    except _PlanError:
        raise OpsError("生成升级参数失败（%s → %s）" % (key, image_arg), EXIT_EXEC) from None

    digest, build_time = _deploy_check_image(
        plan["image_name"], plan["new_tag"], plan["new_img"], plan["project"])

    ui.out("===== 镜像应用升级预演（%s / ns %s）=====" % (envx.current().label, plan["project"]))
    ui.out("  应用        %s（%s）" % (plan["app_name"], plan["uuid"]))
    ui.out("  当前镜像    %s" % plan["cur_img"])
    ui.out("  镜像来源    %s" % plan["cur_src"])
    if plan["cur_meta_img"] and plan["cur_meta_img"] != plan["cur_img"]:
        ui.out("  ⚠ 平台记录 %s 与实例实跑不一致（滚更曾绕开云管台）" % plan["cur_meta_img"])
        ui.out("    本次以实跑为基线：回滚点是 %s，不是平台记录的那个 tag" % plan["cur_tag"])
    ui.out("  目标镜像    %s" % plan["new_img"])
    ui.out("  镜像校验    存在 ✅  linux/amd64 ✅  构建于 %s" % build_time)
    ui.out("  镜像 digest %s" % digest)
    ui.out("  应用版本    %s → %s（口径：对齐镜像 tag）" % (plan["cur_ver"] or "<空>", plan["new_ver"]))
    ui.out("  version_id  %s" % (plan["version_id"] or "<平台无此字段，body 省略>"))
    ui.out("  策略/副本   %s / %s   容器 %s" % (
        plan["strategy"], plan["replicas"], plan["container_name"] or "未设置"))
    ui.out("  env         %s 项全量原样回传（平台是整份覆盖，漏一条=删一条）" % plan["env_n"])
    ui.out("")
    ui.out("  将提交的 body：")
    ui.out(json.dumps(body, ensure_ascii=False, indent=2)[:1200] + " ...")

    if not context().yes:
        ui.out("")
        if envx.current().name == "hongdao":
            ui.out("以上为预演（dry-run），未发起任何写操作。生产红线：二次确认 + 低峰 + 回滚预案。")
        else:
            ui.out("以上为预演（dry-run），未发起任何写操作。")
        ui.out("确认执行请追加 --yes；回滚 = 再跑一次本命令指回 %s" % plan["cur_img"])
        ui.result({"dry_run": True, "plan": plan, "body": body,
                   "image_digest": digest, "image_create_time": build_time})
        return 0

    _err("")
    _err("已下发 PATCH /apps/%s/advanced-config ..." % plan["uuid"])
    _req("PATCH", f"{APP_API}/apps/{plan['uuid']}/advanced-config",
         json.dumps(body, ensure_ascii=False))

    _deploy_wait(plan["uuid"], plan["new_img"],
                 plan["container_name"] or plan["image_name"], str(plan["replicas"]))
    ui.out("")
    ui.out("下一步（缺一不可）：")
    ui.out("  1) 探活 + 业务核验（NodePort 见 cloudos svc %s）" % plan["app_name"])
    ui.out("  2) 比对实例容器镜像里的 digest 与上面推送的 %s"
           "（IT-031 涉及的服务 MUST 复验 PID1=/startapp.sh 与 -Xmx5900m）" % digest)
    ui.out("  回滚：cloudos deploy %s %s --app-version %s --yes"
           % (plan["app_name"], plan["cur_img"], plan["cur_ver"]))
    ui.result({"plan": plan, "body": body, "image_digest": digest, "done": True})
    return 0


# ---------------------------------------------------------------- 镜像构建
def _harbor_project_default() -> str:
    """bash 历史错位的 HARBOR_PROJECT 解析（见模块 docstring）：
    显式 --env → profile 值（hongdao=itpb / test=itp）；否则环境变量或源码期默认 itp。"""
    if context().env_explicit:
        return "itpb" if envx.current().name == "hongdao" else "itp"
    return os.environ.get("HARBOR_PROJECT") or "itp"


def _image_parse_args(argv: list[str]) -> tuple[str, str, dict]:
    """image plan/build 共用参数解析：flag 与位置参数可任意顺序，位置参数取前两个。"""
    state = {"jar": None, "project": None, "img_type": None, "dockerfile": None}
    positional: list[str] = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in ("--jar", "--project", "--img-type", "--dockerfile"):
            if i + 1 >= len(argv):
                raise OpsError(f"{a} 缺值", EXIT_USAGE)
            key = {"--jar": "jar", "--project": "project",
                   "--img-type": "img_type", "--dockerfile": "dockerfile"}[a]
            state[key] = argv[i + 1]
            i += 2
        elif a == "--keep-pkg":
            i += 1  # 兼容旧入口：历史上就是 no-op
        elif a == "--":
            positional.extend(argv[i + 1:])
            break
        elif a.startswith("--"):
            raise OpsError(f"未知选项 {a}", EXIT_USAGE)
        else:
            positional.append(a)
            i += 1
    mod = positional[0] if positional else ""
    ver = positional[1] if len(positional) > 1 else ""
    return mod, ver, state


def _image_prepare(mod: str, ver: str, state: dict) -> dict:
    root = repo_root()
    dockerfile = state["dockerfile"] or os.environ.get("DOCKERFILE") or str(root / "docker" / "Dockerfile")
    jar = state["jar"] or os.environ.get("JAR_OVERRIDE") or str(root / mod / "target" / f"{mod}-{ver}.jar")

    if not os.path.isfile(jar):
        raise OpsError(
            "找不到 jar：%s\n   先构建：cd %s && mise exec -- mvn -pl %s -am clean package -DskipTests"
            % (jar, root, mod),
            EXIT_PRECONDITION,
        )
    if not os.path.isfile(dockerfile):
        raise OpsError("找不到 Dockerfile：%s" % dockerfile, EXIT_PRECONDITION)

    # fat jar 校验：平台用 java -jar 拉起，必须是 spring-boot repackage 产物
    try:
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
    except (OSError, zipfile.BadZipFile) as exc:
        raise OpsError("%s 不是合法的 zip（unzip 读取失败）" % jar, EXIT_PRECONDITION) from exc
    if not any("BOOT-INF/classes" in n for n in names):
        raise OpsError(
            "%s 不是 spring-boot fat jar（无 BOOT-INF/classes），平台无法 java -jar 启动" % jar,
            EXIT_PRECONDITION,
        )

    return {
        "jar": jar,
        "dockerfile": dockerfile,
        "size": os.path.getsize(jar),
        "file_flag": pkg_mod.pkg_gen_id(),
    }


def _cmd_probe(harbor_project: str) -> int:
    c = auth.cfg()
    _info("== CloudOS 镜像构建能力探测（只读，不发写请求）==")
    _info("")
    _info("-- 0. 连通性预检（镜像服务可能在 11000，Web 网关在 443）")
    if not http_mod.check_ports(c.host, ["443", "11000"]):
        raise OpsError(
            "两个端口都不通 —— en9 是否 active？\n"
            "\n"
            "   本机实测（2026-10-19 记录）：\n"
            "     USB 网卡配到 K8s 控制区 172.120.2.x  → 才能通云管台\n"
            "     配到出口区 / 走办公网 en0           → 只有 ICMP 通，TCP 全被拦\n"
            "   查网卡：ifconfig en9 | grep inet",
            EXIT_PRECONDITION,
        )

    _info("")
    _info("-- 1. 登录（走 %s）" % c.base_url)
    try:
        t = auth.get_token("")
    except OpsError:
        raise OpsError("登录失败（原因见上）", EXIT_EXEC) from None
    if not t:
        raise OpsError("登录未返回 token", EXIT_EXEC)
    _info("   ✓ token 长度 %d" % len(t))

    _info("")
    _info("-- 2. 镜像服务基址（前缀固定 %s）" % image_mod.IMG_API_PREFIX)
    best = ""
    for base in (c.base_url, "http://%s:11000" % c.host, "https://%s:11000" % c.host):
        code, _body = image_mod._img_http("GET", image_mod.IMG_API_PREFIX + "/types", target_base=base)
        if code in ("401", "403"):
            _info("   %s  GET %s%s/types  ← 路由在" % (code, base, image_mod.IMG_API_PREFIX))
            best = base
        elif code == "404":
            _info("   %s  GET %s%s/types  ← 无路由" % (code, base, image_mod.IMG_API_PREFIX))
        else:
            _info("   %s  GET %s%s/types" % (code, base, image_mod.IMG_API_PREFIX))
    if best:
        _info("   ⇒ 用 IMG_BASE_URL=%s 跑后续命令" % best)
    else:
        _info("   ⇒ 都没路由：换探测路径，或确认平台版本是否含容器云模块")

    probe_base = best or os.environ.get("IMG_BASE_URL") or ""

    _info("")
    _info("-- 3. 镜像分类 UUID（构建必填 imgType）")
    types = image_mod.image_types(target_base=probe_base)
    if types == "NETWORK_UNREACHABLE" or not types:
        _info("   取不到（网络或路由问题）")
    else:
        for line in types.splitlines():
            f = (line.split("\t") + ["", ""])[:2]
            _info("   uuid=%s  name=%s" % (f[0], f[1]))

    _info("")
    _info("-- 4. 已有镜像（确认 tag 占用 / 取回滚点）")
    vers = image_mod.image_versions(project=harbor_project, target_base=probe_base)
    if vers == "NETWORK_UNREACHABLE" or not vers:
        _info("   取不到（网络或路由问题）")
    else:
        for line in vers.splitlines():
            f = (line.split("\t") + ["", "", ""])[:3]
            _info("   %-28s tagCount=%s  size=%sMB" % (f[0], f[1], f[2]))

    _info("")
    _info("================ fileFlag 语义怎么验（决定本命令能否用）================")
    _info("文档从未定义 fileFlag，也没有一处提到 Dockerfile 能否 COPY 外部文件。")
    _info("最快判定法：跑一次最小构建，看返回 data.build[] 里 docker 原话：")
    _info("")
    _info("  情况 1（可用）：日志出现 COPY/ADD 且有 context 大小")
    _info("                  ⇒ fileFlag 就是构建上下文文件，本命令成立。")
    _info("  情况 2（不成立）：报 COPY failed: file not found，或日志只有 FROM 一行")
    _info("                  ⇒ fileFlag 只是任务标识，镜像仓库装不进 jar。")
    _info("                     转而用软件中心的 jar 类型部署（平台自带 JDK 镜像，x86）。")
    _info("")
    _info("最小构建的 Dockerfile 只写两行：")
    _info("  FROM %s/jdk:21" % harbor_project)
    _info("  COPY app.jar /app.jar")
    ui.result({"host": c.host, "img_base_url_best": best, "harbor_project": harbor_project})
    return 0


def _cmd_plan(mod: str, ver: str, state: dict, harbor_project: str) -> int:
    p = _image_prepare(mod, ver, state)
    img_type = state["img_type"] or os.environ.get("IMG_TYPE_UUID") or ""
    img_base = os.environ.get("IMG_BASE_URL") or auth.cfg().base_url

    _info("================ 将要执行（dry-run，未发任何写请求）================")
    _info("服务        : %s" % mod)
    _info("版本        : %s" % ver)
    _info("jar         : %s  (%s bytes)" % (p["jar"], p["size"]))
    _info("Dockerfile  : %s" % p["dockerfile"])
    _info("fileFlag    : %s   ← 推断：客户端预生成的文件标识（未验证）" % p["file_flag"])
    _info("镜像        : %s/%s:%s" % (harbor_project, mod, ver))
    _info("构建接口    : POST %s%s/images/build" % (img_base, image_mod.IMG_API_PREFIX))
    _info("")
    _info("-- 上传用字段（POST /api/cloud/deploy/v1.0/packages, multipart）")
    _info("   id=%s  pkgType=jar  name=%s  version=%s" % (p["file_flag"], mod, ver))
    _info("   file=@%s   filename 固定 app.jar（对齐 COPY app.jar）" % p["jar"])
    _info("")
    _info("-- 构建用 body")
    with open(p["dockerfile"], encoding="utf-8") as f:
        dtext = f.read()
    _info("   dockerfile = %s…" % dtext[:120].rstrip("\n"))
    _info("   imgName    = %s" % mod)
    _info("   version    = %s" % ver)
    _info("   imgType    = %s" % (img_type or "<probe 取得>"))
    _info("   type       = private")
    _info("   project    = %s" % harbor_project)
    _info("   fileFlag   = %s" % p["file_flag"])
    _info("")
    _info("-- 前置检查（MUST 人工确认）")
    _info("   [ ] Harbor 里 %s 已有可回滚的上一个 tag" % mod)
    _info("   [ ] 目标 tag %s 未被占用（部署纪律：版本末位 +1，NEVER 同 tag 覆盖）" % ver)
    _info("   [ ] 当前 pod 状态允许滚更（itp-ops.sh cloudos pods %s）" % mod)
    _info("   [ ] IMG_TYPE_UUID 已从 probe 获取")
    _info("")
    _info("-- 架构（ARM 开发机的关键约束）")
    _info("   本机只出 jar（字节码与架构无关），镜像由平台 x86 构建节点打。")
    _info("   但 %s/images/build 的 body **没有 architecture 参数**" % image_mod.IMG_API_PREFIX)
    _info("   （持续交付 BUILD_IMAGE 任务才有），镜像架构由构建节点决定、不可指定。")
    _info("   构建完 MUST 核对产物 arch：")
    _info("     docker manifest inspect %s/%s:%s | grep architecture" % (harbor_project, mod, ver))
    _info("   期望 amd64。拿到 arm64 说明构建节点是 ARM，x86 业务节点拉不起来。")
    _info("")
    _info("确认无误后：itp-ops.sh cloudos image build %s %s --yes" % (mod, ver))
    ui.result({"module": mod, "version": ver, "jar": p["jar"], "jar_size": p["size"],
               "dockerfile": p["dockerfile"], "file_flag": p["file_flag"],
               "img_type": img_type, "harbor_project": harbor_project, "dry_run": True})
    return 0


def _cmd_build(mod: str, ver: str, state: dict, harbor_project: str) -> int:
    ui.log_warn("本命令走旧端点 /api/cloud/hcr/v1.0/images/build —— 红岛实测 404/img.5（IT-024），")
    ui.log_warn("测试同样无该路由。要出镜像请用 itp-ops.sh buildx（buildx 端点，两环境通用）。")
    # 差异 §4：门提到 image_prepare 之前，缺 --yes 本地即拒
    require_yes("build 会向生产平台发写请求（先跑 image plan 看清单）")

    p = _image_prepare(mod, ver, state)
    img_type = state["img_type"] or os.environ.get("IMG_TYPE_UUID") or ""
    if not img_type:
        raise OpsError(
            "缺 IMG_TYPE_UUID —— 先跑 itp-ops.sh cloudos image probe 取镜像分类 UUID",
            EXIT_PRECONDITION,
        )

    _info("== 1/4 上传 jar（fileFlag=%s）" % p["file_flag"])
    if not pkg_mod.pkg_upload(p["jar"], mod, ver, p["file_flag"], "app.jar"):
        raise OpsError("上传失败", EXIT_EXEC)
    _info("   ✓ 上传接口不回 ID，fileFlag 用预生成值")

    # 回查确认落盘（大小对得上是上传自检的唯一手段）
    found = pkg_mod.pkg_find_version(mod, ver)
    if found:
        f = (found.split("\t") + ["", ""])[:2]
        _info("   ✓ 回查版本 ID=%s  文件=%s" % (f[0], f[1]))
    else:
        _info("   ⚠ 回查不到该版本 —— 上传可能未落盘，继续构建大概率失败")

    _info("== 2/4 提交镜像构建（平台 x86 节点 docker build & push）")
    with open(p["dockerfile"], encoding="utf-8") as f:
        dockerfile_text = f.read()
    code, body = image_mod.image_build(
        dockerfile_text, mod, ver, img_type, p["file_flag"], harbor_project,
        "built from local jar via itp-ops.sh cloudos image",
    )
    if http_mod.is_net_fail(code):
        raise OpsError("构建请求未发出（网络不可达）", EXIT_TRANSPORT)
    if code != "200":
        raise OpsError("构建 HTTP %s：%s" % (code, body[:600]), EXIT_EXEC)

    try:
        d = json.loads(body)
    except ValueError:
        raise OpsError("构建响应不是 JSON", EXIT_EXEC) from None
    if d.get("status") is not True:
        _err("构建失败：code=%s msg=%s" % (d.get("code"), d.get("msg")))
        _info("   ⚠ 响应里没找到 digest —— 判据是 Harbor 里有该 tag，不是 HTTP 200")
    else:
        data = d.get("data") or {}
        build, push = data.get("build") or [], data.get("push") or []
        digest = None
        for e in push:
            digest = (e.get("aux") or {}).get("Digest") or digest
            st = e.get("status") or ""
            if "digest:" in st:
                digest = st.split("digest:")[-1].strip().split()[0]
        # build 日志里出现 COPY/ADD 才说明 fileFlag 是构建上下文文件
        copy_seen = any(
            ("COPY" in (e.get("stream") or "")) or ("ADD" in (e.get("stream") or ""))
            for e in build
        )
        _info("   ✓ 构建+推送完成")
        _info("   docker 日志里 COPY/ADD：%s" % (
            "出现 ✓ ⇒ fileFlag 是上下文文件" if copy_seen else "未出现 ⚠ 见文件尾缺口 1"))
        _info("   digest = %s" % (digest or "（未在响应里找到，去 Harbor 核对）"))
        if not digest:
            _info("   ⚠ 响应里没找到 digest —— 判据是 Harbor 里有该 tag，不是 HTTP 200")

    _info("== 3/4 回查镜像版本")
    detail = image_mod.image_detail(mod, harbor_project)
    if detail and detail != "NETWORK_UNREACHABLE":
        for line in detail.splitlines():
            f = (line.split("\t") + ["", "", ""])[:3]
            _info("   %-20s %-12s %s" % (f[0], f[1], f[2]))
    else:
        _info("   回查失败，去 Harbor 页面核对")

    _info("== 4/4 滚更应用（本命令不做）")
    _info("   只负责把镜像推到 Harbor；应用侧仍走既有发布流程：")
    _info("   itp-ops.sh cloudos app %s  → 确认当前镜像 → 平台改 image tag 滚更" % mod)
    _info("")
    _info("回滚：把应用 image tag 指回上一个可用版本（Harbor 里旧 tag 未删除）")
    ui.result({"module": mod, "version": ver, "harbor_project": harbor_project,
               "file_flag": p["file_flag"], "done": True})
    return 0


# ---------------------------------------------------------------- 测试环境附加
def _pkg_upload_cmd(argv: list[str]) -> int:
    if not argv or not argv[0]:
        raise OpsError("需要文件路径", EXIT_USAGE)
    file = argv[0]
    name = argv[1] if len(argv) > 1 and argv[1] else os.path.basename(file)
    ver = argv[2] if len(argv) > 2 and argv[2] else "v1"
    require_yes(f"pkg-upload {file}")
    if not os.path.isfile(file):
        raise OpsError("文件不存在：%s" % file, EXIT_PRECONDITION)
    # id 客户端预生成（上传接口不回 ID；旧测试脚本用 name-version 拼，格式不符平台短 ID）
    id_ = pkg_mod.pkg_gen_id()
    if not pkg_mod.pkg_upload(file, name, ver, id_):
        raise OpsError("上传失败", EXIT_EXEC)
    _info("   ✓ 已上传（id=%s；回查：itp-ops.sh cloudos pkg-list，按 name+version 找）" % id_)
    ui.result({"id": id_, "name": name, "version": ver, "file": file})
    return 0


# ---------------------------------------------------------------- CATALOG
CATALOG = {
    "name": "cloudos",
    "summary": "云管台运维：应用列表/env/实例/日志/副本伸缩/规格/升级/镜像构建（新华三 CloudOS E7501 PaaS）",
    "default_env": "hongdao",
    "env_note": "红岛生产（172.120.209.151 / itpbadmin / ns itpb）为默认；--env test 切测试（172.20.217.210 / admin / ns itp）",
    "subcommands": [
        {"name": "health", "usage": "cloudos health", "summary": "连通性自检（网卡/TCP/登录/业务），写操作前必跑",
         "args": [], "writes": False, "example": "itp-ops.sh cloudos health",
         "stdout_human": "===== CloudOS 连通性自检（172.120.209.151）===== … 结论：可以操作",
         "json_data": {"host": "172.120.209.151", "env": "hongdao", "ok": True}},
        {"name": "auth-debug", "usage": "cloudos auth-debug", "summary": "登录诊断（密文长度、公钥指纹、token）",
         "args": [], "writes": False, "example": "itp-ops.sh cloudos auth-debug",
         "stdout_human": "===== 登录请求 ===== … 返回码对照：…",
         "json_data": {"http": "200", "rsa_len": 172}},
        {"name": "logout", "usage": "cloudos logout", "summary": "清本环境 token 缓存",
         "args": [], "writes": False, "example": "itp-ops.sh cloudos logout",
         "stdout_human": "已清 token 缓存 /tmp/.cloudos-hongdao.token",
         "json_data": {"token_cache": "/tmp/.cloudos-hongdao.token"}},
        {"name": "apps", "usage": "cloudos apps [status]", "summary": "应用列表；status 可选 OK/Error/Stopped...；别名 check→health",
         "args": [{"name": "status", "required": False}], "writes": False,
         "example": "itp-ops.sh cloudos apps Error",
         "stdout_human": "共 27 个应用\nNAME  UUID  STATUS  INST  VERSION  NAMESPACE …",
         "json_data": {"count": 27, "apps": [{"name": "fep-app", "uuid": "…", "status": "OK"}]}},
        {"name": "app", "usage": "cloudos app <名字|uuid>", "summary": "应用详情（含 env 全量）",
         "args": [{"name": "名字|uuid", "required": True}], "writes": False,
         "example": "itp-ops.sh cloudos app fep-app",
         "stdout_human": "name         : fep-app … --- env（n 项，按 key 排序）--- …",
         "json_data": {"app": {"name": "fep-app"}, "env": {"SERVICE_URL": "…"}}},
        {"name": "env", "usage": "cloudos env <名字|uuid>", "summary": "只打 env（排查 SERVICE_*_URL 是否指向 itp）",
         "args": [{"name": "名字|uuid", "required": True}], "writes": False,
         "example": "itp-ops.sh cloudos env fep-app",
         "stdout_human": "# fep-app / <uuid> / ns=itpb / v=2.0.105\nKEY=value …",
         "json_data": {"name": "fep-app", "env": {"KEY": "value"}}},
        {"name": "svc", "usage": "cloudos svc <名字|uuid>", "summary": "访问方式与 NodePort 映射（别名 service；svc list 等价）",
         "args": [{"name": "名字|uuid", "required": True}], "writes": False,
         "example": "itp-ops.sh cloudos svc fep-app",
         "stdout_human": "vip: …\nservice: …  type=NodePort\n  PORT_NAME  PROTO …",
         "json_data": {"vip": "…", "services": []}},
        {"name": "svc create", "usage": "cloudos svc create <名字|uuid> <服务名> [--type ClusterIP|NodePort] [--port 8080] [--container-port 8080] [--node-port N] [--session-keep]",
         "summary": "新建访问方式（默认集群内网 ClusterIP 8080→8080）。平台是 Service 真源：手工 kubectl apply 的同类对象重装即丢，新增访问方式 MUST 走本命令",
         "args": [{"name": "名字|uuid", "required": True}, {"name": "服务名", "required": True},
                  {"name": "--type", "required": False}, {"name": "--port", "required": False},
                  {"name": "--container-port", "required": False}, {"name": "--node-port", "required": False},
                  {"name": "--session-keep", "required": False}],
         "writes": True, "example": "itp-ops.sh cloudos svc create fep-app fep-app-int --yes",
         "stdout_human": "已提交。核对：itp-ops.sh cloudos svc fep-app 应出现 fep-app-int（type=ClusterIP）",
         "json_data": {"uuid": "…", "service_name": "fep-app-int", "type": "ClusterIP"}},
        {"name": "pods", "usage": "cloudos pods <名字|uuid>", "summary": "实例列表（podIP / ready / 重启次数）；别名 instances",
         "args": [{"name": "名字|uuid", "required": True}], "writes": False,
         "example": "itp-ops.sh cloudos pods fep-app",
         "stdout_human": "POD  READY  POD_IP  RESTART  CONTAINERS …",
         "json_data": {"count": 2, "instances": []}},
        {"name": "logs", "usage": "cloudos logs <名字|uuid> [行数]", "summary": "平台侧运行日志（默认 200 行；红岛该接口恒空，主力手段是 itp-ops.sh log tail）",
         "args": [{"name": "名字|uuid", "required": True}, {"name": "行数", "required": False}],
         "writes": False, "example": "itp-ops.sh cloudos logs fep-app 100",
         "stdout_human": "<日志文本>",
         "json_data": {"ns": "itpb", "pod": "…", "container": "…", "tail": "200"}},
        {"name": "act", "usage": "cloudos act <名字|uuid> start|stop|restart", "summary": "启停重启，生产写操作；别名 action",
         "args": [{"name": "名字|uuid", "required": True}, {"name": "action", "required": True}],
         "writes": True, "example": "itp-ops.sh cloudos act fep-app restart --yes",
         "stdout_human": "{美化 JSON 响应}",
         "json_data": {"uuid": "…", "action": "restart"}},
        {"name": "scale", "usage": "cloudos scale <名字|uuid> <副本数>", "summary": "调单个副本数（异步，自动轮询到 ready）；已是目标则跳过",
         "args": [{"name": "名字|uuid", "required": True}, {"name": "副本数", "required": True}],
         "writes": True, "example": "itp-ops.sh cloudos scale fep-app 2 --yes",
         "stdout_human": "fep-app 已是 2 实例（2 ready, status=OK），跳过",
         "json_data": {"key": "fep-app", "target": 2, "skipped": True}},
        {"name": "scale-all", "usage": "cloudos scale-all <副本数> [--exclude a,b]", "summary": "批量调副本数；不带 --yes 是预演",
         "args": [{"name": "副本数", "required": True}, {"name": "--exclude", "required": False}],
         "writes": True, "example": "itp-ops.sh cloudos scale-all 1 --exclude fep-app --yes",
         "stdout_human": "待调整（→ 1 实例）： … 共 N 个应用",
         "json_data": {"target": 1, "count": 3}},
        {"name": "spec", "usage": "cloudos spec [名字|uuid|all]", "summary": "查看规格（默认 all）",
         "args": [{"name": "名字|uuid|all", "required": False}], "writes": False,
         "example": "itp-ops.sh cloudos spec fep-app",
         "stdout_human": "fep-app            req=4000m/8192Mi  limit=4000m/8192Mi",
         "json_data": {"count": 27, "specs": []}},
        {"name": "spec-set", "usage": "cloudos spec-set <名字|uuid> [cpu] [mem]", "summary": "设规格（requests=limits 同值，默认 4000m / 8192Mi = 4C/8G），改完滚动重启 Pod",
         "args": [{"name": "名字|uuid", "required": True}, {"name": "cpu", "required": False}, {"name": "mem", "required": False}],
         "writes": True, "example": "itp-ops.sh cloudos spec-set fep-app 4000m 8192Mi --yes",
         "stdout_human": "fep-app 已是 req=limit=4000m/8192Mi，跳过",
         "json_data": {"key": "fep-app", "cpu": "4000m", "memory": "8192Mi"}},
        {"name": "spec-all", "usage": "cloudos spec-all [cpu] [mem] [--exclude a,b]", "summary": "批量对齐规格；不带 --yes 是预演",
         "args": [{"name": "cpu", "required": False}, {"name": "mem", "required": False}, {"name": "--exclude", "required": False}],
         "writes": True, "example": "itp-ops.sh cloudos spec-all 4000m 8192Mi --yes",
         "stdout_human": "目标规格：req=limit=4000m/8192Mi  排除：无 … 待改 N 个，已是目标 M 个",
         "json_data": {"todo": 0, "skip": 27}},
        {"name": "deploy", "usage": "cloudos deploy <名字|uuid> <裸 tag | 镜像名:tag | 完整引用> [--app-version <v>]",
         "summary": "镜像应用切 tag 升级（PATCH advanced-config，env 全量回传）；不带 --yes = 只读预演（含目标 tag 存在性/arch 校验）；「当前镜像」以实例实跑为准；回滚=同命令指回旧 tag；别名 upgrade",
         "args": [{"name": "名字|uuid", "required": True}, {"name": "镜像", "required": True}, {"name": "--app-version", "required": False}],
         "writes": True, "example": "itp-ops.sh cloudos deploy recon-server 1.0.28",
         "stdout_human": "===== 镜像应用升级预演（红岛生产 / ns itpb）===== … 以上为预演（dry-run）…",
         "json_data": {"dry_run": True, "plan": {"cur_img": "…", "new_img": "…"}}},
        {"name": "raw", "usage": "cloudos raw <METHOD> <URI> [BODY]", "summary": "任意 REST 兜底；非 GET/HEAD 视为写操作",
         "args": [{"name": "METHOD", "required": True}, {"name": "URI", "required": True}, {"name": "BODY", "required": False}],
         "writes": True, "example": "itp-ops.sh cloudos raw GET /api/cloud/app-mgmt/v1.0/apps",
         "stdout_human": "{美化 JSON}",
         "json_data": {"status": True}},
        {"name": "image probe", "usage": "cloudos image probe", "summary": "只读探测（基址/镜像分类/已有 tag，仍可用于取 imgType）",
         "args": [], "writes": False, "example": "itp-ops.sh cloudos image probe",
         "stdout_human": "== CloudOS 镜像构建能力探测（只读，不发写请求）== …",
         "json_data": {"host": "172.120.209.151", "img_base_url_best": ""}},
        {"name": "image plan", "usage": "cloudos image plan <模块> <版本> [--jar <p>] [--project <name>] [--img-type <uuid>] [--dockerfile <p>]",
         "summary": "dry-run，打印将提交的全部参数（旧端点路线，见 IT-024）",
         "args": [{"name": "模块", "required": True}, {"name": "版本", "required": True}],
         "writes": False, "example": "itp-ops.sh cloudos image plan fep-app 1.0.0",
         "stdout_human": "================ 将要执行（dry-run，未发任何写请求）================ …",
         "json_data": {"module": "fep-app", "version": "1.0.0", "dry_run": True}},
        {"name": "image build", "usage": "cloudos image build <模块> <版本> [--jar <p>] [--project <name>] [--img-type <uuid>] [--dockerfile <p>]",
         "summary": "走 POST /images/build（历史端点；两套环境都应改用 itp-ops.sh buildx）",
         "args": [{"name": "模块", "required": True}, {"name": "版本", "required": True}],
         "writes": True, "example": "itp-ops.sh cloudos image build fep-app 1.0.0 --yes",
         "stdout_human": "[INFO] == 1/4 上传 jar（fileFlag=…） …",
         "json_data": {"module": "fep-app", "version": "1.0.0", "done": True}},
        {"name": "appgroups", "usage": "cloudos appgroups", "summary": "应用组列表（测试环境附加）",
         "args": [], "writes": False, "example": "itp-ops.sh --env test cloudos appgroups",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "pod-list", "usage": "cloudos pod-list [ns]", "summary": "容器云 pod 列表（前缀 /api/cloud/container/v1.0 待校准）",
         "args": [{"name": "ns", "required": False}], "writes": False,
         "example": "itp-ops.sh --env test cloudos pod-list itp",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "pod-get", "usage": "cloudos pod-get <pod>", "summary": "容器云 pod 详情（前缀待校准）",
         "args": [{"name": "pod", "required": True}], "writes": False,
         "example": "itp-ops.sh --env test cloudos pod-get fep-app-xxx",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "pod-delete", "usage": "cloudos pod-delete <pod>", "summary": "删除容器云 pod（写操作；前缀待校准）",
         "args": [{"name": "pod", "required": True}], "writes": True,
         "example": "itp-ops.sh --env test cloudos pod-delete fep-app-xxx --yes",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "svc-clusters", "usage": "cloudos svc-clusters", "summary": "服务治理集群列表",
         "args": [], "writes": False, "example": "itp-ops.sh --env test cloudos svc-clusters",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "svc-cluster-nodes", "usage": "cloudos svc-cluster-nodes <uuid>", "summary": "服务治理集群节点",
         "args": [{"name": "uuid", "required": True}], "writes": False,
         "example": "itp-ops.sh --env test cloudos svc-cluster-nodes <uuid>",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "pkg-list", "usage": "cloudos pkg-list [page] [size]", "summary": "软件中心 jar 包列表",
         "args": [{"name": "page", "required": False}, {"name": "size", "required": False}],
         "writes": False, "example": "itp-ops.sh --env test cloudos pkg-list 1 20",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "pkg-get", "usage": "cloudos pkg-get <id>", "summary": "软件中心包版本详情",
         "args": [{"name": "id", "required": True}], "writes": False,
         "example": "itp-ops.sh --env test cloudos pkg-get <version_id>",
         "stdout_human": "{美化 JSON}", "json_data": {"status": True}},
        {"name": "pkg-upload", "usage": "cloudos pkg-upload <file> [name] [version]", "summary": "上传软件中心 jar 包（写操作；id 客户端预生成，上传不回 ID）",
         "args": [{"name": "file", "required": True}, {"name": "name", "required": False}, {"name": "version", "required": False}],
         "writes": True, "example": "itp-ops.sh --env test cloudos pkg-upload /path/app.jar app 1.0.0 --yes",
         "stdout_human": "[INFO]    ✓ 已上传（id=…；回查：itp-ops.sh cloudos pkg-list，按 name+version 找）",
         "json_data": {"id": "cGjEhXIdOe", "name": "app", "version": "1.0.0"}},
    ],
}


# ---------------------------------------------------------------- 主入口
def main(args: list[str]) -> int:
    sub = args[0] if args else ""
    rest = args[1:]

    # 无参数 / 帮助：不需要环境初始化（测试环境公钥未配时也能看用法）
    if sub in ("", "-h", "--help", "help"):
        ui.out(_help_text())
        return 0

    env = envx.init_env("hongdao")
    # bash：CLOUDOS_ENV 由入口 --env 别名 / itp_env_init 解析；非显式时环境变量可覆盖
    ctx = ui.context()
    cloudos_env = (env.name if ctx.env_explicit else (os.environ.get("CLOUDOS_ENV") or env.name))
    cfg = auth.env_init(cloudos_env)

    envx.banner("cloudos " + " ".join([sub] + rest))
    # 旧 bash 的环境行（stderr），保留便于核对 CloudOS 地址与账号
    print("== 环境：%s（%s / 账号 %s）==" % (cloudos_env, cfg.base_url, cfg.username),
          file=sys.stderr)

    harbor_project = _harbor_project_default()

    if sub in ("auth-debug", "auth_debug"):
        return _auth_debug()
    if sub in ("health", "check"):
        return _health()
    if sub == "logout":
        return _logout()
    if sub == "apps":
        return _apps(rest[0] if rest else "")
    if sub == "app":
        return _app_detail(_need(rest, 0, "需要应用名或 uuid"))
    if sub == "env":
        return _app_env(_need(rest, 0, "需要应用名或 uuid"))
    if sub in ("svc", "service"):
        if rest and rest[0] == "create":
            return _svc_create(rest[1:])
        if rest and rest[0] == "list":
            return _app_service(_need(rest, 1, "需要应用名或 uuid"))
        return _app_service(_need(rest, 0, "需要应用名或 uuid"))
    if sub in ("pods", "instances"):
        return _app_pods(_need(rest, 0, "需要应用名或 uuid"))
    if sub == "logs":
        return _app_logs(_need(rest, 0, "需要应用名或 uuid"), rest[1] if len(rest) > 1 else "200")
    if sub in ("act", "action"):
        return _app_action(_need(rest, 0, "需要应用名或 uuid"),
                           _need(rest, 1, "需要 start|stop|restart"))
    if sub == "scale":
        rc = _app_scale(_need(rest, 0, "需要应用名或 uuid"), _need(rest, 1, "需要副本数"))
        return 5 if rc != 0 else 0
    if sub == "scale-all":
        return _scale_all(rest)
    if sub in ("deploy", "upgrade"):
        return _app_deploy(rest)
    if sub == "spec":
        return _app_spec_show(rest[0] if rest else "all")
    if sub == "spec-set":
        return _app_spec_set(
            _need(rest, 0, "需要应用名或 uuid"),
            rest[1] if len(rest) > 1 else "4000m",
            rest[2] if len(rest) > 2 else "8192Mi",
        )
    if sub == "spec-all":
        return _spec_all(rest)
    if sub == "raw":
        method = _need(rest, 0, "需要 METHOD")
        uri = _need(rest, 1, "需要 URI")
        body = rest[2] if len(rest) > 2 else ""
        if method not in ("GET", "HEAD"):
            require_yes(f"{method} {uri}")
        _req_json(method, uri, body)
        return 0
    if sub == "appgroups":
        _req_json("GET", f"{APP_API}/appgroups")
        return 0
    if sub == "pod-list":
        uri = f"{CONTAINER_API_PREFIX}/pods"
        if rest:
            uri = f"{uri}?namespace={rest[0]}"
        _req_json("GET", uri)
        return 0
    if sub == "pod-get":
        _req_json("GET", f"{CONTAINER_API_PREFIX}/pods/{_need(rest, 0, '需要 pod 名')}")
        return 0
    if sub == "pod-delete":
        pod = _need(rest, 0, "需要 pod 名")
        require_yes(f"删除 pod {pod}")
        _req_json("DELETE", f"{CONTAINER_API_PREFIX}/pods/{pod}")
        return 0
    if sub == "svc-clusters":
        _req_json("GET", "/api/cloud/svcgw/v1.0/kaas_clusters")
        return 0
    if sub == "svc-cluster-nodes":
        _req_json("GET", f"/api/cloud/svcgw/v1.0/kaas_clusters/{_need(rest, 0, '需要集群 uuid')}")
        return 0
    if sub == "pkg-list":
        page = rest[0] if len(rest) > 0 else "1"
        size = rest[1] if len(rest) > 1 else "20"
        _req_json("GET", f"{pkg_mod.PKG_API_PREFIX}/packages?page={page}&size={size}")
        return 0
    if sub == "pkg-get":
        _req_json("GET", f"{pkg_mod.PKG_API_PREFIX}/packages/{_need(rest, 0, '需要版本 id')}")
        return 0
    if sub == "pkg-upload":
        return _pkg_upload_cmd(rest)
    if sub == "image":
        img_sub = rest[0] if rest else ""
        img_rest = rest[1:]
        if img_sub == "probe":
            return _cmd_probe(harbor_project)
        if img_sub in ("plan", "build"):
            mod, ver, state = _image_parse_args(img_rest)
            if not mod or not ver:
                raise OpsError(
                    "用法：image %s <module> <version>" % img_sub, EXIT_USAGE)
            project = state["project"] or harbor_project
            if img_sub == "plan":
                return _cmd_plan(mod, ver, state, project)
            return _cmd_build(mod, ver, state, project)
        raise OpsError("image 子命令只能是 probe / plan / build（收到 %s）" % img_sub, EXIT_USAGE)

    # 未知命令：usage 照旧打到 stdout（bash 行为；bash 此时已完成 env init，
    # 首行显示真实 CLOUDOS_HOST），退出码按契约为 2
    ui.out(_help_text(cloudos_env, cfg.host))
    raise OpsError(f"未知命令：{sub}", EXIT_USAGE)

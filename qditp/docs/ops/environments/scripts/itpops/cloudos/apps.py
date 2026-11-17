"""cloudos「应用」业务段 —— 连通性自检 / 登录诊断 / 应用与实例只读 / 访问方式 / 日志 / 启停。

从 impl.py 按业务段拆出（2026-11-04），函数体逐字未改。"""

from __future__ import annotations

import json
import os
import re
import subprocess
import time
from . import auth
from . import http as http_mod
from ..core import environments as envx, ui
from ..core.runtime import require_yes, repo_root
from ..errors import (
    EXIT_EXEC,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

from .common import APP_API, _err, _line, _need, _req, resolve_app_uuid

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

    # 2) TCP 443（或 CLOUDOS_PORT 指定的端口）
    port = os.environ.get("CLOUDOS_PORT") or "443"
    if http_mod.check_ports(c.host, [port]):
        ui.out(f"  [2/3] TCP {c.host}:{port} 通")
    else:
        if env.name == "hongdao":
            ui.out(f"  [2/3] TCP {c.host}:{port} **不通**（172.120 段路由是否还指向可用接口？）")
        else:
            ui.out(f"  [2/3] TCP {c.host}:{port} **不通**（测试网 172.20 段是否可达？）")
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
    _line("container_image:", meta.get("container_image"))
    _line("created_at   :", app.get("created_at"))
    _line("description  :", app.get("description"))
    ui.out("")
    ui.out("# 实跑镜像以 cloudos pods <应用> 的 IMAGE 列为准（本字段是平台记录值，可能滞后，见 IT-034）")
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
    ui.out("%-40s %-6s %-16s %-8s %s" % ("POD", "READY", "POD_IP", "RESTART", "IMAGE"))
    ui.out("-" * 110)
    lines = []
    for p in rows:
        # 业务容器镜像（剔除 istio proxyv2 sidecar）；deploy 后核验 image tag 用
        imgs = [c.get("image") or "" for c in (p.get("containers") or [])
                if isinstance(c, dict) and "proxyv2" not in (c.get("image") or "")]
        lines.append("%-40s %-6s %-16s %-8s %s" % (
            p.get("name"), p.get("ready"), p.get("podIP"),
            p.get("restartTime"), ",".join(i for i in imgs if i) or "-"))
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

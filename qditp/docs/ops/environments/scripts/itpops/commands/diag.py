"""itpops · diag 域 —— 网络可达性自检 / 入向路由全链路导出。

移植自 commands/diag.sh（bash，238 行）：reach（原 ops/red-island-reachability.sh）
+ routes（原 ops/dump-48000-routes.sh）二合一。命令面与旧 bash 逐字兼容：
子命令名、参数、stdout 结果文本一致；help 文案原样拷贝（含 DIAG_IF / REMOTE_HOST
两处插值，插值时机也照抄——见下「坑」3）。

【旧 bash 的坑/巧思（原样保留原因）】
  1. reach 是**纯本机**探测：ifconfig / ping / nc 都跑在操作机上——USB 网卡插在
     本机，探测视角必须是本机；只有 routes 经 core.remote 打远端跳板。bash 的
     reach 一个 ssh 都不发，此处零差异保留本机执行（探测目标是远端网络，但探测
     点是本机）。
  2. 区段表多端口行的 read 语义：`IFS='|' read -r label host ports` 三变量收
     「K8s控制|172.120.2.13|22|31920」时 ports="22|31920"（多余分隔符原样保留在
     末变量里），随后 `for p in $ports` 按默认空白分词只得一个 token "22|31920"，
     nc 拿到非法端口恒 FAIL。即 bash 多端口行实际输出 "22|31920:FAIL"。逐字复刻
     （split("|", 2) + str.split()），输出与 bash 字节一致。
  3. help 插值时机：diag_usage 在 itp_env_init **之前**展开，${REMOTE_HOST} 显示
     的是源码期合成值（lib/remote.sh: `REMOTE_HOST="${ITP_REMOTE_HOST:-k8s-master}"`
     + diag.sh: `REMOTE_HOST="${SSH_HOST:-${REMOTE_HOST}}"`）= SSH_HOST ||
     ITP_REMOTE_HOST || k8s-master；${DIAG_IF} 默认 en9。照抄。
  4. SSH_HOST 兼容（旧 dump-48000-routes.sh 的环境变量名）：bash 在 source 期把
     SSH_HOST 并进 REMOTE_HOST，且 _env_apply 非显式路径覆盖不掉非空 REMOTE_HOST
     ——于是 SSH_HOST 能一路活到 routes 的连通性预检。这里在入口等价合成
     （SSH_HOST 非空则写入 REMOTE_HOST 环境变量，供 envx 同名覆盖链取用）；
     显式 --env 时两侧都强制 profile 值（SSH_HOST 被压掉），语义一致。
  5. 基线目录路径表达式 `${ITP_OPS_DIR}/../migration/ingress-config-cleaned` 原样
     复刻（= environments/migration/…）。注意：README 称「硬依赖」的真基线在
     scripts/migration/ingress-config-cleaned，bash 的 `../` 疑似多打一层——当前
     树上该目录恒缺失、恒走「基线目录缺失」显式告警分支（NEVER 静默）。为 stdout
     逐字兼容保留原表达式；要修 MUST 新旧两侧同修。
  6. envoy 路由抽取的内嵌 walk 逐行复刻，含潜在坑：`m.get("safe_regex", {})` 在
     JSON 里 safe_regex 显式为 null 时会 AttributeError（bash 内嵌 python 同样会
     崩、崩前部分行已落盘）。按零差异原则不修。
  7. dump 失败时 stderr 首行进提示、yaml 文件删除；.err 中转文件在 Python 版里
     用内存捕获等价替代（终态目录内容一致）。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 失败退出码细分：bash 一律 return 1（need_cmd die 也是 exit 1）；Python 按
     契约抛 OpsError——缺 ssh/python3、mkdir 失败、网卡没拿到 IP → 前置 3；
     ssh 跳板不通 → 传输 4；未知子命令 → 用法 2（usage 照旧打 stdout，与
     cloudos 域同约定）。
  2. 错误行前缀：cli 统一以「错误：」落 stderr（bash 是裸文本「未知命令：x」）。
  3. 「基线目录缺失」告警里的路径串：bash 显示含 `../` 的未归一路径，Python 显示
     归一绝对路径（stderr 仅人看，指向同一位置）。
  4. 远端命令里的 POD/CT 拼接用 remote_quote_args 转义（bash 是裸插值；正常 pod
     名下两者字节一致，仅恶意/异常名时 Python 更安全）。
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path

from ..core import environments as envx, remote, ui
from ..core.runtime import need_cmd, scripts_dir
from ..errors import EXIT_PRECONDITION, EXIT_TRANSPORT, EXIT_USAGE, OpsError

# ---------------------------------------------------------------- 帮助（逐字拷贝自 diag_usage，两处插值见「坑」3）
def _help_remote_host() -> str:
    """bash 源码期 REMOTE_HOST：SSH_HOST || ITP_REMOTE_HOST || k8s-master（help 在 env init 前展开）。"""
    return os.environ.get("SSH_HOST") or os.environ.get("ITP_REMOTE_HOST") or "k8s-master"


def _help_text() -> str:
    iface = os.environ.get("DIAG_IF") or "en9"
    host = _help_remote_host()
    return f"""\
itp-ops.sh diag —— 网络可达性自检 / 入向路由导出

用法: itp-ops.sh diag <命令> [参数...]

命令
  reach                       红岛分区可达性自检：看 USB 网卡 {iface} 拿到哪个区的
                              IP，逐区探 ICMP + TCP 端口，判定「插到哪个区、
                              哪些区通了」。插好网卡后跑一次即可。
  routes [输出目录]           导出 ITP 公网 48000 入向路由全链路配置到文件：
                              ns itp-gateway 的网关 + ns itp 的 Gateway/VirtualService，
                              含 envoy 实际下发路由，并与仓库基线快照 diff。
                              默认输出到 ~/itp-snapshots/48000-routes-<时间戳>/。
  help                        本帮助

前置与说明
  · routes 导出的是「集群当前值」，不是仓库基线；diff 段用来自动比对基线快照是否漂移。
  · routes 前置：本机能 ssh {host}（测试环境 172.20.211.21:22），不通就先换公司网 / 进 VPN。
  · reach 判读口诀：三项关键服务全 ✅ 才全通；插在出口区 .249 是隔离的，换口即可。"""


# ---------------------------------------------------------------- 本机探测小工具（reach 纯本机，见「坑」1）
def _run_local(cmd: list[str]) -> subprocess.CompletedProcess:
    """本机执行，失败/缺命令都只当作「探测失败」，不抛（bash 里 >/dev/null 2>&1 后只看退出码）。"""
    try:
        return subprocess.run(cmd, capture_output=True, text=True)
    except OSError:
        return subprocess.CompletedProcess(cmd, 127, "", "")


def _iface_ip(iface: str) -> str:
    """ifconfig $IF | awk '/inet /{print $2}' | head -1 的等价（首个含 "inet " 行的第 2 字段）。"""
    r = _run_local(["ifconfig", iface])
    for line in r.stdout.splitlines():
        if "inet " in line:
            fields = line.split()
            return fields[1] if len(fields) >= 2 else ""
    return ""


def _ping_ok(host: str) -> bool:
    return _run_local(["ping", "-c1", "-t1", host]).returncode == 0


def _nc_ok(host: str, port: str, timeout: str) -> bool:
    return _run_local(["nc", "-z", "-G", timeout, host, port]).returncode == 0


# ---------------------------------------------------------------- reach（原 red-island-reachability.sh）
# 区段表逐字拷贝：| 标签 | 代表 host | 要探的端口。⚠ 多端口行的语义见「坑」2——
# bash read 三变量收四段时 ports="22|31920"，nc 拿到的是单个非法端口 token。
_ZONES = (
    "K8s控制|172.120.2.13|22|31920",
    "K8s工作|172.120.1.11|22",
    "云管|172.120.3.10|22",
    "业务|172.120.110.11|31919|31920",
    "私有|172.120.125.1|22",
    "安全|172.120.209.101|22",
    "工作站|172.120.211.1|22",
    "核心|172.120.216.1|22",
    "出口|172.120.249.254|",
    "管理|172.120.250.1|22",
    "环回|172.120.255.1|22",
)

_KEY_SERVICES = (
    "hd-linux SSH|172.120.2.13|22",
    "VL|172.120.2.13|31920",
    "前端 web|172.120.110.11|31919",
)


def _reach() -> int:
    iface = os.environ.get("DIAG_IF") or "en9"
    ui.out(f"=== 1. 网卡 {iface} 当前 IP（决定你插到哪个红岛区）===")
    ip = _iface_ip(iface)
    if not ip:
        # bash：提示落 stderr 后 return 1；Python 原样打提示 + 细分码（差异 1）
        print(
            f"  ⚠️  {iface} 没拿到 IP——USB 没插好或没插业务口。插到红岛 K8s 控制区 172.120.2.x 口后重试。",
            file=sys.stderr,
        )
        raise OpsError(f"网卡 {iface} 没拿到 IP（USB 没插好或没插业务口）", EXIT_PRECONDITION)
    ui.out(f"  {iface} = {ip}")
    if ip.startswith("172.120.2."):
        ui.out("  → K8s 控制区（可直达 hd-linux:22 / VL:31920）")
    elif ip.startswith("172.120.110."):
        ui.out("  → 业务区（可直达 前端:31919）")
    elif ip.startswith("172.120.249."):
        ui.out("  ⚠️  出口区（隔离！到 K8s/业务无三层路由，只能 ping 本网段网关）")
    else:
        ui.out(f"  → 未知区（{ip}），逐区探测看哪些通")

    ui.out("")
    ui.out("=== 2. 逐区可达性（ping + TCP 端口）===")
    zones: list[dict] = []
    for entry in _ZONES:
        label, host, ports_str = entry.split("|", 2)
        icmp = "OK" if _ping_ok(host) else "FAIL"
        pout = ""
        port_marks: dict[str, str] = {}
        # bash：IFS='|' read 只作用于 read 本身，这里按默认空白分词——"22|31920" 是单 token（坑 2）
        for p in ports_str.split():
            ok = _nc_ok(host, p, "3")
            pout += f" {p}:{'OK' if ok else 'FAIL'}"
            port_marks[p] = "OK" if ok else "FAIL"
        ui.out("  %-8s %-18s ICMP=%-4s%s" % (label, host, icmp, pout))
        zones.append({"label": label, "host": host, "icmp": icmp, "ports": port_marks})

    ui.out("")
    ui.out("=== 3. 关键服务探活（前端 / VL / hd-linux SSH）===")
    key_services: list[dict] = []
    for target in _KEY_SERVICES:
        label, host, port = target.split("|", 2)
        ok = _nc_ok(host, port, "5")
        if ok:
            ui.out(f"  ✅ {label} {host}:{port} 可达")
        else:
            ui.out(f"  ❌ {label} {host}:{port} 不可达")
        key_services.append({"label": label, "host": host, "port": port, "ok": ok})

    ui.out("")
    ui.out("=== 4. 结论 ===")
    ui.out("  若 3 全 ✅：已全通，可 ssh hd-linux 拉集群 + 回写 MEMORY")
    ui.out(f"  若 hd-linux/VL ✅ 但 前端 ❌：说明前端在 172.120.110.x 业务区，你的 {iface} 在 .2 控制区，"
           "跨区不通——需红岛侧放行 .2↔.110 三层路由")
    ui.out("  若 3 全 ❌：你插在出口区 .249（隔离区），换插 K8s 控制区 .2 或 业务区 .110 的口")

    ui.result({"iface": iface, "ip": ip, "zones": zones, "key_services": key_services})
    return 0


# ---------------------------------------------------------------- routes（原 dump-48000-routes.sh）
_DUMP_OBJECTS = (
    ("vs", "fep-app-vr", "itp", "fep-app-vr.yaml"),
    ("gateway", "accountserver-gw", "itp", "accountserver-gw.yaml"),
    ("svc", "itp-gateway", "itp-gateway", "itp-gateway-service.yaml"),
    ("svc", "itp-gateway-tgrce-svc", "itp-gateway", "itp-gateway-tgrce-svc.yaml"),
    ("deploy", "itp-gateway", "itp-gateway", "itp-gateway-deployment.yaml"),
)

# 内嵌 python 的 jsonpath（bash 里是单引号串，原样保留）
_VS_JSONPATH_CMD = (
    r"""kubectl get vs fep-app-vr -n itp -o jsonpath='{range .spec.http[*]}"""
    r"""{.match[0].uri.prefix}{" -> "}{.route[0].destination.host}{":"}"""
    r"""{.route[0].destination.port.number}{"  rewrite="}{.rewrite.uri}{"\n"}{end}'"""
)


def _envoy_walk(o: object, rows: list[tuple[str, str]], seen: set[tuple[str, str]]) -> None:
    """bash 内嵌 python 的 walk 逐行复刻（含 safe_regex 潜在坑，见「坑」6）。"""
    if isinstance(o, dict):
        m, rt = o.get("match"), o.get("route")
        if isinstance(m, dict) and isinstance(rt, dict):
            p = m.get("path") or m.get("prefix") or m.get("safe_regex", {}).get("regex", "")
            c = rt.get("cluster") or ((rt.get("weighted_clusters") or {}).get("clusters") or [{}])[0].get("name", "")
            if c and (p, c) not in seen:
                seen.add((p, c))
                rows.append((p, c))
        for v in o.values():
            _envoy_walk(v, rows, seen)
    elif isinstance(o, list):
        for v in o:
            _envoy_walk(v, rows, seen)


def _routes(out_arg: str) -> int:
    host = remote.remote_host()
    ts = time.strftime("%Y%m%d-%H%M%S")
    out_dir = out_arg or str(Path.home() / "itp-snapshots" / f"48000-routes-{ts}")

    ui.out(f"=== 0. 连通性预检（{host}）===")
    if not remote.remote_reachable():
        print(f"  ⚠️  ssh {host} 不通，未生成任何文件。换公司网 / 进 VPN 后重跑。", file=sys.stderr)
        raise OpsError(f"ssh {host} 不通，未生成任何文件（换公司网 / 进 VPN 后重跑）", EXIT_TRANSPORT)
    ui.out("  OK")
    try:
        os.makedirs(out_dir, exist_ok=True)
    except OSError as exc:
        raise OpsError(f"无法创建输出目录：{out_dir}（{exc}）", EXIT_PRECONDITION) from exc

    ui.out("=== 1. 导出对象 yaml（--show-managed-fields=false，剥运行时噪声）===")
    info: dict = {"output_dir": out_dir}

    def dump(kind: str, name: str, ns: str, file: str) -> None:
        # kind/name/ns 是固定注册表值；stderr 首行进提示、失败删 yaml（bash 行为）
        r = remote.remote_exec(f"kubectl get {kind} {name} -n {ns} -o yaml --show-managed-fields=false")
        path = Path(out_dir) / file
        path.write_text(r.stdout, encoding="utf-8")
        if r.returncode == 0:
            ui.out(f"  ✓ {file}")
        else:
            err_lines = (r.stderr or "").splitlines()
            first_err = err_lines[0] if err_lines else ""
            ui.out(f"  ✗ {file}（{first_err}）")
            path.unlink(missing_ok=True)

    for kind, name, ns, file in _DUMP_OBJECTS:
        dump(kind, name, ns, file)

    ui.out("=== 2. 路由摘要（spec.http 下标 = 行号 - 1）===")
    r2 = remote.remote_exec(_VS_JSONPATH_CMD)
    numbered = ["%6d\t%s" % (i, line) for i, line in enumerate(r2.stdout.splitlines(), 1)]
    block = "\n".join(numbered) + ("\n" if numbered else "")
    (Path(out_dir) / "routes.txt").write_text(block, encoding="utf-8")
    if block:
        ui.out(block[:-1] if block.endswith("\n") else block)
    info["route_summary"] = [line for line in r2.stdout.splitlines()]

    ui.out("=== 3. envoy 实际下发路由（VS 写对了 ≠ 已下发）===")
    r_pod = remote.remote_exec("kubectl get pod -n itp-gateway -o jsonpath='{.items[0].metadata.name}'")
    pod = r_pod.stdout.replace("\r", "").rstrip("\n")
    envoy_count = 0
    if pod:
        # bash 是裸插值；remote_quote_args 转义（差异 4，正常 pod 名下字节一致）
        pod_q = remote.remote_quote_args([pod])
        r_ct = remote.remote_exec(
            f"kubectl get pod {pod_q} -n itp-gateway -o jsonpath='{{.spec.containers[0].name}}'"
        )
        ct = r_ct.stdout.replace("\r", "").rstrip("\n")
        ct_q = remote.remote_quote_args([ct])
        ui.out(f"  pod={pod} container={ct}")
        r_envoy = remote.remote_exec(
            f"kubectl exec -n itp-gateway {pod_q} -c {ct_q} -- "
            f"curl -s '127.0.0.1:15000/config_dump?resource=dynamic_route_configs'"
        )
        (Path(out_dir) / "envoy-route-config-dump.json").write_text(r_envoy.stdout, encoding="utf-8")
        # bash 里这里是一段内嵌 python（stdout 重定向进 envoy-routes.txt）——包括
        # 「解析失败：…」也落文件而非终端。逐行移植（见「坑」6）。
        lines_out: list[str] = []
        try:
            data = json.loads(r_envoy.stdout)
        except Exception as exc:  # noqa: BLE001 —— 与内嵌脚本一致：解析失败不致命
            lines_out = ["解析失败：%s" % exc]
        else:
            rows: list[tuple[str, str]] = []
            seen: set[tuple[str, str]] = set()
            _envoy_walk(data, rows, seen)
            lines_out = ["%-34s -> %s" % (p, c) for p, c in rows]
        body = "\n".join(lines_out) + ("\n" if lines_out else "")
        (Path(out_dir) / "envoy-routes.txt").write_text(body, encoding="utf-8")
        envoy_count = body.count("\n")
        ui.out(f"  ✓ envoy-routes.txt（{envoy_count} 条）")
    else:
        ui.out("  ✗ 取不到 itp-gateway pod，跳过")
    info["pod"] = pod or None
    info["container"] = ct if pod else None
    info["envoy_route_count"] = envoy_count

    ui.out("=== 4. 与仓库基线快照 diff（只比 VS/Gateway）===")
    # 修复记录：旧版用 $(dirname $0)/migration/...，脚本迁进 commands/ 后失效，
    # 被 [ -f ] 静默跳过（看着 diff 过、实际没比）。现在显式定位 + 缺失告警。
    # ⚠ 路径表达式原样复刻（含多打的 ../，真基线在 scripts/migration/…）——见「坑」5。
    base = scripts_dir().parent / "migration" / "ingress-config-cleaned"
    if not base.is_dir():
        ui.log_warn(f"基线目录缺失：{base} —— 跳过 diff 段（NEVER 静默：没比过基线就是没比过）")
    else:
        # 2026-10-26 审计：基线快照本身已过期（fep-app-vr.yaml 只有 8 条 prefix，
        # 真值是 5 条 SCG exact + 8 条 prefix = 13 条；2026-11-03 随 IT-050 由 15/7 调整为 13/5）。
        # 用它 diff 会把已上线路由误报成漂移，所以每次比对前都显式告警——NEVER 让「diff 有输出」被当成发现真问题。
        ui.log_warn("⚠️ 基线快照可能已过期：2026-10-26 审计发现 fep-app-vr.yaml 缺 SCG exact 路由"
                    "（真值 13 条：5 SCG exact + 8 prefix）。")
        ui.log_warn("   diff 报出的差异 MUST 先排除基线陈旧因素；重抓方法见 migration/ingress-config-cleaned/README.md")
        for f in ("fep-app-vr.yaml", "accountserver-gw.yaml"):
            base_f, out_f = base / f, Path(out_dir) / f
            if base_f.is_file() and out_f.is_file():
                r = _run_local(["diff", "-u", str(base_f), str(out_f)])
                if r.returncode == 0:
                    ui.out(f"  = {f} 与基线一致")
                else:
                    (Path(out_dir) / (f + ".diff")).write_text(r.stdout + r.stderr, encoding="utf-8")
                    ui.out(f"  ≠ {f} 有差异（见 {f}.diff，通常只是 creationTimestamp/resourceVersion/uid）")
            else:
                ui.log_warn("基线或导出缺 %s（base=%s out=%s），跳过"
                            % (f, "有" if base_f.is_file() else "无", "有" if out_f.is_file() else "无"))

    ui.out("")
    ui.out(f"输出目录：{out_dir}")
    files = sorted(os.listdir(out_dir))
    for name in files:
        ui.out(f"  {name}")
    info["files"] = files
    ui.result(info)
    return 0


# ---------------------------------------------------------------- 域入口
CATALOG = {
    "name": "diag",
    "summary": "网络可达性自检 / 入向路由全链路导出（reach 纯本机探测；routes 经跳板导出集群配置）",
    "default_env": None,
    "env_note": "按子命令（刻意不统一，NEVER 改齐）：reach=hongdao、routes=test",
    "subcommands": [
        {
            "name": "reach",
            "usage": "diag reach",
            "summary": "红岛分区可达性自检：看 USB 网卡（DIAG_IF，默认 en9）拿到哪个区的 IP，逐区探 ICMP + TCP 端口，判定「插到哪个区、哪些区通了」",
            "args": [],
            "writes": False,
            "example": "itp-ops.sh diag reach",
            "stdout_human": "=== 1. 网卡 en9 当前 IP（决定你插到哪个红岛区）=== … === 4. 结论 ===",
            "json_data": {"iface": "en9", "ip": "172.120.2.x",
                          "key_services": [{"label": "VL", "host": "172.120.2.13", "port": "31920", "ok": True}]},
        },
        {
            "name": "routes",
            "usage": "diag routes [输出目录]",
            "summary": "导出 ITP 公网 48000 入向路由全链路配置到文件：ns itp-gateway 的网关 + ns itp 的 Gateway/VirtualService，含 envoy 实际下发路由，并与仓库基线快照 diff",
            "args": [{"name": "输出目录", "required": False}],
            "writes": False,
            "example": "itp-ops.sh diag routes",
            "stdout_human": "=== 0. 连通性预检（k8s-master）=== … 输出目录：~/itp-snapshots/48000-routes-<时间戳>/",
            "json_data": {"output_dir": "~/itp-snapshots/48000-routes-…",
                          "files": ["fep-app-vr.yaml", "routes.txt", "envoy-routes.txt"]},
        },
    ],
}


def main(args: list[str]) -> int:
    need_cmd("ssh")  # bash diag_main 第一句（含 help 在内都要求）
    # bash diag.sh:23 源码期合成：REMOTE_HOST="${SSH_HOST:-${REMOTE_HOST}}"（坑 4）
    if os.environ.get("SSH_HOST"):
        os.environ["REMOTE_HOST"] = os.environ["SSH_HOST"]

    sub = args[0] if args else ""
    rest = args[1:]

    if sub in ("", "-h", "--help", "help"):
        ui.out(_help_text())
        return 0
    if sub == "reach":
        # 纯红岛：探测目标全是 172.120.x 网段（见 _ZONES 区段表）
        envx.init_env("hongdao")
        envx.banner("红岛分区可达性（K8s 控制区 / 业务网 / 出口区）")
        return _reach()
    if sub == "routes":
        # 纯测试环境：抓的是 ns itp / itp-gateway 的 VS/Gateway（红岛是 itpb + ns istio-ingress）
        envx.init_env("test")
        env = envx.current()
        envx.banner(f"入向路由导出（ns {env.k8s_ns}，入口 {env.ingress_entry}）")
        need_cmd("python3")
        return _routes(rest[0] if rest else "")

    ui.out(_help_text())
    raise OpsError(f"未知命令：{sub}", EXIT_USAGE)

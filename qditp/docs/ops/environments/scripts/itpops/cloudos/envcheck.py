"""cloudos env-check —— 发布后 env 漂移巡检（纯 CloudOS 只读，NEVER kubectl/ssh）。

2026-11-06 作为「环境变量升级流程」的配套只读门禁：一次 `GET /apps` 列表即可
巡检平台模型里的 env，发现跨环境串线、Deployment 骨架滞后/缺失等漂移。

只报不改 —— 要修请走 `cloudos env-set`（同为纯 CloudOS REST，带自动复验）。
"""
from __future__ import annotations

from ..core import environments as envx, ui
from ..errors import EXIT_USAGE, OpsError
from .common import APP_API, _req

# 红岛/生产环境的地址特征：测试环境出现即为跨环境串线（IT-016 RPC / IT-036 DB）。
_BAD_HOST_MARKERS = ("hongdao", "itpb", "172.120.")
_JVM_KEYS = ("max_memory", "reserved_memory", "max_direct_memory")


def check_app(app: dict) -> dict:
    """对单个应用详情做只读判据，返回结构化结果（不发任何写请求）。"""
    meta = app.get("metadata") or {}
    env = meta.get("env") or {}
    cur_img = meta.get("container_image") or ""
    issues: list[tuple[str, str]] = []

    if not env:
        # 2026-11-06 实测：victoria-logs / kennethreitz-httpbin 等基础设施应用平台模型
        # 本就不记 env，报 ERROR 是误报。只给 WARN，ERROR 留给跨环境串线这类真 anomaly。
        issues.append(("WARN", "metadata.env 为空（非 Java/基础设施应用平台模型可能不记 env；"
                               "Java 应用需核对 advanced-config 全量替换）"))

    for k, v in env.items():
        if not isinstance(v, str):
            continue
        watched = (k.startswith("SERVICE_") and k.endswith("_URL")) or k in ("DB_HOST", "DB_URL")
        if watched and any(marker in v for marker in _BAD_HOST_MARKERS):
            issues.append(("ERROR", "%s 指向红岛/itpb：%s" % (k, v)))

    missing = [k for k in _JVM_KEYS if k not in env]
    if missing and env:            # env 为空时不再重复报一条，避免噪音翻倍
        issues.append(("WARN", "JVM 三件套缺 %s（IT-020；非 Java 应用可忽略）"
                       % ",".join(missing)))

    res = ((meta.get("image_metadata") or {}).get("metadata") or {}).get("resources")
    dep = res[1] if isinstance(res, list) and len(res) > 1 and isinstance(res[1], dict) else None
    if not dep:
        issues.append(("WARN", "无 CloudOS Deployment 骨架（env-set 需 --allow-no-snapshot）"))
    else:
        spec = ((dep.get("spec") or {}).get("template") or {}).get("spec") or {}
        snap_img = next((c.get("image") for c in (spec.get("containers") or [])
                         if isinstance(c, dict) and c.get("image")), "")
        if cur_img and snap_img and snap_img != cur_img:
            issues.append(("INFO", "骨架镜像滞后 %s（env-set 会自动按 container_image 校正）"
                           % snap_img.rsplit(":", 1)[-1]))

    return {
        "name": app.get("name"), "namespace": app.get("namespace"),
        "version": app.get("version"), "env_n": len(env),
        "issues": [{"level": lv, "msg": msg} for lv, msg in issues],
        "errors": sum(1 for lv, _ in issues if lv == "ERROR"),
        "warns": sum(1 for lv, _ in issues if lv == "WARN"),
    }


def env_check(argv: list[str]) -> int:
    """cloudos env-check [应用...] [--all] —— 不传参数等价于 --all。"""
    if argv and argv[0] in ("-h", "--help", "help"):
        ui.out("用法: itp-ops.sh cloudos env-check [应用...] [--all]"
               "   # 纯 CloudOS 只读 env 漂移巡检（不写任何东西）")
        return 0
    names = []
    for a in argv:
        if a in ("--all",):
            continue
        if a.startswith("--"):
            raise OpsError("未知参数：%s（env-check 只接受应用名 / --all）" % a, EXIT_USAGE)
        names.append(a)

    d = _req("GET", f"{APP_API}/apps?page=1&pageSize=200")
    raw = (d.get("data") or []) if isinstance(d, dict) else []
    # 平台列表接口返回的是数组；ui.result 信封里才是 {"count","apps"} 形态，两种都兼容。
    apps = raw if isinstance(raw, list) else (raw.get("apps") or [])
    ns = envx.current().k8s_ns
    apps = [a for a in apps if a.get("namespace") == ns]
    if names:
        apps = [a for a in apps if a.get("name") in names]

    rows = [check_app(a) for a in apps]
    profile = envx.current()
    ui.out("===== env 漂移巡检（%s / ns %s）=====" % (profile.label, ns))
    ui.out("  应用                          env   ERROR  WARN  说明")
    for r in rows:
        notes = "; ".join(i["msg"] for i in r["issues"] if i["level"] != "INFO") or "—"
        ui.out("  %-28s %4d   %3d   %3d  %s"
               % (r["name"], r["env_n"], r["errors"], r["warns"], notes[:90]))
    total_err = sum(r["errors"] for r in rows)
    total_warn = sum(r["warns"] for r in rows)
    clean = sum(1 for r in rows if not r["errors"] and not r["warns"])
    ui.out("")
    ui.out("汇总：%d 个应用 / ERROR %d / WARN %d / 干净 %d" % (len(rows), total_err, total_warn, clean))
    ui.out("WARN 多为非 Java/基础设施应用，可忽略；ERROR 是跨环境串线，MUST 处理。")
    ui.out("只报不改；修复走 cloudos env-set（纯 CloudOS REST，PATCH 后自动复验）。")
    ui.result({"env": getattr(profile, "name", "") or "", "namespace": ns, "apps": rows,
               "total": len(rows), "errors": total_err, "warns": total_warn,
               "clean": clean})
    return 0


CATALOG = {
    "name": "env-check",
    "summary": "发布后 env 漂移巡检（纯 CloudOS 只读）：跨环境串线 / JVM 三件套 / Deployment 骨架滞后",
    "usage": "cloudos env-check [应用...] [--all]",
    "args": [{"name": "应用", "required": False, "desc": "不传 = 巡检当前命名空间全部应用"},
             {"name": "--all", "required": False}],
    "writes": False,
    "example": "itp-ops.sh --env test cloudos env-check face-pay-server",
    "stdout_human": "===== env 漂移巡检（test / ns itp）===== … 汇总：25 个应用 / ERROR 0 / WARN 24 / 干净 1",
    "json_data": {"total": 25, "errors": 0, "warns": 24, "apps": []},
}

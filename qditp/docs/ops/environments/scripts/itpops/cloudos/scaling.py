"""cloudos「规格与副本」业务段 —— CPU/内存换算、规格查看与批量对齐、副本伸缩与异步复验。

从 impl.py 按业务段拆出（2026-11-04），函数体逐字未改；
换算与幂等判据的业务级单测见 tests/test_cloudos_units.py。"""

from __future__ import annotations

import re
import time
from ..core import environments as envx, ui
from ..core.runtime import require_yes, repo_root
from ..core.ui import context
from ..errors import (
    EXIT_EXEC,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

from .common import APP_API, _err, _req, resolve_app_uuid

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

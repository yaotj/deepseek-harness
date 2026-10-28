#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 PVC app-logs 挂到 itpb 应用容器的 /home/javaapp/app/logs（走 CloudOS advanced-config 升级接口）。

为什么必须走平台接口而不是 kubectl patch：
  IT-020 已证 —— kubectl patch 改出来的东西会被平台在数分钟内按自身存储的
  metadata 回收。所以唯一可持续的写路径是
  PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config。

关键语义（本脚本存在的理由）：
  * env 是 **全量替换**，不是合并。少给一项 = 平台上该项被删 = Pod 里少一个环境变量。
    必须用 metadata.env（平台存储的完整配置项，含 VLOGS_URL）作为唯一来源，
    绝不能用 kubectl 读回来的容器 env（2026-10-26 就是用它导致 VLOGS_URL 丢失）。
  * resources[1] 必须是完整 Deployment；平台无视取到的旧模板（IT-020），
    本脚本按 kubectl 现网 Deployment 重建（去掉 server-side 字段）。
  * version 第 4 段 +1；version_id 用应用当前的 upgrade_id；container_image 不变。

用法：
  python3 mount-applogs.py --list                      # 列出可挂载的 Java 应用
  python3 mount-applogs.py <app> [...] [--dry-run]     # 预演（默认）
  python3 mount-applogs.py <app> [...] --yes           # 真下发
  python3 mount-applogs.py <app> --fix-env             # 只补回丢失的 env 项（不新增挂载）
"""

import json
import os
import re
import subprocess
import sys
import tempfile
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
ITP_OPS = os.path.join(ROOT, "docs/ops/environments/scripts/itp-ops.sh")

NS = "itpb"
PVC = "app-logs"
MOUNT_PATH = "/home/javaapp/app/logs"
VOL_NAME = "app-logs"
ENV_TARGET = "/api/cloud/app-mgmt/v1.0/apps/%s/advanced-config"

# 平台自己会注入的变量，不写进 env（写了会重复）
PLATFORM_INJECTED = {"NAMESPACE", "SERVICE_NAME"}

# itpb 21 个应用统一使用同一地址（2026-10-26 全量核对，无二值）。
# 只在补救场景用：某次 advanced-config 若 env 给漏，平台侧 metadata.env 会被整体替换，
# 该项就永久消失（--restore-vlogs 用来补回）。
VL_URL = "http://victoria-be7vr-internal-svc.itpb.svc:9428"

# 不挂日志盘的应用：不是「日志生产者」或不该共用业务盘
EXCLUDE = {
    "victoria": "它本身就是日志服务器（VictoriaLogs），别自指",
    "web-frontend": "nginx 静态站点，不写 /home/javaapp/app/logs",
    "route-sentinel": "logback 打 stdout，不落这个目录",
    "web-admin": "RuoYi/logback，落盘路径不是这个（待定）",
}


def run(cmd, stdin_data=None, timeout=180):
    p = subprocess.run(cmd, input=stdin_data, capture_output=True, text=True, timeout=timeout)
    return p.returncode, p.stdout, p.stderr


def cloudos(method, uri, body=None):
    args = ["bash", ITP_OPS, "--env", "hongdao", "cloudos", "raw", method, uri]
    payload = None
    if body is not None:
        payload = json.dumps(body, ensure_ascii=False)
        args.append(payload)
    rc, out, err = run(args)
    txt = out[out.find("{"):] if "{" in out else out
    try:
        return json.loads(txt)
    except Exception:
        raise SystemExit(f"CloudOS 返回非 JSON（rc={rc}）:\n{out[:800]}\n{err[:800]}")


def kubectl_deployments():
    rc, out, err = run(["ssh", "hd-linux", f"kubectl -n {NS} get deploy -o json"])
    out = out[out.find("{"):] if "{" in out else out
    d = json.loads(out)
    return {it["metadata"]["name"]: it for it in d["items"]}


def list_apps():
    d = cloudos("GET", "/api/cloud/app-mgmt/v1.0/apps?namespace=%s&limit=200" % NS)
    data = d.get("data")
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        return data.get("list") or data.get("items") or []
    return []


def bump(version):
    parts = str(version).split(".")
    while len(parts) < 4:
        parts.append("0")
    parts[-1] = str(int(parts[-1]) + 1)
    return ".".join(parts)


def clean_deploy(dep):
    """按试点验证过的形态重建 Deployment（去掉 kubectl server-side 字段）。"""
    ps = dep["spec"]["template"]["spec"]

    def pick(d, keys, defaults=None):
        out = {}
        defaults = defaults or {}
        for k in keys:
            if k in d:
                out[k] = d[k]
            elif k in defaults:
                out[k] = defaults[k]
        return out

    containers = []
    for c in ps["containers"]:
        nc = pick(
            c,
            ["args", "command", "env", "image", "imagePullPolicy", "name", "ports",
             "resources", "securityContext", "volumeMounts", "lifecycle"],
            {"args": None, "command": None, "ports": [], "volumeMounts": [],
             "securityContext": {"privileged": True}, "lifecycle": None},
        )
        containers.append(nc)

    pod = pick(
        ps,
        ["containers", "dnsPolicy", "initContainers", "restartPolicy", "schedulerName",
         "securityContext", "serviceAccount", "serviceAccountName",
         "terminationGracePeriodSeconds", "volumes"],
        {"initContainers": [], "volumes": [], "securityContext": {}, "dnsPolicy": "ClusterFirst",
         "restartPolicy": "Always", "schedulerName": "default-scheduler"},
    )
    pod["containers"] = containers

    return {
        "apiVersion": "apps/v1",
        "kind": "Deployment",
        "metadata": {
            "labels": dep["metadata"].get("labels") or {"app": dep["metadata"]["name"]},
            "name": dep["metadata"]["name"],
            "namespace": dep["metadata"]["namespace"],
        },
        "spec": {
            "replicas": dep["spec"]["replicas"],
            "selector": dep["spec"]["selector"],
            "template": {
                "metadata": {"labels": dep["spec"]["template"]["metadata"].get("labels")
                             or {"app": dep["metadata"]["name"]}},
                "spec": pod,
            },
        },
    }


def ensure_mount(deploy_obj, pvc=PVC):
    """给容器 0 挂 PVC；幂等。返回 (vol_added, mount_added)。
    已存在同名 volume 但 claimName 不同时（如从 app-logs 切到 app-logs-v2）就地改指。"""
    ps = deploy_obj["spec"]["template"]["spec"]
    vol_added = mount_added = False
    vols = ps.setdefault("volumes", [])
    hit = [v for v in vols if v.get("name") == VOL_NAME]
    if not hit:
        vols.append({"name": VOL_NAME, "persistentVolumeClaim": {"claimName": pvc}})
        vol_added = True
    else:
        vc = hit[0].setdefault("persistentVolumeClaim", {})
        if vc.get("claimName") != pvc:
            print(f"  claim {vc.get('claimName')} -> {pvc}")
            vc["claimName"] = pvc
            vol_added = True
    c0 = ps["containers"][0]
    mounts = c0.setdefault("volumeMounts", [])
    if not any(m.get("mountPath") == MOUNT_PATH for m in mounts):
        mounts.append({"name": VOL_NAME, "mountPath": MOUNT_PATH})
        mount_added = True
    return vol_added, mount_added


def build_body(app, dep, env_extra=None, pvc=PVC):
    m = app["metadata"]
    env = dict(m.get("env") or {})
    if env_extra:
        env.update(env_extra)
    for k in PLATFORM_INJECTED:
        env.pop(k, None)

    deploy_obj = clean_deploy(dep)
    vol_added, mount_added = ensure_mount(deploy_obj, pvc)

    # 容器 env 与 metadata.env 必须同源同集合（平台按 metadata.env 下发）
    deploy_obj["spec"]["template"]["spec"]["containers"][0]["env"] = [
        {"name": k, "value": v} for k, v in env.items()
    ]

    body = {
        "version_id": m["version_id"],
        "version": bump(m["version"]),
        "service_name": m["service_name"],
        "strategy": "rolling",
        "container_name": m.get("container_name") or dep["metadata"]["name"],
        "container_image": m["container_image"],
        "env": env,
        "pvcResource": {"pv": [], "pvc": [pvc]},
        "affinity": {
            "nodeAffinity": {"preferred": {"in": [], "notIn": []}, "required": {"in": [], "notIn": []}},
            "podAffinity": {"perferPodAffinityRules": [], "requirePodAffinityRules": []},
            "podAntiAffinity": {"perferPodAntiAffinityRules": [], "requirePodAntiAffinityRules": []},
        },
        "resources": [
            {"security": {"capabilities": [], "hostIPC": False, "hostNetwork": False,
                          "hostPID": False, "privileged": False,
                          "readOnlyRootFilesystem": False, "runAsUser": ""}},
            deploy_obj,
        ],
    }
    return body, vol_added, mount_added


def app_uuid(name):
    if re.fullmatch(r"[0-9a-f]{32}", name):
        return name
    for a in list_apps():
        if a.get("name") == name:
            return a["uuid"]
    raise SystemExit(f"未找到应用: {name}")


def verify(name, expect_env, wait=180):
    """下发后复验：等滚动完成（轮询，不靠固定 sleep），再核挂载 / env / VLOGS_URL。"""
    deadline = time.time() + wait
    dep = None
    while True:
        time.sleep(8)
        rc, out, _ = run(["ssh", "hd-linux", f"kubectl -n {NS} get deploy {name} -o json"])
        out = out[out.find("{"):] if "{" in out else out
        dep = json.loads(out)
        st = dep["status"]
        if st.get("replicas") and st.get("readyReplicas") == st.get("replicas") \
                and st.get("updatedReplicas") == st.get("replicas"):
            break
        if time.time() > deadline:
            print(f"  ⚠ 滚动 {wait}s 未收敛，按当前状态判定")
            break
    st = dep["status"]
    ps = dep["spec"]["template"]["spec"]
    c0 = ps["containers"][0]
    env = c0.get("env") or []
    mounts = [x.get("mountPath") for x in (c0.get("volumeMounts") or [])]
    vols = [v.get("name") for v in (ps.get("volumes") or [])]
    vlogs = [e.get("value") for e in env if e["name"] == "VLOGS_URL"]
    ok = (
        st.get("readyReplicas") == st.get("replicas")
        and st.get("replicas", 0) > 0
        and MOUNT_PATH in mounts
        and VOL_NAME in vols
        and len(env) >= expect_env
        and bool(vlogs)
    )
    print(f"  replicas={st.get('replicas')} ready={st.get('readyReplicas')} "
          f"mounts={mounts} volumes={vols} env={len(env)} (期望>={expect_env}) "
          f"VLOGS_URL={'OK' if vlogs else 'MISSING'}")
    return ok, vlogs


def main():
    args = sys.argv[1:]
    yes = "--yes" in args
    fix_env_only = "--fix-env" in args
    dry = "--dry-run" in args or not yes
    # 取应用名：跳过所有 --flag，以及带值 flag（--pvc）后面的那个值
    names = []
    for i, a in enumerate(args):
        if a.startswith("--"):
            continue
        if i > 0 and args[i - 1] == "--pvc":
            continue
        names.append(a)

    deps = kubectl_deployments()

    if "--list" in args:
        print(f"{'应用':<28}{'副本':<6}{'已是目标态':<10}说明")
        for n in sorted(deps):
            d = deps[n]
            c0 = d["spec"]["template"]["spec"]["containers"][0]
            has = MOUNT_PATH in [x.get("mountPath") for x in (c0.get("volumeMounts") or [])]
            note = EXCLUDE.get(n, "")
            print(f"{n:<28}{d['spec']['replicas']:<6}{'✓' if has else '':<10}{note}")
        return

    if not names:
        raise SystemExit("需要应用名。--list 可看清单")

    outdir = tempfile.mkdtemp(prefix="pvc-batch-")
    env_extra = {"VLOGS_URL": VL_URL} if "--restore-vlogs" in args else None
    pvc = PVC
    if "--pvc" in args:
        pvc = args[args.index("--pvc") + 1]
    results = []
    for n in names:
        if n in EXCLUDE and not fix_env_only:
            print(f"\n=== {n} === 跳过：{EXCLUDE[n]}")
            continue
        print(f"\n=== {n} ===")
        uuid = app_uuid(n)
        app = cloudos("GET", f"/api/cloud/app-mgmt/v1.0/apps/{uuid}")["data"]
        if n not in deps:
            print("  ✗ kubectl 里找不到该 Deployment，跳过")
            results.append((n, False))
            continue
        env_now = app["metadata"].get("env") or {}
        expect_env = len(env_now) + 2          # +NAMESPACE +SERVICE_NAME
        body, va, ma = build_body(app, deps[n], env_extra, pvc)
        print(f"  uuid={uuid} version {app['metadata']['version']} -> {body['version']} "
              f"env={len(body['env'])} volume+={va} mount+={ma}")
        path = os.path.join(outdir, f"{n}.json")
        with open(path, "w") as f:
            json.dump(body, f, ensure_ascii=False, indent=2)
        print(f"  payload -> {path}")
        if dry:
            results.append((n, None))
            continue
        r = cloudos("PATCH", ENV_TARGET % uuid, body)
        if not r.get("status"):
            print(f"  ✗ 下发失败: {json.dumps(r, ensure_ascii=False)[:400]}")
            results.append((n, False))
            continue
        print(f"  ✓ 已下发 release_id={((r.get('data') or {}).get('release_id')) if isinstance(r.get('data'), dict) else r.get('data')}")
        ok, _ = verify(n, expect_env)
        results.append((n, ok))

    print("\n=== 汇总 ===")
    for n, ok in results:
        print(f"  {n:<28}{'预演' if ok is None else ('OK' if ok else '✗ 需复核')}")
    print(f"\npayload 目录: {outdir}")
    if dry:
        print("（预演模式，未下发。确认后加 --yes）")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把测试环境 web-admin 的 app-logs-v2 PVC 挂到 /home/javaapp/app/export/。

走 CloudOS PATCH /apps/{uuid}/advanced-config，与 mount-applogs.py 同原理，
只是环境为 test、挂载路径为 export 目录。
"""
import json
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
ITP_OPS = os.path.join(ROOT, "docs/ops/environments/scripts/itp-ops.sh")

APP_NAME = "web-admin"
NS = "itp"
PVC = "app-logs-v2"
MOUNT_PATH = "/home/javaapp/app/export"
VOL_NAME = "app-logs-export"


def run(cmd, stdin_data=None, timeout=180):
    p = subprocess.run(cmd, input=stdin_data, capture_output=True, text=True, timeout=timeout)
    return p.returncode, p.stdout, p.stderr


def cloudos(method, uri, body=None):
    args = ["bash", ITP_OPS, "--env", "test", "cloudos", "raw", method, uri]
    payload = None
    if body is not None:
        payload = json.dumps(body, ensure_ascii=False)
        args.append(payload)
    rc, out, err = run(args)
    txt = out[out.find("{"): ] if "{" in out else out
    try:
        return json.loads(txt)
    except Exception:
        raise SystemExit(f"CloudOS 返回非 JSON（rc={rc}）:\n{out[:800]}\n{err[:800]}")


def kubectl_deployment():
    rc, out, err = run(["ssh", "k8s-master", f"kubectl -n {NS} get deploy {APP_NAME} -o json"])
    out = out[out.find("{"): ] if "{" in out else out
    return json.loads(out)


def clean_deploy(dep):
    """去掉 server-side 字段，保留平台可接受的 Deployment 形态。"""
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


def ensure_mount(deploy_obj):
    """幂等添加 PVC 挂载。"""
    ps = deploy_obj["spec"]["template"]["spec"]
    volumes = ps.setdefault("volumes", [])
    if not any(v.get("name") == VOL_NAME for v in volumes):
        volumes.append({
            "name": VOL_NAME,
            "persistentVolumeClaim": {"claimName": PVC}
        })
        print(f"  添加 volume: {VOL_NAME} -> PVC {PVC}")
    else:
        print(f"  volume {VOL_NAME} 已存在")

    for c in ps["containers"]:
        mounts = c.setdefault("volumeMounts", [])
        if not any(m.get("name") == VOL_NAME for m in mounts):
            mounts.append({"name": VOL_NAME, "mountPath": MOUNT_PATH})
            print(f"  添加 mount: {VOL_NAME} -> {MOUNT_PATH} (container {c['name']})")
        else:
            print(f"  mount {VOL_NAME} 已存在 (container {c['name']})")


def build_body(app_state, deploy_obj):
    """构造 advanced-config PATCH body。"""
    meta = app_state.get("metadata") or {}
    body = {
        "version": app_state.get("version", ""),
        "strategy": meta.get("strategy", "rolling"),
        "container_name": meta.get("container_name", APP_NAME),
        "container_image": meta.get("container_image", ""),
        "env": meta.get("env") or {},
        "resources": [None, deploy_obj],
    }
    return body


def main():
    print(f"== 挂载 {PVC} 到 {APP_NAME}:{MOUNT_PATH}（测试环境）==")

    # 1. 获取应用 UUID 与当前状态
    apps = cloudos("GET", f"/api/cloud/app-mgmt/v1.0/apps?namespace={NS}&limit=200")
    app = None
    for a in (apps.get("data") or []):
        if a.get("name") == APP_NAME:
            app = a
            break
    if not app:
        raise SystemExit(f"未找到应用 {APP_NAME}")
    uuid = app["uuid"]
    print(f"应用 UUID: {uuid}")

    # 2. 获取当前 Deployment 并加挂载
    dep = kubectl_deployment()
    clean = clean_deploy(dep)
    ensure_mount(clean)

    # 3. 构造 body
    body = build_body(app, clean)
    print("\n将提交 advanced-config PATCH，新增挂载点：")
    print(f"  volume: {VOL_NAME} -> PVC {PVC}")
    print(f"  mount:  {VOL_NAME} -> {MOUNT_PATH}")

    if "--yes" not in sys.argv:
        print("\n这是预演，未下发。追加 --yes 执行。")
        return

    # 4. 下发 PATCH
    print("\n下发 PATCH ...")
    resp = cloudos("PATCH", f"/api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config", body)
    print(f"响应: {resp.get('code')} / {resp.get('msg')}")
    print("\n注意：平台会触发滚动重建，约 30-60s 后新 Pod 会带上挂载。")


if __name__ == "__main__":
    main()

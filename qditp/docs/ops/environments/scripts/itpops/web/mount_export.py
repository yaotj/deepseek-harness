"""把测试环境 web-admin 的 app-logs-v2 PVC 挂到 /home/javaapp/app/export/。

走 CloudOS PATCH /apps/{uuid}/advanced-config，与 mount-applogs.py 同原理，
只是环境为 test、挂载路径为 export 目录。
原独立脚本 mount-web-admin-export.py 已收敛为 `itp-ops.sh web mount-export`（原文见 git 历史）。

用法（与旧脚本兼容）：
  itp-ops.sh web mount-export          # 预演：只打印将下发的 PATCH
  itp-ops.sh web mount-export --yes    # 真正下发（平台滚动重建 30-60s）

【与旧版的差异（收编对齐契约）】
  1. 失败统一抛 OpsError（退出码细分：前置 3 / 传输 4 / 执行 5），错误行带统一
     「错误：」前缀（旧为 SystemExit 裸文本）。
  2. 2026-11-06 纯 CloudOS 化：删除 core.remote / kubectl 依赖，Deployment 骨架
     改从 GET /apps/{uuid} 的 metadata.image_metadata.metadata.resources[1] 读取。
     NEVER 再 ssh 跳板机取 k8s Deployment（与 env-set 同一口径）。
  3. CloudOS 调用仍经 itp-ops.sh cloudos raw（子进程），命令面不变。
  4. 仓库根/脚本目录由 execx 推导，被移动位置时报前置错而非算错路径。
"""
from __future__ import annotations

import json
import os
import subprocess
import sys

from ..core import environments as envx, ui
from ..core.runtime import repo_root, scripts_dir
from ..errors import EXIT_EXEC, EXIT_PRECONDITION, EXIT_TRANSPORT, OpsError

ITP_OPS = str(scripts_dir() / "itp-ops.sh")

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
        raise OpsError(
            f"CloudOS 返回非 JSON（rc={rc}）:\n{out[:800]}\n{err[:800]}", EXIT_EXEC
        )


def cloudos_app_detail(uuid):
    """纯 CloudOS 读应用详情（NEVER kubectl / ssh）。"""
    d = cloudos("GET", f"/api/cloud/app-mgmt/v1.0/apps/{uuid}")
    raw = d.get("data") or {}
    return raw.get("data") or raw


def deployment_from_cloudos(app):
    """从应用详情 image_metadata 取当前 Deployment 骨架。"""
    meta = app.get("metadata") or {}
    res = ((meta.get("image_metadata") or {}).get("metadata") or {}).get("resources")
    dep = res[1] if isinstance(res, list) and len(res) > 1 and isinstance(res[1], dict) else None
    if not dep or dep.get("kind") != "Deployment":
        raise OpsError(
            "CloudOS 应用详情无 image_metadata Deployment 骨架 —— 无法安全构造 "
            "advanced-config body（NEVER 回退 kubectl）", EXIT_PRECONDITION)
    return dep


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


CATALOG = {
    "name": "mount-export",
    "summary": "测试环境 web-admin 挂 app-logs-v2 PVC 到 /home/javaapp/app/export（CloudOS advanced-config）",
    "usage": "web mount-export [--yes]",
    "args": [{"name": "--yes", "desc": "真正下发 PATCH；缺省预演"}],
    "writes": True,
    "example": "itp-ops.sh web mount-export --yes",
    "env_note": "固定测试环境（ns itp、纯 CloudOS REST，不依赖跳板机/kubectl）",
}


def main(argv: list[str] | None = None) -> int:
    try:
        return _main(argv)
    except OpsError as exc:
        ui.log_error(f"错误：{exc}")
        return exc.exit_code


def _main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    yes = "--yes" in argv

    envx.init_env("test")
    envx.banner(f"挂载 {PVC} 到 {APP_NAME}:{MOUNT_PATH}")
    print(f"== 挂载 {PVC} 到 {APP_NAME}:{MOUNT_PATH}（测试环境）==")

    # 1. 获取应用 UUID 与当前状态
    apps = cloudos("GET", f"/api/cloud/app-mgmt/v1.0/apps?namespace={NS}&limit=200")
    app = None
    for a in (apps.get("data") or []):
        if a.get("name") == APP_NAME:
            app = a
            break
    if not app:
        raise OpsError(f"未找到应用 {APP_NAME}", EXIT_EXEC)
    uuid = app["uuid"]
    print(f"应用 UUID: {uuid}")

    # 2. 从 CloudOS 应用详情取 Deployment 骨架并加挂载（NEVER kubectl）
    detail = cloudos_app_detail(uuid)
    dep = deployment_from_cloudos(detail)
    clean = clean_deploy(dep)
    ensure_mount(clean)

    # 3. 构造 body
    body = build_body(detail, clean)
    print("\n将提交 advanced-config PATCH，新增挂载点：")
    print(f"  volume: {VOL_NAME} -> PVC {PVC}")
    print(f"  mount:  {VOL_NAME} -> {MOUNT_PATH}")

    if not yes:
        print("\n这是预演，未下发。追加 --yes 执行。")
        ui.result({"app": APP_NAME, "uuid": uuid, "mount_path": MOUNT_PATH, "dry_run": True})
        return 0

    # 4. 下发 PATCH
    print("\n下发 PATCH ...")
    resp = cloudos("PATCH", f"/api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config", body)
    print(f"响应: {resp.get('code')} / {resp.get('msg')}")
    print("\n注意：平台会触发滚动重建，约 30-60s 后新 Pod 会带上挂载。")
    ui.result({"app": APP_NAME, "uuid": uuid, "mount_path": MOUNT_PATH,
               "response": {"code": resp.get("code"), "msg": resp.get("msg")}})
    return 0


if __name__ == "__main__":
    # 调试别名：转发唯一入口（正式入口 `itp-ops.sh web mount-export`）。
    from ..cli.main import main as _cli_main

    sys.exit(_cli_main(["web", "mount-export", *sys.argv[1:]]))

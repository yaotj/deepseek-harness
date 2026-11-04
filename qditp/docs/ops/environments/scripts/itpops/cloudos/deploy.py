"""cloudos「升级」业务段 —— 现查 k8s Deployment、组装 advanced-config、切镜像前后校验。

安全判据（NEVER 同 tag 覆盖 / 跨项目拒绝 / version_id uuid 形态）已有业务级单测锁定。"""

from __future__ import annotations

import json
import re
import time
from ..core import environments as envx, ui
from ..core.ui import context
from ..errors import (
    EXIT_EXEC,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

from .common import APP_API, _err, _need, _req, resolve_app_uuid

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


def _k8s_deploy_for_cloudos(app_name: str) -> dict | None:
    """从 k8s-master 取当前 Deployment 并按 CloudOS advanced-config 可接受形态清理。

    某些通过 kubectl 直接创建、后被 CloudOS 发现的应用（如 route-sentinel），
    PATCH advanced-config 必须携带 resources，否则平台内部校验报 5000010。
    正常应用亦可接受该字段，故部署时统一带上当前 k8s Deployment 快照。
    取失败仅返回 None，由调用方决定是否继续（不阻塞常规发布）。
    """
    try:
        import subprocess
        ns = envx.current().k8s_ns
        cmd = ["ssh", "-o", "ConnectTimeout=15", "k8s-master",
               f"kubectl -n {ns} get deploy {app_name} -o json"]
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if r.returncode != 0:
            ui.log_warn(f"取当前 Deployment 失败：{r.stderr.strip()[:200]}")
            return None
        dep = json.loads(r.stdout[r.stdout.find("{"):])
    except Exception as exc:
        ui.log_warn(f"取当前 Deployment 异常：{exc}")
        return None

    try:
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
        for c in ps.get("containers") or []:
            containers.append(pick(
                c,
                ["args", "command", "env", "image", "imagePullPolicy", "name", "ports",
                 "resources", "securityContext", "volumeMounts", "lifecycle",
                 "livenessProbe", "readinessProbe", "startupProbe"],
                {"ports": [], "volumeMounts": [], "lifecycle": None},
            ))
        pod = pick(
            ps,
            ["containers", "dnsPolicy", "initContainers", "restartPolicy", "schedulerName",
             "securityContext", "serviceAccount", "serviceAccountName",
             "terminationGracePeriodSeconds", "volumes", "imagePullSecrets", "nodeSelector",
             "affinity", "tolerations", "hostNetwork"],
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
    except Exception as exc:
        ui.log_warn(f"清理 Deployment 失败：{exc}")
        return None


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
    # 对 kubectl 直接创建、后被 CloudOS 发现的应用，advanced-config PATCH 必须带 resources，
    # 否则平台内部校验会报 application.5000010（route-sentinel 实测）。
    deploy_obj = _k8s_deploy_for_cloudos(app.get("name") or key)
    if deploy_obj is not None:
        body["resources"] = [None, deploy_obj]
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
    ui.out("  resources   %s" % ("携带当前 k8s Deployment 快照" if "resources" in body else "未携带（取 Deployment 失败）"))
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

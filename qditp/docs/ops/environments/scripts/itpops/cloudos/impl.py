"""cloudos 域「编排层」—— 只放 CATALOG 与 main() 分发。

实现按业务段分布在同包 common / apps / scaling / deploy / images；
命令层 `itpops/commands/cloudos.py` 从本模块重导出 CATALOG + main。
拆分是纯搬移：函数体逐字未改，help / stdout / catalog 与拆分前一致。"""

from __future__ import annotations

import os
import sys
from . import auth
from . import pkg as pkg_mod
from ..core import environments as envx, ui
from ..core.runtime import require_yes, repo_root
from ..errors import (
    EXIT_EXEC,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

from ..cli.suggest import append_hint
from .apps import _app_action, _app_detail, _app_env, _app_logs, _app_pods, _app_service, _apps, _auth_debug, _health, _logout, _svc_create
from .common import APP_API, CONTAINER_API_PREFIX, _help_text, _need, _req_json
from .deploy import _app_deploy, env_set
from .images import _cmd_build, _cmd_plan, _cmd_probe, _harbor_project_default, _image_parse_args, _pkg_upload_cmd
from .scaling import _app_scale, _app_spec_set, _app_spec_show, _scale_all, _spec_all

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
        {"name": "env-set",
         "usage": "cloudos env-set <名字|uuid> KEY=VALUE [KEY=VALUE...] [--delete KEY] [--app-version <v>]",
         "summary": "改应用 env（advanced-config 全量替换）；内置 IT-020 隐藏契约：container_image 必填 / version 第 4 段递增 / 只回传 uuid 形态 version_id / 带当前 k8s Deployment 快照。不带 --yes 是预演，快照取不到时拒绝执行",
         "args": [{"name": "名字|uuid", "required": True},
                  {"name": "KEY=VALUE", "required": True},
                  {"name": "--delete KEY", "required": False},
                  {"name": "--app-version <v>", "required": False}],
         "writes": True,
         "example": "itp-ops.sh --env test cloudos env-set face-pay-server max_memory=4000 reserved_memory=1000 max_direct_memory=100 --yes",
         "stdout_human": "===== 应用 env 变更预演 … env 变更 新增/修改 3 项，删除 0 项，共 25 项全量回传 …",
         "json_data": {"dry_run": True, "plan": {"new_ver": "1.0.91.1", "env_n": 25}}},
    ],
}


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
    if sub == "env-set":
        return env_set(rest)
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
    names = [s["name"] for s in CATALOG["subcommands"]] + ["help"]
    raise OpsError(append_hint(f"未知命令：{sub}", sub, names), EXIT_USAGE)

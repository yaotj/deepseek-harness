"""cloudos 公共层 —— API 常量 / 输出原语 / HTTP 请求封装 / uuid 解析 / 域帮助文本。

依赖图的叶子：NEVER 反向依赖 apps / scaling / deploy / images / impl。

【2.5.0 之前的模块说明（原 impl.py docstring，历史坑位与 bash 差异，保留备查）】
itpops · cloudos 域「实现层」—— 新华三 CloudOS（E7501 PaaS）平台运维。

2026-11-04 从 `itpops/commands/cloudos.py` 整块下沉至此（1776 行，30 个子命令）：
命令层 `commands/cloudos.py` 只保留 CATALOG + main 的重导出，符合「commands/ 只做编排、
实现放特性包」的分层纪律。搬迁是纯文件移动，命令面与 stdout 逐字未变；
本文件内所有 `from ..` 相对导入在新位置仍指向 itpops/，故一行未改。

移植自 commands/cloudos.sh（bash，1549 行）+ lib/cloudos-{auth,http,image,pkg}.sh。
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
  · 旧端点 /images/build 红岛/测试实测 404/img.5（IT-024）。`cloudos image build`
    已于 2026-11-06 删除；出镜像统一走 itp-ops.sh buildx。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 失败退出码细分：bash 一律 exit 1（或 health/logs 的 return 1/2）；
     Python 按契约抛 OpsError——用法错 2、前置 3（health 不通过、缺 jar/Dockerfile、
     缺 IMG_TYPE_UUID）、传输 4（ssh/HTTP 连不上）、执行/业务拒绝 5、缺 --yes 6。
  2. 缺 --yes 的文案走 execx.require_yes 统一格式「这是写操作：…。确认无误请加 --yes」
     （svc create 与 bash 字节一致；act/scale/spec-set 在 bash 用自定义
     die 文案，措辞略有出入，动作信息一致）。
  3. 写操作门新增：pkg-upload / raw 非 GET 在 bash 无 --yes 门，
     按「写操作一律 require_yes」补上（缺 --yes 本地即 exit 6）。
  4. （历史）旧 image build 曾把 --yes 门提到 image_prepare 之前；该命令已删除，
     此差异随命令一并作废，保留编号以免打乱后续条目引用。
  5. app_logs 无实例时 bash 拿空 ns/pod 继续拼 URL、最终以「HTTP 失败」收场；
     Python 直接报「该应用没有实例」终止（少一次无意义请求）。
  6. 环境横幅按契约用 envx.banner（stderr，[环境] 行）；bash 的
     `== 环境：…（… / 账号 …）==` 行同时保留在 stderr（stdout 不受影响）。
  7. 未知命令：bash 打印 cloudos_usage 到 stdout 后 return 1；Python 同样打
     usage 到 stdout，但退出码按契约为 2（USAGE）。
  8. unzip CLI 校验 fat jar 改为 zipfile 模块（纯标准库），判定与报错文案一致。
"""

from __future__ import annotations

import os
import re
import sys
from . import http as http_mod
from ..core import environments as envx, ui
from ..errors import (
    EXIT_EXEC,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

CONTAINER_API_PREFIX = "/api/cloud/container/v1.0"


APP_API = "/api/cloud/app-mgmt/v1.0"


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


def _req(method: str, uri: str, body: str = "") -> object:
    """cloudos_request 等价：成功返回解析结果，失败抛 OpsError（错误形态见 http.py）。"""
    return http_mod.request_json(method, uri, body)


def _req_json(method: str, uri: str, body: str = "") -> None:
    """cloudos_request + 打印美化 JSON（raw/act 等直出响应的命令用）。"""
    d = _req(method, uri, body)
    ui.out(http_mod.pretty(d))
    ui.result(d)


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

镜像契约记录（旧端点 /images/build 已废弃，红岛/测试均不通，见 IT-024）
  image probe                    只读探测（基址/镜像分类/已有 tag，仍可用于取 imgType）
  image plan  <模块> <版本>       dry-run，仅打印旧端点参数作契约记录
        （`image build` 已于 2026-11-06 删除；实际出镜像见下方 buildx）

镜像构建（实际可用，两个环境同一套）
  itp-ops.sh buildx <模块名> <版本> [--env hongdao|test]
       [--jar <绝对路径>] [--dockerfile <共享模板>] [--check]
       平台 x86 节点 buildx 出镜像并推 Harbor；Harbor 项目与基础镜像按 --env 自动取
       （红岛 itpb/jdk21:1.0.0、测试 itp/jdk:25）。--check 为只读模式。

测试环境附加（--env test；前缀未经实测，见域头注）
  appgroups                      应用组列表
  pod-list [ns] / pod-get <pod>                       容器云 pod（只读；前缀待校准）
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

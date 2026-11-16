"""cloudos「镜像构建」业务段 —— 旧端点 /images/build 的 probe / plan / build 与软件中心上传。

⚠ 两套环境实测该端点 404 / img.5，正式发版 MUST 走 `itp-ops.sh buildx`（IT-024）；
本段保留只为取 imgType 与历史 stdout 兼容。"""

from __future__ import annotations

import json
import os
import zipfile
from . import auth
from . import http as http_mod
from . import image as image_mod
from . import pkg as pkg_mod
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

from .common import _err, _info

def _harbor_project_default() -> str:
    """bash 历史错位的 HARBOR_PROJECT 解析（见模块 docstring）：
    显式 --env → profile 值（hongdao=itpb / test=itp）；否则环境变量或源码期默认 itp。"""
    if context().env_explicit:
        return "itpb" if envx.current().name == "hongdao" else "itp"
    return os.environ.get("HARBOR_PROJECT") or "itp"


def _image_parse_args(argv: list[str]) -> tuple[str, str, dict]:
    """image plan/build 共用参数解析：flag 与位置参数可任意顺序，位置参数取前两个。"""
    state = {"jar": None, "project": None, "img_type": None, "dockerfile": None}
    positional: list[str] = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in ("--jar", "--project", "--img-type", "--dockerfile"):
            if i + 1 >= len(argv):
                raise OpsError(f"{a} 缺值", EXIT_USAGE)
            key = {"--jar": "jar", "--project": "project",
                   "--img-type": "img_type", "--dockerfile": "dockerfile"}[a]
            state[key] = argv[i + 1]
            i += 2
        elif a == "--keep-pkg":
            i += 1  # 兼容旧入口：历史上就是 no-op
        elif a == "--":
            positional.extend(argv[i + 1:])
            break
        elif a.startswith("--"):
            raise OpsError(f"未知选项 {a}", EXIT_USAGE)
        else:
            positional.append(a)
            i += 1
    mod = positional[0] if positional else ""
    ver = positional[1] if len(positional) > 1 else ""
    return mod, ver, state


def _image_prepare(mod: str, ver: str, state: dict) -> dict:
    root = repo_root()
    dockerfile = state["dockerfile"] or os.environ.get("DOCKERFILE") or str(root / "docker" / "Dockerfile")
    jar = state["jar"] or os.environ.get("JAR_OVERRIDE") or str(root / mod / "target" / f"{mod}-{ver}.jar")

    if not os.path.isfile(jar):
        raise OpsError(
            "找不到 jar：%s\n   先构建：cd %s && mise exec -- mvn -pl %s -am clean package -DskipTests"
            % (jar, root, mod),
            EXIT_PRECONDITION,
        )
    if not os.path.isfile(dockerfile):
        raise OpsError("找不到 Dockerfile：%s" % dockerfile, EXIT_PRECONDITION)

    # fat jar 校验：平台用 java -jar 拉起，必须是 spring-boot repackage 产物
    try:
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
    except (OSError, zipfile.BadZipFile) as exc:
        raise OpsError("%s 不是合法的 zip（unzip 读取失败）" % jar, EXIT_PRECONDITION) from exc
    if not any("BOOT-INF/classes" in n for n in names):
        raise OpsError(
            "%s 不是 spring-boot fat jar（无 BOOT-INF/classes），平台无法 java -jar 启动" % jar,
            EXIT_PRECONDITION,
        )

    return {
        "jar": jar,
        "dockerfile": dockerfile,
        "size": os.path.getsize(jar),
        "file_flag": pkg_mod.pkg_gen_id(),
    }


def _cmd_probe(harbor_project: str) -> int:
    c = auth.cfg()
    _info("== CloudOS 镜像构建能力探测（只读，不发写请求）==")
    _info("")
    _info("-- 0. 连通性预检（镜像服务可能在 11000，Web 网关在 CLOUDOS_PORT 或 443）")
    web_port = os.environ.get("CLOUDOS_PORT") or "443"
    check_ports = [web_port, "11000"] if web_port != "11000" else ["11000"]
    if not http_mod.check_ports(c.host, check_ports):
        raise OpsError(
            "两个端口都不通 —— en9 是否 active？\n"
            "\n"
            "   本机实测（2026-10-19 记录）：\n"
            "     USB 网卡配到 K8s 控制区 172.120.2.x  → 才能通云管台\n"
            "     配到出口区 / 走办公网 en0           → 只有 ICMP 通，TCP 全被拦\n"
            "   查网卡：ifconfig en9 | grep inet",
            EXIT_PRECONDITION,
        )

    _info("")
    _info("-- 1. 登录（走 %s）" % c.base_url)
    try:
        t = auth.get_token("")
    except OpsError:
        raise OpsError("登录失败（原因见上）", EXIT_EXEC) from None
    if not t:
        raise OpsError("登录未返回 token", EXIT_EXEC)
    _info("   ✓ token 长度 %d" % len(t))

    _info("")
    _info("-- 2. 镜像服务基址（前缀固定 %s）" % image_mod.IMG_API_PREFIX)
    best = ""
    for base in (c.base_url, "http://%s:11000" % c.host, "https://%s:11000" % c.host):
        code, _body = image_mod._img_http("GET", image_mod.IMG_API_PREFIX + "/types", target_base=base)
        if code in ("401", "403"):
            _info("   %s  GET %s%s/types  ← 路由在" % (code, base, image_mod.IMG_API_PREFIX))
            best = base
        elif code == "404":
            _info("   %s  GET %s%s/types  ← 无路由" % (code, base, image_mod.IMG_API_PREFIX))
        else:
            _info("   %s  GET %s%s/types" % (code, base, image_mod.IMG_API_PREFIX))
    if best:
        _info("   ⇒ 用 IMG_BASE_URL=%s 跑后续命令" % best)
    else:
        _info("   ⇒ 都没路由：换探测路径，或确认平台版本是否含容器云模块")

    probe_base = best or os.environ.get("IMG_BASE_URL") or ""

    _info("")
    _info("-- 3. 镜像分类 UUID（构建必填 imgType）")
    types = image_mod.image_types(target_base=probe_base)
    if types == "NETWORK_UNREACHABLE" or not types:
        _info("   取不到（网络或路由问题）")
    else:
        for line in types.splitlines():
            f = (line.split("\t") + ["", ""])[:2]
            _info("   uuid=%s  name=%s" % (f[0], f[1]))

    _info("")
    _info("-- 4. 已有镜像（确认 tag 占用 / 取回滚点）")
    vers = image_mod.image_versions(project=harbor_project, target_base=probe_base)
    if vers == "NETWORK_UNREACHABLE" or not vers:
        _info("   取不到（网络或路由问题）")
    else:
        for line in vers.splitlines():
            f = (line.split("\t") + ["", "", ""])[:3]
            _info("   %-28s tagCount=%s  size=%sMB" % (f[0], f[1], f[2]))

    _info("")
    _info("================ fileFlag 语义怎么验（决定本命令能否用）================")
    _info("文档从未定义 fileFlag，也没有一处提到 Dockerfile 能否 COPY 外部文件。")
    _info("最快判定法：跑一次最小构建，看返回 data.build[] 里 docker 原话：")
    _info("")
    _info("  情况 1（可用）：日志出现 COPY/ADD 且有 context 大小")
    _info("                  ⇒ fileFlag 就是构建上下文文件，本命令成立。")
    _info("  情况 2（不成立）：报 COPY failed: file not found，或日志只有 FROM 一行")
    _info("                  ⇒ fileFlag 只是任务标识，镜像仓库装不进 jar。")
    _info("                     转而用软件中心的 jar 类型部署（平台自带 JDK 镜像，x86）。")
    _info("")
    _info("最小构建的 Dockerfile 只写两行：")
    _info("  FROM %s/jdk:21" % harbor_project)
    _info("  COPY app.jar /app.jar")
    ui.result({"host": c.host, "img_base_url_best": best, "harbor_project": harbor_project})
    return 0


def _cmd_plan(mod: str, ver: str, state: dict, harbor_project: str) -> int:
    p = _image_prepare(mod, ver, state)
    img_type = state["img_type"] or os.environ.get("IMG_TYPE_UUID") or ""
    img_base = os.environ.get("IMG_BASE_URL") or auth.cfg().base_url

    _info("================ 将要执行（dry-run，未发任何写请求）================")
    _info("服务        : %s" % mod)
    _info("版本        : %s" % ver)
    _info("jar         : %s  (%s bytes)" % (p["jar"], p["size"]))
    _info("Dockerfile  : %s" % p["dockerfile"])
    _info("fileFlag    : %s   ← 推断：客户端预生成的文件标识（未验证）" % p["file_flag"])
    _info("镜像        : %s/%s:%s" % (harbor_project, mod, ver))
    _info("构建接口    : POST %s%s/images/build" % (img_base, image_mod.IMG_API_PREFIX))
    _info("")
    _info("-- 上传用字段（POST /api/cloud/deploy/v1.0/packages, multipart）")
    _info("   id=%s  pkgType=jar  name=%s  version=%s" % (p["file_flag"], mod, ver))
    _info("   file=@%s   filename 固定 app.jar（对齐 COPY app.jar）" % p["jar"])
    _info("")
    _info("-- 构建用 body")
    with open(p["dockerfile"], encoding="utf-8") as f:
        dtext = f.read()
    _info("   dockerfile = %s…" % dtext[:120].rstrip("\n"))
    _info("   imgName    = %s" % mod)
    _info("   version    = %s" % ver)
    _info("   imgType    = %s" % (img_type or "<probe 取得>"))
    _info("   type       = private")
    _info("   project    = %s" % harbor_project)
    _info("   fileFlag   = %s" % p["file_flag"])
    _info("")
    _info("-- 前置检查（MUST 人工确认）")
    _info("   [ ] Harbor 里 %s 已有可回滚的上一个 tag" % mod)
    _info("   [ ] 目标 tag %s 未被占用（部署纪律：版本末位 +1，NEVER 同 tag 覆盖）" % ver)
    _info("   [ ] 当前 pod 状态允许滚更（itp-ops.sh cloudos pods %s）" % mod)
    _info("   [ ] IMG_TYPE_UUID 已从 probe 获取")
    _info("")
    _info("-- 架构（ARM 开发机的关键约束）")
    _info("   本机只出 jar（字节码与架构无关），镜像由平台 x86 构建节点打。")
    _info("   但 %s/images/build 的 body **没有 architecture 参数**" % image_mod.IMG_API_PREFIX)
    _info("   （持续交付 BUILD_IMAGE 任务才有），镜像架构由构建节点决定、不可指定。")
    _info("   构建完 MUST 核对产物 arch：")
    _info("     docker manifest inspect %s/%s:%s | grep architecture" % (harbor_project, mod, ver))
    _info("   期望 amd64。拿到 arm64 说明构建节点是 ARM，x86 业务节点拉不起来。")
    _info("")
    _info("⚠ /images/build 是废弃端点（IT-024，红岛实测 404/img.5），本页仅作契约记录。")
    _info("实际出镜像：itp-ops.sh buildx %s %s --env <hongdao|test> --jar <jar> --dockerfile <共享模板>"
          % (mod, ver))
    ui.result({"module": mod, "version": ver, "jar": p["jar"], "jar_size": p["size"],
               "dockerfile": p["dockerfile"], "file_flag": p["file_flag"],
               "img_type": img_type, "harbor_project": harbor_project, "dry_run": True})
    return 0


def _pkg_upload_cmd(argv: list[str]) -> int:
    if not argv or not argv[0]:
        raise OpsError("需要文件路径", EXIT_USAGE)
    file = argv[0]
    name = argv[1] if len(argv) > 1 and argv[1] else os.path.basename(file)
    ver = argv[2] if len(argv) > 2 and argv[2] else "v1"
    require_yes(f"pkg-upload {file}")
    if not os.path.isfile(file):
        raise OpsError("文件不存在：%s" % file, EXIT_PRECONDITION)
    # id 客户端预生成（上传接口不回 ID；旧测试脚本用 name-version 拼，格式不符平台短 ID）
    id_ = pkg_mod.pkg_gen_id()
    if not pkg_mod.pkg_upload(file, name, ver, id_):
        raise OpsError("上传失败", EXIT_EXEC)
    _info("   ✓ 已上传（id=%s；回查：itp-ops.sh cloudos pkg-list，按 name+version 找）" % id_)
    ui.result({"id": id_, "name": name, "version": ver, "file": file})
    return 0

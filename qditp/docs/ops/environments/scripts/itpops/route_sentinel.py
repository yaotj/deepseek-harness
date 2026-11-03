r"""itpops · route-sentinel —— route-sentinel 1.0.1 红岛 itpb 首次部署。

移植自 itp-ops.sh sentinel（bash）。独立入口（不经 itpops cli
分发）：`itp-ops.sh sentinel …`；薄壳 itp-ops.sh sentinel
转调本模块。流程（沿自 bash 头注，2026-10-26 用户授权执行）：
  1/4 清理软件中心残留（上一轮失败的 upload）
  2/4 上传 jar 到软件中心（fileFlag 机制）
  3/4 提交 Docker 构建（CloudOS /images/build；失败兜底 3b/4 Harbor v2 direct push）
  4/4 检查并部署 Deployment + Service
  另：DB 侧 IT-021 未解决（GATEWAY_ROUTE_CONFIG 缺失），网关启动降级空路由。

【坑/巧思（保留原因）】
  · CloudOS /images/build 用的 imgType 是**现查** hcr /types 里 name 以
    Default/default 开头的 uuid（awk `^[Dd]efault`），不是写死——平台分类可变。
  · 构建响应的 digest 在 data.push[].status 里（"digest:" 后第一个词），且循环
    **不 break**（bash 原行为：最后一个含 digest 的 push 条目胜出）。
  · 镜像引用 os-harbor-svc.default.svc.cloudos:443/<harbor项目>/route-sentinel:1.0.1
    （443 是 K8s 拉取端口）；Harbor 项目取环境 profile（红岛 itpb）。
  · 二次确认口径（AGENTS.md §5.C 生产红线）：本脚本明确 --yes——bash 版只在头注
    声明口径、运行时未校验；Python 按该口径落实为 require_yes 门（见差异 1）。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 确认门落实：bash 头注写明「本脚本已明确 --yes，且本次调用带 --yes，视为已获
     用户授权」但参数解析根本不校验；Python 按该等价机制在任何写动作前
     require_yes（缺 --yes → exit 6，本地即拒，不碰远端）。
  2. 失败退出码细分（bash 一律 die→1）：用法 2、缺 --yes 6、缺 jar/Dockerfile 3、
     传输 4（连不上平台/跳板）、执行 5（上传/构建/取 imgType/远端脚本失败）。
  3. bash 三处 shell 缺陷按脚本**意图**修复（原行为即坏，无法零差异）：
     a) 清理残留循环 `for pair in $ids` + `cut -d' ' -f2`：shell 分词把
        "pkgId verId" 拆成单词后 cut 对无分隔符行返回整串 → 每次 DELETE 的
        pkgId/verId 同为单词（URL 错配，实际删不掉且日志错乱）。Python 按行
        正确配对 pkgId/verId，删除按注释意图真正生效。
     b) Harbor 兜底 heredoc 用了引号 `<<'DOCKER_SCRIPT'`，远端 ${VER} 展开为空 →
        jar/tag 全部错乱；且 podman build 的上下文是远端 $HOME 而 jar 在 /tmp
        （COPY 必失败）；digest 又在**本地**跑 podman inspect（本地无 podman）。
        Python：显式注入 VER、build context=/tmp、digest 从远端 inspect 输出取。
     c) 部署 heredoc 未加引号却含 ${DEPLOY}/${SVC}/${NS}（本地未定义，set -u）→
        ssh 根本不执行（rc=127 继续）；总结的 echo ${DEPLOY} 同样 unbound →
        脚本死在倒数第 3 行、退出 127、最后两行回滚提示丢失。Python 显式注入
        这些值，部署与总结完整执行（stdout 多出最后两行回滚命令，exit 0）。
  4. -h/--help 新增（bash 无 help；--help 会被当 jar-path 报「jar 不存在」）。
  5. 选项 --env/--yes 为新增（bash 位置参数后的任何参数被静默忽略）。不带 --env
     时环境值与 bash 硬编码字面一致（红岛：hd-linux / itpb / 172.120.110.25 DSN）。
  6. 环境横幅：bash 无任何环境提示；按契约铁律加 envx.banner（stderr [环境] 行）。
  7. 登录失败即报（bash 的 `cloudos_get_token > /dev/null` 失败被忽略，一路带到
     上传才以「上传失败」收场）；顶层 `local` 误用的 shell 报错噪声不复刻。
  8. RSA 加密/HTTP 纯标准库（复用本包 buildx 模块的 _CloudOS/_http 自包含实现，
     不 import 并行开发中的 itpops/cloudos）；上传 HTTP 失败文案沿用 bash 原文。
"""
from __future__ import annotations

import json
import os
import random
import string
import subprocess
import sys
from pathlib import Path

from .buildx import (
    _CloudOS,
    _die_msg,
    _head_c,
    _http_token,
)
from .core import envx, ui
from .core.execx import require_yes
from .core.remote import remote_exec, remote_quote_args
from .errors import (
    EXIT_EXEC,
    EXIT_OK,
    EXIT_PRECONDITION,
    EXIT_TRANSPORT,
    EXIT_USAGE,
    OpsError,
)

_CMD_NAME = "itp-ops.sh sentinel"

MOD = "route-sentinel"
VER = "1.0.1"
IMG_NAME = "route-sentinel"
IMG_TAG = VER
DEPLOY = "route-sentinel"
SVC = "route-sentinel-svc"
SCM_HOST = "os-harbor-svc.default.svc.cloudos"  # Harbor（443 拉取 / 11443 推送）
BASE_IMAGE_HONGDAO = "os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0"
BASE_IMAGE_TEST = "os-harbor-svc.default.svc.cloudos:443/itp/jdk:25"

_USAGE = f"用法: {_CMD_NAME} <jar-path> <dockerfile-path> [--env hongdao|test] [--yes]"

CATALOG = {
    "name": "sentinel",
    "summary": "route-sentinel 1.0.1 首次部署（清理软件中心残留→上传 jar→镜像构建→部署 Deployment/Service）",
    "default_env": "hongdao",
    "subcommands": [],
    "usage": "sentinel <jar-path> <dockerfile-path> [--env hongdao|test] [--yes]",
    "args": [
        {"name": "jar-path", "required": True, "summary": "route-sentinel fat jar 路径"},
        {"name": "dockerfile-path", "required": True, "summary": "Dockerfile 路径（原样上传，不替换 FROM）"},
        {"name": "--env", "required": False, "summary": "hongdao|test，默认 hongdao（原脚本硬编码红岛，此为新增）"},
        {"name": "--yes", "required": False, "summary": "确认写操作（生产部署红线口径，缺它本地即拒）"},
    ],
    "writes": True,
    "example": "itp-ops.sh sentinel route-sentinel/target/route-sentinel-1.0.1.jar route-sentinel/Dockerfile --yes",
    "env_note": "默认 hongdao（红岛 hd-linux / itpb）；--env 会连带切换跳板与 Harbor 项目",
}


# ---------------------------------------------------------------- 输出小工具
def _err(text: str) -> None:
    """bash `printf … >&2` 等价。"""
    print(text, file=sys.stderr)


def _info(text: str) -> None:
    """bash log_info 等价 —— 旧 core.sh 的 log_info 落 **stdout**（TTY 绿 [INFO]）。"""
    if sys.stdout.isatty():
        print(f"\033[0;32m[INFO]\033[0m {text}")
    else:
        print(f"[INFO] {text}")


def _warn(text: str) -> None:
    """bash log_warn 等价（stderr，黄 [WARN]）。"""
    prefix = "\033[1;33m[WARN]\033[0m" if sys.stderr.isatty() else "[WARN]"
    print(f"{prefix} {text}", file=sys.stderr)


def _pkg_gen_id() -> str:
    """pkg_gen_id 等价：10 位短 ID（与平台应用包版本 ID 同格式）。"""
    alphabet = string.ascii_letters + string.digits
    return "".join(random.choice(alphabet) for _ in range(10))


def _multipart_form(
    fields: list[tuple[str, str]], files: list[tuple[str, str, bytes]]
) -> tuple[bytes, str]:
    """拼 multipart/form-data：普通字段 + 文件（curl -F 同形）。"""
    boundary = "----itpops" + "".join(random.choice(string.hexdigits.lower()) for _ in range(16))
    parts: list[bytes] = []
    for name, value in fields:
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'
            f"{value}\r\n".encode()
        )
    for field, filename, data in files:
        parts.append(
            (
                f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; '
                f'filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n'
            ).encode()
            + data
            + b"\r\n"
        )
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), boundary


# ---------------------------------------------------------------- 远端脚本
def _ssh_script(host: str, script: str, what: str) -> subprocess.CompletedProcess:
    """ssh <host> bash -s 等价（脚本经 bash -c 喂入，输出透传到本地 stdout/stderr）。

    bash 版对 ssh rc 不检查（失败仍打「✓ 完成」）；这里按意图失败即报：
    ssh exit 255 → 传输 4；远端非 0 → 执行 5。
    """
    cmd = remote_quote_args(["bash", "-c", script])
    try:
        r = remote_exec(cmd)
    except OSError as exc:
        raise OpsError(f"ssh 执行失败：{exc}", EXIT_TRANSPORT) from exc
    if r.stdout:
        print(r.stdout, end="")
    if r.stderr:
        print(r.stderr, end="", file=sys.stderr)
    if r.returncode == 255:
        raise OpsError(f"跳板机不可达：{host}（ssh exit 255）", EXIT_TRANSPORT)
    if r.returncode != 0:
        raise OpsError(f"远端脚本失败（{what}）：exit {r.returncode}", EXIT_EXEC)
    return r


def _docker_push_script(jar_name: str, base_image: str, harbor_repo: str, ver: str) -> str:
    """3b 兜底的远端 podman 构建+推送脚本。

    bash 原版用引号 heredoc（${VER} 远端展开为空）+ build context=远端 $HOME
    （COPY 找不到 jar）——两者均按意图修复（见模块 docstring 差异 3b）。
    """
    target = f"{SCM_HOST}:11443/{harbor_repo}:{ver}"
    target443 = f"{SCM_HOST}:443/{harbor_repo}:{ver}"
    return f"""\
set -e
JAR="/tmp/{jar_name}"
echo "[podman] 从远端获取 jar..."
ssh k8s-master "cp /home/java/{jar_name} ${{JAR}} 2>/dev/null" \\
    || scp -o ConnectTimeout=10 k8s-master:/home/java/{jar_name} "${{JAR}}" 2>/dev/null \\
    || {{ echo "[podman] 失败：无法获取 jar，请确保它在 k8s-master:/home/java/"; exit 1; }}
echo "[podman] 基础镜像 {base_image} 已本地存在（之前 pull 过），开始构建..."
cd /tmp
podman build \\
    --build-arg JAR_NAME="{jar_name}" \\
    -t "{target}" \\
    - <<'EOF'
FROM {base_image}
WORKDIR /app
COPY {jar_name} app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
EOF
echo "[podman] 打 tag（额外确保 443 端口的引用）..."
podman tag "{target}" "{target443}"
echo "[podman] push to 443 (K8s 用 443 拉)..."
podman push --tls-verify=false "{target443}"
echo "[podman] digest=$(podman inspect {target} --format '{{{{.Digest}}}}' 2>/dev/null | head -c 64)"
"""


def _deploy_script(ns: str, img_full: str, db_url: str) -> str:
    """4/4 部署远端脚本（Deployment + Service）。

    bash 原版未加引号的 heredoc 里 ${DEPLOY}/${SVC}/${NS} 是本地未定义变量
    （set -u → ssh 根本不执行）——此处按意图显式注入值（见模块 docstring 差异 3c）。
    """
    return f"""\
NS={ns}
DEPLOY={DEPLOY}
IMG="{img_full}"
if ! kubectl get deploy {DEPLOY} -n {ns} >/dev/null 2>&1; then
    echo "[deploy] Deployment 不存在，创建..."
    kubectl create -n {ns} deployment {DEPLOY} \\
        --image={img_full} \\
        --port=8080 \\
        --env=SERVER_PORT=8080 \\
        --env=spring.datasource.url={db_url} \\
        --env=spring.datasource.username=qditp \\
        --env=DB_PASSWORD=placeholder  \\
        --requests=cpu=1,memory=2Gi \\
        --limits=cpu=1,memory=2Gi \\
        2>&1
    # DB_PASSWORD 必须从 secret 取；这里是占位，后续 patch
else
    echo "[deploy] Deployment 已存在，检查镜像..."
    CURRENT=$(kubectl get deploy {DEPLOY} -n {ns} -o jsonpath='{{.spec.template.spec.containers[0].image}}')
    if [[ "${{CURRENT}}" == "{img_full}" ]]; then
        echo "[deploy] 已是最新镜像 {img_full}，跳过"
    else
        echo "[deploy] 镜像不一致，更新..."
        kubectl set image deployment/{DEPLOY} {DEPLOY}={img_full} -n {ns}
    fi
fi
SVC={SVC}
if ! kubectl get svc {SVC} -n {ns} >/dev/null 2>&1; then
    echo "[svc] 创建 Service..."
    cat <<'YAML' | kubectl apply -n {ns} -f -
apiVersion: v1
kind: Service
metadata:
  name: {SVC}
  labels:
    app: {DEPLOY}
spec:
  selector:
    app: {DEPLOY}
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  type: ClusterIP
YAML
else
    echo "[svc] Service 已存在：$(kubectl get svc {SVC} -n {ns} -o custom-columns=PORT:.spec.ports[0].port,TARGET:.spec.ports[0].targetPort --no-headers)"
fi
"""


# ---------------------------------------------------------------- CloudOS 步骤
def _clean_leftover(cos: _CloudOS) -> None:
    """1/4 清理软件中心残留：查 name+version，存在则删除（bash 循环分词缺陷已修）。"""
    code, body = _http_token(
        cos, "GET", f"/api/cloud/deploy/v1.0/packages?page=1&size=20&name={MOD}", timeout=30
    )
    if code != "200":
        _info("   无残留（查询失败或为空，继续）")
        return
    try:
        d = json.loads(body)
    except ValueError:
        _info("   无残留（查询失败或为空，继续）")
        return
    rows = d.get("data") if isinstance(d, dict) else []
    pairs: list[tuple[str, str]] = []
    for r in rows or []:
        for v in (r.get("versions") or []):
            if v.get("version") == VER:
                pairs.append((str(r.get("id", "")), str(v.get("id", ""))))
    if not pairs:
        # bash then 分支里 for 空转不打任何行；查询成功且无残留时同样静默
        return
    for pkg_id, ver_id in pairs:
        if not pkg_id or not ver_id:
            continue
        _info(f"   删除残留 package: pkgId={pkg_id} verId={ver_id}")
        _http_token(
            cos, "DELETE",
            f"/api/cloud/deploy/v1.0/packages/{pkg_id}/versions/{ver_id}",
            timeout=30,
        )


def _pkg_upload(cos: _CloudOS, jar_path: str, file_flag: str) -> None:
    """2/4 上传 jar（multipart 单分片）。失败文案沿用 cloudos-pkg.sh 原文。"""
    with open(jar_path, "rb") as fh:
        data = fh.read()
    form, boundary = _multipart_form(
        fields=[
            ("totalChunks", "1"),
            ("chunkNumber", "1"),
            ("id", file_flag),
            ("pkgType", "jar"),
            ("name", MOD),
            ("version", VER),
            ("visible", "false"),
            ("label", MOD),
        ],
        files=[("file", os.path.basename(jar_path), data)],
    )
    code, body = _http_token(
        cos, "POST", "/api/cloud/deploy/v1.0/packages",
        data=form,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        timeout=300,
    )
    if code in (None, "000"):
        _err("NETWORK_UNREACHABLE")
        raise OpsError("上传失败", EXIT_TRANSPORT)
    if code != "200":
        _err(f"上传 HTTP {code}：{_head_c(body, 400)}")
        raise OpsError("上传失败", EXIT_EXEC)
    try:
        d = json.loads(body)
    except ValueError:
        _err(f"上传失败：响应非 JSON：{_head_c(body, 200)}")
        raise OpsError("上传失败", EXIT_EXEC) from None
    if d.get("status") is not True:
        _err(f"上传失败：code={d.get('code')} msg={d.get('msg')}")
        raise OpsError("上传失败", EXIT_EXEC)


def _image_type_uuid(cos: _CloudOS) -> str:
    """查 hcr /types 里 name 以 Default/default 开头的 uuid（awk `^[Dd]efault` 等价）。"""
    code, body = _http_token(cos, "GET", "/api/cloud/hcr/v1.0/types", timeout=30)
    if code not in (None, "000", "200"):
        return ""
    try:
        d = json.loads(body)
    except ValueError:
        return ""
    rows = d.get("data")
    if isinstance(rows, dict):
        rows = [rows]
    for r in rows or []:
        if not isinstance(r, dict):
            continue
        name = str(r.get("name") or "")
        if name.startswith("Default") or name.startswith("default"):
            return str(r.get("uuid") or "")
    return ""


def _image_build(cos: _CloudOS, dockerfile_text: str, img_type: str, file_flag: str, project: str, jar_path: str) -> tuple[str, str]:
    """3/4 POST /images/build。返回 (http_code, body)（调用方按 code 分流）。"""
    payload = json.dumps(
        {
            "dockerfile": dockerfile_text,
            "imgName": IMG_NAME,
            "version": IMG_TAG,
            "imgType": img_type,
            "description": f"built from {jar_path} via route-sentinel deploy script",
            "type": "private",
            "fileFlag": file_flag,
            "project": project,
        },
        ensure_ascii=False,
    )
    return _http_token(
        cos, "POST", "/api/cloud/hcr/v1.0/images/build",
        data=payload.encode(),
        headers={"Content-Type": "application/json"},
        timeout=120,
    )


def _build_digest(body: str) -> tuple[bool, str]:
    """解析构建响应 digest（bash 内联 python 等价，循环不 break）。

    返回 (解析成功, digest)。status 不为真/非 JSON → (False, "")（调用方走兜底）。
    """
    try:
        d = json.loads(body)
    except ValueError:
        return False, ""
    if d.get("status") is not True:
        _err(f"构建失败：code={d.get('code')} msg={d.get('msg')}")
        return False, ""
    data = d.get("data") if isinstance(d.get("data"), dict) else {}
    digest = None
    for e in (data.get("push") or []):
        st = e.get("status") or ""
        if "digest:" in st:
            digest = st.split("digest:")[-1].strip().split()[0]
    return True, digest or ""


# ---------------------------------------------------------------- 主流程
def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    # 复用入口的全局上下文（quiet/verbose/no-color/--json 不丢失）；独立运行时为默认 Ctx
    ctx = ui.context()
    ctx.command = argv
    try:
        return _run(argv, ctx)
    except OpsError as exc:
        _die_msg(str(exc))
        return exc.exit_code


def _run(argv: list[str], ctx: Ctx) -> int:
    parsed = _parse_args(argv)
    if parsed is None:
        _err(_USAGE)  # -h/--help：与 buildx 一致走 stderr，stdout 恒空
        return EXIT_OK
    jar_path, dockerfile_path, env_name, yes = parsed
    ctx.env = env_name
    ctx.env_explicit = True
    ctx.yes = yes

    # 写操作门（bash 头注的 --yes 等价机制；本地即拒，不碰远端）
    require_yes(f"{MOD} {VER} 部署（清理软件中心残留 / 上传 jar / 构建镜像 / 部署 Deployment+Service）")

    env = envx.init_env("hongdao")
    envx.banner(f"{MOD} {VER} → {env.k8s_ns}")

    if not os.path.isfile(jar_path):
        raise OpsError(f"jar 不存在：{jar_path}", EXIT_PRECONDITION)
    if not os.path.isfile(dockerfile_path):
        raise OpsError(f"dockerfile 不存在：{dockerfile_path}", EXIT_PRECONDITION)

    cos = _CloudOS()
    cos.env_init(env.name)
    if cos.get_token() is None:
        raise OpsError("登录失败", cos.login_exit_code)

    harbor_repo = f"{env.harbor_project}/{IMG_NAME}"
    img_full = f"{SCM_HOST}:443/{harbor_repo}:{IMG_TAG}"
    db_url = f"jdbc:oracle:thin:@//{env.tgt_db_dsn}"

    # ---- 1/4 清理软件中心残留 ----
    _info("=== 1/4 清理软件中心残留 ===")
    _clean_leftover(cos)

    # ---- 2/4 上传 jar（fileFlag 机制）----
    file_flag = _pkg_gen_id()
    _info(f"=== 2/4 上传 jar 到软件中心（fileFlag={file_flag}）===")
    _pkg_upload(cos, jar_path, file_flag)
    _info("   ✓ 已上传")

    # ---- 3/4 提交镜像构建 ----
    _info("=== 3/4 提交镜像构建 ===")
    dockerfile_text = Path(dockerfile_path).read_text(encoding="utf-8")
    img_type = _image_type_uuid(cos)
    if not img_type:
        raise OpsError("取不到 imgType UUID", EXIT_EXEC)

    code, body = _image_build(cos, dockerfile_text, img_type, file_flag, env.harbor_project, jar_path)
    build_method = ""
    digest = ""
    if code != "200":
        _warn(f"   CloudOS /images/build 返回 HTTP {code}：{_head_c(body, 400)}")
        _warn("   改用 Harbor v2 direct push 兜底...")
        build_method = "harbor-push"
    else:
        _info("   ✓ 构建+推送完成")
        ok, digest = _build_digest(body)
        if ok:
            ui.out(f"digest = {digest or '(未在响应里找到)'}")
        if not ok or not digest:
            build_method = "harbor-push"

    # ---- 3b/4 Harbor v2 direct push（兜底）----
    if build_method == "harbor-push":
        _info("=== 3b/4 Harbor v2 direct push（podman）===")
        base_image = BASE_IMAGE_TEST if env.name == "test" else BASE_IMAGE_HONGDAO
        jar_name = f"{MOD}-{VER}.jar"
        r = _ssh_script(
            env.remote_host,
            _docker_push_script(jar_name, base_image, harbor_repo, VER),
            "Harbor v2 direct push",
        )
        digest = _digest_from_output(r.stdout or "") or "(无 digest)"
        _info(f"   digest = {digest}")
    else:
        digest = "(CloudOS 构建响应里)"

    # ---- 4/4 检查并部署 Deployment ----
    _info("=== 4/4 检查并部署 Deployment ===")
    _ssh_script(env.remote_host, _deploy_script(env.k8s_ns, img_full, db_url), "部署 Deployment/Service")

    ui.out("")
    ui.out("==========================================")
    ui.out(f"✓ 完成：{img_full}")
    ui.out(f"  digest   = {digest}")
    ui.out("  部署方式 = CloudOS /images/build（或 Harbor direct push）")
    ui.out("  注意：DB 侧 IT-021（GATEWAY_ROUTE_CONFIG 缺失）未解决")
    ui.out("        网关启动会降级为空路由，Actuator 落库不可用")
    ui.out("  回滚命令：")
    ui.out(f'    ssh {env.remote_host} "kubectl delete deploy {DEPLOY} svc {SVC} -n {env.k8s_ns}"')
    ui.out(f'    ssh {env.remote_host} "kubectl edit vs fep-app-vr -n {env.k8s_ns}"  把 5 条 exact 改回 face-pay-server-svc:30025')
    ui.out("==========================================")
    return EXIT_OK


def _digest_from_output(out: str) -> str:
    """从远端 podman 脚本输出里取 `[podman] digest=…`（bash 在本地 inspect 是 bug，已修）。"""
    for line in out.splitlines():
        if line.startswith("[podman] digest="):
            return line[len("[podman] digest="):].strip()
    return ""


def _parse_args(argv: list[str]) -> tuple[str, str, str, bool] | None:
    """解析 <jar-path> <dockerfile-path> [--env hongdao|test] [--yes]。

    -h/--help 返回 None（调用方打用法）；失败消息沿用 bash 原文（main 统一加前缀）。
    """
    args = list(argv)
    if args and args[0] in ("-h", "--help"):
        return None
    pos: list[str] = []
    env_name = "hongdao"
    yes = False
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--yes":
            yes = True
            i += 1
        elif a == "--env":
            if i + 1 >= len(args):
                raise OpsError("--env 需要值（hongdao|test）", EXIT_USAGE)
            env_name = args[i + 1]
            i += 2
        elif a.startswith("--env="):
            env_name = a[len("--env="):]
            i += 1
        elif a.startswith("-"):
            raise OpsError(f"未知参数：{a}", EXIT_USAGE)
        else:
            pos.append(a)
            i += 1

    if not pos:
        # bash ${1:?用法: $0 <jar-path>} 原样文案
        raise OpsError(f"用法: {_CMD_NAME} <jar-path>", EXIT_USAGE)
    if len(pos) < 2:
        # bash ${2:?用法: $0 <jar-path> <dockerfile-path>} 原样文案
        raise OpsError(f"用法: {_CMD_NAME} <jar-path> <dockerfile-path>", EXIT_USAGE)
    return pos[0], pos[1], env_name, yes


if __name__ == "__main__":
    sys.exit(main())

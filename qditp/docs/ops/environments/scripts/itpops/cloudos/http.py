"""CloudOS HTTP 通道 —— lib/cloudos-http.sh 的 Python 移植。

统一三件事：base url / 鉴权头 / 网络失败识别。三个通道按响应体形态分：
  http_call    JSON 通道（对应 cloudos_http）：返回 (http_code, body)，不中断
  request_json cloudos_json + cloudos_request 合体：成功给解析结果，失败按
               bash 形态输出后抛 OpsError（错误分支文案逐条保留）
  upload_form  multipart 通道（对应 cloudos_upload）
  raw_get      原始通道（对应 cloudos_raw）：日志/文件流，body 不加工

【网络失败 MUST 显式识别（bash 头注原话保留）】
  curl 超时/代理 502 时不写 stdout，http_code 为空或 000。若不拦，调用方拿空
  stdin 去 json 解析会抛 traceback，把人误导向「接口坏了」。这里统一识别为
  code=NETWORK_UNREACHABLE 形态。

【目标基址可临时覆盖】
  默认打 CLOUDOS_BASE_URL；镜像服务在另一前缀甚至另一端口时用 target_base
  覆盖（对应 bash 的 CLOUDOS_TARGET_BASE 前缀变量）。登录始终走 CLOUDOS_BASE_URL。

【curl -k 等价】ssl 未校验 context（生产内网自签证书，与 bash -sSk 同语义）。

与 bash 版的差异：仅错误退出码细分（见包 docstring 差异 §1）；请求字节、头、
载荷、错误分支文案均逐条对齐。
"""
from __future__ import annotations

import json
import ssl
import subprocess
import sys
import urllib.error
import urllib.request

from ..errors import EXIT_EXEC, EXIT_TRANSPORT, OpsError
from . import auth

CLOUDOS_NET_ERR_JSON = (
    '{"status":false,"code":"NETWORK_UNREACHABLE","msg":"连不上目标平台","data":null,"total":0}'
)

_SSL_CTX = ssl._create_unverified_context()  # noqa: SLF001 —— curl -k 等价


def is_net_fail(code: str) -> bool:
    return code == "" or code == "000"


def _request(
    method: str,
    url: str,
    body: str | None,
    token: str,
    timeout: int,
    headers: dict[str, str],
) -> tuple[str, str]:
    """底层请求：返回 (http_code, body)。网络失败 → ("000", "")（curl 形态）。"""
    data = body.encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=_SSL_CTX) as resp:
            return str(resp.status), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        # curl 对非 2xx 也输出 body+code，urllib 会抛 —— 这里还原 curl 语义
        try:
            rbody = exc.read().decode("utf-8", "replace")
        except Exception:  # noqa: BLE001
            rbody = ""
        return str(exc.code), rbody
    except Exception:  # noqa: BLE001 —— 超时/DNS/拒连/代理 502 全部按网络失败
        return "000", ""


def post_login(body: str) -> tuple[str, str]:
    """登录请求（始终走 CLOUDOS_BASE_URL，不受 target_base 影响）。"""
    c = auth.cfg()
    url = f"{c.base_url}/api/sys/oapi/v1/double_factor/login"
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    return _request("POST", url, body, "", c.timeout, headers)


def http_call(
    method: str,
    uri: str,
    body: str = "",
    fresh: str = "",
    *,
    target_base: str = "",
) -> tuple[str, str]:
    """cloudos_http 等价。输出 (http_code, body)，不做 JSON 解析，不改 body。

    token 获取失败 → ("000", CLOUDOS_NET_ERR_JSON)（bash 同）。
    """
    c = auth.cfg()
    try:
        token = auth.get_token(fresh)
    except OpsError:
        return "000", CLOUDOS_NET_ERR_JSON

    base = target_base or c.base_url
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "X-Auth-Token": token,
    }
    send_body = body if (body and method not in ("GET", "HEAD")) else None
    return _request(method, base + uri, send_body, token, c.timeout, headers)


def _args_repr(method: str, uri: str, body: str) -> str:
    # bash `die "CloudOS 请求失败（$*）"`：method uri [body]
    return " ".join(x for x in (method, uri, body) if x)


def request_json(
    method: str,
    uri: str,
    body: str = "",
    *,
    target_base: str = "",
) -> object:
    """cloudos_request 等价。成功返回解析后的对象（非 JSON 响应原样返回 str）。

    失败路径输出与 bash 逐条一致（stderr），再抛 OpsError：
      网络失败   → NET_ERR_JSON + 「CloudOS 请求失败（…）」，EXIT_TRANSPORT
      401/403    → 自动 --fresh 重登重试一次（bash cloudos_json 语义）
      HTTP 非200 → 「错误：HTTP <code> <method> <uri>：<body≤500>」+ 同上，EXIT_EXEC
      业务拒绝   → 「业务失败：code=… msg=…」+ 美化 JSON + 同上，EXIT_EXEC
    """
    args = _args_repr(method, uri, body)
    code, rbody = http_call(method, uri, body, "", target_base=target_base)

    if is_net_fail(code):
        print(CLOUDOS_NET_ERR_JSON, file=sys.stderr)
        raise OpsError(f"CloudOS 请求失败（{args}）", EXIT_TRANSPORT)

    if code in ("401", "403"):
        code, rbody = http_call(method, uri, body, "--fresh", target_base=target_base)
        if is_net_fail(code):
            print(CLOUDOS_NET_ERR_JSON, file=sys.stderr)
            raise OpsError(f"CloudOS 请求失败（{args}）", EXIT_TRANSPORT)

    if code != "200":
        print(f"错误：HTTP {code} {method} {uri}：{rbody[:500]}", file=sys.stderr)
        raise OpsError(f"CloudOS 请求失败（{args}）", EXIT_EXEC)

    try:
        d = json.loads(rbody)
    except ValueError:
        return rbody  # bash：非 JSON 原样透传
    if isinstance(d, dict) and d.get("status") is False:
        print(f"业务失败：code={d.get('code')} msg={d.get('msg')}", file=sys.stderr)
        print(json.dumps(d, ensure_ascii=False, indent=2), file=sys.stderr)
        raise OpsError(f"CloudOS 请求失败（{args}）", EXIT_EXEC)
    return d


def pretty(data: object) -> str:
    """cloudos_json 成功输出形态：美化 JSON（ensure_ascii=False, indent=2）。"""
    if isinstance(data, (dict, list)):
        return json.dumps(data, ensure_ascii=False, indent=2)
    return str(data)


def raw_get(uri: str, *, target_base: str = "") -> str | None:
    """cloudos_raw 等价：原样取 body（日志、octet-stream）。失败返回 None。"""
    c = auth.cfg()
    try:
        token = auth.get_token("")
    except OpsError:
        return None
    base = target_base or c.base_url
    code, rbody = _request(
        "GET", base + uri, None, token, c.timeout,
        {"Accept": "*/*", "X-Auth-Token": token},
    )
    if is_net_fail(code):
        return None
    return rbody


def upload_form(
    uri: str,
    fields: list[tuple[str, str]],
    file_path: str,
    filename: str,
    *,
    target_base: str = "",
    file_field: str = "file",
    timeout: int = 300,
) -> tuple[str, str]:
    """cloudos_upload 等价：multipart/form-data 上传（curl -F 形态）。"""
    boundary = "----itpopsCloudosFormBoundary"
    chunks: list[bytes] = []
    for k, v in fields:
        chunks.append(
            (
                f"--{boundary}\r\n"
                f'Content-Disposition: form-data; name="{k}"\r\n\r\n'
                f"{v}\r\n"
            ).encode("utf-8")
        )
    with open(file_path, "rb") as f:
        file_bytes = f.read()
    chunks.append(
        (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode("utf-8")
    )
    chunks.append(file_bytes)
    chunks.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    payload = b"".join(chunks)
    return _upload_raw(
        uri, payload, f"multipart/form-data; boundary={boundary}",
        target_base=target_base, timeout=timeout,
    )


def _upload_raw(
    uri: str,
    payload: bytes,
    content_type: str,
    *,
    target_base: str = "",
    timeout: int = 300,
) -> tuple[str, str]:
    """multipart 底层：直接发已编码字节。"""
    c = auth.cfg()
    try:
        token = auth.get_token("")
    except OpsError:
        return "000", CLOUDOS_NET_ERR_JSON
    base = target_base or c.base_url
    req = urllib.request.Request(
        base + uri, data=payload, method="POST",
        headers={"Content-Type": content_type, "X-Auth-Token": token},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=_SSL_CTX) as resp:
            return str(resp.status), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        try:
            rbody = exc.read().decode("utf-8", "replace")
        except Exception:  # noqa: BLE001
            rbody = ""
        return str(exc.code), rbody
    except Exception:  # noqa: BLE001
        return "000", ""


def check_ports(host: str, ports: list[str]) -> bool:
    """cloudos_check_ports 等价：nc 探测，逐行输出「   <port> 通/不通」。返回是否至少一个通。"""
    ok = 0
    for port in ports:
        try:
            r = subprocess.run(
                ["nc", "-z", "-G", "5", host, str(port)],
                capture_output=True,
            )
            reachable = r.returncode == 0
        except OSError:
            reachable = False
        if reachable:
            print(f"   {port} 通")
            ok += 1
        else:
            print(f"   {port} 不通")
    return ok != 0

"""CloudOS 镜像仓库 API（§容器云）—— lib/cloudos-image.sh 的 Python 移植。

只封装本仓用得到的那几个端点：
  GET  /types                                  镜像类型（构建时 imgType 取值）
  GET  /images/versions?harborProjectName=     镜像列表（确认 tag 占用、取回滚点）
  GET  /image/versions?name=&type=             单镜像详情（含 digest）
  POST /images/build                           镜像构建（dockerfile + fileFlag）
  POST /images/checkImgExisted                 镜像是否已存在

【易错点（bash 头注原话保留）】
  文档写镜像服务基址 `http://{平台IP}:11000`，但红岛实测 app-mgmt 挂在
  `https://{vip}`(443)——各服务挂哪个端口不统一。故基址独立成 IMG_BASE_URL：
  默认惰性跟随 CLOUDOS_BASE_URL；探测到别的基址时用环境变量覆盖：
    IMG_BASE_URL=http://172.120.209.151:11000 itp-ops.sh cloudos image probe

【本章接口成功码是 `img.0` 而非空串，统一响应格式与 app-mgmt 不同
  （{status,code,msg,data,httpCode}），所以判定只看 status 与 http_code。

与 bash 版的差异：无（返回文本、错误分支逐条一致；仅退出码细分见包 docstring）。
"""
from __future__ import annotations

import json
import sys
import os

from . import auth
from .http import http_call, is_net_fail

IMG_API_PREFIX = "/api/cloud/hcr/v1.0"


def _img_base() -> str:
    # bash `_img_http`：调用时求值 IMG_BASE_URL，空则回落 CLOUDOS_BASE_URL
    return os.environ.get("IMG_BASE_URL") or auth.cfg().base_url


def _img_http(method: str, uri: str, body: str = "", target_base: str = "") -> tuple[str, str]:
    return http_call(method, uri, body, "", target_base=target_base or _img_base())


def image_types(target_base: str = "") -> str:
    """镜像类型列表：每行 `uuid<TAB>name`；网络失败返回 'NETWORK_UNREACHABLE'。"""
    code, body = _img_http("GET", f"{IMG_API_PREFIX}/types", target_base=target_base)
    if is_net_fail(code):
        return "NETWORK_UNREACHABLE"
    if code != "200":
        print(f"HTTP {code}", file=sys.stderr)
        return ""
    try:
        d = json.loads(body)
    except ValueError:
        return ""
    rows = d.get("data")
    if isinstance(rows, dict):
        rows = [rows]
    lines = []
    for r in rows or []:
        if isinstance(r, dict):
            lines.append("%s\t%s" % (r.get("uuid") or "", r.get("name") or ""))
    return "\n".join(lines)


def image_versions(project: str = "itp", vis_type: str = "private", target_base: str = "") -> str:
    """镜像列表：每行 `imageName<TAB>tagCount<TAB>totalSize`。"""
    code, body = _img_http(
        "GET",
        f"{IMG_API_PREFIX}/images/versions?page=1&size=50&type={vis_type}&harborProjectName={project}",
        target_base=target_base,
    )
    if is_net_fail(code):
        return "NETWORK_UNREACHABLE"
    if code != "200":
        print(f"HTTP {code}", file=sys.stderr)
        return ""
    try:
        d = json.loads(body)
    except ValueError:
        return ""
    data = d.get("data") or {}
    rows = data.get("data") if isinstance(data, dict) else data
    lines = []
    for r in rows or []:
        if isinstance(r, dict):
            lines.append("%s\t%s\t%s" % (r.get("imageName"), r.get("tagCount"), r.get("totalSize")))
    return "\n".join(lines)


def image_detail(name: str, project: str = "itp", vis_type: str = "private", target_base: str = "") -> str:
    """单镜像详情：每行 `imgName<TAB>versions<TAB>digest<TAB>imgSize`。"""
    code, body = _img_http(
        "GET",
        f"{IMG_API_PREFIX}/image/versions?name={name}&type={vis_type}&harborProjectName={project}",
        target_base=target_base,
    )
    if is_net_fail(code):
        return "NETWORK_UNREACHABLE"
    if code != "200":
        print(f"HTTP {code}", file=sys.stderr)
        return ""
    try:
        d = json.loads(body)
    except ValueError:
        return ""
    lines = []
    for r in ((d.get("data") or {}).get("images") or []):
        lines.append(
            "%s\t%s\t%s\t%s" % (r.get("imgName"), r.get("versions"), r.get("digest"), r.get("imgSize"))
        )
    return "\n".join(lines)


def image_check_existed(name: str, ver: str, target_base: str = "") -> bool | None:
    """返回 True=已存在 / False=不存在 / None=网络或 HTTP 错误（bash rc 0/1/2）。"""
    payload = json.dumps({"imgName": name, "version": ver})
    code, body = _img_http("POST", f"{IMG_API_PREFIX}/images/checkImgExisted", payload, target_base=target_base)
    if is_net_fail(code):
        return None
    if code != "200":
        return None
    try:
        d = json.loads(body)
    except ValueError:
        return None
    return d.get("data") is True


def image_build(
    dockerfile: str,
    name: str,
    ver: str,
    imgtype: str,
    fileflag: str,
    project: str,
    desc: str = "",
    target_base: str = "",
) -> tuple[str, str]:
    """镜像构建。返回 (http_code, body)；body 里 data.build[]/data.push[] 是 docker 原始日志。"""
    payload = json.dumps(
        {
            "dockerfile": dockerfile,
            "imgName": name,
            "version": ver,
            "imgType": imgtype,
            "description": desc,
            "type": "private",
            "fileFlag": fileflag,
            "project": project,
        },
        ensure_ascii=False,
    )
    return _img_http("POST", f"{IMG_API_PREFIX}/images/build", payload, target_base=target_base)

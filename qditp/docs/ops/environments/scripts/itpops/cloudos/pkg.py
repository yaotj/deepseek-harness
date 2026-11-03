"""CloudOS 应用仓库 API（软件中心）—— lib/cloudos-pkg.sh 的 Python 移植。

【为什么要上传 jar（bash 头注保留）】
  镜像构建接口 /images/build 只接受 dockerfile 字符串 + fileFlag，没有 jarPath。
  「本地 jar → 镜像」要么把 jar 先落到平台的文件存储、由 fileFlag 引用，要么走
  持续交付。本文件负责前者。

【两处文档空白（联调时 MUST 实测，勿当结论）】
  1. `id`（应用包版本 ID）文档标注必填却「未说明由谁生成」。全仓口径是**客户端
     预生成**（pkg_gen_id 产出 10 位短 ID）。
  2. 上传接口**不返回任何 ID**（data 只有 true），版本 ID 只能靠 GET 列表按
     name+version 回查 —— 见 pkg_find_version。

【端点】
  POST /api/cloud/deploy/v1.0/packages           上传应用包（multipart 分片）
  GET  /api/cloud/deploy/v1.0/packages?name=     回查包与版本 ID
  GET  /api/cloud/deploy/v1.0/package-files/{id} 下载应用包（octet-stream）
  DELETE /api/cloud/deploy/v1.0/packages/{id}/batchDel  删除版本

与 bash 版的差异：无（返回文本、错误分支逐条一致；仅退出码细分见包 docstring）。
"""
from __future__ import annotations

import json
import sys
import os
import random
import string

from .http import http_call, is_net_fail, upload_form

PKG_API_PREFIX = "/api/cloud/deploy/v1.0"


def pkg_upload(
    file: str,
    name: str,
    ver: str,
    id_: str,
    filename: str | None = None,
    public: bool = False,
) -> bool:
    """单分片上传（totalChunks=1）。filename 默认取实际文件名；传 app.jar 可对齐
    共享 Dockerfile 里的 `COPY app.jar`。失败信息按 bash 形态打 stderr，返回 False。"""
    filename = filename or os.path.basename(file)
    visible = "true" if public else "false"

    code, body = upload_form(
        f"{PKG_API_PREFIX}/packages",
        [
            ("totalChunks", "1"),
            ("chunkNumber", "1"),
            ("id", id_),
            ("pkgType", "jar"),
            ("name", name),
            ("version", ver),
            ("visible", visible),
            ("label", name),
        ],
        file,
        filename,
    )

    if is_net_fail(code):
        print("NETWORK_UNREACHABLE", file=sys.stderr)
        return False
    if code != "200":
        print(f"上传 HTTP {code}：{body[:400]}", file=sys.stderr)
        return False
    try:
        d = json.loads(body)
    except ValueError:
        print("上传失败：响应不是 JSON", file=sys.stderr)
        return False
    if d.get("status") is not True:
        print(f"上传失败：code={d.get('code')} msg={d.get('msg')}", file=sys.stderr)
        return False
    return True


def pkg_find_version(pkg_name: str, ver: str) -> str | None:
    """该版本的 `version_id<TAB>file_name<TAB>file_size`；找不到/出错返回 None。"""
    code, body = http_call("GET", f"{PKG_API_PREFIX}/packages?page=1&size=20&name={pkg_name}")
    if is_net_fail(code):
        print("NETWORK_UNREACHABLE", file=sys.stderr)
        return None
    if code != "200":
        print(f"HTTP {code}", file=sys.stderr)
        return None
    try:
        d = json.loads(body)
    except ValueError:
        return None
    for pkg in (d.get("data") or []):
        for v in (pkg.get("versions") or []):
            if v.get("version") == ver:
                return "%s\t%s\t%s" % (v.get("id"), v.get("file_name"), v.get("file_size"))
    return None


def pkg_delete_version(pkg_uuid: str, version_id: str, force: bool = False) -> bool:
    """删除版本。失败信息按 bash 形态打 stderr，返回 False。"""
    payload = json.dumps([{"versionId": version_id, "forced": force}])
    code, body = http_call("DELETE", f"{PKG_API_PREFIX}/packages/{pkg_uuid}/batchDel", payload)
    if code != "200":
        print(f"删除 HTTP {code}：{body[:300]}", file=sys.stderr)
        return False
    return True


def pkg_gen_id() -> str:
    """生成 10 位短 ID（与平台应用包版本 ID 同格式）。"""
    return "".join(random.choice(string.ascii_letters + string.digits) for _ in range(10))

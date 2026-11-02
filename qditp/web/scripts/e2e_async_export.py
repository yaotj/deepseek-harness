#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""异步导出 MVP 端到端验证脚本（测试环境）。"""
import base64
import json
import re
import ssl
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

BASE_URL = "http://172.20.211.23:30029"  # 测试环境集群 VIP（避开 Envoy sidecar 502）
USERNAME = "admin"
PASSWORD = "admin123"  # RuoYi 默认超管口令，测试环境未改

def ssl_ctx():
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def request(method, path, data=None, headers=None):
    url = BASE_URL + path
    req = urllib.request.Request(url, method=method, data=data)
    req.add_header("User-Agent", "Mozilla/5.0")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    if data and isinstance(data, (str, bytes)):
        req.add_header("Content-Type", "application/json;charset=UTF-8")
    with urllib.request.urlopen(req, context=ssl_ctx(), timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def parse_captcha(svg_b64):
    svg_xml = base64.b64decode(svg_b64).decode("utf-8")
    # 提取所有 <text x='...' ...>c</text>
    chars = []
    for m in re.finditer(r"<text[^>]+x='(\d+)'[^>]*>([^<])</text>", svg_xml):
        chars.append((int(m.group(1)), m.group(2)))
    chars.sort(key=lambda x: x[0])
    return "".join(c for _, c in chars)


def login():
    print("1) 获取验证码...")
    captcha = request("GET", "/web-server/captchaImage")
    if not captcha.get("captchaEnabled", True):
        uuid, code = "", ""
    else:
        uuid = captcha["uuid"]
        code = parse_captcha(captcha["img"])
        print(f"   识别验证码: {code}")

    print("2) 登录...")
    body = json.dumps({
        "username": USERNAME,
        "password": PASSWORD,
        "code": code,
        "uuid": uuid
    }).encode("utf-8")
    resp = request("POST", "/web-server/login", body)
    if resp.get("code") != 200:
        print(f"   登录失败: {resp}")
        sys.exit(1)
    token = resp["token"]
    print(f"   登录成功，token={token[:20]}...")
    return token


def submit_task(token):
    print("3) 提交 KEY_VERSION 异步导出任务...")
    headers = {"Authorization": f"Bearer {token}"}
    body = json.dumps({
        "taskCode": "KEY_VERSION",
        "taskName": "密钥版本全量导出",
        "paramJson": "{}"
    }).encode("utf-8")
    resp = request("POST", "/web-server/monitor/async-export/submit", body, headers)
    print(f"   响应: {resp}")
    if resp.get("code") != 200:
        sys.exit(1)
    return resp.get("taskId")


def run_quartz_job(token):
    print("4) 查找并手动执行 Quartz 任务...")
    headers = {"Authorization": f"Bearer {token}"}
    # 查询任务列表
    resp = request("GET", "/web-server/monitor/job/list?pageNum=1&pageSize=100", headers=headers)
    job_id = None
    for row in resp.get("rows", []):
        if row.get("invokeTarget") == "asyncExportQuartzTask.execute()":
            job_id = row["jobId"]
            print(f"   找到任务 jobId={job_id}，状态={row.get('status')}")
            break
    if not job_id:
        print("   未找到异步导出 Quartz 任务")
        sys.exit(1)

    body = json.dumps({"jobId": job_id, "jobGroup": "DEFAULT"}).encode("utf-8")
    resp = request("PUT", "/web-server/monitor/job/run", body, headers)
    print(f"   执行响应: {resp}")


def wait_for_task(token, task_id, timeout=120):
    print(f"5) 轮询任务 {task_id} 状态...")
    headers = {"Authorization": f"Bearer {token}"}
    for i in range(timeout // 5):
        resp = request("GET", f"/web-server/monitor/async-export/list?pageNum=1&pageSize=10", headers=headers)
        for row in resp.get("rows", []):
            if row.get("id") == task_id:
                status = row.get("status")
                print(f"   [{i*5}s] status={status}, rowCount={row.get('rowCount')}, fileSize={row.get('fileSize')}")
                if status == "SUCCESS":
                    return row
                if status == "FAILED":
                    print(f"   任务失败: {row.get('errorMsg')}")
                    sys.exit(1)
        time.sleep(5)
    print("   超时未成功")
    sys.exit(1)


def download_file(token, task_id, save_path):
    print(f"6) 下载任务 {task_id} 的导出文件...")
    url = BASE_URL + f"/web-server/monitor/async-export/download/{task_id}"
    req = urllib.request.Request(url)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("User-Agent", "Mozilla/5.0")
    with urllib.request.urlopen(req, context=ssl_ctx(), timeout=60) as resp:
        data = resp.read()
        Path(save_path).write_bytes(data)
        print(f"   已保存 {save_path}，大小 {len(data)} bytes")


def main():
    token = login()
    task_id = submit_task(token)
    run_quartz_job(token)
    task = wait_for_task(token, task_id)
    save_path = f"/tmp/KEY_VERSION_{task_id}.xlsx"
    download_file(token, task_id, save_path)
    print(f"\n✓ 端到端验证通过：taskId={task_id}, rowCount={task.get('rowCount')}, file={save_path}")


if __name__ == "__main__":
    main()

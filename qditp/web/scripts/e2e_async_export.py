#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""异步导出全量端到端验证脚本（测试环境，覆盖全部 10 个业务导出）。"""
import base64
import json
import re
import ssl
import sys
import time
import urllib.request
from pathlib import Path

BASE_URL = "http://172.20.211.23:30029"  # 测试环境集群 VIP（避开 Envoy sidecar 502）
USERNAME = "admin"
PASSWORD = "admin123"  # RuoYi 默认超管口令，测试环境未改

# 11 个待验证的导出任务；paramJson 与前端「后台全量导出」按钮提交时的查询条件对齐。
EXPORT_TASKS = [
    {"taskCode": "KEY_VERSION", "taskName": "密钥版本全量导出", "paramJson": {}},
    {"taskCode": "CARD_POOL_BATCH", "taskName": "卡池批次全量导出", "paramJson": {}},
    {"taskCode": "FACE_PAY_ORDER", "taskName": "当面付订单全量导出",
     "paramJson": {"beginTime": "2024-01-01 00:00:00", "endTime": "2099-12-31 23:59:59"}},
    {"taskCode": "OVERTIME_REFUNDABLE", "taskName": "超时罚金可退款订单导出",
     "paramJson": {"startDate": "2024-01-01", "endDate": "2099-12-31"}},
    {"taskCode": "OFFLINE_CODE_STAT", "taskName": "离线码交易统计导出",
     "paramJson": {"startDate": "2024-01-01", "endDate": "2099-12-31"}},
    {"taskCode": "DAILY_TICKET_REFUND_ORDER", "taskName": "日票可退款订单导出",
     "paramJson": {"beginTime": "2024-01-01 00:00:00", "endTime": "2099-12-31 23:59:59"}},
    {"taskCode": "DAILY_TICKET_REFUND_RECORD", "taskName": "日票退款记录导出",
     "paramJson": {"beginTime": "2024-01-01 00:00:00", "endTime": "2099-12-31 23:59:59"}},
    {"taskCode": "BLACKLIST", "taskName": "黑名单导出", "paramJson": {}},
    {"taskCode": "REG_STAT", "taskName": "注册量统计导出",
     "paramJson": {"startDate": "2024-01-01", "endDate": "2099-12-31"}},
    {"taskCode": "ALIPAY_USER", "taskName": "支付宝用户导出",
     "paramJson": {"queryType": "THIRD_USER_ID", "keyword": "e2e_test"}},
]


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


def submit_task(token, task_def):
    code = task_def["taskCode"]
    name = task_def["taskName"]
    print(f"3) 提交 {code} 异步导出任务...")
    headers = {"Authorization": f"Bearer {token}"}
    body = json.dumps({
        "taskCode": code,
        "taskName": name,
        "paramJson": json.dumps(task_def["paramJson"], ensure_ascii=False)
    }).encode("utf-8")
    resp = request("POST", "/web-server/monitor/async-export/submit", body, headers)
    print(f"   响应: {resp}")
    if resp.get("code") != 200:
        raise RuntimeError(f"提交 {code} 失败: {resp}")
    task_id = resp.get("taskId")
    if not task_id:
        raise RuntimeError(f"提交 {code} 未返回 taskId")
    return task_id


def run_quartz_job(token):
    print("4) 查找并手动执行 Quartz 任务...")
    headers = {"Authorization": f"Bearer {token}"}
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
    if resp.get("code") != 200:
        raise RuntimeError(f"触发 Quartz 失败: {resp}")


def wait_for_task(token, task_id, code, timeout=180):
    print(f"5) 轮询任务 {task_id} ({code}) 状态...")
    headers = {"Authorization": f"Bearer {token}"}
    for i in range(timeout // 5):
        resp = request("GET", f"/web-server/monitor/async-export/list?pageNum=1&pageSize=100", headers=headers)
        for row in resp.get("rows", []):
            if row.get("id") == task_id:
                status = row.get("status")
                row_count = row.get("rowCount")
                file_size = row.get("fileSize")
                print(f"   [{i*5}s] status={status}, rowCount={row_count}, fileSize={file_size}")
                if status == "SUCCESS":
                    return row
                if status == "FAILED":
                    raise RuntimeError(f"任务 {code} 失败: {row.get('errorMsg')}")
        time.sleep(5)
    raise RuntimeError(f"任务 {code} 轮询超时未成功")


def download_file(token, task_id, code, save_dir="/tmp"):
    print(f"6) 下载任务 {task_id} ({code}) 的导出文件...")
    url = BASE_URL + f"/web-server/monitor/async-export/download/{task_id}"
    req = urllib.request.Request(url)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("User-Agent", "Mozilla/5.0")
    with urllib.request.urlopen(req, context=ssl_ctx(), timeout=60) as resp:
        data = resp.read()
        save_path = Path(save_dir) / f"{code}_{task_id}.xlsx"
        save_path.write_bytes(data)
        print(f"   已保存 {save_path}，大小 {len(data)} bytes")
        return save_path, len(data)


def main():
    token = login()
    task_ids = []
    for task_def in EXPORT_TASKS:
        task_id = submit_task(token, task_def)
        task_ids.append((task_def, task_id))

    run_quartz_job(token)

    results = []
    failures = []
    for task_def, task_id in task_ids:
        code = task_def["taskCode"]
        try:
            row = wait_for_task(token, task_id, code)
            path, size = download_file(token, task_id, code)
            results.append({
                "taskCode": code,
                "taskId": task_id,
                "rowCount": row.get("rowCount"),
                "fileSize": row.get("fileSize"),
                "saved": str(path),
                "downloadBytes": size,
            })
        except Exception as e:
            failures.append({"taskCode": code, "taskId": task_id, "error": str(e)})
            print(f"   ✗ {code} 失败: {e}")

    print("\n========== 验证结果汇总 ==========")
    for r in results:
        print(f"✓ {r['taskCode']}: taskId={r['taskId']}, rowCount={r['rowCount']}, "
              f"fileSize={r['fileSize']}, downloadBytes={r['downloadBytes']}")
    if failures:
        print(f"\n✗ 失败 {len(failures)} 个:")
        for f in failures:
            print(f"   {f['taskCode']} (taskId={f['taskId']}): {f['error']}")
        sys.exit(1)
    print(f"\n✓ 全部 {len(EXPORT_TASKS)} 个异步导出端到端验证通过")


if __name__ == "__main__":
    main()

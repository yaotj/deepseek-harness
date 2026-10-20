#!/usr/bin/env python3
"""
route-sentinel 性能测试脚本。
支持 S1~S4 四个场景，使用 http.client 长连接，可指定并发、持续时间和网关地址。
"""
import argparse
import concurrent.futures
import http.client
import os
import statistics
import time
import uuid
from dataclasses import dataclass
from urllib.parse import quote, urlparse


@dataclass
class Scenario:
    name: str
    path: str
    user_id: str
    expected_route: str
    dynamic_user: bool


REGISTERED_USER = os.environ.get("REGISTERED_USER", "00522967")

SCENARIOS = {
    "S1": Scenario(
        name="S1-缓存命中-精确设备URL",
        path="/itptvm/ci/tvm/notiTakeTicketResult",
        user_id=REGISTERED_USER,
        expected_route="face-pay-split-route",
        dynamic_user=False,
    ),
    "S2": Scenario(
        name="S2-缓存未命中-精确设备URL",
        path="/itptvm/ci/tvm/notiTakeTicketResult",
        user_id="ghost-user",
        expected_route="legacy-route",
        dynamic_user=True,
    ),
    "S3": Scenario(
        name="S3-无需查表-默认设备URL",
        path="/itptvm/ci/tvm/requestOrder",
        user_id="ghost-user",
        expected_route="face-pay-default-route",
        dynamic_user=True,
    ),
    "S4": Scenario(
        name="S4-缓存命中-APP域",
        path="/fep-app/ci/app/requestOrder",
        user_id=REGISTERED_USER,
        expected_route="fep-app-route",
        dynamic_user=False,
    ),
}


def build_path(path: str, user_id: str) -> str:
    biz = f'{{"thirdUserId":"{user_id}"}}'
    return f"{path}?bizData={quote(biz, safe='')}"


def worker_run(base_url: str, scenario: Scenario, concurrency: int, duration_sec: int, worker_id: int):
    parsed = urlparse(base_url)
    host = parsed.hostname or "localhost"
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    is_https = parsed.scheme == "https"
    conn_cls = http.client.HTTPSConnection if is_https else http.client.HTTPConnection

    results = []
    errors = []
    conn = None
    stop_at = time.time() + duration_sec

    def ensure_conn():
        nonlocal conn
        if conn is None:
            conn = conn_cls(host, port, timeout=10)

    def close_conn():
        nonlocal conn
        if conn:
            try:
                conn.close()
            except Exception:
                pass
            conn = None

    while time.time() < stop_at:
        user_id = f"ghost-{uuid.uuid4().hex[:8]}" if scenario.dynamic_user else scenario.user_id
        target = build_path(scenario.path, user_id)
        start = time.perf_counter()
        try:
            ensure_conn()
            conn.request("GET", target, headers={"Accept": "*/*", "Connection": "keep-alive"})
            resp = conn.getresponse()
            body = resp.read()
            latency_ms = (time.perf_counter() - start) * 1000
            ok = resp.status == 200
            results.append((latency_ms, ok))
        except Exception as e:
            latency_ms = (time.perf_counter() - start) * 1000
            errors.append((latency_ms, str(e)))
            close_conn()

    close_conn()
    return results, errors


def run_scenario(base_url: str, scenario: Scenario, concurrency: int, duration_sec: int) -> dict:
    all_results = []
    all_errors = []
    start = time.time()

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(worker_run, base_url, scenario, concurrency, duration_sec, i)
            for i in range(concurrency)
        ]
        for future in concurrent.futures.as_completed(futures):
            res, err = future.result()
            all_results.extend(res)
            all_errors.extend(err)

    elapsed = time.time() - start
    success_latencies = [r[0] for r in all_results if r[1]]
    failed = len(all_errors) + sum(1 for r in all_results if not r[1])
    total = len(all_results) + len(all_errors)

    def percentile(data, pct):
        if not data:
            return None
        idx = int(len(data) * pct)
        idx = min(idx, len(data) - 1)
        return sorted(data)[idx]

    summary = {
        "scenario": scenario.name,
        "concurrency": concurrency,
        "duration": duration_sec,
        "total_requests": total,
        "successful_requests": len(success_latencies),
        "failed_requests": failed,
        "qps": round(total / elapsed, 2) if elapsed > 0 else 0,
        "avg_ms": round(statistics.mean(success_latencies), 2) if success_latencies else None,
        "p50_ms": round(statistics.median(success_latencies), 2) if success_latencies else None,
        "p95_ms": round(percentile(success_latencies, 0.95), 2) if success_latencies else None,
        "p99_ms": round(percentile(success_latencies, 0.99), 2) if success_latencies else None,
        "min_ms": round(min(success_latencies), 2) if success_latencies else None,
        "max_ms": round(max(success_latencies), 2) if success_latencies else None,
    }
    return summary


def print_summary(summary: dict):
    print(f"\n========== {summary['scenario']} ==========")
    print(f"并发数: {summary['concurrency']}, 持续时间: {summary['duration']}s")
    print(f"总请求: {summary['total_requests']}, 成功: {summary['successful_requests']}, 失败: {summary['failed_requests']}")
    print(f"QPS: {summary['qps']}")
    print(f"平均延迟: {summary['avg_ms']} ms")
    print(f"P50: {summary['p50_ms']} ms")
    print(f"P95: {summary['p95_ms']} ms")
    print(f"P99: {summary['p99_ms']} ms")
    print(f"最小/最大: {summary['min_ms']} ms / {summary['max_ms']} ms")


def main():
    parser = argparse.ArgumentParser(description="route-sentinel 性能测试")
    parser.add_argument("--base", default="http://localhost:8080", help="网关基础地址")
    parser.add_argument("--concurrency", "-c", type=int, default=50, help="并发数")
    parser.add_argument("--duration", "-d", type=int, default=30, help="每场景持续秒数")
    parser.add_argument("--scenarios", "-s", default="S1,S2,S3,S4", help="执行场景，逗号分隔")
    parser.add_argument("--warmup", "-w", type=int, default=3, help="预热秒数")
    args = parser.parse_args()

    selected = [s.strip() for s in args.scenarios.split(",")]
    print(f"性能测试开始: base={args.base}, concurrency={args.concurrency}, duration={args.duration}s")

    for key in selected:
        if key not in SCENARIOS:
            print(f"未知场景: {key}，跳过")
            continue
        scenario = SCENARIOS[key]
        print(f"\n[Warmup] {scenario.name} ...")
        run_scenario(args.base, scenario, max(1, args.concurrency // 5), args.warmup)
        print(f"[Test] {scenario.name} ...")
        summary = run_scenario(args.base, scenario, args.concurrency, args.duration)
        print_summary(summary)


if __name__ == "__main__":
    main()

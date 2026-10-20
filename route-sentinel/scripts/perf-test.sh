#!/bin/bash
# route-sentinel 性能测试执行脚本
# 优先使用 wrk；未安装时回退到内置 Python 压测脚本
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PYTHON="${PYTHON:-python3}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-50}"
DURATION="${DURATION:-30}"
SCENARIOS="${SCENARIOS:-S1,S2,S3,S4}"
REGISTERED_USER="${REGISTERED_USER:-00522967}"

urlencode() {
    "${PYTHON}" -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''), end='')" "$1"
}

BIZ_REGISTERED="bizData=$(urlencode "{\"thirdUserId\":\"${REGISTERED_USER}\"}")"
BIZ_GHOST="bizData=$(urlencode '{"thirdUserId":"ghost-USER"}')"

URL_S1="${BASE_URL}/itptvm/ci/tvm/notiTakeTicketResult?${BIZ_REGISTERED}"
URL_S2="${BASE_URL}/itptvm/ci/tvm/notiTakeTicketResult?${BIZ_GHOST}"
URL_S3="${BASE_URL}/itptvm/ci/tvm/requestOrder?${BIZ_GHOST}"
URL_S4="${BASE_URL}/fep-app/ci/app/requestOrder?${BIZ_REGISTERED}"

usage() {
    echo "Usage: $0 [wrk|python]"
    echo "环境变量: BASE_URL, CONCURRENCY, DURATION, SCENARIOS"
    exit 1
}

run_wrk() {
    local url="$1"
    local name="$2"
    echo ""
    echo "========== ${name} (wrk) =========="
    wrk -t"${WRK_THREADS:-4}" -c"${CONCURRENCY}" -d"${DURATION}s" --latency "${url}"
}

run_ab_static() {
    local url="$1"
    local name="$2"
    local total=$((CONCURRENCY * DURATION * 20))
    echo ""
    echo "========== ${name} (ab) =========="
    ab -n "${total}" -c "${CONCURRENCY}" "${url}"
}

run_with_wrk() {
    run_wrk "${URL_S1}" "S1-缓存命中-精确设备URL"
    run_wrk "${URL_S4}" "S4-缓存命中-APP域"
    run_wrk "${URL_S3}" "S3-无需查表-默认设备URL"
    echo ""
    echo "注意：S2 动态用户未命中场景请使用 perf-test.py（ab/wrk 单 URL 无法生成动态用户）"
}

run_with_ab() {
    run_ab_static "${URL_S1}" "S1-缓存命中-精确设备URL"
    run_ab_static "${URL_S4}" "S4-缓存命中-APP域"
    run_ab_static "${URL_S3}" "S3-无需查表-默认设备URL"
    echo ""
    echo "注意：S2 动态用户未命中场景请使用 perf-test.py"
}

run_with_python() {
    echo "使用内置 Python 压测脚本（支持动态用户）..."
    "${PYTHON}" "${SCRIPT_DIR}/perf-test.py" \
        --base "${BASE_URL}" \
        --concurrency "${CONCURRENCY}" \
        --duration "${DURATION}" \
        --scenarios "${SCENARIOS}"
}

MODE="${1:-auto}"

case "${MODE}" in
    wrk)
        if ! command -v wrk >/dev/null 2>&1; then
            echo "wrk 未安装，切换为 python 模式"
            run_with_python
        else
            run_with_wrk
        fi
        ;;
    ab)
        if ! command -v ab >/dev/null 2>&1; then
            echo "ab 未安装，切换为 python 模式"
            run_with_python
        else
            run_with_ab
        fi
        ;;
    python)
        run_with_python
        ;;
    auto)
        if command -v wrk >/dev/null 2>&1; then
            run_with_wrk
        elif command -v ab >/dev/null 2>&1; then
            run_with_ab
        else
            run_with_python
        fi
        ;;
    *)
        usage
        ;;
esac

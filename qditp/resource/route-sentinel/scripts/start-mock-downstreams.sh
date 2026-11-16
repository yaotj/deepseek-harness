#!/bin/bash
# 启动 route-sentinel 性能测试所需的 3 个 mock 下游
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON="${PYTHON:-python3}"

echo "Starting mock downstreams on ports 9101/9102/9103..."
exec "${PYTHON}" "${SCRIPT_DIR}/start-mock-downstreams.py"

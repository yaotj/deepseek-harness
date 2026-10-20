#!/usr/bin/env bash
set -euo pipefail

# ITP 镜像下载/保存脚本
# 用途：从旧 Harbor 拉取全部 ITP 镜像并保存为 tar 包，便于迁移到新集群
# 运行位置：必须能解析并访问 os-harbor-svc.default.svc.cloudos:443
#           建议在旧 K8s 集群节点（k8s-master/worker）或已接入集群网络的跳板机上执行

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_LIST="${SCRIPT_DIR}/image-list.txt"
OUTPUT_DIR="${SCRIPT_DIR}/images"
FAILED_LOG="${OUTPUT_DIR}/pull-failed.log"

# 可选：若 Harbor 需要认证，先在此登录
# HARBOR_USER=""
# HARBOR_PASS=""
# echo "${HARBOR_PASS}" | docker login os-harbor-svc.default.svc.cloudos:443 -u "${HARBOR_USER}" --password-stdin

mkdir -p "${OUTPUT_DIR}"
: > "${FAILED_LOG}"

if [[ ! -f "${IMAGE_LIST}" ]]; then
    echo "ERROR: image-list.txt not found at ${IMAGE_LIST}"
    exit 1
fi

# 检查 docker 可用
if ! command -v docker &>/dev/null; then
    echo "ERROR: docker CLI not found"
    exit 1
fi

# 检查旧 Harbor 是否可达（仅校验 DNS，不校验认证）
if ! nslookup os-harbor-svc.default.svc.cloudos &>/dev/null; then
    echo "WARN: os-harbor-svc.default.svc.cloudos cannot be resolved from this host."
    echo "      This script must run on a host that can reach the old cluster's internal Harbor."
fi

echo "开始拉取镜像，输出目录: ${OUTPUT_DIR}"
echo "----------------------------------------"

while IFS= read -r line || [[ -n "${line}" ]]; do
    # 跳过空行和注释
    line="$(echo "${line}" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [[ -z "${line}" ]] && continue
    [[ "${line}" =~ ^# ]] && continue

    image="${line}"
    # tar 文件名：把 / 和 : 替换成 _
    tar_name="$(echo "${image}" | tr '/:' '__').tar"
    tar_path="${OUTPUT_DIR}/${tar_name}"

    echo ""
    echo "[PULL] ${image}"
    if docker pull "${image}"; then
        echo "[SAVE] ${tar_path}"
        docker save "${image}" -o "${tar_path}"
    else
        echo "[FAIL] ${image}" | tee -a "${FAILED_LOG}"
    fi
done < "${IMAGE_LIST}"

echo ""
echo "----------------------------------------"
echo "下载完成"
echo "成功镜像数: $(find "${OUTPUT_DIR}" -name '*.tar' | wc -l)"
if [[ -s "${FAILED_LOG}" ]]; then
    echo "失败镜像数: $(wc -l < "${FAILED_LOG}")"
    echo "失败清单: ${FAILED_LOG}"
fi

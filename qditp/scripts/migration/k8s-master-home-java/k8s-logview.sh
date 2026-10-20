#!/bin/bash
# ==========================================
# K8s 日志快捷查看工具 (联动构建脚本映射 + 容器/日志自适应)
# 用法: ./k8s-logview.sh <服务名或JAR前缀> [关键字] [-f] [行数]
# 示例:
#   ./k8s-logview.sh fep-app                    # 自动识别业务容器+日志
#   ./k8s-logview.sh fep-app-server ERROR       # 自动映射+搜索ERROR
#   ./k8s-logview.sh account -f                 # 实时追踪
#   ./k8s-logview.sh fep-app -2000f             # 查看最近2000行历史日志
#   KLOG_CONTAINER=nginx ./k8s-logview.sh svc   # 强制指定容器
# ==========================================

set -euo pipefail

# 🎯 【配置区】
NS="itp"
LOG_BASE="/home/javaapp/app/logs"
BUILD_SCRIPT="/home/java/build-push.sh"
DEFAULT_LINES=100
# 允许通过环境变量覆盖默认容器名
CONTAINER="${KLOG_CONTAINER:-}"
# 需要自动排除的 Sidecar 容器名称列表
SIDECAR_CONTAINERS="istio-proxy envoy sidecar fluentd filebeat log-collector"

# ---------- 颜色定义 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}📋 $*${NC}"; }
warn()  { echo -e "${YELLOW}💡 $*${NC}"; }
error() { echo -e "${RED}❌ $*${NC}" >&2; }

# ---------- 安全加载构建脚本中的 JAR_MAP ----------
declare -A JAR_MAP
load_jar_map() {
    if [ ! -f "$BUILD_SCRIPT" ]; then
        warn "构建脚本不存在: $BUILD_SCRIPT (跳过映射加载)"
        return
    fi
    while IFS= read -r line; do
        if [[ "$line" =~ ^[[:space:]]*\[\"([^\"]+)\"\]=\"([^\"]+)\" ]]; then
            JAR_MAP["${BASH_REMATCH[1]}"]="${BASH_REMATCH[2]}"
        fi
    done < <(sed -n '/declare -A JAR_MAP/,/^)/p' "$BUILD_SCRIPT")
    
    if [ ${#JAR_MAP[@]} -gt 0 ]; then
        info "已加载 ${#JAR_MAP[@]} 条服务映射"
    fi
}

# ---------- 参数解析 ----------
parse_args() {
    if [ $# -lt 1 ]; then
        error "用法: $0 <服务名或JAR前缀> [关键字] [-f] [行数]"
        exit 1
    fi

    INPUT_NAME="$1"
    KEYWORDS=()
    FOLLOW=false
    LINES=$DEFAULT_LINES
    shift

    for arg in "$@"; do
        if [ "$arg" = "-f" ]; then
            FOLLOW=true
        elif [[ "$arg" =~ ^-[0-9]+[fF]+$ ]]; then
            NUM="${arg#-}"
            NUM="${NUM%[fF]}"
            LINES="$NUM"
            FOLLOW=true
        elif [[ "$arg" =~ ^[0-9]+$ ]]; then
            LINES="$arg"
        else
            KEYWORDS+=("$arg")
        fi
    done

    if [ ${#KEYWORDS[@]} -gt 0 ]; then
        KEYWORD="${KEYWORDS[*]}"
    else
        KEYWORD=""
    fi
}

# ---------- 智能推导 Pod 名称 ----------
resolve_pod() {
    local input="$1"
    local pod_name=""

    pod_name=$(kubectl get pods -n "$NS" --no-headers \
        -o custom-columns=":metadata.name" 2>/dev/null \
        | grep "^${input}" | head -1 || true)

    if [ -z "$pod_name" ] && [ ${#JAR_MAP[@]} -gt 0 ]; then
        for key in "${!JAR_MAP[@]}"; do
            if [[ "${JAR_MAP[$key]}" == "$input" ]]; then
                warn "检测到输入为JAR前缀 [$input]，已映射为K8s部署名 [$key]"
                pod_name=$(kubectl get pods -n "$NS" --no-headers \
                    -o custom-columns=":metadata.name" 2>/dev/null \
                    | grep "^${key}" | head -1 || true)
                break
            fi
        done
    fi

    if [ -z "$pod_name" ]; then
        error "未找到匹配 '$input' 的Pod (命名空间: $NS)"
        echo "可用Pod列表:" >&2
        kubectl get pods -n "$NS" --no-headers \
            -o custom-columns="NAME:metadata.name" 2>/dev/null \
            | grep -i "$input" >&2 || true
        exit 1
    fi

    POD_NAME="$pod_name"
}

# ---------- 智能解析容器名称 (自动过滤Sidecar) ----------
resolve_container() {
    # 如果已通过环境变量指定，直接使用
    if [ -n "$CONTAINER" ]; then
        info "使用环境变量指定的容器: ${CONTAINER}"
        return
    fi

    local pod="$1"
    local containers
    containers=$(kubectl get pod "$pod" -n "$NS" -o jsonpath='{.spec.containers[*].name}' 2>/dev/null)

    if [ -z "$containers" ]; then
        error "无法获取Pod [$pod] 的容器信息"
        exit 1
    fi

    local count
    count=$(echo "$containers" | wc -w)

    if [ "$count" -eq 1 ]; then
        CONTAINER="$containers"
        info "自动识别单容器: ${CONTAINER}"
    else
        # 多容器场景：过滤掉已知的 Sidecar 容器
        local business_containers=""
        for c in $containers; do
            local is_sidecar=false
            for sc in $SIDECAR_CONTAINERS; do
                if [ "$c" = "$sc" ]; then
                    is_sidecar=true
                    break
                fi
            done
            if [ "$is_sidecar" = false ]; then
                business_containers="$business_containers $c"
            fi
        done
        
        # 去除首尾空格并统计剩余容器数
        business_containers=$(echo "$business_containers" | xargs)
        local biz_count
        biz_count=$(echo "$business_containers" | wc -w)

        if [ "$biz_count" -eq 1 ]; then
            CONTAINER="$business_containers"
            info "自动识别业务容器(已过滤Sidecar): ${CONTAINER}"
        elif [ "$biz_count" -gt 1 ]; then
            error "Pod [$pod] 包含多个业务容器，无法自动选择"
            echo "可用业务容器:" >&2
            for c in $business_containers; do echo "  - $c" >&2; done
            echo "💡 请指定: KLOG_CONTAINER=<容器名> $0 ..." >&2
            exit 1
        else
            error "Pod [$pod] 所有容器均为Sidecar，未找到业务容器"
            exit 1
        fi
    fi
}

# ---------- 执行日志查看 (日志文件名随容器名动态适配) ----------
exec_log_view() {
    local cmd_prefix="kubectl exec ${POD_NAME} -n ${NS} -c ${CONTAINER} --"
    local log_dir="${LOG_BASE}/${POD_NAME}"

    # 自动探测目录下最新的 .log 文件
    local log_file
    log_file=$($cmd_prefix bash -c "ls -t '${log_dir}'/*.log 2>/dev/null | head -1" || true)

    if [ -z "$log_file" ]; then
        error "未在 ${log_dir} 下找到任何 .log 文件"
        echo "目录内容:" >&2
        $cmd_prefix ls -la "$log_dir" >&2 || true
        exit 1
    fi

    info "目标Pod: ${POD_NAME}"
    info "目标容器: ${CONTAINER}"
    info "日志文件: ${log_file}"

    if [ "$FOLLOW" = true ]; then
        if [ -n "$KEYWORD" ]; then
            info "实时追踪包含 '${KEYWORD}' 的日志 (最近${LINES}条)..."
            $cmd_prefix tail -n "$LINES" -F "$log_file" | grep -i --line-buffered -E "$(printf '%s|' "${KEYWORDS[@]}")" | sed 's/|$//' 
        else
            info "实时追踪日志 (最近${LINES}条历史)..."
            $cmd_prefix tail -n "$LINES" -F "$log_file"
        fi
    else
        if [ -n "$KEYWORD" ]; then
            info "搜索 '${KEYWORD}' (最近${LINES}条匹配)..."
            $cmd_prefix grep -i -E "$(printf '%s|' "${KEYWORDS[@]}")" "$log_file" | sed 's/|$//' | tail -"$LINES"
        else
            info "查看最近 ${LINES} 行日志..."
            $cmd_prefix tail -"$LINES" "$log_file"
        fi
    fi
}


main() {
    load_jar_map
    parse_args "$@"
    resolve_pod "$INPUT_NAME"
    resolve_container "$POD_NAME"
    exec_log_view
}

main "$@"

#!/bin/bash
# ==========================================
# 服务日志监控 watcher
# 定期检查目标 pod 是否重启，如果重启则重新执行 klog.sh
# ==========================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KLOG="$SCRIPT_DIR/klog.sh"
STATE_FILE="/tmp/klog-watcher-state.txt"
REMOTE_HOST="k8s-master"
NS="itp"
HISTORY=1000

# 目标服务列表
SERVICES=(
  alipay-pay-sign-server
  alipay-account-server
  fep-alipay-server
  fep-alipay
  fep-app-server
  fep-app
  fep-dev-server
  ticket-server
  para-server
  blacklist-server
  pay-sign-server
  industry-data-server
  daily-ticket-server
  gate-txn-pay-server
  key-server
  collect-pay
  collect-pay-server
  account
  security-server
  es-server
)

mkdir -p "$(dirname "$STATE_FILE")"
> "$STATE_FILE"

get_pod() {
  local svc="$1"
  ssh "$REMOTE_HOST" "kubectl get pods -n $NS --no-headers -l app=${svc} -o custom-columns='NAME:.metadata.name'" 2>/dev/null | head -1
}

get_container_ready() {
  local pod="$1"
  ssh "$REMOTE_HOST" "kubectl get pod -n $NS ${pod} -o jsonpath='{.status.containerStatuses[0].ready}'" 2>/dev/null
}

get_restart_count() {
  local pod="$1"
  ssh "$REMOTE_HOST" "kubectl get pod -n $NS ${pod} -o jsonpath='{.status.containerStatuses[0].restartCount}'" 2>/dev/null
}

already_monitoring() {
  local svc="$1"
  pgrep -f "klog.sh ${svc} -${HISTORY}f" >/dev/null 2>&1
}

start_monitor() {
  local svc="$1"
  local pod="$2"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] 启动监控: svc=${svc} pod=${pod}"
  nohup bash "$KLOG" "$svc" "-${HISTORY}f" >/dev/null 2>&1 &
}

kill_monitor() {
  local svc="$1"
  local pid
  pid=$(pgrep -f "klog.sh ${svc} -${HISTORY}f" | head -1)
  if [ -n "$pid" ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 终止旧监控: svc=${svc} pid=${pid}"
    kill "$pid" 2>/dev/null
    sleep 1
  fi
}

while true; do
  for svc in "${SERVICES[@]}"; do
    pod=$(get_pod "$svc")
    if [ -z "$pod" ]; then
      continue
    fi

    restart=$(get_restart_count "$pod")
    ready=$(get_container_ready "$pod")
    key="${svc}|${pod}|${restart}|${ready}"

    old_key=$(grep "^${svc}|" "$STATE_FILE" | tail -1 | cut -d'=' -f2)

    if [ "$key" != "$old_key" ]; then
      # 状态变化：先杀掉旧监控，再重启
      kill_monitor "$svc"

      if [ "$ready" = "true" ]; then
        start_monitor "$svc" "$pod"
      fi

      # 更新状态文件
      sed -i.bak "/^${svc}|/d" "$STATE_FILE"
      echo "${svc}=${key}" >> "$STATE_FILE"
    fi
  done

  sleep 30
done

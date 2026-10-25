#!/bin/bash
# enable-tracing-20261014.sh
# 用途：将 A+B 档 12 个模块接入链路跟踪（三行成组 + VLOGS_URL）
# 注意：本机当前 kubectl context 为 orbstack，非生产集群；
#       本脚本 MUST 在能访问生产集群（172.20.x.x）的节点上执行。

set -euo pipefail

NS=itp

MODULES=(
  para-server
  blacklist-server
  face-pay-server
  alipay-pay-sign-server
  fep-app
  fep-dev-server
  fep-acc
  fep-alipay
  ticket-server
  account
  alipay-account-server
  trans-query
)

add_tracing_env() {
  local deploy=$1
  echo "==> 配置 deployment/$deploy"

  kubectl -n $NS set env deployment/"$deploy" \
    management.tracing.enabled=true \
    management.tracing.sampling.probability=0 \
    spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration

  # trans-query 和 face-pay-server 的基线此前无 VLOGS_URL，需要补
  if [[ "$deploy" == "trans-query" || "$deploy" == "face-pay-server" ]]; then
    kubectl -n $NS set env deployment/"$deploy" \
      VLOGS_URL=http://victoria-logs-pbi6a-svc.itp.svc:30032
  fi
}

verify() {
  local deploy=$1
  echo "==> 验证 deployment/$deploy"
  kubectl -n $NS get deploy "$deploy" -o jsonpath='{range .spec.template.spec.containers[0].env[?(@.name=="management.tracing.enabled")]}{.name}={.value}{"\n"}{end}'
  kubectl -n $NS get deploy "$deploy" -o jsonpath='{range .spec.template.spec.containers[0].env[?(@.name=="management.tracing.sampling.probability")]}{.name}={.value}{"\n"}{end}'
  kubectl -n $NS get deploy "$deploy" -o jsonpath='{range .spec.template.spec.containers[0].env[?(@.name=="spring.autoconfigure.exclude")]}{.name}={.value}{"\n"}{end}'
  kubectl -n $NS get deploy "$deploy" -o jsonpath='{range .spec.template.spec.containers[0].env[?(@.name=="VLOGS_URL")]}{.name}={.value}{"\n"}{end}'
}

echo "当前 context: $(kubectl config current-context)"
echo "请确认是生产集群后再继续！"
read -r -p "按 Enter 继续..."

for m in "${MODULES[@]}"; do
  add_tracing_env "$m"
done

echo ""
echo "=== 验证 ==="
for m in "${MODULES[@]}"; do
  verify "$m"
done

echo ""
echo "完成。建议随后重建 12 个模块镜像以固化仓库配置。"

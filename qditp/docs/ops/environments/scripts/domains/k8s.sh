#!/bin/bash
# ============================================================================
# itp-ops · k8s 域 —— 镜像构建推送（本地 jar → k8s-master → Harbor）
#
# 【本文件是什么】
#   2026-10-25 重构：原 k8s/deploy-to-harbor.sh（单/多服务部署，失败继续）
#   + k8s/batch-deploy.sh（串行全量部署，失败即断）二合一为一个域。
#   命令与输出逐条保留原样；差异只有两处：
#     1. ALL_SERVICES 全量清单收归本文件**单一真源**（旧两个脚本各抄一份，
#         historically MUST 手动保持一致，已多次出现改一边忘另一边）
#     2. deploy / deploy-all 是生产写操作（推 Harbor + 触发远端构建），
#        按统一骨架加 --yes 门；用法其余不变
#
# 【ALL_SERVICES 纪律（MUST）】
#   · 服务名 MUST 是仓库根目录下的模块目录名（不是镜像名、不是 Deployment 名）
#   · 语义 = 可构建推送的模块全集，不是生产部署清单（差异见下方 ALL_SERVICES 处注释，
#     NEVER 按生产部署清单裁剪本清单——那会改变 deploy/deploy-all 的全量语义）
#   · 镜像名 ≠ 模块名：fep-app-server 的镜像名是 itp/fep-app（<image.prefix>），
#     核对推送结果时 MUST 先 grep pom 的 <image.prefix>，NEVER 用目录名拼
#   · web-admin 不适用本脚本（前端，走 zip + nginx Deployment）
#
# 【依赖】lib/core.sh（log_*/die/require_yes/ITP_OPS_ROOT）、lib/remote.sh（REMOTE_HOST）
# ============================================================================

# ---------------------------------------------------------------- 全量清单（单一真源）
# ⚠️ 语义说明（2026-10-25 复核，NEVER 误读）：
#   本清单 = **仓库里可构建推送的模块全集**（根 pom 中有 kubernetes-maven-plugin 的模块目录），
#   不是「生产部署清单」。两者 historically 不相等，差异如下（NEVER 擅自「对齐」任何一边）：
#     · 本清单有、生产不需要：acc-secure-server（死模块）、collect-pay-server（2026-10-13 已下线）、
#       collect-ticket-server（未部署）——构建推送无害，但 deploy-all 全量跑会带着它们；
#     · 生产部署清单（docs/ops/environments/跨环境共性.md §一，原「生产环境清单」已删并并入）
#       还有 web-server(web-admin) / web（前端）——那两个不走 build-push（前端走 zip + nginx
#       Deployment），NEVER 加进本清单；
#     · 跨环境共性.md §一 自身已知不全：缺 card-pool-server / face-pay-server / trans-query-server
#       （这三个线上都在跑，decisions.md 有滚更记录）。以代码（根 pom + deploy/k8s/ YAML）为准。
ALL_SERVICES=(
    # ---- 原清单中经确认存在且可部署的（保持原有相对顺序，勿随意调换）----
    "account-server"
    "acc-secure-server"
    "acc-security-server"
    "acc-es-server"
    "collect-ticket-server"
    "collect-pay-server"
    "pay-sign-server"
    "ticket-server"
    "industry-data-server"
    "fep-app-server"
    "fep-dev-server"
    "gate-txn-pay-server"
    "blacklist-server"
    "key-server"
    "para-server"
    # ---- 2026-09-16 补齐：根 pom 中存在、有 kubernetes-maven-plugin，但原清单遗漏的 ----
    "card-pool-server"
    "face-pay-server"
    "daily-ticket-server"
    "trans-query-server"
    "fep-alipay-server"
    "fep-acc-server"
    "alipay-account-server"
    "alipay-pay-sign-server"
    "recon-server"
)

REMOTE_DIR="${ITP_REMOTE_DIR:-/home/java}"

# ---------------------------------------------------------------- 用法
k8s_usage() {
    cat <<EOF
itp-ops.sh k8s —— 镜像构建推送（本机 jar → ${REMOTE_HOST}:${REMOTE_DIR} → Harbor）

用法: itp-ops.sh k8s <命令> [服务...] [--yes]

命令
  deploy [服务...]              单/多服务部署；不带参数 = 全量 ${#ALL_SERVICES[@]} 个。
                               某个失败继续下一个，末尾汇总（成功/失败/总计）。
  deploy-all [服务...]          串行全量部署；不带参数 = 全量 ${#ALL_SERVICES[@]} 个。
                               ⚠️ 失败即中断后续（依赖有序部署时用这条），末尾带耗时汇总。
  services                      打印全量清单（单一真源，共 ${#ALL_SERVICES[@]} 个）
  help                          本帮助

示例
  itp-ops.sh k8s deploy fep-dev-server --yes        # 只部署一个
  itp-ops.sh k8s deploy acc-es-server ticket-server --yes
  itp-ops.sh k8s deploy-all --yes                   # 有序全量，失败即断
  itp-ops.sh k8s services                           # 看清单

说明
  · deploy / deploy-all 是生产写操作（推 Harbor + 远端 build-push），
    缺 --yes 直接拒绝，与 cloudos 域同一套门。
  · JAR 从 <repo>/<服务>/target/ 取最新可执行包（排除 *-original/-sources/-javadoc.jar），
    版本号从 JAR 文件名提取；target 目录不存在即该服务失败。
  · 远端执行：scp jar → ssh ${REMOTE_HOST} "cd ${REMOTE_DIR} && ./build-push.sh <服务> <版本>"。
  · 判成功看远端输出的 digest: / Pushed，NEVER 只看退出码。
EOF
}

# ---------------------------------------------------------------- 版本号提取（原样保留）
k8s_extract_version() {
    local jar_name="$1"
    local service_name="$2"
    local version="${jar_name%.jar}"
    version="${version#${service_name}-}"
    echo "$version"
}

# ---------------------------------------------------------------- 单服务部署（原 deploy_service）
k8s_deploy_service() {
    local SERVICE_NAME="$1"

    log_step "▶️  处理服务: ${SERVICE_NAME}"

    local TARGET_DIR="${ITP_OPS_ROOT}/${SERVICE_NAME}/target"

    # 1. 检查 target 目录
    if [ ! -d "$TARGET_DIR" ]; then
        log_error "target 目录不存在: $TARGET_DIR"
        return 1
    fi

    # 2. 查找最新 JAR（排除 non-runnable 包；-print0 | xargs -0 防空格路径）
    local LATEST_JAR
    LATEST_JAR=$(find "$TARGET_DIR" -maxdepth 1 -name "${SERVICE_NAME}-*.jar" \
        ! -name "*-original.jar" \
        ! -name "*-sources.jar" \
        ! -name "*-javadoc.jar" \
        -type f -print0 | xargs -0 ls -t 2>/dev/null | head -n 1)

    if [ -z "$LATEST_JAR" ]; then
        log_error "未找到匹配的JAR: ${TARGET_DIR}/${SERVICE_NAME}-*.jar"
        return 1
    fi

    local JAR_FILENAME
    JAR_FILENAME=$(basename "$LATEST_JAR")
    local VERSION
    VERSION=$(k8s_extract_version "$JAR_FILENAME" "$SERVICE_NAME")

    # 3. 版本号安全校验
    if [ -z "$VERSION" ] || [ "$VERSION" = "$JAR_FILENAME" ]; then
        log_error "无法从 ${JAR_FILENAME} 中提取版本号!"
        return 1
    fi

    log_info "📄 JAR文件: ${JAR_FILENAME}"
    log_info "🏷️  版本号:  ${VERSION}"

    # 4. SCP 上传
    log_info "⬆️  正在上传到 ${REMOTE_HOST}:${REMOTE_DIR}/ ..."
    if ! scp "$LATEST_JAR" "${REMOTE_HOST}:${REMOTE_DIR}/"; then
        log_error "上传失败: ${JAR_FILENAME}"
        return 1
    fi

    # 5. SSH 远程构建推送
    # ⚠ HARBOR_PROJECT MUST 随命令行显式传给远端：build-push.sh 写的是
    #   ${HARBOR_PROJECT:-itp}，本机 export 的变量不会自动进远端 shell——
    #   不传则 --env hongdao 会静默推到红岛 Harbor 的 itp 项目（应为 itpb）。
    log_info "🔨 正在远程执行: HARBOR_PROJECT=${HARBOR_PROJECT} ./build-push.sh ${SERVICE_NAME} ${VERSION}"
    if ! remote_exec "cd ${REMOTE_DIR} && HARBOR_PROJECT=${HARBOR_PROJECT} ./build-push.sh ${SERVICE_NAME} ${VERSION}"; then
        log_error "构建推送失败: ${SERVICE_NAME}:${VERSION}"
        return 1
    fi

    log_info "✅ ${SERVICE_NAME}:${VERSION} 部署完成!"
    return 0
}

# ---------------------------------------------------------------- deploy（失败继续 + 汇总）
k8s_deploy() {
    require_yes "k8s deploy（推 Harbor + 远端构建）"

    local DEPLOY_LIST
    if [ $# -eq 0 ]; then
        DEPLOY_LIST=("${ALL_SERVICES[@]}")
        log_info "📦 未指定参数，将部署全部 ${#DEPLOY_LIST[@]} 个服务..."
    else
        DEPLOY_LIST=("$@")
        log_info "📦 指定部署 ${#DEPLOY_LIST[@]} 个服务: ${DEPLOY_LIST[*]}"
    fi

    echo "=========================================="

    local SUCCESS_COUNT=0
    local FAIL_COUNT=0
    local svc

    for svc in "${DEPLOY_LIST[@]}"; do
        echo ""
        if k8s_deploy_service "$svc"; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        else
            log_warn "服务 ${svc} 部署失败，继续下一个..."
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
    done

    echo ""
    echo "=========================================="
    log_info "🎉 部署结束! 成功: ${SUCCESS_COUNT}, 失败: ${FAIL_COUNT}, 总计: ${#DEPLOY_LIST[@]}"
    if [ $FAIL_COUNT -gt 0 ]; then
        log_warn "有 ${FAIL_COUNT} 个服务部署失败，请检查上方日志"
        return 1
    fi
    return 0
}

# ---------------------------------------------------------------- deploy-all（失败即断 + 耗时）
k8s_deploy_all() {
    require_yes "k8s deploy-all（推 Harbor + 远端构建）"

    local SERVICES
    if [ $# -gt 0 ]; then
        SERVICES=("$@")
        local MODE="指定部署"
    else
        SERVICES=("${ALL_SERVICES[@]}")
        local MODE="全量部署(预定义列表)"
    fi

    local TOTAL=${#SERVICES[@]}
    local SUCCESS_COUNT=0
    local FAIL_COUNT=0
    local FAILED_LIST=""

    echo -e "${C_BLUE}=========================================="
    echo -e "🚀 开始串行部署 | 模式: ${MODE}"
    echo -e "📋 服务列表(${TOTAL}): ${SERVICES[*]}"
    echo -e "==========================================${C_NC}\n"

    local START_TIME
    START_TIME=$(date +%s)

    local SERVICE
    for SERVICE in "${SERVICES[@]}"; do
        echo -e "${C_YELLOW}------------------------------------------${C_NC}"
        echo -e "${C_BLUE}[STEP] ▶️  (${SUCCESS_COUNT}/${TOTAL}) 正在部署: ${SERVICE}${C_NC}"
        echo -e "${C_YELLOW}------------------------------------------${C_NC}"

        if k8s_deploy_service "$SERVICE"; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            echo -e "\n${C_GREEN}[INFO] ✅ ${SERVICE} 部署成功!${C_NC}\n"
        else
            FAIL_COUNT=$((FAIL_COUNT + 1))
            FAILED_LIST="${FAILED_LIST} ${SERVICE}"
            echo -e "\n${C_RED}[ERROR] ❌ ${SERVICE} 部署失败! 终止后续部署.${C_NC}\n"
            break
        fi
    done

    local END_TIME ELAPSED
    END_TIME=$(date +%s)
    ELAPSED=$((END_TIME - START_TIME))

    echo -e "${C_BLUE}=========================================="
    echo -e "📊 部署汇总报告"
    echo -e "==========================================${C_NC}"
    echo -e "⏱️  总耗时: ${ELAPSED}s"
    echo -e "✅ 成功: ${C_GREEN}${SUCCESS_COUNT}${C_NC}"
    echo -e "❌ 失败: ${C_RED}${FAIL_COUNT}${C_NC}"
    echo -e "📦 总计: ${TOTAL}"

    if [ $FAIL_COUNT -gt 0 ]; then
        echo -e "\n${C_RED}⚠️  失败的服务:${FAILED_LIST}${C_NC}"
        echo -e "${C_YELLOW}💡 修复后可单独重跑: itp-ops.sh k8s deploy${FAILED_LIST} --yes${C_NC}"
        return 1
    else
        echo -e "\n${C_GREEN}🎉 所有服务串行部署完成!${C_NC}"
        return 0
    fi
}

# ---------------------------------------------------------------- 域入口
k8s_main() {
    need_cmd ssh scp
    # 默认 test，且本域实际只对测试可用：红岛跳板 hd-linux 上既无 /home/java 也无
    # build-push.sh（2026-10-26 实测，远端只有 podman 没有 docker）——红岛发版走
    # `itp-ops.sh cloudos image build`（平台 x86 节点打镜像），NEVER 用本域打红岛。
    itp_env_init test || return 1
    local cmd="${1:-}"; shift || true
    case "$cmd" in
        ""|help|-h|--help) k8s_usage; return 0 ;;
        services)
            printf '%s\n' "${ALL_SERVICES[@]}"
            return 0 ;;
        deploy)
            itp_env_banner "推送到 ${REMOTE_HOST}:${REMOTE_DIR}（Harbor 项目 ${HARBOR_PROJECT}）"
            k8s_deploy "$@" ;;
        deploy-all)
            itp_env_banner "全量推送到 ${REMOTE_HOST}:${REMOTE_DIR}（Harbor 项目 ${HARBOR_PROJECT}）"
            k8s_deploy_all "$@" ;;
        *)
            echo "未知命令：${cmd}" >&2
            k8s_usage
            return 1 ;;
    esac
}

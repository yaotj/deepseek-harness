#!/bin/bash
# ============================================================================
# itp-ops · lib/env.sh —— 环境 profile 单一真源
#
# 【本文件是什么】
#   2026-10-26 分环境重构：把散在各域脚本里的环境相关值（跳板主机、K8s 命名空间、
#   数据库 DSN、Harbor 项目、日志地址）收敛到一处，按环境一次性导出。
#
# 【为什么需要】
#   重构前只有 cloudos 域支持 --env，其余域把「测试环境」写死在默认值里：
#     - remote.sh:21  REMOTE_HOST=k8s-master（测试跳板）
#     - log.sh:31     WATCH_NS=itp（测试命名空间）—— 红岛跑 log watch 会静默 watch 错 ns
#     - diag.sh       reach 纯红岛（14 处 172.120）/ routes 纯测试（ns itp），却无环境提示
#   用户执行时无从得知自己在打哪个环境，红岛侧命令还会静默打到测试。
#
# 【用法】
#   itp_env_init <本域的默认环境>      # hongdao | test
#   --env 由入口全局解析后写入 ITP_ENV；未指定时用调用方传入的默认。
#   ⚠ 各域默认环境刻意不统一（见下），NEVER 擅自改成一致——那会让原本能用的命令失效：
#     cloudos → hongdao（红岛是主战场，且默认如此已久）
#     k8s/log → test（k8s 域只对测试可用——红岛跳板 hd-linux 无 /home/java 构建目录，
#               红岛发版走 cloudos image 由平台 x86 节点打镜像；log 域红岛可 watch，
#               tail 依赖的远端脚本红岛尚未部署）
#     diag    → 按子命令：reach=hongdao、routes=test
#     analysis → 不调用（环境无关）
#
# 【变量】所有值都可被同名环境变量覆盖（${VAR:-默认} 形式），优先级：
#   命令行环境变量 > profile 默认值
# ============================================================================

# 环境中文名（供横幅/提示使用）
ITP_ENV_LABEL=""

# 按优先级设值：显式 --env（ITP_ENV_EXPLICIT=1）或直接命令行环境变量 > profile 默认值。
# 为什么需要：REMOTE_HOST / HARBOR_PROJECT 在 lib/remote.sh、domains/cloudos.sh 被 source 时
# 就已定死为测试值（k8s-master / itp），`${VAR:-默认}` 写法覆盖不掉它们 ——
# 于是 --env hongdao 会出现「说是红岛、跳板还连测试」的错配。显式指定环境时 MUST 强制覆盖。
_env_apply() {
    local var="$1" val="$2"
    if [[ -n "${ITP_ENV_EXPLICIT:-}" ]]; then
        printf -v "$var" '%s' "$val"
    else
        printf -v "$var" '%s' "${!var:-$val}"
    fi
}

itp_env_init() {
    local default_env="$1"
    local env="${ITP_ENV:-$default_env}"

    case "$env" in
        hongdao)
            ITP_ENV_LABEL="红岛生产"
            # 跳板：红岛 K8s 控制区 hd-linux（172.120.2.13）
            _env_apply REMOTE_HOST "hd-linux"
            _env_apply REMOTE_DIR "/home/java"
            K8S_NS="${K8S_NS:-itpb}"
            # Harbor 项目：红岛是 itpb（测试是 itp），推送账号 itpadmin
            _env_apply HARBOR_PROJECT "itpb"
            # CloudOS
            _env_apply CLOUDOS_ENV "hongdao"
            # 数据库：源=老库（测试网段），目标=红岛 PDB
            _env_apply SRC_DB_DSN "172.20.222.3:1521/AFCITPDB"
            _env_apply TGT_DB_DSN "172.120.110.25:1521/hditpdb"
            _env_apply TGT_DB_PDB "HDTPDB"
            # VictoriaLogs（红岛 172.120.2.13:31920；NEVER 沿用测试值）
            _env_apply VL_URL "http://172.120.2.13:31920"
            # 红岛公网入向入口
            _env_apply INGRESS_ENTRY "172.120.2.26:48000"
            ;;
        test)
            ITP_ENV_LABEL="测试环境"
            _env_apply REMOTE_HOST "k8s-master"
            _env_apply REMOTE_DIR "/home/java"
            K8S_NS="${K8S_NS:-itp}"
            _env_apply HARBOR_PROJECT "itp"
            _env_apply CLOUDOS_ENV "test"
            _env_apply SRC_DB_DSN "172.20.222.3:1521/AFCITPDB"
            _env_apply TGT_DB_DSN "172.20.222.3:1521/AFCITPDB"
            _env_apply TGT_DB_PDB "AFCITPDB"
            _env_apply VL_URL "http://172.20.211.23:30032"
            _env_apply INGRESS_ENTRY "58.56.166.170:48000"
            ;;
        *)
            printf '错误：未知环境 %s（仅 hongdao / test）\n' "$env" >&2
            return 1
            ;;
    esac

    export ITP_ENV="$env"
    export REMOTE_HOST REMOTE_DIR K8S_NS HARBOR_PROJECT CLOUDOS_ENV
    export SRC_DB_DSN TGT_DB_DSN TGT_DB_PDB VL_URL INGRESS_ENTRY
    return 0
}

# 打印环境横幅。MUST 在每个会打到远端/集群的命令前调用一次——
# 让用户一眼看到「我现在打的是哪个环境」，NEVER 让人猜。
itp_env_banner() {
    local what="$1"
    printf '[环境] %s（%s）  跳板=%s  K8s命名空间=%s  Harbor项目=%s\n' \
        "$ITP_ENV_LABEL" "$ITP_ENV" "$REMOTE_HOST" "$K8S_NS" "$HARBOR_PROJECT" >&2
    if [[ -n "$what" ]]; then
        printf '[环境] 目标：%s\n' "$what" >&2
    fi
}

#!/bin/bash
# ============================================================================
# itp-ops · lib/version.sh —— 升级版本号规则（前端/后端统一，2026-11-02 裁定）
#
# 【规则（用户裁定，全平台唯一口径）】
#   1. 本地构建只升末位 +0.0.1：即「最后一分段 +1」。
#   2. 任一分段超过 99 则向高位进位：该分段归零、前一分段 +1
#      （语义："超过 99 则升 0.1.0"）。例：1.2.99 → 1.3.0；1.99.99 → 2.0.0。
#   3. CloudOS 中仅为调试而升级的包，版本固定哨兵 0.0.0.1（不占用发布序列、
#      与真实发版号区分，便于事后清理）。
#
# 【适用】
#   前端 web（package.json / web-frontend-<v>.zip / Deployment JAR_NAME）
#   与后端各微服务（pom version / jar 名 / 镜像 tag）统一口径。
#
# 【bash 3.2 兼容】NEVER 用 declare -A / mapfile；分段拆解用 IFS + 位置参数。
# ============================================================================

# 校验版本号：仅含数字与点，至少 1 段；无前导/尾随/连续点，无非法字符。
# 用 case glob 而非 =~：避免依赖 zsh/regex 模块，bash 3.2 与 zsh 均可原生工作。
itp_version_valid() {
    local v="$1"
    case "$v" in
        ""|.|*.*.|*.[!0-9]*|*[!0-9.]*|.*|*.) return 1 ;;
        *) return 0 ;;
    esac
}

# 末位 +1，段上限 99，超限向高位进位（该段归零、前段 +1）。
# 用法：itp_next_version <当前版本>  →  stdout 打印下一版本；非法版本号走 stderr 并返 1。
# 实现：纯字符串从右向左分段（${v##*.} / ${v%.*}），不依赖数组下标——
#   避开 zsh 1 号索引与 bash 0 号索引的差异，bash 3.2 与 zsh 行为一致。
itp_next_version() {
    local v="$1"
    itp_version_valid "$v" || { echo "非法版本号: $v" >&2; return 1; }

    local rest="$v" out="" carry=1 seg
    while :; do
        # 取末段；rest 同步砍掉末段
        if [[ "$rest" == *.* ]]; then
            seg="${rest##*.}"; rest="${rest%.*}"
        else
            seg="$rest"; rest=""
        fi
        # 仅当低位有进位时才 +1；超 99 则本段归零、进位继续向高位传递
        if (( carry == 1 )); then
            local s=$(( seg + 1 ))
            if (( s > 99 )); then seg=0; carry=1
            else seg=$s; carry=0; fi
        fi
        # 把当前段拼到结果最前
        if [[ -z "$out" ]]; then out="$seg"; else out="${seg}.${out}"; fi
        [[ -z "$rest" ]] && break
    done
    # 最高段仍溢出（如 99.99）：补一个最高段 1，其余已归零
    if (( carry == 1 )); then out="1.${out}"; fi
    printf '%s\n' "$out"
}

# 调试包哨兵版本（CloudOS 仅为调试而升级时使用，不占用发布序列）。
itp_debug_version() {
    printf '0.0.0.1\n'
}

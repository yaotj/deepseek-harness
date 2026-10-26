#!/bin/bash
# ============================================================================
# CloudOS 认证 —— 单点实现（RSA 加密 + 登录 + token 缓存）
#
# 【为什么要有这个文件】
#   历史上 cloudos-api.sh / cloudos-hongdao.sh / image-build.sh 三个入口各自抄了一份
#   RSA 加密与登录逻辑（约 200 行重复），且 token 缓存格式不一致、共用同一个文件
#   `/tmp/.cloudos-hongdao.token`：hongdao 存 `<时间戳> <token>`，image-build 旧版
#   只存 token 靠 mtime，谁先登录另一个就读错。2026-10-25 重构后全仓只有一个入口
#   `itp-ops.sh`，认证统一走本文件；token 缓存按环境分文件，彻底消除串环境。
#
# 【环境 profile（cloudos_env_init 时应用）】
#   hongdao（默认，红岛生产）：172.120.209.151 / itpbadmin / 公钥已固化
#   test（测试）：172.20.217.210 / admin / 公钥 MUST 从测试登录页抓后填入
#   两者 VIP、账号、RSA 公钥全不同 —— 混用就是把生产命令打到测试或反之。
#
# 【调用约定】
#   入口 MUST 先调 cloudos_env_init（按 CLOUDOS_ENV 应用 profile），再发请求。
#   环境变量可覆盖 profile 的任何值：CLOUDOS_HOST / CLOUDOS_USERNAME /
#   CLOUDOS_PASSWORD / CLOUDOS_DOMAIN / CLOUDOS_TIMEOUT / CLOUDOS_RSA_PUBKEY_B64 /
#   TOKEN_CACHE。入口 SHOULD `trap cloudos_auth_cleanup EXIT`（清公钥临时文件）。
#   lib 本身 NEVER 设置 shell 选项（set -euo），由入口决定。
#
# 【认证模型（红岛 2026-10-23 实测）】
#   POST /api/sys/oapi/v1/double_factor/login
#   Body: {"username","rsa":true,"password":"<RSA-1024 密文 base64>","domain":"default"}
#   业务请求头：X-Auth-Token: <res.token>
#   ⚠ 密码 MUST 真 RSA 加密（密文恒 128 字节 / 172 字符）。base64 明文会返回
#     usrmgt.userOrPasswordError —— 看着像密码错，实际是没解密。
#   ⚠ 账号真值 itpbadmin（不是 itpbdmin / itpadmin；后者是 Harbor 推送账号）。
#   ⚠ RSA 公钥来源与抓法见 scripts 根 README.md「CloudOS 认证模型」。
# ============================================================================

# 非环境相关默认（环境相关项在 cloudos_env_init 里按 profile 设）
CLOUDOS_SCHEME="${CLOUDOS_SCHEME:-https}"
CLOUDOS_DOMAIN="${CLOUDOS_DOMAIN:-default}"
CLOUDOS_TIMEOUT="${CLOUDOS_TIMEOUT:-30}"
TOKEN_TTL="${CLOUDOS_TOKEN_TTL:-600}"   # 秒；平台侧实际更长，这里保守取 10 分钟

# 红岛 RSA 公钥（1024 bit，来自红岛登录页 encryptRSA()，PKCS#8 SubjectPublicKeyInfo base64）
HONGDAO_RSA_PUBKEY_B64="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCbPqaFZ0lgebihxCw5wzjkahPKr6ZI2JNTHbHDnNbOn3Cif6GgnMBr44MhQCRPQTF2FgcxBbn3u7eGcEPMT6OZqcLzcmiMCUQRJ2MUbFI+E1vI1f+iuCQ2w8XZhRoRrmb7wBA0L63gnSwXRkJD0baL5zqlQhpSKfSH0t3opAoahwIDAQAB"

# 应用环境 profile。MUST 在发任何请求前调用；重复调用安全（幂等）。
# 测试环境公钥未抓到（本机长期不可达 172.20.217.210），留空即拒跑并提示抓法——
# 绝不静默退回 base64 明文（2026-10-22 的错判：看着能跑，实际从未通）。
cloudos_env_init() {
    local env="${CLOUDOS_ENV:-hongdao}"
    case "$env" in
        hongdao)
            CLOUDOS_HOST="${CLOUDOS_HOST:-172.120.209.151}"
            CLOUDOS_USERNAME="${CLOUDOS_USERNAME:-itpbadmin}"
            CLOUDOS_PASSWORD="${CLOUDOS_PASSWORD:-QDitp@123!@#}"
            CLOUDOS_RSA_PUBKEY_B64="${CLOUDOS_RSA_PUBKEY_B64:-$HONGDAO_RSA_PUBKEY_B64}"
            ;;
        test)
            CLOUDOS_HOST="${CLOUDOS_HOST:-172.20.217.210}"
            CLOUDOS_USERNAME="${CLOUDOS_USERNAME:-admin}"
            CLOUDOS_PASSWORD="${CLOUDOS_PASSWORD:-Qdmlc123!@#}"
            CLOUDOS_RSA_PUBKEY_B64="${CLOUDOS_RSA_PUBKEY_B64:-}"
            if [[ -z "$CLOUDOS_RSA_PUBKEY_B64" ]]; then
                printf '错误：测试环境 RSA 公钥未配置。\n' >&2
                printf '  本机到 172.20.217.210 长期不可达，公钥一直没抓到。\n' >&2
                printf '  拿到能通测试网的机器后：登录页 F12 → Network → double_factor/login\n' >&2
                printf '  → 前端 JS 搜 encryptRSA / setPublicKey，取 1024-bit PKCS#8 base64，\n' >&2
                printf '   export CLOUDOS_RSA_PUBKEY_B64=<值> 后重试。NEVER 改回 base64 明文。\n' >&2
                return 1
            fi
            ;;
        *)
            printf '错误：未知 CLOUDOS_ENV=%s（仅 hongdao / test）\n' "$env" >&2
            return 1
            ;;
    esac

    # 口令：仓库内明文即唯一真源（2026-10-26 用户裁定）。
    # 各环境的现值直接写在上方 case 分支的默认值里，CLOUDOS_PASSWORD 环境变量仅作**可选覆盖**
    # （临时改密 / 试别的账号时用），不设也能跑——不再强制要求外部提供。
    # 2026-10-26 审计曾把这两个默认值清空并改成「必须由环境变量提供、缺失即退出」，
    # 现按裁定回填：口径反转后，去运维「现取口令」是多余动作，仓库才是真源。
    if [[ -z "$CLOUDOS_PASSWORD" ]]; then
        printf '错误：CLOUDOS_PASSWORD 被显式置空（环境 %s）。\n' "$env" >&2
        printf '  仓库内已有该环境的现值，NEVER 需要手工提供——把 export CLOUDOS_PASSWORD= 去掉即可。\n' >&2
        return 1
    fi

    CLOUDOS_BASE_URL="${CLOUDOS_SCHEME}://${CLOUDOS_HOST}"
    # token 缓存按环境分文件：彻底消除旧版「两个环境共用一个缓存文件」的串号风险
    TOKEN_CACHE="${TOKEN_CACHE:-${TMPDIR:-/tmp}/.cloudos-${env}.token}"
    return 0
}

__cloudos_die() { printf '错误：%s\n' "$*" >&2; exit 1; }

# 单行 base64：接受参数或 stdin 两种用法。
# 用 openssl 而不是 base64 -w0 —— BSD/macOS 的 base64 没有 -w0；且
# `base64 -w0 || base64` 有坑：第一个已把 stdin 读空，fallback 拿到空输入。
cloudos_b64_noline() {
    if [[ $# -gt 0 ]]; then
        printf '%s' "$1" | openssl base64 -A
    else
        openssl base64 -A
    fi
}

# 公钥 PEM 落到临时文件（openssl -inkey 只吃文件）。
# MUST 用全局变量 + 入口 trap，不能写成 $(prepare_pubkey) —— 命令替换在子 shell
# 里退出，trap EXIT 立刻删文件，openssl 随后报 No such file。
CLOUDOS_PUB_PEM_FILE="${CLOUDOS_PUB_PEM_FILE:-${TMPDIR:-/tmp}/cloudos-pub-$$.pem}"

cloudos_prepare_pubkey() {
    [[ -s "$CLOUDOS_PUB_PEM_FILE" ]] && return 0
    printf -- '-----BEGIN PUBLIC KEY-----\n%s\n-----END PUBLIC KEY-----\n' \
        "$CLOUDOS_RSA_PUBKEY_B64" > "$CLOUDOS_PUB_PEM_FILE" \
        || __cloudos_die "无法写入公钥临时文件 ${CLOUDOS_PUB_PEM_FILE}"
}

cloudos_auth_cleanup() {
    [[ -n "${CLOUDOS_PUB_PEM_FILE:-}" ]] && rm -f "$CLOUDOS_PUB_PEM_FILE"
    return 0
}

# 明文 -> PKCS#1 v1.5 -> base64（密文恒 128 字节 / 172 字符）
cloudos_rsa_encrypt() {
    cloudos_prepare_pubkey
    printf '%s' "$1" \
        | openssl pkeyutl -encrypt -pubin -inkey "$CLOUDOS_PUB_PEM_FILE" -pkeyopt rsa_padding_mode:pkcs1 \
        | cloudos_b64_noline
}

# 内部：真发登录请求，成功打印 token
_cloudos_login_once() {
    local enc body resp http_code rbody token
    enc=$(cloudos_rsa_encrypt "$CLOUDOS_PASSWORD") || __cloudos_die "RSA 加密失败（openssl 不可用？）"

    # 长度不对即公钥或填充不对；不拦死只告警，因为平台升级换公钥时仍值得先看真实报错。
    [[ ${#enc} -eq 172 ]] || \
        printf '警告：RSA 密文长度 %s（预期 172），公钥可能是 2048 位或填充方式不符\n' "${#enc}" >&2

    body=$(printf '{"username":"%s","rsa":true,"password":"%s","domain":"%s"}' \
        "$CLOUDOS_USERNAME" "$enc" "$CLOUDOS_DOMAIN")

    resp=$(curl -sSk --max-time "$CLOUDOS_TIMEOUT" -w "\n%{http_code}" \
        -H "Content-Type: application/json" -H "Accept: application/json" \
        -X POST -d "$body" "${CLOUDOS_BASE_URL}/api/sys/oapi/v1/double_factor/login")

    http_code=$(printf '%s' "$resp" | tail -n1)
    rbody=$(printf '%s' "$resp" | sed '$d')

    [[ -n "$http_code" && "$http_code" != "000" ]] \
        || __cloudos_die "连不上 ${CLOUDOS_HOST} —— 查网卡（红岛需插 K8s 控制区 172.120.2.x）"
    [[ "$http_code" == "200" ]] || __cloudos_die "登录 HTTP ${http_code}：${rbody:0:400}"

    token=$(printf '%s' "$rbody" | python3 -c '
import sys, json
d = json.load(sys.stdin)
if d.get("status") is not True:
    sys.stderr.write("登录业务失败：code=%s msg=%s\n" % (d.get("code"), d.get("msg")))
    if d.get("code") == "usrmgt.userOrPasswordError":
        sys.stderr.write(
            "  排查顺序：① 账号是否 itpbadmin（不是 itpbdmin / itpadmin）\n"
            "           ② 密码是否正确\n"
            "           ③ 密码是否真的 RSA 加密（密文应 172 字符；base64 明文会走到这条错）\n"
            "           ④ domain 是否为 default（传空返回 svcmgt.internalServerError）\n")
    sys.exit(1)
print((d.get("res") or {}).get("token") or "")
' 2>&1) || __cloudos_die "$token"

    [[ -n "$token" ]] || __cloudos_die "登录未返回 token：${rbody:0:300}"
    printf '%s' "$token"
}

# 取 token：优先缓存，过期/不存在则重登。`--fresh` 强制重登。
cloudos_get_token() {
    local fresh="${1:-}"

    if [[ "$fresh" != "--fresh" && -f "$TOKEN_CACHE" ]]; then
        local ts now age
        ts=$(cut -d' ' -f1 "$TOKEN_CACHE" 2>/dev/null || echo 0)
        now=$(date +%s)
        age=$(( now - ts ))
        if [[ "$age" -ge 0 && "$age" -lt "$TOKEN_TTL" ]]; then
            local cached
            cached=$(cut -d' ' -f2- "$TOKEN_CACHE")
            # 旧格式（无时间戳前缀）会读成空或整串，一律当失效处理并重登
            if [[ -n "$cached" && "$cached" != *" "* ]]; then
                printf '%s' "$cached"
                return 0
            fi
        fi
    fi

    local token
    token=$(_cloudos_login_once)
    # MUST 显式判返回码：lib 不设 set -e，命令替换在子 shell 里 exit 1 不会中断父 shell，
    # 若不判就会拿空 token 继续请求 —— 表现为一串 401/业务错误，掩盖真正的登录失败。
    if [[ $? -ne 0 || -z "$token" ]]; then
        return 1
    fi

    mkdir -p "$(dirname "$TOKEN_CACHE")"
    ( umask 077; printf '%s %s' "$(date +%s)" "$token" > "$TOKEN_CACHE" )
    printf '%s' "$token"
}

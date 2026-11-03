"""cloudos 子包 —— lib/cloudos-{auth,http,image,pkg}.sh 的 Python 移植。

子模块（与旧 lib/*.sh 一一对应）：
  auth.py   CloudOS 登录 / RSA 加密 / token 缓存（cloudos-auth.sh）
  http.py   HTTP 通道：JSON / multipart 上传 / 原始流（cloudos-http.sh）
  image.py  镜像仓库 API（cloudos-image.sh）
  pkg.py    软件中心应用包 API（cloudos-pkg.sh）

【旧 bash 的坑/巧思（移植时保留的原因）】
  · token 缓存 `<时间戳> <token>` 按环境分文件（${TMPDIR:-/tmp}/.cloudos-<env>.token），
    新旧两版可共用同一缓存；旧格式（无时间戳前缀）一律当失效重登。
    —— 保留：避免新旧工具交替使用时反复登录，且格式校验逻辑等价。
  · RSA 密码加密 MUST 真加密（PKCS#1 v1.5，密文恒 128 字节 / 172 字符）；
    base64 明文会报 usrmgt.userOrPasswordError（看着像密码错，实际是没解密）。
    —— 保留：错误分支与提示语原样照搬。
  · 登录响应解析失败时，bash 把 python traceback 捕获后当错误信息 die（2>&1 技巧）。
    Python 版直接抛 OpsError，提示语更干净（见 auth.login_once 的差异注）。
  · CLOUDOS_HOST / USERNAME / PASSWORD / DOMAIN / TIMEOUT / RSA_PUBKEY_B64 /
    TOKEN_CACHE / IMG_BASE_URL 环境变量可覆盖 profile 值（与 bash `${VAR:-默认}`
    同语义：设为空也算「未设」，回落默认）。
  · curl 的 -k（跳过证书校验）在 Python 版以 ssl 未校验 context 等价复刻。

【与 bash 版的差异】（原则上零差异；以下是唯一差异，均有理由）
  1. 失败不再 exit 1，而是抛 OpsError 带细分退出码（2 用法 / 3 前置 / 4 传输 /
     5 执行 / 6 缺 --yes）——这是 itpops 契约（errors.py）的硬性要求；
     stderr 文案主体保持一致（cli 统一加 `[ERROR] 错误：` 前缀）。
  2. bash 的 `die`/`log_info` 混用 stdout/stderr；Python 版严格按契约：
     结果文本 → ui.out（stdout，--json 时抑制），日志/横幅 → stderr。
     凡旧脚本打到 stdout 的结果行，移植后仍走 ui.out，stdout 字节兼容。
  3. RSA/指纹用 openssl 子进程（与 bash 同一工具、同一填充），失败报
     「RSA 加密失败（openssl 不可用？）」；无第三方 Python 依赖。
"""

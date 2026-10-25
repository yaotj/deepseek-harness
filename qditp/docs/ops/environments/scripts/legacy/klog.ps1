# ==========================================
# Windows 端 K8s 日志快捷查看代理脚本
# （对标 Mac 版 klog.sh）
# 建议文件以 UTF-8 with BOM 保存，避免 Windows PowerShell 5.1 中文/Emoji 乱码
# ==========================================

$RemoteHost   = "k8s-master"
$RemoteScript = "/home/java/k8s-logview.sh"

# ---------- 帮助信息 (包含多种传参示例) ----------
function Show-Help {
    Write-Host @"
🚀 K8s 日志远程快捷查看工具 (Windows 版)

用法: .\klog.ps1 <服务名或JAR前缀> [关键字] [-f] [行数]

📌 传参示例:
  .\klog.ps1 fep-app                      # 查看最近 100 行日志
  .\klog.ps1 fep-app 500                  # 查看最近 500 行日志
  .\klog.ps1 fep-app ERROR                # 搜索包含 ERROR 的最近 100 行
  .\klog.ps1 fep-app ERROR 200            # 搜索包含 ERROR 的最近 200 行
  .\klog.ps1 fep-app -f                   # ⭐ 实时跟踪日志 (Ctrl+C 退出)
  .\klog.ps1 fep-app Exception -f         # ⭐ 实时跟踪包含 Exception 的日志
  .\klog.ps1 fep-app "user login" -f      # ⭐ 实时跟踪带空格的关键字
  .\klog.ps1 fep-app -2000f               # ⭐ 查看最近 2000 行历史日志
  .\klog.ps1 fep-app ERROR Exception -2000f # ⭐ 多关键字过滤 + 历史日志
  `$env:KLOG_CONTAINER="nginx"; .\klog.ps1 gateway # 指定多容器 Pod 中的特定容器

💡 提示:
  - 参数顺序不敏感，脚本会自动识别数字、-f 和关键字
  - 实时跟踪模式下按 Ctrl+C 安全退出
  - KLOG_CONTAINER 透传需本地 ssh_config 配置 SendEnv、远端 sshd 配置 AcceptEnv
"@
    exit 0
}

# 无参数或请求帮助时显示用法
if ($args.Count -eq 0 -or $args[0] -in @('-h', '--help', '/?')) {
    Show-Help
}

# ---------- 核心执行逻辑 ----------
# 对所有参数做单引号安全转义（等价 bash 的 printf %q）
# 防止特殊字符(引号/空格/$等)在 SSH 传输时被远程 Shell 二次解析
$SafeArgs = ($args | ForEach-Object {
    "'" + ($_ -replace "'", "'\''") + "'"
}) -join " "

# -t : 强制分配伪终端(TTY)
#      这是实时跟踪(-f)正常工作的关键:
#      1. tail -f 需要 TTY 才能正确响应 Ctrl+C
#      2. 远程脚本的颜色输出(\033[xxm)需要 TTY 才能渲染
#      3. grep --line-buffered 需要 TTY 才能实现逐行刷新
ssh -t $RemoteHost "bash '$RemoteScript' $SafeArgs"
exit $LASTEXITCODE

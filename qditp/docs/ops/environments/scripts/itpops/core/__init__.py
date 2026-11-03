"""itpops.core —— 公共骨架（日志/上下文、版本算法、环境 profile、远端执行、依赖检查）。

与旧 lib/*.sh 一一对应：
  ui.py      ← lib/core.sh 的日志原语 + 全局上下文 Ctx
  version.py ← lib/version.sh（升级版本号规则，2026-11-02 裁定）
  envx.py    ← lib/env.sh（环境 profile 单一真源）
  remote.py  ← lib/remote.sh（ssh 跳板助手）
  execx.py   ← lib/core.sh 的 require_yes / need_cmd / 仓库根断言
"""

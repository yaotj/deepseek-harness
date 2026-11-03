"""退出码与异常契约（给大模型的稳定机器接口，人看 stderr 文本）。

退出码（全命令统一）：
  0  成功
  1  未分类错误（尽量别用，补细分码）
  2  用法错误（参数不对 / 未知域 / 未知子命令）
  3  前置不满足（缺依赖命令、仓库根断言失败、cloudos health 不通过、环境未知）
  4  远端/传输失败（ssh 不可达、HTTP 连不上）
  5  命令执行失败（远端命令非 0、业务拒绝、解析失败）
  6  写操作缺 --yes 被拒
"""
from __future__ import annotations

EXIT_OK = 0
EXIT_GENERIC = 1
EXIT_USAGE = 2
EXIT_PRECONDITION = 3
EXIT_TRANSPORT = 4
EXIT_EXEC = 5
EXIT_NEED_YES = 6

CODE_BY_EXIT = {
    EXIT_GENERIC: "GENERIC",
    EXIT_USAGE: "USAGE",
    EXIT_PRECONDITION: "PRECONDITION",
    EXIT_TRANSPORT: "TRANSPORT",
    EXIT_EXEC: "EXEC",
    EXIT_NEED_YES: "NEED_YES",
}


class OpsError(Exception):
    """域内业务失败。携带退出码；message 给人看，code 给机器看。"""

    def __init__(self, message: str, exit_code: int = EXIT_EXEC, code: str | None = None):
        super().__init__(message)
        self.exit_code = exit_code
        self.code = code or CODE_BY_EXIT.get(exit_code, "GENERIC")

    def to_json(self) -> dict:
        return {"code": self.code, "message": str(self), "exit": self.exit_code}

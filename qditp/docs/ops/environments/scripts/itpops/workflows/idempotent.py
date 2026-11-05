"""幂等性注解与断点续跑机制。

设计目标：
  · Step 增加 @idempotent 注解，标记该步骤可安全重入
  · runner 记录已完成步骤，失败后可从断点继续
  · 已完成的 idempotent 步骤跳过执行，直接标记成功

使用示例：
  from .runner import Step, idempotent

  @idempotent
  def check_version(ctx, module):
      current = get_current_version(ctx.env, module)
      if current == ctx.target_version:
          ctx.skip("版本已是目标值，跳过")
      else:
          bump_pom(module, ctx.target_version)

注意：当前 runner.py 尚未集成此机制，仅定义接口。
实际落地需在 runner.run() 中增加 done_steps 追踪逻辑。
"""
from __future__ import annotations

from functools import wraps
from typing import Callable, Any


def idempotent(func: Callable) -> Callable:
    """标记函数为幂等操作（可安全重入）。

    幂等操作特性：
      · 多次执行结果相同
      · 失败后可安全重试
      · 已完成的步骤应跳过

    使用方式：
      @idempotent
      def my_step(ctx, **kwargs):
          ...
    """
    func._idempotent = True  # type: ignore
    return func


def is_idempotent(func: Callable) -> bool:
    """检查函数是否标记为幂等。"""
    return getattr(func, "_idempotent", False)


class StepState:
    """步骤执行状态。"""

    def __init__(self, title: str, index: int) -> None:
        self.title = title
        self.index = index
        self.done = False
        self.skipped = False
        self.error: str | None = None

    def mark_done(self) -> None:
        self.done = True
        self.skipped = False

    def mark_skipped(self, reason: str) -> None:
        self.skipped = True
        self.done = True

    def mark_error(self, error: str) -> None:
        self.error = error
        self.done = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "title": self.title,
            "index": self.index,
            "done": self.done,
            "skipped": self.skipped,
            "error": self.error,
        }


class ExecutionCheckpoint:
    """执行检查点：记录已完成步骤，支持断点续跑。"""

    def __init__(self, flow_name: str) -> None:
        self.flow_name = flow_name
        self.steps: dict[int, StepState] = {}

    def add_step(self, title: str, index: int, idempotent: bool = False) -> StepState:
        state = StepState(title, index)
        state._idempotent = idempotent  # type: ignore
        self.steps[index] = state
        return state

    def get_done_steps(self) -> list[int]:
        """返回已完成步骤的索引列表。"""
        return [i for i, s in self.steps.items() if s.done]

    def get_skipped_steps(self) -> list[int]:
        """返回被跳过的步骤索引列表。"""
        return [i for i, s in self.steps.items() if s.skipped]

    def next_unfinished(self) -> int | None:
        """返回下一个未完成的步骤索引。"""
        for i in sorted(self.steps.keys()):
            if not self.steps[i].done:
                return i
        return None

    def to_dict(self) -> dict[str, Any]:
        return {
            "flow": self.flow_name,
            "steps": {str(k): v.to_dict() for k, v in self.steps.items()},
        }


# 当前 runner.py 尚未集成此机制，以下为未来实现要点：
#
# def run(flow, variables, checkpoint=None):
#     if checkpoint is None:
#         checkpoint = ExecutionCheckpoint(flow.name)
#
#     skip_indices = checkpoint.get_done_steps()
#     for i, step in enumerate(flow.steps):
#         if i in skip_indices:
#             if getattr(step, '_idempotent', False):
#                 print(f"[SKIP] 步骤 {i+1} 已完成（幂等），跳过")
#                 continue
#         # 正常执行逻辑...
#
# 实际落地需要在 Step dataclass 中增加 idempotent 字段，
# 并在 runner.run() 中传入 checkpoint 参数。

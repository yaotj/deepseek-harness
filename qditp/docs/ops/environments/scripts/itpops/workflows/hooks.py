"""模块级 hook 机制 —— 允许特定模块在 workflow 步骤前后执行自定义脚本。

设计边界：
  · hook 文件位于模块根目录 `itp-hook.sh`，MUST 是 POSIX shell 脚本
  · 支持的 hook 点：pre_build（出 jar 前）、post_build（出 jar 后）、pre_deploy（部署前）
  · hook 失败不会阻断主流程（只打印 WARN），除非 hook 显式 exit 非 0 且设置 HOOK_STRICT=1
  · hook 通过 stdin 接收 JSON 参数：{"module": "face-pay-server", "step": "pre_build", "env": "test"}
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

from ..core.runtime import repo_root
from ..errors import OpsError


HOOK_FILENAME = "itp-hook.sh"
HOOK_STEPS = ("pre_build", "post_build", "pre_deploy")


def find_hook(module: str) -> Path | None:
    """查找模块目录下的 hook 脚本。"""
    module_path = repo_root() / module
    hook_path = module_path / HOOK_FILENAME
    if hook_path.is_file():
        return hook_path
    return None


def run_hook(module: str, step: str, env: str = "test") -> bool:
    """执行指定 hook 点。返回 True 表示成功或无需执行。

    参数:
      module: 模块目录名（如 face-pay-server）
      step: hook 点名称（pre_build/post_build/pre_deploy）
      env: 环境名（test/hongdao）

    返回:
      True: hook 成功执行或无需执行
      False: hook 失败（非严格模式）
    """
    if step not in HOOK_STEPS:
        return True

    hook_path = find_hook(module)
    if not hook_path:
        return True

    # 检查是否严格模式
    strict = os.environ.get("HOOK_STRICT", "0") == "1"

    payload = json.dumps({"module": module, "step": step, "env": env})

    try:
        result = subprocess.run(
            ["bash", str(hook_path), step],
            input=payload,
            capture_output=True,
            text=True,
            timeout=60,
        )
    except subprocess.TimeoutExpired:
        print(f"[WARN] hook {HOOK_FILENAME} {step} 超时（60s）", file=sys.stderr)
        return not strict
    except Exception as exc:
        print(f"[WARN] hook {HOOK_FILENAME} {step} 执行失败: {exc}", file=sys.stderr)
        return not strict

    if result.returncode != 0:
        msg = (f"[WARN] hook {HOOK_FILENAME} {step} 失败（exit {result.returncode}）\n"
               f"stdout: {result.stdout or '(空)'}\n"
               f"stderr: {result.stderr or '(空)'}")
        print(msg, file=sys.stderr)
        if strict:
            raise OpsError(f"hook {step} 失败，已启用严格模式", 1)
        return False

    if result.stdout:
        print(f"[HOOK {step}] {result.stdout.rstrip()}")
    return True


def list_hooks(module: str) -> list[dict]:
    """列出模块支持的 hook 点。"""
    hook_path = find_hook(module)
    if not hook_path:
        return []

    supported = []
    content = hook_path.read_text(encoding="utf-8")
    for step in HOOK_STEPS:
        if f"on_{step}" in content or step in content:
            supported.append(step)
    return [{"step": s, "file": str(hook_path)} for s in supported]

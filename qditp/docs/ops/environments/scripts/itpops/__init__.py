"""itpops —— 青岛地铁 ITP 平台运维工具集（纯标准库 Python 实现）。

项目层（2.5.0，对标 pip / ansible / gh 等 CLI 项目）：
  README.md          定位 / 结构 / 快速上手 / CLI 契约
  CONTRIBUTING.md    分层纪律 / 包纪律 / 使用纪律 / 验证命令
  CHANGELOG.md       版本化沿革（新 → 旧）
  docs/              平台知识沉淀（cloudos-notes.md）
  pyproject.toml     包元数据 / console script / ruff / pytest

包内四层（NEVER 反向依赖）：
  cli/        表现层：参数解析 / 帮助 / 补全 / 注册表 / JSON 信封
  commands/   一域一文件（CATALOG + main）；只做注册与分发，实现下沉特性包
  core/       公共支撑：ui / version / environments / remote / runtime / crypto
  <特性>/     实现：cloudos(auth/http/image/pkg + 按业务段 common/apps/
              scaling/deploy/images/impl) buildx sentinel web
              analysis versioning linting

唯一入口是 `itp-ops.sh`（薄壳 → `python3 -m itpops`）；`python -m itpops.<特性>`
只是调试别名，一律转发 `cli.main`，不另起一套解析/输出契约。
`pyproject.toml` 另暴露 `itp-ops` console script 供 `pipx install` 后直接使用。
"""
from __future__ import annotations

__version__ = "2.9.4"

# 兼容旧引用（v2.0/2.1 时代用 ITP_OPS_VERSION）
ITP_OPS_VERSION = __version__

# 自动注册所有 Plugin（延迟导入，避免循环依赖）
def _register_plugins() -> None:
    """延迟注册所有 Plugin，避免循环导入。"""
    try:
        from .plugin_auto_register import auto_register_plugins
        auto_register_plugins()
    except Exception:
        pass  # 静默忽略注册失败

# 模块加载时不立即注册，等到首次导入时再注册
_register_plugins()

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
  <特性>/     实现：cloudos(auth/http/image/pkg/impl) buildx sentinel web
              analysis versioning linting

唯一入口是 `itp-ops.sh`（薄壳 → `python3 -m itpops`）；`python -m itpops.<特性>`
只是调试别名，一律转发 `cli.main`，不另起一套解析/输出契约。
`pyproject.toml` 另暴露 `itp-ops` console script 供 `pipx install` 后直接使用。
"""
__version__ = "2.5.0"

# 兼容旧引用（v2.0/2.1 时代用 ITP_OPS_VERSION）
ITP_OPS_VERSION = __version__

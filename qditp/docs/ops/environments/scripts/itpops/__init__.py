"""itpops —— 青岛地铁 ITP 平台运维工具集（Python 底层实现）。

布局（对标 pip/ansible 等 CLI 项目惯例）：
  cli/        表现层：参数解析 / 帮助 / 补全 / 注册表
  commands/   一个文件 = 一个命令域（CATALOG + main(args)）
  core/       公共支撑：ui / version / environments / remote / execx
  cloudos/    CloudOS API 客户端（auth/http/image/pkg）
  buildx/     镜像构建实现          sentinel/  路由哨兵部署实现
  web/        前端发版与挂载实现

唯一入口 `itp-ops.sh`（薄壳 → `python3 -m itpops`）；`pyproject.toml` 另暴露
`itp-ops` console script，供 `pipx install` 后直接使用。
"""
__version__ = "2.3.0"

# 兼容旧引用（v2.0/2.1 时代用 ITP_OPS_VERSION）
ITP_OPS_VERSION = __version__

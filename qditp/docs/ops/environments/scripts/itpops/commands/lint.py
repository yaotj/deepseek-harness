"""itpops · lint 域 —— 仓库健康门禁（YAML 语法与重复键）。

原 `scripts/lint-yaml.py`（IT-032 门禁）已并入 `itpops/linting/yaml_lint.py`：
`itp-ops.sh lint yaml [根目录] [-v] [--jsonl]`。`--jsonl` 逐行 JSON 供程序消费
（docs/提示词/lint-prompts.py 在用）；结构化结果同时进 `-o json` 的 data。

退出码：0 干净 / 1 发现问题（与 ruff、grep 等门禁工具一致）/ 3 缺 PyYAML。
"""
from __future__ import annotations

from pathlib import Path

from ..core import ui
from ..core.runtime import repo_root
from ..errors import EXIT_USAGE, OpsError
from ..linting import yaml_lint

_HELP = """\
itp-ops lint —— 仓库健康门禁

用法: itp-ops.sh lint <检查项> [参数...]

检查项
  yaml [根目录] [-v] [--json]
      扫描 yml/yaml：① 语法错误 ② 同一 mapping 重复键（k8s 两个同名键会静默覆盖）。
      默认扫仓库根；-v 打印每个文件；--jsonl 逐行输出错误（供 lint-prompts.py 消费；注意 --json 是全局输出格式别名）。
      退出码：0 干净 / 1 有 ERROR / 3 缺 PyYAML（pip install pyyaml）。
  help                      本帮助

说明
  · 实现：itpops/linting/yaml_lint.py（原 scripts/lint-yaml.py）。
  · 存量豁免只在 KNOWN_ISSUES 白名单里登记，只减不增；新问题 MUST 修，不许进表。"""

CATALOG = {
    "name": "lint",
    "summary": "仓库健康门禁：YAML 语法错误与重复键（IT-032 类故障的机械拦截）",
    "default_env": None,
    "env_note": "环境无关（本机文件扫描）",
    "subcommands": [
        {
            "name": "yaml",
            "usage": "lint yaml [根目录] [-v] [--jsonl]",
            "summary": "扫描 yml/yaml 的语法错误与重复键；退出码 0 干净 / 1 有 ERROR / 3 缺 PyYAML",
            "args": [
                {"name": "根目录", "required": False, "desc": "默认仓库根"},
                {"name": "-v", "desc": "打印每个被检查文件"},
                {"name": "--jsonl", "desc": "逐行 JSON 输出（供程序消费；--json 是全局 -o json 别名）"},
            ],
            "writes": False,
            "example": "itp-ops.sh lint yaml",
            "stdout_human": "ERROR  face-pay-server/src/main/resources/application.yml:12: mapping values are not allowed here\n\n扫描 53 个 yml/yaml：ERROR 1（存量豁免 0）/ OK 52",
            "json_data": {"files": 53, "ok": 52, "errors": [{"file": "…", "line": 12, "error": "…"}]},
        },
    ],
}


def _yaml(rest: list[str]) -> int:
    as_json = "--jsonl" in rest
    verbose = any(a in ("-v", "--verbose") for a in rest)
    positional = [a for a in rest if not a.startswith("-")]
    if len(positional) > 1:
        raise OpsError("lint yaml 只接受一个根目录参数", EXIT_USAGE)

    root = Path(positional[0]).expanduser() if positional else repo_root()
    if not root.is_dir():
        raise OpsError(f"根目录不存在：{root}", EXIT_USAGE)

    result = yaml_lint.scan(root, verbose=verbose)
    if as_json:
        for line in yaml_lint.render_jsonl(result):
            print(line)          # 逐行 JSON 是给程序吃的，不经 ui（-o json 时会被入口改道）
        ui.log_info(f"扫描 {result['files']} 个 yml/yaml：ERROR {len(result['errors'])}"
                    f"（存量豁免 {len(result['skipped'])}）/ OK {result['ok']}")
    else:
        for line in yaml_lint.render_human(result, root, verbose=verbose):
            ui.out(line)

    ui.result(result)
    return 1 if result["errors"] else 0


def main(args: list[str]) -> int:
    sub = args[0] if args else ""
    rest = args[1:]

    if sub in ("", "-h", "--help", "help"):
        ui.out(_HELP)
        return 0
    if sub == "yaml":
        return _yaml(rest)

    ui.out(_HELP)
    raise OpsError(f"未知检查项：{sub}（支持 yaml）", EXIT_USAGE)

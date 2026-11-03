"""命令注册表 —— 域清单与 CATALOG 汇总（供 cli/help/completion/commands.catalog 消费）。

职责边界（对标 pip `_internal/cli/`）：本模块只做「数据」——域名 → 命令模块映射、
CATALOG 汇总、契约说明；不产出人机文本、不解析参数。命令入口在 `commands/catalog.py`。

新增域时：在 `itpops/commands/<域>.py` 里写 CATALOG，并把「域 → 模块」登记进
`_DOMAIN_MODULES` —— 帮助、补全、catalog 三处自动同步。
"""
from __future__ import annotations

import importlib

from .. import __version__
from ..errors import CODE_BY_EXIT

# 域名 → itpops.commands 下的模块名（与 itp-ops.sh 命令面一致）
_DOMAIN_MODULES: dict[str, str] = {
    "version": "version",
    "cloudos": "cloudos",
    "k8s": "k8s",
    "log": "log",
    "diag": "diag",
    "analysis": "analysis",
    "buildx": "buildx",
    "sentinel": "sentinel",
    "web": "web",
    "catalog": "catalog",
}

EXIT_CODE_DOC = {
    "0": "成功",
    "1": "未分类错误（尽量别用）",
    "2": "用法错误（参数不对 / 未知域 / 未知子命令）",
    "3": "前置不满足（缺依赖、仓库根断言失败、环境未知）",
    "4": "远端/传输失败（ssh 不可达、HTTP 连不上）",
    "5": "命令执行失败（远端命令非 0、业务拒绝、解析失败）",
    "6": "写操作缺 --yes 被拒",
    "130": "中断（Ctrl+C）",
}

GLOBAL_FLAGS_DOC = [
    {"flag": "-e, --env hongdao|test", "desc": "环境切换（全局，所有域生效）。未指定时用各域自己的默认（cloudos/buildx/sentinel=hongdao、k8s/log=test、diag 按子命令）"},
    {"flag": "-y, --yes", "desc": "确认写操作；缺它写命令一律拒绝"},
    {"flag": "-n, --dry-run", "desc": "强制预演（等价不带 --yes，优先于 --yes）；命令无预演分支时报用法错"},
    {"flag": "-o, --output text|json", "desc": "输出格式；--json 为 -o json 的简写"},
    {"flag": "-q, --quiet", "desc": "抑制 INFO/STEP 日志（WARN/ERROR 保留）"},
    {"flag": "-v, --verbose", "desc": "DEBUG 输出（远端命令行等），可叠加 -vv"},
    {"flag": "--no-color", "desc": "关闭颜色（亦可用 NO_COLOR 环境变量）"},
    {"flag": "--trace", "desc": "出错时打印完整堆栈"},
    {"flag": "-h, --help", "desc": "帮助（任意层级有效：域级富文本，命令级由 CATALOG 生成）"},
    {"flag": "-V, --version", "desc": "版本信息（含 python / git commit）"},
    {"flag": "--", "desc": "终止选项解析，其后的参数按位置参数处理"},
]


def load_domains() -> list[dict]:
    """汇总各域 CATALOG；导入失败的域以占位项标注（不中断其它域）。"""
    domains: list[dict] = []
    for name, module_name in _DOMAIN_MODULES.items():
        try:
            mod = importlib.import_module(f".commands.{module_name}", package="itpops")
        except ImportError as exc:  # noqa: F841 —— 记下原因，供排障时 -v 看
            domains.append({
                "name": name,
                "status": "pending",
                "summary": "命令模块导入失败（-v 查看原因）",
                "subcommands": [],
            })
            continue
        entry = dict(getattr(mod, "CATALOG", {"name": name, "subcommands": []}))
        entry.setdefault("name", name)
        entry["status"] = "ok"
        domains.append(entry)
    return domains


def build_catalog() -> dict:
    return {
        "tool": "itp-ops",
        "version": __version__,
        "contract": {
            "stdout": "人类结果文本；-o json 时仅一条 JSON 信封",
            "stderr": "人机日志（INFO/WARN/ERROR/STEP/DEBUG/环境横幅），TTY 才着色",
            "output_formats": ["text", "json"],
            "json_envelope": {"ok": "bool", "command": "list[str]", "data": "object|null",
                              "error": "{code,message,exit}|null", "duration_ms": "int"},
            "exit_codes": EXIT_CODE_DOC,
            "code_names": {str(k): v for k, v in CODE_BY_EXIT.items()},
            "env_vars": {"NO_COLOR": "关闭颜色", "CLICOLOR_FORCE": "强制颜色",
                         "ITP_OPS_ENV": "默认环境（等价 --env，显式 --env 优先）"},
            "help": {"root": "itp-ops.sh -h", "domain": "itp-ops.sh <域> help",
                     "command": "itp-ops.sh <域> <命令> -h"},
            "completion": "itp-ops.sh completion bash|zsh",
        },
        "global_flags": GLOBAL_FLAGS_DOC,
        "domains": load_domains(),
        # 2026-11-03 起独立入口已全部收敛为域子命令；字段保留只为兼容旧消费者
        "standalone": [],
    }

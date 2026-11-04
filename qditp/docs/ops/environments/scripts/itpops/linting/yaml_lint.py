"""YAML 健康检查（IT-032 类故障的机械门禁）。

背景：2026-10-20 一次「点号拍平」把 application.yml 的子键缩进拍坏，snakeyaml 启动期
即 `Application run failed`，缺陷潜伏 9 天——yml 不参与 `mvn test-compile`，仓库无门禁。

检查项（PyYAML SafeLoader + 自定义 mapping constructor，一次过）：
  ① 语法错误（缩进残留、tab、结构破坏）——load_all 自然抛，带 file:line
  ② 重复键（同一 mapping 内同键两次）——自定义 constructor 拦截（k8s yaml 同样致命：
     container 下两个 `env:` 后者静默覆盖前者，无任何报错）

判据：所有问题一律 ERROR（运行期必炸或静默错），任何 ERROR → 退出码 1。
存量豁免走 KNOWN_ISSUES 白名单（带登记日期与理由，只减不增）。

【与旧 scripts/lint-yaml.py 的差异】
  · 收编进 itpops（`itp-ops.sh lint yaml`）；PyYAML 成为可选依赖（缺则前置错 exit 3）。
  · 原 `--json`（逐行 JSON）保留给程序消费；结构化结果同时进 `-o json` 的 data。
"""
from __future__ import annotations

import json
from pathlib import Path

from ..core import ui
from ..errors import EXIT_PRECONDITION, OpsError

try:
    import yaml
except ImportError:  # 可选依赖：缺了只影响本命令
    yaml = None  # type: ignore[assignment]

# ── 检查范围：模块配置 + k8s 部署清单 + 根级 CI/工具配置 ──
EXCLUDE_DIR_NAMES = {"target", "node_modules", "dist", "out", "build",
                     "qditp-wiki", "__pycache__"}
EXCLUDE_REL_PREFIXES = ("deploy/data/",)

# 整体豁免：名义 .yaml 实为 md 文档（被 6 处文档引用，勿改名）
EXCLUDE_FILES = {
    "docs/ops/environments/test-istio-ingress.yaml",
}

# 存量豁免（相对路径 → (行, 理由)）：只允许登记「历史遗留且改动有独立排期」的条目
KNOWN_ISSUES: dict[str, tuple[int, str]] = {}


def _require_yaml() -> None:
    if yaml is None:
        raise OpsError(
            "YAML 门禁需要 PyYAML（可选依赖）：pip install pyyaml", EXIT_PRECONDITION
        )


def _loader():
    """SafeLoader 子类：mapping 构造前先扫重复键（跳过 YAML merge 键 `<<`）。"""
    _require_yaml()

    class DupKeySafeLoader(yaml.SafeLoader):
        pass

    def _construct_mapping_no_dup(loader, node, deep=False):
        seen: dict[object, int] = {}
        for key_node, _value_node in node.value:
            if key_node.tag == "tag:yaml.org,2002:merge":
                continue
            if not isinstance(key_node, yaml.ScalarNode):
                continue
            key = loader.construct_object(key_node, deep=True)
            if key in seen:
                raise yaml.constructor.ConstructorError(
                    "while constructing a mapping", node.start_mark,
                    f"found duplicate key ({key!r}); first seen at line {seen[key]}",
                    key_node.start_mark,
                )
            seen[key] = key_node.start_mark.line + 1
        return yaml.constructor.SafeConstructor.construct_mapping(loader, node, deep=deep)

    DupKeySafeLoader.add_constructor(
        yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _construct_mapping_no_dup)
    return DupKeySafeLoader


def lint_file(path: Path, loader) -> list[tuple[int, str]]:
    """返回 [(line, message)]；空列表表示健康。"""
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        return [(1, f"非 UTF-8 编码: {exc}")]
    try:
        list(yaml.load_all(text, Loader=loader))   # 耗尽生成器：多文档每个 doc 都要解析
    except yaml.YAMLError as exc:
        mark = getattr(exc, "problem_mark", None) or getattr(exc, "context_mark", None)
        line = mark.line + 1 if mark else 1
        problem = getattr(exc, "problem", None) or str(exc).splitlines()[0]
        return [(line, problem)]
    return []


def iter_yaml_files(root: Path):
    for p in sorted(root.rglob("*")):
        if not p.is_file() or p.suffix not in (".yml", ".yaml"):
            continue
        rel = p.relative_to(root).as_posix()
        if any(part in EXCLUDE_DIR_NAMES for part in p.relative_to(root).parts):
            continue
        if any(rel.startswith(pre) for pre in EXCLUDE_REL_PREFIXES):
            continue
        if rel in EXCLUDE_FILES:
            continue
        yield p, rel


def scan(root: Path, *, verbose: bool = False) -> dict:
    """扫描 root；返回 {files, ok, errors:[{file,line,error}], skipped:[...]}。"""
    loader = _loader()
    errors: list[dict] = []
    skipped: list[str] = []
    ok_files: list[str] = []
    n_files = 0
    for path, rel in iter_yaml_files(root):
        n_files += 1
        found = lint_file(path, loader)
        if not found:
            ok_files.append(rel)
            continue
        for line, msg in found:
            if rel in KNOWN_ISSUES and KNOWN_ISSUES[rel][0] == line:
                skipped.append(f"{rel}:{line}: 存量豁免（{KNOWN_ISSUES[rel][1]}）")
                continue
            errors.append({"file": rel, "line": line, "error": msg})
    return {"files": n_files, "ok": len(ok_files), "ok_files": ok_files,
            "errors": errors, "skipped": skipped}


def render_human(result: dict, root: Path, *, verbose: bool) -> list[str]:
    lines: list[str] = []
    if verbose:
        for rel in result.get("ok_files", []):
            lines.append(f"OK     {rel}")
        for item in result["skipped"]:
            lines.append(f"SKIP   {item}")
    for e in result["errors"]:
        lines.append(f"ERROR  {e['file']}:{e['line']}: {e['error']}")
    lines.append(f"\n扫描 {result['files']} 个 yml/yaml：ERROR {len(result['errors'])}"
                 f"（存量豁免 {len(result['skipped'])}）/ OK {result['ok']}")
    return lines


def render_jsonl(result: dict) -> list[str]:
    return [json.dumps(e, ensure_ascii=False) for e in result["errors"]]

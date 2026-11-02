#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""qd-itp YAML 健康检查（IT-032 类故障的机械门禁）

背景：2026-10-20 `ebf27098e0` 把 face-pay-server/application.yml 做「点号拍平」
只拍了前 3 个键、其余子键残留缩进且无父键 → snakeyaml 启动期即 `Application run failed`，
缺陷潜伏 9 天无人发现——yml 不参与 `mvn test-compile`，仓库无任何 yml 门禁。

检查项（PyYAML SafeLoader + 自定义 mapping constructor，双查一次过）：
  ① 语法错误（缩进残留、tab、结构破坏）——load_all 自然抛，带 file:line
  ② 重复键（同一 mapping 内同键两次）——自定义 constructor 拦截；
     k8s yaml 同样致命（container 下两个 `env:`，后者静默覆盖前者，无任何报错）

判据：所有发现问题一律 ERROR（运行期必然炸或静默错），任何 ERROR → 退出码 1。
存量豁免走脚本内 KNOWN_ISSUES 白名单（带登记日期与理由，只减不增）。

用法：
    python3 scripts/lint-yaml.py [仓库根] [-v] [--json]

  -v      打印每个被检查文件（默认只打错误 + 汇总）
  --json  错误以 JSON 行输出（供 lint-prompts.py 等程序消费）
"""

import json
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.stderr.write("ERROR: PyYAML 未安装（pip install pyyaml）\n")
    sys.exit(2)

# ── 检查范围：模块配置 + k8s 部署清单 + 根级 CI/工具配置 ──
# 排除构建产物、前端依赖树、数据快照与外部子模块（qditp-wiki 不归本仓门禁）。
EXCLUDE_DIR_NAMES = {"target", "node_modules", "dist", "out", "build",
                     "qditp-wiki", "__pycache__"}
EXCLUDE_REL_PREFIXES = ("deploy/data/",)

# ── 整体豁免：名义 .yaml 实为 md 文档（markdown 围栏 + 索引引用遍布多文档，勿改名）──
EXCLUDE_FILES = {
    "docs/ops/environments/test-istio-ingress.yaml",  # md 围栏文档，被 测试环境.md/现查命令.md 等 6 处引用
}

# ── 存量豁免（格式：相对路径 → (行, 理由)）────────────────────────────
# 只允许登记「历史遗留且改动有独立排期」的条目；新发现问题 MUST 修复，不许进此表。
KNOWN_ISSUES = {
    # 例："legacy/module/application.yml": (12, "IT-XXX 排期 2026-xx-xx 修复"),
}


class DupKeySafeLoader(yaml.SafeLoader):
    """SafeLoader 子类：mapping 构造前先扫重复键（跳过 YAML merge 键 `<<`）。"""


def _construct_mapping_no_dup(loader, node, deep=False):
    seen = {}
    for key_node, _value_node in node.value:
        # merge 键（tag:yaml.org,2002:merge）不算重复——本仓虽无使用，防御未来
        if key_node.tag == "tag:yaml.org,2002:merge":
            continue
        if not isinstance(key_node, yaml.ScalarNode):
            continue  # 复杂键（sequence/mapping 作键）交给原生 constructor 处理
        key = loader.construct_object(key_node, deep=True)
        if key in seen:
            first_line = seen[key]
            raise yaml.constructor.ConstructorError(
                "while constructing a mapping",
                node.start_mark,
                f"found duplicate key ({key!r}); first seen at line {first_line}",
                key_node.start_mark,
            )
        seen[key] = key_node.start_mark.line + 1
    return yaml.constructor.SafeConstructor.construct_mapping(loader, node, deep=deep)


DupKeySafeLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _construct_mapping_no_dup)


def lint_file(path: Path, rel: str, verbose: bool):
    """返回 (errors, ok) ；errors 为 [(line, message)]。"""
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        return [(1, f"非 UTF-8 编码: {exc}")], False

    try:
        # 用 list 耗尽生成器，确保多文档文件每个 doc 都被解析
        list(yaml.load_all(text, Loader=DupKeySafeLoader))
    except yaml.YAMLError as exc:
        mark = getattr(exc, "problem_mark", None) or getattr(exc, "context_mark", None)
        line = mark.line + 1 if mark else 1
        problem = getattr(exc, "problem", None) or str(exc).splitlines()[0]
        return [(line, problem)], False
    return [], True


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


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    root = Path(args[0] if args else ".").resolve()
    verbose = "-v" in sys.argv
    as_json = "--json" in sys.argv

    if not root.is_dir():
        sys.stderr.write(f"ERROR: 仓库根不存在: {root}\n")
        return 2

    all_errors = []   # (rel, line, message)
    n_files = n_ok = 0
    for path, rel in iter_yaml_files(root):
        n_files += 1
        errors, ok = lint_file(path, rel, verbose)
        if ok:
            n_ok += 1
            if verbose and not as_json:
                print(f"OK     {rel}")
        for line, msg in errors:
            if rel in KNOWN_ISSUES and KNOWN_ISSUES[rel][0] == line:
                if verbose and not as_json:
                    print(f"SKIP   {rel}:{line}: 存量豁免（{KNOWN_ISSUES[rel][1]}）")
                continue
            all_errors.append((rel, line, msg))

    for rel, line, msg in all_errors:
        if as_json:
            print(json.dumps({"file": rel, "line": line, "error": msg},
                             ensure_ascii=False))
        else:
            print(f"ERROR  {rel}:{line}: {msg}")

    n_kn = len(KNOWN_ISSUES)
    summary = (f"\n扫描 {n_files} 个 yml/yaml：ERROR {len(all_errors)}"
               f"（存量豁免 {n_kn}）/ OK {n_ok}")
    print(summary, file=sys.stderr if as_json else sys.stdout)

    return 1 if all_errors else 0


if __name__ == "__main__":
    sys.exit(main())

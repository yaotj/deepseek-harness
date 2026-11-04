"""方法级 HTTP 端点计数（原 analysis/count-endpoints.py，2026-11-04 并入 itp-ops）。

规则（与旧脚本一致）：
- 计数的注解：@GetMapping / @PostMapping / @PutMapping / @DeleteMapping / @RequestMapping
- 类级 @RequestMapping（其后 4 行内含 `class Xxx`）不算端点
- 仅统计 *Controller.java
- 输出每文件方法级端点数 + 合计

与旧脚本的差异：不再要求「从仓库根 cd 后执行」——路径在此处解析，
调用方传什么（相对/绝对）就按什么解释；相对路径以仓库根为基准。
"""
from __future__ import annotations

import os
import re
from pathlib import Path

METHOD_ANNOT = re.compile(r"@(Get|Post|Put|Delete)Mapping\b")
ANY_MAPPING = re.compile(r"@(Get|Post|Put|Delete|Request)Mapping\b")
CLASS_DECL = re.compile(r"\bclass\s+\w+")


def count_file(path: Path) -> tuple[int, int]:
    """返回 (方法级端点数, 类级 @RequestMapping 数)。"""
    with open(path, encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    class_level_req = 0
    total_mapping = 0
    for i, line in enumerate(lines):
        if ANY_MAPPING.search(line):
            total_mapping += 1
            if "@RequestMapping" in line:
                window = "".join(lines[i:i + 4])
                if CLASS_DECL.search(window):
                    class_level_req += 1
    return total_mapping - class_level_req, class_level_req


def iter_controllers(root: Path):
    for dirpath, _dirs, files in os.walk(root):
        for fn in files:
            if fn.endswith("Controller.java"):
                yield Path(dirpath) / fn


def scan(root: Path) -> list[tuple[str, int, int]]:
    """扫描 root，返回 [(相对路径, 方法级, 类级)]，按相对路径排序。"""
    rows: list[tuple[str, int, int]] = []
    for p in iter_controllers(root):
        ml, cl = count_file(p)
        rows.append((p.relative_to(root).as_posix(), ml, cl))
    rows.sort(key=lambda r: r[0])
    return rows


def render(rows: list[tuple[str, int, int]], root_arg: str, *, csv: bool) -> list[str]:
    """按旧脚本的输出格式渲染（csv 用调用方给的 root 前缀，文本用相对路径）。"""
    lines: list[str] = []
    total = sum(ml for _rel, ml, _cl in rows)
    if csv:
        for rel, ml, _cl in rows:
            lines.append(f"{os.path.join(root_arg, rel)},{ml}")
    else:
        for rel, ml, cl in rows:
            tag = f"  (含类级 {cl})" if cl else ""
            lines.append(f"{ml:>3}  {rel}{tag}")
        lines.append("-" * 60)
        lines.append(f"方法级端点合计: {total}  （文件数 {len(rows)}）")
    return lines

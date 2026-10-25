#!/usr/bin/env python3
"""统计某个模块（或整个仓库）方法级 HTTP 端点数，用于让文档里的「端点计数」可由代码复跑刷新，避免随接口新增而失准。

用法（脚本位于 docs/ops/environments/scripts/analysis/，从仓库根执行）：
    python3 docs/ops/environments/scripts/analysis/count-endpoints.py <模块源码目录> [--csv]
    python3 docs/ops/environments/scripts/analysis/count-endpoints.py account-server/src/main/java
    python3 docs/ops/environments/scripts/analysis/count-endpoints.py .            # 全仓

规则：
- 计数的注解：@GetMapping / @PostMapping / @PutMapping / @DeleteMapping / @RequestMapping
- 类级 @RequestMapping（紧邻 `class XxxController` 上方）不算端点，只算方法级
- 仅统计 *Controller.java
- 输出每文件方法级端点数 + 模块合计

注意：这是轻量静态计数，不解析条件编译/多 profile；新增加接口后重跑即可刷新文档数字。
"""
import argparse
import os
import re
import sys

METHOD_ANNOT = re.compile(r"@(Get|Post|Put|Delete)Mapping\b")
ANY_MAPPING = re.compile(r"@(Get|Post|Put|Delete|Request)Mapping\b")
CLASS_DECL = re.compile(r"\bclass\s+\w+")


def count_file(path):
    with open(path, encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    class_level_req = 0
    total_mapping = 0
    for i, line in enumerate(lines):
        if ANY_MAPPING.search(line):
            total_mapping += 1
            # 判断是否为类级 @RequestMapping：看其后 3 行内是否出现 class 声明
            if "@RequestMapping" in line:
                window = "".join(lines[i : i + 4])
                if CLASS_DECL.search(window):
                    class_level_req += 1
    method_level = total_mapping - class_level_req
    return method_level, class_level_req


def iter_controllers(root):
    for dirpath, _dirs, files in os.walk(root):
        for fn in files:
            if fn.endswith("Controller.java"):
                yield os.path.join(dirpath, fn)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root", help="模块 src/main/java 或仓库根")
    ap.add_argument("--csv", action="store_true", help="只输出 csv：文件路径,方法级端点数")
    args = ap.parse_args()

    rows = []
    total = 0
    for path in sorted(iter_controllers(args.root)):
        ml, cl = count_file(path)
        rows.append((path, ml, cl))
        total += ml

    if args.csv:
        for path, ml, cl in rows:
            print(f"{path},{ml}")
    else:
        for path, ml, cl in rows:
            tag = f"  (含类级 {cl})" if cl else ""
            print(f"{ml:>3}  {os.path.relpath(path, args.root)}{tag}")
        print("-" * 60)
        print(f"方法级端点合计: {total}  （文件数 {len(rows)}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())

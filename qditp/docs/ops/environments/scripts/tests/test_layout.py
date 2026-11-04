"""布局回归（2.5.0）：命令层薄壳、调试别名走统一入口、项目文档与元数据齐备。

这些用例锁住「重新组织脚本」的结构结论，防止后续又有人把实现塞回 commands/
或让 `python -m itpops.<特性>` 绕过全局参数解析。
"""
from __future__ import annotations

import json
import unittest

from support import SCRIPTS_DIR, run_cli, run_module


class CommandLayerTest(unittest.TestCase):
    def test_cloudos_command_layer_is_thin(self):
        src = (SCRIPTS_DIR / "itpops/commands/cloudos.py").read_text(encoding="utf-8")
        self.assertLess(len(src.splitlines()), 30, "命令层不该重新长出实现")
        self.assertIn("from ..cloudos.impl import CATALOG, main", src)

    def test_cloudos_impl_is_orchestration_only(self):
        impl = (SCRIPTS_DIR / "itpops/cloudos/impl.py").read_text(encoding="utf-8")
        self.assertIn("CATALOG = {", impl)
        self.assertIn("def main(args: list[str]) -> int:", impl)
        for seg in ("common", "apps", "scaling", "deploy", "images"):
            self.assertTrue((SCRIPTS_DIR / f"itpops/cloudos/{seg}.py").is_file(), seg)
        common = (SCRIPTS_DIR / "itpops/cloudos/common.py").read_text(encoding="utf-8")
        for seg in ("apps", "scaling", "deploy", "images"):
            self.assertNotIn(f"from .{seg} import", common)   # common 是依赖图叶子

    def test_project_docs_and_metadata_exist(self):
        for p in ("README.md", "CONTRIBUTING.md", "CHANGELOG.md",
                  "docs/cloudos-notes.md", ".gitignore",
                  "itpops/py.typed", "pyproject.toml"):
            self.assertTrue((SCRIPTS_DIR / p).is_file(), f"缺少项目文件：{p}")


class DelegatedEntryTest(unittest.TestCase):
    def test_module_level_entries_all_delegate(self):
        """2.6.0：web 两个模块级直调只转发 cli.main；core 两个多余直调块已删除。"""
        delegates = {
            "itpops/web/frontend_release.py": '["web", "release"',
            "itpops/web/mount_export.py": '["web", "mount-export"',
        }
        for path, marker in delegates.items():
            with self.subTest(path=path):
                src = (SCRIPTS_DIR / path).read_text(encoding="utf-8")
                self.assertIn("_cli_main(", src)
                self.assertIn(marker, src)
        for path in ("itpops/buildx/core.py", "itpops/sentinel/core.py"):
            with self.subTest(path=path):
                src = (SCRIPTS_DIR / path).read_text(encoding="utf-8")
                self.assertNotIn('if __name__ == "__main__"', src)

    def test_debug_alias_matches_unified_entry(self):
        for domain in ("buildx", "sentinel"):
            with self.subTest(domain=domain):
                direct = run_module(f"itpops.{domain}", "help")
                unified = run_cli(domain, "help")
                self.assertEqual(direct.returncode, unified.returncode)
                self.assertEqual(direct.stdout, unified.stdout)
                self.assertEqual(direct.stderr, unified.stderr)

    def test_debug_alias_honours_json_contract(self):
        r = run_module("itpops.buildx", "-o", "json", "help")
        payload = json.loads(r.stdout)   # 直调也能出单条信封 = 走了统一入口
        self.assertTrue(payload["ok"])
        self.assertEqual(payload["command"], ["buildx", "-o", "json", "help"])


if __name__ == "__main__":
    unittest.main()

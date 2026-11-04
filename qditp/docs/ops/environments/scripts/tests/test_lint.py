"""YAML 门禁（原 scripts/lint-yaml.py）：语法错、重复键、健康文件。"""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

try:
    import yaml  # noqa: F401
    HAS_YAML = True
except ImportError:  # pragma: no cover - 环境未装可选依赖时跳过
    HAS_YAML = False

from itpops.linting import yaml_lint


@unittest.skipUnless(HAS_YAML, "需要 PyYAML（pip install pyyaml）")
class YamlLintTest(unittest.TestCase):
    def _scan(self, files: dict[str, str]) -> dict:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for rel, text in files.items():
                p = root / rel
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text, encoding="utf-8")
            return yaml_lint.scan(root)

    def test_clean_file(self):
        r = self._scan({"a.yml": "server:\n  port: 8080\n"})
        self.assertEqual(r["errors"], [])
        self.assertEqual((r["files"], r["ok"]), (1, 1))

    def test_syntax_error(self):
        r = self._scan({"a.yml": "server:\n  port: 8080\n   extra: 1\n"})
        self.assertEqual(len(r["errors"]), 1)
        self.assertEqual(r["errors"][0]["file"], "a.yml")

    def test_duplicate_key(self):
        r = self._scan({"k8s.yaml": "spec:\n  env: 1\n  env: 2\n"})
        self.assertEqual(len(r["errors"]), 1)
        self.assertIn("duplicate key", r["errors"][0]["error"])

    def test_excluded_dirs_skipped(self):
        r = self._scan({"target/x.yml": "a: [\n"})
        self.assertEqual(r["files"], 0)

    def test_jsonl_render(self):
        r = self._scan({"a.yml": "a: [\n"})
        lines = yaml_lint.render_jsonl(r)
        self.assertEqual(len(lines), 1)
        self.assertTrue(lines[0].startswith("{"))


if __name__ == "__main__":
    unittest.main()

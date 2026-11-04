"""版本载体 bump（原 scripts/bump-version.py 的规则与文件改写）。"""
from __future__ import annotations

import io
import json
import tempfile
from contextlib import redirect_stdout
import unittest
from pathlib import Path

from itpops.errors import OpsError
from itpops.versioning import bump


class SemverTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(bump.parse_semver("2.1.4"), (2, 1, 4))
        self.assertEqual(bump.parse_semver("1.2.44.2"), (1, 2, 44, 2))
        for bad in ("", "1.", ".1", "1..2", "1.a", "1.2.3.4.5"):
            self.assertIsNone(bump.parse_semver(bad), bad)

    def test_normal(self):
        self.assertEqual(bump.bump_normal((2, 1, 4)), (2, 1, 5))
        self.assertEqual(bump.bump_normal((1, 2, 44, 2)), (1, 2, 44, 3))
        self.assertEqual(bump.bump_normal((2, 1, 99)), (2, 2, 0))
        self.assertEqual(bump.bump_normal((1, 2, 44, 99)), (1, 2, 45, 0))

    def test_debug(self):
        self.assertEqual(bump.bump_debug((2, 1, 4)), (2, 1, 4, 1))
        self.assertEqual(bump.bump_debug((1, 2, 44, 2)), (1, 2, 44, 3))


class BumpFileTest(unittest.TestCase):
    def test_pom_bump_and_dry_run(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            pom = d / "pom.xml"
            pom.write_text(
                "<project><modelVersion>4.0.0</modelVersion>\n"
                "<version>2.1.4</version>\n"
                "<parent><version>3.2.6</version></parent></project>\n",
                encoding="utf-8",
            )
            # dry-run 不落盘
            with redirect_stdout(io.StringIO()):
                new = bump.bump_pom(d, "normal", True)
            self.assertEqual(new, "2.1.5")
            self.assertIn("<version>2.1.4</version>", pom.read_text(encoding="utf-8"))
            # 真 bump：只改第一个 <version>，parent 同步 spring boot 值
            with redirect_stdout(io.StringIO()):
                new = bump.bump_pom(d, "normal", False)
            text = pom.read_text(encoding="utf-8")
            self.assertEqual(new, "2.1.5")
            self.assertIn("<version>2.1.5</version>", text)
            self.assertIn("<parent><version>3.2.6</version></parent>", text)

    def test_package_json_bump(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            (d / "package.json").write_text(
                json.dumps({"name": "web", "version": "1.2.44.2"}, indent=2) + "\n",
                encoding="utf-8",
            )
            with redirect_stdout(io.StringIO()):
                new = bump.bump_package_json(d, "debug", False)
            self.assertEqual(new, "1.2.44.3")
            obj = json.loads((d / "package.json").read_text(encoding="utf-8"))
            self.assertEqual(obj["version"], "1.2.44.3")

    def test_missing_files(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(OpsError):
                bump.bump_pom(Path(td), "normal", True)
            with self.assertRaises(OpsError):
                bump.bump_package_json(Path(td), "normal", True)


if __name__ == "__main__":
    unittest.main()

"""CLI 契约：JSON 信封、退出码、分层帮助、补全、-- 终止符、dry-run 语义。"""
from __future__ import annotations

import unittest

from support import run_cli, run_json, run_module

from itpops.errors import EXIT_OK, EXIT_USAGE


class EnvelopeTest(unittest.TestCase):
    def test_json_envelope_shape(self):
        payload, rc = run_json("version", "next", "1.2.42")
        self.assertEqual(rc, EXIT_OK)
        self.assertTrue(payload["ok"])
        self.assertEqual(payload["data"], {"current": "1.2.42", "next": "1.2.43"})
        self.assertIsNone(payload["error"])
        self.assertIn("duration_ms", payload)
        self.assertIsInstance(payload["command"], list)

    def test_json_error_envelope(self):
        payload, rc = run_json("nope", "x")
        self.assertEqual(rc, EXIT_USAGE)
        self.assertFalse(payload["ok"])
        self.assertEqual(payload["error"]["code"], "USAGE")
        self.assertEqual(payload["error"]["exit"], EXIT_USAGE)

    def test_unknown_global_flag(self):
        r = run_cli("--bogus")
        self.assertEqual(r.returncode, EXIT_USAGE)
        self.assertIn("未知参数", r.stderr)


class HelpTest(unittest.TestCase):
    def test_root_help(self):
        r = run_cli("-h")
        self.assertEqual(r.returncode, EXIT_OK)
        self.assertIn("用法:", r.stdout)
        self.assertIn("completion", r.stdout)

    def test_command_help_comes_from_catalog(self):
        r = run_cli("web", "release", "-h")
        self.assertEqual(r.returncode, EXIT_OK)
        self.assertIn("itp-ops web release", r.stdout)
        self.assertIn("--yes", r.stdout)

    def test_help_never_executes_command(self):
        # 旧 bug：`cloudos scale -h` 会把 -h 当应用名执行
        r = run_cli("cloudos", "scale", "-h")
        self.assertEqual(r.returncode, EXIT_OK)
        self.assertIn("itp-ops cloudos scale", r.stdout)


class CompletionTest(unittest.TestCase):
    def test_bash_completion_lists_domains(self):
        r = run_cli("completion", "bash")
        self.assertEqual(r.returncode, EXIT_OK)
        for d in ("cloudos", "buildx", "sentinel", "web"):
            self.assertIn(d, r.stdout)

    def test_unknown_shell(self):
        r = run_cli("completion", "fish")
        self.assertEqual(r.returncode, EXIT_USAGE)


class ParsingTest(unittest.TestCase):
    def test_double_dash_terminator(self):
        r = run_cli("--", "version", "next", "1.2.42")
        self.assertEqual(r.stdout.strip(), "1.2.43")

    def test_dry_run_without_preview_is_usage_error(self):
        r = run_cli("--dry-run", "k8s", "deploy", "fep-dev-server")
        self.assertEqual(r.returncode, EXIT_USAGE)
        self.assertIn("--dry-run 不可用", r.stderr)


class DirectPythonEntryTest(unittest.TestCase):
    """「其他直接用 python」：特性包自身也能 -m 直调（不经 itp-ops.sh）。"""

    def test_buildx_module_entry(self):
        r = run_module("itpops.buildx", "-h")
        self.assertEqual(r.returncode, 0)
        self.assertIn("itp-ops.sh buildx", r.stderr)

    def test_sentinel_module_entry(self):
        r = run_module("itpops.sentinel", "-h")
        self.assertEqual(r.returncode, 0)
        self.assertIn("itp-ops.sh sentinel", r.stderr)

    def test_web_release_module_entry(self):
        r = run_module("itpops.web.frontend_release", "--help")
        self.assertIn("--skip-build", r.stdout)


if __name__ == "__main__":
    unittest.main()

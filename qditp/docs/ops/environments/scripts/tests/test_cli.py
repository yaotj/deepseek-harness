"""CLI 契约：JSON 信封、退出码、分层帮助、补全、-- 终止符、dry-run 语义。"""
from __future__ import annotations

import subprocess
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

    def test_completion_help_shows_how_to_enable(self):
        r = run_cli("completion", "-h")
        self.assertEqual(r.returncode, EXIT_OK)
        self.assertIn("source <(", r.stdout)
        self.assertIn("bash_completion.d", r.stdout)
        self.assertIn("compinit", r.stdout)

    def test_bash_completion_includes_flow_and_domain_flags(self):
        r = run_cli("completion", "bash")
        self.assertIn("flow) COMPREPLY", r.stdout)
        self.assertIn("list backend frontend jvm", r.stdout)
        self.assertIn("--module", r.stdout)
        self.assertIn("--app", r.stdout)
        self.assertIn("--app-version", r.stdout)

    def test_bash_completion_includes_short_interactive_flag(self):
        r = run_cli("completion", "bash")
        line = next(l for l in r.stdout.splitlines() if l.strip().startswith("flow) COMPREPLY"))
        self.assertIn("--interactive", line)
        self.assertRegex(line, r"(?<![-\w])-i(?![-\w])")

    def test_bash_completion_completes_option_values(self):
        r = run_cli("completion", "bash")
        for fragment in ('-e) COMPREPLY', 'hongdao test', 'text json', '4Gi 8Gi'):
            self.assertIn(fragment, r.stdout)

    def test_completion_scripts_pass_shell_syntax_check(self):
        for shell in ("bash", "zsh"):
            with self.subTest(shell=shell):
                script = run_cli("completion", shell).stdout
                r = subprocess.run([shell, "-n"], input=script,
                                   capture_output=True, text=True)
                self.assertEqual(r.returncode, 0, r.stderr)

    def test_zsh_completion_includes_flow(self):
        r = run_cli("completion", "zsh")
        self.assertIn("flow) _values", r.stdout)
        self.assertIn("list backend frontend jvm", r.stdout)


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
        # 2.6.0：模块级直调也转发统一入口；帮助由 CATALOG 渲染且参数明细不丢
        r = run_module("itpops.web.frontend_release", "--help")
        self.assertEqual(r.returncode, 0)
        self.assertIn("--skip-build", r.stdout)
        self.assertIn("itp-ops web release", r.stdout)


if __name__ == "__main__":
    unittest.main()

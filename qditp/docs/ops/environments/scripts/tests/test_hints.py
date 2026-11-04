"""输入纠错提示（did-you-mean）与交互式发布向导的离线单测。"""
from __future__ import annotations

import io
import unittest
from contextlib import redirect_stdout
from types import SimpleNamespace
from unittest import mock

from support import run_cli, run_json

from itpops.cli import suggest
from itpops.cli.registry import domain_names
from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_OK, EXIT_USAGE, OpsError
from itpops.workflows import runner, wizard


class SuggestUnitTest(unittest.TestCase):
    def test_closest_domain_and_flag(self):
        self.assertEqual(suggest.closest("clouods", domain_names()), ["cloudos"])
        self.assertIn("--env", suggest.closest("--evn", ["--env", "--yes", "--trace"]))

    def test_no_match_returns_empty(self):
        self.assertEqual(suggest.closest("zzzzzz", ["cloudos", "flow"]), [])

    def test_append_hint_format(self):
        text = suggest.append_hint("未知域：clouods", "clouods", domain_names())
        self.assertIn("你是不是想找：cloudos", text)
        self.assertEqual(suggest.append_hint("未知", "zzzzzz", ["cloudos"]), "未知")


class SuggestCliTest(unittest.TestCase):
    def test_unknown_domain_suggests(self):
        r = run_cli("clouods")
        self.assertEqual(r.returncode, EXIT_USAGE)
        self.assertIn("你是不是想找：cloudos", r.stderr)

    def test_unknown_cloudos_subcommand_suggests(self):
        r = run_cli("cloudos", "apss")
        self.assertIn("你是不是想找：apps", r.stderr)

    def test_unknown_flow_suggests(self):
        r = run_cli("flow", "backnd")
        self.assertEqual(r.returncode, EXIT_USAGE)
        self.assertIn("你是不是想找：backend", r.stderr)

    def test_unknown_flow_flag_suggests(self):
        r = run_cli("flow", "backend", "face-pay-server", "--szie", "4Gi")
        self.assertIn("你是不是想找：--size", r.stderr)

    def test_unknown_global_flag_suggests(self):
        r = run_cli("--evn", "test")
        self.assertEqual(r.returncode, EXIT_USAGE)
        self.assertIn("--env", r.stderr)


class WizardCliTest(unittest.TestCase):
    def test_guide_default_is_non_interactive(self):
        r = run_cli("flow", "guide")
        self.assertEqual(r.returncode, EXIT_OK)
        self.assertIn("非交互模式", r.stdout)
        self.assertIn("flow backend", r.stdout)
        self.assertIn("guide -i", r.stdout)

    def test_guide_json_envelope(self):
        payload, rc = run_json("flow", "guide")
        self.assertEqual(rc, EXIT_OK)
        self.assertFalse(payload["data"]["interactive"])
        self.assertEqual(len(payload["data"]["commands"]), 3)

    def test_guide_help_from_catalog(self):
        r = run_cli("flow", "guide", "-h")
        self.assertEqual(r.returncode, EXIT_OK)
        self.assertIn("-i / --interactive", r.stdout)


class WizardInteractiveTest(unittest.TestCase):
    def tearDown(self):
        set_context(Ctx())

    def _tty(self, answers):
        out = io.StringIO()
        with mock.patch("sys.stdin", SimpleNamespace(isatty=lambda: True)), \
                mock.patch("builtins.input", side_effect=list(answers)), \
                redirect_stdout(out):
            set_context(Ctx())
            rc = wizard.run(interactive=True)
        return rc, out.getvalue()

    def test_backend_wizard_builds_command_and_shows_steps(self):
        rc, text = self._tty(["1", "face-pay-server", "", "", "", "test"])
        self.assertEqual(rc, EXIT_OK)
        self.assertIn("./itp-ops.sh flow backend face-pay-server --env test", text)
        self.assertIn("云管台连通性自检", text)
        self.assertIn("切镜像执行", text)
        self.assertNotIn("--yes", text.split("已生成命令")[1].split("\n")[1])

    def test_frontend_wizard_debug(self):
        rc, text = self._tty(["2", "2", "test"])
        self.assertEqual(rc, EXIT_OK)
        self.assertIn("flow frontend --debug --env test", text)

    def test_jvm_wizard_size(self):
        rc, text = self._tty(["3", "face-pay-server", "8Gi", "test"])
        self.assertEqual(rc, EXIT_OK)
        self.assertIn("flow jvm face-pay-server --size 8Gi --env test", text)

    def test_wizard_never_runs_steps(self):
        with mock.patch("sys.stdin", SimpleNamespace(isatty=lambda: True)), \
                mock.patch("builtins.input", side_effect=["1", "face-pay-server", "", "", "", "test"]), \
                mock.patch.object(runner, "run") as rr, redirect_stdout(io.StringIO()):
            set_context(Ctx())
            wizard.run(interactive=True)
        rr.assert_not_called()

    def test_eof_is_usage_error_not_hang(self):
        with mock.patch("sys.stdin", SimpleNamespace(isatty=lambda: True)), \
                mock.patch("builtins.input", side_effect=EOFError), \
                redirect_stdout(io.StringIO()):
            set_context(Ctx())
            with self.assertRaises(OpsError) as cm:
                wizard.run(interactive=True)
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)


if __name__ == "__main__":
    unittest.main()

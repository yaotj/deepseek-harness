"""质量门：JSON 输出隔离、RSA 唯一实现、ui 开关、命令级帮助渲染。"""
from __future__ import annotations

import base64
import io
import json
import os
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

from itpops.cli import help as help_mod
from itpops.cli.main import main as cli_main
from itpops.core import crypto, ui
from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_PRECONDITION, OpsError
from itpops.web.frontend_release import HONGDAO_PUBKEY_B64


class JsonIsolationTest(unittest.TestCase):
    """契约由入口强制：域里残留的 print 不得污染 stdout 信封。"""

    def test_stray_stdout_goes_to_stderr(self):
        def fake_dispatch(domain, args):
            print("PROGRESS 污染 stdout")   # 历史实现（web/buildx/sentinel）的写法
            return 0

        out, err = io.StringIO(), io.StringIO()
        with mock.patch("itpops.cli.main._dispatch", fake_dispatch):
            with redirect_stdout(out), redirect_stderr(err):
                rc = cli_main(["-o", "json", "version", "next", "1.2.42"])
        self.assertEqual(rc, 0)
        payload = json.loads(out.getvalue())        # 仍是单条合法 JSON
        self.assertTrue(payload["ok"])
        self.assertIn("PROGRESS", err.getvalue())   # 污染被改道到 stderr

    def test_text_mode_keeps_stdout(self):
        out = io.StringIO()
        with redirect_stdout(out):
            cli_main(["version", "next", "1.2.42"])
        self.assertEqual(out.getvalue().strip(), "1.2.43")


class CryptoTests(unittest.TestCase):
    """RSA 唯一实现（core/crypto.py）——形状与失败分支。"""

    def test_encrypt_shape(self):
        enc = crypto.rsa_encrypt(HONGDAO_PUBKEY_B64, b"QDitp@123!@#")
        self.assertEqual(len(enc), crypto.EXPECTED_B64_LEN)
        self.assertEqual(len(base64.b64decode(enc)), 128)   # 1024-bit

    def test_bad_pubkey(self):
        with self.assertRaises(OpsError) as cm:
            crypto.parse_pubkey("not-a-valid-key!!")
        self.assertEqual(cm.exception.exit_code, EXIT_PRECONDITION)

    def test_plaintext_too_long(self):
        with self.assertRaises(OpsError):
            crypto.rsa_encrypt(HONGDAO_PUBKEY_B64, b"x" * 200)


class UiSwitchesTest(unittest.TestCase):
    def tearDown(self):
        set_context(Ctx())

    def test_quiet_suppresses_info_keeps_warn(self):
        set_context(Ctx(quiet=True))
        err = io.StringIO()
        with redirect_stderr(err):
            ui.log_info("info line")
            ui.log_warn("warn line")
        self.assertNotIn("[INFO]", err.getvalue())
        self.assertIn("[WARN]", err.getvalue())

    def test_json_mode_suppresses_out(self):
        set_context(Ctx(json_mode=True))
        out = io.StringIO()
        with redirect_stdout(out):
            ui.out("result line")
        self.assertEqual(out.getvalue(), "")

    def test_no_color_env_wins(self):
        set_context(Ctx())
        with mock.patch.dict(os.environ, {"NO_COLOR": "1"}):
            os.environ.pop("CLICOLOR_FORCE", None)
            self.assertFalse(ui.color_enabled())

    def test_clicolor_force(self):
        set_context(Ctx())
        env = {k: v for k, v in os.environ.items() if k != "NO_COLOR"}
        env["CLICOLOR_FORCE"] = "1"
        with mock.patch.dict(os.environ, env, clear=True):
            self.assertTrue(ui.color_enabled())


class HelpRenderTest(unittest.TestCase):
    def test_render_single_word_command(self):
        entry = help_mod.find_subcommand("cloudos", "scale")
        self.assertIsNotNone(entry)
        text = help_mod.render_command_help("cloudos", entry)
        self.assertIn("itp-ops cloudos scale", text)
        self.assertIn("--yes", text)

    def test_render_multi_word_command(self):
        self.assertIsNotNone(help_mod.find_subcommand("cloudos", "image build"))
        self.assertIsNotNone(help_mod.find_subcommand("web", "mount-export"))


if __name__ == "__main__":
    unittest.main()

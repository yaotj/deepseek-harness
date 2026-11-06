"""log vl（VictoriaLogs 查询）业务级单测 —— 2026-11-06 新增。

用户裁定「封装但不脱敏」，所以最要紧的一条判据是：VL 返回的每一行
MUST 原样输出，NEVER 裁剪 header / token 字段。
"""
from __future__ import annotations

import io
import os
import shlex
import unittest
from contextlib import redirect_stderr, redirect_stdout
from types import SimpleNamespace
from unittest import mock

from itpops.commands import log as logmod
from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_USAGE, OpsError

# 含敏感请求头的一行，用来证明「不脱敏」
SENSITIVE_LINE = (
    '{"_msg":"receive request header={Admin-Token=abc.def, '
    'authorization=Bearer xyz, cookie=username=admin}","app":"web-server",'
    '"level":"INFO","_time":"2026-11-06T08:38:33Z"}'
)


class VlQueryTest(unittest.TestCase):
    def test_shortcut_builds_logsql(self):
        q = logmod.build_vl_query(words=["upload-file"], app="web-server",
                                  level="ERROR", last="30m", limit=5)
        self.assertEqual(
            q, '_time:30m AND app:"web-server" AND level:ERROR AND upload-file '
               '| sort by (_time desc) | limit 5')

    def test_defaults(self):
        q = logmod.build_vl_query()
        self.assertEqual(q, '_time:1h | sort by (_time desc) | limit 20')

    def test_expr_is_verbatim(self):
        expr = "_time:1h | stats by (app) count()"
        self.assertEqual(logmod.build_vl_query(expr=expr, app="ignored", limit=99), expr)

    def test_user_pipeline_not_wrapped(self):
        q = logmod.build_vl_query(words=["* | stats by (level) count()"], limit=20)
        self.assertNotIn("sort by", q)

    def test_no_limit(self):
        self.assertEqual(logmod.build_vl_query(limit=0), "_time:1h")

    def test_remote_cmd_is_shell_safe(self):
        q = 'app:"web-server" AND level:ERROR | limit 2'
        cmd = logmod.vl_remote_cmd(q)
        parts = shlex.split(cmd)
        self.assertEqual(parts[0], "curl")
        self.assertIn("--data-urlencode", parts)
        self.assertEqual(parts[-1], "query=" + q)

    def test_url_override(self):
        with mock.patch.dict(os.environ, {"ITP_VL_URL": "http://example/query"}):
            self.assertEqual(logmod._vl_url(), "http://example/query")

    def test_parse_rejects_mixed_expr(self):
        with self.assertRaises(OpsError) as cm:
            logmod._vl_parse(["--expr", "* | stats count()", "--app", "web-server"])
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)

    def test_parse_equals_form(self):
        o = logmod._vl_parse(["--app=web-server", "--level=ERROR", "--limit=3"])
        self.assertEqual((o["app"], o["level"], o["limit"]), ("web-server", "ERROR", 3))


class VlOutputTest(unittest.TestCase):
    def tearDown(self):
        set_context(Ctx())

    def _run(self, args, lines):
        r = SimpleNamespace(returncode=0, stdout="\n".join(lines) + "\n", stderr="")
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(logmod.remote, "remote_exec", return_value=r) as m, \
                redirect_stdout(out), redirect_stderr(err):
            rc = logmod._vl(args)
        return rc, out.getvalue(), err.getvalue(), m

    def test_text_output_is_verbatim_no_redaction(self):
        """核心判据：含 Admin-Token / Bearer / cookie 的行 MUST 原样出现在 stdout。"""
        set_context(Ctx())
        rc, out, _, _ = self._run(["--app", "web-server", "--limit", "1"], [SENSITIVE_LINE])
        self.assertEqual(rc, 0)
        self.assertIn("Admin-Token=abc.def", out)
        self.assertIn("Bearer xyz", out)
        self.assertIn("cookie=username=admin", out)
        self.assertTrue(out.strip().startswith('{"_msg"'))  # 未被格式化/裁剪

    def test_remote_cmd_uses_composed_query(self):
        set_context(Ctx())
        _, _, _, m = self._run(["--app", "web-server", "--level", "ERROR"], [SENSITIVE_LINE])
        cmd = m.call_args.args[0]
        self.assertIn('query=_time:1h AND app:', cmd)
        self.assertIn('AND level:ERROR', cmd)

    def test_json_mode_envelope_keeps_full_log(self):
        set_context(Ctx(json_mode=True, output="json"))
        with mock.patch.object(logmod.remote, "remote_exec",
                               return_value=SimpleNamespace(
                                   returncode=0, stdout=SENSITIVE_LINE + "\n", stderr="")), \
                mock.patch.object(logmod.ui, "result") as result, \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            rc = logmod._vl(["--limit", "1"])
        self.assertEqual(rc, 0)
        payload = result.call_args.args[0]
        self.assertEqual(payload["count"], 1)
        self.assertIn("Admin-Token", payload["logs"][0]["_msg"])

    def test_ssh_transport_failure_is_exit_4(self):
        set_context(Ctx())
        with mock.patch.object(logmod.remote, "remote_exec",
                               return_value=SimpleNamespace(
                                   returncode=logmod.remote.SSH_TRANSPORT_EXIT,
                                   stdout="", stderr="")), \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            with self.assertRaises(OpsError) as cm:
                logmod._vl([])
        self.assertEqual(cm.exception.exit_code, 4)


if __name__ == "__main__":
    unittest.main()

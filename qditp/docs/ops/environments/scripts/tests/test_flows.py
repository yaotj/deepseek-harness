"""flow 工作流离线单测：步骤声明、预演只读、写门、生产保护、变量传递、失败即断。

全部用 mock 隔离 subprocess，绝不碰真实环境（网络断着也能跑）。
"""
from __future__ import annotations

import io
import unittest
from contextlib import redirect_stderr, redirect_stdout
from types import SimpleNamespace
from unittest import mock

from support import run_cli, run_json

from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_EXEC, EXIT_PRECONDITION, EXIT_USAGE, OpsError
from itpops.workflows import definitions, runner


def _r(stdout="", rc=0, stderr=""):
    return SimpleNamespace(returncode=rc, stdout=stdout, stderr=stderr)


def _opts(**kw):
    base = {"env": "test", "module": "face-pay-server", "app": "face-pay-server"}
    base.update(kw)
    return base


class CliFlowTest(unittest.TestCase):
    def test_flow_list_is_local(self):
        r = run_cli("flow", "list")
        self.assertEqual(r.returncode, 0)
        for name in ("backend", "frontend", "jvm"):
            self.assertIn(name, r.stdout)

    def test_flow_list_json_envelope(self):
        payload, rc = run_json("flow", "list")
        self.assertEqual(rc, 0)
        self.assertEqual(len(payload["data"]["flows"]), 3)
        self.assertEqual(payload["data"]["default_env"], "test")

    def test_dry_run_lists_steps_without_yes(self):
        r = run_cli("flow", "backend", "face-pay-server", "--env", "test")
        self.assertEqual(r.returncode, 0)
        self.assertIn("未执行任何步骤", r.stdout)
        self.assertIn("buildx", r.stdout)
        self.assertIn("cloudos deploy", r.stdout)

    def test_production_requires_allow_prod(self):
        r = run_cli("flow", "jvm", "face-pay-server", "--env", "hongdao")
        self.assertEqual(r.returncode, EXIT_PRECONDITION)
        self.assertIn("--allow-prod", r.stderr)

    def test_unknown_flow_is_usage_error(self):
        r = run_cli("flow", "nope")
        self.assertEqual(r.returncode, EXIT_USAGE)

    def test_bad_size_is_usage_error(self):
        r = run_cli("flow", "jvm", "app", "--size", "99Gi", "--env", "test")
        self.assertEqual(r.returncode, EXIT_USAGE)


    def test_app_name_as_module_is_rejected_early(self):
        r = run_cli("flow", "backend", "face-pay", "--env", "test")
        self.assertEqual(r.returncode, EXIT_PRECONDITION)
        self.assertIn("模块目录不存在", r.stderr)
        self.assertIn("--app", r.stderr)

    def test_module_flag_can_replace_positional(self):
        r = run_cli("flow", "backend", "--module", "face-pay-server",
                    "--app", "face-pay", "--env", "test")
        self.assertEqual(r.returncode, 0)
        self.assertIn("应用=face-pay", r.stdout)

    def test_jvm_app_flag_alias(self):
        r = run_cli("flow", "jvm", "--app", "face-pay", "--env", "test")
        self.assertEqual(r.returncode, 0)
        self.assertIn("cloudos env-set face-pay", r.stdout)

    def test_preview_shows_module_app_image(self):
        r = run_cli("flow", "backend", "face-pay-server", "--env", "test")
        self.assertIn("模块=face-pay-server", r.stdout)
        self.assertIn("应用=face-pay-server", r.stdout)
        self.assertIn("镜像=face-pay-server", r.stdout)


class DefinitionTest(unittest.TestCase):
    def test_backend_has_seven_ordered_steps(self):
        flow = definitions.backend(_opts())
        self.assertEqual(flow.name, "backend")
        self.assertEqual(len(flow.steps), 7)
        argv = [" ".join(s.argv) for s in flow.steps]
        self.assertIn("cloudos health", argv[0])
        self.assertIn("version bump", argv[1])
        self.assertIn("mvn", argv[2])
        self.assertIn("--check", argv[3])
        self.assertIn("--jar", argv[4])
        self.assertIn("buildx.Dockerfile", argv[4])
        self.assertNotIn("--yes", argv[5])       # deploy 预演
        self.assertIn("--yes", argv[6])          # 切镜像执行
        self.assertIn("{version}", " ".join(argv))

    def test_backend_fixed_version_skips_bump(self):
        flow = definitions.backend(_opts(version="1.0.91"))
        self.assertEqual(len(flow.steps), 6)
        self.assertNotIn("bump", " ".join(" ".join(s.argv) for s in flow.steps))

    def test_backend_custom_app_image_jar(self):
        flow = definitions.backend(_opts(app="fep-app", image="fep-app", jar="/tmp/x.jar"))
        joined = " ".join(" ".join(s.argv) for s in flow.steps)
        self.assertIn("fep-app", joined)
        self.assertIn("/tmp/x.jar", joined)

    def test_frontend_defaults_to_bump(self):
        flow = definitions.frontend(_opts())
        self.assertEqual(len(flow.steps), 2)
        rel = " ".join(flow.steps[1].argv)
        self.assertIn("web release --bump", rel)
        self.assertIn("--yes", rel)

    def test_frontend_debug_and_skip_build(self):
        rel = " ".join(definitions.frontend(_opts(debug=True, skip_build=True)).steps[1].argv)
        self.assertIn("--debug", rel)
        self.assertIn("--skip-build", rel)
        self.assertNotIn("--bump", rel)

    def test_jvm_presets(self):
        four = " ".join(definitions.jvm(_opts()).steps[1].argv)
        eight = " ".join(definitions.jvm(_opts(size="8Gi")).steps[1].argv)
        self.assertIn("max_memory=4000", four)
        self.assertIn("max_memory=8000", eight)
        self.assertIn("reserved_memory=2000", eight)
        with self.assertRaises(OpsError) as cm:
            definitions.jvm(_opts(size="99Gi"))
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)


class RunnerTest(unittest.TestCase):
    def tearDown(self):
        set_context(Ctx())

    def test_render_replaces_variables(self):
        step = runner.Step("x", ("buildx", "app", "{version}", "--env", "{env}"))
        self.assertEqual(runner._render(step, {"version": "1.0.91", "env": "test"}),
                         ["buildx", "app", "1.0.91", "--env", "test"])
        self.assertEqual(runner._preview(step, {}),
                         ["buildx", "app", "{version}", "--env", "{env}"])

    def test_preview_never_runs_subprocess(self):
        set_context(Ctx(env="test", yes=False))
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(runner.subprocess, "run") as sp, \
                redirect_stdout(out), redirect_stderr(err):
            rc = runner.run(definitions.frontend(_opts()), _opts())
        self.assertEqual(rc, 0)
        sp.assert_not_called()
        self.assertIn("未执行任何步骤", out.getvalue())

    def test_dry_run_flag_overrides_yes(self):
        set_context(Ctx(env="test", yes=True, dry_run=True))
        with mock.patch.object(runner.subprocess, "run") as sp, \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            rc = runner.run(definitions.jvm(_opts()), _opts())
        self.assertEqual(rc, 0)
        sp.assert_not_called()

    def test_execute_chain_captures_version(self):
        set_context(Ctx(env="test", yes=True))
        responses = [
            _r("结论：可以操作"),
            _r("  ✓ pom.xml: 1.0.90 → 1.0.91  (sb=3.2.6)"),
            _r("[INFO] BUILD SUCCESS"),
            _r("[check] 现有镜像"),
            _r("✓ 构建完成：itp/face-pay-server:1.0.91"),
            _r("以上为预演（dry-run）"),
            _r("已收敛：1 个实例全部运行在 ...:1.0.91"),
        ]
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(runner.subprocess, "run", side_effect=responses) as sp, \
                redirect_stdout(out), redirect_stderr(err):
            rc = runner.run(definitions.backend(_opts()), _opts())
        self.assertEqual(rc, 0)
        self.assertEqual(sp.call_count, 7)
        cmd = sp.call_args_list[4].args[0]
        self.assertIn("1.0.91", cmd)            # 上一步捕获的版本传给了 buildx
        self.assertIn("✓ 工作流 backend 完成（7 步）", out.getvalue())

    def test_failure_stops_the_chain(self):
        set_context(Ctx(env="test", yes=True))
        with mock.patch.object(runner.subprocess, "run",
                               side_effect=[_r("boom", rc=5)]) as sp, \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            with self.assertRaises(OpsError) as cm:
                runner.run(definitions.jvm(_opts()), _opts())
        self.assertEqual(cm.exception.exit_code, EXIT_EXEC)
        self.assertEqual(sp.call_count, 1)      # 第一步失败后不再执行

    def test_missing_expect_stops_even_on_exit_zero(self):
        set_context(Ctx(env="test", yes=True))
        step = runner.Step("自检", ("cloudos", "health"), expect="可以操作")
        with mock.patch.object(runner.subprocess, "run", side_effect=[_r("HTTP 200 OK")]), \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            with self.assertRaises(OpsError) as cm:
                runner.run(runner.Flow("t", "t", (step,)), _opts())
        self.assertEqual(cm.exception.exit_code, EXIT_EXEC)
        self.assertIn("缺少判据", str(cm.exception))

    def test_capture_failure_is_explicit(self):
        set_context(Ctx(env="test", yes=True))
        step = runner.Step("bump", ("version", "bump"), capture=r"→\s*([0-9.]+)", var="version")
        with mock.patch.object(runner.subprocess, "run", side_effect=[_r("什么都没有")]), \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            with self.assertRaises(OpsError) as cm:
                runner.run(runner.Flow("t", "t", (step,)), _opts())
        self.assertIn("没能从输出捕获变量", str(cm.exception))


if __name__ == "__main__":
    unittest.main()

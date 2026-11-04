"""cloudos 业务级单测 —— 按业务段拆分 impl.py 的行为基线。

2026-11-04：既有单测主要覆盖 CLI 契约，这里补的是**真实业务判据**
（规格换算、镜像引用安全校验、uuid 解析、参数解析、部署计划、k8s 快照降级），
作为把 impl.py 拆成 common / apps / scaling / deploy / images 的前提。

拆分前后跑的是同一组断言，只是导入与 mock 目标改指向各业务段模块；
断言逐条不变 + 全绿，才能证明拆分是纯搬移。
"""
from __future__ import annotations

import io
import os
import unittest
from contextlib import redirect_stderr
from types import SimpleNamespace
from unittest import mock

from itpops.cloudos import common, deploy, images, impl, scaling
from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_USAGE, OpsError


class SpecUnitsTest(unittest.TestCase):
    """规格换算与比较（scale / spec-set 的幂等判据）—— scaling 段。"""

    def test_cpu_milli(self):
        self.assertEqual(scaling._cpu_milli("4000m"), 4000)
        self.assertEqual(scaling._cpu_milli("2"), 2000)
        self.assertEqual(scaling._cpu_milli("1.5"), 1500)

    def test_mem_mi(self):
        self.assertEqual(scaling._mem_mi("8192Mi"), 8192)
        self.assertEqual(scaling._mem_mi("1Gi"), 1024)
        self.assertEqual(scaling._mem_mi("2048Ki"), 2)
        self.assertEqual(scaling._mem_mi(str(1024 * 1024)), 1)

    def test_spec_fields_pads_short_rows(self):
        self.assertEqual(scaling._spec_fields("c|1|2|3|4"), ("c", "1", "2", "3", "4"))
        self.assertEqual(scaling._spec_fields("c"), ("c", "", "", "", ""))

    def test_spec_matches_unit_equivalence(self):
        self.assertTrue(scaling._spec_matches("4000m", "8192Mi", "4000m", "8192Mi", "4", "8Gi"))
        self.assertFalse(scaling._spec_matches("4000m", "8192Mi", "4000m", "8192Mi", "8000m", "8192Mi"))
        self.assertFalse(scaling._spec_matches("x", "y", "z", "w", "4000m", "8192Mi"))


class ArgUnitsTest(unittest.TestCase):
    def test_need_reports_usage_error(self):
        self.assertEqual(common._need(["a", "b"], 1, "缺参数"), "b")
        with self.assertRaises(OpsError) as cm:
            common._need(["a"], 1, "缺参数")
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)

    def test_image_parse_args_any_order(self):
        mod, ver, state = images._image_parse_args(
            ["--jar", "a.jar", "fep-app", "1.2.44", "--keep-pkg", "--project", "itp"])
        self.assertEqual((mod, ver), ("fep-app", "1.2.44"))
        self.assertEqual(state["jar"], "a.jar")
        self.assertEqual(state["project"], "itp")

    def test_image_parse_args_rejects_bad_flags(self):
        for argv in (["--jar"], ["--bogus"]):
            with self.subTest(argv=argv), self.assertRaises(OpsError) as cm:
                images._image_parse_args(argv)
            self.assertEqual(cm.exception.exit_code, EXIT_USAGE)

    def test_image_parse_args_double_dash(self):
        mod, ver, _ = images._image_parse_args(["--", "-weird", "1.0.0"])
        self.assertEqual((mod, ver), ("-weird", "1.0.0"))


class UuidUnitsTest(unittest.TestCase):
    def test_hex_uuid_passes_through(self):
        key = "a" * 32
        with mock.patch.object(common, "_req") as req:
            self.assertEqual(common.resolve_app_uuid(key), key)
        req.assert_not_called()

    def test_exact_match(self):
        rows = {"data": [{"name": "fep-app", "uuid": "U1"}, {"name": "acc-es", "uuid": "U2"}]}
        with mock.patch.object(common, "_req", return_value=rows), redirect_stderr(io.StringIO()):
            self.assertEqual(common.resolve_app_uuid("fep-app"), "U1")

    def test_not_found_and_ambiguous(self):
        cases = ({"data": []},
                 {"data": [{"name": "fep-app-a", "uuid": "U1"},
                           {"name": "fep-app-b", "uuid": "U2"}]})
        for rows in cases:
            with self.subTest(rows=rows), mock.patch.object(common, "_req", return_value=rows), \
                    redirect_stderr(io.StringIO()):
                with self.assertRaises(OpsError):
                    common.resolve_app_uuid("fep-app")


class HarborProjectTest(unittest.TestCase):
    def tearDown(self):
        set_context(Ctx())

    def test_default_is_legacy_itp(self):
        set_context(Ctx())
        with mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop("HARBOR_PROJECT", None)
            self.assertEqual(images._harbor_project_default(), "itp")

    def test_explicit_env_uses_profile(self):
        set_context(Ctx(env="hongdao", env_explicit=True))
        with mock.patch.object(images.envx, "current", return_value=SimpleNamespace(name="hongdao")):
            self.assertEqual(images._harbor_project_default(), "itpb")
        set_context(Ctx(env="test", env_explicit=True))
        with mock.patch.object(images.envx, "current", return_value=SimpleNamespace(name="test")):
            self.assertEqual(images._harbor_project_default(), "itp")


class DeployPlanTest(unittest.TestCase):
    """升级计划的安全判据：NEVER 同 tag 覆盖 / 跨仓库跨项目 / 镜像名错配 / version_id 形态。"""

    @staticmethod
    def _app(meta_extra=None):
        metadata = {
            "container_image": "reg/itp/app:1.0.0",
            "env": {"SERVICE_URL": "http://x"},
            "strategy": "rolling",
            "container_name": "app",
        }
        metadata.update(meta_extra or {})
        return {"data": {"uuid": "U1", "name": "app", "version": "1.0.0",
                         "instance_num": 2, "metadata": metadata}}

    def _plan(self, image_arg, meta_extra=None, version_override=""):
        with mock.patch.object(deploy, "resolve_app_uuid", return_value="U1"), \
                mock.patch.object(deploy, "_req", return_value=self._app(meta_extra)), \
                mock.patch.object(deploy, "_k8s_deploy_for_cloudos", return_value=None), \
                redirect_stderr(io.StringIO()):
            return deploy._deploy_plan("app", image_arg, version_override, "reg/itp/app:1.0.0")

    def test_bare_tag_builds_full_reference_and_carries_env(self):
        plan, body = self._plan("1.0.1")
        self.assertEqual(plan["new_img"], "reg/itp/app:1.0.1")
        self.assertEqual(plan["new_ver"], "1.0.1")
        self.assertEqual(body["container_image"], "reg/itp/app:1.0.1")
        self.assertEqual(body["env"], {"SERVICE_URL": "http://x"})   # env 全量回传（IT-020）
        self.assertNotIn("version_id", body)

    def test_same_tag_is_rejected(self):
        with self.assertRaises(deploy._PlanError):
            self._plan("1.0.0")

    def test_cross_project_is_rejected(self):
        with self.assertRaises(deploy._PlanError):
            self._plan("reg/itpb/app:1.0.1")

    def test_wrong_image_name_is_rejected(self):
        with self.assertRaises(deploy._PlanError):
            self._plan("other:1.0.1")

    def test_version_id_requires_uuid_shape(self):
        _, body = self._plan("1.0.1", {"version_id": "not-a-uuid"})
        self.assertNotIn("version_id", body)
        _, body = self._plan("1.0.1", {"version_id": "b" * 32})
        self.assertEqual(body["version_id"], "b" * 32)

    def test_app_version_override(self):
        _, body = self._plan("1.0.1", version_override="9.9.9")
        self.assertEqual(body["version"], "9.9.9")


class K8sSnapshotTest(unittest.TestCase):
    """拆分 AST 审计发现的潜伏 bug：异常分支调用了未定义的 `_warn`，会抛 NameError
    而不是按 docstring 承诺「仅返回 None、不阻塞常规发布」。修复后锁定该降级行为。"""

    def _ctx(self):
        return mock.patch.object(deploy.envx, "current",
                                 return_value=SimpleNamespace(k8s_ns="itp"))

    def test_ssh_failure_degrades_to_none(self):
        r = SimpleNamespace(returncode=1, stderr="ssh: unreachable", stdout="")
        err = io.StringIO()
        with mock.patch("subprocess.run", return_value=r), self._ctx(), redirect_stderr(err):
            self.assertIsNone(deploy._k8s_deploy_for_cloudos("app"))
        self.assertIn("取当前 Deployment 失败", err.getvalue())

    def test_ssh_exception_degrades_to_none(self):
        err = io.StringIO()
        with mock.patch("subprocess.run", side_effect=OSError("boom")), \
                self._ctx(), redirect_stderr(err):
            self.assertIsNone(deploy._k8s_deploy_for_cloudos("app"))
        self.assertIn("取当前 Deployment 异常", err.getvalue())


class CatalogShapeTest(unittest.TestCase):
    def test_catalog_is_complete(self):
        cat = impl.CATALOG
        self.assertEqual(cat["name"], "cloudos")
        names = [s["name"] for s in cat["subcommands"]]
        self.assertEqual(len(names), 30)
        self.assertEqual(len(names), len(set(names)))
        for sub in cat["subcommands"]:
            self.assertIsInstance(sub["writes"], bool)
            self.assertTrue(sub["usage"] and sub["summary"])


if __name__ == "__main__":
    unittest.main()

"""cloudos 业务级单测 —— 按业务段拆分 impl.py 的行为基线。

2026-11-04：既有单测主要覆盖 CLI 契约，这里补的是**真实业务判据**
（规格换算、镜像引用安全校验、uuid 解析、参数解析、部署计划、纯 CloudOS 快照校正），
作为把 impl.py 拆成 common / apps / scaling / deploy / images 的前提。

拆分前后跑的是同一组断言，只是导入与 mock 目标改指向各业务段模块；
断言逐条不变 + 全绿，才能证明拆分是纯搬移。
"""
from __future__ import annotations

import io
import os
import unittest
from contextlib import redirect_stderr, redirect_stdout
from types import SimpleNamespace
from unittest import mock

from itpops.cloudos import common, deploy, envcheck, images, impl, scaling
from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_PRECONDITION, EXIT_USAGE, OpsError

# 2026-11-06：resources 快照改为纯 CloudOS 接口后的 mock 返回值
_NO_SNAP = (None, {"source": "none", "corrected": [], "warnings": ["测试：无骨架"]})
_SNAP_OK = ({"kind": "Deployment"}, {"source": "cloudos:image_metadata",
                                    "corrected": [], "warnings": []})


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
                mock.patch.object(deploy, "cloudos_deploy_snapshot", return_value=_NO_SNAP), \
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


class CloudOsSnapshotTest(unittest.TestCase):
    """2026-11-06 裁定：env 升级 resources 快照只走 CloudOS REST，NEVER ssh/kubectl。

    锁定三条业务判据：滞后镜像按 container_image 校正、replicas/规格按 /resources
    实时值校正、本次 env 变更同步进快照；并断言全程零 subprocess 调用。
    """

    @staticmethod
    def _app(*, image="reg/itp/app:1.0.93", snap_image="reg/itp/app:1.0.92"):
        dep = {
            "apiVersion": "apps/v1", "kind": "Deployment",
            "metadata": {"name": "app", "namespace": "itp", "labels": {"app": "app"}},
            "spec": {"replicas": 1, "selector": {"matchLabels": {"app": "app"}},
                     "template": {"metadata": {"labels": {"app": "app"}},
                                  "spec": {"containers": [{
                                      "name": "app", "image": snap_image,
                                      "resources": {"requests": {"cpu": "1", "memory": "2Gi"}},
                                      "env": [{"name": "SERVER_PORT", "value": "8080"},
                                              {"name": "max_memory", "value": "1000"}],
                                  }]}}},
        }
        meta = {"container_image": image, "env": {"SERVER_PORT": "8080"},
                "image_metadata": {"metadata": {"resources": [None, dep]}}}
        return {"uuid": "U1", "name": "app", "metadata": meta, "instance_num": 2}

    @staticmethod
    def _live(replicas=2):
        return {"data": [{"name": "app", "replicas": replicas,
                          "resources": {"requests": {"cpu": "4000m", "memory": "8192Mi"},
                                        "limits": {"cpu": "4000m", "memory": "8192Mi"}}}]}

    def test_no_image_metadata_returns_none(self):
        app = self._app()
        app["metadata"].pop("image_metadata")
        dep, info = deploy.cloudos_deploy_snapshot(app)
        self.assertIsNone(dep)
        self.assertEqual(info["source"], "none")

    def test_stale_image_and_specs_corrected_from_cloudos(self):
        with mock.patch.object(deploy, "_req", return_value=self._live()) as req, \
                mock.patch("subprocess.run", side_effect=AssertionError("NEVER 调 kubectl")):
            dep, info = deploy.cloudos_deploy_snapshot(self._app())
        c = dep["spec"]["template"]["spec"]["containers"][0]
        self.assertEqual(c["image"], "reg/itp/app:1.0.93")     # 不是骨架里的 1.0.92
        self.assertEqual(dep["spec"]["replicas"], 2)
        self.assertEqual(c["resources"]["requests"]["cpu"], "4000m")
        self.assertIn("image", info["corrected"])
        self.assertEqual(req.call_args_list[0].args[0], "GET")
        self.assertIn("/resources", req.call_args_list[0].args[1])

    def test_env_change_is_synced_into_snapshot(self):
        with mock.patch.object(deploy, "_req", return_value=self._live()):
            dep, _ = deploy.cloudos_deploy_snapshot(
                self._app(), set_items={"max_memory": "4000", "NEW_KEY": "v"},
                del_keys=["SERVER_PORT"])
        env = dep["spec"]["template"]["spec"]["containers"][0]["env"]
        by = {e["name"]: e.get("value") for e in env}
        self.assertEqual(by["max_memory"], "4000")
        self.assertEqual(by["NEW_KEY"], "v")
        self.assertNotIn("SERVER_PORT", by)

    def test_resources_endpoint_failure_keeps_skeleton_but_fixes_image(self):
        with mock.patch.object(deploy, "_req", side_effect=OSError("boom")):
            dep, info = deploy.cloudos_deploy_snapshot(self._app())
        self.assertIsNotNone(dep)
        self.assertTrue(any("失败" in w for w in info["warnings"]))
        c = dep["spec"]["template"]["spec"]["containers"][0]
        self.assertEqual(c["image"], "reg/itp/app:1.0.93")     # 校正不依赖该接口
        self.assertEqual(c["resources"]["requests"]["cpu"], "1")  # 规格沿用骨架


class EnvSetTest(unittest.TestCase):
    """IT-020 隐藏契约：env 全量、container_image 必填、version 递增、resources 安全门。"""

    IMG = "os-harbor-svc.default.svc.cloudos:443/itp/face-pay-server:1.0.91"

    @classmethod
    def _app(cls, meta_extra=None, version="1.0.91"):
        meta = {
            "container_image": cls.IMG,
            "env": {"SERVER_PORT": "8080", "DB_HOST": "172.20.222.3:1521"},
            "strategy": "rolling", "container_name": "face-pay-server",
        }
        meta.update(meta_extra or {})
        return {"uuid": "U1", "name": "face-pay-server", "version": version,
                "namespace": "itp", "instance_num": 1, "metadata": meta}

    @staticmethod
    def _profile():
        return mock.patch.object(deploy.envx, "current",
                                 return_value=SimpleNamespace(label="测试环境", name="test", k8s_ns="itp"))

    def test_bump_patch_version(self):
        self.assertEqual(deploy._bump_patch_version("1.0.91"), "1.0.91.1")
        self.assertEqual(deploy._bump_patch_version("1.0.91.1"), "1.0.91.2")
        self.assertEqual(deploy._bump_patch_version(""), "0.0.1.1")

    def test_env_is_full_replacement_with_hidden_contract(self):
        with mock.patch.object(deploy, "cloudos_deploy_snapshot", return_value=_SNAP_OK):
            body, plan = deploy.build_env_patch(
                self._app(),
                set_items={"max_memory": "4000", "reserved_memory": "1000",
                           "max_direct_memory": "100"}, del_keys=[])
        self.assertEqual(body["container_image"], self.IMG)     # 隐藏必填
        self.assertEqual(body["version"], "1.0.91.1")           # version != 当前
        self.assertEqual(body["env"]["SERVER_PORT"], "8080")    # 原有 env 不能丢
        self.assertEqual(body["env"]["DB_HOST"], "172.20.222.3:1521")
        self.assertEqual(body["env"]["max_memory"], "4000")
        self.assertEqual(plan["env_n"], 5)
        self.assertEqual(len(plan["added"]), 3)
        self.assertTrue(plan["resources_snapshot"])
        self.assertIn("resources", body)

    def test_missing_container_image_is_rejected(self):
        with self.assertRaises(OpsError) as cm:
            deploy.build_env_patch(self._app({"container_image": ""}),
                                   set_items={"a": "b"}, del_keys=[])
        self.assertEqual(cm.exception.exit_code, EXIT_PRECONDITION)

    def test_same_version_is_rejected(self):
        with mock.patch.object(deploy, "cloudos_deploy_snapshot", return_value=_NO_SNAP):
            with self.assertRaises(OpsError) as cm:
                deploy.build_env_patch(self._app(), set_items={"a": "b"}, del_keys=[],
                                       version_override="1.0.91")
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)

    def test_delete_and_non_uuid_version_id_is_dropped(self):
        with mock.patch.object(deploy, "cloudos_deploy_snapshot", return_value=_NO_SNAP):
            body, plan = deploy.build_env_patch(
                self._app({"version_id": "not-a-uuid"}), set_items={}, del_keys=["DB_HOST"])
        self.assertNotIn("DB_HOST", body["env"])
        self.assertNotIn("version_id", body)
        self.assertEqual(plan["removed"], ["DB_HOST"])
        self.assertFalse(plan["resources_snapshot"])

    def test_dry_run_never_patches(self):
        set_context(Ctx())                                     # 不带 --yes
        with mock.patch.object(deploy, "resolve_app_uuid", return_value="U1"), \
                mock.patch.object(deploy, "_req", return_value={"data": self._app()}) as req, \
                mock.patch.object(deploy, "cloudos_deploy_snapshot", return_value=_NO_SNAP), \
                self._profile(), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            rc = deploy.env_set(["face-pay-server", "max_memory=4000"])
        self.assertEqual(rc, 0)
        self.assertFalse(any(c.args[0] == "PATCH" for c in req.call_args_list))

    def test_execution_refused_without_resources_snapshot(self):
        """核心安全门：网络不通 / 拿不到 k8s 快照时，--yes 也 MUST 拒绝 PATCH。"""
        set_context(Ctx(yes=True))
        try:
            with mock.patch.object(deploy, "resolve_app_uuid", return_value="U1"), \
                    mock.patch.object(deploy, "_req", return_value={"data": self._app()}) as req, \
                    mock.patch.object(deploy, "cloudos_deploy_snapshot", return_value=_NO_SNAP), \
                    self._profile(), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                with self.assertRaises(OpsError) as cm:
                    deploy.env_set(["face-pay-server", "max_memory=4000"])
            self.assertEqual(cm.exception.exit_code, EXIT_PRECONDITION)
            self.assertIn("IT-020", str(cm.exception))
            self.assertFalse(any(c.args[0] == "PATCH" for c in req.call_args_list))
        finally:
            set_context(Ctx())

    def test_allow_no_snapshot_performs_patch_without_resources(self):
        """显式开关是唯一逃生通道：无骨架时不带 resources 下发，且 MUST 有 PATCH。"""
        set_context(Ctx(yes=True))
        try:
            with mock.patch.object(deploy, "resolve_app_uuid", return_value="U1"), \
                    mock.patch.object(deploy, "_req", return_value={"data": self._app()}) as req, \
                    mock.patch.object(deploy, "cloudos_deploy_snapshot", return_value=_NO_SNAP), \
                    mock.patch.object(deploy, "_deploy_wait", return_value=None), \
                    mock.patch.object(deploy, "verify_env_patch",
                                      return_value={"ok": True, "env_n": 3, "mismatched": {},
                                                    "still_present": [], "attempt": 1,
                                                    "version": "1.0.91.1",
                                                    "container_image": "reg/itp/app:1.0.91"}), \
                    self._profile(), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                rc = deploy.env_set(["face-pay-server", "max_memory=4000",
                                     "--allow-no-snapshot"])
            self.assertEqual(rc, 0)
            self.assertTrue(any(c.args[0] == "PATCH" for c in req.call_args_list))
        finally:
            set_context(Ctx())


class EnvVerifyTest(unittest.TestCase):
    """2026-11-06：PATCH 后自动复验只走 CloudOS GET，NEVER 依赖 kubectl/Pod。"""

    PLAN = {"uuid": "U1", "app_name": "app"}

    @staticmethod
    def _detail(env):
        return {"data": {"uuid": "U1", "version": "1.0.0.1",
                         "metadata": {"env": env,
                                      "container_image": "reg/itp/app:1.0.0"}}}

    def test_set_and_delete_verified_via_cloudos_get(self):
        with mock.patch.object(deploy, "_req", return_value=self._detail(
                {"A": "1", "NEW": "v"})) as req, \
                mock.patch.object(deploy.time, "sleep") as sl:
            r = deploy.verify_env_patch(self.PLAN, {"NEW": "v"}, ["OLD"])
        self.assertTrue(r["ok"])
        self.assertEqual(r["env_n"], 2)
        self.assertEqual(r["attempt"], 1)
        self.assertFalse(sl.called)
        self.assertEqual(req.call_args_list[0].args[0], "GET")

    def test_missing_key_retries_then_reports_failure(self):
        with mock.patch.object(deploy, "_req", return_value=self._detail({"A": "1"})), \
                mock.patch.object(deploy.time, "sleep") as sl:
            r = deploy.verify_env_patch(self.PLAN, {"NEW": "v"}, [])
        self.assertFalse(r["ok"])
        self.assertEqual(r["mismatched"], {"NEW": {"want": "v", "got": None}})
        self.assertEqual(r["attempt"], 5)
        self.assertEqual(sl.call_count, 4)

    def test_deleted_key_still_present_is_failure(self):
        with mock.patch.object(deploy, "_req", return_value=self._detail({"OLD": "x"})), \
                mock.patch.object(deploy.time, "sleep"):
            r = deploy.verify_env_patch(self.PLAN, {}, ["OLD"])
        self.assertFalse(r["ok"])
        self.assertEqual(r["still_present"], ["OLD"])


class EnvCheckTest(unittest.TestCase):
    """2026-11-06 新增的纯 CloudOS 只读巡检：跨环境串线 MUST 报 ERROR。"""

    @staticmethod
    def _app(env, *, snap="reg/itp/app:1.0.0", image="reg/itp/app:1.0.0"):
        dep = {"spec": {"template": {"spec": {"containers": [
            {"name": "app", "image": snap}]}}}}
        return {"name": "app", "namespace": "itp", "version": "1.0.0",
                "metadata": {"env": env, "container_image": image,
                             "image_metadata": {"metadata": {"resources": [None, dep]}}}}

    def test_cross_env_service_url_is_error(self):
        r = envcheck.check_app(self._app({
            "SERVICE_ACCOUNT_URL": "http://account.itpb.svc:8080",
            "DB_HOST": "172.20.222.3:1521",
            "max_memory": "4000", "reserved_memory": "1000", "max_direct_memory": "100"}))
        self.assertEqual(r["errors"], 1)
        self.assertIn("红岛", r["issues"][0]["msg"])

    def test_stale_snapshot_is_info_not_error(self):
        r = envcheck.check_app(self._app(
            {"max_memory": "4000", "reserved_memory": "1000", "max_direct_memory": "100"},
            snap="reg/itp/app:0.9.9", image="reg/itp/app:1.0.0"))
        self.assertEqual(r["errors"], 0)
        self.assertTrue(any(i["level"] == "INFO" for i in r["issues"]))

    def test_empty_env_is_warn_not_error(self):
        """基础设施应用（VL / httpbin）平台模型不记 env，NEVER 误报 ERROR。"""
        r = envcheck.check_app(self._app({}))
        self.assertEqual(r["errors"], 0)
        self.assertEqual(sum(1 for i in r["issues"] if "JVM" in i["msg"]), 0)

    def test_missing_snapshot_is_warn(self):
        app = self._app({"max_memory": "4000", "reserved_memory": "1000",
                         "max_direct_memory": "100"})
        app["metadata"].pop("image_metadata")
        r = envcheck.check_app(app)
        self.assertEqual(r["errors"], 0)
        self.assertTrue(any("allow-no-snapshot" in i["msg"] for i in r["issues"]))


class CatalogShapeTest(unittest.TestCase):
    def test_catalog_is_complete(self):
        cat = impl.CATALOG
        self.assertEqual(cat["name"], "cloudos")
        names = [s["name"] for s in cat["subcommands"]]
        self.assertEqual(len(names), 31)  # 2026-11-06 +env-check，删 image build / pod-delete；2026-11-17 +image rm（IT-073）
        self.assertEqual(len(names), len(set(names)))
        self.assertIn("image rm", names)
        for sub in cat["subcommands"]:
            self.assertIsInstance(sub["writes"], bool)
            self.assertTrue(sub["usage"] and sub["summary"])


if __name__ == "__main__":
    unittest.main()

"""cloudos image rm / 应用名相近建议 / buildx 仓库名警示 —— 2026-11-17 IT-073 配套单测。

发版实测教训：模块名≠仓库名（trans-query-server 模块 → trans-query 仓库）时 buildx
按模块名推孤儿镜像，手工清理只能翻 API 文档调 deleteOneImage。本组测试守三个新判据：
  1. delete_one_image 只认 img.0 成功码；
  2. _cmd_rm 三道护栏（tag 存在性 / 应用占用即拒 / --yes 门）+ 删后复核；
  3. resolve_app_uuid 未命中给相近应用名；buildx 仓库名不匹配给 WARNING（纯函数）。
"""
from __future__ import annotations

import io
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

from itpops.buildx import core as buildx_core
from itpops.cloudos import common, image as image_mod, images
from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_EXEC, EXIT_PRECONDITION, EXIT_USAGE, OpsError


class DeleteOneImageTest(unittest.TestCase):
    """image.py 删除封装：成功码 img.0，其余一律抛 OpsError。"""

    def test_success_returns_parsed_body(self):
        body = ('{"status":true,"code":"img.0","msg":"ok","httpCode":200,'
                '"resources":["os-harbor-svc:443/itp/x:1.0.0"]}')
        with mock.patch.object(image_mod, "_img_http", return_value=("200", body)):
            d = image_mod.delete_one_image("os-harbor-svc:443/itp/x:1.0.0", "t1", "u1")
        self.assertTrue(d["status"])
        self.assertEqual(d["resources"], ["os-harbor-svc:443/itp/x:1.0.0"])

    def test_business_rejection_raises(self):
        body = '{"status":false,"code":"img.5","msg":"镜像被引用"}'
        with mock.patch.object(image_mod, "_img_http", return_value=("200", body)):
            with self.assertRaises(OpsError) as cm:
                image_mod.delete_one_image("img", "t", "u")
        self.assertEqual(cm.exception.exit_code, EXIT_EXEC)
        self.assertIn("img.5", str(cm.exception))

    def test_non_200_raises(self):
        with mock.patch.object(image_mod, "_img_http", return_value=("500", "boom")):
            with self.assertRaises(OpsError) as cm:
                image_mod.delete_one_image("img", "t", "u")
        self.assertEqual(cm.exception.exit_code, EXIT_EXEC)


class ImageRmCommandTest(unittest.TestCase):
    """_cmd_rm 三道护栏 + 删后复核。"""

    def tearDown(self):
        set_context(Ctx())

    def _rec(self, tag: str = "1.0.28") -> dict:
        return {"imgName": "trans-query-server", "versions": tag,
                "imgUrl": "os-harbor-svc:443/itp/trans-query-server:%s" % tag,
                "tentantID": "t-1", "userID": "u-1", "digest": "sha256:x",
                "osAndArch": "linux/amd64"}

    def test_usage_error_on_missing_args(self):
        with self.assertRaises(OpsError) as cm:
            images._cmd_rm([])
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)

    def test_unknown_option_rejected(self):
        with self.assertRaises(OpsError) as cm:
            images._cmd_rm(["repo", "1.0.0", "--force"])
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)

    def test_missing_tag_refuses_before_any_write(self):
        with mock.patch.object(images, "_image_versions_raw", return_value=[self._rec("1.0.26")]), \
             mock.patch.object(images.image_mod, "delete_one_image") as delete:
            with self.assertRaises(OpsError) as cm:
                images._cmd_rm(["trans-query-server", "1.0.28", "--project", "itp"])
        self.assertEqual(cm.exception.exit_code, EXIT_PRECONDITION)
        delete.assert_not_called()

    def test_in_use_refuses_before_any_write(self):
        with mock.patch.object(images, "_image_versions_raw", return_value=[self._rec()]), \
             mock.patch.object(images, "_image_in_use", return_value=["trans-query"]), \
             mock.patch.object(images.image_mod, "delete_one_image") as delete:
            with self.assertRaises(OpsError) as cm:
                images._cmd_rm(["trans-query-server", "1.0.28", "--project", "itp"])
        self.assertEqual(cm.exception.exit_code, EXIT_PRECONDITION)
        self.assertIn("trans-query", str(cm.exception))
        delete.assert_not_called()

    def test_success_deletes_and_rechecks(self):
        set_context(Ctx(yes=True))
        with mock.patch.object(images, "_image_versions_raw",
                               side_effect=[[self._rec()], []]), \
             mock.patch.object(images, "_image_in_use", return_value=[]), \
             mock.patch.object(images.image_mod, "delete_one_image",
                               return_value={"resources": ["os-harbor-svc:443/itp/trans-query-server:1.0.28"]}
                               ) as delete:
            out, err = io.StringIO(), io.StringIO()
            with redirect_stdout(out), redirect_stderr(err):
                rc = images._cmd_rm(["trans-query-server", "1.0.28", "--project", "itp"])
        self.assertEqual(rc, 0)
        delete.assert_called_once_with(
            "os-harbor-svc:443/itp/trans-query-server:1.0.28", "t-1", "u-1")
        self.assertIn("已删除", out.getvalue())
        self.assertIn("复核", out.getvalue())

    def test_still_there_after_delete_warns(self):
        set_context(Ctx(yes=True))
        with mock.patch.object(images, "_image_versions_raw",
                               side_effect=[[self._rec()], [self._rec()]]), \
             mock.patch.object(images, "_image_in_use", return_value=[]), \
             mock.patch.object(images.image_mod, "delete_one_image",
                               return_value={"resources": ["x"]}):
            out, err = io.StringIO(), io.StringIO()
            with redirect_stdout(out), redirect_stderr(err):
                rc = images._cmd_rm(["r", "1.0.28", "--project", "itp"])
        self.assertEqual(rc, 1)
        self.assertIn("仍在版本列表", err.getvalue())


class ResolveAppSuggestTest(unittest.TestCase):
    """resolve_app_uuid 未命中时给相近应用名（应用名≠模块名，IT-073）。"""

    def _apps(self, names: list[str]) -> dict:
        return {"data": [{"name": n, "uuid": "%032d" % i} for i, n in enumerate(names)]}

    def test_exact_match_short_circuits(self):
        with mock.patch.object(common, "_req", return_value=self._apps(["trans-query", "web-admin"])):
            self.assertEqual(common.resolve_app_uuid("trans-query"), "%032d" % 0)

    def test_no_hit_lists_closest_names_on_stderr(self):
        with mock.patch.object(common, "_req",
                               return_value=self._apps(["trans-query", "fep-app", "pay-sign"])):
            err = io.StringIO()
            with redirect_stderr(err), self.assertRaises(OpsError):
                common.resolve_app_uuid("trans-query-server")
        self.assertIn("你是不是想找", err.getvalue())
        self.assertIn("trans-query", err.getvalue())


class BuildxRepoWarningTest(unittest.TestCase):
    """buildx 仓库名警示：与应用仓库集合不匹配给 WARNING，空集合静默。"""

    def test_match_returns_none(self):
        self.assertIsNone(buildx_core._repo_warning("trans-query", {"trans-query", "web-admin"}))

    def test_empty_repos_silent(self):
        self.assertIsNone(buildx_core._repo_warning("anything", set()))

    def test_mismatch_warns_with_it073(self):
        msg = buildx_core._repo_warning("trans-query-server", {"trans-query", "pay-sign-server"})
        self.assertIsNotNone(msg)
        self.assertIn("IT-073", msg)
        self.assertIn("trans-query", msg)

    def test_app_image_repos_parses_container_image(self):
        body = ('{"data":[{"name":"a","metadata":{"container_image":'
                '"os-harbor-svc.default.svc.cloudos:443/itp/trans-query:1.0.28"}},'
                '{"name":"b","metadata":{}}]}')
        fake_cos = object()
        with mock.patch.object(buildx_core, "_http_token", return_value=("200", body)):
            self.assertEqual(buildx_core._app_image_repos(fake_cos), {"trans-query"})

    def test_app_image_repos_http_fail_returns_empty(self):
        fake_cos = object()
        with mock.patch.object(buildx_core, "_http_token", return_value=("500", "boom")):
            self.assertEqual(buildx_core._app_image_repos(fake_cos), set())


if __name__ == "__main__":
    unittest.main()

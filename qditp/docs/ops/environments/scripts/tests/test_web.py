"""web 域业务级单测 —— 应用包升级与配置升级的边界（2026-11-06 审计）。

背景：用户裁定「升级配置，并不是升级应用包」。这两条通道互不影响，
`web release` / `flow frontend` MUST 只换包且把配置引用原样带回，
帮助文本也 MUST 写明边界，避免下一个操作者（人或 AI）再拿发版当改配置。
"""
from __future__ import annotations

import unittest

from itpops.web import frontend_release as release
from itpops.workflows import definitions


class WebUpgradeBodyTest(unittest.TestCase):
    """应用包升级 body：只换 version / version_id，其余（含配置引用）照抄。"""

    META = {
        "strategy": "rolling",
        "env": {"NGINX_VERSION": "1.20"},
        "jvm_args": "",
        "logrotateCron": None,
        "tomcat_version": "8.5",
        "is_skywalking": False,
        "enable_ssl": False,
        "nginx_version": "1.20",
        "service_name": "web-3zilk-svc",
        "setting_file_id": "12ea900b-a396-4ba2-8d3d-7d28b04f7b0c",
        "crt_file_id": "crt-uuid",
        "key_file_id": "key-uuid",
        "image_metadata": {"metadata": {"resources": [None, {"kind": "Deployment"}]}},
    }

    def test_config_refs_are_carried_through(self):
        """2026-11-06 补漏：这三个字段此前漏传，属于应用包升级越界风险。"""
        body = release.build_upgrade_body(self.META, "newvid", "0.0.0.8")
        self.assertEqual(body["setting_file_id"], self.META["setting_file_id"])
        self.assertEqual(body["crt_file_id"], "crt-uuid")
        self.assertEqual(body["key_file_id"], "key-uuid")

    def test_only_version_fields_change(self):
        body = release.build_upgrade_body(self.META, "newvid", "0.0.0.8")
        self.assertEqual(body["version"], "0.0.0.8")
        self.assertEqual(body["version_id"], "newvid")
        self.assertEqual(body["nginx_version"], "1.20")
        self.assertEqual(body["env"], {"NGINX_VERSION": "1.20"})
        self.assertEqual(body["resources"], [None, {"kind": "Deployment"}])

    def test_missing_refs_are_omitted_not_null(self):
        """平台没有这些 id 时 MUST 省略键，NEVER 传 null 触发校验。"""
        meta = {k: v for k, v in self.META.items()
                if k not in ("setting_file_id", "crt_file_id", "key_file_id")}
        body = release.build_upgrade_body(meta, "v", "1")
        for k in ("setting_file_id", "crt_file_id", "key_file_id"):
            self.assertNotIn(k, body)


class FrontendFlowBoundaryTest(unittest.TestCase):
    """帮助文本回归：两处 MUST 明说「不是配置升级」。"""

    def test_flow_notes_state_boundary(self):
        flow = definitions.frontend({"env": "test"})
        self.assertTrue(any("不是配置升级" in n for n in flow.notes))

    def test_catalog_summary_states_boundary(self):
        self.assertIn("不改 nginx 配置", release.CATALOG["summary"])


if __name__ == "__main__":
    unittest.main()

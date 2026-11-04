"""命令注册表：域清单、CATALOG 结构、catalog 命令输出。"""
from __future__ import annotations

import unittest

from support import run_cli, run_json

from itpops.cli.registry import build_catalog, load_domains

EXPECTED_DOMAINS = {
    "version", "cloudos", "k8s", "log", "diag", "analysis",
    "buildx", "sentinel", "web", "catalog", "lint", "flow",
}


class RegistryTest(unittest.TestCase):
    def test_all_domains_loaded(self):
        names = {d["name"] for d in load_domains()}
        self.assertEqual(names, EXPECTED_DOMAINS)
        for d in load_domains():
            self.assertEqual(d["status"], "ok", d["name"])
            self.assertTrue(d.get("summary"), d["name"])

    def test_root_help_lists_every_domain(self):
        """防止新增域时忘了同步 cli/main.py 里硬编码的根帮助列表。"""
        r = run_cli("-h")
        self.assertEqual(r.returncode, 0)
        for name in EXPECTED_DOMAINS:
            self.assertIn(name, r.stdout, f"根帮助缺少域 {name}")

    def test_web_has_two_subcommands(self):
        web = next(d for d in load_domains() if d["name"] == "web")
        subs = {s["name"] for s in web["subcommands"]}
        self.assertEqual(subs, {"release", "mount-export"})

    def test_catalog_contract(self):
        cat = build_catalog()
        self.assertEqual(cat["tool"], "itp-ops")
        self.assertIn("exit_codes", cat["contract"])
        self.assertEqual(cat["contract"]["output_formats"], ["text", "json"])
        self.assertEqual(cat["standalone"], [])
        self.assertTrue(cat["global_flags"])


class CatalogCommandTest(unittest.TestCase):
    def test_json_payload_matches_registry(self):
        payload, rc = run_json("catalog")
        self.assertEqual(rc, 0)
        self.assertTrue(payload["ok"])
        self.assertEqual(
            {d["name"] for d in payload["data"]["domains"]}, EXPECTED_DOMAINS
        )


if __name__ == "__main__":
    unittest.main()

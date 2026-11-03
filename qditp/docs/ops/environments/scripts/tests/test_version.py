"""版本号规则（2026-11-02 裁定）：末位 +1、段上限 99 进位、debug 哨兵。"""
from __future__ import annotations

import unittest

from support import run_cli

from itpops.core.version import debug_version, next_version, version_valid
from itpops.errors import EXIT_USAGE, OpsError


class VersionAlgorithmTest(unittest.TestCase):
    def test_increment_last_segment(self):
        self.assertEqual(next_version("1.2.42"), "1.2.43")
        self.assertEqual(next_version("1.0"), "1.1")
        self.assertEqual(next_version("7"), "8")

    def test_carry_at_99(self):
        self.assertEqual(next_version("1.2.99"), "1.3.0")
        self.assertEqual(next_version("1.99.99"), "2.0.0")

    def test_top_segment_overflow(self):
        self.assertEqual(next_version("99.99"), "1.0.0")

    def test_valid(self):
        for v in ("1", "1.2", "1.2.3", "20261103"):
            self.assertTrue(version_valid(v), v)
        for v in ("", ".", "1.", ".1", "1..2", "1.a", "v1.2", "1.2 "):
            self.assertFalse(version_valid(v), v)

    def test_invalid_raises_usage(self):
        with self.assertRaises(OpsError) as cm:
            next_version("abc")
        self.assertEqual(cm.exception.exit_code, EXIT_USAGE)

    def test_debug_sentinel(self):
        self.assertEqual(debug_version(), "0.0.0.1")


class VersionCommandTest(unittest.TestCase):
    def test_cli_stdout_is_bare_version(self):
        r = run_cli("version", "next", "1.2.42")
        self.assertEqual(r.returncode, 0)
        self.assertEqual(r.stdout.strip(), "1.2.43")

    def test_missing_arg_is_usage_error(self):
        r = run_cli("version", "next")
        self.assertEqual(r.returncode, EXIT_USAGE)


if __name__ == "__main__":
    unittest.main()

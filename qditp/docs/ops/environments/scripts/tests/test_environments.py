"""环境 profile：hongdao / test 取值、显式 --env 强制覆盖、K8S_NS 可覆盖。"""
from __future__ import annotations

import os
import unittest
from unittest import mock

from itpops.core import environments
from itpops.core.ui import Ctx, set_context
from itpops.errors import EXIT_PRECONDITION, OpsError


class EnvProfileTest(unittest.TestCase):
    def setUp(self):
        set_context(Ctx())

    def test_hongdao_profile(self):
        env = environments.init_env("hongdao")
        self.assertEqual(env.name, "hongdao")
        self.assertEqual(env.remote_host, "hd-linux")
        self.assertEqual(env.k8s_ns, "itpb")
        self.assertEqual(env.harbor_project, "itpb")
        self.assertEqual(env.ingress_entry, "172.120.2.26:48000")

    def test_test_profile(self):
        env = environments.init_env("test")
        self.assertEqual(env.k8s_ns, "itp")
        self.assertEqual(env.harbor_project, "itp")
        self.assertEqual(env.remote_host, "k8s-master")

    def test_unknown_env(self):
        set_context(Ctx(env="staging", env_explicit=True))
        with self.assertRaises(OpsError) as cm:
            environments.init_env("test")
        self.assertEqual(cm.exception.exit_code, EXIT_PRECONDITION)

    def test_explicit_env_beats_ambient_var(self):
        set_context(Ctx(env="test", env_explicit=True))
        with mock.patch.dict(os.environ, {"REMOTE_HOST": "somewhere-else"}):
            env = environments.init_env("hongdao")
        self.assertEqual(env.remote_host, "k8s-master")  # explicit → profile 值

    def test_k8s_ns_always_overridable(self):
        set_context(Ctx(env="test", env_explicit=True))
        with mock.patch.dict(os.environ, {"K8S_NS": "custom-ns"}):
            env = environments.init_env("test")
        self.assertEqual(env.k8s_ns, "custom-ns")


if __name__ == "__main__":
    unittest.main()

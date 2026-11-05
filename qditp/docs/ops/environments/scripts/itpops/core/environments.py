"""环境 profile 单一真源 —— lib/env.sh 的 Python 移植。

【用法】
  envx.init_env(default_env)   # hongdao | test；--env 由入口写入 Ctx.env
  envx.banner(what)            # 每个打到远端/集群的命令前打一次横幅

⚠ 各域默认环境刻意不统一（NEVER 擅自改成一致——那会让原本能用的命令失效）：
    cloudos → hongdao（红岛是主战场）
    k8s/log → test（k8s 域只对测试可用；log 域 tail 依赖的远端脚本红岛未部署）
    diag    → 按子命令：reach=hongdao、routes=test
    analysis → 不调用（环境无关）

【变量优先级（与 bash 版 _env_apply 一致）】
  显式 --env（ctx.env_explicit）→ 强制 profile 值（压掉一切历史默认，防止
  「说是红岛、跳板还连测试」错配）；
  未显式 --env → 同名环境变量非空则用之，否则 profile 默认值。
  K8S_NS 例外：任何情况下都可被同名环境变量覆盖。

【YAML 配置（2026-11-05 新增）】
  environments/<env>.yaml 为声明式配置真源，environments.py 的 _PROFILES 字典
  由 YAML 自动生成（启动时加载）。修改 YAML 后无需改 Python 代码。
  环境变量 ITP_ENV_<FIELD> 仍可覆盖任一字段（YAML 优先于硬编码 fallback）。
"""
from __future__ import annotations

import os
import sys
from dataclasses import dataclass
from pathlib import Path

from ..errors import EXIT_PRECONDITION, OpsError
from .ui import context

# 兼容别名：REMOTE_HOST 还接受 ITP_REMOTE_HOST（lib/remote.sh 历史入口）
_ENV_ALIASES = {"REMOTE_HOST": ("ITP_REMOTE_HOST",)}


@dataclass(frozen=True)
class Env:
    """一次执行的环境 profile（init_env 后只读）。"""

    name: str                 # hongdao | test
    label: str                # 中文名（横幅用）
    remote_host: str          # 跳板机
    remote_dir: str
    k8s_ns: str
    harbor_project: str
    cloudos_env: str
    src_db_dsn: str
    tgt_db_dsn: str
    tgt_db_pdb: str
    vl_url: str
    ingress_entry: str

    def as_dict(self) -> dict:
        return {
            "name": self.name,
            "label": self.label,
            "remote_host": self.remote_host,
            "remote_dir": self.remote_dir,
            "k8s_ns": self.k8s_ns,
            "harbor_project": self.harbor_project,
            "cloudos_env": self.cloudos_env,
            "src_db_dsn": self.src_db_dsn,
            "tgt_db_dsn": self.tgt_db_dsn,
            "tgt_db_pdb": self.tgt_db_pdb,
            "vl_url": self.vl_url,
            "ingress_entry": self.ingress_entry,
        }


# K8S_NS 任何情况下都可被环境变量覆盖（bash: K8S_NS="${K8S_NS:-<默认>}"）
_ALWAYS_OVERRIDABLE = {"k8s_ns"}

_CURRENT: Env | None = None


def _load_yaml_profiles() -> dict[str, dict[str, str]]:
    """从 environments/*.yaml 加载配置。失败时返回空字典（退回到硬编码）。"""
    profiles: dict[str, dict[str, str]] = {}
    try:
        import yaml
        scripts_dir = Path(__file__).resolve().parents[2]
        yaml_dir = scripts_dir / "environments"
        if yaml_dir.is_dir():
            for yaml_file in yaml_dir.glob("*.yaml"):
                env_name = yaml_file.stem
                with open(yaml_file, "r", encoding="utf-8") as f:
                    config = yaml.safe_load(f)
                if isinstance(config, dict):
                    # 过滤非字符串值（如 jvm_presets 等嵌套结构）
                    profiles[env_name] = {
                        k: str(v) for k, v in config.items()
                        if isinstance(v, str) and not k.startswith("_")
                    }
    except ImportError:
        pass  # PyYAML 未安装，使用硬编码
    except Exception as exc:
        print(f"[WARN] 加载 YAML 配置失败: {exc}", file=sys.stderr)
    return profiles


# 硬编码默认值（向后兼容 + YAML 加载失败时的 fallback）
_PROFILES_BASE: dict[str, dict[str, str]] = {
    "hongdao": {
        "label": "红岛生产",
        "remote_host": "hd-linux",
        "remote_dir": "/home/java",
        "k8s_ns": "itpb",
        "harbor_project": "itpb",
        "cloudos_env": "hongdao",
        "src_db_dsn": "172.20.222.3:1521/AFCITPDB",
        "tgt_db_dsn": "172.120.110.25:1521/hditpdb",
        "tgt_db_pdb": "HDTPDB",
        "vl_url": "http://172.120.2.13:31920",
        "ingress_entry": "172.120.2.26:48000",
    },
    "test": {
        "label": "测试环境",
        "remote_host": "k8s-master",
        "remote_dir": "/home/java",
        "k8s_ns": "itp",
        "harbor_project": "itp",
        "cloudos_env": "test",
        "src_db_dsn": "172.20.222.3:1521/AFCITPDB",
        "tgt_db_dsn": "172.20.222.3:1521/AFCITPDB",
        "tgt_db_pdb": "AFCITPDB",
        "vl_url": "http://172.20.211.23:30032",
        "ingress_entry": "58.56.166.170:48000",
    },
}

# 运行时配置（YAML 优先，环境变量覆盖）
_PROFILES: dict[str, dict[str, str]] = {}


def _init_profiles() -> None:
    """启动时加载 YAML 配置并合并到 _PROFILES。"""
    global _PROFILES
    _PROFILES = dict(_PROFILES_BASE)  # 先复制硬编码值

    # 加载 YAML（YAML 值覆盖硬编码）
    yaml_profiles = _load_yaml_profiles()
    for env_name, yaml_config in yaml_profiles.items():
        if env_name in _PROFILES:
            _PROFILES[env_name].update(yaml_config)
        else:
            _PROFILES[env_name] = yaml_config


# 模块加载时初始化
_init_profiles()


def _existing_env_var(field: str) -> str:
    """取「同名环境变量」现值：优先字段大写同名，其次兼容别名（如 ITP_REMOTE_HOST）。"""
    names = (field.upper(),) + _ENV_ALIASES.get(field.upper(), ())
    for n in names:
        v = os.environ.get(n, "")
        if v:
            return v
    return ""


def init_env(default_env: str) -> Env:
    """按各域默认环境初始化 profile 并返回。幂等：重复调用按新参数重建。"""
    global _CURRENT

    ctx = context()
    env_name = ctx.env or default_env
    profile = _PROFILES.get(env_name)
    if profile is None:
        raise OpsError(
            f"错误：未知环境 {env_name}（仅 {' / '.join(_PROFILES.keys())}）", EXIT_PRECONDITION
        )

    explicit = ctx.env_explicit
    values: dict[str, str] = {}
    for field, default in profile.items():
        if field == "label":
            values[field] = default
            continue
        if field in _ALWAYS_OVERRIDABLE:
            values[field] = _existing_env_var(field) or default
        elif explicit:
            values[field] = default          # 显式 --env：强制 profile 值
        else:
            values[field] = _existing_env_var(field) or default

    _CURRENT = Env(name=env_name, **values)
    return _CURRENT


def current() -> Env:
    """返回已初始化的环境；未初始化即报前置错误（域必须先 init_env）。"""
    if _CURRENT is None:
        raise OpsError("环境未初始化（域需先调用 envx.init_env）", EXIT_PRECONDITION)
    return _CURRENT


def banner(what: str = "") -> None:
    """打印环境横幅（stderr）。MUST 在每个会打到远端/集群的命令前调用一次。"""
    e = current()
    print(
        f"[环境] {e.label}（{e.name}）  跳板={e.remote_host}  "
        f"K8s命名空间={e.k8s_ns}  Harbor项目={e.harbor_project}",
        file=sys.stderr,
    )
    if what:
        print(f"[环境] 目标：{what}", file=sys.stderr)

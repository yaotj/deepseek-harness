"""环境差异配置化 —— 集中管理 namespace/registry/default_memory 等环境差异。

设计目标：
  · 替换代码中的硬编码值（如 namespace='itp'、registry='harbor.test.local'）
  · 每个环境一个 YAML 文件：environments/test.yaml、environments/hongdao.yaml
  · 运行时通过 envx.get(env_name) 获取配置
  · 新增环境只需加 YAML 文件，不需要改代码

当前状态：
  · 接口已定义，YAML 文件待创建
  · 现有代码仍使用硬编码值，迁移需逐步进行

配置示例（environments/test.yaml）：
  namespace: itp
  registry: harbor.test.local
  cloudos_host: 172.20.217.210
  cloudos_user: admin
  k8s_master: 172.20.211.23
  default_memory: 4Gi
  default_jvm_heap_ratio: 0.5
  jvm_presets:
    4Gi: {max_memory: 4000, reserved_memory: 1000, max_direct_memory: 100, heap: 2900m}
    8Gi: {max_memory: 8000, reserved_memory: 2000, max_direct_memory: 100, heap: 5900m}
"""
from __future__ import annotations

from pathlib import Path
from typing import Any


# 配置目录（相对 scripts_dir）
ENVIRONMENTS_DIR = Path("environments")

# 默认配置（向后兼容）
DEFAULT_CONFIG: dict[str, Any] = {
    "namespace": "itp",
    "registry": "harbor.test.local",
    "cloudos_host": "172.20.217.210",
    "cloudos_user": "admin",
    "k8s_master": "172.20.211.23",
    "default_memory": "4Gi",
    "default_jvm_heap_ratio": 0.5,
    "jvm_presets": {
        "4Gi": {"max_memory": "4000", "reserved_memory": "1000", "max_direct_memory": "100", "heap": "2900m"},
        "8Gi": {"max_memory": "8000", "reserved_memory": "2000", "max_direct_memory": "100", "heap": "5900m"},
    },
}


def get_env_config(env_name: str) -> dict[str, Any]:
    """获取指定环境的配置。

    优先级：
      1. YAML 文件（environments/<env>.yaml）
      2. 环境变量（ITP_ENV_<KEY>）
      3. 默认配置

    参数:
      env_name: 环境名（test/hongdao）

    返回:
      配置字典
    """
    config = dict(DEFAULT_CONFIG)

    # 尝试加载 YAML 文件
    scripts_dir = Path(__file__).resolve().parents[2]
    yaml_path = scripts_dir / ENVIRONMENTS_DIR / f"{env_name}.yaml"
    if yaml_path.is_file():
        try:
            import yaml
            with open(yaml_path, "r", encoding="utf-8") as f:
                yaml_config = yaml.safe_load(f)
            if isinstance(yaml_config, dict):
                config.update(yaml_config)
        except ImportError:
            # PyYAML 未安装，跳过 YAML 加载
            pass
        except Exception as exc:
            import sys
            print(f"[WARN] 加载环境配置失败 {yaml_path}: {exc}", file=sys.stderr)

    # 环境变量覆盖
    for key, value in os.environ.items():
        if key.startswith("ITP_ENV_"):
            cfg_key = key[len("ITP_ENV_"):].lower()
            config[cfg_key] = value

    return config


def get_namespace(env_name: str) -> str:
    """获取命名空间。"""
    return get_env_config(env_name).get("namespace", "itp")


def get_registry(env_name: str) -> str:
    """获取镜像仓库地址。"""
    return get_env_config(env_name).get("registry", "harbor.test.local")


def get_jvm_preset(env_name: str, size: str) -> dict[str, str]:
    """获取 JVM 参数预设。"""
    presets = get_env_config(env_name).get("jvm_presets", {})
    return presets.get(size, presets.get("4Gi", {}))


# 导入 os 模块
import os

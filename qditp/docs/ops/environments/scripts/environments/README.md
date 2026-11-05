# 环境配置文件

本目录包含各环境的声明式配置（YAML），供 `itpops/core/environments_config.py` 加载。

## 文件列表

- `test.yaml` — 测试环境配置
- `hongdao.yaml` — 红岛生产配置

## 配置字段说明

| 字段 | 说明 | 示例 |
|---|---|---|
| `label` | 环境中文名（横幅显示） | 测试环境 |
| `remote_host` | 跳板机主机名 | k8s-master |
| `remote_dir` | 远端工作目录 | /home/java |
| `k8s_ns` | K8s 命名空间 | itp |
| `harbor_project` | Harbor 项目名 | itp |
| `cloudos_env` | CloudOS 环境标识 | test |
| `src_db_dsn` | 源数据库 DSN | 172.20.222.3:1521/AFCITPDB |
| `tgt_db_dsn` | 目标数据库 DSN | 172.20.222.3:1521/AFCITPDB |
| `tgt_db_pdb` | 目标 PDB 名 | AFCITPDB |
| `vl_url` | VictoriaLogs  ingest URL | http://172.20.211.23:30032 |
| `ingress_entry` | 入口地址 | 58.56.166.170:48000 |
| `jvm_presets` | JVM 参数预设 | 见下 |
| `default_memory` | 默认容器内存 | 4Gi |
| `default_jvm_heap_ratio` | 默认堆内存比例 | 0.5 |

## JVM 预设

```yaml
jvm_presets:
  4Gi:
    max_memory: "4000"
    reserved_memory: "1000"
    max_direct_memory: "100"
    heap: "2900m"
  8Gi:
    max_memory: "8000"
    reserved_memory: "2000"
    max_direct_memory: "100"
    heap: "5900m"
```

## 环境变量覆盖

可通过 `ITP_ENV_<KEY>` 环境变量覆盖配置项（如 `ITP_ENV_k8s_ns=my-ns`）。

## 与 environments.py 的关系

- `environments.py` 的 `_PROFILES` 字典仍是**运行时真源**
- YAML 文件是**声明式配置**，用于文档化和未来迁移
- 两者保持一致，YAML 作为单一来源记录配置意图

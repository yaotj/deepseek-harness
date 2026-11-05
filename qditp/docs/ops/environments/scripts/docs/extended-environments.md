# 扩展环境配置

> **状态**：示例配置，可根据实际需求复制修改
> **最后更新**：2026-11-05

---

## 一、现有环境

- `test.yaml` — 测试环境
- `hongdao.yaml` — 红岛生产环境

---

## 二、新增环境模板

### 2.1 开发环境（dev）

```bash
# 复制测试环境配置并修改
cp environments/test.yaml environments/dev.yaml

# 修改关键配置
sed -i '' 's/测试环境/开发环境/g' environments/dev.yaml
sed -i '' 's/k8s-master/k8s-dev/g' environments/dev.yaml
sed -i '' 's/itp/itp-dev/g' environments/dev.yaml
```

**建议配置**：
```yaml
label: 开发环境
remote_host: k8s-dev
k8s_ns: itp-dev
harbor_project: itp-dev
# 开发环境可关闭部分安全检查
cloudos_env: dev
```

### 2.2 预发布环境（staging）

```bash
# 复制生产环境配置并修改
cp environments/hongdao.yaml environments/staging.yaml

# 修改关键配置
sed -i '' 's/红岛生产/预发布环境/g' environments/staging.yaml
sed -i '' 's/hd-linux/k8s-staging/g' environments/staging.yaml
sed -i '' 's/itpb/itpstg/g' environments/staging.yaml
```

**建议配置**：
```yaml
label: 预发布环境
remote_host: k8s-staging
k8s_ns: itpstg
harbor_project: itpstg
cloudos_env: staging
# 预发布环境使用生产配置但隔离网络
vl_url: http://172.30.x.x:30032
```

### 2.3 本地开发环境（local）

```yaml
# environments/local.yaml
label: 本地开发
remote_host: localhost
remote_dir: /tmp/itp-dev
k8s_ns: default
harbor_project: local
cloudos_env: local
# 本地环境使用模拟服务
src_db_dsn: localhost:1521/XE
tgt_db_dsn: localhost:1521/XE
tgt_db_pdb: XE
vl_url: http://localhost:9428
ingress_entry: localhost:8080
```

---

## 三、环境切换

### 3.1 命令行指定

```bash
# 使用 -e/--env 参数
./itp-ops.sh cloudos apps --env dev
./itp-ops.sh flow backend face-pay-server --env staging --yes
```

### 3.2 环境变量

```bash
# 设置默认环境
export ITP_OPS_ENV=dev

# 或临时覆盖
ITP_OPS_ENV=staging ./itp-ops.sh cloudos health
```

### 3.3 查看当前环境

```bash
# 查看加载的配置
python3 -c "
import sys
sys.path.insert(0, 'docs/ops/environments/scripts')
from itpops.core.environments import _PROFILES
print('可用环境:', list(_PROFILES.keys()))
print('当前配置:', _PROFILES.get('test', {}))
"
```

---

## 四、配置字段说明

| 字段 | 必填 | 说明 | 示例 |
|---|---|---|---|
| `label` | ✅ | 环境中文名 | 测试环境 |
| `remote_host` | ✅ | 跳板机主机名 | k8s-master |
| `remote_dir` | ✅ | 远端工作目录 | /home/java |
| `k8s_ns` | ✅ | K8s 命名空间 | itp |
| `harbor_project` | ✅ | Harbor 项目名 | itp |
| `cloudos_env` | ✅ | CloudOS 环境标识 | test |
| `src_db_dsn` | ✅ | 源数据库 DSN | 172.20.222.3:1521/AFCITPDB |
| `tgt_db_dsn` | ✅ | 目标数据库 DSN | 172.20.222.3:1521/AFCITPDB |
| `tgt_db_pdb` | ✅ | 目标 PDB 名 | AFCITPDB |
| `vl_url` | ✅ | VictoriaLogs 地址 | http://172.20.211.23:30032 |
| `ingress_entry` | ✅ | 入口地址 | 58.56.166.170:48000 |
| `default_memory` | | 默认容器内存 | 4Gi |
| `jvm_presets` | | JVM 参数预设 | 见下文 |

### JVM 预设格式

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

---

## 五、环境变量覆盖

### 5.1 覆盖单个字段

```bash
# 覆盖命名空间
export ITP_ENV_k8s_ns=my-namespace

# 覆盖跳板机
export ITP_ENV_remote_host=my-host

# 生效
./itp-ops.sh cloudos health
```

### 5.2 查看所有可覆盖字段

```bash
python3 -c "
import sys
sys.path.insert(0, 'docs/ops/environments/scripts')
from itpops.core.environments import _PROFILES_BASE
for env, config in _PROFILES_BASE.items():
    print(f'{env}: {list(config.keys())}')
"
```

---

## 六、最佳实践

### 6.1 环境命名规范

- 使用小写字母 + 连字符
- 遵循业务含义：test/staging/prod/dev/local
- 避免使用保留字：hongdao/test

### 6.2 配置验证

```bash
# 验证 YAML 语法
python3 -c "
import yaml
with open('environments/dev.yaml') as f:
    config = yaml.safe_load(f)
print('配置有效:', list(config.keys()))
"

# 验证 itp-ops 能加载
./itp-ops.sh flow health --env dev
```

### 6.3 安全注意事项

1. **生产环境** MUST 使用 `--env hongdao` 并加 `--allow-prod`
2. **敏感信息**（数据库密码等）不要存入 YAML，使用环境变量或密钥管理
3. **网络隔离**：不同环境使用不同网络段，避免误操作

---

**维护者**：qd-itp 运维团队

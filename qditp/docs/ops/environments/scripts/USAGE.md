# itp-ops.sh 使用手册

> **版本**：2.9.4
> **最后更新**：2026-11-05
> **定位**：青岛地铁 ITP 平台运维单一入口

---

## 一、快速开始

### 1.1 环境准备

```bash
# 进入脚本目录
cd docs/ops/environments/scripts

# 验证安装
./itp-ops.sh --version
```

### 1.2 第一条命令

```bash
# 环境自检（发版前必跑）
./itp-ops.sh flow health --env test

# 查看应用状态
./itp-ops.sh cloudos apps Error

# 实时日志跟踪
./itp-ops.sh log follow face-pay-server --grep ERROR,WARN
```

---

## 二、核心概念

### 2.1 全局参数

| 参数 | 说明 | 示例 |
|---|---|---|
| `-e, --env` | 环境切换（test/hongdao） | `--env test` |
| `-y, --yes` | 确认写操作（必需） | `--yes` |
| `-n, --dry-run` | 强制预演 | `--dry-run` |
| `-o, --output` | 输出格式（text/json） | `-o json` |
| `-v, --verbose` | DEBUG 输出 | `-v` 或 `-vv` |
| `--trace` | 打印完整堆栈 | `--trace` |
| `--explain` | 命令解释 | `cloudos apps --explain` |

**示例**：
```bash
# JSON 输出（便于 jq 处理）
./itp-ops.sh cloudos apps -o json | jq '.data.apps[] | .name'

# 详细日志
./itp-ops.sh flow backend face-pay-server --env test -v
```

### 2.2 域与命令

itp-ops.sh 采用「域 + 命令」两层结构：

```
itp-ops.sh <域> <命令> [参数...]
```

**可用域**：
- `cloudos` — 云管台运维（应用/env/pods/scale/deploy）
- `buildx` — 镜像构建（平台 x86 出 amd64）
- `k8s` — K8s 部署（测试环境兜底链）
- `log` — 日志查看（tail/watch/follow）
- `diag` — 网络诊断（reach/routes）
- `web` — 前端发版（release/mount-export）
- `sentinel` — route-sentinel 部署
- `flow` — 一键工作流（backend/frontend/jvm/rollback）
- `version` — 版本号计算（next/debug/bump）
- `lint` — 仓库门禁（yaml）
- `analysis` — 静态分析（endpoints）
- `catalog` — 命令注册表
- `completion` — shell 补全

### 2.3 退出码

| 码 | 含义 | 场景 |
|---|---|---|
| 0 | 成功 | 正常完成 |
| 2 | 用法错误 | 参数不对/未知域 |
| 3 | 前置不满足 | 缺依赖/环境未知 |
| 4 | 传输失败 | ssh 不可达 |
| 5 | 执行失败 | 远端命令非 0 |
| 6 | 缺 --yes | 写操作未确认 |
| 130 | 中断 | Ctrl+C |

---

## 三、常用场景

### 3.1 发布后端服务

```bash
# 方式 1：一键工作流（推荐）
./itp-ops.sh flow backend face-pay-server --env test --yes

# 方式 2：手动分步
./itp-ops.sh version bump --dir face-pay-server          # bump 版本
./itp-ops.sh buildx face-pay-server 1.0.92 --env test    # 出镜像
./itp-ops.sh cloudos deploy face-pay-server 1.0.92 --yes # 切镜像
```

**三元组说明**：
- `模块目录`：仓库根下的目录名（如 `face-pay-server`）
- `应用名`：CloudOS 中的应用名（默认同模块名，可用 `--app` 指定）
- `镜像名`：Harbor 镜像名（默认同模块名，可用 `--image` 指定）

### 3.2 发布前端

```bash
# 一键发布（自动 bump 版本）
./itp-ops.sh flow frontend --env test --yes

# 指定版本
./itp-ops.sh flow frontend --version 1.2.45 --env test --yes

# debug 模式（不进版本序列）
./itp-ops.sh flow frontend --debug --env test --yes
```

### 3.3 JVM 参数升级（IT-020）

```bash
# 一键升级（默认 4Gi → 堆 2900m）
./itp-ops.sh flow jvm face-pay-server --env test --yes

# 8Gi 容器
./itp-ops.sh flow jvm face-pay-server --size 8Gi --env test --yes
```

### 3.4 回滚

```bash
# 回滚到指定版本
./itp-ops.sh flow rollback face-pay-server --to 1.0.89 --env test --yes

# 回滚到上一次版本（需手动查询当前 tag）
./itp-ops.sh flow rollback face-pay-server --last --env test --yes
```

### 3.5 日志查看

```bash
# 实时跟踪（tail -f 别名）
./itp-ops.sh log follow face-pay-server

# 过滤关键字 + 高亮
./itp-ops.sh log follow face-pay-server --grep ERROR,WARN,Exception

# 历史日志 + 实时跟踪
./itp-ops.sh log tail face-pay-server -2000f

# 监控守护（Pod 重启自动重挂）
./itp-ops.sh log watch
```

### 3.6 环境自检

```bash
# CI 门禁 / 发版前置检查
./itp-ops.sh flow health --env test

# 仅检查云管台连通
./itp-ops.sh cloudos health

# 网络可达性自检
./itp-ops.sh diag reach
```

---

## 四、高级用法

### 4.1 --explain 命令解释

所有命令支持 `--explain` 开关，打印行为说明：

```bash
# 查看命令解释
./itp-ops.sh cloudos apps --explain
./itp-ops.sh flow backend --explain
./itp-ops.sh version next --explain 1.2.42
```

**输出包含**：
- 【功能】命令用途
- 【用法】参数格式
- 【参数】详细说明
- 【示例】使用示例
- 【环境与注意事项】默认环境、特殊约束
- 【实现要点】源码位置

### 4.2 JSON 输出

所有命令支持 `-o json`，输出结构化数据：

```bash
# 应用列表（JSON）
./itp-ops.sh cloudos apps -o json | jq '.data.apps[] | select(.status=="Error") | .name'

# 工作流执行结果
./itp-ops.sh flow backend face-pay-server --env test -o json
```

**JSON 信封格式**：
```json
{
  "ok": true,
  "command": ["cloudos", "apps"],
  "data": {...},
  "error": null,
  "duration_ms": 1234
}
```

### 4.3 Shell 补全

```bash
# bash
source <(./itp-ops.sh completion bash)

# zsh
./itp-ops.sh completion zsh > "${fpath[1]}/_itp-ops" && compinit
```

**补全支持**：
- 域名补全
- 子命令补全
- `--选项` 补全
- 模块目录补全（`flow backend <Tab>`）
- 文件路径补全（`--jar/--dockerfile`）

### 4.4 环境变量覆盖

```bash
# 覆盖环境配置
export ITP_ENV_k8s_ns=my-namespace
export ITP_ENV_remote_host=my-host

# 强制环境（等价 --env）
export ITP_OPS_ENV=test
```

---

## 五、工作流详解

### 5.1 flow backend

**用途**：后端服务一键发布

**步骤**：
1. 云管台连通性自检
2. bump 模块 pom 版本
3. mvn package 出 jar
4. buildx 构建镜像
5. cloudos deploy 预演
6. cloudos deploy --yes 执行

**示例**：
```bash
# 标准发布
./itp-ops.sh flow backend face-pay-server --env test --yes

# 指定应用名/镜像名
./itp-ops.sh flow backend --module face-pay-server --app face-pay --image itp/face-pay --env test --yes

# 指定版本（跳过 bump）
./itp-ops.sh flow backend face-pay-server --version 1.0.92 --env test --yes
```

### 5.2 flow frontend

**用途**：前端一键发布

**步骤**：
1. 云管台连通性自检
2. web release（出 zip → 上传 → 升级 → 核验）

**示例**：
```bash
# 自动 bump
./itp-ops.sh flow frontend --env test --yes

# debug 模式
./itp-ops.sh flow frontend --debug --env test --yes

# 指定版本
./itp-ops.sh flow frontend --version 1.2.45 --env test --yes
```

### 5.3 flow jvm

**用途**：JVM 参数升级（IT-020）

**步骤**：
1. 云管台连通性自检
2. env-set 注入 JVM 参数
3. env 复验

**预设值**：
- 4Gi 容器：max_memory=4000, reserved=1000, direct=100, heap=2900m
- 8Gi 容器：max_memory=8000, reserved=2000, direct=100, heap=5900m

**示例**：
```bash
./itp-ops.sh flow jvm face-pay-server --env test --yes
./itp-ops.sh flow jvm face-pay-server --size 8Gi --env test --yes
```

### 5.4 flow rollback

**用途**：应用回滚

**示例**：
```bash
# 回滚到指定版本
./itp-ops.sh flow rollback face-pay-server --to 1.0.89 --env test --yes

# 回滚到上一次版本
./itp-ops.sh flow rollback face-pay-server --last --env test --yes
```

---

## 六、故障排查

### 6.1 常见错误

**Q: 网络不通**
```
[ERROR] 错误：cloudos health 不通过
```
**A**: 检查 USB 网卡 en9 是否连接，`ping 172.20.x.x` 验证可达性。

**Q: 未知环境**
```
[ERROR] 错误：未知环境 xxx
```
**A**: 仅支持 `test` 和 `hongdao`，使用 `--env test|hongdao`。

**Q: 写操作被拒**
```
[ERROR] 错误：写操作缺 --yes
```
**A**: 加 `--yes` 参数确认。

**Q: 红岛生产限制**
```
[ERROR] 错误：红岛是生产：flow 一键流程 MUST 显式加 --allow-prod
```
**A**: 加 `--allow-prod` 并确认低峰 + 回滚预案。

### 6.2 调试技巧

```bash
# 开启详细日志
./itp-ops.sh cloudos apps -v

# 打印完整堆栈
./itp-ops.sh cloudos apps --trace

# 查看命令解释
./itp-ops.sh cloudos apps --explain

# JSON 输出便于排查
./itp-ops.sh cloudos apps -o json
```

### 6.3 日志查看

```bash
# 实时跟踪
./itp-ops.sh log follow face-pay-server -f

# 历史日志
./itp-ops.sh log tail face-pay-server -2000

# 过滤关键字
./itp-ops.sh log tail face-pay-server ERROR Exception

# 监控守护
./itp-ops.sh log watch
```

---

## 七、最佳实践

### 7.1 发版前检查

```bash
# 1. 环境自检
./itp-ops.sh flow health --env test

# 2. 查看当前版本
./itp-ops.sh cloudos app face-pay-server

# 3. 预演发布流程
./itp-ops.sh flow backend face-pay-server --env test

# 4. 执行发布
./itp-ops.sh flow backend face-pay-server --env test --yes
```

### 7.2 版本管理规范

- **本地构建**：末位 +1（1.0.91 → 1.0.92）
- **进位规则**：任一分段 >99 向高位进位（1.2.99 → 1.3.0）
- **调试版本**：使用 `--debug` 或 `version debug`（0.0.0.1，不占序列）
- **禁止覆盖**：NEVER 同 tag 覆盖，MUST 取线上实际版本

### 7.3 安全纪律

- **写操作 MUST --yes**：所有修改类命令需要确认
- **生产环境 MUST --allow-prod**：红岛生产额外标识
- **预演优先**：先用 `--dry-run` 或去掉 `--yes` 查看步骤
- **回滚预案**：发布前记录当前版本，便于回滚

---

## 八、参考资源

- **CONTRIBUTING.md** — 开发纪律与包规范
- **CHANGELOG.md** — 版本变更记录
- **docs/cloudos-notes.md** — CloudOS 平台知识沉淀
- **docs/ops/environments/** — 环境配置文档
- **docs/tracking/** — 问题跟踪文档

---

**维护者**：qd-itp 运维团队
**反馈**：遇到问题请查阅 docs/tracking/ 或联系运维负责人

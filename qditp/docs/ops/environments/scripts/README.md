# scripts — ITP 平台运维脚本（单一入口 itp-ops.sh）

> **位置**：本目录原为仓库根级 `scripts/`，2026-10-25 迁入 `docs/ops/environments/scripts/` 并重构为单一入口。
> **归属**：文档层（git 仓库 `qd/`），随 `docs/` 一起在根 `svn:ignore` 里、**不入 SVN**；
> r1244 曾裁决 `scripts/` 不入库的结论继续有效——它们是**本机工具**，不是版本库内容。

## 一、结构

> 2026-11-03 起底层为 **Python 包 `itpops/`**（纯标准库，双受众：人 + 大模型）。
> **唯一入口是 `itp-ops.sh`**；镜像构建 / 路由哨兵 / 前端发版等原独立脚本已全部收敛为
> 它的子命令（`buildx` / `sentinel` / `web release` / `web mount-export`），
> 不再有第二个 `.sh` 入口。

```
scripts/                          ← 一个标准 Python CLI 项目根（工具代码 / 文档 / 资产分离）
├── itp-ops.sh                 ★ 唯一入口（薄壳：exec python3 -m itpops）
├── pyproject.toml             包元数据 + console script + ruff / pytest 配置
├── README.md                  定位 / 结构 / 快速上手 / CLI 契约（本文件）
├── CONTRIBUTING.md            分层纪律 / 包纪律 / 使用纪律（改包前 MUST 读）
├── CHANGELOG.md               版本化重构与迁移记录（新 → 旧）
├── .gitignore                 本项目自带的忽略规则
├── docs/
│   └── cloudos-notes.md       CloudOS 认证模型 / 环境 Δ / 平台怪癖 / API 分类 / 已知缺口
├── itpops/                    纯标准库；cli / commands / core / 特性包四层
│   ├── __init__.py             __version__（单一版本源，pyproject 动态读取）
│   ├── __main__.py             python -m itpops（等价唯一入口）
│   ├── errors.py               退出码契约 0/2/3/4/5/6/130 + OpsError
│   ├── py.typed                PEP 561 类型标记
│   ├── cli/                    表现层：main / registry / help / completion
│   ├── commands/               命令层：一域一文件，只做注册与分发（CATALOG + main）
│   ├── core/                   公共支撑：ui / version / environments / remote / runtime / crypto
│   ├── cloudos/                特性实现：auth / http / image / pkg + common / apps /
│   │                          scaling / deploy / images / impl（按业务段拆分）
│   ├── buildx/ sentinel/ web/  特性实现（core.py + __main__.py 调试别名，均转发统一入口）
│   ├── analysis/ versioning/ linting/   端点计数 / 版本 bump / YAML 门禁实现
│   └── workflows/              工作流层：runner（执行器）+ definitions（backend/frontend/jvm）
├── tests/                     stdlib unittest（112 例：CLI 契约 + 业务级 + 工作流）
├── buildx.Dockerfile          共享构建模板（NEVER 写 ENTRYPOINT/CMD，理由见文件头与 IT-031）
├── hosts/                     ★ 跑在远端机器上的脚本本体（NEVER 在本机执行）
│   └── k8s-master/             测试跳板 /home/java 下的脚本 + snapshots/ 部署台账
└── migration/                 数据库迁移执行包（自带 README，红岛分域重建基线）
    ├── ingress-config/         istio 快照原始版（clean-ingress-yaml.py 的输入）
    └── ingress-config-cleaned/ 清洗后基线（diag routes 的 diff 基线，硬依赖，NEVER 移走）
```

## 二、快速上手

```bash
cd docs/ops/environments/scripts

./itp-ops.sh                          # 总用法
./itp-ops.sh cloudos health            # 云管台连通性自检（写操作前必跑）
./itp-ops.sh cloudos apps Error        # 只列异常态应用
./itp-ops.sh cloudos env fep-app       # env 全量（排 RPC 地址错配首选）
./itp-ops.sh cloudos scale fep-app 2 --yes    # 副本伸缩（生产写操作，缺 --yes 拒绝）
./itp-ops.sh cloudos deploy recon-server 1.0.28          # 切镜像升级：不带 --yes 是只读预演（校 tag/arch + 打 body）
./itp-ops.sh buildx fep-dev-server 1.2.44 --env test    # 出镜像（平台 x86；红岛/测试同一命令）
./itp-ops.sh k8s deploy fep-dev-server --yes  # 单服务镜像构建推送（兜底链）
./itp-ops.sh k8s deploy-all --yes      # 有序全量，失败即断
./itp-ops.sh log tail fep-app -f       # 实时跟踪日志（Ctrl+C 退出）
./itp-ops.sh log watch                 # 监控守护（Pod 重启自动重挂 tail -f）
./itp-ops.sh diag reach                # 红岛分区可达性自检
./itp-ops.sh diag routes               # 48000 入向路由全链路导出 + 基线 diff
./itp-ops.sh web release --bump        # 前端发版预演（--yes 才上传+升级）
./itp-ops.sh web mount-export          # web-admin 挂导出目录（预演；--yes 下发）
./itp-ops.sh sentinel route-sentinel/target/route-sentinel-1.0.1.jar route-sentinel/Dockerfile --yes
./itp-ops.sh analysis endpoints account-server/src/main/java   # 端点计数
./itp-ops.sh version bump --dir account-server --dry-run    # 版本 bump（预演；去 --dry-run 落盘）
./itp-ops.sh lint yaml                                      # YAML 门禁（语法 + 重复键）
./itp-ops.sh flow list                                    # 三条一键工作流
./itp-ops.sh flow backend face-pay-server --env test --yes # 后端一键发布（自检→jar→镜像→切镜像）
#   位置参数是模块目录名；应用名/镜像名不同时用 --app / --image（或 --module）显式指定
./itp-ops.sh flow frontend --env test --yes              # 前端一键发布（出 zip→上传→升级→核验）
./itp-ops.sh flow jvm face-pay-server --env test --yes   # JVM 三件套一键升级（IT-020）
```

每个域的详细用法：`./itp-ops.sh <域> help`。

**全局参数**（可出现在任意位置，入口负责剥离，域只收业务参数）：
`-e/--env hongdao|test`（**全局生效**；各域默认刻意不统一：cloudos=hongdao、k8s/log=test、
diag 按子命令）、`-y/--yes`（确认写操作）、`-n/--dry-run`（强制预演）、
`-o/--output text|json`（`--json` 为简写）、`-q/--quiet`、`-v/--verbose`、`--no-color`、
`--trace`、`-h/--help`、`-V/--version`、`--`（终止选项解析）。
`./itp-ops.sh catalog -o json` 输出全量自描述命令注册表。

**CLI 用法约定**（2026-11-03 对齐 kubectl / gh / git 的成熟做法）：

| 约定 | 做法 |
|---|---|
| 分层帮助 | `-h` 任意层级有效：根 → 总览；`<域> help`/`<域> -h` → 域内富文本；`<域> <命令> -h` → 由 CATALOG 生成（含参数/示例/输出契约） |
| 输出格式 | 默认 text；`-o json`（或 `--json`）→ stdout 单条信封 `{ok, command, data, error, duration_ms}`，可接 `jq` |
| 写操作门 | 写命令必须 `-y/--yes`；`-n/--dry-run` 强制预演（优先于 --yes），无预演分支的命令直接报用法错，绝不静默写 |
| 质量开关 | `-q` 抑制 INFO/STEP、`-v` 出 DEBUG（远端 ssh 命令行）、`--trace` 打全栈 |
| 颜色 | 仅 TTY 上色；`NO_COLOR` 关、`CLICOLOR_FORCE=1` 强制、`--no-color` 最高优先 |
| 退出码 | 0 成功 / 2 用法 / 3 前置 / 4 传输 / 5 执行 / 6 缺 --yes / 130 中断 |
| 补全 | `./itp-ops.sh completion bash|zsh` 生成脚本，源或放入补全目录 |
| 环境变量 | `ITP_OPS_ENV` 等价 `--env`（显式 --env 优先） |

```bash
./itp-ops.sh -h                       # 总览（含退出码/环境变量约定）
./itp-ops.sh cloudos scale -h         # 单命令帮助（不会把 -h 当参数执行）
./itp-ops.sh completion zsh > "${fpath[1]}/_itp-ops"
./itp-ops.sh -o json cloudos apps Error | jq '.data.apps[].name'
```

**环境与跳板**（红岛 ssh/kubectl 通道 2026-10-26 实测已通；但红岛是**生产**，默认一律停在 test）：

| `--env` | 跳板 `REMOTE_HOST` | K8s 命名空间 | Harbor 项目 | 该环境下可用的域 |
|---|---|---|---|---|
| `test`（k8s/log 默认） | `k8s-master` | `itp` | `itp` | 全部 |
| `hongdao`（cloudos 默认） | `hd-linux`（172.120.2.13，主机名 `ITP01`） | `itpb` | `itpb`（推送账号 `itpadmin`） | `cloudos` / `log watch` / `diag reach` |

> **两个实测缺口（2026-10-26，决定「哪个域能打红岛」）**：
> 1. `k8s` 域只对测试可用——红岛跳板 `hd-linux` 上**没有** `/home/java`，也没有 `build-push.sh`，
>    且远端只有 `podman` 没有 `docker`。红岛发版 MUST 走平台侧构建
>    （本机只传 jar + Dockerfile，镜像由平台 x86 构建节点产出）。⚠ 但入口**不是** `cloudos image build`——它走旧端点
>    `/images/build`，红岛实测 404 / `img.5`；实测可用的是 `itp-ops.sh buildx`（见 `构建与发布.md` §三第 5 步、IT-024）。
> 2. `log tail` 只对测试可用——它依赖远端 `/home/java/k8s-logview.sh`，该脚本红岛尚未部署；
>    红岛看日志用 `log watch`（走 kubectl）或 `ssh hd-linux kubectl logs`。
> `hosts/k8s-master/` 因此只是**测试跳板**脚本的仓内镜像，红岛侧脚本未入仓。

## 三、CloudOS 知识沉淀

认证模型、两套环境 Δ、平台实测怪癖、API 分类与已知缺口已拆到
[docs/cloudos-notes.md](docs/cloudos-notes.md)。改 `itpops/cloudos/` 或走发版链前 MUST 读，
尤其是「密码 MUST 真 RSA 加密」「`domain` 必须为 `default`」「账号 `itpbadmin`」三条。

## 四、itpops 包纪律

改包前 MUST 读 [CONTRIBUTING.md](CONTRIBUTING.md)：分层纪律、输出分流、退出码契约、
远端参数转义、写操作门、token 缓存、RSA 唯一实现与验证命令。

## 五、使用纪律

路径推导、写操作默认拒绝、`ALL_SERVICES` 单一真源、`migration/` 语义、敏感值口径、
「分析 / 门禁 / bump 一律走 `itp-ops.sh`」等约定见
[CONTRIBUTING.md](CONTRIBUTING.md)「三、使用纪律」。

## 六、重构与迁移记录

逐版本变更见 [CHANGELOG.md](CHANGELOG.md)（按新 → 旧排列）。当前版本 **itpops 2.8.1**。

---

**文档索引**：[README.md](README.md)（上手）· [CONTRIBUTING.md](CONTRIBUTING.md)（改包）·
[CHANGELOG.md](CHANGELOG.md)（沿革）· [docs/cloudos-notes.md](docs/cloudos-notes.md)（平台知识）

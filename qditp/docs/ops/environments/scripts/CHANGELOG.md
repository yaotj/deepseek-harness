# 变更记录（CHANGELOG）

> 2026-11-04 由 `README.md`「六、重构与迁移记录」拆出，历史条目按**新 → 旧**排列、内容逐字保留。
> 版本号真源是 `itpops/__init__.py` 的 `__version__`（`pyproject.toml` 动态读取）。

## 2.5.0 — 2026-11-04 按最佳实践重新组织项目层

- **唯一入口语义收口**：`python -m itpops.buildx` / `python -m itpops.sentinel` 从「直接调特性实现」
  改为注入域名后转发 `itpops.cli.main`，不再绕过全局参数 / JSON 信封 / 退出码契约；
  正式入口仍只有 `itp-ops.sh`（`python -m itpops` 为等价别名）。
- **命令层瘦身**：`commands/cloudos.py`（1776 行 / 30 子命令）整块下沉到
  `cloudos/impl.py`，命令层变为十余行薄壳（重导出 `CATALOG` + `main`）；
  相对导入层级不变，help / stdout / `catalog -o json` 逐字对比无差异。
- **文档按开源惯例分层**：414 行 README 拆为 `README.md`（定位 / 上手 / CLI 契约）、
  `CONTRIBUTING.md`（纪律 / 验证）、`CHANGELOG.md`（沿革，新 → 旧）、
  `docs/ops/environments/scripts/docs/cloudos-notes.md`（平台知识）；章节编号与标题保留，
  避免破坏全仓 §引用锚点。
- **项目元数据**：`pyproject.toml` 补 `readme` / `dev` 可选依赖 / ruff lint 规则 / 包数据，
  新增项目级 `.gitignore` 与 `itpops/py.typed`（PEP 561）。
- 勘误：`cloudos/__init__.py` 里「RSA 用 openssl 子进程」的过时描述改为
  `core/crypto.py` 纯标准库实现；`commands/buildx.py` 命名说明修正旧入口名。
- 验证：`unittest` 59 passed、`compileall` / `bash -n` 通过、各域 help 与 catalog 结构
  逐字一致、文档门禁 ERROR 0。

**2026-11-03 文件组织对标开源实践（itpops 2.3.0）**：

- 分层重排（对照 pip `_internal/cli/` + `_internal/commands/`、ansible `cli/`）：
  `cli/{main,registry,help,completion}.py`（表现层）/ `commands/*.py`（命令层，原 `domains/`）/
  `core/{ui,version,environments,remote,runtime}.py`（公共支撑）/ `cloudos|buildx|sentinel|web/`（特性实现）。
- 命名修正：`envx.py` → `environments.py`、`execx.py` → `runtime.py`；`catalog` 从「数据+命令混合」
  拆成 `cli/registry.py`（数据）+ `commands/catalog.py`（命令）；`buildx.py` / `route_sentinel.py`
  收进特性包 `buildx/core.py` / `sentinel/core.py`。
- 新增 `pyproject.toml`：版本单一源头（`itpops.__version__`，`__version__` 取代 `ITP_OPS_VERSION`）、
  `itp-ops` console script（`pipx install .` 后可直接用）、ruff / pytest 配置、零第三方依赖。
- 新增 `tests/`（stdlib unittest，27 例）：版本算法进位、环境 profile 覆盖规则、CLI 契约
  （信封/退出码/分层帮助/补全/`--`/dry-run）、注册表完整性。
- 验证：`python3 -m unittest discover -s tests` 27 passed；`itp-ops.sh -h` / `catalog -o json`（10 域）
  正常；`compileall` 通过；lint ERROR 0。
**2026-11-03 入口与命名收敛（「只有一个入口」）**：

- 删除 4 个独立入口，全部收敛为 `itp-ops.sh` 子命令（实现仍在 `itpops/`，可 `python3 -m` 直调）：

  | 旧文件 | 新命令 | 改名理由 |
  |---|---|---|
  | `hongdao-buildx.sh` | `itp-ops.sh buildx` | 名字暗示「红岛专用」，实际 `--env` 双环境 |
  | `hongdao-buildx.Dockerfile` | `buildx.Dockerfile` | 同上；共享模板，FROM 按环境替换 |
  | `hongdao-deploy-route-sentinel.sh` | `itp-ops.sh sentinel` | 名长且带 hongdao，实为一次性部署工具 |
  | `web_frontend_release.py` | `itp-ops.sh web release` | 收敛入口；参数/示例见 `web release -h` |
  | `mount-web-admin-export.py` | `itp-ops.sh web mount-export` | 同上 |
  | `web-frontend-release.README.md` | 并入 `docs/ops/web前端云平台发版流程.md` | 脚本没了，README 不再独立存在 |

- 附带：`domains/version_cmd.py` → `domains/version.py`（去掉历史 `_cmd` 后缀）；`itpops/buildx/core.py` /
  `route_sentinel.py` / `web/*` 的用法文本与 CATALOG 改指新命令；`buildx` / `route_sentinel` 入口改为
  复用入口上下文（此前各自 `Ctx()` 会把 `-q/-v/-o json` 等全局态冲掉）。
- 全仓引用（AGENTS.md、构建与发布、环境文档、tracking、skills、MEMORY）同步改写；`bash itp-ops.sh` 冗余前缀清理。

**2026-11-03 CLI 用法现代化（v2.1.0，参照 kubectl/gh/git 惯例）**：

- 全局参数按成熟 CLI 补齐：`-e/--env`、`-y/--yes`、`-n/--dry-run`（预演优先，无预演分支
  直接报用法错）、`-o/--output text|json`（`--json` 保留为简写）、`-q/--quiet`、`-v/--verbose`
  （出远端命令行）、`--no-color`（+`NO_COLOR`/`CLICOLOR_FORCE`）、`--trace`、`--` 终止符、
  `-V/--version`（含 python/git commit）。
- 分层帮助：`<域> <命令> -h` 由 `itpops/cli/help.py` 从 CATALOG 生成，**修掉旧行为**
  （`cloudos scale -h` 会把 `-h` 当应用名执行、`k8s deploy -h` 直接报缺 --yes）；
  域级 `help` 仍是各域富文本，基线逐字节未变（6 域实测 diff 全空）。
- 新增 `itp-ops.sh completion bash|zsh`（从 CATALOG 派生，域/子命令增删自动同步），
  以及 `itp-ops.sh help <域> [命令]` 别名。
- `-o json` 信封增加 `duration_ms`；`catalog` 契约补充 output_formats / env_vars / help / completion。
- 回归：6 域 help 与改造前逐字节一致；`version next` 算法 parity 不变；`catalog -o json` 全 ok。

**2026-11-03 Python 化重构**（「重新组织脚本，底层用 python，分模块，给大模型也给人用」）：

- 底层 bash → Python 包 `itpops/`（纯标准库，`from __future__ import annotations`）；
  `itp-ops.sh` 与 `itp-ops.sh buildx` / `itp-ops.sh sentinel` /
  `itp-ops.sh web release` / `itp-ops.sh web mount-export` 保留原名为薄壳，**命令面逐字兼容**
  （help/结果文本与旧版 stdout diff 为空，逐域实测）。
- 双受众契约：人——结果 stdout、日志/横幅 stderr、TTY 才着色；大模型——`--json` 单条信封
  `{ok, command, data, error}` + `catalog --json` 自描述命令注册表 + 稳定退出码
  （0/2/3/4/5/6，见 `itpops/errors.py`）。
- 版本算法真源收敛到 `itpops/core/version.py`（与 `lib/version.sh` 逐字节 parity 实测：
  1.2.42→1.2.43、1.2.99→1.3.0、1.99.99→2.0.0、99.99→1.0.0）；顺带修正
  `itp-ops.sh web release` 旧自带 `next_version` 的多重进位 bug（1.99.99 曾错算 1.0.0.0），
  前端发版统一走真源。
- 移植期修复的 bash 历史缺陷（均已实测证实并就地记录在对应模块 docstring「与 bash 版的差异」）：
  `log watch` 漏 tail 子命令致从未工作、`WATCH_NS` source 时求值吃不到 `--env hongdao`、
  route-sentinel 清理循环分词错配 / heredoc 引号致 `${VER}` 空 / 部署段 `set -u` 未绑定等。
  零差异原则其余保留：HARBOR_PROJECT 历史错位、spec-set 第 20 次探测怪癖、diag 多端口行恒 FAIL 等照搬。
- 旧 bash（`lib/`、`domains/`）与迁移期对照基线 `*.legacy.*` 在 parity 验证全绿后删除；
  机制注释沉淀于本 README §四 与各模块 docstring。

**2026-10-26 合理性审计（「每一个脚本都要有合理性」）**：

- **`db-history/` 全删**（11 个历史 SQL，HEAD 里本就未跟踪）：一度因「它是 schema 生成器的 DDL 补充源、
  AGM 的 2 序列 + 2 索引只在这里」而保留 6 个（分入 `migration/ddl-extra/` `tools/` `seed/`），
  **用户二次裁决「不需要保留」后 11 个全部删除**——理由：这些 DDL **已经烘进生成基线**
  （`20261017_schema_plat.sql` 的 105/177/200/723/1319/1322 行、`schema_user.sql` 305 行），
  源文件只影响「重跑生成器」。⚠️ **代价已就地记录**：`20261017_schema_gen.py` 的 ddl-extra 分支移除、
  生成文件 7 处 `[来源]`/`[序列]` 改标「源文件已删…MUST 手工保留」、manifest 5 行标「（已删）」并附警示段，
  `deploy/sche/by-domain/_补充索引_本地DDL_需与老库核对.sql` 与 5 篇 docs 的引用同步改写。
  **重跑生成器前 MUST 先读 `migration/20261017_schema_manifest.md` 的「（已删）」注记。**
- **`analysis/` 两个死脚本删**：`extract_interfaces.py` 依赖不存在的 `API.md` + `docs/提取的需求`
  （实测一跑即崩）、`extract_requirements.py` 扫的 4 个源目录全不存在（实跑「共提取 0 个文档」）。
  `domains/analysis.sh` 的 `interfaces` / `requirements` 两个子命令同步下线——**保留判据是「能跑 + 有人用」，
  从来没有可跑源文档的脚本就是噪音**。
- **`migration/` 三项删**：`face-pay-log-config.md`（2026-10-14 一次性调查笔记，全仓零引用，
  所引 VictoriaLogs 地址已是测试环境旧值）、`pull-and-save.sh` + `image-list.txt`
  （旧 Harbor 镜像归档工具，自包含无引用，迁移已完成不再复用）、空目录 `migration/images/`。
- **第二轮（同日继续审计）`legacy/` 整体删**（7 文件）：`_fix.ps1` / `_run_script.ps1`
  （含明文 root 口令，目标 `172.20.211.21`——按纪律第 6 条的现行口径，**仓库内明文本身不是删它的理由**，
  这两个文件是「零引用的历史存档」被清理，与口令无关）、
  `enable-tracing-20261014.sh`（已执行完）、`klog.cmd` / `klog.ps1`（已被 `itp-ops.sh log tail` 取代）、
  `cloudos-api.sh`（认证结论已证伪，内容已并入 `domains/cloudos.sh`）及该目录 README。
- **第二轮 `generate-prod-migration-report.py` + 产物 `prod-migration-config-checklist.md` 删**：全仓零引用，
  生成器唯一产出就是那份没人读的报告。⚠️ 连带影响：原 `k8s-current-config/` 失去唯一消费者，
  现已按环境迁到 `../snapshots/test/`，作**集群实况快照**独立保留（内容无害——
  `secrets.txt` 只是 Secret 名称清单，不含值）。
- **保留且有理由**：`ingress-config/`（clean-ingress-yaml.py 输入，产出 diag routes 的 diff 基线）、
  `hosts/k8s-master/`（`log tail` 远端侧 `/home/java/k8s-logview.sh` 本体参考）、
  migration 执行包与 20261023 域拆分工具链（全部有外部引用，且分域重建仍在进行）。
- **原则**：保留 = 有独特内容且有人会用；删除 = 已被基线/文档/代码仓吸收的纯执行痕迹，或根本跑不起来。
  每个去留结论都记在被删/被迁文件的相关引用处，NEVER 静默删。

**2026-10-25 重构**（「重新组织结构，重新编写脚本」）：

- 单一入口 `itp-ops.sh` + 公共 `lib/` + 五个域（cloudos/k8s/log/diag/analysis）。
- 三合一：`cloudos-hongdao.sh` + `image-build.sh` + `cloudos-api.sh` → `domains/cloudos.sh`；
  `deploy-to-harbor.sh` + `batch-deploy.sh` → `domains/k8s.sh`（ALL_SERVICES 两份手抄清单并为单一真源）；
  `klog.sh` + `klog-watcher.sh` → `domains/log.sh`；`dump-48000-routes.sh` + `red-island-reachability.sh` → `domains/diag.sh`。
- 顺带修掉的旧缺陷：`KLOG_CONTAINER` 以前设了也不生效（ssh 不转发本地环境变量，现拼进远端命令前缀）；
  watcher 状态文件写入 `svc=key` 却按 `^svc|` 查找，永远匹配不到 ⇒ 每 30s 把全部监控杀一遍再起一遍（现按同格式查找 + PID 杀进程树，堵住 ssh 子进程泄漏）；`dump-48000-routes.sh` 基线目录路径失效被 `[ -f ]` 静默跳过（现显式定位 + 缺失告警）。
- 被取代的 9 个旧脚本 `git rm`，内容逐条并入新脚本；历史/一次性脚本一度进 `legacy/` 存档，
  **2026-10-26 第二轮审计后该目录整体删除**（见下）。
- 验证：`bash -n` 全量、各域 usage 冒烟、`--yes` 门拒真发、`analysis endpoints` 实跑、lint ERROR 0。

**2026-10-25 迁移**：根级 `scripts/`（98 文件）整体迁入 `docs/ops/environments/scripts/`；
全仓 `docs/**`、`AGENTS.md`、`deploy/**` 引用（184 处）同步改写；`docs/提示词/lint-prompts.py`
根级路径白名单校正（`scripts/` → `deploy/`）；运行时路径深度修复并实测。


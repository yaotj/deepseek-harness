# itpops 开发约定（CONTRIBUTING）

> 2026-11-04 由 `README.md`「四、itpops 包纪律」「五、使用纪律」拆出，条款逐字保留。
> 改 `itpops/` 前 MUST 读；运维使用方法见 [README.md](README.md)，平台知识见 [docs/cloudos-notes.md](docs/cloudos-notes.md)。

## 一、如何验证

```bash
cd docs/ops/environments/scripts
python3 -m unittest discover -s tests     # stdlib，零第三方依赖
python3 -m compileall -q itpops
bash -n itp-ops.sh
cd ../../../../ && python3 docs/提示词/lint-prompts.py   # 在 qditp/ 仓根跑，ERROR MUST 为 0
```

> 分层纪律：`cli/` 只做表现（解析 / 帮助 / 补全 / 信封），`commands/` 只做编排
> （注册 + 分发 + 输出 + 抛错），`core/` 只放无业务语义的公共支撑，
> 特性实现放各自包。命令层一旦变成实现主体（如 cloudos 的 1776 行），
> MUST 下沉到 `itpops/<特性>/impl.py`，命令层只重导出 `CATALOG` + `main`。
> NEVER 让 `core/` 反向依赖 `commands/`，也 NEVER 在 `cli/` 里写业务。

## 二、itpops 包纪律（改包前 MUST 读；2026-11-03 由 bash lib/ 迁移）

> 原「lib 纪律」逐条迁移如下。bash 专属坑（set -euo / 命令替换 die / printf %q）
> 已由 Python 语言特性消除；对应能力在 `itpops/core/`（ui / remote / runtime / version / environments）。
>
> **分层纪律（对标 pip `_internal/cli/` + `_internal/commands/`）**：
> `cli/` 只做表现（解析/帮助/补全/信封），`commands/` 只做编排（调特性包、输出、抛错），
> `core/` 只放无业务语义的公共支撑，特性实现放各自包（`cloudos/` `buildx/` `sentinel/` `web/`）。
> NEVER 让 `core/` 反向依赖 `commands/`，也 NEVER 在 `cli/` 里写业务。

1. **失败抛 `OpsError(message, exit_code)`**，退出码契约见 `itpops/errors.py`：
   0 成功 / 2 用法 / 3 前置 / 4 传输 / 5 执行 / 6 缺 --yes。NEVER 静默继续。
2. **人机输出分流**：人看的结果走 `ui.out()`（stdout）、日志/横幅走 `ui.log_*`（stderr，TTY 才着色）；
   `--json` 模式 stdout 只有 cli 打的一条信封（`{ok, command, data, error}`），机器载荷走 `ui.result()`。
   **兜底由入口强制**：`-o json` 分发期间 stdout 被临时导向 stderr（`contextlib.redirect_stdout`），
   域里残留的 `print` 污染不了信封；新代码仍 MUST 用 `ui.*`，别依赖兜底。
3. **远端参数转义**用 `remote_quote_args`（shlex.quote），NEVER 直接拼裸参数。
4. **跟踪类命令**（`log tail -f`）NEVER 合并进非 TTY 通道——`remote_exec_tty` 的伪终端是刷新/Ctrl+C/颜色的前提。
5. **新增 API 模块**按域建 `itpops/cloudos/<域>.py`，只封装本仓用到的端点，docstring 写清前缀与易错点。
6. **域契约**：`itpops/commands/<域>.py` 暴露 `main(args) -> int` + `CATALOG`（自描述注册表，
   `itpops catalog` 汇总给大模型）；新增域还要在 `itpops/cli/registry.py` 的
   `_DOMAIN_MODULES` 登记。
7. **token 缓存**按环境分文件（`/tmp/.cloudos-<env>.token`，格式 `<时间戳> <token>`，与旧 bash 互读兼容）。
8. **改包 MUST 跑单测**：`cd docs/ops/environments/scripts && python3 -m unittest discover -s tests`
   （134 例覆盖版本算法 / 环境 profile / CLI 契约 / 注册表 / 直调 / JSON 隔离 / 加密 /
   ui 开关 / bump / 端点 / YAML / 布局与委托 / cloudos 业务判据；纯 stdlib，无需 pytest）。
9. **加密只有一处**：RSA PKCS#1 v1.5 唯一实现在 `itpops/core/crypto.py`，
   `cloudos/auth`、`buildx`、`web` 一律调用它——NEVER 复制粘贴一份（2026-11-04 review 已清掉 3 份重复）。
   `pyproject.toml` 另有 `[tool.pytest.ini_options]`，装了 pytest 也能直接 `pytest`。


## 三、使用纪律

1. **路径推导**：脚本 MUST 由自身位置推导仓库根（`BASH_SOURCE`/`__file__`），NEVER 硬编码绝对路径。
   入口 `itpops/core/runtime.py` 用 `account-server` 目录存在性断言——深度再错会直接报错而不是静默找错目录。
2. **写操作默认拒绝**：`k8s deploy/deploy-all`、`cloudos act/scale/scale-all/spec-set/deploy/image build`
   必须显式 `--yes`（入口全局解析，位置任意）。
3. **ALL_SERVICES 是单一真源**（`itpops/commands/k8s.py`）：24 个服务，语义是**可构建推送的模块全集**
   （根 pom 里有 kubernetes-maven-plugin 的模块目录），**不是生产部署清单**——两者 historically
   不相等（详见 `itpops/commands/k8s.py` 处注释：3 个死/下线模块在构建清单里、web-admin/前端不在构建清单里、
   跨环境共性.md §一 自身还缺 3 个在跑的服务）。NEVER 擅自「对齐」任何一边；服务名是仓库根模块目录名，
   **镜像名 ≠ 模块名**（`fep-app-server` 的镜像是 `itp/fep-app`，取自 pom `<image.prefix>`，
   NEVER 用目录名拼）。`web-admin` 不适用本脚本（前端走 zip + nginx Deployment）。
4. **`migration/` 语义**：**执行包**（`2026101x_bundle_*.sql`：可重跑、有生成器、有断言、有回滚）+
   `20261017_schema_*.sql`（分域建表基线，含来源标注）+ 20261023 域拆分工具链。
   原 `db-history/`（11 个历史 SQL）已于 2026-10-26 全删（5 个纯执行历史 + 6 个一度迁入
   `ddl-extra/`/`tools/`/`seed/` 后经用户二次裁决一并删除）。⚠️ **重跑 `20261017_schema_gen.py`
   不会再产出那些已删脚本里的 DDL**（AGM 2 表 + 2 序列 + 2 索引、COM_DEVICE_SYN_KEY、
   TBL_NOTICE_APP_FAILURE_RECORD、USER_ITP_REG_INFO_BAK20260909）——它们仍在生成基线里，
   **谁重跑谁负责手工补回**，详见 `migration/20261017_schema_manifest.md` 的「（已删）」注记。
5. **别用文件名猜用途**：`hosts/k8s-master/` 里是**跑在远端 k8s-master 上**的脚本本体（NEVER 在本机执行，
   会连远端集群）；`migration/ingress-config*/` 是 istio 抓取快照，与数据库无关；
   集群实况快照在**上一层** `docs/ops/environments/snapshots/{test,hongdao}/`（按环境分层，
   与 scripts 平级——它们不是脚本，是抓取产物）。
6. **敏感值口径（2026-10-26 用户裁定，本项目唯一真源）**：**仓库内的明文口令与配置即唯一真源**，
   脚本默认值直列不掩码——`lib/cloudos-auth.sh` 的 `CLOUDOS_PASSWORD`、迁移 SQL 的 `IDENTIFIED BY`、
   校验脚本的 `ITP_DB_PASSWORD` 都有仓库内现值，**MUST NOT 改成「必须外部提供 / 缺失即退出」**；
   环境变量只作**可选覆盖**（临时改密 / 试别的账号），不设也能跑。跨环境真源表见
   `docs/ops/environments/跨环境共性.md` §三。
   - **仍适用**：`NEVER 外传`、截图打码、群聊 / 邮件不贴口令（渠道纪律，与仓库内直列不冲突）。
   - **已作废**：2026-10-26 审计曾把 `legacy/` 的两 `.ps1`（含明文 root 口令，目标 `172.20.211.21`）
     连同「旧值视为已泄露 MUST 轮换」一并记为安全待办；按本次裁定，**仓库内明文不再视为待治理缺陷**，
     `legacy/` 目录已删（见 §六），其内容不进版本库是「无用代码清理」理由，NEVER 再引申为安全理由。
   - 口径反转**不新增暴露面**：这些值早已随 `c4ce75894a` 推送到 `origin/main`，git 历史里的明文一直存在。
   另：`enable-tracing-20261014.sh` 已于 2026-10-14 执行完毕，后续接入链路跟踪 MUST 按
   `docs/architecture/common-components.md` §3.1.2「三行成组」口径做，NEVER 照抄那个已删脚本的 env 注入方式。
7. **分析/门禁/bump 一律走 `itp-ops.sh`**：端点计数在 `itpops/analysis/endpoints.py`、
   YAML 门禁在 `itpops/linting/yaml_lint.py`、版本 bump 在 `itpops/versioning/bump.py`，
   原 `analysis/count-endpoints.py`、`scripts/lint-yaml.py`、`scripts/bump-version.py` 已删除。
   相对路径以仓库根为基准（不再需要先 cd）。
   （原 `extract_interfaces.py` / `extract_requirements.py` 于 2026-10-26 审计删除——
   依赖的 `API.md` 与 `docs/提取的需求` 从不存在，一个跑必崩、一个空转输出 0。）


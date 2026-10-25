# scripts — ITP 平台运维脚本（单一入口 itp-ops.sh）

> **位置**：本目录原为仓库根级 `scripts/`，2026-10-25 迁入 `docs/ops/environments/scripts/` 并重构为单一入口。
> **归属**：文档层（git 仓库 `qd/`），随 `docs/` 一起在根 `svn:ignore` 里、**不入 SVN**；
> r1244 曾裁决 `scripts/` 不入库的结论继续有效——它们是**本机工具**，不是版本库内容。

## 一、结构

```
scripts/
├── itp-ops.sh                ★ 唯一入口：全局参数（--env/--yes）解析 + 域分发
├── lib/                      公共库（NEVER 设 shell 选项；失败返回非 0 + 调用方显式判）
│   ├── core.sh                 日志 / 写操作门(--yes) / 仓库根推导 / 依赖检查
│   ├── remote.sh               ssh 跳板助手（k8s-master；TTY 纪律 + printf %q 转义）
│   └── cloudos-{auth,http,image,pkg}.sh   CloudOS API 四模块（auth ← http ← {image,pkg}）
├── domains/                  按域分组的命令实现（每个域一个 <domain>_main）
│   ├── cloudos.sh              云管台运维：apps/env/pods/logs/scale/act/spec/image…
│   ├── k8s.sh                  镜像构建推送：deploy（失败继续）/ deploy-all（失败即断）
│   ├── log.sh                  日志：tail（查看）/ watch（Pod 重启自动重挂监控）
│   ├── diag.sh                 诊断：reach（红岛分区可达性）/ routes（48000 入向路由导出）
│   └── analysis.sh             静态分析：endpoints
├── analysis/                 count-endpoints.py 本体（由 domains/analysis.sh 调度，勿直接跑）
└── migration/                数据库迁移执行包（自带 README，红岛分域重建基线）
```

## 二、快速上手

```bash
cd docs/ops/environments/scripts

./itp-ops.sh                          # 总用法
./itp-ops.sh cloudos health            # 云管台连通性自检（写操作前必跑）
./itp-ops.sh cloudos apps Error        # 只列异常态应用
./itp-ops.sh cloudos env fep-app       # env 全量（排 RPC 地址错配首选）
./itp-ops.sh cloudos scale fep-app 2 --yes    # 副本伸缩（生产写操作，缺 --yes 拒绝）
./itp-ops.sh k8s deploy fep-dev-server --yes  # 单服务镜像构建推送
./itp-ops.sh k8s deploy-all --yes      # 有序全量，失败即断
./itp-ops.sh log tail fep-app -f       # 实时跟踪日志（Ctrl+C 退出）
./itp-ops.sh log watch                 # 监控守护（Pod 重启自动重挂 tail -f）
./itp-ops.sh diag reach                # 红岛分区可达性自检
./itp-ops.sh diag routes               # 48000 入向路由全链路导出 + 基线 diff
./itp-ops.sh analysis endpoints account-server/src/main/java   # 端点计数
```

每个域的详细用法：`./itp-ops.sh <域> help`。

**全局参数**（可出现在任意位置，入口负责剥离，域只收业务参数）：
`--env hongdao|test`（仅 cloudos 域用，默认 hongdao=红岛生产）、`--yes`（确认写操作）、
`-h/--help/--version`（仅域前有效）。

## 三、CloudOS 知识沉淀（原 cloudos/README.md，重构后并入此处）

### 3.1 认证模型（红岛实测正确姿势）

```
① POST /api/sys/oapi/v1/double_factor/login
   Body: {"username":"itpbadmin","rsa":true,"password":"<RSA-1024 密文 base64>","domain":"default"}
② 业务 API 请求头：X-Auth-Token: <res.token>
```

- **密码 MUST RSA 加密**（PKCS#1 v1.5 + base64），密文恒 **128 字节 / 172 字符**。
  长度不对即公钥或填充不对，`cloudos auth-debug` 会直接报出来。
- **`domain` MUST 为 `default`**。传空返回 `svcmgt.internalServerError`（不是密码错）。
- **账号真值是 `itpbadmin`**，不是 `itpbdmin`（项目文档原笔误）、也不是 `itpadmin`（那是 Harbor 推送账号）。
- ⚠️ **历史错判**：2026-10-22 曾把认证写成「base64 明文 + rsa:true」，依据是「测试环境跑通过」。
  实际是浏览器抓包证明前端走 JSEncrypt，平台按 `rsa` 字段决定是否解密。base64 明文在红岛一律返回
  `usrmgt.userOrPasswordError`——**看着像密码错，其实是没解密**，极易引向反复试密码。
  测试环境至今没抓到公钥，相关命令拒绝执行并给抓取指引；**NEVER 为「能跑」改回 base64 明文**。

RSA 公钥抓法（平台升级后需重抓）：

```
https://<host>/platform/static/js/login/login.<hash>.js   ← 文件里搜 encryptRSA / setPublicKey
```

`<hash>` 从 `/platform/static/js/app.<hash>.js` 的 chunk 映射表里取（本项目实测 `login/login → 9d53907c`）。

### 3.2 平台实测怪癖（都是坑）

| 现象 | 结论 |
|---|---|
| `POST /apps/{uuid}/scale` 按官方文档发 `{"replicas":N}` → 500 `McpPlaintext` 反序列化错 | 实际报文是 **body `[]` + query `?replicas=N`**（2026-10-23 实测，三种写法只有它通）。报错完全不像参数位置问题，别转去用 kubectl——两者对平台是两套状态 |
| `logs` 返回 200 + 空 body | 红岛该接口恒空（换 pod、换容器均空）。日志真源是 VictoriaLogs `172.120.2.13:31920` 或 `kubectl logs` |
| `domain` 传空 → `svcmgt.internalServerError` | 不是服务故障，是缺 domain |
| `userOrPasswordError` | 先查账号拼写，再查是否真 RSA 加密，**最后**才怀疑密码 |
| `admin` / `itpadmin` 登录失败 | `itpadmin` 是 Harbor 推送账号，不是云管台账号 |
| 双实例判据 | 不是 `instance_num=2`，而是**两个副本落在不同节点**；同节点双副本等于没扩容。查法：`ssh hd-linux "kubectl get pods -n itpb -o custom-columns=APP:.metadata.labels.app,NODE:.spec.nodeName --no-headers \| sort"` |
| 跨模块 RPC 全失败 | 平台按 `application.properties` 生成的 env 保留测试默认值，不显式注入 `SERVICE_*_URL` 就指向 `itp` 命名空间（IT-016）。`cloudos env <app>` 一次打全 38 项 env 核对 |

三个设计点（`scale` 族命令）：**幂等**（已是目标值不发请求）、**异步复验**（`status=true` 只代表已下发，
轮询 `instance_ok_num` 每 3s 一次最多 180s，未达标退出非 0——看 HTTP 200 就走人 = 假成功）、
**批量默认 dry-run**（`scale-all` 不带 `--yes` 只打印清单）。

### 3.3 API 分类（5.0 PaaS 文档 232 端点，7.0 路径一致）

| 分类 | 端点数 | 路径前缀 |
|---|---|---|
| 应用仓库 | 15 | `/api/cloud/deploy/v1.0/*` |
| 应用管理 | 63 | `/api/cloud/app-mgmt/v1.0/*` |
| 容器云（镜像服务） | - | **`/api/cloud/hcr/v1.0/*`**（文档基址 `http://{ip}:11000`，但红岛 app-mgmt 实测在 https 443——各服务挂哪个端口不统一，MUST 现探） |
| 服务治理 | 27 | `/api/cloud/svcgw/v1.0/*` |
| 持续交付 | - | **`/api/cicd/*`**（原文写 `/cicd/*`，实测缺 `/api` 一层会 404；不带 token 返回 401 才是路由存在的判据） |
| 其他 | 127 | 各类接口 |

文档真源：`docs/reference/cloudos/CloudOS 5.0_PaaS_API_E7501.md`（「企业应用API」章）。
控制台页面 `/enterprise-apps/#/apply/app/appDetail/<uuid>` 对应的就是 `app <uuid>` 那组接口。

### 3.4 已知缺口（首次实测前勿当结论）

1. `fileFlag` 语义未验证（`image build` 全押在这里）。若平台要求它由上传接口返回而非客户端生成，
   build 会报 file not found；判定看 `data.build[]` 的 COPY。不成立时的正路是软件中心 jar 类型部署。
2. `totalChunks=1` 一次性上传；jar 超网关 body 限制（经验 ~100MB）需改分片，续传/去重行为未实测。
3. 上传会在软件中心留 jar 应用包记录（`POST /packages` 副作用）；`pkg_delete_version` 可清，默认不删。
4. 镜像服务网关基址未定位（2026-10-23 网络中断）。`cloudos image probe` 就是为此写的。
5. 构建日志走 `data.build[]`（docker 原始输出），平台无错误码表，失败只能读这里。
6. 镜像架构不可指定，构建后 MUST 核对 amd64。
7. 测试环境（`--env test`）RSA 公钥未抓到，命令停在 `env_init` 报错；容器云 `pod_*` 前缀亦未经实测。

## 四、lib 纪律（改 lib 前 MUST 读）

1. lib **NEVER** 设 shell 选项（`set -euo`），由入口决定；函数失败返回非 0 + 调用方显式判。
2. **NEVER 在命令替换里 die**（`x=$(foo)` 里 `foo` 调 `die`/`exit` 传不出子 shell，父 shell 拿空值继续，
   表现成一串下游错误掩盖真正的失败）。这是被坑出来的，见各 lib 头注。
3. 打印用法用 heredoc，**NEVER** 用 `sed -n '/【用法】/,/【选项】/p' $0`——那条命令自身含起止标记，
   会二次触发范围把剩余代码全打出来。
4. 远端命令是拼字符串执行的，**NEVER 直接拼 `"$@"`**——用 `remote_quote_args`（printf %q）。
5. 跟踪类命令（`log tail -f`）**NEVER 去掉 `ssh -t`**——伪终端是刷新/Ctrl+C/颜色的前提。
6. 新增 API 模块按域建 `lib/cloudos-<域>.sh`，只封装本仓用到的端点，头注释写清前缀与易错点。
7. token 缓存按环境分文件（`/tmp/.cloudos-<env>.token`，格式 `<时间戳> <token>`，互读兼容）。

## 五、使用纪律

1. **路径推导**：脚本 MUST 由自身位置推导仓库根（`BASH_SOURCE`/`__file__`），NEVER 硬编码绝对路径。
   入口 `lib/core.sh` 用 `account-server` 目录存在性断言——深度再错会直接报错而不是静默找错目录。
2. **写操作默认拒绝**：`k8s deploy/deploy-all`、`cloudos act/scale/scale-all/spec-set/image build`
   必须显式 `--yes`（入口全局解析，位置任意）。
3. **ALL_SERVICES 是单一真源**（`domains/k8s.sh`）：24 个服务，语义是**可构建推送的模块全集**
   （根 pom 里有 kubernetes-maven-plugin 的模块目录），**不是生产部署清单**——两者 historically
   不相等（详见 `domains/k8s.sh` 处注释：3 个死/下线模块在构建清单里、web-admin/前端不在构建清单里、
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
5. **别用文件名猜用途**：`migration/k8s-master-home-java/` 里是**跑在远端 k8s-master 上**的脚本快照；
   `migration/ingress-config*/` 是 istio 抓取快照，与数据库无关。
6. **⚠️ 安全待办（原 legacy 存档已删，此条 MUST 保留）**：曾入库的 `_fix.ps1` / `_run_script.ps1`
   **含明文 root 口令**（目标 `172.20.211.21`），自提交 `93e608e` 起就在版本库、且已随 `origin/main`
   推送远端——**删文件只能防扩散，口令仍在 git 历史里**。若该口令至今有效，
   **MUST 找运维轮换**（这是处置的根本手段，删文件只是顺带）。
   另：`enable-tracing-20261014.sh` 已于 2026-10-14 执行完毕，后续接入链路跟踪 MUST 按
   `docs/architecture/common-components.md` §3.1.2「三行成组」口径做，NEVER 照抄那个已删脚本的 env 注入方式。
7. **分析脚本 MUST 走 `itp-ops.sh analysis`**：`count-endpoints.py` 按「从仓库根运行」设计，域入口负责 cd，
   从子目录直接 `python3` 调会静默找不到文件或把输出写错地方。
   （原 `extract_interfaces.py` / `extract_requirements.py` 于 2026-10-26 审计删除——
   依赖的 `API.md` 与 `docs/提取的需求` 从不存在，一个跑必崩、一个空转输出 0。）

## 六、重构与迁移记录

**2026-10-25 迁移**：根级 `scripts/`（98 文件）整体迁入 `docs/ops/environments/scripts/`；
全仓 `docs/**`、`AGENTS.md`、`deploy/**` 引用（184 处）同步改写；`docs/提示词/lint-prompts.py`
根级路径白名单校正（`scripts/` → `deploy/`）；运行时路径深度修复并实测。

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
  （**含明文 root 口令**——见纪律第 6 条的安全待办，口令已随 `93e608e` 入远端历史，**改密才是根本处置**）、
  `enable-tracing-20261014.sh`（已执行完）、`klog.cmd` / `klog.ps1`（已被 `itp-ops.sh log tail` 取代）、
  `cloudos-api.sh`（认证结论已证伪，内容已并入 `domains/cloudos.sh`）及该目录 README。
- **第二轮 `generate-prod-migration-report.py` + 产物 `prod-migration-config-checklist.md` 删**：全仓零引用，
  生成器唯一产出就是那份没人读的报告。⚠️ 连带影响：`k8s-current-config/` 失去唯一消费者，
  现仅作**集群实况快照**独立参考价值（内容无害——`secrets.txt` 只是 Secret 名称清单，不含值）。
- **保留且有理由**：`ingress-config/`（clean-ingress-yaml.py 输入，产出 diag routes 的 diff 基线）、
  `k8s-master-home-java/`（`log tail` 远端侧 `/home/java/k8s-logview.sh` 本体参考）、
  migration 执行包与 20261023 域拆分工具链（全部有外部引用，且分域重建仍在进行）。
- **原则**：保留 = 有独特内容且有人会用；删除 = 已被基线/文档/代码仓吸收的纯执行痕迹，或根本跑不起来。
  每个去留结论都记在被删/被迁文件的相关引用处，NEVER 静默删。

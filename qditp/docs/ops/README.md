# docs/ops — 部署与运维交付文档

面向**部署、配置、上线**的交付物。与其他目录的分工：

- `docs/business/` — 业务域编码提示词（写代码前读）
- `docs/architecture/` — 架构与模块技术设计（理解结构时读）
- `docs/ops/` — 部署与配置清单（**上线、配环境、排查环境问题时读**）
- `docs/reviews/` — 代码审查记录（按链路 + 日期归档）
- `docs/接口规范文档/`、`docs/业务需求文档/`、`docs/技术规范文档/` — 甲方提供的原始规格，只读，勿改

## 环境总览

| 环境 | 类型 | 状态 | 说明 |
|---|---|---|---|
| 本地开发 | dev | 在用 | 开发者本机 / 本地 Docker |
| 测试环境 | test | 在用 | 当前对接新华三（H3C）底座，ns `itp`（k8s-master） |
| 红岛 | 生产 | 待部署 | 生产站点之一 |
| 辽阳东 | 生产 | 待部署 | 生产站点之一，与红岛同为生产、部署方式相同 |

> 红岛与辽阳东均为**生产环境**，理论部署方式一致；二者当前均**待部署**（状态会变，本表 MUST 随实况更新，NEVER 当永久事实）。
> 服务部署清单 / 环境变量 / 网络开通 / 上线核对见 `[environments/跨环境共性.md](environments/跨环境共性.md)`；按环境（本地 / 测试 / 红岛 / 辽阳东）的事实与 NodePort 见 [`environments/环境总览.md`](environments/环境总览.md)。

## 文档清单

| 文档 | 用途 | 何时读 |
|---|---|---|
| [environments/环境总览.md](environments/环境总览.md) | **4 环境（本地开发 / 测试 / 红岛 / 辽阳东）的入口**，各环境特有事实（网段 / NodePort / 部署状态 / 底座 / USB 网卡分区）拆到 4 份子文档；跨环境共性 + 仓库现值 + 双跑红线仍归 `[environments/跨环境共性.md](environments/跨环境共性.md)`，本目录不重复 | 按环境排查 / 上线 / 配新环境时；环境总表见本文件 |
| [跨环境共性.md](environments/跨环境共性.md) | 服务部署清单、外部依赖（Oracle / HSM / FTP / 支付中心 / 支付宝）、环境变量、网络开通、部署顺序、上线核对清单 | 上线前、扩环境、排查连不通 |
| [environments/网络路由配置.md](environments/网络路由配置.md) | **本机（macOS）通过 USB 网卡 `AX88179B`（`en9`）接入各环境内网的配法** + **红岛网段与端点清单**：核心原理（只配网卡地址不够，MUST 补目标网段路由，否则跨区流量回落办公网默认路由被入口防火墙丢弃）、四环境参数表（红岛 `172.120.0.0/16` 实测可用；测试 / 辽阳东待现查）、四步操作 + 验证门、五个已踩坑（`-ifscope` 假成功 / `networksetup` 返回 0 但不下发 / ICMP 静默致误判 / 自签证书 / 路由不持久）；**§七 由 ACLC 红岛数据中心设备管理表（2026-10-15）整理：C 值分区规划、`1/2/3/4/110/125/209/211/249/250` 各段网关（统一 `.254`）与段内关键端点、2026-10-23 端点可达性实测、结论「三层全通 / 四层按分区 ACL」** | 插 USB 网卡接内网时、遇到「本网段通但其他区全不通」时、换环境切网段时、查红岛某 IP 属于哪个区 / 该不该通 |
| [DBeaver数据库操作手册.md](DBeaver数据库操作手册.md) | Windows PC 用 DBeaver 直连 qditp 库的完整方法（v2.0，2026-10-17 基于《红岛Oracle RAC 19c部署方案》重写）：**主场景红岛生产 HDTPDB**（RAC 19c CDB+PDB，VIP `172.120.110.25:1521/hditpdb`，服务名必填、NEVER 用 SID `itpcdb1/2`，字符集 ZHS16GBK，dba 用户 `qditp`），测试库 `AFCITPDB` 为对照；含驱动与 TNS/URL 备选、网络预检、常见错误（ORA-12514/12541/01017/28000/00942）处置、只读与变更安全红线、PDB 自证 SQL | 人工查库、排障、教新人连库；NEVER 当参数权威源（地址/口令以仓库为唯一真源，见 `docs/ops/environments/跨环境共性.md` §三；源文档换版 MUST 同步 §2.1） |
| [测试数据退款操作手册.md](测试数据退款操作手册.md) | 五条业务线的退款入口与必填参数、待退判定条件（逐线不同）、核验用退款记录表、无幂等等执行铁律 | 清退测试订单、排查退款失败 |
| [流量切换Runbook.md](流量切换Runbook.md) | 新旧应用互切与入向路由分发：`fep-app-vr` 两个开关、带 `test` op 的 patch 命令、三重验证、NEVER 清单、切换后新旧表落点差异 | 要把某业务域流量在两个服务间切换、或排查「请求落到了哪个服务」 |
| [web前端云平台发版流程.md](web前端云平台发版流程.md) | **web 前端（H3C 云平台 CloudOS）发新版本的两步 UI 流程**：①应用包仓库「添加版本」（本地上传 zip / 远端地址，自动化传不了本地文件）→ ②应用实例「升级」（软件包版本 + 目标应用版本两个字段都改 → 确定 →「业务不可用」提示再确定）；含核验（变更记录留痕 / 实例满员）、回滚（回滚点＝线上当前版本）、坑位（包仓库「部署」≠ 升级、双版本字段、el-select 可访问名跳变难定位、低峰执行） | 给 web 前端发新版本（综管台 `web/` 出 zip 后经云平台上线）、排查「云平台升级卡在哪个步骤」；**一键脚本 `python3 environments/scripts/web_frontend_release.py <版本> [--yes]`（build→上传→升级→核验）**；本地出 zip 判据另见 `docs/architecture/web-admin-development-workflow.md` §5 |
| [K8S迁移清单.md](K8S迁移清单.md) | **异网新集群迁移盘点**（2026-09-22 实测）：集群底座与 32 个负载现状、入向路由 8 条前缀落点、密钥与配置债、外部依赖逐条地址、四类地址替换矩阵、5 个待定问题、建议顺序与 NEVER 清单；对象 YAML 基线在 [`../../deploy/k8s/`](../../deploy/k8s/) | 规划集群迁移、评估「换一套环境要改多少配置」、排网络开通与对端改地址需求 |
| [tasks/00-总览.md](tasks/00-总览.md) | **定时任务唯一权威**（2026-10-20 由 `定时任务清单.csv` + `定时任务需求对照.md` 合并为目录）：菜单层结论摘要 + 需求映射 + 业务裁决；各模块实现细节、NEVER 约束、参数键、ADR 号见 `docs/ops/tasks/` 下对应模块文档 | 回答「这些定时任务做了没有」、排查「某个补偿到底有没有在跑」、新增定时任务前先确认有没有重复、评估定时任务类需求工作量 |
| [生产数据库容量规划.md](生产数据库容量规划.md) | **上线后 12 个月的容量预估 + 已按实测校准**：126 张表的分域口径、**§5.1 老 ITP 环境实测对标**（总 10,010 GB／已用 7,283 GB，其中 `RACDG` **89.60% 仅剩 624 GB**、两组偏差 **1,006 GB**）；**实测日增 < 3 GB ⇒ 原「60 万日行程」假设高估 7.7 倍，修正为约 7.8 万、年增 1.07~3.21 TB**；**原「申请 20 TB」结论已作废，改为维持现有 10 TB + 先做磁盘组均衡**；Oracle 全栈容量、P0~P2 风险清单、分层保留策略、**10 项待确认**、**附录 A 实测校准 SQL**。**未连库、纯 DDL 静态解析**，§4 的绝对值已标注「高估 7.7 倍、NEVER 引用」，以 §5.1 修正值为准 | 向 DBA 申请存储前先读 §5.1、设计归档保留策略、排查表空间增长；**三条与数据量无关的紧急项**：①老环境 `RACDG` 仅剩 624 GB（按实测也只够 208 天，均衡可到 543 天）；②2026-12 前必做「分区到期」整改（8 张手工 RANGE 分区只建到 `P202612`，2027-01-01 起新数据全落 `P_MAX`）；③全库零归档零清理 |
| [数据库按域分库计划.md](数据库按域分库计划.md) | **按域分库规划总纲**（2026-10-14 建，库内实测 162 张业务表 + 3 张 `BAK_RECON_*` 快照、15 张分区表 = 13 RANGE + 2 LIST）：按模块归 13 业务域，标出跨域共享表（`USER_ITP_REG_INFO` / `BLACKLIST*` / `TBL_*` / `SYS_*` / `QRTZ_*` / `APP_USER_PAY_CHANNEL`）与技术债（`STATION_INFO`(ticket) 与 `TBL_STATION_INFO`(para) 两处并存）；厘清**逻辑分库（同实例多 schema，不扩容）≠ 物理分库（多实例，才解容量）**——当前 `RACDG` 89.6% 满，逻辑分库不解容量；推荐路径 M0 修 P0（补 2027 分区 / 定归档 / `F2F_*` GLOBAL→LOCAL / 磁盘均衡）→ M1 建 `QDITP_PLAT` + 共享同义词 → M2 迁 USER+RIDE → M3 迁 TXN（容量热点，须双跑锁）→ M4 全量校验回滚；含 4 用户库映射、跨库共享策略、无第二环境迁移兜底（CTAS 快照 + 双跑锁）。**状态：草案 v2**——用户已定**方案 A 逻辑分库 + 4 个用户库**（`QDITP_USER`/`QDITP_TXN`/`QDITP_RIDE`/`QDITP_PLAT`），尚待甲方 DBA / 业务方校准（4 库分组认可度 / 独立表空间 / DB Link / `itp_ro` 只读契约 / 与「唯一目标库」裁决的关系），**NEVER 当作已定案执行**；**归属清单实测只覆盖 127/162 张，34 张漏统（ALIPAY_* 8 / TBL_BOM_* 8 + BOM_TOPUP_RESULT / TBL_TVM_* 9 / BLACKLIST 主表等）待补** | 规划拆库 / 分域 schema；承接容量规划 §5.1 的整改项 |
| [入口服务链路排查规范.md](入口服务链路排查规范.md) | **tracing / traceId / VictoriaLogs 接入与排查的权威入口**：入口服务清单、拿 traceId 的三档方法、全链路检索、无 traceId 的判读（哪些正常哪些要处理）、VictoriaLogs 查询范式、响应头回传 traceId 方案。原「Tracing 全量接入评估」（按模块分三档接入、`para-server`/`blacklist-server` 配置缺口等结论）已并入本规范 + `docs/architecture/runtime-observability-traps.md`，原独立评估文件已删除 | 决定某模块开不开 tracing、排查 traceId 断链、治理日志上报量与存储 |
| [定位排查手册.md](定位排查手册.md) | **「现象有了、但代码看着没错」时的排查顺序**：§0 三步定性、§1 六类高频症状（兜底 catch 失效 / 加了字段收不到 / Druid WallFilter 三陷阱 / 返回成功实际没成 / 虚拟线程 pin / 时间窗类判定整体失效——容器时区 ≠ 业务时区）、§2 部署后五个不可靠信号与可靠替代、§3 两条「不是 bug」的误报 | 定位不到原因时；**尤其当缺陷「编译通过、单测全绿、只在特定运行时分支上炸」时**。本手册**只做索引与排查顺序，规则条文以 `AGENTS.md` §5 / §7 为准**，冲突时以 AGENTS.md 为准并回来修 |
| [构建与发布.md](构建与发布.md) | **「构建应用、发布应用」的单一入口**：三条发布路径总览（测试 k8s / 红岛 CloudOS / 前端 zip）+ 本机构建（mise / 构建顺序 / install model rpc / 出 jar 双命令）+ 测试环境六步（`k8s deploy` 行为边界、滚更、探活、digest 比对、digest 强制回滚）+ 红岛生产（JDK 兼容、buildx 现状与脚本化缺口、`advanced-config` 上线、env 三纪律、副本纪律）+ 前端一条命令 + 通用完成判据与真源分工表 | 发版前、构建失败、排查「推没推上去」「跑的到底是哪版」。`AGENTS.md` §6 / §5.C 留高频红线，完整方法以本文为准 |
| [推荐部署顺序.md](environments/推荐部署顺序.md) | **按业务依赖关系推导的六波部署顺序**（依据 `service.*.url` 实测调用关系 + `@EnableRpcXxx` 注入点 + 外部依赖）：W0 前置（网络策略/Secret/时区/VictoriaLogs）→ W1 安全与公共能力（`security-server`→`para`→`key`→`card-pool`→`blacklist`→`industry-data`）→ W2 业务核心（`account`→`ticket`→`pay-sign`→`gate-txn-pay`→`daily-ticket`→`alipay-account`→`alipay-pay-sign`→`face-pay`→`trans-query`）→ W3 接入层（`fep-app`→`fep-alipay`→`fep-dev`→`fep-acc`→`es-server`）→ W4 `recon-server` → W5 `web-admin`→`web`；含互依赖环处理、单副本约束、每批验证门；**不承载版本/端口快照，数字 MUST 现查** | 全新集群全量部署、整批滚动更新、异网迁移排期；单模块日常发版不需要本文 |
| 模块配置清单（本目录尚未建立；单模块配置视图见 qditp-wiki 的 reference/config.md，源仓内无此文件，lint 断链属预期；跨模块配置键全量聚合 + 敏感值原样） | **23 个模块的 `application.properties` + Deployment env 聚合**（每个模块一份 `.md`，配 `部署事实` 摘要 + 配置键表 + secretKeyRef 标记 + env 覆盖标注；敏感值 NEVER 写真值，全部 `<secret:...>` 占位）。与 qditp-wiki reference/config.md（配置项字典，跨模块配置键全量聚合 + 敏感值原样）对照：本目录是「单模块视图」、config.md 是「跨模块全量聚合视图 | 想快速查「某个模块所有配置键是什么、哪些走 Secret、哪些被 env 覆盖」时用；NEVER 当生产取值依据（敏感值已脱敏，实际值 MUST 查集群 Secret + 线上 Deployment env） |

> 生产 Oracle 地址与 `DB_*` 环境变量见本目录 `[environments/跨环境共性.md](environments/跨环境共性.md)` §二、§四；`other.sql.*` 数据源装配与「哪些模块无库」见 [`../architecture/common-components.md`](../architecture/common-components.md)。

## AI 使用约束

- 涉及**部署配置、环境变量、外部系统地址、密钥**的改动，**MUST** 先读上表对应文档。
- 修改 `application.properties|yml` 中的地址或凭据 **MUST** 先与用户确认目标环境，**NEVER** 擅自改动线上路由。
- 新增敏感配置项 **MUST** 写成 `${ENV_VAR:}`（空默认值），**NEVER** 写默认真值。
- **NEVER** 在对话、日志、提交信息或文档中回显密钥值，引用时只写键名与位置。
- 发现新的环境类问题（错配地址、测试残留、本地路径、单副本约束）**MUST** 追加到 `[environments/跨环境共性.md](environments/跨环境共性.md)` 的问题章节，而不是只在对话里说。

## 待办与未闭合问题

**本文件不承载待办清单。** 现行问题跟踪索引在 [`../tracking/问题清单.md`](../tracking/问题清单.md)（含重档 / 轻档两档登记粒度，判据见 `AGENTS.md` §5.0「问题跟踪」）；**上线前必须处理的问题**在 [`environments/跨环境共性.md`](environments/跨环境共性.md) §六。

**NEVER 在本文件另存一份待办快照**——2026-08-25 ~ 2026-10-20 期间此处曾存过一份，其条目已全部由上述两处承接（`APP_TERMINATION_REQUEST` 复核结论见 `[跨环境共性.md](environments/跨环境共性.md)` §六「本条作废」；IF8A-35 / IF8A-42 / IF8A-75 的实现与遗留见 [`../business/account-employee-card.md`](../business/account-employee-card.md)；IF8A-36「暂不实现，仅登记」的裁决见 [`../business/未落地需求.md`](../business/未落地需求.md)）。再存一份只会制造双份真源。
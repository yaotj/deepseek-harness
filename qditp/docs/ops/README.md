# docs/ops — 部署与运维交付文档

面向**部署、配置、上线**的交付物。与其他目录的分工：

- `docs/business/` — 业务域编码提示词（写代码前读）
- `docs/architecture/` — 架构与模块技术设计（理解结构时读）
- `docs/ops/` — 部署与配置清单（**上线、配环境、排查环境问题时读**）
- `docs/reviews/` — 代码审查记录（按链路 + 日期归档）
- `docs/接口规范文档/`、`docs/业务需求文档/`、`docs/技术规范文档/` — 甲方提供的原始规格，只读，勿改

## 文档清单

| 文档 | 用途 | 何时读 |
|---|---|---|
| [生产环境清单.md](生产环境清单.md) | 服务部署清单、外部依赖（Oracle / HSM / FTP / 支付中心 / 支付宝）、环境变量、网络开通、部署顺序、上线核对清单 | 上线前、扩环境、排查连不通 |
| [测试数据退款操作手册.md](测试数据退款操作手册.md) | 五条业务线的退款入口与必填参数、待退判定条件（逐线不同）、核验用退款记录表、无幂等等执行铁律 | 清退测试订单、排查退款失败 |
| [流量切换.md](流量切换.md) | 新旧应用互切与入向路由分发：`fep-app-vr` 两个开关、带 `test` op 的 patch 命令、三重验证、NEVER 清单、切换后新旧表落点差异 | 要把某业务域流量在两个服务间切换、或排查「请求落到了哪个服务」 |
| [K8S迁移清单.md](K8S迁移清单.md) | **异网新集群迁移盘点**（2026-09-22 实测）：集群底座与 32 个负载现状、入向路由 8 条前缀落点、密钥与配置债、外部依赖逐条地址、四类地址替换矩阵、5 个待定问题、建议顺序与 NEVER 清单；对象 YAML 基线在 [`../../deploy/k8s/`](../../deploy/k8s/) | 规划集群迁移、评估「换一套环境要改多少配置」、排网络开通与对端改地址需求 |
| [定时任务清单.csv](定时任务清单.csv) | **代码侧定时任务总账**（按实测维护）：19 条 `sys_job` + 15 处真实 `@Scheduled`，含触发目标、cron、并发与单副本约束 | 排查「某个补偿到底有没有在跑」、新增定时任务前先确认有没有重复 |
| [定时任务需求对照.md](定时任务需求对照.md) | 甲方 19 项定时任务需求 ↔ 代码现状逐条对照（完全落地 4 / 部分落地 5 / 零实现 7 / 已关闭 3），附 7 处「同名但语义不同」的易混点与 5 点待业务确认 | 回答甲方「这些定时任务做了没有」、评估定时任务类需求工作量 |
| [数据库容量规划.md](数据库容量规划.md) | **上线后 12 个月的容量预估 + 已按实测校准**：126 张表的分域口径、**§5.1 老 ITP 环境实测对标**（总 10,010 GB／已用 7,283 GB，其中 `RACDG` **89.60% 仅剩 624 GB**、两组偏差 **1,006 GB**）；**实测日增 < 3 GB ⇒ 原「60 万日行程」假设高估 7.7 倍，修正为约 7.8 万、年增 1.07~3.21 TB**；**原「申请 20 TB」结论已作废，改为维持现有 10 TB + 先做磁盘组均衡**；Oracle 全栈容量、P0~P2 风险清单、分层保留策略、**10 项待确认**、**附录 A 实测校准 SQL**。**未连库、纯 DDL 静态解析**，§4 的绝对值已标注「高估 7.7 倍、NEVER 引用」，以 §5.1 修正值为准 | 向 DBA 申请存储前先读 §5.1、设计归档保留策略、排查表空间增长；**三条与数据量无关的紧急项**：①老环境 `RACDG` 仅剩 624 GB（按实测也只够 208 天，均衡可到 543 天）；②2026-12 前必做「分区到期」整改（7 张手工 RANGE 分区只建到 `P202612`，2027-01-01 起新数据全落 `P_MAX`）；③全库零归档零清理 |
| [排障手册.md](排障手册.md) | **「现象有了、但代码看着没错」时的排查顺序**：§0 三步定性、§1 六类高频症状（兜底 catch 失效 / 加了字段收不到 / Druid WallFilter 三陷阱 / 返回成功实际没成 / 虚拟线程 pin / 时间窗类判定整体失效——容器时区 ≠ 业务时区）、§2 部署后五个不可靠信号与可靠替代、§3 两条「不是 bug」的误报 | 定位不到原因时；**尤其当缺陷「编译通过、单测全绿、只在特定运行时分支上炸」时**。本手册**只做索引与排查顺序，规则条文以 `AGENTS.md` §5 / §7 为准**，冲突时以 AGENTS.md 为准并回来修 |

> 生产 Oracle 地址与 `DB_*` 环境变量见本目录 `生产环境清单.md` §二、§四；`other.sql.*` 数据源装配与「哪些模块无库」见 [`../architecture/common-components.md`](../architecture/common-components.md)。

## AI 使用约束

- 涉及**部署配置、环境变量、外部系统地址、密钥**的改动，**MUST** 先读上表对应文档。
- 修改 `application.properties|yml` 中的地址或凭据 **MUST** 先与用户确认目标环境，**NEVER** 擅自改动线上路由。
- 新增敏感配置项 **MUST** 写成 `${ENV_VAR:}`（空默认值），**NEVER** 写默认真值。
- **NEVER** 在对话、日志、提交信息或文档中回显密钥值，引用时只写键名与位置。
- 发现新的环境类问题（错配地址、测试残留、本地路径、单副本约束）**MUST** 追加到 `生产环境清单.md` 的问题章节，而不是只在对话里说。

## 当前待办（截至 2026-08-25）

### 🔴 2026-08-26 首件事：复核 `APP_TERMINATION_REQUEST` 是否存在

2026-08-25 枚举 `all_tables WHERE owner='QDITP'` 得到 110 张表，其中**没有 `APP_TERMINATION_REQUEST`**（只有 `ALIPAY_TERMINATION_REQUEST`；同脚本的 `APP_PAY_SIGN_INFO` / `APP_PAY_SIGN_LOG` / `APP_PAY_SIGN_REQUEST` / `APP_USER_PAY_CHANNEL` 都在）。若确认缺失，**整条解约链路（IF8A-06 申请 → `/internal/termination/execute` → 支付平台回调）一调用就 `ORA-00942`，功能整体不可用**。

⚠️ 该结论**未二次确认**：复核时 database MCP（`172.20.211.23:30031`）已连不上，`createNamedConnection` 也失败。

执行步骤：

1. 重建 MCP 命名连接（连接有租约 `leaseTtl=1h` / `maxLifetime=2h`，报「连接不存在」直接重建，**不要**去排查网络或数据库）
2. `SELECT table_name FROM all_tables WHERE owner='QDITP' AND table_name='APP_TERMINATION_REQUEST'`
3. 缺失则按 `pay-sign-server/src/main/resources/sql/pay-sign-schema.sql:148` 建表，含 `UK_ATR_REQUEST_SIGN_SEQ`、`IDX_ATR_USER_STATUS`、`IDX_ATR_NOTIFY_STATUS` 三个索引
4. 建表后调一次 IF8A-06 验证落库，并确认 `pay-sign` Pod 日志无 `ORA-00942`
5. MCP 仍不可用时的退路：`ssh k8s-master` 在 pay-sign 容器内用现有数据源验证

顺带核对上述 4 张已存在的表的索引与字段长度——2026-08-25 只核了表名，未逐列比对。

详见 `生产环境清单.md` §六 P0。

### 🟠 IF8A-36 请求移除签约信息（`requestAgreeRelease`）— 暂不实现，仅登记（用户 2026-09-08 决定）

**业务口径（用户 2026-09-08 口述，唯一来源，实施前仍需甲方文档确认字段）**：移除签约与解约是两件事。解约 / 直接解约要 ITP 请求支付系统；**移除签约时 ITP 不请求支付系统**，直接把签约记录状态改成解约成功即可。触发场景是「用户协议在第三方那边已失效」或「钱包解绑」——协议在对端已经没了，再发解约请求没有意义。

**当前状态：pay-sign 侧代码已存在但链路未接通，暂不继续实现。** fep-app 无对应透传入口，APP 打 `/app/requestAgreeRelease` 落到的是 account 侧「删支付通道」那条链路（语义不同）。下面的待办保留备查，**恢复实现前先重新确认第 1 条裁决**。

pay-sign-server 侧已落地（`ContractDomainServiceImpl.removeSignAgreement`、`PaySignService` / `PaySignServiceImpl`、`PaySignAppController` 的 `POST /ci/app/requestAgreeRelease`，`model` 新增 `RequestAgreeReleaseReqDTO` / `RequestAgreeReleaseResult`），语义是**不请求支付平台**，按 `agreementCode`（= `APP_PAY_SIGN_INFO.REQUEST_SIGN_SEQ`）直接把 `SIGN_STATUS` 置 `UNSIGNED` 并写 `TERMINATION_TIME`。

**⚠️ 先裁决再动手：同名接口已有另一条链路。** `requestAgreeRelease` 在 account 链路上已实现——`fep-app-server/AppAccountController` → `rpc/AccountClient.requestAgreeRelease` → `AccountApplicationServiceImpl:519-524`，注释写「钱包协议 requestAgreeRelease：解绑语义与本地删除支付通道一致」，实现是直接委托 `requestRemovePayChannel`，用的 DTO 是 `RequestRemovePayChannelReqDTO`。两条链路语义不同（一个删支付通道、一个改签约状态），DTO 不同，**若再在 fep-app 加一个 `/app/requestAgreeRelease` 路由会撞车**。

待办：

1. 与甲方/用户确认 IF8A-36 的落点究竟是 account 侧的「解绑支付通道」还是 pay-sign 侧的「签约置解约成功」，**NEVER** 两处并存
2. 裁决后再决定是否补 fep-app 透传层（`PaySignAppService` / `PaySignAppServiceImpl` / `PaySignController` 路由 + `rpc/PaySignClient` 方法）；新增内部接口的 DTO 已在 `model`，符合 §5.2
3. **鉴权缺口**：`/ci/app/requestAgreeRelease` 是状态变更型端点，当前无鉴权与归属校验，任何网络可达方凭 `agreementCode` 即可改他人签约状态（AGENTS.md §5.2 明令要求），上线前必须补
4. **字段一致性**：本次只写了 `SIGN_STATUS` + `TERMINATION_TIME`，未写 `TERMINATION_STATUS`。解约链路是双状态列设计（`pay-sign-schema.sql:180` / `:185`），需确认移除签约是否也要同步该列，否则解约扫表逻辑可能读到不一致状态
5. 补 `docs/business/pay-sign.md` 接口清单与「移除签约 vs 解约」差异说明；**NEVER** 新建「未实现服务开发计划.md」这类汇总文档（§5.3 已禁止，仓库内也不存在该文件）

### ✅ IF8A-35 / IF8A-42 / IF8A-75 已实现（2026-09-08）

- IF8A-35 `/app/requestUserAccInfo` — 查用户账务信息（`gate-txn-pay-server`，返回 `unpaidCount` / `failureCount`）
- IF8A-42 `/app/cancelAccount` — 销户（**2026-09-09 按规范表 91 收敛**：原落地路径 `/app/userCancel` 及 `/ci/app` 前缀已删除，只保留这一条；account-server 内部路径仍是 `/userCancel`，属实现细节）。落点 `account-server`：`AccountApplicationServiceImpl.userCancel` 置 `DEL_YN=0` + 写 `USER_ITP_REG_LOG`（`OPER_TYPE=2`），登记前先调一次 IF8A-35 校验欠费（`app.user-cancel.check-unsettled`，fail-closed → `8024`；有欠费 → `8023`）。**不删支付通道**——APP 顺序是 35→42→75，通道由 75 清理。`bizData` 接 `thirdUserId` + `phone`，但**注销口径只用 `thirdUserId`**：`USER_ITP_REG_INFO` 无手机号列，`phone` 仅打掩码日志
- IF8A-75 `/app/unbindAgreement` — 直接解绑支付方式。落点 **`pay-sign-server`**：`TerminationInternalServiceImpl.unbindAgreement` 委托既有 `executeTermination`（`/internal/termination/execute` 的同一套状态机 + CAS，立即调支付中心），APP 入口 `PaySignAppController` `/ci/app/unbindAgreement`，链路 `fep-app-server PaySignController` → `rpc/PaySignClient.unbindAgreement`
  - **APP 实际在调 `/userData/unbindAgreement`**（2026-09-09 实测）。该路径原先未注册，被全局异常处理器兜成 `retCode=<UUID>` + HTTP 200，**不是 404**，APP 无法识别失败 → 用户 `00522948` 已注销但支付宝签约仍 `SIGNED`、通道残留 `ACTIVE`、`APP_TERMINATION_REQUEST` 零条。已在 `fep-app-server/PaySignController.java:97` 加为兼容别名（与 `/ci/app/` `/app/` 两条并存），规范正式路径待甲方确认后再收敛到一条
  - 2026-09-09 首次端到端跑通：`/userData/unbindAgreement` → 支付中心解约 → 回调 `receiveTerminationResult` → 清通道 + 删签约 + 销户归档，一次事务内完成
  - **NEVER 把 75 接到 `requestTermination`**（本文件此前如此记载，已更正）：`requestTermination`（IF8A-06）只登记申请等账期结束的扫表任务，与 75「立即请求支付渠道」正相反
  - 钱包渠道 `0B` 在 `unbindAgreement` 里短路为 `8001`：钱包开户后直接绑通道、不生成签约流水，没有可解约的协议
  - **不校验未结清欠费**（用户 2026-09-08 裁决）：75 语义是强制解绑，欠费由后续催收流程处理
  - 应答 `0000` 只表示「已向支付渠道发起」，不等于已解绑完成；收口以 `processTermination` 主动查支付中心 §2.4 `queryResult` 为准

配套修正：`account-server.requestRemovePayChannel` 增加「忽略 `DEL_YN` 再查一次」的兜底（`UserItpRegInfoMapper.selectAnyByThirdUserIdAndCardIdAndCardType`）。否则 42 已置 `DEL_YN=0` 后，75 解约成功分支回调清理支付通道时会返回 `8004`，留下「渠道已解约、本地 `APP_USER_PAY_CHANNEL` 残留」且无法自愈。

仍未闭合：

1. **`DEL_YN` 极性别搞反：`1 = 有效`，`0 = 已注销`**。`UserItpRegInfoMapper.xml` 多数 select/update 带 `and DEL_YN = 1`，新增查询默认也要带；仅销户后的清理链路例外（见上）
2. **鉴权缺口**：`/userCancel` 与 `/app/unbindAgreement` 均为裸 POST，无验签与归属校验（AGENTS.md §5.2 明令要求），上线前必须补，单独立项
3. **`service.paySign.url` 在 account-server（`application.properties:29`）是 `http://127.0.0.1:9096`**，K8s 内指向自身 Pod；判断线上是否可达 **MUST 查 Deployment env**
4. IF8A-42 的 `8024`（账务查询失败）分支尚未实测，需临时改坏 `service.gateTxnPay.url` 才能触发
5. **销户归档已实现（2026-09-08），2026-09-09 补第二个触发点**：口径不是「42 时就删」，而是**通道全部解绑 + 记录全为注销态时才归档**。落点两处：①`account-server.requestRemovePayChannel` → `AccountArchiveServiceImpl.archiveIfLastChannelRemoved`（**旧名 `archiveUserInfoIfLastChannelRemoved`，随账户域拆分改名**，2026-09-23 核对；正常顺序 35→42→75）；②`userCancel` 的两条出口经 `tryArchiveAfterCancel` 各调一次（应对**反序场景**：通道先解绑完、之后才销户，此时 75 那条路径永不再触发。实测 `00522946` 通道 10:07/10:45 删完、14:24 销户，靠该补丁在 14:50:33 重调 IF8A-42 收口）。触发条件三个同时满足：①`APP_USER_PAY_CHANNEL` 中该用户已无任何通道（`countByThirdUserId` 为 0，thirdUserId 全量口径）；②`USER_ITP_REG_INFO` 中该用户还有记录；③这些记录**全部** `DEL_YN = 0`（IF8A-42 已执行）。动作是逐行往 `USER_ITP_REG_LOG` 插 `OPER_TYPE=3` 快照，再 `deleteCanceledByThirdUserId`（WHERE 带 `DEL_YN = 0`，不误删并发新开户）。归档幂等，`tryArchiveAfterCancel` 吞异常只记 warn，**NEVER** 让归档失败把已成功的销户翻成失败
   - **排查残留的口径**：`select count(*) from USER_ITP_REG_INFO where DEL_YN = 0` 应为 0；不为 0 时对这些 `THIRD_USER_ID` 重调一次 IF8A-42 即可
   - **「用户信息历史表」就是 `USER_ITP_REG_LOG`**（用户裁决复用，不新建表、不 ALTER）。该表只有 `CARD_ID` / `CARD_TYPE` / `THIRD_USER_ID` / `MSISDN` / `OPER_DATE_TIME` / `OPER_TYPE` 六个业务列，原表 19 列中的 `ITP_CARD_TYPE` / `USER_NAME` / `USER_ID` / `CARD_ISSUE_CODE` / `REG_TMS` / `THIRD_PAY_ID` / `HCE_DATA` / `COMPANION_FLAG` 等 **13 列归档后不可恢复**，这是已知且被接受的信息损失
   - 落在 75 而不是 42，正是为了绕开原先记录的两个死结：42 时通道还没解绑，那时删记录会让 75 回调清通道返 8004，且 `selectAnyByThirdUserIdAndCardIdAndCardType` 兜底同时失效。放到「最后一个渠道解绑完」，前置依赖已消费完毕
   - 失败即整体回滚（用户裁决）：`requestRemovePayChannel` 的 `catch` 原本吞异常导致 `rollbackFor` 不生效，已加 `markRollbackOnly()` 显式标记，避免「通道删了、归档失败」被提交
   - **`for update` 的取锁顺序不可调换（2026-09-08 review 修复）**：`AccountArchiveServiceImpl.archiveIfLastChannelRemoved`（**NEVER 按旧名 `archiveUserInfoIfLastChannelRemoved` 去搜**——已随账户域拆分改名，2026-09-23 核对）必须**先** `selectAnyListByThirdUserIdForUpdate` 锁住该用户的开户记录、**再** `countByThirdUserId`。同一用户多渠道并发解绑时，后到的事务会阻塞在取锁这步，等前一个提交后再 count 才能读到「全部渠道已删除」。**NEVER 先 count 后取锁** —— 两个事务会各自看到对方未提交的渠道仍存在、双方都跳过归档，用户信息永久残留且没有补偿路径
   - **NEVER 在归档后再指望「忽略 `DEL_YN` 再查一次」的兜底** —— 原表行已不存在，残留通道再也清不掉。因此归档的前置条件①不可放宽
   - **仍未闭合**：归档只挂在 `requestRemovePayChannel`。若 75 先于 42 执行（通道已清空但 `DEL_YN` 还是 1，不满足归档条件），之后 42 才跑，此时已无通道可删、`requestRemovePayChannel` 不会再被调用，**归档永久不触发**。建议在 `userCancel` 末尾也判一次「该用户是否已无支付通道」，覆盖任意顺序
6. **IF8A-75 的 `CARD_ID` 缺口已修（2026-09-08）**：`APP_PAY_SIGN_INFO.CARD_ID` / `CARD_TYPE` **全库为 NULL**（签约链路从不写这两列），而 `APP_TERMINATION_REQUEST` 同名列是 NOT NULL，导致 `createTerminationRequest` 补建申请必然 ORA-01400、75 返回 8007。修法：account-server 新增只读接口 `/queryPayChannelByContractNo`（`APP_USER_PAY_CHANNEL.REQ_CONTRACT_NO` 即 `requestSignSeq`），`unbindAgreement` 先取 `cardId` / `cardType` 再进 `executeTermination`。**NEVER 再假定「缺字段可从签约记录回填」**——`createTerminationRequest` 的方法注释曾如此声称，与数据不符

### 其他

`生产环境清单.md` §一 记录了 2026-08-25 实测的**生产集群** NodePort 映射（`kubernetes.b` @ `172.20.211.23`，用户已确认为生产地址，22 个 ITP 服务 + 7 个无关 Pod，均 1 副本），并逐项比对出 **4 处生产在跑的地址错配**：`service.fepDev.url` 指向自身、`service.alipayAccount.url` 指向 key-server、`service.gateTxnPay.url` 用容器端口、`service.dailyTicket.url` 仍是 `127.0.0.1`；另有 `fep-alipay-server`(30023) 为已弃用的旧部署仍在运行（现只用 `fep-alipay` 30020），需停止并删除。这些是**线上正在发生的故障**，修正等同变更线上路由，**MUST** 先与用户确认。

另两条 P0（2026-08-25 新增）：`/app/ticket/realName` 线上持续 404，APP 端已发布、服务端无实现（500 行内 93 次，仍在发生）；`acc-es-server` 13 张表已按 mapper 反推建表，**字段长度待甲方原始 DDL 复核**，有业务数据写入前必须比对完。

§六 记录了 P0 与 P1，均**未修复**：测试地址残留（含 `gate-txn-pay-server` 的 `wallet.app-gateway-url`）、`ticket-server` 配置行损坏、无效占位地址、商户归属切换日临近；P1 侧新增两类，**`service.alipay-pay-sign.url` 在 `fep-alipay-server` / `blacklist-server` 指向 9096（pay-sign-server）**，以及**三处依赖存在但 URL 键完全缺失**（`ticket-server` 缺 paySign、`fep-app-server` 缺 collectPay、`pay-sign-server` 缺 gateTxnPay，落到集群内无法解析的默认服务名）。上线核对 **MUST** 把「键缺失」当成独立一类检查。

`database`(30031) 已确认是 **BYOK database MCP Server**，不是 Oracle 实例，与业务数据源无关。

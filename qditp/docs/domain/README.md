# 领域划分（qd-itp）

本目录记录**领域边界与聚合归属**，是 `docs/business/`（按业务域写的编码提示词）之上的一层抽象。
两者分工：`docs/business/` 回答「这个业务怎么实现的」，本目录回答「这个东西归谁管、边界在哪」。

调整表归属、拆库、评审外部架构方案、判断跨服务调用是否合理时 **MUST** 先读本目录。

## 本目录文件

- `README.md`（本文）— 六个领域、聚合归属、四条边界自查判据
- `state-machines.md` — 四台在跑的状态机、三件套规范、为什么不引状态机框架
- `outbox.md` — 「落库状态 + 扫表补偿」模板：四列形状、扫表 SQL 的四个坑、Java 扫描骨架的三条不变量、三分支处置（**NEVER 抽公共 outbox 表**）
- `decisions.md` — 领域相关 ADR，含**已撤回的判断**（撤回记录 NEVER 删，防止重新踩）

## 一、六个领域

数据库当前是单库单 Schema（开发阶段），后续会拆。域划分同时是拆库的 Schema 边界。

| 领域 | 目标库 | 聚合与主要表 | owner 服务 |
|---|---|---|---|
| **账户 Account** | `itp_account` | ItpUser(`USER_ITP_REG_INFO`)、PayChannel(`APP_USER_PAY_CHANNEL`)、EmployeeCard(`USER_ACC_EMPLOYEE_CARD*`)、CardPool(`LOGIC_CARD_POOL_*`) | account-server、alipay-account-server、card-pool-server |
| **销售 Sales** | `itp_sale` | SalesOrder(`F2F_ORDER` / `TBL_TVM_*` / `TBL_BOM_*` / `TICKET_COLLECT_*`)、DailyTicketOrder、TravelTicketOrder、RefundRequest | collect-pay-server、face-pay-server、collect-ticket-server、daily-ticket-server |
| **行程 Journey** | `itp_journey` | RideCode(`QRCODE_STATUS`)、GateTxn(`QRCODE_TXN_DETAIL`)、GateTxnPay(`GATE_TXN_PAY` + `SUPPLEMENT_ORDER*`)、TicketInstance(`F2F_TICKET` / `DAILY_TICKET_INSTANCE`)、`STATION_INFO` | ticket-server、gate-txn-pay-server |
| **支付 Payment** | `itp_pay` | PayTxn(`PAY_TXN_DETAIL`)、PayRefund(`PAY_REFUND_DETAIL`)、PayCallback(`PAY_CALLBACK_LOG`)、ChannelContract(`APP_PAY_SIGN_INFO` + `APP_TERMINATION_REQUEST`)、`ALIPAY_PAY_*` | pay-sign-server、alipay-pay-sign-server |
| **参数 Reference** | `itp_common` | 25 张 `TBL_PARA_*` / 票价 / 风控、`METRO_*` 密钥、`BLACKLIST*` | para-server、key-server、blacklist-server |
| **结算 Settlement** | `itp_common` | ReconBatch(`RECON_*`)、ACC 文件、`TBL_STL_*`、`TBL_TKT_ES_*` 制卡 | recon-server、acc-es-server |

`web-server` / `web-admin` 只读写 `SYS_*` / `GEN_*`，**零业务表**，不属于任何业务域。

### 已确认的关键边界

- **`GATE_TXN_PAY` 与 `PAY_TXN_DETAIL` NEVER 合并。** 两表按 `ORDER_NO` 1:1，是刻意的「地铁侧账单 / 支付侧执行」纵切（`gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql:3` 注释；该文件 2026-09-14 从 `fep-dev-server` 迁入 owner 模块，同批把 `pay-txn-schema.sql` 迁到 `pay-sign-server`。**两份都是全仓唯一的 DDL，NEVER 当成重复副本删除**）。**这条纵切线就是行程域与支付域的边界。**
- **签约拆成两个概念**：`APP_USER_PAY_CHANNEL`（「用户授权了哪些支付方式、默认用哪个」= 账户能力）属账户域；`APP_PAY_SIGN_INFO` + `APP_TERMINATION_REQUEST`（「与某渠道的协议实例」，协议号由渠道下发）属支付域。`APP_USER_PAY_CHANNEL.REQ_CONTRACT_NO` 是账户域对支付域聚合的**引用（只存 ID）**，这是跨域引用的正确做法，**不要改成存副本**。
- **黑名单不是账户状态**，是与账户状态正交的标志，owner 在 blacklist-server。查询用户全貌 = 账户状态 × 黑名单标志 × 各通道签约状态。
- **票实例横跨三域**：售出信息（销售）、使用状态（行程）、ACC 上报（结算）同在一行（如 `DAILY_TICKET_INSTANCE`）。owner 定为**行程域**（过闸是热路径写），销售域与结算域只读。`ACC_NOTICE_STATUS` / `ACC_NOTICE_TIME` 是结算域写行程域表的一处例外，建 `RECON_*` 时应挪走。
- **卡号池属账户域**：`cardPoolClient` 的消费方只有 account-server / alipay-account-server / web-admin，无票务服务。
- **支付宝渠道是账户域内的独立落库链路**：`alipay-account-server` 与 `account-server` 同属账户域，但**不共享写入方**，也**没有 RPC 相互调用**（该模块无 `@EnableRpcAccount`）。两侧各自独占自己的表：ITP 用户 → `USER_ITP_REG_INFO` / `USER_PHONE_CHANGE_LOG`，支付宝用户 → `ALIPAY_USER_INFO` / `ALIPAY_PHONE_CHANGE_LOG`。**NEVER 让一侧写另一侧的表，也 NEVER 为「统一 owner」把两条链路合表** —— 曾据此设计过收口方案并被推翻，见 `decisions.md` 撤回 6。「同域」只意味着概念归属相同，不意味着必须共用表或共用序列（共用序列在拆库后必然主键冲突）。

## 二、四条边界自查判据

调整边界或评审方案时逐条过，比看上面的表可靠。

### 判据 1：双向 RPC = 域边界画错

两个服务互相调对方 ⇒ 它们大概率在同一个域里，或有一个域被切成两半。

查法：`grep -rn "xxxClient\." <module>/src --include="*.java"` 双向各查一次。

**只读方向的双向引用是正常的**（跨域读别人的聚合），**双向写才是病根**。

已知的一处：pay-sign-server 与 account-server 互调 5 个方法，其中 2 个是写。见 `decisions.md` ADR-D8。

### 判据 2：1:N 的子实体 NEVER 是父实体状态机的一个状态

- 一个订单 N 笔退款 ⇒ 退款状态不能进订单状态机
- 一个账户 N 个通道 ⇒ 签约/解约状态不能进账户状态机

外部提供的架构方案已在此处踩空两次（2026-09-11，两份 Spring Statemachine 方案分别把「行程+扣款+退款」压成 11 态、把「账户+通道签约」压成 8 态）。**评审外部方案第一条就查这个。**

### 判据 3：热路径写入决定 owner

一个实体被多个域读写时，owner 归**高频写**的那个域，其余域只读。

例：票实例的使用状态每次过闸都写 ⇒ owner 是行程域，不是销售域。

### 判据 4：域内应能本地事务强一致

如果一条业务链路**在同一个域内**却需要跨服务补偿，说明这个域被服务边界切断了。

逐条套现有链路：

| 链路 | 跨域？ | 一致性 | 判定 |
|---|---|---|---|
| 开户（账户 + 卡号池 + 行程注册） | 跨域 | 最终一致（代码本就不带事务） | 符合 |
| 过闸扣费（行程 → 支付） | 跨域 | 最终一致 | 符合 |
| 售票（销售 → 支付） | 跨域 | 最终一致 | 符合 |
| **解约（通道 + 协议）** | **域内** | 跨服务跨库 | **违反，见 ADR-D8** |

## 三、跨域协作规则

1. **跨域只读**：直接 RPC 查对方聚合，允许。
2. **跨域写**：**NEVER** 直接改对方的表或调对方的命令式接口后不管结果。MUST 落「同步状态列 + `@Scheduled` 补偿重推 + 达重试上限转异常工单」。
3. **跨域一致性**：不引 MQ / Seata。用**本地消息表 + 扫表补偿**，参照 `face-pay-server` 的 `F2F_NOTIFY_TASK`（已在跑）与 `APP_TERMINATION_REQUEST` 的 `NOTIFY_*`（已在跑）。
4. **派生规则不是状态迁移**：跨聚合的联动由 owner 服务在收到事实后自行判定。例：「最后一个通道解绑 ⇒ 销户归档」由 account-server 自己算（`AccountArchiveServiceImpl.archiveIfLastChannelRemoved`；**旧名 `AccountApplicationServiceImpl.archiveUserInfoIfLastChannelRemoved` 已随账户域六轮拆分作废**，见 `decisions.md:730`，2026-09-23 核对），**不是**由 pay-sign-server 远程驱动的一次状态迁移。

## 四、对外接口 vs 对内接口

**对外接口定义 NEVER 修改；对内接口可以改。** 对外接口一律经 `fep-app-server` 暴露。

判据：**能被 `parseBizData(...)` 解析的 DTO 就是对外契约。**

已确认的对外接口（2026-09-11 实测）：

| 路径 | 位置 |
|---|---|
| `/ci/app/requestAgreeRelease` + `/app/requestAgreeRelease`（双别名） | `fep-app-server/.../AppAccountController.java:65` |
| `/ci/app/requestRemovePayChannel` + `/app/requestRemovePayChannel`（双别名） | 同上 `:98` |
| 换手机号 | `fep-app-server/.../PhoneChangeController.java:37`，该文件 `:21` 另有约束「NEVER 再加 `updatePhone` / `/ci/app` 路径别名或 `newMsisdn`」 |

连带红线：

- `RequestRemovePayChannelReqDTO` / `RequestRemovePayChannelResult` **同时是对外 bizData 契约和内部 RPC 参数**（`AccountAppServiceImpl:80` 原样透传）。**NEVER 给这两个类加字段** —— 加字段等于改对外接口，且会踩 `AGENTS.md §7` 的「`model` 版本恒 2.0.0 + Fastjson2 静默丢字段 + 链路上每个模块镜像都要重建」那个坑。需要携带新信息时**新建内部 DTO**。
- `ItpCommonFormRequest`（2026-09-11 收口前各模块自有副本的统称是 `CommonFormRequest`，现全项目唯一、在 `model` 模块）报文形态（`x-www-form-urlencoded` + `bizData` JSON）不可改。

各业务服务上与对外路径同名的端点（如 account-server 的 `/requestRemovePayChannel`）**是对内端点**，可以改。

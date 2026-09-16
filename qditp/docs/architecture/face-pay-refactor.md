# 当面付重构技术设计（face-pay-server）

**状态**：设计已定，未开工 ｜ **建立日期**：2026-09-08 ｜ **基线代码**：`main` @ `8e8b7bd`

把当面付从 `collect-pay-server` 剥出到新服务 `face-pay-server`，**同契约重写 + 蓝绿替换**，新建表不复用旧表。

## 一、定性：这是同契约重写，不是重构

四个前提由用户 2026-09-08 确认：

- **边界**：全量平移 34 个接口（含 IF5A-01/03/09 票卡透传），`collect-pay-server` 最终下线
- **切换**：改路由（设备域 `fep-app-vr` 两条 VirtualService route + APP 域 `fep-app` 一条 env），**不是改 K8s Service selector**；2026-09-15 已执行，详见 §22.10
- **URL**：设备侧 URL 前缀**一字不改**
- **规模**：日订单 1~10 万，峰值 50~300 QPS
- **表**：新建一套，不复用现有表
- **在途订单**：测试阶段不考虑

由此得出的第一原则：**第一阶段只做行为等价，不修缺陷**。

`docs/testing/face-pay/04-阻塞项与缺陷候选.md` E 节记的 7 条缺口、`docs/testing/bom-oneside/02` D 节记的 7 条，全部放到切换成功之后再逐条修。理由是可观测性——重写和修 bug 混在一起，切换后出问题无法区分「平移漏了」还是「新逻辑有 bug」，蓝绿的回滚价值也就没了。

## 二、⚠️ 已推迟的决策（生产切换方案评审前必须回来定）

**在途与跨日订单的兼容**。测试阶段不考虑，但生产切换绕不过去。三类操作的时间窗口是无限长的：

- **补传通知**：§7.5 与 §7.3 都要求「设备本地维护数据库或文件，记录未发送，等网络或 ITP 正常后**补传通知**」，规格未给时限
- **跨日退款**：《非现金购票单程票退款功能3.docx》R4 是「按逻辑卡号查**发售时间最近的一笔**订单」；规格另有「ITP 平台**每日批量**退款未取票交易」
- **隔天取票**：APP 下单与 TVM 取票是两次独立会话

所以「等在途订单收敂后再切」不成立。切换方案要在下列之间选一个：

- **新表写 + 旧表只读**（推荐）：收拢在一个 `LegacyOrderReader` 里，只读不写，设明确退役日期（建议 90 天）后删除。需双表读的是七个接口：`requestPayResult`、`requestGetPayResult`、四个结果上报、`requestOrderResult` + `requestTicketRefund`、取票三件套
- 数据迁移：表结构已重新设计，映射本身是风险源
- 不兼容：接受切换后旧订单的退款与补传通知失败

`LegacyOrderReader` 是只读旁路，**不影响新表设计**，所以现在做表设计不会返工。

### 二.1 已执行的裁决（2026-09-09）：方案一「新表写 + 旧表只读」已落地

`face-pay-server/src/main/java/.../legacy/` 四个类 + 一份 XML，开关 `f2f.legacy.enabled`（默认 true，环境变量 `F2F_LEGACY_READ_ENABLED`）：

- `F2fLegacyOrderMapper` / `F2fLegacyOrderMapper.xml` — **只有 SELECT**，四条语句：四张旧订单表 UNION ALL 查一笔、三张旧退款表 UNION ALL 计数、`TBL_TICKET_REFUND_RECORD` 按票计数、旧子票 join main ticket
- `LegacyOrderView` / `LegacyTicketView` — 全 String 投影。**金额与时间一律 `TO_CHAR` 后在 Java 侧宽松解析**：旧库这些列是 VARCHAR 且格式未核实（§14.6 第 1、4 条），在 UNION ALL 里做 `TO_NUMBER` / `TO_TIMESTAMP`，一行脏数据就让整条查询报错、退化成「查不到订单」
- `LegacyOrderReader` — 旧 `STATUS` 按 `ItpStatusEnum` 映射（3→CREATED、0→PAYING、1→PAID、2→PAY_FAILED），**未知取值留空不猜**；解析不了的字段留 null 并 warn，**NEVER 兜底成 now() 或 0**

**已接线的查询链路**（新表未命中即查旧库）：TVM `requestPayResult` / `requestPayOrderDetail`、BOM `requestGetPayResult` / `requestOrderResult`、APP `requestPayResult`；`channelCodeOf` 与 `payCenterOrderNoOf` 同样带旧库兜底。

**防重复退款闸门放在 `F2fRefundService.refund` 一处**：七种退款来源全部经它收口，所以一处即覆盖设备主动退、出票失败差额退、BOM 原路退、按票退、充值失败退、日结批量退、后台手工退。判定逻辑是「有 `ticketLogicNum` 就查 `TBL_TICKET_REFUND_RECORD`，否则查三张整单退款表」，命中即**拒绝**并给中文原因——**不伪造 `RefundOutcome`**，旧退款单号在新表里不存在，编一个会让上游把假单号回给设备。

**为什么必须查旧退款表**：旧订单表的 `STATUS` 没有「已退款」取值（§14.1 里 REFUNDED 无来源），新表 `UK_F2F_REFUND_IDEM` 只管新表自己的记录。不查旧表 = 跨日退款对旧单二次出款。

**旧单上的写操作按「投影不可写」处理**：`LegacyOrderReader.isLegacy(order)` 为 true 时，`REMARK` 带 `LEGACY:<表名>` 前缀、`id` 为 null。三处 PENDING 分支（TVM / BOM / APP 的查支付结果）识别到旧单后**只回「处理中」不做收口**——对旧单执行 `updateStatus` 一定是 0 行，而 0 行在本模块的语义是「状态机拒绝」，会得出完全相反的结论。

**本方案覆盖不到的一件事**：切换瞬间「已下单未支付」的旧单，其支付回调 `payNotice` 会打到新服务而新表没有该单，仍回 `orderNotExist`。窗口等于二维码有效期（180 秒），**切换步骤里 MUST 包含「改路由前 3 分钟停止新下单」或明确接受该窗口**，靠只读旁路解决不了（要解决就得写旧表，与本方案冲突）。

**退役**：旧单全部超出退款与补传窗口后（建议 90 天），删掉 `legacy` 包与注入点即可，新表链路不依赖它。想先停用不删代码就把 `f2f.legacy.enabled` 置 false。

## 三、领域模型

从规格与需求文档推导，"当面付"是七个独立概念，旧实现把它们糊成了 **23 张表**（逐表列清单与字段级映射见 §十四）。

| 表 | 职责 | 粒度 | 替代旧表 |
|---|---|---|---|
| `F2F_ORDER` | 收款单：业务意图、金额、状态机 | 一次收款 | `TvmPayPreOrder` / `TVM_APP_ORDER` / `BomNoCashOrder` |
| `F2F_PAYMENT` | 与支付中心的每次交互流水 | 一次支付尝试 | **旧实现无此概念** |
| `F2F_RESULT_REPORT` | 设备业务结果上报 | 一次上报 | `TBL_TVM_MAIN_TICKET` 的上报部分 |
| `F2F_TICKET` | 票明细 | 一张票 | `TBL_TVM_SUB_TICKET` |
| `F2F_REFUND` | 退款单 | 一票一次退款 | `TBL_TICKET_REFUND_RECORD` / `TBL_BOM_TICKET_REFUND` |
| `F2F_NOTIFY_TASK` | 出向通知可靠投递 | 一条通知 | 三张 Notice 表合并 |
| `F2F_DEVICE_STATUS` | 设备最新心跳状态 | 一台设备 | **旧实现只打日志不落库** |

DDL 见 `face-pay-server/src/main/resources/sql/f2f-schema.sql`。

## 四、订单状态机

**内部状态机不等于对外返回值**——这是旧实现逻辑混乱的主要来源之一。

```
CREATED ──> PAYING ──> PAID ──> FULFILLED
   │           │         │
   │           │         ├──> FULFILL_FAILED ──> REFUNDING ──> REFUNDED
   │           │         └──> TOPUP_SUSPECT
   │           └──> PAY_FAILED
   └──> EXPIRED
```

- `EXPIRED`：二维码 180 秒超时未支付
- `TOPUP_SUSPECT`：充值存疑，待 BOM 人工判断。对应 §7.3「TVM 充值结果存疑，打印故障单 → 用户凭故障单到 BOM → 操作员判断 → IF2A-09 上报」。因此 `F2F_ORDER.SUSPECT_SLIP_NO` 必填，BOM 凭故障单号查
- `PAID` 但未取票 → 由「每日批量退款」任务推进到 `REFUNDING`

**对外映射**由一个纯函数完成，按渠道不同（**取值出处与代码行号见 §15.3**，同一语义在 TVM 与 BOM 用词不同是既有契约事实，不是设计取舍）：

- `IF2A-03` 查询支付结果：`ORDERED` / `SUCCESS` / `FAILED`
- `IF8A-05` 扫码支付应答：只有 `SUCCESS` / `FAILED`（**无 PROCESSING**）
- `IF8A-06` 查询支付结果：`SUCCESS` / `FAILED` / `PROCESSING`

这个映射函数 **MUST 能脱离 Spring 独立单测**。旧实现把映射逻辑散在多个 service 方法里，测不了。

## 五、幂等键清单

每个写入口一个，由数据库约束保证，不靠业务代码判重。

| 写操作 | 幂等键 | 索引 |
|---|---|---|
| 下单 | `(CHANNEL, DEVICE_ID, DEVICE_SEQ)` | `UK_F2F_ORDER_DEV_SEQ`，函数索引做部分唯一 |
| 支付成功 | `ORDER_NO` where `PAY_STATUS='SUCCESS'` | `UK_F2F_PAY_SUCCESS`，函数索引 |
| 支付尝试 | `(ORDER_NO, ATTEMPT_NO)` | `UK_F2F_PAY_ATTEMPT` |
| 结果上报 | `(REPORT_TYPE, ORDER_NO)` | `UK_F2F_REPORT_IDEM` |
| 退款 | `(ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'), REFUND_SOURCE)` | `UK_F2F_REFUND_IDEM` |
| 通知 | `(NOTIFY_TYPE, ORDER_NO, NVL(REFUND_NO,'#NONE#'))` | `UK_F2F_NOTIFY_IDEM` |
| 心跳 | `(CHANNEL, DEVICE_ID)` | 主键，`MERGE INTO` |

配 `DuplicateKeyException` 兜底，符合 `AGENTS.md` §5.1「唯一索引 + `DuplicateKeyException` 兜底」。

**部分退款不受 `UK_F2F_REFUND_IDEM` 误拦**：需求 R2 是「全部或部分单程票退款」，按票粒度；买 3 退 1 再退 1 是两条不同 `TICKET_LOGIC_NUM` 的记录。整单退时 `TICKET_LOGIC_NUM` 为空，用 `NVL` 占位避免多笔整单退绕过约束。

## 六、由约束直接解决的已知缺陷

| 缺陷 | 原编号 | 本设计的解法 |
|---|---|---|
| 退款无幂等，可重复退款 | E6 | `UK_F2F_REFUND_IDEM` |
| 取票订单锁定 2008 未实现 | E2 | `F2F_ORDER.ACTIVATE_DEVICE_ID` + `ACTIVATE_TMS`，非空即锁定 |
| 充值限额 1000 元未拦 | E1 | `F2F_ORDER.CARD_BEFORE_AMOUNT` 落库，校验有数据依据 |
| BOM 退款查不到「最近一笔」 | E5-R4 | `IDX_F2F_ORDER_CARD (CARD_ID, CREATE_TMS DESC)` + `IDX_F2F_TICKET_RECENT` |
| `transType` 九值只实现 42 | E4 | `TRANS_TYPE` / `ADMIN_TRANS_TYPE` 显式建模 + `CHECK` 约束 |
| 每日批量退款无任务 | E3 | `IDX_F2F_ORDER_SCAN (ORDER_STATUS, EXPIRE_TMS)` 提供扫表路径 |
| 支付重试不可追溯 | 新发现 | `F2F_PAYMENT` 一行一次尝试 |
| 设备失联查不出来 | 新发现 | `F2F_DEVICE_STATUS` |

**注意**：表结构只是提供了修复的基础，业务逻辑仍需在阶段 6 实现。表建好不等于缺陷已修。

## 七、服务内部分层

单服务，端口沿用 `58101`，`spring.application.name` **沿用 `itpagm`**（改名会连带 `rpc` 默认服务名问题，见 `AGENTS.md` §8 键缺失一条）。

- `controller/` 协议接入。三组 URL 前缀原样保留：`/itptvm/ci/tvm/**`、`/itpbom/ci/bom/**`、`/ci/app/**`，加运营端 `/page/face-pay/**`。只做参数校验与 DTO 转换
- `application/` 业务编排。**按用例组织，不按设备**。核心用例六个：下单、拉码支付、查支付结果、结果上报、退款、票卡透传。TVM/BOM/APP 差异用策略，旧实现是三套复制
- `channel/paycenter/` 渠道适配。报文组装、签名、三条 URL、`scene` 三态。**MUST 能脱离 Spring 独立单测**
- `repository/` 持久层，走自研 `mybatis-adaptor`
- `passthrough/` IF5A 三个透传接口，注明「无业务逻辑，只转 ticket-server」

**`providerId="03"` 的跨域分流**（旧代码在 `TvmOrderController.java:78/169/188` 三处）收拢成显式的 `DeviceRouter`，不散在 Controller 里。这三处是「TVM 路径进来、BOM 逻辑处理」，不能按 URL 前缀简单分层。

## 八、两条硬性编码约束

**`@Transactional` 方法内禁止调 channel 层。** 对照 `AGENTS.md` §5.2 与 2026-08-26 生产事故（`PaySignWorkflow.receivePayResult`，订单循环重推 8 分钟、`PAY_CALLBACK_LOG` 零条落库）。要在**代码结构上**保证：channel 层调用只能从不带事务的 orchestrator 发起，或用 `TransactionSynchronizationManager` 的 `afterCommit`。不靠 review 记住。

旧实现里同形态的已知点：`collect-ticket-server/.../TicketCollectServiceImpl.java:440`、`ticket-server/.../CardDataHandler.java:259`。

**支付中心调用不得在请求线程上长时间阻塞。** `spring.threads.virtual.enabled=true` 是全服务默认（`resource/micro/web/src/main/resources/web.properties:6`）。300 QPS 峰值 + ojdbc8 大量 `synchronized` 方法，一两条慢 SQL 即可 pin 满载体线程池导致全 JVM 虚拟线程停止调度。`F2F_PAYMENT.COST_MS` 落库就是为了给这类问题留取证数据。

## 九、ADR

- **ADR-1 单服务而非拆分**。300 QPS 单服务足够；本项目无注册中心无网关，`service.*.url` 全硬编码，`AGENTS.md` §8 已记三类错配事故，多一个服务多一组错配面。**备选**：按设备域拆两服务——拒绝，共享订单模型会重复
- **ADR-2 新建表不复用旧表**（2026-09-08 用户决定，推翻初版共库方案）。收益是可重新建模并把幂等做进约束；代价是生产切换需要 `LegacyOrderReader`，见 §二
- **ADR-3 URL、端口、`application.name` 全部沿用**。设备侧动不了；改 name 连带 rpc 默认服务名问题
- **ADR-4 第一阶段只做行为等价，不修缺陷**。保证切换失败与新逻辑 bug 可区分
- **ADR-5 契约基线取自生产流量而非代码**。规格明确「即使是接口中无描述的字段，也需要参与签名组串」，说明存在未声明字段，从 DTO 反推会漏
- **ADR-6 引入 `F2F_PAYMENT` 支付流水**。旧实现无此概念导致支付重试不可追溯。**备选**：在订单表加重试次数字段——拒绝，丢失每次交互的报文与耗时
- **ADR-7 订单号带版本标识位 `F2`**。零成本换取将来双表读的路由能力

## 十、分阶段

| 阶段 | 内容 | 可独立验证 |
|---|---|---|
| 0 | 给现有 `TBL_TICKET_REFUND_RECORD` 加唯一索引 | 是，旧服务受益。若因存量重复数据失败，本身是发现 |
| 1 | 契约固化：34 接口 + 生产真实样本对，导入 Apifox | 是 |
| 2 | 新表 DDL 上生产并**核对建表成功** | 是 |
| 3 | 新服务重写 + channel 层单测 | 是 |
| 4 | 影子比对：只读不写、接流量副本、比对响应 | 是 |
| 5 | 改路由切流量（`fep-app-vr` 两条 route + `fep-app` 一条 env） | 回滚为改回原目标，见 §22.10 |
| 6 | 逐条修 14 处缺口 | 是 |
| 7 | 生产切换的在途兼容方案（§二 推迟项） | 阶段 5 前必须完成决策 |

阶段 2 要独立验证的原因：`AGENTS.md` §8 记「代码有 mapper、生产库无表」是本项目高频缺陷，`acc-es-server` 13 张表曾全缺。

## 十一、落地时的 Oracle 注意点

- **注释只能用 `COMMENT ON`**。SQL 正文内 `--` 或 `/* */` 会被 Druid WallFilter 判定注入并**静默失效**（`AGENTS.md` §5.1，`PayTxnDetailMapper.updatePayCallback` 已炸过）。且 `dm.properties:5` 在达梦关了 WallFilter，**该类缺陷只在 Oracle 生产暴露**——测试环境若连达梦测不出来
- **分区表的唯一索引必须 GLOBAL**。`F2F_ORDER` / `F2F_PAYMENT` / `F2F_RESULT_REPORT` 按 `CREATE_TMS` interval 月分区，唯一索引不含分区键，归档 `DROP PARTITION` 时 **MUST** 带 `UPDATE GLOBAL INDEXES`
- **`DEVICE_SEQ` 的部分唯一索引**用 `CASE WHEN ... IS NULL THEN NULL` 写法。直接建复合唯一索引会因 Oracle 在部分列非 NULL 时仍校验，导致同一台 TVM 的多笔订单互相冲突（TVM 不传 `bomOptSeq`）
- 金额一律 `NUMBER(12)`，单位分（规格统一为分）

## 十二、测试阶段必须造的四个场景

新表的核心收益是唯一约束，这些验不到则重构白做：

1. **重复退款**：同一 `ticketLogicNum` 连续两次 `requestTicketRefund` → 第二次被 `UK_F2F_REFUND_IDEM` 拦住，支付中心只收到一次
2. **重复下单（BOM）**：同一 BOM 同一 `bomOptSeq` 两次 → 被 `UK_F2F_ORDER_DEV_SEQ` 拦住；同时验 TVM 多笔正常订单不被误拦（`DEVICE_SEQ` 为空的分支）
3. **重复通知**：同一订单出票结果重复上送 → `F2F_RESULT_REPORT` 只一条，`F2F_NOTIFY_TASK` 只一条
4. **重复下单（TVM）—— 验证方案 1 的前提**：制造一次 TVM 请求超时后的设备自动重发（断网数秒或让服务端延迟响应超过设备超时阈值），**比对两次请求的 `orderNo` 是否相同**。
   - 相同 → 方案 1 成立，第二次被 `UK_F2F_ORDER_NO` 拦住并幂等返回已有订单
   - 不同 → **方案 1 的前提被推翻，MUST 回到 §十六 P0-2 重新决策**，同时确认多余订单确实在 180 秒后转 `EXPIRED`

场景 4 是本轮唯一「验证假设」而非「验证实现」的用例，**MUST 在阶段 1 契约固化时做，NEVER 推到阶段 4 影子比对**——它的结论会反过来改设计。

## 十三、风险与假设

- **假设**：34 个接口的调用方只有 TVM / BOM / APP / 支付中心 / web-server Quartz 五方。若有第三方直连或直接读库，`LegacyOrderReader` 方案需重评
- **风险（最大）**：契约基线的完整性。若基线从代码推而非生产流量抓，切换后暴露的问题会集中在「设备实际在传但代码未声明的字段」上，而这类问题**在测试环境复现不出来**
- **待确认**：上游三个调用方向的真实地址。`fep-app-server` **缺 `service.collectPay.url`**（`AGENTS.md` §8 已记，落到默认服务名 `collect-pay-server-service`）；`collect-ticket-server` 用自己模块的 `CollectPayClient`；`web-server` Quartz 经 `F2FClient` 调 `service.f2f.url`。按 §8，线上真实值 **MUST 查 K8s Deployment env**

## 十四、旧表字段映射（`LegacyOrderReader` 用）

**来源**：`collect-pay-server/src/main/resources/mapper/*.xml` 的 `resultMap` 与 `insert` 语句实际写法，2026-09-08 逐文件核对。**不是**从生产库 `USER_TAB_COLUMNS` 读的——列的**物理类型与长度未经核实**，只有 mapper 声明的 `jdbcType`。阶段 2 上线前 **MUST** 用 MCP 核对一次真实表结构（先 `createNamedConnection`，见 `AGENTS.md` §8）。

**旧表清单（23 张，按语义分组，组间无重复）**：

- 订单类 6：`TBL_TVM_ORDER_PAY`、`TBL_TVM_ORDER_PAY_PRE`、`TBL_TVM_ORDER_TOPUP`、`TBL_TVM_APP_ORDER`、`TBL_BOM_ORDER_PAY`、`TBL_BOM_SALE_INFO`
- 取票锁定 1：`TBL_TVM_TAKE_TICKET_ORDER`
- 票据类 4：`tbl_tvm_main_ticket`、`tbl_bom_main_ticket`、`TBL_TVM_SUB_TICKET`、`TBL_BOM_SUB_TICKET`
- 上报类 3：`TBL_BOM_BUS_RESULT`、`TBL_BOM_TOPUP_RESULT`、`TBL_TVM_Topup_Notiy`
- 退款类 5：`TBL_TVM_ORDER_REFUND`、`TBL_BOM_ORDER_REFUND`、`TBL_APP_ORDER_REFUND`、`TBL_BOM_TICKET_REFUND`、`TBL_TICKET_REFUND_RECORD`
- 通知类 3：`tbl_notice_app_taketicket_record`、`tbl_notice_app_refund_record`、`TBL_NOTICE_APP_FAILURE_RECORD`
- 支付日志 1：`App_Pay_Logs`

**表名大小写不统一**（`tbl_tvm_main_ticket` 小写、`TBL_TVM_Topup_Notiy` 混合、`App_Pay_Logs` 混合），写 `LegacyOrderReader` 的 SQL 时按原文抄，别统一成大写。上表的分组是按语义划的，`tbl_*_main_ticket` 虽归在「票据类」，字段级映射时同时供 §14.4 上报用。

### 14.1 `F2F_ORDER` ← 四张订单表

| 新列 | TVM 购票 `TBL_TVM_ORDER_PAY` | TVM 充值 `TBL_TVM_ORDER_TOPUP` | BOM `TBL_BOM_ORDER_PAY` | APP `TBL_TVM_APP_ORDER` |
|---|---|---|---|---|
| `ORDER_NO` | `ORDER_NO` | `ORDER_NO` | `ORDER_NO` | `ORDER_NO` |
| `CHANNEL` | `CHANNEL` | `CHANNEL` | `CHANNEL` | 常量 `01` |
| `BIZ_TYPE` | 常量 `01` | 常量 `02` | 常量 `04` ⚠️见 §15.2 | 常量 `03` ⚠️见下 |
| `ORDER_STATUS` | `STATUS` | `STATUS` | `STATUS` | `PAY_STATUS` |
| `ORDER_AMOUNT` | `TOTAL_PRICE` | `TRANS_AMOUNT` | `TRANS_AOUNT` ⚠️ | `totalPrice` / `PAY_AMOUNT` |
| `TRANS_TYPE` | — | — | `TRANS_TYPE` | — |
| `ADMIN_TRANS_TYPE` | — | — | `ADMIN_TRANS_TYPE` | — |
| `DEVICE_ID` | `DEVICE_ID` | `DEVICE_ID` | `DEVICE_ID` | `DEVICE_ID` |
| `DEVICE_SEQ` | — | — | `BOM_OPT_SEQ` | — |
| `OPERATOR_ID` | — | — | `OPERATER_ID` ⚠️ | — |
| `SHIFT_ID` | — | — | `SHIFT_ID` | — |
| `THIRD_USER_ID` | — | — | — | `USER_ID` |
| `CARD_ID` | — | `TICKET_LOGIC_NUM` | `CARD_ID` | — |
| `TICKET_NUM` | `TICKET_NUM` | — | — | `TICKET_NUM` |
| `TICKET_PRICE` | `TICKET_PRICE` | — | — | `TICKET_PRICE` |
| `SINGLE_TICKET_TYPE` | `TICKET_TYPE` | — | — | `TICKET_TYPE` |
| `ENTRY_STATION_CODE` | `IN_STATION_CODE` | — | 查 `TBL_BOM_SALE_INFO` | `IN_STATION_CODE` |
| `EXIT_STATION_CODE` | `OUT_STATION_CODE` | — | 查 `TBL_BOM_SALE_INFO` | `OUT_STATION_CODE` |
| `CARD_BEFORE_AMOUNT` | — | `BEFORE_AMOUNT` | — | — |
| `CARD_AFTER_AMOUNT` | — | `AFTER_AMOUNT` | — | — |
| `QRCODE_GEN_DATE` | — | — | — | `QRCODE_GEN_DATE` |
| `RANDOM_FACT` | — | — | — | `RANDOM_FACT` |
| `ACTIVATE_FLAG` | — | — | — | `ACTIVATE_FLAG` |
| `ACTIVATE_TMS` | — | — | — | `ACTIVE_TIME` |
| `PAID_TMS` | — | — | — | `PAY_TIME` |
| `CREATE_TMS` | `CREATE_TIME` | `CREATE_TIME` | `CREATE_TIME` | `CREATE_TIME` |
| `UPDATE_TMS` | `UPDATE_TIME` | `UPDATE_TIME` | `UPDATE_TIME` | `UPDATE_TIME` |
| `REMARK` | `MSG` | `MSG` | `MSG` | `MSG` |

**无旧库来源的新列**（切换后旧订单这几个字段只能为空，`LegacyOrderReader` **NEVER** 编造默认值）：`STATION_CODE`、`EXPIRE_TMS`、`FULFILL_TMS`、`ACTIVATE_DEVICE_ID`、`SUSPECT_SLIP_NO`（旧库在上报表的 `FAULT_SLIP_SEQ`，不在订单表）。

⚠️ **APP 侧 `BIZ_TYPE` 是 `03`（取票）不是 `01`（购票）**：本表最初写的是 `01`，2026-09-09 实现 `F2fAppOrderService` 时定为 `03`——APP 侧下的是取票单，购票在 TVM 侧。`LegacyOrderReader` 的 UNION ALL 已按 `03` 投影，与运行时一致。**判断口径 MUST 以 `F2fAppOrderService.createOrder` 为准，本表是设计期草稿。**


`TBL_TVM_ORDER_PAY_PRE` 只有 6 列（`ORDER_NO` / `TRANS_TYPE` / `TRANS_AMOUNT` / `DEVICE_ID` / `CREATE_TIME` / `UPDATE_TIME`）、只有 `insert` 与 `selectByOrderNo`、**没有任何 update**——它是「预下单落个痕」，新表里由 `F2F_ORDER` 的 `CREATED` 状态取代，不单独建表。

### 14.2 `F2F_PAYMENT` ← 三张订单表的支付列 + `App_Pay_Logs`

旧实现**没有支付流水概念**，支付信息以列的形式挂在订单表上，因此一笔订单只留得下最后一次尝试。回填时 `ATTEMPT_NO` 一律填 `1`。

| 新列 | 旧来源 |
|---|---|
| `ORDER_NO` | 三张订单表的 `ORDER_NO` / `App_Pay_Logs.ORDER_NO` |
| `PAY_SCENE` | 无直接列，由 `PAY_TYPE` + `URL` 是否为空推断 |
| `PAY_STATUS` | 订单表的 `STATUS` / `PAY_STATUS`、`App_Pay_Logs.PAY_RESULT` |
| `PAY_AMOUNT` | `TOTAL_PRICE` / `TRANS_AMOUNT` / `TRANS_AOUNT` / `PAY_AMOUNT` |
| `PAY_TYPE` | `PAY_TYPE`（`TBL_BOM_ORDER_PAY` 无此列） |
| `PAYMENT_VENDOR` | `TBL_BOM_ORDER_PAY.PAYMENT_VENDOR` |
| `PAY_CHANNEL_CODE` | `TBL_BOM_ORDER_PAY.PAYMENT_CODE`、`TBL_TVM_APP_ORDER.PAY_CHANNEL_CODE`、`App_Pay_Logs.PAY_CHANNEL_CODE` / `CHANNEL_CODE` / `CHANNEL_TYPE` |
| `PAY_URL` | `URL` |
| `PAY_CENTER_ORDER_NO` | `PAYCENTER_ORDERNO`、`App_Pay_Logs.TRADE_NO` |
| `CHANNEL_ORDER_NO` | `PAYCENTER_CHANNELORDERNO`（APP 表叫 `PAYCENTER_CHANNEL_ORDERNO`，**多一个下划线**） |
| `RET_MSG` | `MSG` |
| `CREATE_TMS` | `CREATE_TIME` / `App_Pay_Logs.CREATE_TMS` |

`REQUEST_BODY` / `RESPONSE_BODY` / `COST_MS` / `RET_CODE` / `FINISH_TMS` **旧库无来源**。`TBL_TVM_APP_ORDER.paymentInfo` 与 `merchantOrderNo`、`REQUEST_PAY_FLAG` 三列语义待确认，先原样存 `RESPONSE_BODY` 留证，**NEVER** 猜着往结构化列里塞。

`App_Pay_Logs` 是唯一「时间列本来就是 `TIMESTAMP`、金额本来就是 `INTEGER`」的旧表（列名也是唯一用 `CREATE_TMS` 的），其余表全是 `VARCHAR`。

### 14.3 `F2F_TICKET` ← `TBL_TVM_SUB_TICKET` / `TBL_BOM_SUB_TICKET`

两张表列完全相同：`MAIN_TICKET_ID`(BIGINT)、`TICKET_LOGIC_NUM`、`TRANS_DATE`、`create_Time`（**原文这个大小写**）、`TRANS_AMOUNT`、`RSV2`。

| 新列 | 旧来源 |
|---|---|
| `ORDER_NO` | **无此列**，必须经 `MAIN_TICKET_ID` join `tbl_tvm_main_ticket` / `tbl_bom_main_ticket` 才拿到 |
| `TICKET_LOGIC_NUM` | `TICKET_LOGIC_NUM` |
| `TRANS_DATE` | `TRANS_DATE` |
| `TICKET_PRICE` | `TRANS_AMOUNT` |
| `CREATE_TMS` | `create_Time` |
| `TICKET_STATUS` | 无来源，回填按 `ISSUED` |
| `REFUND_NO` | 可能在 `RSV2`（见 14.6 第 2 条），**MUST 抽样核对后再用** |

新表的 `ORDER_NO` 直接落列、不再需要 join——这是 `F2F_TICKET` 相对旧结构的主要改动，也意味着 `LegacyOrderReader` 读旧票必须自己做这个 join。

### 14.4 `F2F_RESULT_REPORT` ← 四张上报表

| 新列 | `tbl_*_main_ticket` | `TBL_BOM_TOPUP_RESULT` | `TBL_TVM_Topup_Notiy` | `TBL_BOM_BUS_RESULT` |
|---|---|---|---|---|
| `ORDER_NO` | `ORDER_NO` | `ORDER_NO` | `ORDER_NO` | `ORDER_NO` |
| `REPORT_TYPE` | 由 `NOTIFY_TYPE` 推 | 常量 `TOPUP_OK` | 由 `topupStatus` 推 | 常量 `BOM_BIZ_RESULT` |
| `DEVICE_ID` | — | — | — | `DEVICE_ID` |
| `ORDER_TICKET_NUM` | `BUY_TICKET_NUM` | — | — | — |
| `ACTUAL_TICKET_NUM` | `ACTUAL_TAKE_TICKET_NUM` | — | — | — |
| `TOPUP_STATUS` | — | `TOPUP_STATUS` | `topupStatus` | — |
| `OPT_RESULT` | — | — | — | `OPT_RESULT` |
| `FAULT_SLIP_SEQ` | `FAULT_SLIP_SEQ` | — | `faultSlipSeq` | — |
| `ERROR_CODE` | `ERROR_CODE` | — | `errorCode` | — |
| `ERROR_MESSAGE` | `ERROR_MESSAGE` | — | `errorMessage` | — |
| `TRANS_AMOUNT` | — | `TRANS_AMOUNT` | `trans_Amount` | — |
| `AFTER_AMOUNT` | — | `AFTER_AMOUNT` | `AFTER_AMOUNT` | — |
| `REPORT_TMS` | `TAKE_TICKE_DATE` ⚠️ | `TRANS_DATE` | `trans_Date` / `faultOccurDate` | — |
| `CREATE_TMS` | `CREATE_TIME` | `SYSDATE` ⚠️ | `CREATE_TIME` | `CREATE_TIME` |

**同语义两张表列名完全不统一**：BOM 侧是 `TRANS_DATE` / `TRANS_AMOUNT` / `TOPUP_STATUS`，TVM 侧是 `trans_Date` / `trans_Amount` / `topupStatus`。`LegacyOrderReader` 里这两条 SQL 不能共用一段列清单。

`TBL_BOM_BUS_RESULT.NOTIFY_ID` 是该表主键（其余上报表主键是 `ID` 或无主键），新表统一用自增 `ID` + `UK_F2F_REPORT_IDEM(REPORT_TYPE, ORDER_NO)`，`NOTIFY_ID` 回填进 `RAW_BODY` 留证。

### 14.5 `F2F_REFUND` / `F2F_NOTIFY_TASK` / `F2F_DEVICE_STATUS`

`F2F_REFUND` ← 五张退款表。`TBL_TVM_ORDER_REFUND` / `TBL_BOM_ORDER_REFUND` / `TBL_APP_ORDER_REFUND` 三张列几乎相同：

| 新列 | 旧来源 |
|---|---|
| `REFUND_NO` | `REFUND_NO`（三张表都是主键） |
| `ORIG_ORDER_NO` | `PAY_ORDER_NO` |
| `REFUND_STATUS` | `REFUND_STATUS` |
| `REFUND_AMOUNT` | `REFUND_AMOUNT` |
| `REFUND_REASON` | `REFUND_REASON` |
| `PAY_CENTER_REFUND_NO` | `MERCHANT_REFUND_NO` / `CHANNEL_REFUND_NO`（两列，新表只留一列，**MUST 确认该用哪个**） |
| `FAIL_REASON` | `REFUND_MSG` |
| `FINISH_TMS` | `REFUND_TIME` |
| `REFUND_SOURCE` | 无列，按来源表推：TVM→`TOPUP_FAIL`/`TAKE_TICKET_FAIL`、BOM→`BOM_ORIGINAL`、APP→`APP_REQUEST` |

差异：只有 `TBL_TVM_ORDER_REFUND` 有 `BUSINESS_TYPE`，且其 `CREATE_TIME` 声明为 `TIMESTAMP`，另两张是 `VARCHAR`；另两张有 `UPDATE_TIME`，它没有。

`TBL_BOM_TICKET_REFUND` 与 `TBL_TICKET_REFUND_RECORD` 是**同一次插入双写的两张表**（`BomNoCashOrderMapper.xml:109` 与 `:125`），列名在 insert 里写成小驼峰：`orderNo`、`TakeTicketNum`、`ticketRefundDate`、`ticketLogicNum`、`transDate`、`transAmount`、`transType`、`refundNo`。按票退款的 `TICKET_LOGIC_NUM` / `ORIG_TRANS_DATE` / `REFUND_NUM` / `TRANS_TYPE` 从这里取。

`F2F_NOTIFY_TASK` ← 三张通知表，`RETRY_TIMES` ← `retryTimes`、`NOTIFY_STATUS` ← `status`（注意 `tbl_notice_app_taketicket_record.status` 是 `VARCHAR`，另两张是 `INTEGER`）、`ORDER_NO` ← `ORDER_NO`；`PAYLOAD` 无来源，按各表业务列拼 JSON。`refundType` / `refundResult` / `refundResultDesc` / `refundDate` / `refundAmount`（退款通知表）与 `orderTicketNum` / `actualTakeTicketNum` / `takeTickeDate` / `takeTiketFaultReason`（失败通知表，**原文 `Tiket` 少一个 c**）都进 `PAYLOAD`。

`F2F_DEVICE_STATUS` **无旧表来源**——旧实现心跳只打日志不落库，全部行都是切换后新产生的。

### 14.6 回填时的四个前提（两个已定，两个待核实）

1. **金额单位 = 分（已定，用户 2026-09-08 确认）**。新表 `NUMBER(12)` 全部按分，与旧库一致，**不需要换算**，`LegacyOrderReader` 直接 `TO_NUMBER` 即可。仍需注意两点：旧库金额列声明为 `VARCHAR`，转换时 **MUST** 处理空串与前导零；`TBL_TVM_ORDER_REFUND.REFUND_AMOUNT` 在 `resultMap` 声明 `INTEGER`、在 `insert` 按 `VARCHAR` 传（`RefundOrderMapper.xml:54`），同一列两种 `jdbcType` 是旧代码的不一致，单位本身没有歧义。
2. **`tbl_*_main_ticket` 的 `limit 1` 由新版本修掉（已定，用户 2026-09-08 确认）**。两张 main ticket 的 `selectByOrderNo` 都用了 `limit 1`，Oracle 不支持该语法，**这两条查询在 Oracle 上必然报错**——要么该路径从未被调用，要么已在静默失败。新服务对应查询 **MUST** 写成 `FETCH FIRST 1 ROWS ONLY`（Oracle 12c+）或 `ROWNUM = 1`，且 **MUST 带显式 `ORDER BY`**：原语句没有排序，"取一条"本身就是不确定的，照搬会把不确定性带进新实现。这条同时是 `collect-pay-server` 的一处现网缺陷，是否回头修旧服务由 §十的阶段 6 决定。
3. **`RSV1` / `RSV2` 逐表按实际用法处理（待核实，用户 2026-09-08 明确按实际定）**。已知两种冲突用法：`TvmOrderMapper.xml:44-62` 的分页 `resultMap` 把 `RSV2` 映射成 `refundNo`；`BomNoCashOrderMapper.xml:145`、`:157` 的两个扫表查询用 `rsv2 is null` 当「未处理」标记。**同一列在两处被当作两种东西**，因此 **NEVER 写一段通用的 RSV 转换逻辑**——每张表各自确认，确认结果补记到本节。新表已不再设预留列，这两种语义分别落到 `F2F_TICKET.REFUND_NO` 与 `F2F_ORDER.ORDER_STATUS`。
4. **时间格式（待核实）**。旧库时间列绝大多数是 `VARCHAR`，具体格式（`yyyyMMddHHmmss` 还是带分隔符）未核实；`tbl_*_main_ticket.CREATE_TIME`、`TBL_TVM_ORDER_REFUND.CREATE_TIME`、`App_Pay_Logs.CREATE_TMS` 声明为 `TIMESTAMP`。转 `TIMESTAMP(6)` 的 `TO_TIMESTAMP` 格式串 **MUST 按表分别定**，不能全局一套。

## 十五、契约基线与取值来源（阶段 1 的输入）

### 15.1 34 个接口的清单：**已存在，不再新建汇总文件**

`AGENTS.md` §5.3 明确禁止在仓库内重建「接口清单」类汇总文件（2026-08-25 删过 8 份派生产物）。清单已分散记录在三处，**MUST 直接用这三处，NEVER 另起一份**：

- **TVM 侧 14 条**（带 URL + 行号 + 注释编号的表格）：`docs/testing/face-pay/01-TVM.md:5-23`
- **APP 侧 7 条**（散文体，带行号）：`docs/testing/face-pay/01-TVM.md:24`
- **BOM 侧 11 条**（表格）：`docs/testing/face-pay/02-BOM.md:5-19`
- **全域 bullet 清单**（含运营端与已废弃类）：`docs/business/tvm-bom-pay.md:16-39`

**34 的构成**（2026-09-08 按 Controller 注解逐个数过，与上述文档一致）：

- `TvmOrderController`（`/itptvm/ci/tvm`）14 条
- `BomOrderController`（`/itpbom/ci/bom`）11 条
- `TvmAppOrderController`（`/ci/app`）7 条
- `FacePayOrderPageController`（`/page/face-pay/orders`）2 条
- 小计 ci 32 + page 2 = **34**

**不计入 34 的 4 条**：`CollectPayController` 整个文件 67 行全部被 `//` 注释（含 `package` 与 `@RestController`），`/requestPay`（IF8A-09）、`/payQuery`（IF8A-10）、`/requestRefund`（IF8A-12）、`/refundQuery`（IF8A-13）均不注册。⚠️ `docs/business/tvm-bom-pay.md:39` 写「IF8A-09/10/12/13 未生效」，`docs/testing/face-pay/00:77` 只点名 IF8A-09——**两处记载粒度不一致**，以代码为准（4 个都不生效）。

**编号不可作为定位依据**（这条直接影响阶段 1 的 Apifox 建模）：`IF2A-08` 与 `IF2A-09` 在 TVM 与 BOM 下各有一个不同实现，`IF8A-04/05/06` 在 BOM 域与 APP 域含义不同。冲突清单见 `docs/testing/face-pay/02-BOM.md:21-24` 与 `04-阻塞项与缺陷候选.md:48-57`（后者指出同一份《技术规范-第9部分》在 §7.3 与 §7.5/§7.6 各定义了一次）。**Apifox 的接口标识 MUST 用「模块 + URL」，NEVER 用编号。**

**另有一处 javadoc 与注解不一致**，照注释配用例会调不通：`TvmAppOrderController.java:90` 的 javadoc 写 `/ci/app/requestPayInfo`，实际注解是 `/requestPaymentInfo`（`:98`）；`TvmOrderController.java:62` 写 `/ci/tvm/requestGenSjtOrder`，实际类级前缀是 `/itptvm/ci/tvm`。

### 15.2 `TRANS_TYPE` 与 `BIZ_TYPE`：两个独立维度，此前 §14.1 记错

**更正 §14.1**：那里 BOM 一列的 `BIZ_TYPE` 写的是「由 `TRANS_TYPE` 推」，**这个推导关系不存在**。两者在旧代码里是各自独立的字典列：

- **`TRANS_TYPE`**（BOM 交易类型）取值有出处——`docs/testing/face-pay/04-阻塞项与缺陷候选.md:131-142`（E4）抄录了规格 IF8A-04 请求参数的九个取值：`02` 超时更新 · `03` 超程更新/一卡通余额不足更新 · `04` 未出站更新处理 · `05` 无入站更新处理 · `06` 储值票即时退卡/单程票退票 · `22` 充值 · `2A` 黑名单卡锁定 · `2B` 卡锁定解除 · `42` 行政处理。
  代码侧**只有 `42` 有校验**（`BomOrderController.java:101-103`，要求 `adminTransType` 非空），其余八个只作字典值落库。同处还纠正了一个易错点：**`02` 是「超时更新」不是「超程更新」，「超程更新」是 `03`**——我在 `f2f-schema.sql` 的 `COMMENT ON COLUMN F2F_ORDER.TRANS_TYPE` 里写的是「02超时更新，03超程更新」，与规格一致，不用改。
- **`BIZ_TYPE`** 对应的是 `BusinessTypeEnum`（`constant/BusinessTypeEnum.java:8-12`），五个值：`01` 扫码购票 · `02` 扫码充值 · `03` 扫码取票 · `04` bom 支付 · `05` app 退款。

由此产生一个**需要确认的设计决定**：`f2f-schema.sql` 的 `CK_F2F_ORDER_BIZ` 只允许 `01`~`04`，**故意不收 `05`**——新模型里退款是 `F2F_REFUND` 独立表，不是订单的一种业务类型。若要与旧枚举严格一一对应则需加 `05`，但那会让「退款」同时存在于两张表。**当前按不加处理**，此处留痕以免后来被当成漏写。

### 15.3 对外返回值：三套取值都在代码里，且 TVM 与 BOM 用词不同

§四 的结论经代码核对**成立**，补上出处：

- **`IF2A-03` TVM 查询支付结果** — 字段是 `paymentResult`，取值来自 `DevicePayCodeEnum`（`constant/DevicePayCodeEnum.java:8-11`）：`ORDERED` 已下单 / `SUCCESS` 成功 / `FAILED` 失败。组装点 `common/DeviceResponse.java:24`（成功）、`:34`（失败）、`:43`（支付中→`ORDERED`）。
- **`IF8A-05` BOM 扫码支付** — 只有 `SUCCESS` / `FAILED`。**`PROCESSING` 分支存在但被注释掉了**（`BomOrderServiceImpl.java:311`），这就是「无 PROCESSING」的直接原因，不是设计如此。
- **`IF8A-06` BOM 查询支付结果** — **硬编码字面量**，不走枚举：`"SUCCESS"`（`BomOrderServiceImpl.java:365`）、`"FAILED"`（`:371`）、`"PROCESSING"`（`:424`）。方法注释也写明了这三个值（`BomOrderService.java:43`）。

**由此得到两条新实现必须遵守的约束**：

1. **同一语义在两个渠道用不同词**：「支付中」在 TVM 侧是 `ORDERED`，在 BOM 侧是 `PROCESSING`。§四 说的「映射函数按渠道不同」不是设计取舍，而是**既有契约的事实**，改任何一个都会打破设备侧兼容。
2. **`UNPAID` 在出向被压成 `FAILED`**（`TvmOrderServiceImpl.java:307-309`、`TvmTopupServiceImpl.java:187-189`）。旧代码注释解释为「未支付也是终态」。新实现的 `F2F_ORDER.ORDER_STATUS` 有独立的 `EXPIRED`，映射到对外值时 **MUST 继续压成 `FAILED`**，否则设备会收到没见过的取值。

**取值来源的可靠性**：以上全部来自代码与 mapper，**不是**甲方规格原文。规格侧只在 `docs/testing/face-pay/04` 有二手抄录。阶段 1 用生产流量核对时，**MUST 优先信流量样本**——尤其 `paymentResultDesc`（中文描述）这类字段，代码里写的是「成功」「失败」「已下单」，设备是否依赖具体文案未知。

## 十六、设计复核结论（2026-09-08，按 34 接口逐条对照）

### 已改（`f2f-schema.sql` 五处）

- **`CK_F2F_ORDER_CHANNEL` 去掉 `'07'`**。原先按 `BaseRequestDTO.java:9` 注释把 STT 定为 `07`，但 `model/.../DeviceTypeEnum.java:48` 里 STT 是 `14`，两套编码冲突（`docs/testing/face-pay/03-STT.md:17`）。且 STT 无任何接入实现，34 个接口里没有 STT 入口，三种落地方式的支付方向相反（复用 BOM 是操作员扫乘客，复用 TVM 是乘客扫机器），`scene` 取值取决于需求而非实现。**在需求未定时把 `07` 写进约束等于替甲方定了口径**，已收窄为 `01/02/03`，理由写进 `COMMENT ON COLUMN F2F_ORDER.CHANNEL`。
- **`IDX_F2F_ORDER_USER` 加入 `ACTIVATE_FLAG`**。`requestPreActiveOrderList` 的查询是 `WHERE USER_ID=? AND ACTIVATE_FLAG=?`（`TvmAppOrderMapper.xml:126-131`），原索引不含该列。
- **`CK_F2F_NOTIFY_TARGET` 收窄为仅 `'APP'`**。旧实现三张通知表全是 `tbl_notice_app_*`，无向 BOM / TVM 的出向通知，原先放 `BOM` / `TVM` 属无依据的超前设计。新增目标前 MUST 先确认对端有接收接口。
- **`CK_F2F_REFUND_SOURCE` 增加 `'TVM_REQUEST'`**。TVM `/requestRefund`（注释「退款接口。自己用」，34 条之一，无编号）此前在六个 source 里无对应项，实现时会被随手塞进 `PAGE_MANUAL`（那是运营端，语义不同）。
- **`F2F_RESULT_REPORT` 表注释补上唯一索引的前提**。六个上报接口收敂为五个 `REPORT_TYPE`（TVM `topupCardResultNoti` 与 BOM `notiTopupResult` 同映射 `TOPUP_OK`），成立的前提是**同一订单只会被单一渠道上报**，因此 `UK_F2F_REPORT_IDEM` 不含 `CHANNEL`。前提被推翻时 MUST 改索引。

### P0-2 已定：TVM / APP 的防重下单采用方向 1「不设业务防重，靠过期收口」（用户 2026-09-08 决定）

> ⚠️ **2026-09-08 更正：本节原写的「方案 1 = 靠 `UK_F2F_ORDER_NO` 兜重复下单」建立在一个错误前提上，该前提已被代码证伪，见下方「前提已证伪」小节。结论从「靠唯一索引防重」改为「不设业务防重」，但**不新增任何唯一索引**这一点不变，因此 DDL 无需改动。**

**决定：TVM / APP 下单不做业务层判重，重复订单由 180 秒过期扫表收口。不在 `F2F_ORDER` 上新增任何基于业务字段的唯一索引。**

背景：`UK_F2F_ORDER_DEV_SEQ` 依赖 `DEVICE_SEQ`，而只有 BOM 传 `bomOptSeq`，TVM / APP 不传。旧实现同样无判重（`TvmOrderServiceImpl` 11 处 `selectByOrderNo`，无一处比对重复）。

**为什么不能用「设备 + 金额 + 时间窗」**：TVM 上「同一次意图被重发」与「排队的两位乘客各买同价同数量票」的报文完全一致——同 `deviceId`、同起讫站、同 `ticketPrice`、同张数，只差 `orderNo`。BOM 能区分是因为有操作员流水号，TVM 没有对应字段。按前者建约束会拦掉后者，高峰期这不是理论风险。

**前提已证伪（2026-09-08，抽契约时发现）**：原方案 1 依赖「`orderNo` 由设备提供、重发时复用同一个号」。**实际上 TVM 拉码下单的 `orderNo` 是 ITP 自己生成的**——`RequestGenSjtOrderReqDTO` 里没有 `orderNo` 字段（只有 `entryStationCode` / `exitStationCode` / `ticketPrice` / `singelTicketNum` / `singleTicketType` / `payType`），订单号由 `TvmOrderServiceImpl.generateOrderNo()` 取 `ORDER_NO_SEQ.NEXTVAL` 生成后**返回给设备**。因此设备每次重发我方都会发一个新号，`UK_F2F_ORDER_NO` 永远不冲突，**靠它防重等于零防护**。连带结论：**§十二 场景 4「实测设备是否复用同一个 `orderNo`」失效**，设备侧根本没有 `orderNo` 可复用，该场景不必再造。

**方向 1 的风险边界（这是可以接受它的理由）**：重复下单产生的多余订单停在 `CREATED`，180 秒无支付即由扫表置 `EXPIRED`（`IDX_F2F_ORDER_SCAN` 提供路径）。真正的资金影响需要**两笔都完成支付**，那是两次独立支付行为、两笔独立 `F2F_PAYMENT`，会在对账中暴露。代价是高峰期可能积压无效 `CREATED` 订单，但不影响乘客当场购票。与旧实现行为一致（旧实现同样无判重：`TvmOrderServiceImpl` 11 处 `selectByOrderNo`，无一处比对重复）。

**彻底解法仍是让 TVM 传设备流水号**（BOM 已有 `bomOptSeq`，`UK_F2F_ORDER_DEV_SEQ` 已就位可直接接），但属契约变更，与「URL 与报文一字不改」冲突，**MUST 作为独立需求提给甲方与 TVM 厂商**，不在本次重写范围内。

**前提不成立时的风险边界（这是选方案 1 的另一半理由）**：重复下单产生的多余订单停在 `CREATED`，180 秒无支付即由 `EXPIRED` 自然淘汰（`IDX_F2F_ORDER_SCAN` 提供扫表路径）。真正造成资金影响需要**两笔都完成支付**，而那是两次独立的支付行为、两笔独立的 `F2F_PAYMENT`，会在对账中暴露。因此即使前提被推翻，损失被限制在「多一笔待退款订单」，而非「乘客当场无法购票」。方案 3（窗口内幂等返回）的错误方向恰好相反，这是不选它的原因。

**实现约束**：`orderNo` 由设备提供时 **MUST 直接落库并依赖 `UK_F2F_ORDER_NO` 抛 `DuplicateKeyException`**，NEVER 先 `SELECT` 判存在再 `INSERT`——后者在并发下无效，且旧实现正是这个形态。捕获 `DuplicateKeyException` 后 **MUST 查出已有订单原样返回**（幂等返回），NEVER 返回失败。

**未采用的两个方案**：方案 2（TVM 也传设备流水号）是唯一彻底解法，但属契约变更，与「URL 与报文一字不改」冲突，应作为独立需求提给甲方与 TVM 厂商；方案 3（窗口内同参数幂等返回）在窗口内会把第二位乘客的真实购票并入前一笔，错误方向比方案 1 更严重。

### 尚未处理的 P1

- **IF5A 三个透传接口的定位有内部矛盾**：§七 说「只转 ticket-server 无落库」，`AGENTS.md` §2.2.2 说 IF5A 链路不完整，`docs/testing/bom-oneside/00:9-10` 说 IF5A-01+03 → `CardDataHandler`「链路完整可跑」。三处未闭合，透传层开工前 MUST 确认是否真无落库需求——若有，七张表不够。
- **`PAY_TYPE=0`（本地拼聚合码不调支付中心）时 `F2F_PAYMENT` 记什么状态未定**。`PAY_STATUS` 的五个取值都围绕「与支付中心交互」定义，这条分支是否落库、落什么状态，设计里是空的。

## 十七、建表执行记录与回滚 SQL

**目标库**：`jdbc:oracle:thin:@172.20.222.3:1521/AFCITPDB`，schema `QDITP`（用户 2026-09-08 确认可写）。MCP 连接键是 **`qditp`（用户名），不是 `createNamedConnection` 时传的 name** —— 传 name 会被忽略，用 name 调任何工具都报 `Connection not found`，MUST 先 `listConnections` 确认真实键。

**执行范围**：`face-pay-server/src/main/resources/sql/f2f-schema.sql` 全文共 **78 条语句 = 7 `CREATE TABLE` + 25 `CREATE INDEX` + 46 `COMMENT ON`**。**零条 ALTER / DROP / DML，不触碰任何旧表**。

**已执行（2026-09-08，78/78 成功，0 失败）**。库内核对结果：
- `USER_TABLES` 7 张全部存在，`F2F_ORDER` / `F2F_PAYMENT` / `F2F_RESULT_REPORT` 的 `PARTITIONED=YES`，其余 4 张 `NO`，与设计一致。
- `USER_INDEXES` 命中 36 行 = 25 条显式索引 + 7 条主键索引 + 4 条 CLOB 自动生成的 LOB 索引。
- 四个用函数表达式实现「部分唯一 / NVL 占位」的索引均落地为 `FUNCTION-BASED NORMAL / UNIQUE / PARTITIONED=NO`（即 GLOBAL），符合预期：`UK_F2F_ORDER_DEV_SEQ`、`UK_F2F_PAY_SUCCESS`、`UK_F2F_REFUND_IDEM`、`UK_F2F_NOTIFY_IDEM`。
- `USER_CONSTRAINTS` 22 条（7 PK + 15 CHECK），CHECK 名称与 DDL 逐条对齐。
- `IDX_F2F_ORDER_CARD` / `IDX_F2F_ORDER_USER` / `IDX_F2F_PAY_ORDER` / `IDX_F2F_TICKET_RECENT` 也显示为 `FUNCTION-BASED`，原因是含 `DESC` 列，Oracle 内部按函数索引实现，非异常。

**执行通道：MCP 的 `executeDdl` / `executeDdlRemote` 都不能用于建这三张分区表**。两者在服务端先过一层 SQL 解析器，遇到 `PARTITION BY RANGE` 直接报 `Encountered unexpected token: "RANGE"`，语句根本没发到 Oracle。实际执行走本机 `python3` + `oracledb`（4.0.2，thin 模式，无需 Oracle 客户端），按 `;` 切分后逐条 `cursor.execute`。**后续再有分区表 DDL MUST 走同一通道，NEVER 反复重试 MCP 的 DDL 工具。** 只读核对仍可用 MCP（`batchQuery` 查 `USER_TABLES` / `USER_INDEXES` / `USER_CONSTRAINTS`）。

**回滚 SQL**（索引与注释随表删除，7 张表之间无外键，可任意顺序执行）：

```sql
DROP TABLE F2F_ORDER PURGE;
DROP TABLE F2F_PAYMENT PURGE;
DROP TABLE F2F_RESULT_REPORT PURGE;
DROP TABLE F2F_TICKET PURGE;
DROP TABLE F2F_REFUND PURGE;
DROP TABLE F2F_NOTIFY_TASK PURGE;
DROP TABLE F2F_DEVICE_STATUS PURGE;
```

`PURGE` 表示不进回收站。执行前 `searchTables(keyword:"F2F")` 返回空，确认无同名对象被覆盖。

## 十八、渠道层（`channel/paycenter`）落地记录

九个类，位于 `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/channel/paycenter/`：

- `PayCenterProperties`（`pay.center`）—— 四条**完整 URL** + 聚合码收银台 URL + 回调地址 + 商户号 / 私钥 / 聚合码密钥 + 超时。密钥全部 `${ENV_VAR:}`。
- `PayScene` —— `qrcode` / `scan` / `app` 三态，对应旧三个 builder；不预留 STT。
- `PayCenterRequest` —— 六字段信封，`toString()` 只输出 `bizData` / `sign` 的**长度**。
- `PayCenterSigner` —— 纯类。待签串固定序 `merchantNo&apiVersion&signType&charset&bizData`，不排序不剔空；另含聚合码 `md5("orderNo=..&key=..")`。
- `PayCenterPayCommand`（record）—— 构造即校验：`scan` 必带 `authCode`，非 `qrcode` 必带 `paymentVendor`，金额必须为正。
- `PayCenterMessageFactory` —— 纯类，四种报文 + 聚合码 URL。**bizData 用 `LinkedHashMap` 显式定键序**，因为键序会进待签串；用 POJO 则键序由 Fastjson 排序策略决定，升级依赖即可能静默改签名内容。
- `PayCenterResult` / `PayCenterStatus` —— 把「业务成功 / 业务失败 / 没答上来」三分。
- `PayCenterClient` —— JDK 内置 `java.net.http.HttpClient`（不引新依赖；旧实现用的 commons-httpclient 3.x 已不维护），**不抛异常也不返回 null**。
- `PayCenterChannelConfig` —— 集中装配，signer 与 factory 故意不加 `@Component`，保证能脱离 Spring 单测。

**已修的旧实现缺陷**：`PayCenterServiceImpl.callPayCenter` 异常后 `return null`，各调用方一律按「支付中心返回结果为空」写 `status=FAILED`——把「不知道」当成了「没付成功」。新实现区分 `transportFailed`，该分支 MUST 落 `PAY_STATUS='UNKNOWN'`。另去掉了旧 `SignUtils:33` 打印私钥的那行日志。

**必须人工复核的一点（支付签名，AGENTS.md §5.2 安全红线）**：同一个支付中心网关，本项目存在**两套待签串算法**——collect-pay 侧是信封五键固定序（本模块沿用），pay-sign 侧 `PayGatewayClient.buildSignSource` 是「bizData 的 JSON 键 TreeMap 排序后拼接、剔空值」。本次按「同契约重写」保留 collect-pay 口径，**没有**去统一，两者该并到哪一套需人工与支付中心确认。

**验证状态**：`mise exec -- mvn clean test -pl face-pay-server` 通过，19 个用例 0 失败。
- 报文层 11 例（不需数据库）：三种 scene 的 bizData 逐字比对、退款 / 查询报文、待签串形态（并用公钥验签）、聚合码 URL、三条入参校验。
- 持久层 8 例（`F2fMapperSmokeTest`，需数据库）：Spring 上下文起得来、7 个 mapper 全部注入成功、**23 条 SELECT 逐条真的在 Oracle 上执行过**，全部返回空集/`null`，无一条被 Druid WallFilter 判为注入（反证 SQL 正文确实没有注释）。该类用 `@EnabledIfEnvironmentVariable(named = "DB_HOST")` 控制，无库环境自动跳过，不会拖垮 `mvn test`。运行方式：`DB_HOST=host:port DB_DATABASE=.. DB_USERNAME=.. DB_PASSWORD=.. mise exec -- mvn test -pl face-pay-server`。
- **仍未验证**：写路径（`insert` / `update` / `MERGE INTO` / `INSERT ALL`）——留到 TVM 下单纵向切片时连业务一起验，那时才有明确的数据清理边界；与真实支付中心的连通性和对端验签结果（需测试环境地址与凭据）。

**`PAY_TYPE=0` 口径（2026-09-08 暂定，按建议执行，未经业务复核）**：落一行 `F2F_PAYMENT`，`PAY_STATUS='INIT'`、`PAY_TYPE='0'`、`PAY_URL` 存聚合码整串、`PAY_CENTER_ORDER_NO` 留空。理由是这笔后续一定要由支付中心回调或查询接口收口成 SUCCESS/FAILED，留一行才能让 `UK_F2F_PAY_ATTEMPT` 与对账有落脚点；不落库则回调到达时无处可更。此口径 **MUST 在阶段 1 契约固化时与业务确认**，若被推翻只影响这一条分支的落库时机，不影响表结构。

## 十九、订单号格式（2026-09-08 定，长度待核实）

**旧格式**：`ProductType.code`(2) + `yyyyMMddHHmmss`(14) + 序列(4，左补零) = **20 位**，序列取 `ORDER_NO_SEQ.NEXTVAL`。见 `collect-pay-server/.../utils/OrderNoUtils.java:26-29` 与 `mapper/OrderSeqMapper.java:16`。TVM 拉码走 `ProductType.ordinaryTicket` = `00`。

**新格式**：`F2`(2) + `00`(2，单程票) + `yyyyMMddHHmmss`(14) + 序列(4，左补零) = **22 位**，序列取**新序列 `F2F_ORDER_NO_SEQ`**。

两点理由：
- `F2` 前缀让蓝绿并行期两套服务产生的订单号一眼可分，排障与对账都能直接归属到服务版本（§三 的 `ORDER_NO` 列注释「含版本标识位F2」即此意）。
- **不共用旧 `ORDER_NO_SEQ`**：两个服务并行期各自发号才不会相互影响，旧服务回滚也不会因为新服务消耗过序列而出现空洞。

⚠️ **待核实（未决，影响长度）**：**若 ACC 对账文件或 BOM 侧按 20 位定长解析订单号，22 位会直接解析错位**，此时必须回退。回退方式有两种，届时二选一：
- 去掉 `F2` 前缀，回到 20 位，版本区分改用 `F2F_ORDER` 表自身（新表即新服务，本来就能区分）；
- 或把 `F2` 挤进现有 20 位（如占用序列段 4 位中的 2 位），代价是每秒并发上限从 9999 降到 99，**不可取**。

因此优先方案是「回到 20 位、不做前缀」。**开工写 `orderNo` 生成器时 MUST 先向甲方 / ACC 侧确认是否定长解析**；确认前代码里的格式集中在一个类里（不散落字符串拼接），改一处即可切换。

**`F2F_ORDER_NO_SEQ` 已创建**（2026-09-09，与 7 张表同库），DDL 归档在 `face-pay-server/src/main/resources/sql/f2f-schema.sql` 末尾，回滚为 `DROP SEQUENCE F2F_ORDER_NO_SEQ`。序列 `CYCLE` 到 9999 后回绕，因此格式类 `F2fOrderNo` 对序列取模 10000 保证长度恒定。注意 **Oracle 序列不支持 `COMMENT ON`**（`ORA-32594`），别再往 schema 里加该语句。

## 二十、`PAY_CHANNEL_CODE` 回填（2026-09-09 补齐）

**缺口**：旧实现在支付成功时写入渠道码并在查询接口回吐，新实现的 `markSuccess` 一开始没有该参数，导致 `F2F_PAYMENT.PAY_CHANNEL_CODE` 恒为空、TVM 查询接口的 `paymentChannelCode` 恒为 null。

**已改**：
- `F2fPaymentMapper.markSuccess` 增加 `payChannelCode` 参数，XML 里写成 `PAY_CHANNEL_CODE = NVL(#{payChannelCode}, PAY_CHANNEL_CODE)`——**传 null 时保留原值**，避免某次回调没带渠道码就把已有值抹掉。
- 三个成功分支都回填：支付回调取 `PayNoticeReqDTO.paymentVendor`；查询接口与过期收口取支付中心查询应答的 `paymentVendor`，经新增私有方法 `F2fTvmOrderService.markPaymentSuccess` 统一落库。
- `markPaymentSuccess` 吞掉 `DuplicateKeyException` 与 `updated == 0`（都表示「该订单已有成功尝试」），**不打断订单状态推进**——钱确实收了，订单必须置 `PAID`。

**副产品修复**：查询接口与过期收口此前只改 `F2F_ORDER`、完全不动 `F2F_PAYMENT`，支付尝试会永远停在 `INIT`。现在两条路径都会把该次尝试推到 `SUCCESS`。

**验证**：`mise exec -- mvn -o test -pl face-pay-server`，43 passed / 0 failed（真实 Oracle 写库用例含渠道码回填与查询回吐两条断言）。SQL 审计日志确认 `PAY_CHANNEL_CODE = NVL('0C', PAY_CHANNEL_CODE)` 实际执行且影响 1 行——**没被 Druid WallFilter 拦**。跑完 `F2F_ORDER` / `F2F_PAYMENT` 均 0 行，库已还原。

**注意（非缺陷）**：`SqlAudit` 打印 SQL 时按顺序把 `?` 替换成参数值，因此**参数值里含 `?` 的字段会让后续占位符错位**，日志中出现过 `checkoutCounternullorderNo=...` 这种拼歪的串。落库的真实值是对的（`PayCenterMessageFactory:106` 拼的是 `base + "?orderNo=" + ...`），**别据此判定 URL 拼错**。

## 二十一、重写进度台账（2026-09-09，按 URL 逐条）

**唯一进度依据是 Controller 的 `@PostMapping` 注解**（AGENTS.md §5.3 禁止另建汇总文件，本节是设计文档内的实现记录，不是派生报告）。

### 已实现并通过测试（TVM 14 条）

- `/itptvm/ci/tvm/requestGenSjtOrder` — 拉码下单
- `/itptvm/ci/tvm/requestPayResult` — 查询支付结果
- `/itptvm/ci/tvm/payNotice` — 支付中心回调（⚠️ 无验签，待人工复核）
- `/itptvm/ci/tvm/notiDeviceHeard` — 心跳。**旧实现只 return success 不落库**，现落 `F2F_DEVICE_STATUS`（MERGE INTO）
- `/itptvm/ci/tvm/notiTakeTicketResult` — 出票成功上报，幂等靠 `UK_F2F_REPORT_IDEM`
- `/itptvm/ci/tvm/notiTakeTicketFailResult` — 出票失败上报 + 差额退款
- `/itptvm/ci/tvm/requestRefund` — 设备主动退款（错误码族 **9999**）
- `/itptvm/ci/tvm/requestPayment` — 付款码支付（错误码族 **8999**），`F2fScanPayService`。**去掉了旧实现在 `@Transactional` 内 `Thread.sleep` 轮询最长 180 秒**，改由设备侧轮询；订单从统一 `F2F_ORDER` 查（旧实现查 `BOM_NO_CASH_ORDER`，TVM 自己下的单永远查不到）
- `/itptvm/ci/tvm/requestActiveTicket` — 二维码激活，`F2fTakeTicketService`。条件 UPDATE 抢占（旧实现是无条件 UPDATE，两台设备扫同一码都返回成功），未抢到回 **2008** 该订单已被其他设备激活
- `/itptvm/ci/tvm/requestTakeTicketAuth` — 取票鉴权。查询键改为 `ACTIVATE_DEVICE_ID`，未激活/查不到回 **2003**（旧实现回空订单，设备可能把 `singelTicketNum="null"` 当 1 张打）
- `/itptvm/ci/tvm/requestTopup` — 一卡通充值下单（`BIZ_TYPE=02`），`F2fTopupService`
- `/itptvm/ci/tvm/topupCardResultNoti` — 充值成功上报，`PAID → FULFILLED`（旧实现停在「支付成功」，日结退款任务无法区分「没充上」与「充上了没记」）
- `/itptvm/ci/tvm/topupCardFailNoti` — 充值失败上报 + 全额退款（`SOURCE_TOPUP_FAIL`）
- `/itptvm/ci/tvm/requestPayOrderDetail` — 支付中心反查订单详情。**直接绑 `@ModelAttribute` 不解 bizData**（支付中心以普通表单字段发 `orderNo`）；状态映射 `payCenterOrderStatus` 照搬旧口径，未知状态回**空串**，勿「修正」

### 已实现并通过测试（BOM 11 条，`/itpbom/ci/bom`）

- `notiDeviceHeard`、`requestGenNoCashOrder`、`requestPayment`、`requestGetPayResult`、`notiBusResult`、`notiTopupResult`、`requestOrderResult`、`requestTicketRefund`、`requestCardDataAnalyse`、`requestUpdateCardData`、`notiUpdateHceData`
- `F2fBomOrderService`：下单幂等靠 `UK_F2F_ORDER_DEV_SEQ`（CHANNEL+DEVICE_ID+DEVICE_SEQ），`DuplicateKeyException` → 回既有 `orderNo`；业务结果上报幂等靠 `UK_F2F_REPORT_IDEM`，失败触发 `SOURCE_BOM_ORIGINAL` 全额退款；`requestTicketRefund` 按张退并回写 `TICKET_STATUS=REFUNDING`
- `F2fHceService`（IF5A-01/03/09）：**远端 retCode 不再透传给 BOM**（BOM 只认 8xxx 族），统一收敛成 `8999` + 内嵌远端码的消息；改状态的操作在 `F2F_RESULT_REPORT` 留审计行（`ORDER_NO` 存 `cardId`）
- 错误码族沿用 BOM 既有的 `0000/8999/8001-8007`，`8003` 为参数非法

### 已实现并通过测试（APP 7 条，`/ci/app`）

- `requestOrder`、**`requestPaymentInfo`**（旧 Javadoc 写 `/requestPayInfo`，是注释笔误，真实 URL 以注解为准）、`requestPayResult`、`requestPreActiveOrderList`、`requestRefundTicket`、`requestRefundTicketResult`、`receiveRefundResult`
- `F2fAppOrderService`：`CHANNEL=01`、`BIZ_TYPE=03`、`TRANS_TYPE=03`；退款查询按 **`ORIG_ORDER_NO`** 查（旧实现拿 `orderNo` 当 `refundNo`）
- 错误码族 `0000/8001/8003/8999/9999`；`payInfo` 响应固定 `signType="00"`、`sign=""`——**响应确实不签名，是既有形态**
- 修掉旧实现 4 处缺陷：状态判定写成字面量 `"3"` 而非 `"FAILED"`；`orderNo` 当 `refundNo` 用；线程池内同步推 APP；`catch` 一切后统一回 `9999 请求异常`（现按 `RefundOutcome` 区分）

### 已实现并通过测试（运营后台 2 条，`/page/face-pay/orders`）

- `GET /page/face-pay/orders` — 分页查询。**必须给检索范围**（订单号 / 支付中心订单号 / 完整下单时间区间之一），否则回 `illegalParams`；`payCenterOrderNo` 用 `EXISTS` 子查询过滤，**NEVER 改成 JOIN**——一单多次支付尝试会把行数乘开、`total` 失真
- `POST /page/face-pay/orders/{orderNo}/refund` — 后台整单退款（`SOURCE_PAGE_MANUAL`）。可退状态白名单 `PAID/FULFILLED/FULFILL_FAILED`；重复退款靠扫 `selectByOrigOrderNo` 找同来源退款单判定（旧实现靠 `RSV2` 非空判定）；**请求体故意没有金额字段**，金额只从订单总额算
- 返回 `ResultVO`（管理后台形态），**不套设备侧 retCode 契约**

### 已建好的支撑服务

- `F2fRefundService` + `RefundCommand` / `RefundOutcome` — 7 种退款来源统一入口，幂等靠 `UK_F2F_REFUND_IDEM`；**对端未答留 INIT 不置 FAILED**，`reconcileRefund` 查询收口
- `F2fNotifyService` + `F2fNotifyJob` + `AppNotifyProperties`（前缀 **`f2f.notify.app`**）— 通知入队 + 扫表投递 + 指数退避（30s→600s）。**接收链路只入队，不同步推送**
- `F2fTicketIssueService` — 出票上报（TVM/BOM 共用，旧实现按 providerId 分流成两套代码）
- `F2fScanPayService`、`F2fTakeTicketService`、`F2fTopupService`、`F2fBomOrderService`、`F2fAppOrderService`、`F2fHceService`、`F2fPageRefundService`
- `F2fDeviceHeartbeatService`、`F2fDeviceRefundService`
- `legacy` 包（`LegacyOrderReader` + `F2fLegacyOrderMapper` + 两个 View）— 切换过渡期旧库只读旁路，开关 `f2f.legacy.enabled`，详见 §二.1
- `F2fChannel`（01-APP / 02-TVM / 03-BOM）、`F2fOrderNo.BIZ_TOPUP/BIZ_REFUND`

### 未实现 / 待办（按优先级）

1. **§十二 场景 4** — TVM 重发同一 `orderNo` 的比对，结论可能反向影响防重下单的设计
2. **Phase 0 建唯一索引：已暂缓**（用户 2026-09-09 裁决「先不要改旧表、旧应用」）。脚本留档在 `face-pay-server/src/main/resources/sql/legacy-phase0-ticket-refund-index.sql`，**NEVER 执行**，解禁需重新授权。**被接受的敞口**：按票退款的旧库判重只有 `LegacyOrderReader.hasTicketRefund` 的 COUNT，挡串行不挡并发——并发两笔同票退款会双双穿透到支付中心（支付中心侧是否再判重未核实）。**Phase 4 双写影子比对**同样受此裁决约束：影子比对若需要改旧应用来双写，不做；只能做「新服务读旧库 + 离线比对」这种不碰旧应用的形态
3. **切换步骤**：改路由前 3 分钟停止新下单，或明确接受「已下单未支付旧单收不到 payNotice」的 180 秒窗口（见 §二.1 最后一段）
4. **上线前必办**：`payNotice` 生产验签口径确认（测试环境已裁决不做）；订单号 20/22 位长度待 ACC 确认；`sign` 待签串待与 bestonepay 联调确认；**`application.yml` 的 `pay-center.private-key` 与 `jhm-key` 目前是内联明文**（用户 2026-09-09 为测试环境便利裁决），生产前 **MUST** 改回 `${ENV:}` + K8s Secret 注入（§5.2 敏感配置）——旧应用同样是明文，属同类既存缺陷，不因此免除
5. **部署未接线**：`face-pay-server/pom.xml` 没有 `activeByDefault` 的 remote profile，`mvn package` **不会**推镜像（与 §7 列出的那批模块不同）
6. **`NOTIFY_APP_PAY_RESULT_URL` 旧配置无对应项**（2026-09-09 核实：仓库 grep + `kubectl get deploy collect-pay -n itp` env 双查）：APP 通知地址只有三条——`notice-app-taketicketresult-url`、`notice-app-taketicketfailureresult-url`、`notice-app-refundresult-url`（集群 env 与仓库 `application.yml:36/37/39` 一致，均为 `dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/receive*`），没有支付结果通知。`PAY_RESULT` 是重写新增的第四种类型（`CK_F2F_NOTIFY_TYPE`、`F2fNotifyService.TYPE_PAY_RESULT`、`F2fNotifyJob:107` → `AppNotifyProperties.getPayResultUrl()`），旧实现从未发过。**留空的后果**：该类型只入队不投递，`F2fNotifyJob` 记「通知地址未配置」并退避重试至 `GIVEUP`，另外三类不受影响。**待办**：与 APP 侧确认是否真需要这条通知
7. **`pay.center.app-refund-notice-url` 集群值与仓库值不同，且它不是「我方出向地址」**（2026-09-09 实测，纠正此前记载）：集群 env 是 `http://58.56.166.170:48000/fep-app/ci/app/receiveRefundResult`（指向 fep-app，形态正确），仓库 `application.yml:33` 是 `http://58.56.166.170:48000/itptvm/ci/tvm/payNotice`（**过期值**）。用途见 `AppOrderServiceImpl:530-532`——它被塞进请求支付中心退款报文的 `notifyUrl` 字段，是**告诉支付中心往哪推**，与 `notice-app-refundresult-url`（`AppOrderServiceImpl:691`，我方 `doPostFormData` 出向）是两件事。新服务若要复刻这个 `notifyUrl`，**MUST** 取集群 env 值，**NEVER** 抄仓库 yml。另：`pay.center.pay-notice` 与 `query-refund-url` **不在集群 env 里**，线上生效的是 jar 内默认值
8. **`SERVICE_TICKET_URL` / `SERVICE_ACCOUNT_URL` 的真实值已确认**（2026-09-09 取自 collect-pay Deployment env）：`service.ticket.url=http://172.20.211.23:30014`、`service.account.url=http://172.20.211.23:30013`。仓库 `collect-pay-server/application.yml` 的 `127.0.0.1:9103` / `127.0.0.1:9098` 是 §8 第三类缺陷（值指向自身 Pod），**NEVER** 按仓库值填 face-pay 的 env
9. **心跳落库的三个配套项：用户 2026-09-09 裁决「暂不处理」**。心跳本身继续落库（`F2fDeviceHeartbeatService.mergeHeartbeat` → `F2F_DEVICE_STATUS`，旧实现不落库），但以下三项挂起，解禁需重新提出：
   - **`F2F_DEVICE_STATUS` 当前只写不读**：mapper 的 `selectByChannelAndDevice` / `selectHeartbeatTimeout` / `selectByChannel` 三个查询方法**无任何调用方**，controller 层零引用。设备在线状态没有出口，运营侧看不到。补一个 `/page/face-pay/` 设备状态列表接口即可兑现价值
   - **`deviceId` 无取值校验（被接受的敞口）**：设备接口全部不验签，而 `MERGE INTO` 对新 `deviceId` 走 INSERT，因此**任何网络可达方用随机 deviceId 刷心跳可无限撑大该表**。旧实现不落库，故这是重写新增的攻击面。缓解方式：按设备台账白名单校验，或至少校验格式（实测均为 8 位数字，如 `06220701` / `03520801`），不合规只回 `0000` 不落库
   - **`f2f.device.offlineTimeoutSeconds=300` 与实测心跳间隔不匹配**：该值按「1 分钟心跳 × 5 倍容忍」定，但 2026-09-09 实测 BOM 约 20~30 秒一次、TVM 约 60 秒一次，等于要漏 15 次心跳才判离线，掉线发现偏慢。建议值 120，属参数决策
   - 另：`HEARTBEAT_COUNT` 累加列同样无读取方，若不做心跳次数统计可省，优先级最低

### 已建好的 `@Scheduled` 任务（4 个，均在 `scheduler` 包）

- `F2fOrderExpireJob` — 二维码过期订单收口（方向 1 防重下单的兜底）
- `F2fRefundReconcileJob` — 退款收口，主动查支付中心退款查询接口。**没有它，「对端未答留 INIT」就是死状态**
- `F2fNotifyJob` — 扫 `F2F_NOTIFY_TASK` 投递 APP 通知，指数退避 30s→600s，超 `maxRetryTimes` 置 `GIVEUP`
- `F2fDeviceOfflineJob` — 心跳超时置离线，默认 300 秒窗口（1 分钟心跳 × 5 倍容忍）

四个任务都：不带 `@Transactional`、逐笔 try/catch、多副本下重复执行安全（靠状态白名单收敛）。

### 已修掉的旧实现缺陷（累计）

| 缺陷 | 旧行为 | 新行为 |
|---|---|---|
| 退款无幂等 | `doRefund` 无幂等键，重复调用重复退 | `UK_F2F_REFUND_IDEM` 三要素 |
| 退款失败被当成功 | `doRefund` 恒 `return true`，调用方据此写「已退款」 | `RefundOutcome` 区分 rejected / alreadyExisted / accepted |
| 出票上报无幂等 | 断网重传重复插主+子票记录 | `UK_F2F_REPORT_IDEM`，重传直接回成功 |
| 通知同步推送 | 设备请求线程内发 HTTP，失败置 `NOTICE_FAIL` 后无重试 | 落库入队 + 扫表退避重试 + `GIVEUP` |
| 差额退款算负数 | `actualNum > buyNum` 时退负数金额 | 应退张数非正即拒绝并告警 |
| 差额退款抛异常 | `transType` 非 01/03 时 `new BigDecimal("")` | 单价缺失即拒绝并告警 |
| `providerId` NPE | `request.getProviderId().equals("03")` | `F2fChannel.fromProviderId` + 缺省归 TVM |
| `actualTakeTicketNum` NPE | `Integer.parseInt` 裸调用 | `actualNum()` 返回 null → 回 2002 |
| 心跳不落库 | controller 直接 return success | MERGE INTO `F2F_DEVICE_STATUS` |
| 退款金额无上限 | 设备传多少退多少 | 超过订单金额即拒绝 |
| 出票不校验已支付 | 未支付订单也能记出票 | 白名单只允许 `PAID` |
| 事务内轮询 180 秒 | `getBomPayResult` 在 `@Transactional` 内 `Thread.sleep` 轮询（与 2026-08-26 生产事故同形） | 不轮询，一次查询即答，未明确回「处理中」由设备侧再问 |
| 二维码激活可被多设备抢到 | 无条件 `UPDATE`，两台设备扫同码都成功 | 条件 `UPDATE` + 0 行回 **2008** |
| 激活不落二维码三要素 | 未写 `QRCODE_GEN_DATE` / `RANDOM_FACT` | `activateForDevice` 一并落库，否则取票鉴权永远查不到 |
| 取票鉴权查错设备字段 | 按 `DEVICE_ID`（下单设备）查 | 按 `ACTIVATE_DEVICE_ID` 查 |
| 充值成功不推进状态 | 停在「支付成功」，日结退款任务无法区分「没充上」与「充上了没记」 | `PAID → FULFILLED` |
| BOM 下单无幂等 | 断网重传重复建单 | `UK_F2F_ORDER_DEV_SEQ` → 回既有 `orderNo` |
| HCE 远端错误码直接透传 BOM | BOM 只认 8xxx 族，收到陌生码无法处理 | 收敛成 `8999` + 内嵌远端码的消息 |
| APP 退款查询用错键 | 拿 `orderNo` 当 `refundNo` 查 | 按 `ORIG_ORDER_NO` 查 |
| APP 状态判定字面量错 | 比较 `"3"` 而非 `"FAILED"` | 统一走状态常量 |
| 后台重复退款判定不可靠 | 靠 `RSV2` 非空判定 | 扫 `SOURCE_PAGE_MANUAL` 退款单判定 |
| 切换后旧单退款无判重 | 旧库退款记录只在旧三张表里，新表 `UK_F2F_REFUND_IDEM` 看不到 | `F2fRefundService.refund` 一处闸门查旧库，命中即拒绝（§二.1） |

**验证**：`mise exec -- mvn -o test -pl face-pay-server` → **52 passed / 0 failed**（19 skipped 是需真实 Oracle 的写库用例）。**34 条 URL 全部接线完成**：TVM 14 + BOM 11 + APP 7 + 运营后台 2；旧库只读旁路（§二.1）已接线并有 8 条纯逻辑用例覆盖状态映射与宽松解析。

> **纠正此前记载**：本段原写「mapper XML 绑定全部通过」，**这是错的**——单测不加载 Spring 上下文、不解析 mapper XML，`F2fResultReportMapper.xml` 缺 `</update>` 一直存在却全绿。XML 层面的证据只能来自 `xmllint --noout` 或真实启动，见下节。


### 两处安全事项的当前裁决（2026-09-09）

**1. 入向验签：测试环境不做，用户已裁决。** `payNotice` 与全部设备接口沿用旧实现的「不验签」，测试阶段不再作为阻塞项。**上线前仍需确认生产口径**——该接口能按 `merchantOrderNo` 把任意订单标记为已支付，生产裸暴露的风险与测试环境不是一回事。

**2. 「同一网关两套 sign 待签串」说的是这件事**（2026-09-09 核实，不是猜测）：

两个模块打的是**完全相同的三条 URL**（`dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/` 下的 `payment/requestPay`、`payment/payQuery`、`refund/requestRefund`，`pay-sign-server/application.properties:84-86` 与 `collect-pay-server/application.yml:28-30` 字符串一致），但算 `sign` 的待签串规则不同：

- `collect-pay-server`（`SignUtils.buildSignData`）**签外层信封**：`LinkedHashMap` 固定 5 键、按插入序、不排序、不剔空值 —— `merchantNo=..&apiVersion=..&signType=..&charset=..&bizData=<整个Base64串>`
- `pay-sign-server`（`PayGatewayClient.buildSignSource:111-124`）**签 bizData 内部字段**：把 bizData 反序列化成 `TreeMap` 按键名字典序拼接、剔空值 —— `amount=600&merchantOrderNo=GT..&subject=..`；信封的 4 个字段**完全不参与**

两套没有交集：一个签信封、一个签内容。而两条链路都在生产跑通过（pay-sign 的免密扣款在补 `/v1` 后有成功记录，collect-pay 的 TVM 购票也在跑），**因此至少存在一种可能：其中一条的验签在网关侧实际未生效**（对该商户号关闭了验签，或验签失败仍放行）。

要问 bestonepay 的问题就一句：`/api/v1/payment/requestPay` 的 `sign` 以哪个待签串为准，是否按商户号区分规则。

**已执行的裁决（用户 2026-09-09：「参考 pay-sign-server，后续测试失败再回退」）**：`PayCenterSigner` 已切到 pay-sign-server 那一套——签 bizData 内部字段、`TreeMap` 键名字典序、剔空白值、嵌套结构按键排序序列化。

- 只改了「签什么」，**没改「发什么」**：`bizData` 仍是 Base64（两个模块本来就一致，见 `PayGatewayClient.buildRequest:98`），信封五字段照旧发送，只是不再参与待签串。
- 连带改动：`PayCenterSigner.sign(request, bizDataJson)` 多收一个入参——待签串要用 Base64 **之前**的原始 JSON，无法从 `request.getBizData()` 反推，因此 `PayCenterMessageFactory.envelope` 里把 JSON 单独留了一份。
- **回退方式**（联调失败时）：把 `buildSignSource` 换回 `merchantNo=..&apiVersion=..&signType=..&charset=..&bizData=<Base64>` 的信封五键拼接，并去掉 `sign` 的第二个入参。改动集中在 `PayCenterSigner` 一个类 + `envelope` 一处调用。
- 验证：44 passed / 0 failed。新增两条断言——待签串等于 `merchantOrderNo=F2F20260908001`（**信封字段一个都不出现**）、键名按字典序排且空白值被剔掉。此前误以为查询报文的键是 `orderNo`，实测是 `merchantOrderNo`，已按实测值订正。

**仍未验证的是对端是否接受**：本地只能证明「我们按 pay-sign 的规则算出了签名」，网关认不认要等联调。

### 双跑重放实测发现的新实现缺陷（2026-09-10，扫码购票链路）

用户在测试环境做了一次真实扫码购票，按 §22.8 把请求逐条重放到新服务，发现 4 个**单测与编译都发现不了**的缺陷。四个都已修，镜像 `1.0.4`。

| 缺陷 | 表现 | 根因 | 修法 |
|---|---|---|---|
| `F2fResultReportMapper.xml` 缺 `</update>` | pod `1/1 Running` 但每 30 秒刷 `Failed to load BaseExport beans`，接口全 404 | 早期日志里才有真因 `SAXParseException lineNumber:138 元素类型 "update" 必须由匹配的结束标记终止`；**单测不解析 mapper XML** | 补结束标记；对 9 个 mapper XML 跑 `xmllint --noout` |
| `other.web.unSafeUrlChars: null` | **全量请求 403** `{"msg":"check url unSafe"}`，warn 日志是 `unsafe char:`（空） | YAML 的 `null` 绑成空串 → `"".split(" ")` = `[""]` → `url.contains("")` 恒真（`FirstFilter.checkUrlSafe`）。旧应用给的是字符串 `"null"` | 改成 `unSafeUrlChars: "null"` |
| `totalTicketPrice` 由字符串变数字 | 响应 `"totalTicketPrice":200` 而旧实现是 `"200"` | 旧 `TvmPayOrder.totalPrice` 是 String 字段，新表 `ORDER_AMOUNT` 是 NUMBER，直接 `put` 就序列化成数字。对端收银台按 String 取值会解析失败 | `TvmResponses` 里 `String.valueOf(...)`，**A 类契约差异** |
| `F2F_TICKET.CREATE_TMS` 未赋值 | 出票上报第 1 次回 UUID `retCode`，`ORA-01400: 无法将 NULL 插入 ("CREATE_TMS")` | 该列 NOT NULL 且表上无默认值 | `saveTickets` 显式 `setCreateTms/setUpdateTms`，**NEVER 依赖数据库默认值** |

**第 5 个、也是最严重的一个：上报行先于票落库提交，导致票永久丢失且设备看到成功。**

`receiveTakeTicketResult` 原顺序是「插上报 → 插票」，两条 SQL 各自自动提交（该方法按 §5.2 不能带事务，失败分支要调退款）。上一条 `ORA-01400` 发生时：上报行**已提交**，票插入失败抛出 → 设备重传 → `insertReport` 撞 `UK_F2F_REPORT_IDEM` 直接回 `0000 成功`，`F2F_TICKET` 永远是空的。实测证据：`F2F_RESULT_REPORT` 1 行（ID=1、`PROCESSED=0`）、`F2F_TICKET` 0 行、设备侧第 2 次拿到 `{"retCode":"0000"}`。

修法是**把 `saveTickets` 挪到 `insertReport` 之前**（成功/失败两条上报路径都改），没有引入事务：

- 票插入本身靠 `UK_F2F_TICKET_LOGIC (TICKET_LOGIC_NUM, TRANS_DATE)` 幂等，重传重跑不会重复插；
- 插票失败时上报行不落库，设备重传能完整重跑一遍，不再被幂等闸门吞掉。

**通用结论（其它模块同样适用）**：**幂等锚点 MUST 最后写。**先写幂等键、后做实际业务动作，等于把「业务没做成」固化成「已经做过」——比不幂等更糟，因为它连重试的机会都拿掉了。

**验证（1.0.4，同一订单 `00202609100924150083`）**：先删掉那条孤儿上报行（原值已记录，还原 SQL 见下），再用原始 `bizData` 重放两次：

- 第 1 次 `{"retCode":"0000","retMsg":"成功"}`；`F2F_TICKET` 落 1 行（ID=2、`ISSUED`、`TICKET_PRICE=200`、`CREATE_TMS/UPDATE_TMS=09:37:00`）；`F2F_ORDER` `PAID → FULFILLED`
- 第 2 次同样 `0000`，且 `F2F_RESULT_REPORT` 仍 1 行、`F2F_TICKET` 仍 1 行——**幂等这次是因为正确的原因**
- 还原 SQL（测试环境删行留痕）：`INSERT INTO F2F_RESULT_REPORT (ID, REPORT_TYPE, ORDER_NO, CHANNEL, DEVICE_ID, ORDER_TICKET_NUM, ACTUAL_TICKET_NUM, REPORT_TMS, OPT_RESULT, OPT_RESULT_DESC, PROCESSED, RECEIVE_TMS, CREATE_TMS) VALUES (1,'TAKE_TICKET_OK','00202609100924150083','02','06220D01',1,1,'20260910092500','0','出票成功','0',TO_DATE('2026-09-10 09:24:47','YYYY-MM-DD HH24:MI:SS'),TO_DATE('2026-09-10 09:24:47','YYYY-MM-DD HH24:MI:SS'))`

**同链路已验证通过的部分**：20 位 orderNo `00202609100924150083`（用户 2026-09-10 裁决「按照旧的来」）、`totalTicketPrice:"200"`、`payNotice` 回 `{"code":"0","msg":"成功"}` 且状态机推进到 `PAID`、重复 `payNotice` 幂等、`requestPayResult` 回 `paymentResult:"SUCCESS"` / `paymentChannelCode:"03"`、`requestPayOrderDetail` 支付后 `orderStatus:"2"` 且带 `payDate`、`F2F_PAYMENT.PAY_STATUS=SUCCESS` 带渠道与支付中心单号、**旧表零变更**。

**待核实**：该订单 `F2F_ORDER.TRANS_TYPE` 为 null。它决定 §出票上报是否回推 APP（`TRANS_TYPE=03`）与差额退款的单价口径，**不能长期留空**，需回到 TVM 下单入口确认是设备没传还是新实现没落。

**旧应用的两处日志泄密（不改旧应用，仅记录）**：`SignUtils` 把完整 RSA 私钥打进日志；另有一行 `=======paramsStr========orderNo=...&key=...` 把 signKey 打出来且**不受日志级别控制**。日志由 VictoriaLogs 采集，等于密钥进了日志库。上线前需处置。

### 旧单退款实测（2026-09-10，用户裁决打旧应用）

用户当天在旧应用真实下单支付的那笔：`00202609100912085006`，2 元、支付宝（`CHANNEL=03`）、`STATUS=1 支付成功`、`PAYCENTER_ORDERNO=287589498912407552`。

**先打新服务，被拒（未出款）**：`POST /itptvm/ci/tvm/requestRefund` → `{"retCode":"9999","retMsg":"没有找到匹配的订单，请确认订单号是否正确"}`。

原因不是缺陷而是设计边界：`F2fDeviceRefundService.requestRefund` 用 `orderMapper.selectByOrderNo`（**只查新表**），没有走 `LegacyOrderReader`。这与 `F2fTvmOrderService:113-117` 的约束一致——旧库投影不在 `F2F_ORDER` 里，写链路用它会让后续 UPDATE 全部 0 行，而 0 行在本模块语义是「状态机拒绝」。

**由此暴露的迁移缺口：切换后旧单无法从新服务退款。**用户 2026-09-10 裁决：**旧应用保留内部退款入口，过完退款窗口再下线**——不给新服务加旧单退款分支、也不迁数据。连带约束：

- 切换时 **NEVER 直接停掉 `collect-pay`**，只把设备与 APP 流量摘走（改路由，见 §22.10），进程与 `/itptvm/ci/tvm/requestRefund`、运营后台 `/{orderNo}/refund` 保持可用；
- 退款窗口多长**需要业务/ACC 给口径**，这决定旧应用能在什么时候真正下线，属未闭合项。

**再打旧应用，退款成功（真实出款，用户已授权）**：

- 响应 `{"retCode":"0000","retMsg":"成功"}`，耗时 **6673ms**（同步等支付中心，`MoreInterceptor` 按 ERROR 记了一条慢请求）
- 支付中心 `refund/requestRefund` 回 `code=0`，`merchantRefundNo=RF20260910094812ba9808`，`channelRetCode=0000`
- `TBL_TVM_ORDER_REFUND` 落 1 行（`REFUND_STATUS='0' 退款中`），随后线程池 `executor-1` 主动查 `refund/refundQuery` 拿到 `status=SUCCESS`，把该行更新为 `'1' 退款成功`
- 证据来自 `SqlAudit` 打出的 insert / update 原文与 `fetchRowCount:1`（**当时 MCP 库连接已断，没做 DB 直查**）

顺带确认的一处契约：旧 `requestPayOrderDetail` 响应里 `"totalTicketPrice":"200"` 是**字符串**、`singleTicketNum` 是**数字**，与前面 `TvmResponses` 的修法一致。

**这次退款暴露的旧实现缺陷（4 条，均不改旧应用，仅记录）**：

| 缺陷 | 实测证据 | 影响 |
|---|---|---|
| 设备传的 `refundReason` 被整个丢弃 | 传入「双跑验证-旧应用基线退款」，发给支付中心与落库的都是配置值 `pay.center.refundReason`（`TvmCommonServiceImpl:97`） | 退款原因永远是同一句，无法区分退款场景；对账与客诉都查不出真实原因 |
| 该配置值本身是 unicode 转义串且被二次转义 | 出向 bizData 里是 `refundReason=\\u51FA\\u7968...`，落库 `REFUND_REASON` 也是 `\u51FA\u7968\u6570\u91CF\u4E0D\u8DB3` 字面量 | 支付中心侧看到的是乱码字面量而非中文 |
| `REFUND_TIME` 同一列两种格式 | insert 写 `'2026-09-10 09:47:50'`，update 写 `'2026-09-10T09:47:50'`（带 `T`） | 列是 VARCHAR2 所以不报错，但按时间排序/比较会错 |
| `channelRefundNo` 恒为 null | 支付中心退款响应与查询响应都没回渠道退款单号 | 与渠道对账缺关键字段 |

新实现对应行为：`refundReason` 原样落 `F2F_REFUND.REFUND_REASON`（**与旧实现不同，属 B 类落库差异，已知并保留新行为**——把设备传的原因丢掉没有任何好处）；时间列是 `DATE`/`TIMESTAMP` 类型，不存在格式漂移；退款收口由 `F2fRefundReconcileJob` 扫表完成，不用线程池。

### 过期收口无限轮询（2026-09-10 发现并修复，镜像 1.0.5）

**现象**：`F2fOrderExpireJob` 每 30 秒对同一笔订单 `F200202609100914540082`（22 位旧格式，1.0.3 之前那版生成）调一次支付中心 `payQuery`，恒回 `code=9999 未找到数据`，恒打 `过期收口未拿到支付中心结果，本轮不动状态`，**持续 40 分钟没有出口**。

**根因是把两种结局混为一谈**。原代码：

```java
if (result.isTransportFailed() || !result.isSuccessCode()) {
    return false;   // 一律当作「没拿到结果」，下轮再试
}
```

但 `PayCenterResult` 的类注释本来就把三种结局分开写清楚了：传输失败（不知道，NEVER 判 FAILED）、**业务失败（`code != 0`，对端明确拒绝，可判 FAILED）**、业务成功。这里把「对端明确说没有这笔单」当成了「对端没答上来」——而前者是确定性结论：支付中心没有这笔单，等于用户从未支付，可以直接收口。

**改法两层**，`F2fTvmOrderService` + `F2fOrderMapper` + `F2fOrderExpireJob`：

1. **主修**：`isTransportFailed()` 才返回 false 等下轮；`!isSuccessCode()` 直接 `updateStatus(... "EXPIRED", "支付中心无此订单，判定未支付")` 并返回 true。
2. **兜底**：扫表 SQL 加 `EXPIRE_TMS >= #{earliestExpireTms}` 下界（`f2f.order.expireGiveUpHours`，默认 24），超窗的单不再被扫、不再外呼；另加 `countStaleExpired` 每轮计数，`> 0` 时打 ERROR 让问题可见而不是静默消失。

这一层等价于 `F2fRefundReconcileJob` 的 `RETRY_TIMES < 20`——**`F2F_ORDER` 没有重试计数列，用时间窗表达同一个闸门**，代价是不需要 DDL。

**通用结论**：**每个「状态不明就下轮再试」的分支 MUST 有出口**，要么靠重试计数、要么靠时间窗。本项目 4 个 `@Scheduled` 里 `F2fRefundReconcileJob`（`RETRY_TIMES < 20`）与 `F2fNotifyJob`（`maxRetryTimes` + `GIVEUP`）本来就有，只有过期收口漏了；**新增扫表补偿任务 MUST 先确认出口条件**。

**验证（1.0.5，实测）**：
- 部署后第一轮即 `过期收口 支付中心明确无此订单，置 EXPIRED, orderNo=F200202609100914540082, code=9999`，`候选=1, 已收口=1, 待重试=0`
- 之后 90 秒窗口内 **`payQuery` 出现 0 次**、无候选、无外呼——轮询彻底停止
- 单测 52 passed / 0 failed；9 个 mapper XML 过 `xmllint --noout`（这一步已固化为改 XML 后的必做项）

### APP 单程票购票双跑重放（2026-09-10 17:0x）

用户在 APP 上真实买了 5 张单程票（入 0121 / 出 0125 / 单价 200 分 / 合计 1000 分），从旧应用 `BodyCacheFilter` 日志取到原始报文后重放到新服务。**这是 APP 侧（`/ci/app/**`）第一次跑真实报文**，此前只验过 TVM。

旧应用原始两步（17:00:53 / 17:00:54）：`/ci/app/requestOrder` → `orderNo=00202609101700533755aa1b`；`/ci/app/requestPaymentInfo` → 支付宝 app pay 报文。

重放到新服务（1.0.5）：两步都 `retCode=0000`，`F2F_ORDER` 落 `CHANNEL=01 / BIZ_TYPE=03 / TRANS_TYPE=03 / ORDER_AMOUNT=1000 / TICKET_NUM=5 / ENTRY 0121 / EXIT 0125 / THIRD_USER_ID=00522943 / ORDER_STATUS=PAYING`，`F2F_PAYMENT` 落 attempt 1 `PROCESSING / PAY_AMOUNT=1000 / RET_CODE=0`。

**最有价值的结论：支付中心接受了新服务的签名。** `requestPaymentInfo` 真调了 `payment/requestPay` 并拿回完整支付宝报文（`out_trade_no=287619115556700160`、`total_amount=10`、`subject=APP单程票购票`、`body=地铁单程票` 与旧应用逐字一致）。§二十一「仍未验证的是对端是否接受」这一项**至此闭合**——`PayCenterSigner` 切成 pay-sign-server 那套（签 bizData 内部字段、`TreeMap` 字典序、剔空值）是对端认的。此前只有 `payQuery` 回业务码的间接证据，现在是 `requestPay` 成功创建支付单的直接证据。

**发现的差异（3 条）**：

| # | 差异 | 旧 | 新 | 判定 |
|---|---|---|---|---|
| 1 | **APP orderNo 长度与构成** | **24 位**：`00` + `yyyyMMddHHmmss` + **8 位随机 hex**（`00202609101700533755aa1b`） | **20 位**：`00` + `yyyyMMddHHmmss` + 4 位序号（`00202609101702550084`） | **A 类，待裁决**。用户 2026-09-10「按照旧的来」那次裁决讨论的是 **TVM 的 20 位**，当时未察觉 **APP 侧旧格式根本不是 20 位**。若 APP / 支付中心对该字段有长度或格式假设，这会炸 |
| 2 | 支付中心订单号在「已发起支付、未支付成功」阶段的可见性 | 下单即落 `TBL_TVM_APP_ORDER.MERCHANTORDERNO=287618970906689536`（**注意旧表列名语义倒置**：支付中心订单号落在 `MERCHANTORDERNO`，而 `PAYCENTER_ORDERNO` 恒空） | `F2F_PAYMENT.PAY_CENTER_ORDER_NO` 此阶段为 null，要等 `payNotice` 回调或 `payQuery` 收口才由 `markSuccess` / `markPaymentSuccess` 回填 | **B 类，风险低但需记**。退款要求订单已支付、届时已回填，故不影响退款；影响的是支付中间态的对账与排障关联键 |
| 3 | `PAY_CHANNEL_CODE` 未落库 | 同样为 null | 同样为 null | **新旧一致，不是新增缺陷**。请求里明确传了 `payChannelCode=03`，两边都没落——旧实现的既有缺口 |

**新发现的一个缺口（新旧都有，需业务确认）**：APP 单程票订单 `EXPIRE_TMS` 为 null，而 `F2fOrderExpireJob` 的扫表条件是 `EXPIRE_TMS IS NOT NULL`，因此**这类订单下单后不支付会永久停在 `PAYING`，没有任何任务收口**。旧实现同样没有 APP 未支付关单任务（`refundAppNotTakeTickets` 管的是「已支付未取票」，不是未支付）。支付宝报文里 `timeout_express=3m` 说明支付窗口是 3 分钟，因此技术上可以按 3 分钟设 `EXPIRE_TMS` 让现有收口任务接管——但这是**旧实现没有的新行为**，按同契约重写原则不擅自加，**待业务确认后再定**。

**旧表零变更核对**：`TBL_TVM_APP_ORDER` 从 53 行变 54 行，增量那条是**用户 17:00:53 真实下单由旧应用自己写入的**，不是重放造成——重放全程只打 `face-pay-server`，未触碰旧应用任何写接口。

### BOM 票卡分析 / 更新双跑（2026-09-10 17:2x，只读部分已验，写操作未验）

用户在 BOM 上连做了 8 步真实操作（卡 `0178885088135717` 与 `0426091000000013`），从旧应用 `BodyCacheFilter` 取到完整报文序列：

- `notiUpdateHceData` ×2（`adviceOpt=018` / `005`，卡 `0426091000000013`）→ 均 `0000`
- `requestCardDataAnalyse` ×3（`updateType=00` / `01` / `00`）→ 均 `0000`，可见状态推进 `cardStatus 05 → 04`、`lastTikcetTransSeq 10 → 11`、`lastStationCode 0245 → 0218`
- `requestUpdateCardData` ×3：`adviceOpt=020` 回 **`8001 票卡状态不允许此操作: codeStatus=05, adviceOpt=020`**（拒绝分支）；`adviceOpt=018` / `005` 回 `0000` 且 `cardData=null`

**只读接口同刻双打，两边逐字一致。** 对同一张卡、同一时刻分别打旧应用与新服务的 `requestCardDataAnalyse`（`updateType=00` 与 `01` 各一次），响应字段全等：

```
cardStatus=08  lastLineCode=06  lastStationCode=0622  lastUpdateDate=20260910172130
lastTransAmout="0"  lastTikcetTransSeq="12"  transAmount="0"  managerCode=""
cardIssueDate=""  msisdn=""  providerId="03"  adviceOpt=["000"] / ["018"]
```

三个既有拼写错误字段全部保留：`lastTransAmout`（少 n）、`lastTikcetTransSeq`（Tikcet）、`adviceOpt` 是**数组**而非字符串。只有 JSON 键顺序不同——fastjson `JSONObject` 无序，已确认非契约。

**写操作为什么没验（这是一条需要写进 §22.8.2 分级判据的新情况）**：

`F2fHceService.requestUpdateCardData` / `notiUpdateHceData` 是**纯转发到 ticket-server**（`ticketClient.requestUpdateCardData(remote)`），票卡状态存在 **ticket-server 的表**里。新旧两个服务**共用同一个 ticket-server、同一张票卡表**——这不是 `F2F_*` 新表隔离的场景。因此：

> **重放判据不能只看「是否出款」，还要看「写入目标是否为新表」。** BOM 的 HCE 写操作不出一分钱，但它改的是**新旧共享的票卡状态**，重放等于把真实票卡再推进一次状态、不可逆。这类接口 MUST 归**红色**，与「会出款」同级。

连带事实：卡 `0178885088135717` 现已被用户那 8 步推到 `cardStatus=08` / `seq=12`，**前置状态已不匹配当时的 05 / 04**，即便重放也多半直接落到 8001，验不到成功路径。

**用户 2026-09-10 裁决：写操作暂不验，先存档只读结论。** 台账状态：

- `requestCardDataAnalyse`（`updateType=00` / `01`）— **已双跑验证，逐字一致**
- `requestUpdateCardData`、`notiUpdateHceData` — **未验证**。将来要验 MUST 满足其一：①用一张专用测试卡跑完整「分析→更新→再分析」；②在用户操作后**立刻**抓报文重放（前置状态最接近）；③只对比 8001 拒绝分支（零副作用，但需先在旧应用确认确实会被拒）

### BOM 售票（providerId=03）补实现与端到端重放（2026-09-10 20:1x，镜像 1.0.8 → 1.0.9）

BOM 卖单程票走的是 **TVM 的 URL**（`/itptvm/ci/tvm/*`），靠 `providerId=03` 分流，不在 `/itpbom` 下。
新服务原先在这里直接回 `2999 BOM渠道下单尚未接入`（功能缺口），本次补齐并重放到 FULFILLED。

实现要点（`F2fTvmOrderService.createBomSaleOrder`）：

- `CHANNEL='03'`、`BIZ_TYPE='01'`、`ORDER_STATUS=CREATED`，**不碰支付中心、不出二维码**，响应只回 `orderNo`（BOM 族 `0000`/`8999`），逐字对齐旧 `BomOrderServiceImpl.requestBomSaleOrder:189`（三张表 INSERT 后即返回，全程无外呼）。
- **旧实现三张表里没有任何一列存 `'03'`**，渠道只存在于路由分支里、落库后无从分辨；新表用 `CHANNEL` 记住它。
- `TRANS_TYPE` 留空：新表该列的语义是 **BOM 非现金的设备 transType**（`02/03/04/05/06/22/2A/2B/42`），与旧 `TBL_TVM_ORDER_PAY_PRE.TRANS_TYPE='04'`（`BusinessTypeEnum`）**不是同一套码**。写 `'04'` 会和「未出站更新」撞码；BOM 售票由 `CHANNEL='03' + BIZ_TYPE='01'` 唯一确定，与 TVM 售票单同样留空，口径一致。
- `EXPIRE_TMS` 新增 `f2f.order.bomSaleExpireSeconds`（默认 1800）。**不能复用 `qrcodeExpireSeconds`（180 秒）**：BOM 无二维码，下单到扫码是人工操作；也 **NEVER 留 null**——APP 单已踩过「`EXPIRE_TMS` 为空 ⇒ `F2fOrderExpireJob` 永远扫不到 ⇒ 弃单永久停在 CREATED」。
- 校验提到 controller 分流之前：两渠道必填项相同，旧实现只在 TVM 分支校验，BOM 缺参会在 `new BigDecimal(null)` 抛 NPE、退化成 UUID retCode。校验失败仍回 TVM 族 `2002`（同 URL 同文案）。

#### 重放实测发现的两个新缺陷

| # | 级别 | 现象 | 根因 | 处置 |
|---|---|---|---|---|
| 1 | **A** | `requestPayment` 回 UUID retCode，`ORA-12899: PAYMENT_VENDOR 值太大 (实际 18, 最大 8)` | **设备侧 `paymentCode` / `paymentVendor` 的命名与语义是反的**：`paymentCode` 其实是渠道码（`03`），`paymentVendor` 其实是 18 位用户付款码。旧实现在 `BomOrderServiceImpl:272` → `PayCenterCommon:96,103` 做了交叉（设备 `paymentCode`→网关 `paymentVendor`，设备 `paymentVendor`→网关 `authCode`），新实现照名字直连，既撑爆 `VARCHAR2(8)`，外发报文也会把付款码当渠道码 | 1.0.9 修：`F2fScanPayService` 落库与外发都按语义交叉 |
| 2 | B | 订单已 `FULFILLED` 但 `FULFILL_TMS` 为 null | 履约推进只改状态、未回填时间戳 | 1.0.10 修：`F2fOrderMapper.updateStatus` 在 `toStatus='FULFILLED'` 时写 `FULFILL_TMS = NVL(FULFILL_TMS, SYSTIMESTAMP)` |

**修 `FULFILL_TMS` 为什么放在 mapper 而不是各 service**：推进到 `FULFILLED` 的调用点有四处（`F2fTicketIssueService`、`F2fTopupService` 两处、`F2fBomOrderService`），逐个加必然漏一个，而漏掉的那条链路的缺失只会在对账时才被发现。用 `NVL` 保证幂等——重复上报若已推进过，不覆盖首次履约时间。**实测**：1.0.10 新订单 `00202609102025470086` 的 `FULFILL_TMS=20:25:48`（与 `PAID_TMS` 同秒），1.0.9 跑的 `00202609102013250085` 仍为 null，对比确认生效。

**新规则：跨系统字段名 NEVER 当作语义依据。** 迁移时凡是「对端字段名与本地列名同名」的映射，MUST 回到旧实现看它实际把值放进了哪个位置，尤其是支付报文里 `vendor` / `code` / `authCode` 这组极易互换的名字。本例中列宽约束（8 位）恰好挡住了错值，否则会静默发出一笔字段错位的扣款。

#### 端到端结论（订单 `00202609102013250085`，1 分，`0622→0121`）

- `requestGenSjtOrder` → `{"retCode":"0000","retMsg":"成功","orderNo":"00202609102013250085"}`，与旧应用同形（20 位 `00`+时间戳+4 位序列，无 `payUrl`）；落库 `CHANNEL=03 / BIZ_TYPE=01 / CREATED / EXPIRE_TMS=+30min`，**无 `F2F_PAYMENT` 行**。
- `requestPayment`（1.0.9）→ `0000 / ORDERED / 支付中心未返回结果，请稍后查询`。落库 `PAYMENT_VENDOR='03'`、`AUTH_CODE=288196526786014049`、`PAY_STATUS=UNKNOWN`、`COST_MS=30012`，**订单仍 `CREATED`**——「对端没答上来 NEVER 写 PAY_FAILED」按设计生效。
- **支付中心 `payment/requestPay` 在 scan 场景 30 秒超时**（`HttpTimeoutException`；重放用的是过期的一次性付款码）。旧应用 `bom.payTimeOut=180` 说明这条链路本就预期长达 3 分钟，因此 `pay.center.read-timeout-ms=30000` 对付款码支付偏短——**待定项**：是否为 SCAN 场景单独放宽读超时，需与支付中心确认其同步应答上限。
- 用 `payNotice`（`status=SUCCESS`）推成 `PAID`（`code=0`），再 `notiTakeTicketResult`（真实票 `001707310D010CC7`）→ `0000`，订单 `FULFILLED`、`F2F_TICKET` 一行 `ISSUED`、`F2F_RESULT_REPORT` 一行 `TAKE_TICKET_OK / CHANNEL=03`。出票上报的 BOM 分支无需分流，`channelOf` 记对渠道码即可，与类注释一致。

还原 SQL（如需清理本次构造数据）：
```sql
DELETE FROM F2F_RESULT_REPORT WHERE ORDER_NO = '00202609102013250085';
DELETE FROM F2F_TICKET WHERE ORDER_NO = '00202609102013250085';
DELETE FROM F2F_PAYMENT WHERE ORDER_NO = '00202609102013250085';
DELETE FROM F2F_ORDER WHERE ORDER_NO = '00202609102013250085';
```

### BOM 自检报文双跑：「订单不存在」retCode 从 8999 纠正为 8006（2026-09-11，镜像 1.0.11）

抓 `collect-pay-server` 日志时发现 BOM 设备除业务报文外还会发**自检报文**：`orderNo` 是二十个 `0`，接口必然走「订单不存在」分支。这类报文**零副作用、可无限重放**，是校对错误码族的理想样本——本次就是靠它发现了一处契约退化。

双跑对比（同一份自检报文，旧 = `collect-pay-5466475cb4-vljwx`，新 = 1.0.10）：

- `notiTopupResult` → 旧 `8999 充值订单不存在` / 新 `8999 充值订单不存在`，**逐字一致**
- `notiBusResult` → 旧 **`8006`** / 新 **`8999`**，retMsg 相同但 **retCode 不一致**

顺着这条线核对旧实现全部「订单不存在」分支，得到 BOM 域的真实码表：

- `requestPayment`（`BomOrderServiceImpl:256`）→ `BomPayCodeEnum.ORDER_NO_ERROR` = **8006**
- `requestGetPayResult`（`:359`）→ **8006**
- `notiBusResult`（`:447`）→ **8006**
- `requestTicketTRefund`（`:1544`）→ **`9999`**，BOM 域仅此一处，**NEVER 跟着改成 8006**
- `notiTopupResult` → `8999`，文案是「充值订单不存在」而非「订单号错误…」

而新实现这五处**一律用了 `BomResponses.failMessage`（8999）**。`BomResponses` 类注释里明明写着「8006 订单号错误 … 旧实现只实际用了 8003 与 8006」，实现却没照着走——**注释写对了、代码写错了**，且单测与编译都发现不了。

修法（1.0.11）：`BomResponses` 新增 `CODE_ORDER_NO_ERROR = "8006"` 与语义化方法 `orderNotFound()`，三处改为调它；退款那处显式写 `fail("9999", ...)`。**NEVER 把这四处合并成一个通用方法**——它们的码本来就不同。

实测四条全部对齐旧应用：`notiBusResult` / `requestPayment` / `requestGetPayResult` 回 `8006`，`notiTopupResult` 保持 `8999`。

**新规则：错误码族的每一个分支都要逐条对旧实现，NEVER 用「同族默认失败码」一把梭。** 本模块四套 retCode 族（TVM 2xxx / BOM 8999 / BOM-8006 / 退款 9999）本身就是既有契约的历史包袱，族内还有分歧。判据是「设备侧会不会按这个码走不同分支」——`8006` 对设备意味着「订单号打错了，提示重输」，`8999` 意味着「通用失败」，混淆会让设备给出错误提示。

**连带方法论：BOM 自检报文是免费的契约测试集。** 后续验任何 BOM 接口，先从日志里捞一份 `orderNo` 全 0 的自检报文双跑一遍，零风险且能覆盖拒绝分支——比构造业务数据便宜得多。

### 端点清单全量 diff：34 个设备接口零缺口，但漏了 `/internal/recon/export`（2026-09-11）

把两个模块的 controller 映射全量导出对比（脚本见下），而不是靠日志抽样——**日志只能覆盖「恰好被调过的」接口，旧应用 pod 频繁重启，一个窗口内往往只有心跳**。

```
R=<repo>; for mod in collect-pay-server face-pay-server; do
  (cd $R/$mod/src/main/java && for f in $(grep -rlE '@(Rest)?Controller' .); do
    base=$(grep -E '^[[:space:]]*@RequestMapping\("' $f | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
    grep -E '^[[:space:]]*@(Post|Get|Request)Mapping\("' $f | sed -E 's/.*"([^"]+)".*/\1/' \
      | while read m; do [ "$m" != "$base" ] && echo "$base$m"; done
  done | sort -u > /tmp/ep-$mod.txt)
done
comm -23 /tmp/ep-collect-pay-server.txt /tmp/ep-face-pay-server.txt
```

**`^[[:space:]]*` 这个锚是必须的。** 第一版没加，把 `//    @PostMapping("/requestPay")` 这类注释掉的映射也算成了端点，凭空多出 4 个假缺口（`/ci/app/payQuery` / `refundQuery` / `requestPay` / `requestRefund` —— 旧应用 `CollectPayController` 里 IF8A-09/10/12/13 整段是注释）。**统计端点 MUST 排除注释行**，否则结论直接错。第二版还踩了「`cd ../../..` 退错层级导致第二个模块用了第一个模块的目录」，两个文件内容相同、diff 干净——**「diff 为空」也可能是自己跟自己比出来的**，MUST 顺带打印两侧计数并确认不相等才可信。

修正后的结论：**旧 34 个端点 vs 新 33 个，唯一缺口是 `/internal/recon/export`**，34 个设备 / APP 接口一一对应，无遗漏、也无多余。

#### 这个缺口不是「忘了写」，而是蓝绿切换的一个真实卡点

`/internal/recon/export` 是日终对账链路里 `recon-server` 拉 collect-pay 分片数据的入口，**不在本次重写范围内**（重写目标是 34 个设备接口），但它跑在同一个 Deployment 上，而且**日志里 2026-09-11 已有 3 次真实调用**——recon-server 已部署并在拉数。

它读的是**旧四张表**：`TBL_TVM_ORDER_PAY` / `TBL_TVM_ORDER_TOPUP` / `TBL_TVM_APP_ORDER` / `TBL_BOM_ORDER_PAY`。于是切换有两条路，**两条都有问题**：

- 把 `collect-pay-c23ku-svc` 的 selector 指向 face-pay-server ⇒ recon 调进来 **404**，对账 collect-pay 源直接失败。**这条反而安全**，因为会报错、会被发现。

> **2026-09-15 后记**：本节这个卡点**没有变成阻塞项**，因为实际切换走的是改路由（§22.10）而不是改 selector —— `recon-server` 直连 `collect-pay-c23ku-svc:30024`、不经网关，切换后它仍打旧服务，对账链路一行未动。**但迁移缺口本身仍然存在**：`face-pay-server` 至今没有 `/internal/recon/export`，所以切换后新产生的 `F2F_*` 订单**完全不进对账**。用户 2026-09-15 裁决「切完再考虑对账数据源归属」，即**明确接受这段账期缺口**，解禁条件是补该端点或由 recon 侧按账期决定拉哪一边。
- 保留旧应用专供 recon（另给它一个 Service）⇒ 接口在、能返回数据，但**新订单已经写进 `F2F_*`、旧四张表不再增长**，对账文件里 TVM / BOM 相关段位会**静默归零**。**这条危险**：没有任何报错，只有对账数字不对，且要等 ACC 那边发现。

**结论：切换前 MUST 先决定对账数据源怎么迁，NEVER 只切设备流量就宣布完成。** 备选方案（未实施、未评审）：在 face-pay-server 补一个同路径 `/internal/recon/export`，按同样分片契约读 `F2F_*`；切换过渡期两侧并存，recon 侧按账期决定拉哪一边，或两边都拉后合并。选哪条要 recon 域的人一起定，见 `docs/business/recon.md`。

## 二十二、旧应用 → 新应用迁移工作方法

本节是从 `collect-pay-server` → `face-pay-server` 这次迁移中**实际踩出来**的方法，不是理论流程。每一步都带「门禁」——即**用什么证据证明这一步真的做到了**。之所以强调门禁：本次迁移有两次「自认为做完了」被实测推翻（mapper XML 声称绑定通过、`app-refund-notice-url` 声称是旧应用错配），两次的共同原因都是**用推断代替实测**。

其它模块要做同类重写时，直接按本节执行；若届时被复用超过一次，再抽成独立文档。

### 22.0 定性：先判断是「重构」还是「同契约重写」

- **同契约重写**：对外 URL、响应键名、错误码族、中文提示**一个字都不能变**，内部实现与表结构全部重做。适用于旧实现缺陷密集、增量改造反而更危险的情况。
- **重构**：保留表与主流程，逐点修缺陷。
- **判据**：如果「要改的缺陷」里有幂等、状态机、事务边界这类**结构性**问题，选重写；只有零散 NPE、字面量写错，选重构。
- **门禁**：定性结论写进文档并明确「新表 + 新服务 + 不碰旧表旧应用」的边界，后续所有争议以此裁决。本次的定性见 §一。

### 22.1 契约基线：只认代码，不认文档

- 逐 URL 抽取：`@RequestMapping` + `@PostMapping` 拼出完整路径，**MUST** 与旧应用逐字符一致（本次两侧都是 `/itptvm/ci/tvm`、`/itpbom/ci/bom`，`server.servlet.context-path` 两侧都没配）。
- 逐字段抽取请求 DTO：**错别字是契约**，`singelTicketNum` / `takeTickeDate` / `transAount` / `tranDate` / `lastTransAmout` / `lastTikcetTransSeq` / `operaterId` 全部原样保留。改成正确拼写等于改契约。
- 逐错误码抽取：本域四套 retCode 族**并存且不能统一**（TVM 2xxx；TVM-requestRefund 9999；BOM 0000/8999/8001-8007；APP 0000/8001/8003/8999/9999）。统一错误码是重写中最常见的破坏性「改进」。
- 校验顺序与提示语也是契约：同一入参在旧实现里先校验哪个、失败回什么中文，设备侧可能按文案分支。
- **门禁**：每个接口都能在旧代码里指出「响应体这几个键从哪一行 put 进去」。做不到就是没抽完，**NEVER** 凭甲方接口文档补齐——文档与代码不一致时以代码为准（AGENTS.md §4 开头）。

### 22.2 新表设计与旧表映射

- 新表全部换前缀（本次 `F2F_*`），与旧表零交集，从物理上保证「不碰旧表」。
- 同时产出**旧表字段映射表**（本次 §十四）：新表每列来自旧库哪张表哪一列。这张表是后面「旧库只读旁路」的输入，不是文档摆设。
- 幂等键在建表时就定死（唯一索引），**NEVER** 留到代码里用「先查再插」实现。
- **门禁**：建表脚本 + 回滚脚本同时提交（本次 §十七）；映射表里每一列都有来源或明确标注「新增列，旧库无对应」。

### 22.3 编码期硬约束（本项目专有，违反必翻车）

按 AGENTS.md §5.1 / §5.2 执行，重写场景里最容易踩的四条：

- SQL 正文**不能有注释**（Druid WallFilter `commentAllow=false`，语句静默失效且只在 Oracle 暴露）。注释写在 mapper XML 的 `<!-- -->` 里。
- `@Transactional` 内**不能有 RPC**，也不能提交通知任务（2026-08-26 生产事故同形）。重写时「顺手把调用挪进事务」是高频误操作。
- 状态机用**白名单**：`updateStatus(orderNo, 允许的前置状态集合, 新状态)`，返回 0 行即拒绝。
- 幂等靠**唯一索引 + `DuplicateKeyException`**，不靠先查再插。
- **门禁**：`grep -rn '\-\-' <module>/src/main/resources/mapper/` 无命中（排除 XML 注释内）；每个 `@Transactional` 方法体内无 `Client` / `WebClient` / `http` 调用。

### 22.4 构建自检门禁（本次两个缺陷都该在这里被挡住）

单测通过 ≠ 可启动。**MUST** 在推镜像前跑完这四项：

1. `mise exec -- mvn -o test -pl <module>` — 编译 + 单测
2. **`xmllint --noout <module>/src/main/resources/mapper/*.xml`** — mapper XML 良构性。本次 `F2fResultReportMapper.xml` 缺一个 `</update>`，**编译和 52 个单测全部通过**，只在容器启动时炸 `SAXParseException`。单测不解析 mapper XML，这个洞必须靠 xmllint 补
3. 只读旁路类 `grep -c -iE "<insert|<update|<delete"` **必须为 0** —— 证明「不写旧表」不是口头承诺
4. 配置项逐个核对（见 22.5）
- **门禁**：四项全绿才推镜像。**NEVER** 用「单测通过」代替「能启动」。

### 22.5 配置迁移：三处对照，缺一不可

这是本次两个线上级缺陷的来源，也是最容易被跳过的一步。**MUST 三处并列比对**：

| 来源 | 取法 | 陷阱 |
|---|---|---|
| 旧应用仓库 `application.yml` | 直接读 | **可能是过期值**。本次 `app-refund-notice-url` 仓库写 `/itptvm/ci/tvm/payNotice`，集群里其实是 `/fep-app/ci/app/receiveRefundResult` |
| 旧应用集群 Deployment env | `kubectl get deploy <name> -n itp -o jsonpath='{range .spec.template.spec.containers[0].env[*]}{.name}={.value}{"\n"}{end}'` | **这才是线上生效值**（env 覆盖 jar 内同名键）。env 里没有的键才落到 jar 默认值 |
| 新服务 `application.yml` + 待建 env | 逐键映射 | 新增键要确认旧实现是否真需要 |

逐键三态分类：**①旧有新有**（取集群 env 值）；**②旧有新无**（漏实现，补）；**③旧无新有**（重写新增，确认是否真需要——本次 `NOTIFY_APP_PAY_RESULT_URL` 属此类，且无任何生产者，结论是留空）。

两个具体坑：

- **YAML 裸 `null` 会被绑成空串**。本次 `other.web.unSafeUrlChars: null` 导致 `"".split(" ")` = `[""]`，而 `url.contains("")` 恒为 true，**所有请求一律 403 `{"msg":"check url unSafe"}`**，warn 日志打的是 `unsafe char:`（空）。旧应用是 env 字符串 `"null"`，行为完全不同。凡是「照抄旧应用 env 值」的键，**MUST 带引号**。
- **一个键在两侧的语义可能不同**。`app-refund-notice-url` 是塞进出向报文 `notifyUrl` 告诉支付中心往哪推，`notice-app-refundresult-url` 才是我方 POST 的地址。**MUST 看调用点**（本次是 `AppOrderServiceImpl:530` vs `:691`），不能按键名猜。
- **门禁**：产出一张「旧键 → 新键 → 取值 → 来源（仓库/集群env/jar默认）」的对照表，三态分类无遗漏。

### 22.6 部署到测试环境

- 新服务的 jkube `remote` profile **不设 `activeByDefault`**（与其它模块相反，本次是有意的）：切流量前不应被误推 Harbor。出镜像用 `mise exec -- mvn -o clean package -pl <module> -Premote -DskipTests`。
  - 注意 `k8s:build k8s:push` 这种带前缀的调法在 profile 未激活时报 `No plugin found for prefix 'k8s'`，**MUST 用 `-Premote` 让 execution 在 `package` 阶段触发**。
- **镜像 tag 每次改动都 +0.0.1**，不要覆盖同 tag。集群 `imagePullPolicy: IfNotPresent`，覆盖同 tag 后节点不会重新拉取，改动静默不生效。本次 1.0.0 → 1.0.1（修 XML）→ 1.0.2（修配置）。
- 滚动更新：`kubectl set image deploy/<name> -n itp <container>=<repo>/<image>:<tag>` + `kubectl rollout status`。回滚就是把 tag 换回去，**一条命令可逆**。
- **门禁**：push 日志出现 `Pushed ... :<tag>` 或 `digest:`（两种措辞都要匹配，按 `Pushed itp/` 过滤会漏）；`kubectl get pod -o custom-columns=...IMAGE` 确认新 pod 用的是新 tag。

### 22.7 启动验证：`Running` 不等于起来了

- `kubectl get pod` 显示 `1/1 Running` **不代表 Spring 上下文 refresh 成功**。本次 pod 一直 Running，但上下文从未 refresh，只是每 30 秒刷一条 `Failed to load BaseExport beans`。
- **判据只有一条**：日志里有 `Started <XxxApplication> in N seconds`。
- 排查启动失败 **MUST 看日志开头**，不是结尾。结尾全是周期性噪声，真实原因（`Application run failed` + `Caused by` 链）在最前面。过滤噪声：`grep -vE 'CustomMeterRegistry|has not been refreshed|^\s+at '`。
- 取日志：`kubectl logs -n itp <pod> -c <container>`。注意 `kubectl logs deploy/<name>` 在滚动期间可能取到旧 pod，**MUST 用具体 pod 名**。
- `scripts/klog.sh` 的**关键字过滤实测不生效**（传不存在的关键字仍返回全量），结论 **MUST 本地重新 grep**。
- **门禁**：`grep -c 'Started .*Application' = 1`，且 pod 名与新 tag 对得上。

### 22.8 双跑 + 重放对比（本方法的主干，其余步骤都是为它服务）

**整条迁移的骨架是一个闭环，不是瀑布**：两个应用同时在测试环境跑 → 分析旧应用收到的真实请求 → 原样重放到新应用 → 查日志与数据库是否符合预期 → 逐项比对与旧应用的行为差异 → 有差异就改代码/配置，回到重放。收敛到「差异清单为空或每条差异都有书面接受」才算这个接口验完。

**为什么必须双跑而不是只跑新的**：旧应用是唯一可信的契约基线（§22.1 只认代码，但代码读错的可能仍在）。同一份报文两边各打一次、逐字节比响应，是唯一能同时验证「我读对了契约」和「我实现对了契约」的手段。本次就是靠双跑才发现新服务全量 403 —— 只看新应用日志会以为是报文构造错了。

#### 22.8.1 双跑的前提：新服务必须能被调到，且不能抢流量

- 新服务**不建 Service**（或 Service 不接入向路由）：设备流量继续全打旧应用，新服务只接受我们手工重放。**face-pay 在 2026-09-15 之前一直是这个状态**；当天新建 `face-pay-server-svc:30025` 并改路由后，该状态结束。
- 新服务无 Service 时怎么调：`kubectl exec -n itp <pod> -c istio-proxy -- curl -s -X POST http://127.0.0.1:<port>/<path> -d '<body>'`。业务容器多半没有 curl，istio-proxy 有。
- **两个应用共用同一个库**（本次同为 `AFCITPDB`/`qditp`），双跑安全的前提是**新服务只写新表**（§22.2 换前缀 + §22.9 只读旁路）。这一点没做到，双跑本身就会污染旧数据。

#### 22.8.2 重放的安全分级（照抄流量前必须先分类）

同一份报文重放到新服务，副作用**并不都局限在新表内**。按外部副作用分三级，逐级需要更强的授权：

- **绿色·可直接重放**：纯查询、心跳、状态查询。副作用最多是新表内的 MERGE / INSERT。
- **黄色·需确认**：会写新表业务数据但不出网的（下单只落库不调支付中心的那类）。重放会产生垃圾订单，需约定清理口径。
- **红色·MUST 先取得明确授权**：会触发**外部真实动作**的 —— 调支付中心测试网关（下单 / 查询 / 退款，退款是真出款动作）、向 fep-app 推 APP 通知、经 `rpc` 调 ticket-server / account-server（会产生真实乘车码、真实卡操作）。**NEVER 未经授权重放红色接口。**
- 判定方法：看该接口实现里有没有 `PayCenterClient` / `notifyService.enqueue` / `*Client` 的 RPC 调用。

#### 22.8.3 拿到真实请求

- 先摸清**哪些接口真有流量**：`kubectl logs deploy/<old> --tail=200000 | grep -oE 'controller\.ci\.[a-z]+\.[A-Za-z]+ - [^,]{2,30}' | sort | uniq -c | sort -rn`。本次结论：20 万行里只有心跳（BOM 876 / TVM 294），**其余 33 个接口零真实流量**。这是本方法的最大现实约束 —— 没有流量就没有可重放的样本。
- 旧应用 `other.web.enableLogRequestInFilter=true`（集群 env 已开），`FirstFilter` 会打请求体，因此**只要发生过，完整 `bizData` 就在日志里**，能原样重放。
- 流量不足时的三条补充路径，按可信度排序：①请设备侧或业务方在测试环境跑一遍真实场景（最可信）；②从旧库历史数据反推报文（可信度中等，注意旧库字段可能已被后续流程改写）；③按契约构造（可信度最低，**只能验证实现，不能验证「我读对了契约」**，必须在结论里标注区别）。
- **门禁**：每个接口的样本来源要标明是①②③哪一类。

#### 22.8.4 链式重放与单号映射（单请求重放不够）

多数接口不是独立的：下单 → 请求支付信息 → payNotice → 激活 → 取票 → 出票上报 → 退款，后一步依赖前一步的状态。因此：

- **MUST 按场景成串重放**，而不是逐个接口打一枪。场景清单直接用 `docs/testing/face-pay/` 里已有的用例。
- **新服务自己生成 `orderNo`（`F2fOrderNoGenerator`），无法复用旧单号**。链式重放时要维护一张「旧单号 ↔ 新单号」映射，后续步骤用新单号请求、但比对时对回旧链路的对应步骤。**NEVER 把旧单号硬塞给新服务** —— 那走的是 §22.9 的旧库只读旁路，验的是另一件事。
- 时间相关字段（`timestamp`、二维码过期窗口）重放时要按当前时间重算，否则会命中过期分支，造成「差异」实为样本失效。

#### 22.8.5 差异分类（这是「分析行为差异」的落点）

比对结果 **MUST 分成三类**，处置口径完全不同：

- **A 类·契约差异**：HTTP 状态码、`retCode`、`retMsg`、响应键名与键序、中文提示。**目标是零**。本次 A 类差异出现过一次（全量 403 `{"msg":"check url unSafe"}`），根因是配置而非代码。
- **B 类·落库差异**：新表落了旧实现不落的数据（本次心跳 `F2F_DEVICE_STATUS`），或字段语义调整。**预期会有**，但每条都要能对上设计意图，并确认 ①旧表零变化 ②不产生对外可见的行为变化。说不出设计意图的差异按缺陷处理。
- **C 类·时序差异**：同步改异步（本次 APP 通知从请求线程内推送改为落库 + 扫表重试）、轮询改单次应答（BOM 去掉事务内 180 秒轮询）。**必须确认对端能接受**：设备侧会不会因为「立刻拿不到终态」而误判。这类差异单测和单次重放都发现不了，要靠场景重放 + 观察后续补偿任务。
- **门禁**：每个接口产出一张三类差异表；A 类必须为空，B/C 类每条都有结论（符合设计 / 已书面接受 / 待改）。

#### 22.8.6 每轮重放的证据要求

1. 旧应用响应原文
2. 新服务响应原文（同一份请求体）
3. 逐字节比对结论
4. 新表相关行的 SQL 查询结果
5. 旧表零变化的证据
6. 幂等复验：同一报文再打一次（本次验证 `MERGE` 使 `HEARTBEAT_COUNT` 1→2 且不产生重复行）
7. 边界复验：关键字段缺失 / 非法值时两边响应是否一致（本次 `deviceId` 缺失两边都回 `0000`）

- **门禁**：七项齐备才算这个接口「已验证」。**没有真实流量、没跑过重放的接口 MUST 在台账里标注「未验证」，NEVER 用单测通过冒充链路验证** —— 本次 52 个单测全绿的同时，服务连启动都启动不了。

### 22.9 切换期的两个必备闸门

同契约重写不是「切过去就完了」，切换瞬间存在两类跨库状态：

- **旧库只读旁路**：新服务查不到订单时回查旧库（本次 `legacy` 包，4 个类 + 一个 `f2f.legacy.enabled` 开关）。三条铁律：①**只读**，`grep` 证明无 INSERT/UPDATE/DELETE；②**类型转换全放 Java**，不要在 UNION ALL 里 `TO_NUMBER`/`TO_TIMESTAMP`——一行脏数据会让整条查询失败、退化成「查不到订单」；③解析失败**返回 null 并告警，NEVER 兜底成 now() 或 0**。
- **跨库重复动作闸门**：旧库的退款记录在新表看不到，不查就会跨日二次出款。**MUST 放在唯一收口点**（本次 7 个退款来源全走 `F2fRefundService.refund`，一处闸门覆盖全部），并且**返回「拒绝」而不是伪造一条成功记录**——旧单号在新表不存在。
- **投影不可写**：从旧库投影出来的对象 `id` 恒为 null，若误传给 `updateStatus`，0 行会被误读成「状态机拒绝」，结论正好相反。**MUST 有 `isLegacy()` 判断并在所有写路径前短路。**
- **门禁**：只读性 grep 为 0；闸门有纯逻辑单测覆盖；每条写路径都能指出 legacy 短路在哪一行。

### 22.10 切流量与回滚

**2026-09-15 已实际执行，本节按实测重写。此前写的「改 K8s Service selector（蓝绿）」是错的、NEVER 回退** —— 那条路第一步就断：`collect-pay-c23ku-svc` 的 `targetPort` 是 **8080**（collect-pay 靠 Deployment env `server.port=8080` 顶掉了 yml 里的 58101），而 face-pay 容器内实测 `58101=200`、`8080=000`，翻 selector 等于把全量流量打到无人监听的端口。

- **真正的切换点是路由，不是 Service**。两处，各一条命令：
  - 设备域：`kubectl edit vs fep-app-vr -n itp`（ns `itp`），把 `/itptvm/` 与 `/itpbom/` 两条 route 的 destination 由 `collect-pay-c23ku-svc:30024` 改为 `face-pay-server-svc:30025`。`rewrite.uri` 保持原样（新模块 controller 用同样的类级前缀）。
  - APP 域：`fep-app` Deployment 的 env **`service.collectPay.url`**（键名带点）改为 `http://face-pay-server-svc.itp.svc:30025`，会触发滚动重启。
- **入向链路（实测）**：`58.56.166.170:48000` → 节点 `172.20.211.200`（= `k8s02-gateway-866a2`）→ `itp-gateway`（ns `itp-gateway`，`image: auto` 的 istio 网关，svc `:50908`）→ `fep-app-vr`。**查现行路由 MUST 读 envoy**：`kubectl exec -n itp-gateway <itp-gateway-pod> -- curl -s '127.0.0.1:15000/config_dump?resource=dynamic_route_configs'`。**NEVER 只看 `kubectl get vs -A` 的摘要表判断某个前缀归谁** —— 那张表不显示 path，曾据此误判「没有 VirtualService 定义 /itptvm」，白走两版错方案。
- **前提**：新服务先有自己的 Service。本次新建 `face-pay-server-svc`（NodePort `30025:30025` → targetPort **58101**，selector `app=face-pay-server`），它同时是切换前的验证入口与切换后的排障入口。
- **这个切法的关键收益：内部调用方零影响**。`recon-server`（`/internal/recon/export`）、`gate-txn-pay`（`/internal/app-order/**`）、`web-admin`（`NoticeAppTask` 4 个端点）都是**直连** `collect-pay-c23ku-svc:30024`、不走网关，因此对账 / 补款 / 旧表通知继续在旧服务上跑，**不需要把 `/internal/**` 先迁到新模块**（新模块至今没有这两组端点）。按 selector 切则这三条会同时断——这是选路由而非 Service 的决定性理由。
- 切换前仍需处理**在途状态**：「已下单未支付的旧单收不到 `payNotice`」的 180 秒窗口（等于二维码有效期），要么切换前 3 分钟停止新下单，要么明确书面接受。
- **回滚：三条独立命令**，可分别执行、秒级生效：`vs` 两条 route 改回 `collect-pay-c23ku-svc:30024`；`fep-app` 那条 env 改回原值（本次原值是 `http://172.20.211.23:30024`，**MUST 改前先记下**）；镜像 `kubectl set image` 回上一个 tag。
- **`collect-pay` NEVER 直接停掉**：旧单退款入口与上面三类内部端点都还在它上面（ADR「旧应用保留内部退款入口，过完退款窗口再下线」）。
- **门禁**：切换步骤、观察指标、回滚命令写成清单，切换时逐条打勾；切完 MUST 用「同一份报文分别打新旧服务、比对 retCode」做契约基线核对（本次三条链路 `2002` / `8006` / `8003` 两边完全一致）。

### 22.11 记录纪律

- 每个**裁决**（含「暂不处理」）都要落到文档，写清：结论、日期、被接受的敞口、解禁条件。本次 §二十一 待办第 2 / 9 条即此形态。
- 每个**被实测推翻的结论**都要就地改写并标注「纠正此前记载」，不要只追加新结论——两个互相矛盾的结论并存比没有结论更糟（AGENTS.md §5.3 删掉 4 份互相矛盾进度报告的教训）。
- 新发现的环境类问题追加到 `docs/ops/生产环境清单.md`；新发现的工程陷阱追加到 `AGENTS.md` §7 / §8。
- **NEVER** 新建「实现进度报告 / 接口清单」这类汇总文件（AGENTS.md §5.3）。进度写在本文件的台账章节里。

### 22.12 一页速查

主干是一个**闭环**，不是瀑布 —— 22.0~22.7 是为了让闭环能跑起来，22.9~22.11 是闭环收敛后才做的事：

```
准备期（一次性）
  定性 → 抽契约(代码为准,错别字保留) → 建新表+映射表+回滚脚本
  → 编码(四条硬约束) → 自检四项(test / xmllint / 只读grep / 配置三处对照)
  → 出镜像(-Premote, tag+1) → 滚动更新 → 确认 "Started ... in N seconds"

主干闭环（逐接口/逐场景反复跑，直到差异清单收敛）
  ┌─→ 两个应用同时在跑(新服务不建Service,不抢流量)
  │   → 分析旧应用真实请求(先摸流量分布,再取完整bizData)
  │   → 按安全分级(绿/黄/红)决定能否重放,红色先要授权
  │   → 成串重放(维护旧↔新单号映射,时间字段重算)
  │   → 查新服务日志 + 查库(新表符合设计 / 旧表零变化)
  │   → 差异三分类(A契约=必须为0 / B落库 / C时序)
  └───← 有A类或说不清的B/C → 改代码或配置 → 重新出镜像

收敛后
  装两个闸门(旧库只读旁路 + 跨库重复动作拦截)
  → 改路由切流量(处理在途窗口, 先演练回滚) → 裁决与纠正入档
```

每一步只有拿到门禁证据才能进下一步。**本次的教训可以压缩成一句：凡是「应该没问题」的地方，都要有一条实测命令能证明它没问题。**

## 参考

- `docs/testing/face-pay/` — 当面付测试知识库，`04` 的 E 节是缺口清单
- `docs/testing/bom-oneside/` — BOM 单边处理，`02` 的 D 节含规格核对结论
- `docs/business/tvm-bom-pay.md`、`docs/business/app-ticket-collect.md`
- `docs/external/支付中心网关接口文档.md`
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx` §7.3 / §7.5
- `docs/业务需求文档/非现金购票单程票退款功能3.docx`
- `AGENTS.md` §2.2.1、§5.1、§5.2、§8

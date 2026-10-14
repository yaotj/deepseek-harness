# ITP 与第三方支付对账（TXNDIFF + 渠道账单）设计草案

> 来源：最新对账需求 §一「对账差异明细文件」「互联网支付对账日账单」「第三方账单文件」。
> 状态：**设计已落地（recon-server 内独立流程），9 项确认结论见 §五**。外部契约中项1/2/5/7 以代码默认假设先行、待甲方拍板；
> 项3/4/6/8/9 已据 `PAY_TXN_DETAIL` 真实字段与既有数据访问模式确认。
> 关联：`docs/business/recon.md`（现有 ITP→ACC 四类文件）、`model/.../recon/ReconFileTypeEnum.java`。

## 一、目标与边界

| 项 | 说明 |
|---|---|
| 输入 A | 第三方/渠道侧日账单 `reconciliation-bill-date.csv`（需求给的 22 字段，落地路径 `/itp/recon/`） |
| 输入 B | ITP 侧自己产生的支付交易明细（按自然日） |
| 处理 | ITP 与第三方支付 **按自然日** 对账，找出差异 |
| 输出 | 差异明细文件 `ITP.TXNDIFF.YYYYMMDD`，投递到 `/itp/recon/`（与现有四类文件同 FTP 根） |
| 差异方 | 「第三方有 ITP 没有」「ITP 有第三方没有」两类，需带 **差异原因** 与 **扣款失败原因(平台返回)** |

**与现有 recon-server 的关系**：现有 recon-server 只做「ITP→ACC」四类文件（源服务导出分片 → recon-server 合并 → FTP 投递），
**不消费任何外部账单**，也没有「ITP 侧交易 vs 外部账单」的比对能力。TXNDIFF 走的是**另一条链路**（取渠道账单 → 与 ITP 自有交易比对），
不应硬塞进现有 source-export 编排（现有模型假定每个文件类型由某个源服务上传分片，而 TXNDIFF 是 ITP 自己算出来的）。建议作为 **recon-server 内新增的独立子流程** 或 **独立定时任务**。

## 二、第三方账单文件 `reconciliation-bill-date.csv`（输入 A）

需求字段（顺序即表头）：

| # | 字段 | 说明 | 约束 |
|---|---|---|---|
| 1 | bill_date | 账单所属日期 | Y, yyyy-MM-dd |
| 2 | payment_channel | 支付渠道枚举 | Y, 如 ALIPAY |
| 3 | payment_vendor | 渠道编码 | Y, 如 03 |
| 4 | channel_name | 渠道名称 | Y |
| 5 | channel_merchant_no | 渠道侧商户号 | N |
| 6 | terminal_no | 终端号 | N |
| 7 | business_type | PAY/REFUND/REVERSAL/OTHER | Y |
| 8 | trade_status | SUCCESS/FAIL/CLOSED/UNKNOWN | Y |
| 9 | trade_time | 渠道交易时间 | N, yyyy-MM-dd HH:mm:ss |
| 10 | clearing_date | 清算日期 | N |
| 11 | merchant_order_no | 商户订单号 | N（**关联键**） |
| 12 | channel_trade_no | 渠道交易流水号 | N（**关联键**） |
| 13 | merchant_refund_no | 商户退款单号 | N |
| 14 | channel_refund_no | 渠道退款流水号 | N |
| 15 | currency | 币种 | Y, ISO 4217 |
| 16 | order_amount | 订单应收金额 | N, decimal(18,2) |
| 17 | transaction_amount | 实际交易金额（正数） | Y, decimal(18,2) |
| 18 | fee_amount | 渠道手续费 | N |
| 19 | settlement_amount | 结算金额 | N |
| 20 | discount_amount | 优惠金额 | N |
| 21 | product_name | 商品名称 | N |
| 22 | remark | 备注 | N |

落地表见 `recon-server/src/main/resources/sql/recon-thirdparty-schema.sql` 的 `RECON_THIRD_BILL`。

## 三、差异明细文件 `ITP.TXNDIFF.YYYYMMDD`（输出）

表头（需求原文）：运营日期、交易类型、逻辑卡号、交易日期、交易原价、当前车站名称、设备编码、票卡类型、票卡子类型、次数、
支付渠道、激活时间、数量、交易流水号、交易订单号、订单金额、实际扣款金额、优惠累计消费金额、超时金额、**差异方、扣款失败原因**。

即：**与 DETAIL 第 1~19 段完全一致，第 20、21 段替换为「差异方」「扣款失败原因」**（共 21 段）。
段含义对齐 `docs/business/recon.md` 的 DETAIL 节与本次 DETAIL 21 字段改造（见 `ReconFileTypeEnum.DETAIL_FIELD_COUNT=21` 及两源导出器）。

落地表见 `RECON_TXN_DIFF`。

## 四、处理流程（推荐）

```
[T+某时刻，按自然日]
  1. 获取渠道账单 reconciliation-bill-date.csv          ← 待确认：FTP 拉取 / 接口推送？路径/凭据？
  2. CSV 解析 → 入库 RECON_THIRD_BILL（幂等按 BILL_DATE+CHANNEL_TRADE_NO）
  3. 抽取 ITP 侧当日交易明细（按自然日）               ← 待确认：源表=PAY_TXN_DETAIL？关联键 merchant_order_no / channel_trade_no
  4. 双边以 商户订单号/渠道交易流水号 为键做 FULL OUTER 比对
       - 第三方有 & ITP 无  → DIFF_SIDE=THIRD_ONLY
       - ITP 有 & 第三方无  → DIFF_SIDE=ITP_ONLY
       - 双方都有但金额/状态不一致 → 也记差异（差异原因=金额不符/状态不符）
  5. 生成 ITP.TXNDIFF.YYYYMMDD（管道分隔、UTF-8、无表头，同现有四类文件契约）
  6. FTP 投递 /itp/recon/（复用 ReconFtpService 的 .uploading→rename→回查 机制）
```

金额单位：**一律按「分」**（与 EXP/PAY/BUS/DETAIL 一致）。渠道账单 `transaction_amount` 为 decimal(18,2) 元，
入库与比对前 MUST 乘 100 转分（或统一在比对层换算），NEVER 混用元/分。详见 §六 E。

## 五、待甲方/渠道侧确认项（评估结论）

> 2026-10-12 评估：基于 `PAY_TXN_DETAIL` 真实字段与现有 recon-server 数据访问模式，逐项确认如下。
> 标注 **【已确认】** 的可直接落地；**【默认假设】** 为代码已采用、待甲方拍板的默认行为；**【待确认】** 仍阻断某部分落地。

1. **【默认假设】账单投递方式**：`recon-thirdparty.md` 原问「渠道 PUSH 还是 ITP PULL」。
   代码落地为「**本地放置优先**」（从 `recon.thirdparty.bill-local-dir` 读取匹配 `bill-file-pattern` 的文件），FTP 拉取作为后续扩展点。
   仍需甲方确认：账单最终落到 ITP 侧的**实际通道与路径/凭据**，以便启用 FTP 拉取模式。
2. **【默认假设】多渠道路由**：代码**按自然日合并为单一 `ITP.TXNDIFF.YYYYMMDD`**（渠道标识落在第 11 段支付渠道 + `RECON_TXN_DIFF.PAY_CHANNEL`）。
   仍需甲方确认：`payment_channel` 取值清单及是否需按渠道拆文件。
3. **【已确认·源表=PAY_TXN_DETAIL】** 经核对 `pay-sign-server/.../pay-txn-schema.sql`，`PAY_TXN_DETAIL` 承载：
   `MERCHANT_ORDER_NO`、`CHANNEL_ORDER_NO`（关联键，见项4）、`CARD_ID`/`CARD_TYPE`（逻辑卡号/票卡类型）、
   `PAY_CHANNEL_CODE`（支付渠道）、`TOTAL_AMOUNT`/`CASH_AMOUNT`/`COUPON_AMOUNT`/`REFUND_AMOUNT`（分）、
   `PAY_TIME`（YYYYMMDDHH24MISS）、`TXN_DATE`（YYYYMMDD）、`DEBIT_REQUEST_RESULT`（扣款结果 PROCESSING/SUCCESS/FAIL）。
   **可覆盖 TXNDIFF 第 1~19 段中的金额/卡/订单/渠道部分**。
   **【残余待确认】**：车站名称/设备编码/激活时间/次数/超时金额属乘车域，支付台账不持有 → TXNDIFF 这些段在 ITP 侧留空；
   若甲方要求这些段也填实，需由 DETAIL 导出数据（gate-txn-pay + daily-ticket）LEFT JOIN `PAY_TXN_DETAIL` 补全（另行排期）。
   **【2026-10-12 二次核查补正】**：face-pay(BOM/TVM) 渠道支付**不落 `PAY_TXN_DETAIL`**（已确认），故 `PAY_TXN_DETAIL` 仅覆盖 QR 后付费 + 日票；
   当面付改由 `F2F_ORDER JOIN F2F_PAYMENT(SUCCESS)` 独立抽取（见 §八 整改），关联键 `merchantOrderNo=F2F_ORDER.ORDER_NO`、`channelTradeNo=F2F_PAYMENT.CHANNEL_ORDER_NO`。
4. **【已确认·关联键】**：渠道账单 `merchant_order_no` → `PAY_TXN_DETAIL.MERCHANT_ORDER_NO`；
   渠道账单 `channel_trade_no` → `PAY_TXN_DETAIL.CHANNEL_ORDER_NO`。以 `(MERCHANT_ORDER_NO, CHANNEL_ORDER_NO)` 为双边 FULL OUTER JOIN 键，
   已在 `ThirdPartyBill.joinKey()` 与 `ItpTxnRecord.joinKey()` 同构实现。
   **唯一性备注**：`PAY_TXN_DETAIL` 唯一索引为 `(ORDER_NO, TXN_DATE)`，同 `(MERCHANT_ORDER_NO, CHANNEL_ORDER_NO)` 可能多行
   （如多次重试）；比对时 ITP 侧同键多行取「支付成功优先」的一行，已在 `TxnDiffReconService` 处理。
5. **【默认假设】渠道枚举映射**：`payment_channel|payment_vendor` → ITP `PAY_CHANNEL_CODE` 的映射表由
   `recon.thirdparty.channel-mapping.*` 配置驱动，**缺省按原值透传**（不参与 JOIN，仅影响第 11 段展示）。
   仍需甲方提供确切映射值。
6. **【默认假设·已落地】差异方与差异原因枚举**：`RECON_TXN_DIFF.DIFF_SIDE` 取值 `ITP_ONLY` / `THIRD_ONLY` / `AMOUNT_MISMATCH` / `STATUS_MISMATCH`，
   仅「有差异」的记录进 TXNDIFF（符合「差异明细」语义）。`AMOUNT_MISMATCH`/`STATUS_MISMATCH` 为本次新增的两种差异（金额/状态不符）。
7. **【默认假设·部分确认】扣款失败原因**：ITP 侧取 `PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT`（FAIL 时写「ITP扣款失败:xxx」）；
   渠道侧 THIRD_ONLY 且 `trade_status=FAIL` 时写「渠道交易失败:xxx」。其余情况写缺失说明。**仍待甲方确认**失败原因的标准值域
   （是否取支付中心回查原文、 remark 透传规则）。
8. **【已确认】账期后缀=自然日**：TXNDIFF 文件名 `YYYYMMDD` 取**自然日**（账单日），与 DETAIL 的运营日口径（任务 D，offset=1）**不同**，
   已在 `ThirdPartyReconScheduler` 按 `TXN_DATE=YYYYMMDD` 取 ITP 侧、按账单 `BILL_DATE` 命名，二者同一自然日。
9. **【默认假设·已落地】调度归属=独立流程**：TXNDIFF **不并入**现有四类文件日终批(sys_job 225)，作为 recon-server 内独立流程。
   触发入口 `POST /internal/recon/thirdparty/run`（供 web-admin 新注册 sys_job 调用）；亦支持 `@Scheduled`（默认关闭 `scheduler.enabled=false`，避免双跑）。

### 已落地的代码（recon-server）
- `storage/ThirdPartyReconProperties.java` — 配置项。
- `thirdparty/ThirdPartyBill.java` / `ItpTxnRecord.java` / `TxnDiffRecord.java` — 实体。
- `thirdparty/ThirdPartyBillCsvParser.java` — 22 字段 CSV 解析（手工、支持引号）。
- `thirdparty/ThirdPartyBillIngestService.java` — 幂等入库 `RECON_THIRD_BILL`。
- `thirdparty/ItpTxnExtractor.java` + `mapper/PayTxnDetailMapper.xml` — 取 `PAY_TXN_DETAIL`（pay-sign，覆盖 QR 后付费 + 日票）。
- **face-pay 流（分片推送·2026-10-13 重做）**：recon-server 单数据源**无** `F2F_ORDER`/`F2F_PAYMENT` 的 SELECT 授权、且 face-pay 库即将拆库（见 §八），故**废弃直连 F2F_* 的 `F2fTxnMapper`**，改走既有对账分片机制：
  - face-pay 侧：`ReconExportService.exportF2fTxn` 在抽取指令里把 `F2F_ORDER JOIN F2F_PAYMENT(SUCCESS)`（按 `PAID_TMS` 落窗、逐行）经 `ReconPartSink` 上送为 `F2F_TXN` 分片；SQL 见 `F2fReconExportMapper.selectFacePayTxnDetails`；行格式为 21 段（`F2fTxnLine`）。
  - recon-server 侧：`thirdparty/FacePayTxnShardFetcher.java` 按账期(`txnDate`) 起独立小批次 `RECON_F2F_{txnDate}`、下发抽取、轮询收齐、读回分片转 `ItpTxnRecord`；`ItpTxnExtractor` 改为 `PAY_TXN_DETAIL`(pay-sign) + `FacePayTxnShardFetcher`(face-pay) 两流 Union。关联键 `merchantOrderNo=ORDER_NO` / `channelTradeNo=CHANNEL_ORDER_NO` 不变。
  - 跨模块契约：`model/.../F2fTxnLine.java`（21 段行编解码，两端共用）；枚举新增 `ReconFileTypeEnum.F2F_TXN` 与 `ReconFileType.F2F_TXN`。
- `thirdparty/TxnDiffReconService.java` — FULL OUTER JOIN → `RECON_TXN_DIFF`。
- `thirdparty/TxnDiffFileGenerator.java` — 生成 `ITP.TXNDIFF.YYYYMMDD` + FTP 投递（复用 `ReconFtpService`，已放开文件名正则含 TXNDIFF）。
- `thirdparty/ThirdPartyReconScheduler.java` + `ThirdPartyReconController.java` — 编排与内部触发端点。
- `mapper/ReconThirdBillMapper.xml` / `ReconTxnDiffMapper.xml` — 建表脚本见 `recon-server/src/main/resources/sql/recon-thirdparty-schema.sql`（已存在，待 DBA 执行）。


## 六、金额单位澄清（任务 E，跨 DETAIL/TXNDIFF）

- `docs/business/recon.md` B9 已记录 DETAIL「线网最高票价或者 1」的 `1` 单位（元/分）三处文档冲突、未定义。
- 本需求 DETAIL 表头未标单位；EXP/PAY/BUS 明确标「单位分」。
- **推荐默认（待甲方确认前不写死换算）**：DETAIL / TXNDIFF 所有金额字段 **统一按「分」**，与 EXP/PAY/BUS 一致；
  「线网最高票价或者 1」按「分」理解（即 1 元=100 分或 1 分，需甲方拍板，但至少在代码内不混用元/分、不做隐式 ÷100）。
- 渠道账单为「元」，入库/比对层 MUST 显式换算为分，换算点集中、可审计。

## 七、建议代码结构（确认后落地）

```
recon-server/src/main/java/com/chinasofti/huateng/recon/thirdparty/
  ThirdPartyBillFtpPuller.java     // 1. 获取 csv（待定投递方式）
  ThirdPartyBillCsvParser.java     // 2. 解析 22 字段 → RECON_THIRD_BILL
  ThirdPartyBillIngestService.java // 2b. 幂等入库
  ItpTxnExtractor.java             // 3. 抽 ITP 侧当日交易（待定源表）
  TxnDiffReconService.java         // 4. 比对 → RECON_TXN_DIFF
  TxnDiffFileGenerator.java        // 5. 生成 ITP.TXNDIFF.YYYYMMDD（复用 ReconRecord + ReconFtpService）
  ThirdPartyReconScheduler.java    // 触发（独立定时任务或并入 sys_job）
recon-server/src/main/resources/sql/recon-thirdparty-schema.sql
```

> 上述 Java 已于 2026-10-12 落地（见 §五「已落地的代码」），外部契约未定部分以代码默认假设先行（项1/2/5/7），待甲方拍板后调配置即可，无需改代码。

## 八、设计合理性评估（2026-10-12，P0 已于当日二次核查修正）

> 落地后复盘。结论：架构骨架合理，但 **ITP 侧源表选型违背需求硬约束**，须整改；状态/关联键/金额比对口径偏粗，有漏报/误报风险。

### P0 致命（已二次核查 · 2026-10-12 修正）：原「改源为三域 DETAIL 并集」结论不成立，须回退
- 需求原文：「TXNDIFF 第 1~19 段与 DETAIL 完全一致」。原评估据此判定 `ItpTxnExtractor` 应以三域 DETAIL 并集为 ITP 侧源、将 `PAY_TXN_DETAIL` 降级为关联键桥。
- **二次核查发现该前提不成立**，两处事实与原假设相反：
  1. **face-pay 根本不导出 `ITP.DETAIL`**。`face-pay-server/.../service/ReconExportService.java` 类注释明确「只负责 ITP.PAY 与 ITP.BUS 两类文件」，其 `exportDetail` 不存在；`F2fReconExportMapper.xml` 无任何 detail 查询。故 `ITP.DETAIL` 实际只包含 `gate-txn-pay`(出站/超时费) + `daily-ticket`(发售) 两段，**当面付(BOM/TVM) 从未进入 DETAIL**。
  2. **gate-txn-pay 的 DETAIL 不是全量 QR 后付费明细**。`gate/.../mapper/ReconExportMapper.xml#selectDetailPage` 过滤 `NVL(OVERTIME_AMOUNT,0)>0 AND CARD_TYPE IN ('0445','0446','0447','0448')` —— 仅「虚拟电子多日计次票且产生超时费」的子集。海量的普通 QR 单程扣费**不在任何 DETAIL 文件里**（EXP 仅 13 段单边交易，亦非 21 段明细）。
- **推论**：`DETAIL` 在现网是**窄口径子集**，而渠道账单是**全量渠道支付**。若 ITP 侧改用 DETAIL 并集，反而会把普通 QR 单程扣费、当面付(BOM/TVM) 整片漏掉，对渠道账单产生大量**误报的 THIRD_ONLY**（渠道有、ITP DETAIL 无），与对账「找全差异」的目标背道而驰。
- **正确结论**：`PAY_TXN_DETAIL`（pay-sign 支付台账）是 QR 后付费 / 日票 渠道支付的完备源（持有 `MERCHANT_ORDER_NO`/`CHANNEL_ORDER_NO` 关联键）；但 face-pay(BOM/TVM) 渠道支付**不落此表**（已确认），故 ITP 侧须以 `PAY_TXN_DETAIL` ∪ `F2F_ORDER JOIN F2F_PAYMENT` 的 Union 为基，方能覆盖全部三交易源（关联键 `merchantOrderNo=F2F_ORDER.ORDER_NO` / `channelTradeNo=F2F_PAYMENT.CHANNEL_ORDER_NO`）。原 P0「改源为三域 DETAIL 并集」应撤销，转为下面的 **P0(口径裁决)** + **face-pay 流(已补)** + **P2(字段增实)**。
- **仍真实存在的硬约束冲突（须 甲方裁决）**：需求「TXNDIFF 1~19 = DETAIL 1~19」只对「确实进了 DETAIL 的交易」可逐字满足（即 daily-ticket 发售单 + gate 超时费多日票）；对普通 QR 单程、当面付等未进 DETAIL 的交易，ITP 侧 1~19 只能由支付台账/各域表尽力构造，**无法与一份不存在的 DETAIL 行逐字对齐**。这是「需求口径」与「现网 DETAIL 实施口径」的冲突，不是代码 bug，必须拉甲方确认 TXNDIFF 到底要对齐哪份「交易明细」。

### P1 高：比对口径偏粗，存在漏报/误报
- **状态比对**：`statusMismatch` 对渠道 `trade_status`(SUCCESS/FAIL/CLOSED/UNKNOWN) 与 ITP `(PAY_STATUS 或 DEBIT_REQUEST_RESULT)=SUCCESS` 做布尔异或。问题：①两套状态机不同；②ITP 处于 PROCESSING/RETRY（终态前）即被误判 STATUS_MISMATCH。**应只在双方都到终态时比**，并给定显式映射（如 渠道SUCCESS↔ITP SUCCESS、渠道FAIL↔ITP FAIL/CLOSED），中间态不参与。
- **关联键非唯一**：`PAY_TXN_DETAIL` 唯一索引是 `(ORDER_NO, TXN_DATE)`，`(MERCHANT_ORDER_NO, CHANNEL_ORDER_NO)` 可多行（重试/多次请求）。当前「取 SUCCESS 优先」是启发式，未处理 1:N，FULL OUTER JOIN 可能错配。**需甲方确认渠道账单关联键是否全局唯一**；若非唯一，应以 `ORDER_NO` 为主线（渠道账单 `merchant_order_no` 到底对应 `ORDER_NO` 还是 `MERCHANT_ORDER_NO` 须澄清）。
- **金额只比「实付」**：仅 `channel.transaction_amount` vs `itp.cash_amount`，未比 `order_amount` vs `total_amount`，也未声明 `transaction_amount` 是否含优惠/退款。建议同时核对 订单金额/实付金额 两对并明确口径。

### P2 中：工程健壮性
- **账单投递**：项1 仅落地本地放置，FTP 拉取未实现；若渠道直推 FTP 需补拉取（建议实现 `ThirdPartyBillFtpPuller`）。
- **重跑事务**：`reconcile` 的 `delete+insert` 无 `@Transactional`，中途失败会丢当日差异；建议加事务或分段幂等。
- **空差异文件**：与既有「零交易日也要有文件」口径是否一致需甲方明确 TXNDIFF 是否同理（当前逻辑会生成 0 行文件并投递）。

### 已合理（保留）
- 独立流程不污染既有四类文件编排；复用 `ReconFtpService` 投递(.uploading→rename→verify)；配置驱动默认关闭可安全部署；幂等入库+可重跑；CSV 解析支持引号；金额元/分显式换算集中；调度双入口且有 enabled 互斥。

### 整改建议顺序（修订 · 2026-10-12 二次核查后）
- **（已落地）P1 状态/金额**：`TxnDiffReconService` 已改为「仅双方都到终态才比状态 + 显式 SUCCESS/FAIL/CLOSED 映射（非终态跳过）」、「双金额对（订单金额↔total_amount、实付金额↔cash_amount）任一对不符即记 AMOUNT_MISMATCH」。代码已编译通过。
- **（必须·待 甲方裁决）P0 口径**：拉甲方确认 TXNDIFF 的 ITP 侧 1~19 段究竟对齐哪份交易明细——**→ 2026-10-12 用户确认采用 (B)**：维持 `PAY_TXN_DETAIL` 全量基线（覆盖最全、不误报），撤销原「改源为三域 DETAIL 并集」方案；后续按 P2 增强对「同在 DETAIL 的订单」复用 DETAIL 行增实 1~19。
  - (A) 对齐 `DETAIL`：则普通 QR 单程 / 当面付 会大面积 `THIRD_ONLY` 误报，需先把 `DETAIL` 扩成全量（属大改，含 face-pay 新增 DETAIL 导出）；
  - (B) 以 `PAY_TXN_DETAIL` 全量支付为 ITP 侧（覆盖最全，已落地），但 1~19 乘车域字段对未进 DETAIL 的交易只能尽力填。
  **推荐 (B)**，并保留 (A) 的「若订单同在 DETAIL 则 1~19 直接复用 DETAIL 行」作为 P2 增强。
- **（已解决·2026-10-13）P0 覆盖·授权与拆库**：② **已确认 recon-server 单数据源用户不具备 `F2F_ORDER`/`F2F_PAYMENT` 的 SELECT 权限**（即便经 web API 亦无授权），且 face-pay 库即将拆库 → 直连 F2F_* 的方案（`F2fTxnMapper`）已废弃。改为 face-pay 侧推送 `F2F_TXN` 分片、recon-server 侧 `FacePayTxnShardFetcher` 按账期拉回（沿用既有 `ReconPartSink`/`ReconClient` 分片机制，与 PAY/BUS 同源、DB 拆分安全）。① 的「face-pay 不落 `PAY_TXN_DETAIL`」结论不变，当面付漏对账缺口仍以该分片流闭合。**部署前仍须用库内查询坐实 `PAY_TXN_DETAIL` 授权（pay-sign 仍直连），NEVER 凭代码推断。**
- **（已落地·2026-10-13 重做）face-pay 流以分片推送闭合覆盖缺口**：`ItpTxnExtractor` = `PAY_TXN_DETAIL`(pay-sign 直连) + `FacePayTxnShardFetcher`(face-pay 分片拉回) 的 Union；face-pay 关联键 `merchantOrderNo=F2F_ORDER.ORDER_NO`、`channelTradeNo=F2F_PAYMENT.CHANNEL_ORDER_NO`；21 段由 `F2fTxnLine` 契约承载（车站=STATION_CODE、设备=DEVICE_ID、次数=TICKET_NUM、激活时间=ACTIVATE_TMS、交易流水号=DEVICE_SEQ、票卡类型/子类型暂空、超时金额无），支付渠道=PAY_CHANNEL_CODE、优惠=COUPON_AMOUNT。原直连 `F2fTxnMapper` 已删除，face-pay 库拆库不影响对账。`recon-server` + `face-pay-server` 均已编译通过。
- **（可选·P2 增强）1~19 增实**：在 `PAY_TXN_DETAIL` 为基的基础上，对「同在 DETAIL 的订单」用 ORDER_NO 反查三域 DETAIL/业务表，把车站/设备/票卡子类型/次数/激活时间/里程序列号/超时金额等乘车域字段填实，使这部分 TXNDIFF 行与 DETAIL 逐字一致；未进 DETAIL 的订单维持台账尽力填。属增强，不阻塞上线。
- **（建议）P1 关联键**：澄清唯一性与 `merchant_order_no` 对应列（ORDER_NO 还是 MERCHANT_ORDER_NO），必要时以 ORDER_NO 为主线；现有「同键取 SUCCESS 优先」启发式保留。
- **（可选）P2**：FTP 拉取、`@Transactional`、空文件策略。

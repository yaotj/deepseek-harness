# Schema 对齐计划

## 1. 任务目标

将项目各模块的 `src/main/resources/sql/*-schema.sql` 文件与 `docs/tables.md` 完全对齐，确保 16 个共同表的字段长度、类型、非空约束、主键定义与生产数据库一致。

## 2. 决策总结

| 问题      | 决策                                                                                      |
| ------- | --------------------------------------------------------------------------------------- |
| 缺失表处理   | C. 暂时不新增，仅修复现有 16 个共同表的差异                                                               |
| 项目独有表处理 | A. 全部保留（ONLINE\_ORDER、ONLINE\_ORDER\_TICKET、QR\_CODE\_STATUS、ONLINE\_DEVICE\_HEARTBEAT） |
| 对齐粒度    | A. 完全对齐，所有字段长度/类型/非空约束与 tables.md 完全一致                                                  |
| 执行范围    | A. 全项目统一更新，覆盖全部 16 个共同表                                                                 |
| 执行方式    | 由于涉及字段长度扩大、非空约束变更、主键变更、虚拟列添加，**直接替换完整的 CREATE TABLE 语句**，而非 ALTER TABLE                 |

## 3. 影响范围

### 3.1 涉及模块与文件

| 模块               | Schema 文件                      | 涉及表                                                                                         |
| ---------------- | ------------------------------ | ------------------------------------------------------------------------------------------- |
| pay-sign-server  | `pay-sign-schema.sql`          | APP\_PAY\_SIGN\_INFO, APP\_PAY\_SIGN\_LOG, APP\_PAY\_SIGN\_REQUEST, APP\_USER\_PAY\_CHANNEL |
| blacklist-server | `blacklist-schema.sql`         | BLACKLIST, BLACKLIST\_OPERATE\_LOG                                                          |
| fep-dev-server   | `gate-txn-pay-schema.sql`      | GATE\_TXN\_PAY                                                                              |
| fep-dev-server   | `pay-txn-schema.sql`           | PAY\_TXN\_DETAIL, PAY\_REFUND\_DETAIL, PAY\_CALLBACK\_LOG                                   |
| ticket-server    | `ticket-server-schema.sql`     | QRCODE\_STATUS                                                                              |
| ticket-server    | `qrcode-txn-detail-schema.sql` | QRCODE\_TXN\_DETAIL                                                                         |
| key-server       | `key-server-schema.sql`        | METRO\_CA\_KEYSTORE                                                                         |
| account-server   | `account-server-schema.sql`    | USER\_ITP\_REG\_INFO, USER\_ITP\_REG\_LOG, USER\_ACC\_TICKETNO                              |

### 3.2 主要差异类型

1. **字段长度扩大**：VARCHAR2 普遍从 `128/64/32` 扩大为 `256/128/64`
2. **NUMBER 精度扩大**：从 `NUMBER(20)` / `NUMBER(12)` 扩大为 `NUMBER(22)`
3. **TIMESTAMP 精度**：从 `TIMESTAMP` 改为 `TIMESTAMP(6)`
4. **非空约束**：部分字段从可空 `Y` 改为非空 `N`，或添加 `DEFAULT` 值
5. **主键变更**：`APP_USER_PAY_CHANNEL` 主键从 `THIRD_USER_ID` 改为 `(THIRD_USER_ID, CARD_TYPE, CHANNEL)`
6. **虚拟列添加**：`APP_USER_PAY_CHANNEL`、`USER_ITP_REG_INFO` 需要添加分区虚拟列

## 4. 详细修改计划

### 4.1 pay-sign-server/pay-sign-schema.sql

#### APP\_PAY\_SIGN\_INFO

* `REQUEST_SIGN_SEQ`: VARCHAR2(64) → VARCHAR2(128)

* `THIRD_USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `SIGN_STATUS`: VARCHAR2(16) → VARCHAR2(32)

* `PAYMENT_VENDOR`: VARCHAR2(16) → VARCHAR2(32)

* `CARD_ID`: VARCHAR2(64) → VARCHAR2(128)

* `CARD_TYPE`: VARCHAR2(32) → VARCHAR2(64)

* `PAY_ACCOUNT_ID`: VARCHAR2(64) → VARCHAR2(128)

* `PAY_AGREEMENT_NO`: VARCHAR2(128) → VARCHAR2(256)

* `DISPLAY_ACCOUNT`: VARCHAR2(128) → VARCHAR2(256)

* `SIGN_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL

* `TERMINATION_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL

#### APP\_PAY\_SIGN\_LOG

* 所有 VARCHAR2 长度翻倍

* `REQUEST_BODY`: VARCHAR2(4000) → VARCHAR2(8000)

* `RESPONSE_BODY`: VARCHAR2(4000) → VARCHAR2(8000)

* `RESULT_MSG`: VARCHAR2(512) → VARCHAR2(1024)

* `CREATE_TMS`: TIMESTAMP → TIMESTAMP(6) NOT NULL

#### APP\_PAY\_SIGN\_REQUEST

* `ID`: NUMBER(20) → NUMBER(22)

* 所有 VARCHAR2 长度翻倍

* `NOTIFY_RETRY_COUNT`: NUMBER(10) → NUMBER(22)

* `NOTIFY_STATUS`: VARCHAR2(16) → VARCHAR2(32)

* `TERMINATION_TIME`: VARCHAR2(32) → VARCHAR2(64)

* `NOTIFY_RESULT`: VARCHAR2(512) → VARCHAR2(1024)

* `CREATE_TMS`: TIMESTAMP → TIMESTAMP(6) NOT NULL

* `THIRD_USER_ID`: 添加 NOT NULL 约束

#### APP\_USER\_PAY\_CHANNEL

* 所有 VARCHAR2 长度翻倍

* 主键从 `THIRD_USER_ID` 改为 `(THIRD_USER_ID, CARD_TYPE, CHANNEL)`

* 添加分区虚拟列 `THIRD_USER_ID_SUFFIX`

* `CREATE_TMS`: TIMESTAMP → TIMESTAMP(6) NOT NULL

* `UPDATE_TMS`: TIMESTAMP → TIMESTAMP(6) NOT NULL

### 4.2 blacklist-server/blacklist-schema.sql

#### BLACKLIST

* `ID`: NUMBER → NUMBER(22) NOT NULL

* `CREATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

#### BLACKLIST\_OPERATE\_LOG

* `ID`: NUMBER → NUMBER(22) NOT NULL

* `OPERATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

### 4.3 fep-dev-server/gate-txn-pay-schema.sql

#### GATE\_TXN\_PAY

* `ID`: NUMBER(20) → NUMBER(22)

* `ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `DEBIT_STATUS`: VARCHAR2(16) → VARCHAR2(32) NOT NULL DEFAULT 'INIT'

* `THIRD_USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `CARD_ID`: VARCHAR2(32) → VARCHAR2(64)

* `CARD_TYPE`: VARCHAR2(8) → VARCHAR2(16)

* `DEVICE_ID`: VARCHAR2(16) → VARCHAR2(32)

* `TRX_TYPE`: VARCHAR2(4) → VARCHAR2(8)

* `TICKET_TRANS_SEQ`: VARCHAR2(64) → VARCHAR2(128)

* `IN_STATION`: VARCHAR2(16) → VARCHAR2(32)

* `IN_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `OUT_STATION`: VARCHAR2(16) → VARCHAR2(32)

* `OUT_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `TXN_DATE`: VARCHAR2(8) → VARCHAR2(16)

* `TRX_AMOUNT`: NUMBER(12) → NUMBER(22) NOT NULL DEFAULT 0

* `OVERTIME_AMOUNT`: NUMBER(12) → NUMBER(22) NOT NULL DEFAULT 0

* `TOTAL_AMOUNT`: NUMBER(12) → NUMBER(22) NOT NULL DEFAULT 0

* `ISSUE_CHANNEL_CODE`: VARCHAR2(8) → VARCHAR2(16)

* `SIGN_CHANNEL_CODE`: VARCHAR2(8) → VARCHAR2(16)

* `REMARK`: VARCHAR2(512) → VARCHAR2(1024)

* `CREATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

* `UPDATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

### 4.4 fep-dev-server/pay-txn-schema.sql

#### PAY\_TXN\_DETAIL

* `ID`: NUMBER(20) → NUMBER(22)

* `ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `PAY_TYPE`: VARCHAR2(16) → VARCHAR2(32) NOT NULL DEFAULT 'PAY'

* `PAY_STATUS`: VARCHAR2(16) → VARCHAR2(32) NOT NULL DEFAULT 'INIT'

* `THIRD_USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `CARD_ID`: VARCHAR2(32) → VARCHAR2(64)

* `CARD_TYPE`: VARCHAR2(8) → VARCHAR2(16)

* `PAYMENT_VENDOR`: VARCHAR2(16) → VARCHAR2(32)

* `PAY_CHANNEL_CODE`: VARCHAR2(16) → VARCHAR2(32)

* `REQUEST_SIGN_SEQ`: VARCHAR2(128) → VARCHAR2(256)

* `AMOUNT`: NUMBER(12) → NUMBER(22) NOT NULL DEFAULT 0

* `TOTAL_AMOUNT`: NUMBER(12) → NUMBER(22)

* `CASH_AMOUNT`: NUMBER(12) → NUMBER(22)

* `COUPON_AMOUNT`: NUMBER(12) → NUMBER(22)

* `REFUND_STATUS`: VARCHAR2(16) → VARCHAR2(32) NOT NULL DEFAULT 'NONE'

* `REFUND_AMOUNT`: NUMBER(12) → NUMBER(22) NOT NULL DEFAULT 0

* `MERCHANT_ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `CHANNEL_ORDER_NO`: VARCHAR2(128) → VARCHAR2(256)

* `PAY_USER_ID`: VARCHAR2(128) → VARCHAR2(256)

* `REQUEST_COUNT`: NUMBER(6) → NUMBER(22) NOT NULL DEFAULT 0

* `PAY_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `TXN_DATE`: VARCHAR2(8) → VARCHAR2(16)

* `CREATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

* `UPDATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

#### PAY\_REFUND\_DETAIL

* `ID`: NUMBER(20) → NUMBER(22)

* `REFUND_ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `REFUND_STATUS`: VARCHAR2(16) → VARCHAR2(32) NOT NULL DEFAULT 'INIT'

* `REFUND_AMOUNT`: NUMBER(12) → NUMBER(22) NOT NULL

* `REFUND_REASON`: VARCHAR2(512) → VARCHAR2(1024)

* `MERCHANT_REFUND_NO`: VARCHAR2(64) → VARCHAR2(128)

* `REFUND_NO`: VARCHAR2(64) → VARCHAR2(128)

* `CHANNEL_REFUND_NO`: VARCHAR2(128) → VARCHAR2(256)

* `REQUEST_COUNT`: NUMBER(6) → NUMBER(22) NOT NULL DEFAULT 0

* `REFUND_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `TXN_DATE`: VARCHAR2(8) → VARCHAR2(16)

* `RET_CODE`: VARCHAR2(16) → VARCHAR2(32)

* `RET_MSG`: VARCHAR2(256) → VARCHAR2(512)

* `PAY_CENTER_CODE`: VARCHAR2(16) → VARCHAR2(32)

* `PAY_CENTER_MSG`: VARCHAR2(512) → VARCHAR2(1024)

* `CREATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

* `UPDATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

#### PAY\_CALLBACK\_LOG

* `ID`: NUMBER(20) → NUMBER(22)

* `ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `CALLBACK_TYPE`: VARCHAR2(16) → VARCHAR2(32) NOT NULL

* `CALLBACK_STATUS`: VARCHAR2(16) → VARCHAR2(32)

* `REFUND_ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `MERCHANT_ORDER_NO`: VARCHAR2(64) → VARCHAR2(128)

* `CHANNEL_ORDER_NO`: VARCHAR2(128) → VARCHAR2(256)

* `MERCHANT_REFUND_NO`: VARCHAR2(64) → VARCHAR2(128)

* `REFUND_NO`: VARCHAR2(64) → VARCHAR2(128)

* `CHANNEL_REFUND_NO`: VARCHAR2(128) → VARCHAR2(256)

* `PAY_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `REFUND_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `TOTAL_AMOUNT`: NUMBER(12) → NUMBER(22)

* `CASH_AMOUNT`: NUMBER(12) → NUMBER(22)

* `COUPON_AMOUNT`: NUMBER(12) → NUMBER(22)

* `REFUND_AMOUNT`: NUMBER(12) → NUMBER(22)

* `PAY_USER_ID`: VARCHAR2(128) → VARCHAR2(256)

* `PAYMENT_VENDOR`: VARCHAR2(16) → VARCHAR2(32)

* `TXN_DATE`: VARCHAR2(8) → VARCHAR2(16)

* `RAW_BODY`: CLOB(4000) → CLOB

* `HANDLE_STATUS`: VARCHAR2(16) → VARCHAR2(32)

* `HANDLE_MSG`: VARCHAR2(512) → VARCHAR2(1024)

* `CREATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

### 4.5 ticket-server/ticket-server-schema.sql

#### QRCODE\_STATUS

* `CARD_ID`: VARCHAR2(16) → VARCHAR2(32) NOT NULL

* `USE_COUNT`: NUMBER(10) → NUMBER(22)

* `CHANNEL`: VARCHAR2(4) → VARCHAR2(4)

* `CODE_STATUS`: VARCHAR2(4) → VARCHAR2(4)

* `GATE_IN_TIME`: VARCHAR2(14) → VARCHAR2(14)

* `GATE_IN_STATION`: VARCHAR2(4) → VARCHAR2(4)

* `LAST_TXN_TIME`: VARCHAR2(14) → VARCHAR2(14)

* `LAST_TXN_STATION`: VARCHAR2(4) → VARCHAR2(4)

* `TXN_SEQ`: VARCHAR2(64) → VARCHAR2(64)

* `CREATE_TIME`: TIMESTAMP → TIMESTAMP(6)

* `UPDATE_TIME`: TIMESTAMP → TIMESTAMP(6)

* `GATE_STATUS`: VARCHAR2(32) → VARCHAR2(32)

* `CARD_ID_TAIL`: VARCHAR2(2) → VARCHAR2(4)

### 4.6 ticket-server/qrcode-txn-detail-schema.sql

#### QRCODE\_TXN\_DETAIL

* `ID`: NUMBER(20) → NUMBER(22)

* `DEVICE_ID`: VARCHAR2(16) → VARCHAR2(32)

* `ITP_USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `TRX_TYPE`: VARCHAR2(4) → VARCHAR2(8)

* `ISSUE_CHANNEL_CODE`: VARCHAR2(8) → VARCHAR2(16)

* `SIGN_CHANNEL_CODE`: VARCHAR2(8) → VARCHAR2(16)

* `CARD_ID`: VARCHAR2(32) → VARCHAR2(64)

* `CARD_TYPE`: VARCHAR2(8) → VARCHAR2(16)

* `HANDLE_DATE_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `TXN_DATE`: VARCHAR2(8) → VARCHAR2(16)

* `HANDLE_STATION_CODE`: VARCHAR2(16) → VARCHAR2(32)

* `TRX_AMOUNT`: NUMBER(12) → NUMBER(22)

* `OVERTIME_AMOUNT`: NUMBER(12) → NUMBER(22)

* `LAST_TICKET_STATUS`: VARCHAR2(4) → VARCHAR2(8)

* `HANDLE_RESULT_CODE`: VARCHAR2(8) → VARCHAR2(16)

* `LAST_HANDLE_STATION_CODE`: VARCHAR2(16) → VARCHAR2(32)

* `LAST_HANDLE_DATE_TIME`: VARCHAR2(14) → VARCHAR2(28)

* `TICKET_TRANS_SEQ`: VARCHAR2(64) → VARCHAR2(128)

* `RESERVE1`: VARCHAR2(512) → VARCHAR2(1024)

* `RESERVE2`: VARCHAR2(512) → VARCHAR2(1024)

* `CREATE_TIME`: TIMESTAMP → TIMESTAMP(6) NOT NULL DEFAULT SYSTIMESTAMP

### 4.7 key-server/key-server-schema.sql

#### METRO\_CA\_KEYSTORE

* `ID`: NUMBER(20) → NUMBER(22)

### 4.8 account-server/account-server-schema.sql

#### USER\_ITP\_REG\_INFO

* `ID`: NUMBER(10) → NUMBER(22) NOT NULL

* `CARD_ID`: VARCHAR2(64) → VARCHAR2(128)

* `CARD_TYPE`: VARCHAR2(32) → VARCHAR2(64)

* `THIRD_USER_ID`: VARCHAR2(64) → VARCHAR2(128) NOT NULL

* 添加虚拟列 `THIRD_USER_ID_SUFFIX`

* `MSISDN`: VARCHAR2(32) → VARCHAR2(64)

* `REG_TMS`: TIMESTAMP(6) → TIMESTAMP(6)

* `DEL_YN`: NUMBER(10) → NUMBER(22)

* `DEL_THIRD_USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `UN_REG_TMS`: TIMESTAMP(6) → TIMESTAMP(6)

* `USER_NAME`: VARCHAR2(128) → VARCHAR2(256)

* `USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `CARD_ISSUE_CODE`: VARCHAR2(32) → VARCHAR2(64)

* `THIRD_PAY_ID`: VARCHAR2(64) → VARCHAR2(128)

* `CHANNEL`: VARCHAR2(32) → VARCHAR2(64)

* `REQ_CONTRACT_NO`: VARCHAR2(64) → VARCHAR2(128)

#### USER\_ITP\_REG\_LOG

* `ID`: NUMBER(10) → NUMBER(22) NOT NULL

* `CARD_ID`: VARCHAR2(64) → VARCHAR2(128)

* `CARD_TYPE`: VARCHAR2(32) → VARCHAR2(64)

* `THIRD_USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `MSISDN`: VARCHAR2(32) → VARCHAR2(64)

* `OPER_DATE_TIME`: TIMESTAMP(6) → TIMESTAMP(6)

* `OPER_TYPE`: NUMBER(10) → NUMBER(22)

#### USER\_ACC\_TICKETNO

* `ID`: NUMBER(10) → NUMBER(22) NOT NULL

* `CARD_ID`: VARCHAR2(64) → VARCHAR2(128) NOT NULL

* `THIRD_USER_ID`: VARCHAR2(64) → VARCHAR2(128)

* `INSERT_TMS`: TIMESTAMP(6) → TIMESTAMP(6)

* `REG_TMS`: TIMESTAMP(6) → TIMESTAMP(6)

## 5. 执行策略

### 5.1 修改方式选择

**直接替换完整 CREATE TABLE 语句**，原因：

1. 差异点过多（16 个表，平均每表 10+ 处修改）
2. 涉及主键变更、虚拟列添加，ALTER TABLE 难以优雅处理
3. 直接替换可确保 100% 与 tables.md 一致
4. 避免 ALTER TABLE 在 Oracle 上的限制（如缩小字段长度、添加 NOT NULL 时数据冲突）

### 5.2 执行顺序

1. **先更新 account-server**（USER\_ITP\_REG\_INFO 有分区虚拟列，复杂度最高）
2. **再更新 pay-sign-server**（主键变更，需要重建表数据）
3. **然后更新 fep-dev-server**（GATE\_TXN\_PAY 有分区，修改需谨慎）
4. **最后更新 ticket-server、blacklist-server、key-server**

## 6. 验证方案

### 6.1 静态验证

* 对比修改后的 schema 文件与 tables.md 的字段列表

* 确认每个表的字段数、字段名、类型、长度、非空约束完全一致

### 6.2 动态验证

* 连接 Oracle 数据库执行 `DESC 表名` 对比

* 执行 `SELECT COUNT(*) FROM 表名` 确认数据未丢失

### 6.3 应用验证

* 启动各模块服务，确认 MyBatis 映射正常

* 执行关键业务流程，确认无字段长度溢出异常

## 7. 风险与回滚

### 7.1 主要风险

1. **数据丢失**：重建表时如果备份不全会导致数据丢失
2. **字段缩短导致数据截断**：虽然本次是扩大字段，但需确认 tables.md 本身无错误
3. **主键变更导致重复键**：`APP_USER_PAY_CHANNEL` 主键变更可能暴露重复数据
4. **分区表重建耗时**：GATE\_TXN\_PAY、PAY\_TXN\_DETAIL 等大表重建可能较慢

### 7.2 回滚方案

* 执行前备份所有 schema 文件到 `backup/schema-20240625`

* 对于需要重建的表，执行前先 `CREATE TABLE 表名_BAK AS SELECT * FROM 表名`

* 如出现问题，恢复 schema 文件并从备份表恢复数据

## 8. 假设与前提

1. **tables.md 为权威数据源**：假设 tables.md 是从生产数据库导出的最新结构
2. **开发/测试环境可接受重建**：假设当前修改的是开发/测试环境，可接受表重建
3. **Java 实体类无需修改**：经检查，所有 Java Entity 类均未使用字段长度注解（如 `@Size`、`@Column(length=xxx)`），因此无需修改 Java 代码
4. **MyBatis Mapper XML 无需修改**：Mapper 中使用的是字段名映射，不依赖字段长度
5. **分区策略不变**：仅修改字段定义，不改变现有分区策略


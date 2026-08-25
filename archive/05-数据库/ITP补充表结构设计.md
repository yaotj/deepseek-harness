# 青岛地铁 ITP 平台补充表结构设计

> **设计依据**：
> 1. 《青岛地铁ITP与APP接口规范》（`10-业务规范与标准/青岛地铁-ITP与APP接口规范.md`）
> 2. 《城市轨道交通自动售检票系统技术规范 第9部分 互联网业务规范》Q/QD-SB-J-GS-9.9—2020（`10-业务规范与标准/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.md`）
>
> **目标数据库**：Oracle（与 `tables.md` 既有表约定保持一致）
> **整理时间**：2026-08-18
> **配套脚本**：`ITP补充表建表.sql`

---

## 一、设计概述

### 1.1 设计原则

1. **命名**：表名大写；沿用既有域前缀（`PAY_`/`QRCODE_`/`DAILY_TICKET_`/`APP_`/`ALIPAY_`/`TBL_` 等）；主键 `PK_`、唯一 `UK_`、普通索引 `IDX_` + 表名缩写；序列 `SEQ_表名`。
2. **类型**：字符串 `VARCHAR2(n CHAR)`；金额一律 `NUMBER`、单位**分**；系统时间 `TIMESTAMP(6)`；接口规范定义的报文型时间（`YYYYMMDDHH24MISS`）保留 `VARCHAR2` 原格式，避免转换歧义。
3. **审计字段**：交易/流水表带 `CREATE_TIME`/`UPDATE_TIME`（默认 `SYSTIMESTAMP`）。
4. **幂等**：设备上报、批量推送类表建立业务唯一索引（如 卡号+交易类型+交易时间+交易计数器+设备号）。
5. **分区**：大流量交易表按月 RANGE 分区（`TXN_DATE`，`P202606`~`P202612` + `P_MAX`，与既有分区表一致，分区维护方式见 tables.md 十二章）。
6. **软删除**：沿用既有 `DELETE_FLAG`/`DEL_YN` 约定；日志流水类表不删除、靠分区清理。

### 1.2 设计范围

`tables.md` 已覆盖账户、签约、闸机交易、过闸扣费、支付、参数、密钥、黑名单、日票、ACC 事件源、支付宝出行共 11 个域约 40 张表。本设计**不重复已有表**，只做两件事：

- **新增 27 张表**（第三章）：补齐两份规范中尚无落表支撑的接口与业务；
- **存量表变更建议**（第四章）：4 张既有表的字段扩展（ALTER）。

---

## 二、已有表盘点与接口覆盖

| 域 | 已有表 | 覆盖的接口/业务 |
|----|--------|----------------|
| 账户 | USER_ITP_REG_INFO、USER_ITP_REG_LOG、USER_ACC_TICKETNO、APP_USER_PAY_CHANNEL | IF8A-01 请求开户、IF8A-23/24 支付通道管理、卡号池分配 |
| 签约 | APP_PAY_SIGN_INFO、APP_PAY_SIGN_LOG、APP_PAY_SIGN_REQUEST | IF8A-16 签约请求信息、IF8A-22 签约结果咨询、签约结果通知（含 NOTIFY_STATUS 补偿推送） |
| 票务交易 | QRCODE_STATUS、QRCODE_TXN_DETAIL | IF8A-03 行业数据状态底表、IF1A-01 闸机检票通知、IF8A-19 BLE 检票通知、IF1A-04 查询票卡状态 |
| 过闸扣费 | GATE_TXN_PAY | 出站/超时出站扣费订单（实时扣费、每日 9 点定点扣费） |
| 参数 | TBL_PARA_VERSION、TBL_LINE_INFO、TBL_STATION_INFO、TBL_TSF_INFO、TBL_SECT_INFO、TBL_FARE_MATRIX、TBL_BASE_FARE | IF8A-07/08/09/10/17 线路、车站、最大购票数、票价、版本 |
| 支付 | PAY_TXN_DETAIL、PAY_REFUND_DETAIL、PAY_CALLBACK_LOG | IF8A-11 请求支付、IF8A-12/13 退款、IF8A-18 支付结果查询、支付通道回调 |
| 密钥 | METRO_CA_KEYSTORE、METRO_MEMBER_STATIC_KEY | IF7B-02 地铁 CA 密钥、IF7B-07 HCE 消费密钥（DPK）缓存 |
| 黑名单 | BLACKLIST、BLACKLIST_OPERATE_LOG | if8a_73 查询黑名单、黑名单人工/风控维护 |
| 日票 | DAILY_TICKET_ORDER、DAILY_TICKET_INSTANCE、DAILY_TICKET_PAY_LOG、DAILY_TICKET_REFUND | if8a_60~67/70/71/72 日票与旅游票下单、支付、退款、激活、ACC 通知、小程序同步 |
| ACC 事件源 | TBL_STL_ACCT_INFO、TBL_STL_PERSON_INFO、TBL_STL_TICKET_INFO、TBL_TKT_ES_* | ACC 结算账户、票卡信息、事件源文件处理 |
| 支付宝出行 | ALIPAY_USER_INFO、ALIPAY_CARD_POOL、ALIPAY_SIGN_INFO、ALIPAY_SIGN_LOG、ALIPAY_REG_LOG、ALIPAY_TERMINATION_REQUEST、ALIPAY_PAY_LOG、ALIPAY_REFUND_LOG、ALIPAY_TRAVEL_RECORD | 支付宝出行开户、签约、解约登记、支付、退款、乘车记录 |

---

## 三、新增表设计（27 张）

### 3.1 购票订单域（4 张）

#### 3.1.1 SINGLE_TICKET_ORDER（单程票订单表）

**支撑接口**：IF8A-20 请求下单、IF2A-01 提交单程票订单、IF8A-14 获取激活取票订单、IF8A-15 激活取票订单、IF2A-03 查询支付结果、IF2A-08 扫码取票订单查询、IF8A-12/13 退款。

规范要点：APP/TVM/BOM 三渠道购票共用订单；TVM 生成支付 URL 二维码（显示 180 秒，每秒轮询支付结果）；扫码取票时订单与"设备编码+二维码生成时间+随机因子"绑定；出票故障按未出票金额退款。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_SINGLE_TICKET_ORDER） |
| ORDER_NO | VARCHAR2(64) | Y | 订单号（业务唯一） |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID（APP 购票） |
| USER_ID | VARCHAR2(64) | N | 用户编码（IF8A-20 userId） |
| ORDER_SOURCE | VARCHAR2(8) | Y | 订单来源：APP/TVM/BOM |
| ENTRY_STATION_CODE | VARCHAR2(8) | N | 起点站点代码 |
| EXIT_STATION_CODE | VARCHAR2(8) | N | 终点站点代码 |
| TICKET_PRICE | NUMBER(12) | Y | 票价（分） |
| SINGLE_TICKET_NUM | NUMBER(6) | Y | 购买数量 |
| SINGLE_TICKET_TYPE | VARCHAR2(4) | Y | 0 按站点购票/1 固定票价 |
| ORDER_STATUS | VARCHAR2(32) | Y | ORDERED 已下单/PAID 已支付/ACTIVATED 已激活/ISSUED 已出票/FAULT 出票故障/REFUNDING 退款中/REFUNDED 已退款/CLOSED 已关闭 |
| PAY_CHANNEL_CODE | VARCHAR2(16) | N | 支付通道编码 |
| PAY_DATE | TIMESTAMP(6) | N | 支付时间 |
| ACTIVATE_DATE | TIMESTAMP(6) | N | 激活时间 |
| ACTIVATE_DEVICE_ID | VARCHAR2(32) | N | 激活取票设备编码 |
| ACTIVATE_QR_GEN_DATE | VARCHAR2(20) | N | 设备取票二维码生成时间 |
| ACTIVATE_RANDOM_FACT | VARCHAR2(64) | N | 设备随机因子 |
| ACTUAL_TAKE_TICKET_NUM | NUMBER(6) | N | 实际出票数量 |
| TAKE_TICKET_DATE | TIMESTAMP(6) | N | 出票时间 |
| FAULT_REASON | VARCHAR2(512) | N | 出票故障原因 |
| DEVICE_ID | VARCHAR2(32) | N | 下单设备编码 |
| TXN_DATE | DATE | Y | 订单日期（月分区字段） |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_SINGLE_TICKET_ORDER (ID)
- UK_STO_ORDER_NO (ORDER_NO, TXN_DATE) — 唯一本地分区索引
- IDX_STO_USER_DATE (THIRD_USER_ID, TXN_DATE) — 本地分区索引
- IDX_STO_STATUS_DATE (ORDER_STATUS, TXN_DATE) — 定时退款/补偿扫描
- IDX_STO_ACT_DEVICE (ACTIVATE_DEVICE_ID, ACTIVATE_RANDOM_FACT, TXN_DATE) — TVM 取票订单查询

**分区**：RANGE 按月（P202606~P202612、P_MAX）。

#### 3.1.2 TICKET_ISSUE_DETAIL（出票明细表）

**支撑接口**：IF2A-04 出票结果通知（ticketList 写卡数据）、IF2A-05 出票故障通知；BOM 发售单程票复用。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_TICKET_ISSUE_DETAIL） |
| ORDER_NO | VARCHAR2(64) | Y | 订单号 |
| TICKET_LOGIC_NUM | VARCHAR2(32) | Y | 票卡逻辑号 |
| TRANS_DATE | VARCHAR2(14) | Y | 交易日期 YYYYMMDDHHMMSS |
| TRANS_AMOUNT | NUMBER(12) | N | 交易金额（分） |
| ISSUE_STATUS | VARCHAR2(16) | Y | ISSUED 已出票/FAULT 出票故障 |
| TXN_DATE | DATE | Y | 月分区字段 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- UK_TID_ORDER_TICKET (ORDER_NO, TICKET_LOGIC_NUM, TXN_DATE) — 唯一本地分区索引（断网重传幂等）
- IDX_TID_TICKET (TICKET_LOGIC_NUM, TXN_DATE)

**分区**：RANGE 按月。

#### 3.1.3 TOPUP_ORDER（充值订单表）

**支撑接口**：IF2A-09 请求充值下单、IF2A-06 充值结果通知、IF2A-07 充值失败通知、IF2A-09(BOM) BOM 上报充值结果通知。

规范要点：充值金额+卡内余额>1000 元返回 2004；充值存疑（topupStatus=02）打印故障单转 BOM 处理；取消后支付成功即时退款。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_TOPUP_ORDER） |
| ORDER_NO | VARCHAR2(64) | Y | 订单号（业务唯一） |
| TICKET_LOGIC_NUM | VARCHAR2(32) | Y | 票卡逻辑号 |
| TICKET_PHYSICS_NUM | VARCHAR2(32) | N | 票卡物理号 |
| BEFORE_AMOUNT | NUMBER(12) | N | 充值前金额（分） |
| TRANS_AMOUNT | NUMBER(12) | Y | 请求充值金额（分） |
| AFTER_AMOUNT | NUMBER(12) | N | 充值后余额（分） |
| ORDER_STATUS | VARCHAR2(32) | Y | ORDERED/PAID/TOPUP_SUCCESS/TOPUP_FAIL/SUSPICIOUS 存疑/CANCELED/REFUNDED |
| TOPUP_STATUS | VARCHAR2(4) | N | 00 成功/01 失败/02 存疑/03 取消 |
| DEVICE_ID | VARCHAR2(32) | N | 设备编码 |
| FAULT_OCCUR_DATE | VARCHAR2(20) | N | 故障时间 |
| FAULT_SLIP_SEQ | VARCHAR2(64) | N | 故障凭条号 |
| ERROR_CODE | VARCHAR2(16) | N | 错误代码 |
| ERROR_MESSAGE | VARCHAR2(512) | N | 错误信息 |
| PAY_DATE | TIMESTAMP(6) | N | 支付时间 |
| TXN_DATE | DATE | Y | 月分区字段 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- UK_TO_ORDER_NO (ORDER_NO, TXN_DATE) — 唯一本地分区索引
- IDX_TO_CARD_DATE (TICKET_LOGIC_NUM, TXN_DATE)

**分区**：RANGE 按月。

#### 3.1.4 BOM_NOCASH_ORDER（BOM 非现金收款订单表）

**支撑接口**：IF8A-04(BOM) 请求非现金收款下单（requestGenNoCashOrder）、IF8A-05(BOM) 扫码支付、IF8A-06(BOM) 查询支付结果、IF2A-08(BOM) 业务操作结果通知。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_BOM_NOCASH_ORDER） |
| ORDER_NO | VARCHAR2(64) | Y | 订单号（业务唯一） |
| TRANS_TYPE | VARCHAR2(4) | Y | 02 超时更新/03 超程更新/04 未出站更新/05 无入站更新/06 退票/22 充值/2A 黑名单锁定/2B 解锁/42 行政处理 |
| ADMIN_TRANS_TYPE | VARCHAR2(4) | N | 行政交易类型 01~0A（TRANS_TYPE=42 时必填） |
| OPERATER_ID | VARCHAR2(32) | N | 操作员编码 |
| SHIFT_ID | VARCHAR2(32) | N | 班次序列号 |
| CARD_ID | VARCHAR2(64) | N | 逻辑卡号 |
| TRANS_AMOUNT | NUMBER(12) | Y | 交易金额（分） |
| BOM_OPT_SEQ | VARCHAR2(64) | N | 终端设备操作流水号 |
| DEVICE_ID | VARCHAR2(32) | Y | BOM 设备编码 |
| ORDER_STATUS | VARCHAR2(32) | Y | ORDERED/SUCCESS/FAILED/CANCELED |
| OPT_RESULT | VARCHAR2(16) | N | 业务操作结果（SUCCESS/FAILED） |
| OPT_RESULT_DESC | VARCHAR2(512) | N | 操作结果描述 |
| TRAN_DATE | VARCHAR2(14) | N | 交易时间 |
| TXN_DATE | DATE | Y | 月分区字段 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- UK_BNO_ORDER_NO (ORDER_NO, TXN_DATE) — 唯一本地分区索引
- IDX_BNO_DEVICE_DATE (DEVICE_ID, TXN_DATE)
- IDX_BNO_CARD_DATE (CARD_ID, TXN_DATE)

**分区**：RANGE 按月。

### 3.2 乘车与补站域（4 张）

#### 3.2.1 SUPP_STATION_RECORD（自助补站记录表）

**支撑接口**：IF8A-04(APP) 请求用户自助补站（requestExcessFare）。

规范要点：单日自助补站次数上限 T1（默认 15 次，超限返回 8304）；BOM 更新后的票卡不允许自助更新（8300）；补站成功后异步推送行业数据。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_SUPP_STATION_RECORD） |
| CARD_ID | VARCHAR2(64) | Y | 逻辑卡号 |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID |
| CARD_TYPE | VARCHAR2(16) | N | 卡类型 |
| UPGRADE_AREA_TYPE | VARCHAR2(4) | Y | 01 补进站/02 补出站 |
| UPGRADE_STATION_CODE | VARCHAR2(8) | Y | 补进/出站编码 |
| UPGRADE_REASON | VARCHAR2(256) | N | 更新原因（预留） |
| UPGRADE_DATE_TIME | VARCHAR2(20) | Y | 更新操作时间 YYYYMMDDHH24mmss |
| RESULT_CODE | VARCHAR2(8) | Y | 处理结果码（0000/8300~8304/9999） |
| SUPP_DATE | VARCHAR2(8) | Y | 补站日期 YYYYMMDD（T1 日次数统计） |
| TXN_SEQ | VARCHAR2(64) | N | 行程序列号 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_SUPP_STATION_RECORD (ID)
- UK_SSR_BIZ (CARD_ID, UPGRADE_AREA_TYPE, UPGRADE_DATE_TIME) — 幂等
- IDX_SSR_CARD_DATE (CARD_ID, SUPP_DATE) — 单日次数校验
- IDX_SSR_USER_DATE (THIRD_USER_ID, SUPP_DATE)

#### 3.2.2 SINGLE_SIDE_TXN（单边交易表）

**支撑依据**：业务规范 5.2.1.3~5.2.1.6（不完整交易、单边交易定义、扣费规则、黑名单规则）；对账文件 ITP.EXP 单边交易明细。

规范要点：结算周期 T0（默认 4 天）内缺进站或缺出站记录标记为单边；每日 9 点定点批量匹配扣费；单边交易累计 T2 次（默认 15 笔）列入黑名单；单边明细文件每日 2 点生成 T-2 数据。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_SINGLE_SIDE_TXN） |
| CARD_ID | VARCHAR2(64) | Y | 逻辑卡号 |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID |
| TRANS_SEQ | VARCHAR2(64) | Y | 行程序列号 |
| SIDE_TYPE | VARCHAR2(4) | Y | 1 单边进站/2 单边出站 |
| ENTRY_STATION_CODE | VARCHAR2(8) | N | 进站车站代码 |
| ENTRY_TIME | VARCHAR2(20) | N | 进站时间 |
| ENTRY_DEVICE_CODE | VARCHAR2(32) | N | 进站设备编码 |
| EXIT_STATION_CODE | VARCHAR2(8) | N | 出站车站代码 |
| EXIT_TIME | VARCHAR2(20) | N | 出站时间 |
| EXIT_DEVICE_CODE | VARCHAR2(32) | N | 出站设备编码 |
| ORDER_AMOUNT | NUMBER(12) | N | 订单金额（分） |
| DEBIT_AMOUNT | NUMBER(12) | N | 实际扣款金额（分） |
| DISCOUNT_AMOUNT | NUMBER(12) | N | 优惠金额（分） |
| EXP_TYPE | VARCHAR2(4) | N | 明细类型：1 单边入账/2 单边出账/3 单边入站人工单/4 单边出站人工单/5 自主补进站/6 自主补出站/7~15 其他（对应 ITP.EXP 文件格式） |
| PROCESS_STATUS | VARCHAR2(32) | Y | PENDING 待处理/MATCHED 已匹配完整/SINGLE_DEBITED 已单边扣费/MANUAL_PROCESSED 人工处理/SUPPLEMENTED 已补站 |
| SETTLE_DATE | VARCHAR2(8) | N | 评判结算日期（超过 T0 的日期） |
| DEBIT_ORDER_NO | VARCHAR2(128) | N | 关联扣费订单号（GATE_TXN_PAY.ORDER_NO） |
| PAY_METHOD | VARCHAR2(16) | N | 支付方式 |
| TXN_DATE | DATE | Y | 交易日期（月分区字段） |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_SINGLE_SIDE_TXN (ID)
- UK_SST_CARD_SEQ (CARD_ID, TRANS_SEQ, TXN_DATE) — 唯一本地分区索引（幂等）
- IDX_SST_STATUS_DATE (PROCESS_STATUS, TXN_DATE) — 每日 9 点定点扣费扫描
- IDX_SST_USER_DATE (THIRD_USER_ID, TXN_DATE) — T2 黑名单计数

**分区**：RANGE 按月。

#### 3.2.3 BOM_UPDATE_RECORD（BOM 票卡更新记录表）

**支撑接口**：IF5A-01 请求票卡分析、IF5A-03 请求票卡更新、IF5A-09 HCE 票卡更新结果通知。

规范要点：ITP 按卡号与付费区标分析可做操作（adviceOpt：000 无需更新/018 补进站/006 补出站/005 20 分免费进站更新）；更新后生成更新交易记录，并向 APP_SERVER 与 BOM 双向推送新行业数据。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_BOM_UPDATE_RECORD） |
| CARD_ID | VARCHAR2(64) | Y | 逻辑卡号 |
| CARD_TYPE | VARCHAR2(16) | N | 卡类型 |
| SOURCE | VARCHAR2(8) | Y | 来源：BOM 更新/HCE 通知 |
| UPDATE_TYPE | VARCHAR2(4) | Y | 更新区域 00 非付费区/01 付费区 |
| ADVICE_OPT | VARCHAR2(8) | Y | 操作类型 018/006/005 |
| OPERATER_ID | VARCHAR2(32) | N | 操作员编码 |
| UPDATE_STATION_CODE | VARCHAR2(8) | N | 补站站点 |
| OPT_DATE | VARCHAR2(14) | Y | 更新时间 |
| TRANS_AMOUNT | NUMBER(12) | N | 交易金额（分） |
| CARD_DATA | VARCHAR2(512) | N | 更新后行业数据 |
| HCE_DATA | VARCHAR2(512) | N | HCE 数据（IF5A-09） |
| TICKET_TRANS_SEQ | VARCHAR2(64) | N | 票卡交易计数器 |
| DEVICE_ID | VARCHAR2(32) | Y | BOM 设备编码 |
| TXN_DATE | DATE | Y | 月分区字段 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_BOM_UPDATE_RECORD (ID)
- IDX_BUR_CARD_DATE (CARD_ID, TXN_DATE)
- IDX_BUR_DEVICE_DATE (DEVICE_ID, TXN_DATE)

**分区**：RANGE 按月。

#### 3.2.4 OFFLINE_CODE_RECORD（离线码数据记录表）

**支撑接口**：if8d_03 获取离线码数据（requestNoSignalData）。

规范要点：未进站时返回下一序列号的进出站行业数据；已进站时返回当前序列号的出站行业数据。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_OFFLINE_CODE_RECORD） |
| CARD_ID | VARCHAR2(64) | Y | 逻辑卡号 |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID |
| CARD_TYPE | VARCHAR2(16) | N | 卡类型 |
| TRANS_SEQ | NUMBER(12) | Y | 行程序列号 |
| ENTRY_DATA | VARCHAR2(512) | N | 进站行业数据（HexString） |
| EXIT_DATA | VARCHAR2(512) | N | 出站行业数据（HexString） |
| CHANNEL | VARCHAR2(16) | N | ITP 默认支付渠道 |
| GEN_TIME | TIMESTAMP(6) | Y | 生成时间 |
| STATUS | VARCHAR2(16) | Y | UNUSED/USED/EXPIRED |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_OFFLINE_CODE_RECORD (ID)
- UK_OCR_CARD_SEQ (CARD_ID, TRANS_SEQ)
- IDX_OCR_USER (THIRD_USER_ID)

### 3.3 解约与账户域（5 张）

#### 3.3.1 APP_TERMINATION_REQUEST（地铁 APP 解约请求表）

**支撑接口**：IF8A-06 请求解约、IF8B-02 解约结果通知、if8a_75 直接解绑支付方式、if8a_36 请求移除签约信息。

规范要点：普通解约先标记"解约审核中"，等单边扣费周期（账期）结束后定时任务才向支付通道发起解约，结果推送 APP；直接解绑用于用户长期未登录的强制解绑，立即请求支付渠道；协议移除用于第三方侧协议已失效/钱包解绑，ITP 不请求支付系统。与支付宝侧 ALIPAY_TERMINATION_REQUEST 对应，本表服务地铁 APP 渠道。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| TERMINATION_SEQ | VARCHAR2(128) | Y | 解约流水号（主键） |
| THIRD_USER_ID | VARCHAR2(128) | Y | 第三方用户 ID |
| CARD_ID | VARCHAR2(64) | N | 逻辑卡号 |
| CARD_TYPE | VARCHAR2(64) | N | 卡类型 |
| REQUEST_SIGN_SEQ | VARCHAR2(128) | N | 签约流水号 |
| CHANNEL | VARCHAR2(32) | N | 支付通道编码 |
| OPERATION_TYPE | VARCHAR2(32) | Y | TERMINATE 普通解约/DIRECT_UNBIND 直接解绑/RELEASE 协议移除 |
| STATUS | VARCHAR2(32) | Y | REVIEWING 审核中/PENDING_EXEC 待账期结束执行/EXECUTING 执行中/SUCCESS/FAIL |
| SCHEDULE_DATE | DATE | N | 计划执行日期（账期结束日） |
| TERMINATION_RESULT | VARCHAR2(1024) | N | 结果描述 |
| TERMINATION_TIME | TIMESTAMP(6) | N | 解约完成时间 |
| NOTIFY_STATUS | VARCHAR2(16) | N | IF8B-02 推送状态 PENDING/SUCCESS/FAIL |
| NOTIFY_TIME | TIMESTAMP(6) | N | 最后推送时间 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_APP_TERMINATION_REQUEST (TERMINATION_SEQ)
- IDX_ATR_USER (THIRD_USER_ID, STATUS)
- IDX_ATR_SCHED (STATUS, SCHEDULE_DATE) — 账期结束定时任务扫描

#### 3.3.2 USER_KEY_INFO（用户密钥信息表）

**支撑接口**：IF8A-02 请求同步密钥（keyList）、IF7B-03 请求生成用户 SM2 密钥、IF7B-04 请求签名用户公钥、IF7B-05 请求导出用户私钥。

规范要点：密钥列表含工作密钥（对称，keyId=00）与用户非对称密钥对（keyId=01）；私钥以 KEK 保护下发；公钥默认有效期 7 天；密钥过期客户端重新同步。METRO_MEMBER_STATIC_KEY 仅存 HCE 消费密钥，二维码用户密钥由本表承载。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_USER_KEY_INFO） |
| CARD_ID | VARCHAR2(64) | Y | 逻辑卡号 |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID |
| KEY_ID | VARCHAR2(4) | Y | 密钥编码 00 工作密钥/01 用户非对称密钥对 |
| KEY_TYPE | VARCHAR2(4) | Y | 0 对称密钥/1 非对称密钥 |
| KEY_USER_ID | VARCHAR2(64) | N | 非对称密钥运算的用户标识 |
| KEY_PRIVATE | VARCHAR2(512) | N | 用户私钥（KEK 加密） |
| KEY_PUBLIC | VARCHAR2(512) | N | 用户公钥 X |
| KEY_PUBLIC_EFFECTIVE_DATE | VARCHAR2(20) | N | 公钥有效期（默认 7 天） |
| SIGN_DATA | VARCHAR2(512) | N | 公钥证书签名数据 |
| CA_IDX | VARCHAR2(16) | N | 公钥验签密钥索引 |
| KEY_WRAP_VALUE | VARCHAR2(512) | N | 对称密钥值（KEK 加密） |
| KEY_EFFECTIVE_DATE | VARCHAR2(20) | N | 对称密钥有效期 |
| KVC | VARCHAR2(64) | N | 对称密钥检查值 |
| RESERVE | VARCHAR2(256) | N | 预留字段 |
| KEY_STATUS | VARCHAR2(8) | Y | VALID 有效/EXPIRED 过期/REVOKED 吊销 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_USER_KEY_INFO (ID)
- UK_UKI_CARD_KEYID (CARD_ID, KEY_ID)
- IDX_UKI_USER (THIRD_USER_ID)

#### 3.3.3 KEY_SYNC_LOG（密钥同步日志表）

**支撑接口**：IF1A-02 AGM 密钥同步、IF8A-02 APP 密钥同步、BOM 密钥同步流程。

规范要点：AGM 每日运营开始或重启时同步 ITP CA 公钥；失败每 2 小时重试；运营期间 ITP 不更换密钥。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_KEY_SYNC_LOG） |
| SYNC_TYPE | VARCHAR2(8) | Y | AGM/APP/BOM |
| DEVICE_ID | VARCHAR2(32) | N | 设备编码（AGM/BOM） |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID（APP） |
| CARD_ID | VARCHAR2(64) | N | 逻辑卡号 |
| KEY_BATCH_NUMBER | NUMBER(10) | N | 密钥批次号 |
| REQUEST_BODY | CLOB | N | 请求报文快照 |
| RESPONSE_BODY | CLOB | N | 应答报文快照 |
| RESULT_CODE | VARCHAR2(16) | N | 结果码 |
| RESULT_MSG | VARCHAR2(512) | N | 结果描述 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_KEY_SYNC_LOG (ID)
- IDX_KSL_DEVICE (DEVICE_ID, CREATE_TIME)
- IDX_KSL_USER (THIRD_USER_ID, CREATE_TIME)

#### 3.3.4 USER_PHONE_CHANGE_LOG（更换手机号日志表）

**支撑接口**：if8a_76 更换手机号（changePhone）。

规范要点：同步更新用户表、普通卡池表、日票卡池表三处手机号。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_USER_PHONE_CHANGE_LOG） |
| THIRD_USER_ID | VARCHAR2(128) | Y | 第三方用户 ID |
| OLD_PHONE | VARCHAR2(64) | N | 原手机号 |
| NEW_PHONE | VARCHAR2(64) | Y | 新手机号 |
| RESULT_CODE | VARCHAR2(16) | N | 结果码 |
| RESULT_MSG | VARCHAR2(512) | N | 结果描述 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_USER_PHONE_CHANGE_LOG (ID)
- IDX_UPCL_USER (THIRD_USER_ID, CREATE_TIME)

#### 3.3.5 CREDIT_QUERY_LOG（征信查询日志表）

**支撑接口**：IF8A-21 信用能力咨询（生码前拦截欠款用户）、征信状态更新（扣费成功后查询征信，异常推送 APP）。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_CREDIT_QUERY_LOG） |
| QUERY_TYPE | VARCHAR2(32) | Y | CONTRACT_ADVISORY 信用能力咨询/CREDIT_STATUS 征信状态更新 |
| THIRD_USER_ID | VARCHAR2(128) | Y | 第三方用户 ID |
| PAYMENT_VENDOR | VARCHAR2(16) | Y | 支付类型 03/04/06/07/0B |
| REQUEST_SIGN_SEQ | VARCHAR2(128) | N | 签约请求流水号 |
| CARD_ID | VARCHAR2(64) | N | 逻辑卡号 |
| CREDIT_RESULT | VARCHAR2(16) | N | 征信结果 PASS/REJECT |
| RESULT_CODE | VARCHAR2(16) | N | 结果码 |
| RESULT_MSG | VARCHAR2(512) | N | 结果描述 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_CREDIT_QUERY_LOG (ID)
- IDX_CQL_USER (THIRD_USER_ID, CREATE_TIME)

### 3.4 推送通知域（2 张）

#### 3.4.1 APP_PUSH_NOTIFY_LOG（APP 推送通知日志表）

**支撑接口**：IF8B-01 行业数据推送、IF8B-02 解约结果通知、IF8B-04 退款结果通知、IF8B-05 支付结果通知、IF8B-06 出票成功/签约结果通知、IF8B-07 出票故障通知、IF8B-08 闸机 CA 公钥变更通知、卡片行程通知、行程订单通知、多日票次数扣减通知、征信状态更新。

规范要点：ITP→APP_SERVER 的所有异步推送统一落表，失败重试补发；行业数据推送携带 cardData 及进出站设备码。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_APP_PUSH_NOTIFY_LOG） |
| NOTIFY_TYPE | VARCHAR2(32) | Y | CARD_DATA 行业数据/TERMINATION_RESULT 解约结果/REFUND_RESULT 退款结果/PAYMENT_RESULT 支付结果/TAKE_TICKET_RESULT 出票成功/SIGN_RESULT 签约结果/TAKE_TICKET_FAULT 出票故障/CA_KEY_CHANGE CA公钥变更/CARD_TRAN 行程通知/ORDER_PAY 行程订单通知/COUNTING_TIMES 计次扣减/CREDIT_STATUS 征信状态 |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID |
| CARD_ID | VARCHAR2(64) | N | 逻辑卡号 |
| ORDER_NO | VARCHAR2(128) | N | 订单号 |
| BIZ_KEY | VARCHAR2(128) | N | 业务键（行程序列号/密钥批次号等） |
| REQUEST_BODY | CLOB | N | 推送报文快照 |
| NOTIFY_STATUS | VARCHAR2(16) | Y | PENDING/SUCCESS/FAIL |
| RETRY_COUNT | NUMBER(6) | N | 重试次数，默认 0 |
| LAST_NOTIFY_TIME | TIMESTAMP(6) | N | 最后推送时间 |
| RESULT_CODE | VARCHAR2(16) | N | APP 应答码 |
| RESULT_MSG | VARCHAR2(512) | N | APP 应答描述 |
| TXN_DATE | DATE | Y | 月分区字段 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_APP_PUSH_NOTIFY_LOG (ID)
- IDX_APNL_TYPE_STATUS (NOTIFY_TYPE, NOTIFY_STATUS, TXN_DATE) — 本地分区索引，补发扫描
- IDX_APNL_USER_DATE (THIRD_USER_ID, TXN_DATE)
- IDX_APNL_ORDER_DATE (ORDER_NO, TXN_DATE)

**分区**：RANGE 按月。

#### 3.4.2 BLACKLIST_PUSH_LOG（黑名单推送日志表）

**支撑接口**：IF8B-03 黑名单结果通知（定期推送，每批最多 25 笔）。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_BLACKLIST_PUSH_LOG） |
| BATCH_NO | VARCHAR2(64) | Y | 推送批次号 |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID |
| CARD_ID | VARCHAR2(64) | Y | 逻辑卡号 |
| CARD_TYPE | VARCHAR2(16) | N | 卡类型 |
| BLACK_LIST_TYPE | VARCHAR2(4) | Y | 1 加入黑名单/2 移除黑名单 |
| OPTION_DATE | VARCHAR2(20) | Y | 操作时间 |
| EXPIRE_TIME | VARCHAR2(20) | N | 黑名单有效期（加入时有效） |
| PUSH_STATUS | VARCHAR2(16) | Y | PENDING/SUCCESS/FAIL |
| PUSH_TIME | TIMESTAMP(6) | N | 推送时间 |
| RESULT_CODE | VARCHAR2(16) | N | 应答码 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_BLACKLIST_PUSH_LOG (ID)
- IDX_BPL_BATCH (BATCH_NO)
- IDX_BPL_CARD (CARD_ID)
- IDX_BPL_STATUS (PUSH_STATUS)

### 3.5 风控与结算域（3 张）

#### 3.5.1 RISK_THRESHOLD_CONFIG（风控阈值参数表）

**支撑依据**：业务规范 5.2.1.1 默认阈值（参数化配置，可动态调整）。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| THRESHOLD_CODE | VARCHAR2(16) | Y | 阈值编码（主键）：T0/T1/T2/T3 |
| THRESHOLD_NAME | VARCHAR2(128) | Y | 阈值名称 |
| THRESHOLD_VALUE | NUMBER(12) | Y | 阈值（T0=4 天、T1=15 次、T2=15 笔、T3=0 次） |
| UNIT | VARCHAR2(16) | N | 单位（天/次/笔） |
| REMARK | VARCHAR2(512) | N | 备注 |
| UPDATE_USER | VARCHAR2(32) | N | 最后更新人 |
| UPDATE_TIME | TIMESTAMP(6) | N | 最后更新时间 |

**索引**：PK_RISK_THRESHOLD_CONFIG (THRESHOLD_CODE)

#### 3.5.2 SETTLE_FILE_LOG（结算对账文件生成日志表）

**支撑依据**：业务规范 7.1 车票交易对账文件下发（ITP.EXP 单边明细、ITP.PAY 统计汇总、ITP.BUS 商业优惠；每日 2 点统计 T-2 日 2 点至 T-1 日 2 点数据，生成至 FTP）。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_SETTLE_FILE_LOG） |
| FILE_TYPE | VARCHAR2(8) | Y | EXP 单边明细/PAY 统计汇总/BUS 商业优惠 |
| FILE_NAME | VARCHAR2(128) | Y | 文件名（如 ITP.EXP.20190818） |
| STAT_DATE | VARCHAR2(8) | Y | 统计业务日期 |
| FILE_PATH | VARCHAR2(256) | N | FTP 存放路径 |
| RECORD_COUNT | NUMBER(12) | N | 记录条数 |
| GENERATE_STATUS | VARCHAR2(16) | Y | INIT/SUCCESS/FAIL |
| GENERATE_TIME | TIMESTAMP(6) | N | 生成时间 |
| ERROR_MSG | VARCHAR2(1024) | N | 失败原因 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_SETTLE_FILE_LOG (ID)
- UK_SFL_TYPE_DATE (FILE_TYPE, STAT_DATE)
- IDX_SFL_STATUS (GENERATE_STATUS)

#### 3.5.3 SETTLE_DAILY_SUMMARY（日结算汇总表）

**支撑依据**：ITP.PAY 统计汇总文件内容落库（20 个统计字段），供清结算查询与报表使用。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_SETTLE_DAILY_SUMMARY） |
| STAT_DATE | VARCHAR2(8) | Y | 日期 |
| LINE_CODE | VARCHAR2(8) | Y | 线路 |
| STATION_CODE | VARCHAR2(8) | Y | 车站 |
| DEVICE_ID | VARCHAR2(32) | Y | 设备编号 |
| PAY_METHOD | VARCHAR2(16) | Y | 支付方式 |
| ISSUE_COUNT | NUMBER(12) | N | BOM/TVM 发售笔数 |
| ISSUE_AMOUNT | NUMBER(16) | N | BOM/TVM 发售金额（分） |
| TOPUP_COUNT | NUMBER(12) | N | BOM/TVM 充值笔数 |
| TOPUP_AMOUNT | NUMBER(16) | N | BOM/TVM 充值金额（分） |
| TRAVEL_TICKET_COUNT | NUMBER(12) | N | 旅游票张数（发售） |
| TRAVEL_TICKET_AMOUNT | NUMBER(16) | N | 旅游票金额（分） |
| GATE_COUNT | NUMBER(12) | N | 过闸笔数 |
| GATE_AMOUNT | NUMBER(16) | N | 过闸金额（分） |
| APP_BUY_COUNT | NUMBER(12) | N | APP 购票笔数 |
| APP_BUY_AMOUNT | NUMBER(16) | N | APP 购票金额（分） |
| BOM_ADMIN_COUNT | NUMBER(12) | N | BOM 行政处理笔数 |
| BOM_ADMIN_AMOUNT | NUMBER(16) | N | BOM 行政处理金额（分） |
| SINGLE_SIDE_COUNT | NUMBER(12) | N | 单边交易笔数 |
| SINGLE_SIDE_AMOUNT | NUMBER(16) | N | 单边交易金额（分） |
| BOM_PROCESS_COUNT | NUMBER(12) | N | BOM 处理笔数 |
| BOM_PROCESS_AMOUNT | NUMBER(16) | N | BOM 处理金额（分） |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_SETTLE_DAILY_SUMMARY (ID)
- UK_SDS_DIM (STAT_DATE, LINE_CODE, STATION_CODE, DEVICE_ID, PAY_METHOD)

### 3.6 设备与 ACC 对接域（3 张）

#### 3.6.1 DEVICE_INFO（设备信息表）

**支撑接口**：IF1A-03 AGM 设备心跳、IF2A-07(BOM) 设备心跳、IF2A-10(TVM) 设备心跳；错误码 2001 非法设备校验。

规范要点：设备每分钟心跳；心跳异常时 TVM 暂停互联网售票、BOM 暂停非现金支付。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| DEVICE_ID | VARCHAR2(32) | Y | 设备编码（主键） |
| DEVICE_TYPE | VARCHAR2(4) | Y | 02 TVM/03 BOM/04 AGM |
| LINE_CODE | VARCHAR2(8) | N | 线路代码 |
| STATION_CODE | VARCHAR2(8) | N | 车站代码 |
| DEVICE_STATUS | VARCHAR2(16) | Y | NORMAL 正常/SUSPENDED 业务暂停/OFFLINE 离线 |
| LAST_HEARTBEAT_TIME | TIMESTAMP(6) | N | 最后心跳时间 |
| REMARK | VARCHAR2(256) | N | 备注 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_DEVICE_INFO (DEVICE_ID)
- IDX_DI_STATION (STATION_CODE, DEVICE_TYPE)

#### 3.6.2 ACC_MODE_BROADCAST（ACC 降级模式广播表）

**支撑接口**：IF7A-01 接收模式广播（receiveModeBroadcast）。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_ACC_MODE_BROADCAST） |
| REQUEST_TIME | VARCHAR2(20) | N | 请求时间 |
| MODE_NOTE_ID | VARCHAR2(64) | Y | 发生降级模式的车站/终端设备 ID |
| MODE_OPERATION_ID | VARCHAR2(64) | Y | 降级运营模式 ID |
| MODE_CHANGE_DATE | VARCHAR2(20) | Y | 降级发生时间 YYYYMMDDHH24MISS |
| RECEIVE_TIME | TIMESTAMP(6) | Y | 接收时间 |

**索引**：
- PK_ACC_MODE_BROADCAST (ID)
- IDX_AMB_NOTE (MODE_NOTE_ID, MODE_CHANGE_DATE)

#### 3.6.3 ACC_FILE_NOTIFY_LOG（ACC 文件通知日志表）

**支撑接口**：IF7A-02 文件通知（receiveFileNoti），含 ESLOGIC 逻辑卡号文件（文件名 ESLOGIC.日期.顺序号）等。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_ACC_FILE_NOTIFY_LOG） |
| REQUEST_SEQ | VARCHAR2(128) | Y | 请求批次号（唯一） |
| FILE_TYPE | VARCHAR2(8) | Y | 文件类型 |
| FILE_NAME | VARCHAR2(128) | Y | 文件名 |
| PROCESS_STATUS | VARCHAR2(16) | Y | RECEIVED/PROCESSING/SUCCESS/FAIL |
| PROCESS_TIME | TIMESTAMP(6) | N | 处理时间 |
| RESULT_CODE | VARCHAR2(16) | N | 结果码 |
| RESULT_MSG | VARCHAR2(512) | N | 结果描述 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_ACC_FILE_NOTIFY_LOG (ID)
- UK_AFNL_SEQ (REQUEST_SEQ)
- IDX_AFNL_NAME (FILE_NAME)

### 3.7 补款与统计域（4 张）

#### 3.7.1 SUPPLEMENT_PAY_ORDER（补款订单表）

**支撑接口**：if8a_26 请求补款下单（requestPayOrder）。

规范要点：商品编码固定 001、数量固定 1；orderNoList 为原乘车订单数组；补款成功后需回写原乘车订单状态（关联关系见 3.7.2）。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_SUPPLEMENT_PAY_ORDER） |
| ORDER_NO | VARCHAR2(64) | Y | 补款单号（业务唯一） |
| THIRD_USER_ID | VARCHAR2(128) | N | 第三方用户 ID |
| GOODS_CODE | VARCHAR2(8) | Y | 商品编码（固定 001） |
| PRICE | NUMBER(12) | Y | 价格（分） |
| QUANTITY | NUMBER(6) | Y | 数量（固定 1） |
| PAY_STATUS | VARCHAR2(32) | Y | INIT/PAYING/SUCCESS/FAIL |
| PAY_DATE | TIMESTAMP(6) | N | 支付时间 |
| TXN_DATE | DATE | Y | 月分区字段 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_SUPPLEMENT_PAY_ORDER (ID)
- UK_SPO_ORDER_NO (ORDER_NO, TXN_DATE) — 唯一本地分区索引
- IDX_SPO_USER_DATE (THIRD_USER_ID, TXN_DATE)

**分区**：RANGE 按月。

#### 3.7.2 SUPPLEMENT_PAY_ORDER_REL（补款订单关联表）

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_SUPPLEMENT_PAY_ORDER_REL） |
| SUPP_ORDER_NO | VARCHAR2(64) | Y | 补款单号 |
| ORIGIN_ORDER_NO | VARCHAR2(128) | Y | 原乘车订单号 |
| TXN_DATE | DATE | Y | 月分区字段 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_SUPPLEMENT_PAY_ORDER_REL (ID)
- IDX_SPOR_SUPP (SUPP_ORDER_NO, TXN_DATE)
- IDX_SPOR_ORIGIN (ORIGIN_ORDER_NO, TXN_DATE)

**分区**：RANGE 按月。

#### 3.7.3 TRAVEL_BILL_STATISTICS（行程月度账单统计表）

**支撑接口**：if8a_43 查询月度账单（queryTravelBillStatistics）。按月预聚合快照，避免实时全量扫描交易明细。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_TRAVEL_BILL_STATISTICS） |
| STAT_MONTH | VARCHAR2(8) | Y | 统计月份 yyyyMM |
| THIRD_USER_ID | VARCHAR2(128) | Y | 第三方用户 ID |
| METRO_TOTAL_AMOUNT | NUMBER(16) | N | 地铁出行总消费（分） |
| METRO_TOTAL_SAVE_AMOUNT | NUMBER(16) | N | 地铁出行总节省（分） |
| EXPECT_TOTAL_AMOUNT | NUMBER(16) | N | 预计总消费（分） |
| EXPECT_SAVE_AMOUNT | NUMBER(16) | N | 预计总节省（分） |
| METRO_TOTAL_COUNT | NUMBER(12) | N | 地铁出行总次数 |
| LAST_TRAVEL_DATE | VARCHAR2(32) | N | 最晚出行日期 yyyy.MM-dd HH:mm |
| LAST_TRAVEL_ENDING | VARCHAR2(128) | N | 最晚出行出站站点名称 |
| DISCOUNT_COMPARISON | VARCHAR2(32) | N | 折扣节省占比 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | Y | 更新时间 |

**索引**：
- PK_TRAVEL_BILL_STATISTICS (ID)
- UK_TBS_MONTH_USER (STAT_MONTH, THIRD_USER_ID)

#### 3.7.4 CONTRACT_EXCEPTION_LOG（签约异常日志表）

**支撑接口**：接收签约异常状态通知（receiveAgreementException）。

规范要点：ITP 扣费失败后判断失败原因，属签约异常的记录并通知 APP。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_CONTRACT_EXCEPTION_LOG） |
| THIRD_USER_ID | VARCHAR2(128) | Y | 第三方用户 ID |
| CARD_ID | VARCHAR2(64) | N | 逻辑卡号 |
| TRANS_SEQ | VARCHAR2(64) | N | 行程序列号 |
| EXCEPTION_TYPE | VARCHAR2(32) | N | 异常类型 |
| EXCEPTION_MSG | VARCHAR2(512) | N | 异常描述 |
| NOTIFY_STATUS | VARCHAR2(16) | Y | PENDING/SUCCESS/FAIL |
| NOTIFY_TIME | TIMESTAMP(6) | N | 通知时间 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_CONTRACT_EXCEPTION_LOG (ID)
- IDX_CEL_USER (THIRD_USER_ID, CREATE_TIME)
- IDX_CEL_NOTIFY (NOTIFY_STATUS)

### 3.8 客流域（2 张）

#### 3.8.1 PASSENGER_FLOW_BATCH（客流数据批次表）

**支撑接口**：5 分钟客流数据更新（receive/passengerFlow）。

规范要点：ITP 每 5 分钟推送各站进站客流；passengerData 为 AES 加密 JSON（key 站点代码、value 客流量）；timeSeq=1 时清理明细库。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_PASSENGER_FLOW_BATCH） |
| TIME_SEQ | NUMBER(12) | Y | 时间序列（=1 时清理明细数据） |
| TIME_START | VARCHAR2(20) | Y | 开始时间 |
| TIME_END | VARCHAR2(20) | Y | 结束时间 |
| TRANS_TYPE | VARCHAR2(4) | Y | 进出站类型 |
| TRANS_DATE | VARCHAR2(8) | Y | 日期 |
| RAW_DATA | CLOB | N | 原始客流数据（AES 密文） |
| PROCESS_STATUS | VARCHAR2(16) | Y | RECEIVED/PROCESSED/FAIL |
| RECEIVE_TIME | TIMESTAMP(6) | Y | 接收时间 |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_PASSENGER_FLOW_BATCH (ID)
- UK_PFB_BATCH (TRANS_DATE, TRANS_TYPE, TIME_SEQ)
- IDX_PFB_STATUS (PROCESS_STATUS)

#### 3.8.2 PASSENGER_FLOW_DETAIL（客流明细表）

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ID | NUMBER(22) | Y | 主键（SEQ_PASSENGER_FLOW_DETAIL） |
| BATCH_ID | NUMBER(22) | Y | 关联批次 ID |
| STATION_CODE | VARCHAR2(8) | Y | 站点代码 |
| FLOW_COUNT | NUMBER(12) | Y | 客流量 |
| TRANS_TYPE | VARCHAR2(4) | Y | 进出站类型 |
| TRANS_DATE | VARCHAR2(8) | Y | 日期 |
| TIME_START | VARCHAR2(20) | N | 开始时间 |
| TIME_END | VARCHAR2(20) | N | 结束时间 |
| TXN_DATE | DATE | Y | 月分区字段（便于按分区清理） |
| CREATE_TIME | TIMESTAMP(6) | Y | 创建时间 |

**索引**：
- PK_PASSENGER_FLOW_DETAIL (ID)
- IDX_PFD_STATION_DATE (STATION_CODE, TRANS_DATE, TXN_DATE) — 本地分区索引
- IDX_PFD_BATCH (BATCH_ID, TXN_DATE)

**分区**：RANGE 按月。

---

## 四、存量表变更建议（ALTER）

### 4.1 GATE_TXN_PAY（过闸扣费订单表）

IF8A-05/if8a_34 交易记录查询应答字段在现表中缺失，建议补充：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ORDER_EXP_TYPE | VARCHAR2(4) | 订单异常类型：0 正常/1 单边账(入站)/2 单边账(出站)/3 单边入站人工单/4 单边出站人工单/5 双段计费行程超时 |
| DISCOUNT_FEE | NUMBER(12) | 优惠金额（分） |
| DISCOUNT_INFO | VARCHAR2(2000) | 优惠详情（JSON 数组） |
| COMPANION_FLAG | VARCHAR2(4) | 同行票标识 Y/N |
| TICKET_CODE | VARCHAR2(64) | 日票票号 |
| COUNTING_TIMES | NUMBER(6) | 计次票扣减次数 |
| COUNTING_FLAG | VARCHAR2(4) | 是否计次票 Y/N |
| OFFLINE_FLAG | VARCHAR2(4) | 离线码标识 |
| ATTRIBUTABLE_PARTY | VARCHAR2(64) | 订单应收商户 |
| RECEIVING_PARTY | VARCHAR2(64) | 订单实收商户 |
| ENTRY_DEVICE_CODE | VARCHAR2(32) | 进站设备码（IF8B-01 行业数据推送需要） |

### 4.2 BLACKLIST（黑名单表）

IF8B-03 黑名单通知含有效期字段，业务规范 T3 需累计进黑名单次数，建议补充：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| EXPIRE_TIME | TIMESTAMP(6) | 黑名单有效期 |
| IN_BLACK_COUNT | NUMBER(6) | 累计进入黑名单次数（达到 T3 永久禁用） |
| BLACK_SOURCE | VARCHAR2(16) | 来源：RISK 风控自动/MANUAL 人工 |
| PERMANENT_YN | VARCHAR2(4) | 永久禁用标识 Y/N |

### 4.3 DAILY_TICKET_ORDER（日票订单表）

if8a_73 免费下单（兑换码/手动发放）需要记录兑换码，建议补充：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| EXCHANGE_CODE | VARCHAR2(64) | 兑换码（payChannelCode=02 兑换码下单时记录） |

同时 ORDER_TYPE 注释扩展为 `1 日票/2 旅游票`（旅游票为聚合单，子票对应 DAILY_TICKET_INSTANCE 记录）。

### 4.4 USER_ITP_REG_INFO（用户注册信息表）

if8a_42 用户销户已由 DEL_YN/DEL_THIRD_USER_ID/UN_REG_TMS 覆盖，无需变更；if8a_35 用户账务信息（未支付数/扣费失败数）通过对 GATE_TXN_PAY、PAY_TXN_DETAIL 聚合查询实现，不落表。

---

## 五、接口—表总体映射

| 接口 | 名称 | 落表 |
|------|------|------|
| IF8A-01 | 请求开户 | USER_ITP_REG_INFO、USER_ITP_REG_LOG、USER_ACC_TICKETNO |
| IF8A-02 | 请求同步密钥 | **USER_KEY_INFO**、METRO_MEMBER_STATIC_KEY、**KEY_SYNC_LOG** |
| IF8A-03 | 请求行业数据 | QRCODE_STATUS（状态），密文实时由 ACC 加密 |
| IF8A-04 | 请求自助补站 | **SUPP_STATION_RECORD**、QRCODE_STATUS |
| IF8A-05 | 查询交易记录 | QRCODE_TXN_DETAIL、GATE_TXN_PAY（+4.1 扩展） |
| IF8A-06 | 请求解约 | APP_PAY_SIGN_INFO、**APP_TERMINATION_REQUEST** |
| IF8A-07/08/09/10 | 线路/车站/最大张数/票价 | TBL_LINE_INFO、TBL_STATION_INFO、TBL_FARE_MATRIX、TBL_BASE_FARE |
| IF8A-11 | 请求支付 | PAY_TXN_DETAIL |
| IF8A-12/13 | 请求退款/退款查询 | PAY_REFUND_DETAIL |
| IF8A-14/15 | 获取/激活取票订单 | **SINGLE_TICKET_ORDER** |
| IF8A-16 | 请求签约信息 | APP_PAY_SIGN_INFO、APP_PAY_SIGN_REQUEST |
| IF8A-17 | 线路站点代码版本 | TBL_PARA_VERSION |
| IF8A-18 | 支付结果查询 | PAY_TXN_DETAIL、PAY_CALLBACK_LOG |
| IF8A-19 | BLE 闸机检票通知 | QRCODE_TXN_DETAIL |
| IF8A-20 | 请求下单 | **SINGLE_TICKET_ORDER** |
| IF8A-21 | 信用能力咨询 | **CREDIT_QUERY_LOG**、APP_PAY_SIGN_INFO |
| IF8A-22 | 签约结果咨询 | APP_PAY_SIGN_INFO |
| IF8A-23/24 | 添加/默认支付通道 | APP_USER_PAY_CHANNEL |
| IF8A-25 | 请求实名（已停用） | — |
| IF8A-26 | 请求补款下单 | **SUPPLEMENT_PAY_ORDER、SUPPLEMENT_PAY_ORDER_REL** |
| IF8A-29 | 查询上次行程 | QRCODE_TXN_DETAIL、QRCODE_STATUS |
| IF8A-32 | 查询是否单边 | QRCODE_STATUS、**SINGLE_SIDE_TXN** |
| IF8A-34/35 | 订单详情/账务信息 | GATE_TXN_PAY（+扩展）、PAY_TXN_DETAIL 聚合 |
| IF8A-36 | 移除签约信息 | **APP_TERMINATION_REQUEST**（OPERATION_TYPE=RELEASE） |
| IF8A-37 | 购票订单详情 | **SINGLE_TICKET_ORDER** |
| IF8A-38 | 验证符合条件行程 | QRCODE_TXN_DETAIL |
| IF8A-41 | 账单统计 | GATE_TXN_PAY、PAY_TXN_DETAIL 聚合 |
| IF8A-42 | 用户销户 | USER_ITP_REG_INFO（DEL_YN）、USER_ITP_REG_LOG |
| IF8A-43 | 月度账单 | **TRAVEL_BILL_STATISTICS** |
| IF8A-60~65 | 日票下单/支付/查询/退款/取消 | DAILY_TICKET_ORDER、DAILY_TICKET_INSTANCE、DAILY_TICKET_PAY_LOG、DAILY_TICKET_REFUND |
| IF8A-67 | 日票激活 | DAILY_TICKET_INSTANCE |
| IF8A-70 | 旅游票下单 | DAILY_TICKET_ORDER（ORDER_TYPE=2）、DAILY_TICKET_INSTANCE |
| IF8A-71 | 通知 ACC 车票已使用 | DAILY_TICKET_INSTANCE（ACC_NOTICE_STATUS） |
| IF8A-72 | 小程序票状态同步 | DAILY_TICKET_ORDER |
| IF8A-73 | 查询黑名单/免费下单 | BLACKLIST（+4.2 扩展）/ DAILY_TICKET_ORDER（+4.3 扩展） |
| IF8A-75 | 直接解绑 | **APP_TERMINATION_REQUEST**（OPERATION_TYPE=DIRECT_UNBIND） |
| IF8A-76 | 更换手机号 | USER_ITP_REG_INFO、**USER_PHONE_CHANGE_LOG** |
| IF8A-77 | 更换渠道码默认支付方式 | APP_USER_PAY_CHANNEL |
| IF8D-03 | 获取离线码数据 | **OFFLINE_CODE_RECORD** |
| IF8B-01 | 行业数据推送 | **APP_PUSH_NOTIFY_LOG** |
| IF8B-02 | 解约结果通知 | **APP_TERMINATION_REQUEST、APP_PUSH_NOTIFY_LOG** |
| IF8B-03 | 黑名单结果通知 | **BLACKLIST_PUSH_LOG** |
| IF8B-04~08 | 退款/支付/出票/签约/故障/CA 变更通知 | **APP_PUSH_NOTIFY_LOG**（签约通知亦可走 APP_PAY_SIGN_REQUEST.NOTIFY_STATUS） |
| 卡片行程/行程订单/计次扣减/签约异常/征信通知 | 新增推送 | **APP_PUSH_NOTIFY_LOG、CONTRACT_EXCEPTION_LOG、CREDIT_QUERY_LOG** |
| 5 分钟客流 | 客流推送 | **PASSENGER_FLOW_BATCH、PASSENGER_FLOW_DETAIL** |
| IF1A-01 | 闸机检票通知 | QRCODE_TXN_DETAIL、QRCODE_STATUS |
| IF1A-02 | AGM 密钥同步 | METRO_CA_KEYSTORE、**KEY_SYNC_LOG** |
| IF1A-03 | AGM 设备心跳 | **DEVICE_INFO** |
| IF1A-04 | 查询票卡状态 | QRCODE_STATUS |
| IF2A-01 | 提交单程票订单 | **SINGLE_TICKET_ORDER** |
| IF2A-03 | 查询支付结果 | PAY_TXN_DETAIL |
| IF2A-04/05 | 出票结果/故障通知 | **TICKET_ISSUE_DETAIL、SINGLE_TICKET_ORDER** |
| IF2A-06/07 | 充值结果/失败通知 | **TOPUP_ORDER** |
| IF2A-08 | 扫码取票订单查询 | **SINGLE_TICKET_ORDER** |
| IF2A-09 | 请求充值下单 | **TOPUP_ORDER** |
| IF2A-10 | TVM 设备心跳 | **DEVICE_INFO** |
| IF2A-11 | 扫码支付 | PAY_TXN_DETAIL |
| IF5A-01 | 请求票卡分析 | QRCODE_STATUS、QRCODE_TXN_DETAIL |
| IF5A-03 | 请求票卡更新 | **BOM_UPDATE_RECORD**、QRCODE_STATUS |
| IF5A-09 | HCE 票卡更新通知 | **BOM_UPDATE_RECORD** |
| IF8A-04/05/06(BOM) | 非现金收款/扫码支付/支付查询 | **BOM_NOCASH_ORDER**、PAY_TXN_DETAIL |
| IF2A-07(BOM) | BOM 心跳 | **DEVICE_INFO** |
| IF2A-08(BOM) | 业务操作结果通知 | **BOM_NOCASH_ORDER** |
| IF2A-09(BOM) | BOM 上报充值结果 | **TOPUP_ORDER** |
| IF7A-01 | 接收模式广播 | **ACC_MODE_BROADCAST** |
| IF7A-02 | 文件通知 | **ACC_FILE_NOTIFY_LOG** |
| IF7B-01 | 请求逻辑卡号 | USER_ACC_TICKETNO |
| IF7B-02 | 请求生成 CA 密钥 | METRO_CA_KEYSTORE |
| IF7B-03/04/05 | 用户 SM2 密钥/公钥签名/私钥导出 | **USER_KEY_INFO** |
| IF7B-06 | 请求签名行业数据 | 实时调用，不落表 |
| IF7B-07/08 | HCE 消费密钥/发售 HCE 票 | METRO_MEMBER_STATIC_KEY、USER_ITP_REG_INFO（HCE_DATA） |
| 风控阈值 T0~T3 | 业务规范 5.2.1.1 | **RISK_THRESHOLD_CONFIG** |
| 单边交易与定点扣费 | 业务规范 5.2.1.3~5 | **SINGLE_SIDE_TXN**、GATE_TXN_PAY |
| 对账文件 ITP.EXP/PAY/BUS | 业务规范 7.1 | **SETTLE_FILE_LOG、SETTLE_DAILY_SUMMARY** |

（加粗为本次新增表）

---

## 六、序列与分区清单

### 6.1 新增序列（27 个）

SEQ_SINGLE_TICKET_ORDER、SEQ_TICKET_ISSUE_DETAIL、SEQ_TOPUP_ORDER、SEQ_BOM_NOCASH_ORDER、SEQ_SUPP_STATION_RECORD、SEQ_SINGLE_SIDE_TXN、SEQ_BOM_UPDATE_RECORD、SEQ_OFFLINE_CODE_RECORD、SEQ_USER_KEY_INFO、SEQ_KEY_SYNC_LOG、SEQ_USER_PHONE_CHANGE_LOG、SEQ_CREDIT_QUERY_LOG、SEQ_APP_PUSH_NOTIFY_LOG、SEQ_BLACKLIST_PUSH_LOG、SEQ_SETTLE_FILE_LOG、SEQ_SETTLE_DAILY_SUMMARY、SEQ_ACC_MODE_BROADCAST、SEQ_ACC_FILE_NOTIFY_LOG、SEQ_SUPPLEMENT_PAY_ORDER、SEQ_SUPPLEMENT_PAY_ORDER_REL、SEQ_TRAVEL_BILL_STATISTICS、SEQ_CONTRACT_EXCEPTION_LOG、SEQ_PASSENGER_FLOW_BATCH、SEQ_PASSENGER_FLOW_DETAIL

（APP_TERMINATION_REQUEST、RISK_THRESHOLD_CONFIG、DEVICE_INFO 以业务编码为主键，不需要序列）

### 6.2 月分区表（10 张，与既有表同规则）

SINGLE_TICKET_ORDER、TICKET_ISSUE_DETAIL、TOPUP_ORDER、BOM_NOCASH_ORDER、BOM_UPDATE_RECORD、SINGLE_SIDE_TXN、APP_PUSH_NOTIFY_LOG、SUPPLEMENT_PAY_ORDER、SUPPLEMENT_PAY_ORDER_REL、PASSENGER_FLOW_DETAIL

分区范围 P202606~P202612 + P_MAX；新增月份分区的 SPLIT 语句模板见 tables.md 12.2。

### 6.3 遗留事项说明

1. 规范中 IF8B-06 编号重复（出票成功/签约结果两个接口），本设计在 NOTIFY_TYPE 中以 TAKE_TICKET_RESULT、SIGN_RESULT 区分。
2. if8a_42 用户销户接口地址与 if8a_41 相同（均为 requestTransStatistics），属文档笔误，实现时以独立地址为准，落表不受影响。
3. 行业数据密文（cardData）有效期约 4 小时（TIME_STAMP 机制），属瞬态数据，不单独建表；推送时快照存于 APP_PUSH_NOTIFY_LOG.REQUEST_BODY。
4. 支付宝出行渠道表已独立成套（ALIPAY_*），本设计不与其混用；地铁 APP 渠道解约走 APP_TERMINATION_REQUEST。

---

## 七、相关文档

- `tables.md` — 既有表结构文档
- `支付宝接口建表.sql` — 支付宝出行建表脚本
- `ITP补充表建表.sql` — 本设计配套建表脚本
- `02-微服务文档/` — 各服务实现文档
- `03-接口文档/接口设计对比/接口规范与项目实现对照表.md` — 规范与实现对照

# 数据库表结构文档

> **说明**: 本文档基于各模块 `src/main/resources/sql/*-schema.sql` 提取，与源码实现保持一致。
> **整理时间**: 2026-07-28
> **数据库类型**: Oracle

---

## 目录

- [一、账户服务表（account-server）](#一账户服务表account-server)
- [二、支付签约服务表（pay-sign-server）](#二支付签约服务表pay-sign-server)
- [三、票务服务表（ticket-server）](#三票务服务表ticket-server)
- [四、参数服务表（para-server）](#四参数服务表para-server)
- [五、支付交易表（fep-dev-server）](#五支付交易表fep-dev-server)
- [六、密钥服务表（key-server）](#六密钥服务表key-server)
- [七、黑名单服务表（blacklist-server）](#七黑名单服务表blacklist-server)
- [八、日票服务表（daily-ticket-server）](#八日票服务表daily-ticket-server)
- [九、ACC 事件源表（acc-es-server）](#九acc-事件源表acc-es-server)

---

## 一、账户服务表（account-server）

### 1.1 USER_ITP_REG_INFO（ITP 用户注册信息表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | 自增 | 主键 |
| CARD_ID | VARCHAR2 | 64 | Y | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | Y | - | 转换后的卡类型 |
| ITP_CARD_TYPE | VARCHAR2 | 64 | Y | - | APP 入参卡类型 |
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 第三方用户标识 |
| THIRD_USER_ID_SUFFIX | VARCHAR2 | 4 | Y | SUBSTR(THIRD_USER_ID, -2) | 分区字段（虚拟列） |
| MSISDN | VARCHAR2 | 64 | N | - | 手机号 |
| REG_TMS | TIMESTAMP(6) | - | N | - | 注册时间 |
| DEL_YN | NUMBER | 22 | N | - | 删除标识，1-有效，0-已注销 |
| DEL_THIRD_USER_ID | VARCHAR2 | 128 | N | - | 注销操作对应的第三方用户标识 |
| UN_REG_TMS | TIMESTAMP(6) | - | N | - | 注销时间 |
| USER_NAME | VARCHAR2 | 256 | N | - | 用户姓名 |
| USER_ID | VARCHAR2 | 128 | N | - | 证件号 |
| CARD_ISSUE_CODE | VARCHAR2 | 64 | N | - | 发卡机构编码 |
| THIRD_PAY_ID | VARCHAR2 | 128 | N | - | 第三方支付用户标识 |
| CHANNEL | VARCHAR2 | 64 | N | - | 渠道编码 |
| REQ_CONTRACT_NO | VARCHAR2 | 128 | N | - | 签约请求号 |
| HCE_DATA | VARCHAR2 | 512 | N | - | HCE 卡数据，开户时生成并由闸机交易 reserve1 更新 |

**索引**：
- IDX_UIRI_THIRD_USER_ACTIVE (THIRD_USER_ID, DEL_YN, REG_TMS DESC) - 本地分区索引
- IDX_UIRI_CARD_ID (CARD_ID) - 本地分区索引
- IDX_UIRI_THIRD_PAY_ID (THIRD_PAY_ID) - 本地分区索引

**分区**：LIST 分区，按 THIRD_USER_ID_SUFFIX 分区（P00-P99，PDF 为默认分区）

**注释**：ITP 用户注册信息表

---

### 1.2 USER_ITP_REG_LOG（ITP 用户注册日志表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | 自增 | 主键 |
| CARD_ID | VARCHAR2 | 64 | Y | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | Y | - | 转换后的卡类型 |
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 第三方用户标识 |
| MSISDN | VARCHAR2 | 64 | N | - | 手机号 |
| OPER_DATE_TIME | TIMESTAMP(6) | - | N | - | 操作时间 |
| OPER_TYPE | NUMBER | 22 | N | - | 操作类型 |

**索引**：
- IDX_UIRL_THIRD_USER_ID (THIRD_USER_ID, OPER_DATE_TIME DESC)
- IDX_UIRL_CARD_ID (CARD_ID, OPER_DATE_TIME DESC)

**注释**：ITP 用户注册日志表

---

### 1.3 USER_ACC_TICKETNO（账户卡号池表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | 自增 | 主键 |
| CARD_ID | VARCHAR2 | 64 | Y | - | 逻辑卡号 |
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 第三方用户标识，空表示未分配 |
| INSERT_TMS | TIMESTAMP(6) | - | N | - | 插入时间 |
| REG_TMS | TIMESTAMP(6) | - | N | - | 分配注册时间 |

**索引**：
- UK_UAT_CARD_ID (CARD_ID) - 唯一索引
- IDX_UAT_UNUSED_CARD (THIRD_USER_ID, INSERT_TMS)

**注释**：账户卡号池表

---

### 1.4 APP_USER_PAY_CHANNEL（用户支付通道绑定表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 第三方用户ID |
| CARD_ID | VARCHAR2 | 128 | Y | - | 地铁会员卡号 |
| CARD_TYPE | VARCHAR2 | 64 | Y | - | 卡类型编码 |
| CHANNEL | VARCHAR2 | 32 | Y | - | 支付通道编码 |
| THIRD_PAY_ID | VARCHAR2 | 128 | N | - | 支付账户ID |
| REQ_CONTRACT_NO | VARCHAR2 | 128 | N | - | 第三方签约流水号 |
| STATUS | VARCHAR2 | 64 | Y | - | 通道状态 |
| CREATE_TMS | TIMESTAMP(6) | - | Y | SYSTIMESTAMP | 创建时间 |
| UPDATE_TMS | TIMESTAMP(6) | - | Y | SYSTIMESTAMP | 更新时间 |
| THIRD_USER_ID_SUFFIX | VARCHAR2 | 4 | Y | SUBSTR(THIRD_USER_ID, -2) | 分区字段（虚拟列） |

**索引**：
- PK_APP_USER_PAY_CHANNEL (THIRD_USER_ID, CARD_TYPE, CHANNEL) - 主键
- IDX_AUPC_CARD_ID (CARD_ID) - 本地分区索引
- IDX_AUPC_CHANNEL (CHANNEL) - 本地分区索引

**分区**：LIST 分区，按 THIRD_USER_ID_SUFFIX 分区（P00-P99，PDF 为默认分区）

**注释**：用户支付通道绑定表

---

## 二、支付签约服务表（pay-sign-server）

### 2.1 APP_PAY_SIGN_INFO（免密签约主表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| REQUEST_SIGN_SEQ | VARCHAR2 | 128 | Y | - | 签约请求流水号（主键），标识一次签约请求的唯一流水 |
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 第三方用户ID，对应APP侧用户唯一标识 |
| SIGN_STATUS | VARCHAR2 | 32 | N | - | 签约状态，NOT_SIGNED未签约、SIGNING签约中、SIGNED已签约、UNSIGNED已解约 |
| PAYMENT_VENDOR | VARCHAR2 | 32 | N | - | 支付类型/支付厂商编码，如03支付宝、04微信、06建行龙支付、07云闪付、0B钱包 |
| CARD_ID | VARCHAR2 | 128 | N | - | 地铁会员卡号/逻辑卡号，用于绑定免密支付通道 |
| CARD_TYPE | VARCHAR2 | 64 | N | - | 卡类型编码 |
| PAY_ACCOUNT_ID | VARCHAR2 | 128 | N | - | 支付账户ID，签约成功后由支付系统返回的支付用户编码 |
| PAY_AGREEMENT_NO | VARCHAR2 | 256 | N | - | 支付协议号，签约成功后由支付系统返回 |
| DISPLAY_ACCOUNT | VARCHAR2 | 256 | N | - | 签约展示账号，用于签约页面展示，如昵称、手机号、姓名 |
| SIGN_TIME | TIMESTAMP(6) | - | N | - | 签约成功时间 |
| TERMINATION_TIME | TIMESTAMP(6) | - | N | - | 解约成功时间 |
| SIGN_CHANNEL | VARCHAR2 | 32 | N | - | 签约渠道编码，如：METRO_APP地铁APP、ALIPAY支付宝、WECHAT微信等 |

**索引**：
- UK_APP_PAY_SIGN_INFO_USER_VENDOR (THIRD_USER_ID, PAYMENT_VENDOR) - 唯一索引
- IDX_APP_PAY_SIGN_INFO_USER_ID (THIRD_USER_ID)

**注释**：免密签约主表

---

### 2.2 APP_PAY_SIGN_LOG（免密签约流水日志表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| REQUEST_SIGN_SEQ | VARCHAR2 | 128 | Y | - | 签约请求流水号（主键） |
| THIRD_USER_ID | VARCHAR2 | 128 | N | - | 第三方用户ID |
| PAYMENT_VENDOR | VARCHAR2 | 32 | N | - | 支付类型/支付厂商编码 |
| OPERATION_TYPE | VARCHAR2 | 128 | Y | - | 操作类型，SIGN-签约，UNSIGN-解约 |
| REQUEST_BODY | VARCHAR2 | 8000 | N | - | 请求报文JSON快照 |
| RESPONSE_BODY | VARCHAR2 | 8000 | N | - | 响应报文JSON快照 |
| RESULT_CODE | VARCHAR2 | 64 | N | - | 业务处理结果码 |
| RESULT_MSG | VARCHAR2 | 1024 | N | - | 业务处理结果描述 |
| CREATE_TMS | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 日志创建时间 |

**索引**：
- PK_APP_PAY_SIGN_LOG (REQUEST_SIGN_SEQ) - 主键
- IDX_APP_PAY_SIGN_LOG_USER_ID (THIRD_USER_ID)

**注释**：免密签约流水日志表

---

### 2.3 APP_PAY_SIGN_REQUEST（免密签约请求流水表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | SEQ_APP_PAY_SIGN_REQUEST.nextval | 主键ID |
| REQUEST_SIGN_SEQ | VARCHAR2 | 128 | Y | - | 签约请求流水号 |
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 第三方用户ID |
| PAYMENT_VENDOR | VARCHAR2 | 32 | Y | - | 支付类型/支付厂商编码 |
| DISPLAY_ACCOUNT | VARCHAR2 | 256 | Y | - | 签约展示账号 |
| OPERATION_TYPE | VARCHAR2 | 128 | Y | - | 操作类型 |
| REQUEST_BODY | VARCHAR2 | 8000 | N | - | 请求报文JSON快照 |
| RESPONSE_BODY | VARCHAR2 | 8000 | N | - | 响应报文JSON快照 |
| RESULT_CODE | VARCHAR2 | 64 | N | - | 业务处理结果码 |
| RESULT_MSG | VARCHAR2 | 1024 | N | - | 业务处理结果描述 |
| SIGN_STATUS | VARCHAR2 | 32 | N | - | 签约状态 |
| SIGN_CHANNEL | VARCHAR2 | 32 | N | - | 签约渠道编码 |
| PAY_ACCOUNT_ID | VARCHAR2 | 128 | N | - | 支付账户ID，补偿通知时使用 |
| PAY_AGREEMENT_NO | VARCHAR2 | 256 | N | - | 支付协议号，补偿通知时使用 |
| CARD_ID | VARCHAR2 | 128 | Y | - | 卡ID，解约补偿通知时使用 |
| CARD_TYPE | VARCHAR2 | 64 | Y | - | 卡类型，解约补偿通知时使用 |
| TERMINATION_TIME | VARCHAR2 | 64 | N | - | 解约时间，解约补偿通知时使用 |
| CREATE_TMS | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 创建时间 |
| NOTIFY_STATUS | VARCHAR2 | 32 | N | 'PENDING' | 通知状态: PENDING/SUCCESS/FAILED |
| NOTIFY_RETRY_COUNT | NUMBER | 22 | N | 0 | 通知重试次数 |
| NOTIFY_TIME | TIMESTAMP(6) | - | N | - | 最后通知时间 |
| NOTIFY_RESULT | VARCHAR2 | 1024 | N | - | 通知结果描述 |

**索引**：
- PK_APP_PAY_SIGN_REQUEST (ID) - 主键
- IDX_APP_PAY_SIGN_REQUEST_REQUEST_SIGN_SEQ (REQUEST_SIGN_SEQ)
- IDX_APP_PAY_SIGN_REQUEST_NOTIFY_STATUS (NOTIFY_STATUS, NOTIFY_RETRY_COUNT)

**序列**：SEQ_APP_PAY_SIGN_REQUEST

**注释**：免密签约请求流水表

---

## 三、票务服务表（ticket-server）

### 3.1 QRCODE_STATUS（二维码状态表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| CARD_ID | VARCHAR2 | 32 | Y | - | 二维码卡号（主键） |
| USE_COUNT | NUMBER | 10 | N | - | 使用次数 |
| CHANNEL | VARCHAR2 | 4 | N | - | 渠道 |
| CODE_STATUS | VARCHAR2 | 4 | N | - | 二维码状态 |
| GATE_IN_TIME | VARCHAR2 | 14 | N | - | 进站时间，格式为yyyymmddhh24miss |
| GATE_IN_STATION | VARCHAR2 | 4 | N | - | 进站车站 |
| LAST_TXN_TIME | VARCHAR2 | 14 | N | - | 最后交易时间，格式为yyyymmddhh24miss |
| LAST_TXN_STATION | VARCHAR2 | 4 | N | - | 最后交易车站 |
| TXN_SEQ | VARCHAR2 | 64 | N | - | 交易流水号 |
| CREATE_TIME | TIMESTAMP | - | N | - | 创建时间 |
| UPDATE_TIME | TIMESTAMP | - | N | - | 更新时间 |
| GATE_STATUS | VARCHAR2 | 32 | N | - | 进出站状态 |
| CARD_ID_TAIL | VARCHAR2 | 2 | Y | SUBSTR(CARD_ID, -2) | 分区字段（虚拟列） |

**索引**：
- UK_QR_CODE_STATUS_CARD (CARD_ID, CARD_ID_TAIL) - 唯一本地分区索引

**分区**：LIST 分区，按 CARD_ID_TAIL 分区（P_00-P_99，P_OTHER 为默认分区）

**注释**：二维码状态表

---

### 3.2 QRCODE_TXN_DETAIL（二维码闸机交易明细表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 20 | Y | - | 主键ID |
| DEVICE_ID | VARCHAR2 | 16 | N | - | 设备ID，公共请求参数 |
| ITP_USER_ID | VARCHAR2 | 64 | N | - | ITP用户编码，二维码中数据 |
| TRX_TYPE | VARCHAR2 | 4 | Y | - | 交易类型：01进站，02出站，03超时出站 |
| ISSUE_CHANNEL_CODE | VARCHAR2 | 8 | N | - | 发行渠道代码 |
| SIGN_CHANNEL_CODE | VARCHAR2 | 8 | N | - | 签约通道代码 |
| CARD_ID | VARCHAR2 | 32 | Y | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 8 | N | - | 票卡类型 |
| HANDLE_DATE_TIME | VARCHAR2 | 14 | Y | - | 闸机交易时间，格式YYYYMMDDHH24MISS |
| TXN_DATE | VARCHAR2 | 8 | Y | - | 交易日期，取HANDLE_DATE_TIME前8位，格式YYYYMMDD，用于月分区 |
| HANDLE_STATION_CODE | VARCHAR2 | 16 | N | - | 闸机交易车站代码 |
| TRX_AMOUNT | NUMBER | 12 | N | - | 实际交易金额，单位分 |
| OVERTIME_AMOUNT | NUMBER | 12 | N | - | 超时金额，单位分 |
| LAST_TICKET_STATUS | VARCHAR2 | 4 | N | - | 上次票卡状态 |
| HANDLE_RESULT_CODE | VARCHAR2 | 8 | N | - | 闸机处理结果码 |
| LAST_HANDLE_STATION_CODE | VARCHAR2 | 16 | N | - | 上次交易车站代码 |
| LAST_HANDLE_DATE_TIME | VARCHAR2 | 14 | N | - | 上次交易时间，格式YYYYMMDDHH24MISS |
| TICKET_TRANS_SEQ | VARCHAR2 | 64 | N | - | 二维码交易计数器，文档字段名为tikcetTransSeq |
| RESERVE1 | VARCHAR2 | 512 | N | - | 预留字段1 |
| RESERVE2 | VARCHAR2 | 512 | N | - | 预留字段2 |
| CREATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 创建时间 |

**索引**：
- PK_QRCODE_TXN_DETAIL (ID) - 主键
- UK_QRCODE_TXN_DETAIL_BIZ (CARD_ID, TRX_TYPE, HANDLE_DATE_TIME, TICKET_TRANS_SEQ, DEVICE_ID, TXN_DATE) - 交易幂等唯一索引（本地分区）
- IDX_QRCODE_TXN_DETAIL_CARD_DATE (CARD_ID, TXN_DATE, HANDLE_DATE_TIME) - 按卡号查询（本地分区）
- IDX_QRCODE_TXN_DETAIL_DEVICE_DATE (DEVICE_ID, TXN_DATE, HANDLE_DATE_TIME) - 按设备查询（本地分区）
- IDX_QRCODE_TXN_DETAIL_USER_DATE (ITP_USER_ID, TXN_DATE, HANDLE_DATE_TIME) - 按用户查询（本地分区）

**分区**：RANGE 分区，按 TXN_DATE 月分区（P202606-P202612，P_MAX 为兜底分区）

**序列**：SEQ_QRCODE_TXN_DETAIL

**注释**：二维码闸机交易明细表

---

## 四、参数服务表（para-server）

### 4.1 TBL_PARA_VERSION（参数当前版本维护表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PARA_TYPE | VARCHAR2 | 4 | Y | - | 参数类型，0001路网，0002日历，0003车票，0004费率（主键） |
| CURRENT_VER_NO | NUMBER | 10 | Y | - | 当前已入库参数版本号 |
| CURRENT_FILE_NAME | VARCHAR2 | 100 | N | - | 当前已入库参数文件名 |
| VALID_DATE_TIME | VARCHAR2 | 14 | N | - | 参数生效时间 |
| MD5_VALUE | VARCHAR2 | 32 | N | - | 当前已入库参数文件MD5 |
| LAST_UPD_USER | VARCHAR2 | 10 | N | 'itp' | 最后更新者 |
| LAST_UPD_TMS | DATE | - | N | SYSDATE | 最后更新时间 |

**索引**：
- PK_TBL_PARA_VERSION (PARA_TYPE) - 主键

**注释**：参数当前版本维护表

---

### 4.2 TBL_LINE_INFO（线路信息参数表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PARA_VER_NO | NUMBER | 6 | Y | - | 参数版本号（主键部分） |
| LINE_CODE | VARCHAR2 | 2 | Y | - | 线路节点编码（主键部分） |
| LINE_NM | VARCHAR2 | 50 | Y | - | 线路中文名 |
| LINE_E_NM | VARCHAR2 | 50 | Y | - | 线路英文名 |

**索引**：
- PK_TBL_LINE_INFO (PARA_VER_NO, LINE_CODE) - 主键

**注释**：线路信息参数表

---

### 4.3 TBL_STATION_INFO（车站信息参数表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PARA_VER_NO | NUMBER | 6 | Y | - | 参数版本号（主键部分） |
| STATION_CODE | VARCHAR2 | 4 | Y | - | 车站编码（主键部分） |
| OWNER_LINE_ID | VARCHAR2 | 2 | Y | - | 所属线路编码 |
| OWNER_INCOME_ID | VARCHAR2 | 4 | N | '0' | 所属收益方 |
| STATION_TYPE | VARCHAR2 | 1 | N | '0' | 车站类型 |
| STATION_NM | VARCHAR2 | 50 | Y | - | 车站中文名 |
| STATION_E_NM | VARCHAR2 | 60 | Y | - | 车站英文名 |

**索引**：
- PK_TBL_STATION_INFO (PARA_VER_NO, STATION_CODE) - 主键

**注释**：车站信息参数表

---

### 4.4 TBL_TSF_INFO（换乘站信息表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PARA_VER_NO | NUMBER | 6 | Y | - | 参数版本号（主键部分） |
| FROM_STAT_CODE | VARCHAR2 | 4 | Y | - | 换出车站节点编码（主键部分） |
| FROM_LINE_CODE | VARCHAR2 | 2 | Y | - | 换出线路节点编码（主键部分） |
| TO_LINE_CODE | VARCHAR2 | 2 | Y | - | 换入线路节点编码（主键部分） |
| TO_STAT_CODE | VARCHAR2 | 4 | Y | - | 换入车站节点编码（主键部分） |
| TSF_STATION_TYPE | VARCHAR2 | 2 | N | - | 换乘类型 |
| TSF_TIME | NUMBER | 4 | N | 60 | 换乘时间 |
| TSF_DISTANCE | NUMBER | 6 | N | 0 | 换乘距离 |

**索引**：
- PK_TBL_TSF_INFO (PARA_VER_NO, FROM_STAT_CODE, FROM_LINE_CODE, TO_STAT_CODE, TO_LINE_CODE) - 主键

**注释**：换乘站信息表

---

### 4.5 TBL_SECT_INFO（区段信息参数）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PARA_VER_NO | NUMBER | 6 | Y | - | 参数版本号（主键部分） |
| SECT_NO | NUMBER | 6 | Y | - | 区段编号（主键部分） |
| SECT_NAME | VARCHAR2 | 50 | Y | - | 区段中文名 |
| SECT_E_NAME | VARCHAR2 | 50 | Y | - | 区段英文名 |
| STAT_CODE1 | VARCHAR2 | 4 | Y | - | 开始车站编码 |
| STAT_CODE2 | VARCHAR2 | 4 | Y | - | 结束车站编码 |

**索引**：
- PK_TBL_SECT_INFO (PARA_VER_NO, SECT_NO) - 主键

**注释**：区段信息参数

---

### 4.6 TBL_FARE_MATRIX（费率等级参数表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PARA_VER_NO | NUMBER | 6 | Y | - | 参数版本号（主键部分） |
| BEGIN_STAT_CODE | VARCHAR2 | 4 | Y | - | 进站车站编码（主键部分） |
| END_STAT_CODE | VARCHAR2 | 4 | Y | - | 出站车站编码（主键部分） |
| FARE_TIER | NUMBER | 4 | Y | - | 费率等级 |

**索引**：
- PK_TBL_FARE_MATRIX (PARA_VER_NO, BEGIN_STAT_CODE, END_STAT_CODE) - 主键

**注释**：费率等级参数表

---

### 4.7 TBL_BASE_FARE（费率类型参数表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PARA_VER_NO | NUMBER | 6 | Y | - | 参数版本号（主键部分） |
| FARE_TYPE | NUMBER | 6 | Y | - | 费率组代码（主键部分） |
| FARE_TIER | NUMBER | 4 | Y | - | 费率等级（主键部分） |
| TICKET_PRICE | NUMBER | 9 | Y | - | 费率值 |

**索引**：
- PK_TBL_BASE_FARE (PARA_VER_NO, FARE_TYPE, FARE_TIER) - 主键

**注释**：费率类型参数表

---

## 五、支付交易表（fep-dev-server）

### 5.1 PAY_TXN_DETAIL（支付交易明细表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | - | 主键ID |
| ORDER_NO | VARCHAR2 | 128 | Y | - | 地铁侧订单号，同时作为支付接口orderNo |
| PAY_TYPE | VARCHAR2 | 32 | Y | 'PAY' | 交易类型：PAY支付，REFUND退款 |
| PAY_STATUS | VARCHAR2 | 32 | Y | 'INIT' | 支付状态：INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待重试，CLOSED关闭 |
| THIRD_USER_ID | VARCHAR2 | 128 | N | - | 第三方用户ID，对应设备报文itpUserId转换后的用户标识 |
| CARD_ID | VARCHAR2 | 64 | N | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | N | - | 票卡类型 |
| PAYMENT_VENDOR | VARCHAR2 | 32 | N | - | 支付方式 |
| PAY_CHANNEL_CODE | VARCHAR2 | 32 | N | - | 支付通道编码 |
| REQUEST_SIGN_SEQ | VARCHAR2 | 256 | N | - | 签约流水号或免密协议流水 |
| AMOUNT | NUMBER | 22 | N | 0 | 请求支付金额，单位分 |
| TOTAL_AMOUNT | NUMBER | 22 | N | - | 订单总金额，单位分 |
| CASH_AMOUNT | NUMBER | 22 | N | - | 实付金额，单位分 |
| COUPON_AMOUNT | NUMBER | 22 | N | - | 优惠金额，单位分 |
| REFUND_STATUS | VARCHAR2 | 32 | N | 'NONE' | 退款状态：NONE未退款，PROCESSING退款中，PARTIAL部分退款，SUCCESS已全额退款，FAIL退款失败 |
| REFUND_AMOUNT | NUMBER | 22 | N | 0 | 已退款总金额，单位分 |
| LAST_REFUND_TIME | TIMESTAMP(6) | - | N | - | 最近一次退款完成时间 |
| MERCHANT_ORDER_NO | VARCHAR2 | 128 | N | - | 商户订单号 |
| CHANNEL_ORDER_NO | VARCHAR2 | 256 | N | - | 渠道订单号 |
| PAY_USER_ID | VARCHAR2 | 256 | N | - | 支付渠道账户或买家账户 |
| REQUEST_COUNT | NUMBER | 22 | N | 0 | 已发起支付请求次数 |
| NEXT_REQUEST_TIME | TIMESTAMP(6) | - | N | - | 下次允许重试支付时间 |
| LAST_REQUEST_TIME | TIMESTAMP(6) | - | N | - | 最近一次发起支付时间 |
| FIRST_REQUEST_TIME | TIMESTAMP(6) | - | N | - | 首次发起支付时间 |
| RESPONSE_TIME | TIMESTAMP(6) | - | N | - | 最近一次收到支付响应时间 |
| PAY_TIME | VARCHAR2 | 28 | N | - | 支付完成时间，格式YYYYMMDDHH24MISS |
| TXN_DATE | DATE | - | Y | - | 订单日期，格式YYYYMMDD，用于月分区 |
| CREATE_TIME | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 更新时间 |

**索引**：
- PK_PAY_TXN_DETAIL (ID) - 主键
- UK_PAY_TXN_DETAIL_ORDER (ORDER_NO, TXN_DATE) - 唯一本地分区索引
- IDX_PAY_TXN_DETAIL_STATUS (PAY_STATUS, TXN_DATE, NEXT_REQUEST_TIME) - 本地分区索引
- IDX_PAY_TXN_DETAIL_CARD_DATE (CARD_ID, TXN_DATE) - 本地分区索引
- IDX_PAY_TXN_DETAIL_USER_DATE (THIRD_USER_ID, TXN_DATE) - 本地分区索引
- IDX_PAY_TXN_DETAIL_CHANNEL_ORDER (CHANNEL_ORDER_NO, TXN_DATE) - 本地分区索引

**分区**：RANGE 分区，按 TXN_DATE 月分区（P202606-P202612，P_MAX 为兜底分区）

**序列**：SEQ_PAY_TXN_DETAIL

**注释**：支付交易明细表

---

### 5.2 PAY_REFUND_DETAIL（支付退款明细表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | - | 主键ID |
| REFUND_ORDER_NO | VARCHAR2 | 128 | Y | - | 内部退款单号（主键） |
| ORDER_NO | VARCHAR2 | 128 | Y | - | 原支付订单号，对应PAY_TXN_DETAIL.ORDER_NO |
| REFUND_STATUS | VARCHAR2 | 32 | Y | 'INIT' | 退款状态：INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待重试，CLOSED关闭 |
| REFUND_AMOUNT | NUMBER | 16 | Y | - | 退款金额，单位分 |
| REFUND_REASON | VARCHAR2 | 1024 | N | - | 退款原因 |
| MERCHANT_REFUND_NO | VARCHAR2 | 128 | N | - | 商户退款单号 |
| REFUND_NO | VARCHAR2 | 128 | Y | - | 退款单号 |
| CHANNEL_REFUND_NO | VARCHAR2 | 256 | N | - | 渠道退款单号 |
| REQUEST_COUNT | NUMBER | 22 | N | 0 | 已发起退款请求次数 |
| NEXT_REQUEST_TIME | TIMESTAMP(6) | - | N | - | 下次允许重试退款时间 |
| LAST_REQUEST_TIME | TIMESTAMP(6) | - | N | - | 最近一次发起退款时间 |
| REFUND_TIME | VARCHAR2 | 28 | N | - | 退款完成时间，格式YYYYMMDDHH24MISS |
| TXN_DATE | DATE | - | Y | - | 订单日期，格式YYYYMMDD，用于月分区 |
| RET_CODE | VARCHAR2 | 32 | N | - | 响应码 |
| RET_MSG | VARCHAR2 | 512 | N | - | 响应信息 |
| PAY_CENTER_CODE | VARCHAR2 | 32 | N | - | 支付中心错误码 |
| PAY_CENTER_MSG | VARCHAR2 | 1024 | N | - | 支付中心错误原因 |
| REQUEST_BODY | CLOB | 4000 | N | - | 退款请求报文 |
| RESPONSE_BODY | CLOB | 4000 | N | - | 退款响应报文 |
| CREATE_TIME | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 更新时间 |

**索引**：
- PK_PAY_REFUND_DETAIL (ID) - 主键
- UK_PAY_REFUND_DETAIL_NO (REFUND_ORDER_NO, TXN_DATE) - 唯一本地分区索引
- IDX_PAY_REFUND_DETAIL_ORDER (ORDER_NO, TXN_DATE) - 本地分区索引
- IDX_PAY_REFUND_DETAIL_STATUS (REFUND_STATUS, TXN_DATE, NEXT_REQUEST_TIME) - 本地分区索引

**分区**：RANGE 分区，按 TXN_DATE 月分区（P202606-P202612，P_MAX 为兜底分区）

**序列**：SEQ_PAY_REFUND_DETAIL

**注释**：支付退款明细表

---

### 5.3 PAY_CALLBACK_LOG（支付回调流水表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | - | 主键ID |
| ORDER_NO | VARCHAR2 | 128 | Y | - | 订单号 |
| CALLBACK_TYPE | VARCHAR2 | 32 | Y | - | 回调类型：PAY支付回调，REFUND退款回调 |
| CALLBACK_STATUS | VARCHAR2 | 32 | N | - | 回调中的支付或退款状态 |
| REFUND_ORDER_NO | VARCHAR2 | 128 | N | - | 退款订单号 |
| MERCHANT_ORDER_NO | VARCHAR2 | 128 | N | - | 商户订单号 |
| CHANNEL_ORDER_NO | VARCHAR2 | 256 | N | - | 渠道订单号 |
| MERCHANT_REFUND_NO | VARCHAR2 | 128 | N | - | 商户退款单号 |
| REFUND_NO | VARCHAR2 | 128 | N | - | 退款单号 |
| CHANNEL_REFUND_NO | VARCHAR2 | 256 | N | - | 渠道退款单号 |
| PAY_TIME | VARCHAR2 | 28 | N | - | 支付时间，格式YYYYMMDDHH24MISS |
| REFUND_TIME | VARCHAR2 | 28 | N | - | 退款时间，格式YYYYMMDDHH24MISS |
| TOTAL_AMOUNT | NUMBER | 22 | N | - | 订单总金额，单位分 |
| CASH_AMOUNT | NUMBER | 22 | N | - | 实付金额，单位分 |
| COUPON_AMOUNT | NUMBER | 22 | N | - | 优惠金额，单位分 |
| REFUND_AMOUNT | NUMBER | 22 | N | - | 退款金额，单位分 |
| PAY_USER_ID | VARCHAR2 | 256 | N | - | 支付用户ID |
| PAYMENT_VENDOR | VARCHAR2 | 32 | N | - | 支付方式 |
| TXN_DATE | DATE | - | Y | - | 交易日期，格式YYYYMMDD，用于月分区 |
| RAW_BODY | CLOB | 4000 | N | - | 回调原文 |
| HANDLE_STATUS | VARCHAR2 | 32 | N | - | 本地处理状态：SUCCESS成功，FAIL失败 |
| HANDLE_MSG | VARCHAR2 | 1024 | N | - | 处理结果描述 |
| CREATE_TIME | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 创建时间 |

**索引**：
- PK_PAY_CALLBACK_LOG (ID) - 主键
- IDX_PAY_CALLBACK_LOG_ORDER (ORDER_NO, TXN_DATE) - 本地分区索引
- IDX_PAY_CALLBACK_LOG_REFUND (REFUND_ORDER_NO, TXN_DATE) - 本地分区索引

**分区**：RANGE 分区，按 TXN_DATE 月分区（P202606-P202612，P_MAX 为兜底分区）

**序列**：SEQ_PAY_CALLBACK_LOG

**注释**：支付回调流水表

---

## 六、密钥服务表（key-server）

### 6.1 METRO_CA_KEYSTORE（地铁CA密钥仓库）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | - | 主键 |
| KEY_IDX | VARCHAR2 | 8 | N | - | ITP密钥索引 |
| KEY_PRIVATE | VARCHAR2 | 512 | N | - | ITP私钥 |
| KEY_PUBLIC | VARCHAR2 | 512 | N | - | ITP公钥 |
| KEY_EFFECTIVE_DATE | VARCHAR2 | 40 | N | - | 有效期 |
| KEY_STATUS | NUMBER | 22 | N | - | 状态：0初始化，1使用，2暂停 |
| MANAGER_ID | VARCHAR2 | 40 | N | - | 管理员编码 |
| RESERVE | VARCHAR2 | 200 | N | - | 预留字段 |
| REMARK | VARCHAR2 | 200 | N | - | 备注 |
| UPDATE_DATE | TIMESTAMP(6) | - | N | - | 修改日期 |
| REG_DATE | TIMESTAMP(6) | - | Y | - | 注册日期 |
| KEY_PAIR | VARCHAR2 | 2048 | N | - | SM2密钥对 |

**索引**：
- PK_METRO_CA_KEYSTORE (ID) - 主键
- IDX_METRO_CA_KEYSTORE_STATUS (KEY_STATUS)
- IDX_METRO_CA_KEYSTORE_KEYIDX (KEY_IDX)

**注释**：地铁会CA密钥仓库

---

### 6.2 METRO_MEMBER_STATIC_KEY（HCE会员卡静态密钥缓存）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | - | 主键 |
| METRO_MEMBER_CARD_NUM | VARCHAR2 | 32 | Y | - | HCE逻辑卡号（唯一） |
| KEY_WRAP_VALUE1 | VARCHAR2 | 512 | Y | - | ACC KEK加密的DPK |
| KEY_STATUS | NUMBER | 22 | N | 1 | 状态：1启用，2停用 |
| UPDATE_DATE | TIMESTAMP(6) | - | N | - | 修改日期 |
| REG_DATE | TIMESTAMP(6) | - | Y | - | 注册日期 |

**索引**：
- PK_METRO_MEMBER_STATIC_KEY (ID) - 主键
- UK_METRO_MEMBER_STATIC_KEY_CARD (METRO_MEMBER_CARD_NUM) - 唯一索引

**序列**：SEQ_METRO_MEMBER_STATIC_KEY

**注释**：HCE会员卡静态密钥缓存

---

## 七、黑名单服务表（blacklist-server）

### 7.1 BLACKLIST（黑名单表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | 自增 | 主键ID |
| CARD_ID | VARCHAR2 | 32 | Y | - | 卡ID |
| THIRD_USER_ID | VARCHAR2 | 16 | N | - | 三方用户ID |
| REASON | VARCHAR2 | 1000 | N | - | 拉黑原因 |
| CREATE_TIME | TIMESTAMP(6) | - | Y | SYSTIMESTAMP | 创建时间 |

**索引**：
- PK_BLACKLIST (ID) - 主键
- IDX_BLACKLIST_CARD_ID (CARD_ID)
- IDX_BLACKLIST_THIRD_USER_ID (THIRD_USER_ID)

**注释**：黑名单表

---

### 7.2 BLACKLIST_OPERATE_LOG（黑名单操作记录表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | 自增 | 主键ID |
| CARD_ID | VARCHAR2 | 32 | Y | - | 卡ID |
| THIRD_USER_ID | VARCHAR2 | 16 | N | - | 三方用户ID |
| OPERATE_TYPE | VARCHAR2 | 32 | Y | - | 操作类型，ADD新增，DELETE删除 |
| REASON | VARCHAR2 | 1000 | N | - | 操作原因 |
| OPERATE_TIME | TIMESTAMP(6) | - | Y | SYSTIMESTAMP | 操作时间 |

**索引**：
- PK_BLACKLIST_OPERATE_LOG (ID) - 主键
- IDX_BLACKLIST_LOG_CARD_ID (CARD_ID)
- IDX_BLACKLIST_LOG_OPERATE_TIME (OPERATE_TIME)

**注释**：黑名单操作记录表

---

## 八、日票服务表（daily-ticket-server）

### 8.1 DAILY_TICKET_ORDER（日票订单表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | VARCHAR2 | 64 | Y | - | 主键ID |
| ORDER_NO | VARCHAR2 | 64 | Y | - | 日票订单号（唯一） |
| ORDER_TYPE | VARCHAR2 | 8 | Y | '1' | 订单类型，日票固定为1 |
| THIRD_USER_ID | VARCHAR2 | 64 | N | - | 第三方用户编号 |
| USER_ID | VARCHAR2 | 64 | N | - | APP用户编号 |
| ORDER_SOURCE | VARCHAR2 | 8 | N | - | 订单来源 |
| CARD_TYPE | VARCHAR2 | 16 | N | - | APP侧卡类型，日票生码时码体票种仍固定为0441 |
| SHOW_TYPE | VARCHAR2 | 16 | N | - | 展示票类型 |
| TICKET_NAME | VARCHAR2 | 128 | N | - | 车票名称 |
| TICKET_PRICE | NUMBER | 12 | N | - | 票价，单位分 |
| PAY_AMOUNT | NUMBER | 12 | N | - | 实付金额，单位分 |
| PAY_CHANNEL_CODE | VARCHAR2 | 16 | N | - | 支付渠道编码 |
| CHANNEL_TYPE | VARCHAR2 | 8 | N | - | APP支付场景来源，1转app，2转wap |
| PAY_SCENE | VARCHAR2 | 16 | N | - | 支付服务scene字段 |
| ORDER_STATUS | VARCHAR2 | 32 | Y | - | 订单状态：CREATED/PAYING/PAID/CANCELED/REFUNDING/REFUNDED等 |
| PAY_STATUS | VARCHAR2 | 32 | N | - | 支付状态：INIT/PAYING/PAID/FAIL等 |
| TRADE_NO | VARCHAR2 | 128 | N | - | 第三方支付交易号 |
| PAYMENT_ORDER_NO | VARCHAR2 | 128 | N | - | 支付系统订单号 |
| PAY_DATE | TIMESTAMP | - | N | - | 支付完成时间 |
| CREATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 更新时间 |

**索引**：
- PK_DAILY_TICKET_ORDER (ID) - 主键
- IDX_DAILY_TICKET_ORDER_USER (THIRD_USER_ID, USER_ID)
- IDX_DAILY_TICKET_ORDER_STATUS (ORDER_STATUS, PAY_STATUS)

**注释**：日票订单表

---

### 8.2 DAILY_TICKET_INSTANCE（日票票实例表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | VARCHAR2 | 64 | Y | - | 主键ID |
| ORDER_NO | VARCHAR2 | 64 | Y | - | 关联日票订单号 |
| THIRD_USER_ID | VARCHAR2 | 64 | N | - | 第三方用户编号 |
| CARD_NUM | VARCHAR2 | 64 | N | - | 日票虚拟卡号 |
| CARD_ISSUE | VARCHAR2 | 32 | N | - | 发卡机构代码 |
| APP_CARD_TYPE | VARCHAR2 | 16 | N | - | APP侧卡类型 |
| CODE_TICKET_TYPE | VARCHAR2 | 16 | Y | '0441' | 码体车票类型，日票固定为0441 |
| TICKET_TYPE | VARCHAR2 | 16 | N | - | APP侧票类型 |
| SHOW_TYPE | VARCHAR2 | 16 | N | - | 展示票类型 |
| TICKET_CODE | VARCHAR2 | 64 | N | - | 车票编码 |
| TICKET_NAME | VARCHAR2 | 128 | N | - | 车票名称 |
| PERIOD | NUMBER | 4 | N | - | 有效期天数 |
| ACTUAL_TIMES | NUMBER | 8 | N | - | 实际可用次数，-99表示不限次 |
| TRANS_SEQ | NUMBER | 12 | N | - | 交易序号 |
| TRANS_AMOUNT | NUMBER | 12 | N | - | 交易金额，单位分 |
| DISCOUNT_AMOUNT | NUMBER | 12 | N | - | 优惠金额，单位分 |
| PAY_CHANNEL | VARCHAR2 | 16 | N | - | 支付渠道 |
| TICKET_STATUS | VARCHAR2 | 32 | Y | - | 票状态：INIT/ACTIVATED/USED/EXPIRED/REFUND_LOCKED/REFUNDED等 |
| COUNTING_START | NUMBER | 20 | N | - | 计次/计时开始时间，毫秒时间戳 |
| COUNTING_END | NUMBER | 20 | N | - | 计次/计时结束时间，毫秒时间戳 |
| ACTIVATE_TIME | TIMESTAMP | - | N | - | 激活时间 |
| FIRST_USE_TIME | TIMESTAMP | - | N | - | 首次使用时间 |
| ACC_NOTICE_STATUS | VARCHAR2 | 32 | N | - | ACC通知状态：INIT/SUCCESS/FAIL等 |
| ACC_NOTICE_TIME | TIMESTAMP | - | N | - | ACC通知时间 |
| CREATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 更新时间 |

**索引**：
- PK_DAILY_TICKET_INSTANCE (ID) - 主键
- UK_DAILY_TICKET_INSTANCE_ORDER (ORDER_NO) - 唯一索引
- IDX_DAILY_TICKET_INSTANCE_CARD (CARD_NUM)
- IDX_DAILY_TICKET_INSTANCE_STATUS (TICKET_STATUS, ACC_NOTICE_STATUS)

**注释**：日票票实例表

---

### 8.3 DAILY_TICKET_PAY_LOG（日票支付交互日志表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | VARCHAR2 | 64 | Y | - | 主键ID |
| ORDER_NO | VARCHAR2 | 64 | Y | - | 日票订单号 |
| BIZ_TYPE | VARCHAR2 | 32 | Y | - | 业务类型，如PAY/QUERY/REFUND/CALLBACK |
| PAY_CHANNEL_CODE | VARCHAR2 | 16 | N | - | 支付渠道编码 |
| REQUEST_BODY | CLOB | 4000 | N | - | 请求报文 |
| RESPONSE_BODY | CLOB | 4000 | N | - | 响应报文 |
| RESULT_CODE | VARCHAR2 | 32 | N | - | 处理结果码 |
| RESULT_MSG | VARCHAR2 | 512 | N | - | 处理结果信息 |
| CREATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 创建时间 |

**索引**：
- PK_DAILY_TICKET_PAY_LOG (ID) - 主键
- IDX_DAILY_TICKET_PAY_LOG_ORDER (ORDER_NO, BIZ_TYPE)

**注释**：日票支付交互日志表

---

### 8.4 DAILY_TICKET_REFUND（日票退款记录表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | VARCHAR2 | 64 | Y | - | 主键ID |
| ORDER_NO | VARCHAR2 | 64 | Y | - | 原日票订单号 |
| REFUND_ORDER_NO | VARCHAR2 | 64 | N | - | 退款订单号 |
| REFUND_AMOUNT | NUMBER | 12 | N | - | 退款金额，单位分 |
| REFUND_STATUS | VARCHAR2 | 32 | N | - | 退款状态：REFUNDING/REFUNDED/FAILED/WAIT_VERIFY等 |
| REFUND_TYPE | VARCHAR2 | 8 | N | - | 退款类型，00直接退款，01激活后核验退款 |
| REFUND_DATE | TIMESTAMP | - | N | - | 退款完成时间 |
| VERIFY_AFTER_TIME | TIMESTAMP | - | N | - | 激活后退款核验时间，通常为申请后5天 |
| CREATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | - | N | SYSTIMESTAMP | 更新时间 |

**索引**：
- PK_DAILY_TICKET_REFUND (ID) - 主键
- IDX_DAILY_TICKET_REFUND_VERIFY (REFUND_STATUS, VERIFY_AFTER_TIME)

**注释**：日票退款记录表

---

## 九、ACC 事件源表（acc-es-server）

### 9.1 TBL_STL_ACCT_INFO（票卡结算账户信息表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| TICKET_LOGIC_NO | VARCHAR2 | 16 | Y | - | 票卡逻辑号（主键） |
| TICKET_TYPE | NUMBER | 4 | Y | - | 票卡类型 |
| TICKET_SUB_TYPE | NUMBER | 4 | Y | - | 票卡子类型 |
| TICKET_CSN | VARCHAR2 | 16 | Y | - | 票卡CSN |
| TICKET_FACE_NO | VARCHAR2 | 32 | N | - | 票卡面号 |
| CARD_TYPE | VARCHAR2 | 4 | N | - | 卡类型 |
| TICKET_VER | NUMBER | 2 | N | - | 票卡版本 |
| PHY_TYPE | NUMBER | 6 | N | - | 物理类型 |
| PUB_DATE | CHAR | 8 | N | - | 发行日期 |
| PUB_BATCH_NO | NUMBER | 9 | N | - | 发行批次号 |
| VALID_AREA | VARCHAR2 | 40 | N | - | 有效区域 |
| MISC_CD | VARCHAR2 | 12 | N | - | 杂项代码 |
| INIT_AMT | NUMBER | 9 | N | 0 | 初始金额 |
| INIT_REW_AMT | NUMBER | 9 | N | 0 | 初始奖励金额 |
| DEP_AMT | NUMBER | 9 | N | 0 | 押金 |
| REM_DEP_AMT | NUMBER | 9 | N | 0 | 剩余押金 |
| EXPIRE_DATE | CHAR | 8 | N | - | 过期日期 |
| TICKET_STATUS | CHAR | 2 | N | - | 票卡状态 |
| REFUND_STATUS | CHAR | 2 | N | - | 退款状态 |
| CHANGE_DATE | CHAR | 8 | N | - | 变更日期 |
| SALE_TIME | DATE | - | N | - | 发售时间 |
| TOT_ADD_AMT | NUMBER | 9 | N | 0 | 总充值金额 |
| TOT_ADD_COUNT | NUMBER | 9 | N | 0 | 总充值次数 |
| ADD_COUNTER | NUMBER | 9 | N | 0 | 充值计数器 |
| TOT_CONS_COUNT | NUMBER | 9 | N | 0 | 总消费次数 |
| TOT_CONS_AMT | NUMBER | 9 | N | 0 | 总消费金额 |
| CONS_COUNTER | NUMBER | 9 | N | 0 | 消费计数器 |
| TICKET_BAL | NUMBER | 9 | N | 0 | 票卡余额 |
| ACCT_BAL | NUMBER | 9 | N | 0 | 账户余额 |
| LAST_TXN_TMS | DATE | - | N | - | 最后交易时间 |
| ORG_TICKET_ID | VARCHAR2 | 16 | N | - | 原始票卡ID |
| NEW_TICKET_ID | VARCHAR2 | 16 | N | - | 新票卡ID |
| LAST_UPD_ID | VARCHAR2 | 10 | Y | - | 最后更新人 |
| LAST_UPD_TMS | DATE | - | Y | - | 最后更新时间 |

**注释**：票卡结算账户信息表

---

### 9.2 TBL_STL_PERSON_INFO（票卡个人资料表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| TICKET_ID | VARCHAR2 | 20 | Y | - | 票卡ID（主键） |
| PERSON_NAME | VARCHAR2 | 30 | N | - | 姓名 |
| PERSON_SEX | VARCHAR2 | 2 | N | - | 性别 |
| PID_CD | VARCHAR2 | 5 | N | - | 证件类型代码 |
| PID_CODE | VARCHAR2 | 50 | N | - | 证件号码 |
| PASSWD | VARCHAR2 | 255 | N | - | 密码 |
| LANG_CD | VARCHAR2 | 30 | N | - | 语言代码 |
| COMPANY_NM | VARCHAR2 | 50 | N | - | 公司名称 |
| EMP_NO | VARCHAR2 | 10 | N | - | 员工号 |
| CON_TEL_NO | VARCHAR2 | 30 | N | - | 联系电话 |
| CON_MAIL | VARCHAR2 | 50 | N | - | 联系邮箱 |
| ADDRESS | VARCHAR2 | 255 | N | - | 地址 |
| POST_CD | VARCHAR2 | 10 | N | - | 邮编 |
| LAST_UPD_ID | VARCHAR2 | 10 | Y | - | 最后更新人 |
| LAST_UPD_TMS | TIMESTAMP(6) | - | Y | - | 最后更新时间 |
| PHOTO_BLOB | BLOB | 4000 | N | - | 照片 |

**注释**：票卡个人资料表

---

### 9.3 TBL_STL_TICKET_INFO（票卡信息表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| TICKET_LOGIC_NO | VARCHAR2 | 16 | Y | - | 票卡逻辑号（主键） |
| TICKET_TYPE | NUMBER | 4 | Y | - | 票卡类型 |
| TICKET_SUB_TYPE | NUMBER | 4 | N | - | 票卡子类型 |
| TICKET_CSN | VARCHAR2 | 16 | N | - | 票卡CSN |
| FACE_ID | VARCHAR2 | 32 | N | - | 面号ID |
| TICKET_KIND_FLAG | CHAR | 3 | N | - | 票卡类型标志 |
| TICKET_VER | NUMBER | 2 | Y | - | 票卡版本 |
| PHY_TYPE | NUMBER | 6 | Y | - | 物理类型 |
| PUB_DATE | CHAR | 8 | Y | - | 发行日期 |
| PUB_BATCH_NO | NUMBER | 9 | N | - | 发行批次号 |
| VALID_AREA | VARCHAR2 | 40 | N | - | 有效区域 |
| MISC_CD | VARCHAR2 | 12 | N | - | 杂项代码 |
| INIT_AMT | NUMBER | 9 | N | 0 | 初始金额 |
| INIT_REW_AMT | NUMBER | 9 | N | 0 | 初始奖励金额 |
| EXPIRE_DATE | CHAR | 8 | N | - | 过期日期 |
| TICKET_STATUS | CHAR | 2 | N | - | 票卡状态 |
| CHANGE_DATE | CHAR | 8 | N | - | 变更日期 |
| SALE_TIME | DATE | - | N | - | 发售时间 |
| CONS_COUNTER | NUMBER | 9 | N | - | 消费计数器 |
| TICKET_BAL | NUMBER | 9 | N | 0 | 票卡余额 |
| ACCT_BAL | NUMBER | 9 | N | 0 | 账户余额 |
| LAST_TXN_TMS | DATE | - | N | - | 最后交易时间 |
| LAST_UPD_ID | VARCHAR2 | 10 | Y | - | 最后更新人 |
| LAST_UPD_TMS | DATE | - | Y | - | 最后更新时间 |

**注释**：票卡信息表

---

### 9.4 TBL_TKT_ES_*（ACC 事件源相关表）

以下为 ACC 事件源服务使用的表结构：

| 表名 | 说明 |
|------|------|
| TBL_TKT_ES_ACCOUNT | 事件源账户表 |
| TBL_TKT_ES_ASSIGN | 任务分配表 |
| TBL_TKT_ES_FILE_PROC_LOG | 文件处理日志表 |
| TBL_TKT_ES_INFO | 事件源信息表 |
| TBL_TKT_ES_PROC | 事件源处理表 |
| TBL_TKT_ES_REPORT | 事件源报表表 |
| TBL_TKT_ES_TASK | 事件源任务表 |
| TBL_TKT_PRE_PERSON | 预分配人员表 |
| TBL_TKT_TASK_PLAN | 任务计划表 |

详细字段定义请参考 `acc-es-server/src/main/resources/sql/TBL_STL_ACCT_INFO(1).sql`

---

## 十、支付宝出行专属表（alipay-pay-sign-server）

### 10.1 ALIPAY_USER_INFO（支付宝用户信息表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 支付宝用户ID（主键） |
| CARD_ID | VARCHAR2 | 64 | Y | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | Y | - | 卡类型编码 |
| CARD_ISSUE_CODE | VARCHAR2 | 64 | Y | - | 发卡渠道代码，固定0007 |
| MSISDN | VARCHAR2 | 64 | N | - | 手机号 |
| EXTEND1 | VARCHAR2 | 256 | N | - | 扩展字段1 |
| EXTEND2 | VARCHAR2 | 256 | N | - | 扩展字段2 |
| CHANNEL | VARCHAR2 | 64 | Y | - | 开户渠道编码 |
| THIRD_PAY_ID | VARCHAR2 | 128 | N | - | 第三方支付ID |
| REQ_CONTRACT_NO | VARCHAR2 | 128 | N | - | 签约请求号 |
| STATUS | VARCHAR2 | 32 | N | - | 用户状态 |
| DELETE_FLAG | VARCHAR2 | 1 | N | '0' | 删除标记（0:正常 1:删除） |
| VERSION | VARCHAR2 | 32 | N | '1' | 乐观锁版本号 |
| CREATE_TIME | DATE | - | N | SYSDATE | 创建时间 |
| UPDATE_TIME | DATE | - | N | SYSDATE | 更新时间 |
| CREATE_BY | VARCHAR2 | 32 | N | - | 创建人 |

**索引**：
- PK_ALIPAY_USER_INFO (THIRD_USER_ID) - 主键
- IDX_ALIPAY_USER_CARD_ID (CARD_ID)
- IDX_ALIPAY_USER_CHANNEL (CHANNEL)

**注释**：支付宝用户信息表

---

### 10.2 ALIPAY_SIGN_INFO（支付宝签约信息表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| AGREEMENT_CODE | VARCHAR2 | 128 | Y | - | 协议号（主键） |
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 支付宝用户ID |
| CARD_ID | VARCHAR2 | 64 | N | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | N | - | 卡类型编码 |
| CHANNEL_AGREEMENT_CODE | VARCHAR2 | 256 | N | - | 渠道协议号 |
| CHANNEL_USER_ACCOUNT | VARCHAR2 | 256 | N | - | 渠道用户账号 |
| CHANNEL | VARCHAR2 | 32 | N | - | 支付渠道编码 |
| SIGN_STATUS | VARCHAR2 | 32 | N | - | 签约状态（SIGNED已签约/UNSIGNED已解约） |
| OPERATION_TYPE | VARCHAR2 | 128 | N | - | 操作类型 |
| SIGN_TIME | DATE | - | N | - | 签约成功时间 |
| TERMINATION_TIME | DATE | - | N | - | 解约成功时间 |
| DELETE_FLAG | VARCHAR2 | 1 | N | '0' | 删除标记（0:正常 1:删除） |
| VERSION | VARCHAR2 | 32 | N | '1' | 乐观锁版本号 |
| CREATE_TIME | DATE | - | N | SYSDATE | 创建时间 |
| UPDATE_TIME | DATE | - | N | SYSDATE | 更新时间 |

**索引**：
- PK_ALIPAY_SIGN_INFO (AGREEMENT_CODE) - 主键
- IDX_ALIPAY_SIGN_USER (THIRD_USER_ID, CHANNEL, DELETE_FLAG)

**注释**：支付宝签约信息表

---

### 10.3 ALIPAY_PAY_LOG（支付宝支付流水表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| PAY_SEQ | VARCHAR2 | 64 | Y | - | 支付流水号（主键） |
| THIRD_USER_ID | VARCHAR2 | 64 | N | - | 支付宝用户ID |
| CARD_ID | VARCHAR2 | 32 | N | - | 逻辑卡号 |
| ORDER_NO | VARCHAR2 | 64 | Y | - | 订单号 |
| TRADE_NO | VARCHAR2 | 64 | N | - | 支付宝交易号 |
| PAY_AMOUNT | VARCHAR2 | 16 | Y | - | 支付金额（分） |
| PAY_STATUS | VARCHAR2 | 16 | Y | - | 支付状态（SUCCESS/FAIL） |
| PAY_TYPE | VARCHAR2 | 16 | N | - | 支付类型 |
| SCENE | VARCHAR2 | 32 | N | - | 支付场景 |
| PAYMENT_VENDOR | VARCHAR2 | 32 | N | - | 支付厂商编码 |
| INDUSTRY_TYPE | VARCHAR2 | 16 | N | - | 行业类型（1-地铁 2-公交 3-打车 4-购物） |
| SUBJECT | VARCHAR2 | 128 | N | - | 订单标题 |
| BODY | VARCHAR2 | 256 | N | - | 订单描述 |
| REQUEST_SIGN_SEQ | VARCHAR2 | 64 | N | - | 签约流水号（免密场景） |
| ORDER_TIME_OUT | VARCHAR2 | 16 | N | - | 订单超时时间（秒） |
| AUTH_CODE | VARCHAR2 | 64 | N | - | 授权码 |
| NOTIFY_URL | VARCHAR2 | 256 | N | - | 回调地址 |
| RETURN_URL | VARCHAR2 | 256 | N | - | 返回前端页面地址 |
| IP_ADDRESS | VARCHAR2 | 64 | N | - | 用户IP地址 |
| INDUSTRY_DETAIL | CLOB | 4000 | N | - | 行业详情（JSON格式） |
| REQUEST_BODY | CLOB | 4000 | N | - | 原始请求报文 |
| RESPONSE_BODY | CLOB | 4000 | N | - | 原始响应报文 |
| RESULT_CODE | VARCHAR2 | 16 | Y | - | 响应码 |
| RESULT_MSG | VARCHAR2 | 256 | N | - | 响应信息 |
| OPERATOR | VARCHAR2 | 32 | N | - | 操作人 |
| IP | VARCHAR2 | 64 | N | - | 请求IP |
| REMARK | VARCHAR2 | 256 | N | - | 备注 |
| DELETE_FLAG | VARCHAR2 | 1 | Y | '0' NOT NULL | 删除标记（0:正常 1:删除） |
| VERSION | VARCHAR2 | 32 | Y | '1' NOT NULL | 乐观锁版本号 |
| CREATE_TIME | DATE | - | Y | SYSDATE NOT NULL | 创建时间 |
| UPDATE_TIME | DATE | - | Y | SYSDATE NOT NULL | 更新时间 |
| CREATE_BY | VARCHAR2 | 32 | N | - | 创建人 |
| REFUND_AMOUNT | VARCHAR2 | 100 | N | - | 已退款金额（分） |
| REFUND_STATUS | VARCHAR2 | 20 | N | - | 退款状态（SUCCESS-全额退款完成/PARTIAL-部分退款/NONE-无退款） |

**索引**：
- PK_ALIPAY_PAY_LOG (PAY_SEQ) - 主键
- IDX_ALIPAY_PAY_ORDER (ORDER_NO, DELETE_FLAG)
- IDX_ALIPAY_PAY_USER (THIRD_USER_ID, CREATE_TIME DESC)
- IDX_ALIPAY_PAY_CARD (CARD_ID, CREATE_TIME DESC)

**注释**：支付宝支付流水表

---

### 10.4 ALIPAY_REFUND_LOG（支付宝退款流水表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| REFUND_SEQ | VARCHAR2 | 128 | Y | - | 退款流水号（主键） |
| THIRD_USER_ID | VARCHAR2 | 128 | N | - | 支付宝用户ID |
| CARD_ID | VARCHAR2 | 64 | N | - | 逻辑卡号 |
| ORDER_NO | VARCHAR2 | 64 | N | - | 原订单号 |
| REFUND_AMOUNT | VARCHAR2 | 16 | N | - | 退款金额（分） |
| REFUND_STATUS | VARCHAR2 | 32 | N | - | 退款状态（SUCCESS/FAIL） |
| REFUND_REASON | VARCHAR2 | 256 | N | - | 退款原因 |
| CARD_ISSUE_CODE | VARCHAR2 | 64 | N | - | 卡机构编号，支付宝0007 |
| CHANNEL_AGREEMENT_NO | VARCHAR2 | 256 | N | - | 渠道协议号 |
| REFUND_ORDER_NO | VARCHAR2 | 128 | N | - | 退款订单号 |
| REQUEST_BODY | CLOB | 4000 | N | - | 原始请求报文 |
| RESPONSE_BODY | CLOB | 4000 | N | - | 原始响应报文 |
| RESULT_CODE | VARCHAR2 | 64 | N | - | 响应码 |
| RESULT_MSG | VARCHAR2 | 1024 | N | - | 响应信息 |
| OPERATOR | VARCHAR2 | 32 | N | - | 操作人 |
| IP | VARCHAR2 | 64 | N | - | 请求IP |
| REMARK | VARCHAR2 | 256 | N | - | 备注 |
| DELETE_FLAG | VARCHAR2 | 1 | N | '0' | 删除标记（0:正常 1:删除） |
| VERSION | VARCHAR2 | 32 | N | '1' | 乐观锁版本号 |
| CREATE_TIME | DATE | - | N | SYSDATE | 创建时间 |
| UPDATE_TIME | DATE | - | N | SYSDATE | 更新时间 |
| CREATE_BY | VARCHAR2 | 32 | N | - | 创建人 |

**索引**：
- PK_ALIPAY_REFUND_LOG (REFUND_SEQ) - 主键
- IDX_ALIPAY_REFUND_ORDER (ORDER_NO, DELETE_FLAG)
- IDX_ALIPAY_REFUND_ORDER_NO (REFUND_ORDER_NO, DELETE_FLAG)

**注释**：支付宝退款流水表

---

### 10.5 ALIPAY_TRAVEL_RECORD（支付宝乘车记录表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ORDER_NO | VARCHAR2 | 64 | Y | - | 订单号（主键） |
| THIRD_USER_ID | VARCHAR2 | 64 | Y | - | 支付宝用户ID |
| CARD_ID | VARCHAR2 | 32 | Y | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 16 | Y | - | 发卡类型代码 |
| CHANNEL | VARCHAR2 | 32 | Y | - | 支付渠道 |
| TRADE_ORDER_NO | VARCHAR2 | 64 | N | - | 交易订单号 |
| TXN_SEQ | VARCHAR2 | 32 | N | - | 交易序列号 |
| ENTRY_STATION_NAME | VARCHAR2 | 64 | N | - | 进站站点名称 |
| ENTRY_DATE | VARCHAR2 | 16 | N | - | 进站时间 |
| GATE_IN_STATION | VARCHAR2 | 64 | N | - | 进站闸机 |
| EXIT_STATION_NAME | VARCHAR2 | 64 | N | - | 出站站点名称 |
| EXIT_DATE | VARCHAR2 | 16 | N | - | 出站时间 |
| GATE_OUT_STATION | VARCHAR2 | 64 | N | - | 出站闸机 |
| PAY_METHOD | VARCHAR2 | 16 | N | - | 支付方式 |
| TOTAL_AMOUNT | VARCHAR2 | 16 | N | - | 总金额（分） |
| DISCOUNT_AMOUNT | VARCHAR2 | 16 | N | - | 优惠金额（分） |
| PAY_AMOUNT | VARCHAR2 | 16 | N | - | 实付金额（分） |
| STATUS | VARCHAR2 | 16 | N | - | 记录状态 |
| DELETE_FLAG | VARCHAR2 | 1 | N | '0' | 删除标记（0:正常 1:删除） |
| VERSION | VARCHAR2 | 32 | N | '1' | 乐观锁版本号 |
| CREATE_TIME | DATE | - | N | SYSDATE | 创建时间 |
| UPDATE_TIME | DATE | - | N | SYSDATE | 更新时间 |
| CREATE_BY | VARCHAR2 | 32 | N | - | 创建人 |

**索引**：
- PK_ALIPAY_TRAVEL_RECORD (ORDER_NO) - 主键
- IDX_ALIPAY_TRAVEL_USER (THIRD_USER_ID, CHANNEL)
- IDX_ALIPAY_TRAVEL_CARD (CARD_ID)
- IDX_ALIPAY_TRAVEL_DATE (ENTRY_DATE)

**注释**：支付宝乘车记录表

---

### 10.6 ALIPAY_REG_LOG（支付宝开户流水表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| REQUEST_SEQ | VARCHAR2 | 128 | Y | - | 请求流水号（主键） |
| RESPONSE_SEQ | VARCHAR2 | 128 | N | - | 响应流水号 |
| THIRD_USER_ID | VARCHAR2 | 128 | Y | - | 支付宝用户ID |
| CARD_ID | VARCHAR2 | 64 | Y | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | Y | - | 卡类型编码 |
| CARD_ISSUE_CODE | VARCHAR2 | 64 | N | - | 发卡渠道代码 |
| CHANNEL | VARCHAR2 | 64 | Y | - | 开户渠道编码 |
| MSISDN | VARCHAR2 | 64 | N | - | 手机号 |
| EXTEND1 | VARCHAR2 | 256 | N | - | 扩展字段1 |
| EXTEND2 | VARCHAR2 | 256 | N | - | 扩展字段2 |
| REQUEST_BODY | CLOB | 4000 | N | - | 原始请求报文 |
| RESPONSE_BODY | CLOB | 4000 | N | - | 原始响应报文 |
| RESULT_CODE | VARCHAR2 | 64 | N | - | 响应码 |
| RESULT_MSG | VARCHAR2 | 1024 | N | - | 响应信息 |
| OPERATOR | VARCHAR2 | 32 | N | - | 操作人 |
| IP | VARCHAR2 | 64 | N | - | 请求IP |
| REMARK | VARCHAR2 | 256 | N | - | 备注 |
| VERSION | VARCHAR2 | 32 | N | '1' | 乐观锁版本号 |
| CREATE_TIME | DATE | - | N | SYSDATE | 创建时间 |
| CREATE_BY | VARCHAR2 | 32 | N | - | 创建人 |

**索引**：
- PK_ALIPAY_REG_LOG (REQUEST_SEQ) - 主键
- IDX_ALIPAY_REG_LOG_USER (THIRD_USER_ID, CREATE_TIME DESC)
- IDX_ALIPAY_REG_LOG_CARD (CARD_ID, CREATE_TIME DESC)

**注释**：支付宝开户流水表

---

### 10.7 ALIPAY_CARD_POOL（支付宝卡号池表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| CARD_ID | VARCHAR2 | 64 | Y | - | 逻辑卡号（主键） |
| THIRD_USER_ID | VARCHAR2 | 128 | N | - | 支付宝用户ID |
| STATUS | VARCHAR2 | 32 | Y | - | 卡号状态（UNASSIGNED未分配/ASSIGNED已分配/USED已使用） |
| INSERT_TMS | DATE | - | N | SYSDATE | 插入时间 |
| REG_TMS | DATE | - | N | - | 注册分配时间 |
| DELETE_FLAG | VARCHAR2 | 1 | N | '0' | 删除标记（0:正常 1:删除） |
| VERSION | VARCHAR2 | 32 | N | '1' | 乐观锁版本号 |
| CREATE_TIME | DATE | - | N | SYSDATE | 创建时间 |
| UPDATE_TIME | DATE | - | N | SYSDATE | 更新时间 |

**索引**：
- PK_ALIPAY_CARD_POOL (CARD_ID) - 主键
- IDX_ALIPAY_CARD_POOL_STATUS (STATUS, DELETE_FLAG)

**注释**：支付宝卡号池表

---

### 10.8 ALIPAY_TERMINATION_REQUEST（支付宝解约登记表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| TERMINATION_SEQ | VARCHAR2 | 128 | Y | - | 解约流水号（主键） |
| AGREEMENT_CODE | VARCHAR2 | 128 | Y | - | 协议号 |
| THIRD_USER_ID | VARCHAR2 | 128 | N | - | 支付宝用户ID |
| CARD_ID | VARCHAR2 | 64 | N | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | N | - | 卡类型编码 |
| CHANNEL | VARCHAR2 | 32 | N | - | 支付渠道编码 |
| MERCHANT_NO | VARCHAR2 | 128 | N | - | 合作方机构编号/商户号 |
| OPERATION_TYPE | VARCHAR2 | 128 | Y | - | 操作类型（固定TERMINATE） |
| STATUS | VARCHAR2 | 32 | Y | - | 解约状态（PENDING待处理/SUCCESS成功/FAIL失败） |
| TERMINATION_RESULT | VARCHAR2 | 1024 | N | - | 解约结果描述 |
| TERMINATION_TIME | DATE | - | N | - | 解约完成时间 |
| DELETE_FLAG | VARCHAR2 | 1 | N | '0' | 删除标记（0:正常 1:删除） |
| VERSION | VARCHAR2 | 32 | N | '1' | 乐观锁版本号 |
| CREATE_TIME | DATE | - | N | SYSDATE | 创建时间 |
| UPDATE_TIME | DATE | - | N | SYSDATE | 更新时间 |

**索引**：
- PK_ALIPAY_TERMINATION_REQUEST (TERMINATION_SEQ) - 主键
- IDX_ALIPAY_TERM_AGREEMENT (AGREEMENT_CODE, DELETE_FLAG)

**注释**：支付宝解约登记表

---

## 十一、其他表

### 11.1 GATE_TXN_PAY（二维码过闸扣费订单表）

| 字段名 | 类型 | 长度 | 必填 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| ID | NUMBER | 22 | Y | - | 主键ID |
| ORDER_NO | VARCHAR2 | 128 | Y | - | 地铁侧扣费订单号，同时作为支付接口orderNo |
| DEBIT_STATUS | VARCHAR2 | 32 | Y | 'INIT' | 扣费状态：INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待补扣，CLOSED关闭 |
| THIRD_USER_ID | VARCHAR2 | 128 | N | - | 第三方用户ID，对应设备报文itpUserId转换后的用户标识 |
| CARD_ID | VARCHAR2 | 64 | Y | - | 逻辑卡号 |
| CARD_TYPE | VARCHAR2 | 64 | Y | - | 票卡类型 |
| DEVICE_ID | VARCHAR2 | 32 | N | - | 设备ID，公共请求参数 |
| TRX_TYPE | VARCHAR2 | 8 | Y | - | 交易类型：02出站，03超时出站 |
| TICKET_TRANS_SEQ | VARCHAR2 | 128 | N | - | 二维码交易计数器，文档字段名为tikcetTransSeq |
| IN_STATION | VARCHAR2 | 32 | N | - | 进站车站代码 |
| IN_TIME | VARCHAR2 | 28 | N | - | 进站时间，格式YYYYMMDDHH24MISS |
| OUT_STATION | VARCHAR2 | 32 | N | - | 出站车站代码 |
| OUT_TIME | VARCHAR2 | 28 | Y | - | 出站时间，格式YYYYMMDDHH24MISS |
| TXN_DATE | DATE | - | Y | - | 出站交易日期，取OUT_TIME前8位，格式YYYYMMDD，用于月分区 |
| TRX_AMOUNT | NUMBER | 22 | N | 0 | 闸机交易金额，单位分 |
| OVERTIME_AMOUNT | NUMBER | 22 | N | 0 | 超时金额，单位分 |
| TOTAL_AMOUNT | NUMBER | 22 | N | 0 | 应扣总金额，单位分 |
| ISSUE_CHANNEL_CODE | VARCHAR2 | 16 | N | - | 发行渠道代码 |
| SIGN_CHANNEL_CODE | VARCHAR2 | 16 | N | - | 签约通道代码 |
| REMARK | VARCHAR2 | 1024 | N | - | 备注 |
| CREATE_TIME | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP(6) | - | N | SYSTIMESTAMP | 更新时间 |

**索引**：
- PK_GATE_TXN_PAY (ID) - 主键
- UK_GATE_TXN_PAY_ORDER_NO (ORDER_NO, TXN_DATE) - 唯一本地分区索引
- UK_GATE_TXN_PAY_BIZ (CARD_ID, TRX_TYPE, OUT_TIME, TICKET_TRANS_SEQ, DEVICE_ID, TXN_DATE) - 过闸交易幂等唯一索引（本地分区）
- IDX_GATE_TXN_PAY_CARD_DATE (CARD_ID, TXN_DATE, OUT_TIME) - 本地分区索引
- IDX_GATE_TXN_PAY_USER_DATE (THIRD_USER_ID, TXN_DATE, OUT_TIME) - 本地分区索引
- IDX_GATE_TXN_PAY_STATUS_DATE (DEBIT_STATUS, TXN_DATE, CREATE_TIME) - 本地分区索引
- IDX_GATE_TXN_PAY_DEVICE_DATE (DEVICE_ID, TXN_DATE, OUT_TIME) - 本地分区索引

**分区**：RANGE 分区，按 TXN_DATE 月分区（P202606-P202612，P_MAX 为兜底分区）

**序列**：SEQ_GATE_TXN_PAY

**注释**：二维码过闸扣费订单表

---

## 十二、分区表维护说明

### 12.1 月分区表列表

以下表采用 Oracle 月分区，按日期字段分区：

| 表名 | 分区字段 | 分区方式 | 当前分区 |
|------|----------|----------|----------|
| QRCODE_TXN_DETAIL | TXN_DATE (VARCHAR2 YYYYMMDD) | RANGE | P202606 ~ P_MAX |
| PAY_TXN_DETAIL | TXN_DATE (DATE) | RANGE | P202606 ~ P_MAX |
| PAY_REFUND_DETAIL | TXN_DATE (DATE) | RANGE | P202606 ~ P_MAX |
| PAY_CALLBACK_LOG | TXN_DATE (DATE) | RANGE | P202606 ~ P_MAX |
| GATE_TXN_PAY | TXN_DATE (DATE) | RANGE | P202606 ~ P_MAX |

### 12.2 新增分区示例

以 2027 年 1 月为例：

```sql
-- 新增 QRCODE_TXN_DETAIL 分区
ALTER TABLE QRCODE_TXN_DETAIL
SPLIT PARTITION P_MAX
AT ('20270201')
INTO (
    PARTITION P202701,
    PARTITION P_MAX
);

-- 新增 PAY_TXN_DETAIL 分区
ALTER TABLE PAY_TXN_DETAIL
SPLIT PARTITION P_MAX
AT ('20270201')
INTO (
    PARTITION P202701,
    PARTITION P_MAX
);
```

### 12.3 查询分区情况

```sql
SELECT TABLE_NAME, PARTITION_NAME, HIGH_VALUE
FROM USER_TAB_PARTITIONS
WHERE TABLE_NAME IN (
    'QRCODE_TXN_DETAIL',
    'PAY_TXN_DETAIL',
    'PAY_REFUND_DETAIL',
    'PAY_CALLBACK_LOG',
    'GATE_TXN_PAY'
)
ORDER BY TABLE_NAME, PARTITION_POSITION;
```

---

## 十三、相关文档

- `docs/02-微服务文档/alipay-pay-sign-server.md` - 支付宝支付签约服务微服务文档
- `docs/02-微服务文档/ticket-server.md` - 票务服务微服务文档
- `docs/02-微服务文档/para-server.md` - 参数服务微服务文档
- `docs/02-微服务文档/fep-dev-server.md` - FEP 开发环境服务微服务文档
- `docs/05-数据库/支付宝接口建表.sql` - 支付宝出行接口建表语句
- `docs/05-数据库/支付宝接口建表-发票字段.sql` - 支付宝接口建表发票字段

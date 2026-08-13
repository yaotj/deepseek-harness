# ticket-server 源码文档

> 本文基于 `ticket-server` 全部源码编写，路径：`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/ticket-server`
> 最后更新：2026-07-20

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 配置文件](#2-配置文件)
- [3. 接口清单](#3-接口清单)
- [4. 核心业务流程](#4-核心业务流程)
- [5. 数据模型](#5-数据模型)
- [6. 枚举常量](#6-枚举常量)
- [7. 文件清单](#7-文件清单)
- [8. 服务交互](#8-服务交互)
- [9. 待办事项](#9-待办事项)
- [10. 源码文件索引](#10-源码文件索引)

---

## 1. 模块概述

`ticket-server` 是票务核心服务，端口 `9103`，Spring Boot 3.2.6 / Java 21，Oracle 数据库。

### 1.1 核心职责

- 维护 `QRCodeStatus`：二维码乘车状态的主表，记录卡号、码状态、进站信息、最后交易信息等。
- 维护 `QRCodeTxnDetail`：二维码闸机交易明细表，记录每次闸机刷码的进出站、金额、渠道等信息。
- 处理闸机 IF1A-01 通知：接收 `AGM` 的 `notiVerifyResult`，更新票卡状态并写入交易明细。
- 支持 APP 侧乘车状态注册与查询：开户后初始化乘车状态、查询二维码状态、查询用户上次行程、自助补站。
- 支持 IF8A-05 交易记录查询：`/ci/app/requestTransList`，供 `fep-app-server` 通过 RPC 调用。
- 支持支付宝渠道行业数据推送：按 `issueChannelCode=07` 区分，闸机通知完成后推送行业数据和行程数据。

### 1.2 技术栈

- Spring Boot 3.2.6、Spring MVC、MyBatis、Oracle
- Fastjson、OkHttp、Spring 异步线程池
- para-client、industry-data-client RPC 调用

---

## 2. 配置文件

### 2.1 application.properties

路径：`ticket-server/src/main/resources/application.properties`

```properties
server.port=9103
spring.application.name=ticket-server

mybatis.mapper-locations=classpath*:mapper/*.xml

ticket.default-channel=01
ticket.default-code-status=03
ticket.default-gate-status=00
ticket.default-last-txn-station=FFFF
ticket.default-gate-in-station=FFFF

service.para.url=http://127.0.0.1:9107
service.industryData.url=http://127.0.0.1:9105

app.notify.industry-data-url=http://127.0.0.1:8080/ci/app/receiveCardDataFromItp
app.notify.alipay-industry-data-url=https://dtcustomer.bestonepay.com/ngopenplatform/notify/receiveCardDataFromItp
app.notify.alipay-push-trans-data-url=https://dtcustomer.bestonepay.com/ngopenplatform/notify/pushTransData

industry.issue-channel-code=01
industry.ticket-type=0441
industry.timestamp-expire-hours=4

other.sql.host=${DB_HOST:172.20.222.3:1521}
other.sql.type=oracle
other.sql.database=${DB_NAME:AFCITPDB}
other.sql.username=${DB_USERNAME:qditp}
other.sql.password=${DB_PASSWORD:qditp}
```

### 2.2 关键默认值说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ticket.default-channel` | `01` | 默认渠道代码 |
| `ticket.default-code-status` | `03` | 默认二维码状态：待进站 |
| `ticket.default-gate-status` | `00` | 默认进出站状态 |
| `ticket.default-last-txn-station` | `FFFF` | 默认最后交易车站 |
| `ticket.default-gate-in-station` | `FFFF` | 默认进站车站 |

---

## 3. 接口清单

### 3.1 AGM 侧接口

#### 3.1.1 IF1A-01 闸机检票通知

- **路径**：`POST /ci/agm/notiVerifyResult`
- **Controller**：`TicketAgmController`
- **Service**：`TicketRideStatusServiceImpl.notifyVerifyResult`

**请求参数**：

```java
NotifyVerifyResultReqDTO {
    String cardId;                // 逻辑卡号
    String deviceId;              // 设备ID
    String trxType;               // 交易类型：01进站、02出站、03超时出站
    String handleDateTime;        // 闸机交易时间：yyyyMMddHHmmss
    String handleStationCode;     // 闸机交易车站代码
    String issueChannelCode;      // 发行渠道代码
    String signChannelCode;       // 签约通道代码
    String cardType;              // 票卡类型
    String itpUserId;             // ITP用户编码（16进制）
    String ticketTransSeq;        // 二维码交易计数器
    String trxAmount;             // 实际交易金额（分）
    String overtimeAmount;        // 超时金额（分）
    String handleResultCode;      // 闸机处理结果码
    String reserve1;              // 预留1
    String reserve2;              // 预留2
}
```

**响应参数**：

```java
NotifyVerifyResultRespDTO {
    String retCode;   // 0000成功
    String retMsg;    // 成功
}
```

### 3.2 APP 侧接口

#### 3.2.1 注册用户乘车状态

- **路径**：`POST /ci/app/registerRideStatus`
- **Controller**：`TicketRideStatusController`
- **Service**：`TicketRideStatusServiceImpl.registerRideStatus`

**请求参数**：

```java
RegisterRideStatusReqDTO {
    String cardId;       // 逻辑卡号
    String channel;      // 渠道
    String thirdUserId;  // 第三方用户ID
}
```

**响应参数**：

```java
RegisterRideStatusRespDTO {
    String retCode;      // 0000成功
    String retMsg;       // 成功
    String cardId;       // 逻辑卡号
    String itpUserId;    // ITP用户编码
    String cardStatus;   // 二维码状态
}
```

#### 3.2.2 获取用户乘车状态

- **路径**：`POST /ci/app/queryQrCodeStatus`
- **Controller**：`TicketRideStatusController`
- **Service**：`TicketRideStatusServiceImpl.queryQrCodeStatus`

**请求参数**：

```java
QueryStatusReqDTO {
    String cardId;       // 逻辑卡号
    String thirdUserId;  // 第三方用户ID
}
```

**响应参数**：

```java
QueryStatusRespDTO {
    String retCode;         // 0000成功
    String retMsg;          // 成功
    String cardId;          // 逻辑卡号
    String thirdUserId;     // 第三方用户ID
    String gateInTime;      // 进站时间：yyyyMMddHHmmss
    String gateInStation;   // 进站车站
    String status;          // 二维码状态
    String lastTxnTime;     // 最后交易时间
    String lastTxnStation;  // 最后交易车站
    String txnSeq;          // 交易流水号
}
```

#### 3.2.3 IF8A-29 查询用户上次行程

- **路径**：`POST /ci/app/queryUserItinerary`
- **Controller**：`TicketRideStatusController`
- **Service**：`TicketRideStatusServiceImpl.queryUserItinerary`

**请求参数**：

```java
QueryUserItineraryReqDTO {
    String cardNum;  // 逻辑卡号
}
```

**响应参数**：

```java
QueryUserItineraryResult {
    String retCode;
    String retMsg;
    MemberItineraryDTO memberItinerary; {
        String ticketStatus;      // 票卡状态
        String payStatus;         // 支付状态
        String thisStationCode;   // 当前车站代码
        String thisStationName;   // 当前车站名称
        String thisTransTime;     // 当前交易时间
        String transSeq;          // 交易流水号
        Integer transValue;       // 交易金额
        Integer overtimeTransValue; // 超时金额
        String payChannel;        // 支付渠道
        Integer oriTicketAmt;     // 原票价
        Integer debitAmt;         // 扣款金额
        Integer orderExpType;     // 订单异常类型
        String discountInfo;      // 优惠信息
        Integer carbonDiscount;   // 碳减排优惠
        String lastStationCode;   // 上次车站代码（出站交易才有）
        String lastStationName;   // 上次车站名称
        String lastTransTime;     // 上次交易时间
    }
}
```

#### 3.2.4 IF8A-04 请求自助补站

- **路径**：`POST /ci/app/requestExcessFare`
- **Controller**：`TicketRideStatusController`
- **Service**：`TicketRideStatusServiceImpl.requestExcessFare`

**请求参数**：

```java
RequestExcessFareReqDTO {
    String cardId;                // 逻辑卡号
    String upgradeAreaType;       // 补站类型：01补进站、02补出站
    String upgradeStationCode;    // 补站车站代码
    String upgradeDateTime;       // 补站时间：yyyyMMddHHmmss
}
```

**响应参数**：

```java
RequestExcessFareResult {
    String retCode;  // 0000成功
    String retMsg;   // 成功
}
```

#### 3.2.5 查询进站设备

- **路径**：`GET /ci/app/queryEntryDevice`
- **Controller**：`TicketRideStatusController`
- **Service**：`TicketRideStatusServiceImpl.queryEntryDevice`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `cardId` | String | 是 | 逻辑卡号 |

**响应**：设备ID字符串（String）

#### 3.2.6 IF1A-01 闸机检票通知（APP侧透传）

- **路径**：`POST /ci/app/notiVerifyResult`
- **Controller**：`TicketRideStatusController`
- **Service**：`TicketRideStatusServiceImpl.notifyVerifyResult`

与 AGM 侧接口业务逻辑一致，用于 APP 侧调用的透传。

### 3.3 Channel 侧接口

#### 3.3.1 支付宝出行-查询乘车记录列表

- **路径**：`POST /ci/channel/findTravelList`
- **Controller**：`AlipayTripController`
- **Service**：`TicketTransServiceImpl.alipayTripFindTravelList`

**请求参数**：

```java
AlipayTripFindTravelListReqDTO {
    String thirdUserId;  // 第三方用户ID
    String startDate;    // 开始日期：yyyyMMdd
    String endDate;      // 结束日期：yyyyMMdd
    String page;         // 页码
    String size;         // 每页大小
}
```

**响应参数**：

```java
AlipayTripFindTravelListRespDTO {
    String retCode;
    String retMsg;
    int pageNumber;
    int pageSize;
    int totalPage;
    int totalCount;
    List<AlipayTripTravelRecordDTO> ticketTransRecord;
}
```

#### 3.3.2 支付宝出行-查询乘车记录详情

- **路径**：`POST /ci/channel/findTravelDetail`
- **Controller**：`AlipayTripController`
- **Service**：`TicketTransServiceImpl.alipayTripFindTravelDetail`

**请求参数**：

```java
AlipayTripFindTravelDetailReqDTO {
    String thirdUserId;  // 第三方用户ID
    String orderNo;      // 订单号/交易流水号
    String cardId;       // 逻辑卡号（可选）
}
```

**响应参数**：

```java
AlipayTripFindTravelDetailRespDTO {
    String retCode;
    String retMsg;
    String entryStationName;
    String entryDate;
    String exitStationName;
    String exitDate;
    String payAmount;
    String totalAmount;
    String orderExpType;
    String tradeOrderNo;
    String payTradeOrderNo;
    String payOrderNoDate;
    String payChannelCode;
    String debitRequestResult;
    String discountFee;
    String discountInfo;
    String companionFlag;
    String cardNum;
    String ticketCode;
    String countingTimes;
    String countingFlag;
}
```

### 3.4 交易记录接口

#### 3.4.1 IF8A-05 请求查询交易记录

- **路径**：`POST /ci/app/requestTransList`
- **Controller**：`TicketTransController`
- **Service**：`TicketTransServiceImpl.requestTransList`

**请求参数**：

```java
RequestTransListReqDTO {
    String thirdUserId;       // 第三方用户ID
    String cardId;            // 逻辑卡号
    String cardType;          // 票卡类型
    Integer pageNumber;       // 页码
    Integer pageSize;         // 每页大小
    String startDate;         // 开始日期：yyyyMMdd
    String endDate;           // 结束日期：yyyyMMdd
    String debitRequestResult;// 扣款结果：空=全部，0=成功，1=失败
    String ticketCode;        // 日票票号
}
```

**响应参数**：

```java
RequestTransListResult {
    String retCode;
    String retMsg;
    String pageNumber;
    String pageSize;
    String totalPage;
    List<TransRecordDTO> ticketTransRecord;
}
```

**TransRecordDTO 字段**：

```java
TransRecordDTO {
    String entryStationName;
    String entryDate;
    String exitStationName;
    String exitDate;
    String payAmount;
    String orderExpType;
    String tradeOrderNo;
    String payTradeOrderNo;
    String payOrderNoDate;
    String payChannelCode;
    String debitRequestResult;
    Integer discountFee;
    String discountInfo;
    String companionFlag;
    String cardNum;
    String ticketCode;
    Integer countingTimes;
    String countingFlag;
    String offlineFlag;
    String attributableParty;
    String receivingParty;
}
```

---

## 4. 核心业务流程

### 4.1 闸机检票通知（IF1A-01）

**入口**：`TicketAgmController.notifyVerifyResult`

**业务逻辑**（`TicketRideStatusServiceImpl.notifyVerifyResult`）：

1. 参数校验：`cardId`、`handleDateTime`、`trxType` 必填。
2. 查询当前 `QRCodeStatus`：根据 `cardId` 查询。
3. 填充上次交易字段：将当前状态的 `codeStatus`、`lastTxnStation`、`lastTxnTime` 写入本次请求的 `lastTicketStatus`、`lastHandleStationCode`、`lastHandleDateTime`。
4. 构建交易明细 `QRCodeTxnDetail`：
   - `itpUserId` 从 16 进制转 10 进制。
   - `txnDate` 取 `handleDateTime` 前 8 位。
   - `trxAmount`、`overtimeAmount` 从字符串转 `Long`。
5. 写入 `QRCODE_TXN_DETAIL`：
   - 主键 `ID` 使用 `SEQ_QRCODE_TXN_DETAIL.NEXTVAL`。
   - 唯一索引 `(CARD_ID, TRX_TYPE, HANDLE_DATE_TIME, TICKET_TRANS_SEQ, DEVICE_ID, TXN_DATE)`。
   - 若触发 `DuplicateKeyException`，返回成功（幂等处理）。
6. 更新 `QRCodeStatus`：
   - `useCount` + 1。
   - `codeStatus` 按 `trxType` 映射：`01->04`（已进站）、`02->05`（已出站）、`03->06`（超时出站）。
   - `gateStatus` 直接记录 `trxType`。
   - `lastTxnTime`、`lastTxnStation`、`txnSeq` 更新为本次值。
   - `gateInTime`、`gateInStation` 仅在 `trxType=01` 时更新。
7. 异步通知 APP 行业数据：`appNotifyService.notifyVerifyResult`。
8. 支付宝渠道特殊处理：若 `issueChannelCode=07`，调用 `appNotifyService.pushAlipayTripData` 推进行程数据。

**状态映射表**：

| `trxType` | 含义 | `codeStatus` | `gateStatus` |
|-----------|------|--------------|--------------|
| `01` | 进站 | `04` | `01` |
| `02` | 出站 | `05` | `02` |
| `03` | 超时出站 | `06` | `03` |

### 4.2 注册用户乘车状态

**入口**：`TicketRideStatusController.registerRideStatus`

**业务逻辑**（`TicketRideStatusServiceImpl.registerRideStatus`）：

1. 参数校验：`cardId` 必填。
2. 查询现有 `QRCodeStatus`：若不存在则新建，`useCount=0`。
3. 初始化状态：
   - `channel`：请求渠道，默认 `01`。
   - `codeStatus`：默认 `03`（待进站）。
   - `lastTxnTime`：`00000000000000`。
   - `lastTxnStation`：`FFFF`。
   - `txnSeq`：`0`。
   - `gateStatus`：`00`。
   - `gateInTime`：`00000000000000`。
   - `gateInStation`：`FFFF`。
4. 调用 `qrCodeStatusMapper.upsert` 持久化。
5. 返回 `cardId`、`itpUserId`、`cardStatus`。

### 4.3 查询二维码状态

**入口**：`TicketRideStatusController.queryQrCodeStatus`

**业务逻辑**（`TicketRideStatusServiceImpl.queryQrCodeStatus`）：

1. 参数校验：`cardId` 必填。
2. 查询 `QRCodeStatus`：若不存在返回 `8004`（没有账号卡片数据）。
3. 返回当前状态的 `gateInTime`、`gateInStation`、`codeStatus`、`lastTxnTime`、`lastTxnStation`、`txnSeq`。

### 4.4 查询用户上次行程（IF8A-29）

**入口**：`TicketRideStatusController.queryUserItinerary`

**业务逻辑**（`TicketRideStatusServiceImpl.queryUserItinerary`）：

1. 参数校验：`cardNum` 必填。
2. 查询当前 `QRCodeStatus`。
3. 查询最新交易明细：`qrCodeTxnDetailMapper.selectLatestByCardId`。
4. 构建 `MemberItineraryDTO`：
   - 若无交易明细，使用 `QRCodeStatus` 的 `lastTxnStation`、`lastTxnTime`、`txnSeq`。
   - 若有交易明细，使用明细的 `handleStationCode`、`handleDateTime`、`ticketTransSeq`、`trxAmount`、`overtimeAmount`、`signChannelCode`。
   - 若为出站交易（`trxType=02/03`），填充 `lastStationCode`、`lastStationName`、`lastTransTime`。
   - 车站名称通过 `paraClient.requestStationName` 查询参数服务解析。

### 4.5 自助补站（IF8A-04）

**入口**：`TicketRideStatusController.requestExcessFare`

**业务逻辑**（`TicketRideStatusServiceImpl.requestExcessFare`）：

1. 参数校验：`cardId`、`upgradeAreaType`、`upgradeStationCode` 必填。
2. 查询现有 `QRCodeStatus`：若不存在则新建。
3. 补出站校验：若 `upgradeAreaType=02` 且无进站记录，返回 `8304`（没有进站记录，无法补出站）。
4. 构建新状态：
   - `upgradeAreaType=01`（补进站）：`codeStatus=04`，`gateInTime`、`gateInStation` 更新为补值。
   - `upgradeAreaType=02`（补出站）：`codeStatus=05`，`gateInTime`、`gateInStation` 保留原值。
5. 调用 `qrCodeStatusMapper.upsert` 持久化。
6. 返回 `0000`。

**注意**：当前仅更新平台状态，TODO 需调用 BOM/ACC 真实接口同步进出站记录，以及费用扣减、日票逻辑待 Phase 2 实现。

### 4.6 交易记录查询（IF8A-05）

**入口**：`TicketTransController.requestTransList`

**业务逻辑**（`TicketTransServiceImpl.requestTransList`）：

1. 分页参数：`pageNumber` 默认 1，`pageSize` 默认 10。
2. 日期格式：`startDate`、`endDate` 去掉 `-`。
3. 票卡类型映射：`cardType` 通过 `CardTypeMapping.toIssueCardType` 转换。
4. 调用 `qrCodeTxnDetailMapper.selectTransList`、`countTransList` 分页查询。
5. `discountInfo` 默认值 `[]`。
6. 返回分页结果。

### 4.7 支付宝出行记录查询

**入口**：`AlipayTripController.findTravelList`、`findTravelDetail`

**业务逻辑**（`TicketTransServiceImpl.alipayTripFindTravelList/alipayTripFindTravelDetail`）：

1. 日期格式处理同上。
2. 仅查询 `ISSUE_CHANNEL_CODE='07'` 的交易明细。
3. 列表：按 `thirdUserId`、日期范围分页查询。
4. 详情：按 `thirdUserId`、`orderNo=ticketTransSeq` 查询单条记录。
5. `totalAmount = NVL(trxAmount, 0) + NVL(overtimeAmount, 0)`。
6. 转换为 `AlipayTripTravelRecordDTO` 返回。

### 4.8 行业数据推送与行程数据推送

**入口**：`AppNotifyServiceImpl.notifyVerifyResult`、`pushAlipayTripData`

**行业数据推送流程**（异步线程池执行）：

1. 调用 `industryDataClient.buildCardData` 生成卡数据。
2. 根据 `issueChannelCode` 选择推送地址：
   - `07`：支付宝地址 `alipayIndustryDataNotifyUrl`。
   - 其他：默认地址 `industryDataNotifyUrl`。
3. 通过 OkHttp POST `multipart/form-data` 推送 `bizData`。

**行程数据推送流程**（支付宝渠道）：

1. 构建 `AlipayPushTransDataReqDTO`：
   - `transSeq`、`tirpNo` 由 `cardId + handleDateTime + trxType` 生成。
   - `transLine` 通过 `stationInfoMapper.selectByStationCode` 查询 `lineCode`，兜底使用 `stationCode`。
2. 通过 OkHttp POST 推送到 `alipayPushTransDataUrl`。

---

## 5. 数据模型

### 5.1 QRCODE_STATUS

表名：`QRCODE_STATUS`

**表结构**：

```sql
CREATE TABLE QRCODE_STATUS (
    CARD_ID           VARCHAR2(32 CHAR)   NOT NULL PRIMARY KEY,
    USE_COUNT         NUMBER(10),
    CHANNEL           VARCHAR2(4),
    CODE_STATUS       VARCHAR2(4),
    GATE_IN_TIME      VARCHAR2(14),
    GATE_IN_STATION   VARCHAR2(4),
    LAST_TXN_TIME     VARCHAR2(14),
    LAST_TXN_STATION  VARCHAR2(4),
    TXN_SEQ           VARCHAR2(64),
    CREATE_TIME       TIMESTAMP,
    UPDATE_TIME       TIMESTAMP,
    GATE_STATUS       VARCHAR2(32),
    CARD_ID_TAIL      VARCHAR2(2 CHAR) GENERATED ALWAYS AS (SUBSTR(CARD_ID, -2)) VIRTUAL
) PARTITION BY LIST (CARD_ID_TAIL) (P_00 ... P_99, P_OTHER);
```

**分区说明**：按 `CARD_ID` 后两位虚拟列 `CARD_ID_TAIL` 做 `LIST` 分区，共 101 个分区（`P_00` ~ `P_99`、`P_OTHER`）。

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `CARD_ID` | VARCHAR2(32) | 二维码卡号（主键） |
| `USE_COUNT` | NUMBER(10) | 使用次数 |
| `CHANNEL` | VARCHAR2(4) | 渠道 |
| `CODE_STATUS` | VARCHAR2(4) | 二维码状态 |
| `GATE_IN_TIME` | VARCHAR2(14) | 进站时间（yyyymmddhh24miss） |
| `GATE_IN_STATION` | VARCHAR2(4) | 进站车站 |
| `LAST_TXN_TIME` | VARCHAR2(14) | 最后交易时间 |
| `LAST_TXN_STATION` | VARCHAR2(4) | 最后交易车站 |
| `TXN_SEQ` | VARCHAR2(64) | 交易流水号 |
| `CREATE_TIME` | TIMESTAMP | 创建时间 |
| `UPDATE_TIME` | TIMESTAMP | 更新时间 |
| `GATE_STATUS` | VARCHAR2(32) | 进出站状态 |

**Entity**：`QRCodeStatus`（`com.chinasofti.huateng.ticket.entity.QRCodeStatus`）

### 5.2 QRCODE_TXN_DETAIL

表名：`QRCODE_TXN_DETAIL`

**表结构**：

```sql
CREATE TABLE QRCODE_TXN_DETAIL (
    ID                        NUMBER(20) NOT NULL PRIMARY KEY,
    DEVICE_ID                 VARCHAR2(16 CHAR),
    ITP_USER_ID               VARCHAR2(64 CHAR),
    TRX_TYPE                  VARCHAR2(4 CHAR) NOT NULL,
    ISSUE_CHANNEL_CODE        VARCHAR2(8 CHAR),
    SIGN_CHANNEL_CODE         VARCHAR2(8 CHAR),
    CARD_ID                   VARCHAR2(32 CHAR) NOT NULL,
    CARD_TYPE                 VARCHAR2(8 CHAR),
    HANDLE_DATE_TIME          VARCHAR2(14 CHAR) NOT NULL,
    TXN_DATE                  VARCHAR2(8 CHAR) NOT NULL,
    HANDLE_STATION_CODE       VARCHAR2(16 CHAR),
    TRX_AMOUNT                NUMBER(12),
    OVERTIME_AMOUNT           NUMBER(12),
    LAST_TICKET_STATUS        VARCHAR2(4 CHAR),
    HANDLE_RESULT_CODE        VARCHAR2(8 CHAR),
    LAST_HANDLE_STATION_CODE  VARCHAR2(16 CHAR),
    LAST_HANDLE_DATE_TIME     VARCHAR2(14 CHAR),
    TICKET_TRANS_SEQ          VARCHAR2(64 CHAR),
    RESERVE1                  VARCHAR2(512 CHAR),
    RESERVE2                  VARCHAR2(512 CHAR),
    CREATE_TIME               TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
) PARTITION BY RANGE (TXN_DATE) (...);
```

**分区说明**：按 `TXN_DATE`（字符串 `YYYYMMDD`）做 `RANGE` 月分区，目前有 `P202606` ~ `P202612`、`P202701`、`P_MAX` 等分区，需按月维护后续分区。

**索引说明**：

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| `UK_QRCODE_TXN_DETAIL_BIZ` | LOCAL UNIQUE | `(CARD_ID, TRX_TYPE, HANDLE_DATE_TIME, TICKET_TRANS_SEQ, DEVICE_ID, TXN_DATE)` | 业务幂等唯一索引 |
| `IDX_QRCODE_TXN_DETAIL_CARD_DATE` | LOCAL | `(CARD_ID, TXN_DATE, HANDLE_DATE_TIME)` | 按卡号查询 |
| `IDX_QRCODE_TXN_DETAIL_DEVICE_DATE` | LOCAL | `(DEVICE_ID, TXN_DATE, HANDLE_DATE_TIME)` | 按设备查询 |
| `IDX_QRCODE_TXN_DETAIL_USER_DATE` | LOCAL | `(ITP_USER_ID, TXN_DATE, HANDLE_DATE_TIME)` | 按用户查询 |

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID` | NUMBER(20) | 主键ID（序列自增） |
| `DEVICE_ID` | VARCHAR2(16) | 设备ID |
| `ITP_USER_ID` | VARCHAR2(64) | ITP用户编码（10进制） |
| `TRX_TYPE` | VARCHAR2(4) | 交易类型：01进站、02出站、03超时出站 |
| `ISSUE_CHANNEL_CODE` | VARCHAR2(8) | 发行渠道代码 |
| `SIGN_CHANNEL_CODE` | VARCHAR2(8) | 签约通道代码 |
| `CARD_ID` | VARCHAR2(32) | 逻辑卡号 |
| `CARD_TYPE` | VARCHAR2(8) | 票卡类型 |
| `HANDLE_DATE_TIME` | VARCHAR2(14) | 闸机交易时间（yyyyMMddHHmmss） |
| `TXN_DATE` | VARCHAR2(8) | 交易日期（yyyyMMdd，取 HANDLE_DATE_TIME 前8位） |
| `HANDLE_STATION_CODE` | VARCHAR2(16) | 闸机交易车站代码 |
| `TRX_AMOUNT` | NUMBER(12) | 实际交易金额（分） |
| `OVERTIME_AMOUNT` | NUMBER(12) | 超时金额（分） |
| `LAST_TICKET_STATUS` | VARCHAR2(4) | 上次票卡状态 |
| `HANDLE_RESULT_CODE` | VARCHAR2(8) | 闸机处理结果码 |
| `LAST_HANDLE_STATION_CODE` | VARCHAR2(16) | 上次交易车站代码 |
| `LAST_HANDLE_DATE_TIME` | VARCHAR2(14) | 上次交易时间 |
| `TICKET_TRANS_SEQ` | VARCHAR2(64) | 二维码交易计数器 |
| `RESERVE1` | VARCHAR2(512) | 预留字段1 |
| `RESERVE2` | VARCHAR2(512) | 预留字段2 |
| `CREATE_TIME` | TIMESTAMP | 创建时间 |

**Entity**：`QRCodeTxnDetail`（`com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail`）

### 5.3 STATION_INFO

表名：`STATION_INFO`

```sql
CREATE TABLE STATION_INFO (
    STATION_CODE     VARCHAR2(16 CHAR)  NOT NULL PRIMARY KEY,
    LINE_CODE        VARCHAR2(8 CHAR)   NOT NULL,
    STATION_NAME     VARCHAR2(128 CHAR) NOT NULL,
    STATION_EN_NAME  VARCHAR2(256 CHAR)
);
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `STATION_CODE` | VARCHAR2(16) | 车站编号（主键） |
| `LINE_CODE` | VARCHAR2(8) | 线路编号 |
| `STATION_NAME` | VARCHAR2(128) | 车站名称 |
| `STATION_EN_NAME` | VARCHAR2(256) | 车站英文名称 |

**Entity**：`StationInfo`（`com.chinasofti.huateng.ticket.entity.StationInfo`）

**初始化数据**：包含青岛地铁 1/2/3/4/6/8/11/13 号线共约 130+ 个车站。

---

## 6. 枚举常量

### 6.1 TicketErrorCodeEnum

路径：`com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum`

| 枚举 | 返回码 | 说明 |
|------|--------|------|
| `SUCCESS` | `0000` | 成功 |
| `FAIL` | `9999` | 失败 |
| `SYSTEM_ERROR` | `9001` | 系统内部错误 |
| `INVALID_PARAM` | `8001` | 无效的参数 |
| `NO_DATA` | `8002` | 无数据 |
| `QR_CODE_NOT_FOUND` | `8004` | 没有账号卡片数据 |
| `CODE_STATUS_NORMAL` | `8301` | 码状态正常，无需更新 |
| `EXIT_STATION_SUCCESS` | `8302` | 补出站成功 |
| `ENTRY_STATION_SUCCESS` | `8303` | 补进站成功 |
| `AGM_RETURN_STATUS_ABNORMAL1` | `8401` | AGM返回值状态异常 |
| `AGM_RETURN_STATUS_ABNORMAL2` | `8402` | AGM返回值状态异常 |
| `ACC_COMM_ERROR` | `8501` | ACC通讯异常 |
| `ACC_RETURN_STATUS_ABNORMAL` | `8502` | ACC返回值状态异常 |
| `NO_ENTRY_RECORD` | `8304` | 没有进站记录，无法补出站 |

### 6.2 业务常量

| 常量 | 来源 | 说明 |
|------|------|------|
| `ticket.default-channel` | application.properties | 默认渠道代码：`01` |
| `ticket.default-code-status` | application.properties | 默认二维码状态：`03`（待进站） |
| `ticket.default-gate-status` | application.properties | 默认进出站状态：`00` |
| `ticket.default-last-txn-station` | application.properties | 默认最后交易车站：`FFFF` |
| `ticket.default-gate-in-station` | application.properties | 默认进站车站：`FFFF` |

### 6.3 交易类型 `trxType`

| 代码 | 含义 | 映射 `codeStatus` |
|------|------|-------------------|
| `01` | 进站 | `04`（已进站） |
| `02` | 出站 | `05`（已出站） |
| `03` | 超时出站 | `06`（超时出站） |

### 6.4 二维码状态 `codeStatus`

| 代码 | 含义 |
|------|------|
| `03` | 待进站 |
| `04` | 已进站 |
| `05` | 已出站 |
| `06` | 超时出站 |

### 6.5 渠道代码 `issueChannelCode`

| 代码 | 含义 |
|------|------|
| `01` | 默认渠道 |
| `07` | 支付宝渠道 |

### 6.6 补站类型 `upgradeAreaType`

| 代码 | 含义 |
|------|------|
| `01` | 补进站 |
| `02` | 补出站 |

---

## 7. 文件清单

### 7.1 Java 源文件

| 相对路径 | 说明 |
|----------|------|
| `src/main/java/com/chinasofti/huateng/TicketServer.java` | 启动类 |
| `src/main/java/com/chinasofti/huateng/ticket/config/TicketAsyncConfig.java` | 异步线程池配置 |
| `src/main/java/com/chinasofti/huateng/ticket/constant/TicketErrorCodeEnum.java` | 错误码枚举 |
| `src/main/java/com/chinasofti/huateng/ticket/controller/ci/agm/TicketAgmController.java` | AGM 侧控制器 |
| `src/main/java/com/chinasofti/huateng/ticket/controller/ci/app/TicketRideStatusController.java` | APP 侧乘车状态控制器 |
| `src/main/java/com/chinasofti/huateng/ticket/controller/ci/app/TicketTransController.java` | APP 侧交易记录控制器 |
| `src/main/java/com/chinasofti/huateng/ticket/controller/ci/channel/AlipayTripController.java` | 支付宝出行控制器 |
| `src/main/java/com/chinasofti/huateng/ticket/entity/QRCodeStatus.java` | 二维码状态实体 |
| `src/main/java/com/chinasofti/huateng/ticket/entity/QRCodeTxnDetail.java` | 交易明细实体 |
| `src/main/java/com/chinasofti/huateng/ticket/entity/StationInfo.java` | 车站信息实体 |
| `src/main/java/com/chinasofti/huateng/ticket/mapper/QRCodeStatusMapper.java` | 二维码状态 Mapper |
| `src/main/java/com/chinasofti/huateng/ticket/mapper/QRCodeTxnDetailMapper.java` | 交易明细 Mapper |
| `src/main/java/com/chinasofti/huateng/ticket/mapper/StationInfoMapper.java` | 车站信息 Mapper |
| `src/main/java/com/chinasofti/huateng/ticket/model/app/RequestTransListReqDTO.java` | 交易记录请求参数 |
| `src/main/java/com/chinasofti/huateng/ticket/model/app/RequestTransListResult.java` | 交易记录响应结果 |
| `src/main/java/com/chinasofti/huateng/ticket/model/app/TransRecordDTO.java` | 单条交易记录 DTO |
| `src/main/java/com/chinasofti/huateng/ticket/service/TicketRideStatusService.java` | 乘车状态服务接口 |
| `src/main/java/com/chinasofti/huateng/ticket/service/TicketTransService.java` | 交易记录服务接口 |
| `src/main/java/com/chinasofti/huateng/ticket/service/AppNotifyService.java` | APP 通知服务接口 |
| `src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java` | 乘车状态服务实现 |
| `src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketTransServiceImpl.java` | 交易记录服务实现 |
| `src/main/java/com/chinasofti/huateng/ticket/service/impl/AppNotifyServiceImpl.java` | APP 通知服务实现 |

### 7.2 资源文件

| 相对路径 | 说明 |
|----------|------|
| `src/main/resources/application.properties` | 配置文件 |
| `src/main/resources/mapper/QRCodeStatusMapper.xml` | 二维码状态 Mapper XML |
| `src/main/resources/mapper/QRCodeTxnDetailMapper.xml` | 交易明细 Mapper XML |
| `src/main/resources/mapper/StationInfoMapper.xml` | 车站信息 Mapper XML |
| `src/main/resources/sql/ticket-server-schema.sql` | 二维码状态表 DDL |
| `src/main/resources/sql/qrcode-txn-detail-schema.sql` | 交易明细表 DDL |
| `src/main/resources/sql/station-info-schema.sql` | 车站信息表 DDL + 初始化数据 |

---

## 8. 服务交互

### 8.1 上游调用

| 服务 | 调用方式 | 接口 | 说明 |
|------|----------|------|------|
| `fep-app-server` | HTTP | `/ci/agm/notiVerifyResult` | 闸机 IF1A-01 通知 |
| `fep-app-server` | HTTP | `/ci/app/registerRideStatus` | 开户后注册乘车状态 |
| `fep-app-server` | HTTP | `/ci/app/queryQrCodeStatus` | 查询二维码状态 |
| `fep-app-server` | HTTP | `/ci/app/queryUserItinerary` | 查询用户上次行程 |
| `fep-app-server` | HTTP | `/ci/app/requestExcessFare` | 自助补站 |
| `fep-app-server` | HTTP | `/ci/app/requestTransList` | IF8A-05 查询交易记录 |
| `fep-app-server` | HTTP | `/ci/app/queryEntryDevice` | 查询进站设备 |
| 支付宝 | HTTP | `/ci/channel/findTravelList` | 查询乘车记录列表 |
| 支付宝 | HTTP | `/ci/channel/findTravelDetail` | 查询乘车记录详情 |

### 8.2 下游依赖

| 服务 | 调用方式 | 接口/方法 | 说明 |
|------|----------|-----------|------|
| `para-server` | RPC | `requestStationName` | 查询车站名称 |
| `industry-data-server` | RPC | `buildCardData` | 生成行业卡数据 |
| `APP` | HTTP POST | `/ci/app/receiveCardDataFromItp` | 推送行业数据 |
| `支付宝` | HTTP POST | `/ngopenplatform/notify/receiveCardDataFromItp` | 支付宝行业数据推送 |
| `支付宝` | HTTP POST | `/ngopenplatform/notify/pushTransData` | 推送行程数据 |

### 8.3 交互图

```
┌──────────┐     IF1A-01      ┌──────────────┐
│ AGM/FEP  │ ───────────────► │ ticket-server│
└──────────┘                  └──────┬───────┘
                                      │
                                      │ 1. 写入 QRCODE_TXN_DETAIL
                                      │ 2. 更新 QRCODE_STATUS
                                      │ 3. 异步推送行业数据
                                      ▼
                              ┌──────────────┐
                              │ industry-data│
                              └──────────────┘
                                      │
                                      │ generate cardData
                                      ▼
                              ┌──────────────┐
                              │     APP      │
                              └──────────────┘
```

```
┌──────────┐     IF1A-01      ┌──────────────┐
│ AGM/FEP  │ ───────────────► │ ticket-server│
└──────────┘                  └──────┬───────┘
                            issueChannelCode=07
                                      │
                                      │ pushAlipayTripData
                                      ▼
                              ┌──────────────┐
                              │   支付宝      │
                              └──────────────┘
```

---

## 9. 待办事项

以下为源码中标注的 TODO：

1. **补站后需调用 BOM/ACC 真实接口同步进出站记录**（`TicketRideStatusServiceImpl.requestExcessFare`）
2. **费用扣减、超时判断、日票逻辑待 Phase 2 实现**（`TicketRideStatusServiceImpl.requestExcessFare`）
3. **补站成功后需推送行业数据更新（IF8B-01），通知 APP 刷新生码数据**（`TicketRideStatusServiceImpl.requestExcessFare`）
4. **支付宝行程数据推送中 `orderExpType`、`debitRequestResult`、`companionFlag`、`countingTimes`、`countingFlag` 字段待补全**（`AppNotifyServiceImpl.pushAlipayTripData`、`TicketTransServiceImpl.convertToAlipayTripTravelRecordDTO`）
5. **QRCODE_TXN_DETAIL 月分区需按月维护后续分区**（`qrcode-txn-detail-schema.sql`）

---

## 10. 源码文件索引

### 10.1 所有 Java 源文件

```
ticket-server/pom.xml
ticket-server/src/main/java/com/chinasofti/huateng/TicketServer.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/config/TicketAsyncConfig.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/constant/TicketErrorCodeEnum.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/controller/ci/agm/TicketAgmController.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/controller/ci/app/TicketRideStatusController.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/controller/ci/app/TicketTransController.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/controller/ci/channel/AlipayTripController.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/entity/QRCodeStatus.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/entity/QRCodeTxnDetail.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/entity/StationInfo.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/mapper/QRCodeStatusMapper.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/mapper/QRCodeTxnDetailMapper.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/mapper/StationInfoMapper.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/model/app/RequestTransListReqDTO.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/model/app/RequestTransListResult.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/model/app/TransRecordDTO.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/TicketRideStatusService.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/TicketTransService.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/AppNotifyService.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketTransServiceImpl.java
ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/AppNotifyServiceImpl.java
```

### 10.2 所有资源文件

```
ticket-server/src/main/resources/application.properties
ticket-server/src/main/resources/mapper/QRCodeStatusMapper.xml
ticket-server/src/main/resources/mapper/QRCodeTxnDetailMapper.xml
ticket-server/src/main/resources/mapper/StationInfoMapper.xml
ticket-server/src/main/resources/sql/ticket-server-schema.sql
ticket-server/src/main/resources/sql/qrcode-txn-detail-schema.sql
ticket-server/src/main/resources/sql/station-info-schema.sql
```

---

## 附录：业务术语表

| 术语 | 英文 | 说明 |
|------|------|------|
| 闸机 | AGM | Automatic Gate Machine，自动检票机 |
| 二维码状态 | QRCodeStatus | 票卡乘车状态的持久化记录 |
| 交易明细 | QRCodeTxnDetail | 每次闸机刷码交易的详细记录 |
| 发码渠道 | issueChannelCode | 二维码的发行渠道，如 01 默认、07 支付宝 |
| 交易类型 | trxType | 01 进站、02 出站、03 超时出站 |
| 票卡状态 | codeStatus | 03 待进站、04 已进站、05 已出站、06 超时出站 |
| 自助补站 | excess fare | 乘客因未正常进出站，APP 端申请补进出站记录 |
| 行业数据 | industry data | 按交通部规范推送给 APP 的卡数据，用于刷码乘车 |
| 行程数据 | trip data | 推送给支付宝的乘车行程记录 |

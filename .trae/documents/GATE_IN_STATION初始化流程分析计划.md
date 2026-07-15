# GATE_IN_STATION 初始化流程分析与修复计划

## 摘要

用户要求分析 `GATE_IN_STATION` 变量的完整初始化流程。经代码分析，发现关键问题：`GATE_IN_STATION`（进站车站）在初始化时被错误地使用了 `defaultGateStatus`（进出站状态默认值 `"00"`），而非独立的车站默认值。这导致 `GATE_IN_STATION` 初始化为 `"00"`，与字段语义（进站车站）不符。

## 当前状态分析

### 完整调用链

```
【初始化流程 - 开户时】
ticket-server application.properties
    ticket.default-gate-status=00
        ↓
TicketRideStatusServiceImpl.registerRideStatus()
    qrCodeStatus.setGateInStation(defaultGateStatus)  // ← 错误：应为车站默认值，而非状态默认值
        ↓
QRCodeStatusMapper.upsert() → QRCODE_STATUS 表
    字段：GATE_IN_STATION VARCHAR2(4)

【查询流程 - 拉码时】
fep-app-server: IndustryDataServiceImpl.requestIndustryData()
    ↓
    queryTicketStatus() → TicketClient.queryQrCodeStatus()
        ↓
ticket-server: TicketRideStatusServiceImpl.queryQrCodeStatus()
    ↓
QRCodeStatusMapper.selectByCardId() → QRCODE_STATUS 表
    ↓
返回 QueryStatusRespDTO (含 gateInStation="00")
    ↓
fep-app-server: buildUnsignedIndustryData()
    firstNonBlankExcludeZero(lastTxnStation, gateInStation)
        // lastTxnStation 为 "0000"（全0），被排除
        // gateInStation 为 "00"，非全0，被选中
        ↓
    normalizeHex("00", 4, "FFFF") → "0000"  // 补零到4位
        ↓
    行业数据报文中站点码为 "0000"
```

### 关键代码

**1. 初始化位置（问题根源）**

**文件:** [TicketRideStatusServiceImpl.java](file:///d:/workspace/zr/qditp/ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java#L31-L32)

```java
@Value("${ticket.default-gate-status:00}")
private String defaultGateStatus;
```

**文件:** [TicketRideStatusServiceImpl.java](file:///d:/workspace/zr/qditp/ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java#L64-L65)

```java
qrCodeStatus.setGateStatus(defaultGateStatus);      // 正确：GATE_STATUS = "00"
qrCodeStatus.setGateInStation(defaultGateStatus);   // ← 错误：GATE_IN_STATION = "00"
```

**问题：** `GATE_IN_STATION`（进站车站）被错误地赋值为 `defaultGateStatus`（进出站状态），语义不匹配。

**2. fep-app-server 使用逻辑**

**文件:** [IndustryDataServiceImpl.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L168)

```java
String lastStationCode = normalizeHex(
    firstNonBlankExcludeZero(qrStatus.getLastTxnStation(), qrStatus.getGateInStation()), 
    4, 
    "FFFF"
);
```

- `firstNonBlankExcludeZero` 优先返回非空且不全为0的值
- `lastTxnStation` 初始为 `"0000"`（全0，被排除）
- `gateInStation` 初始为 `"00"`（非全0，被选中）
- `normalizeHex("00", 4, "FFFF")` → `"0000"`（补零到4位）

**3. 相关配置**

**文件:** [ticket-server/application.properties](file:///d:/workspace/zr/qditp/ticket-server/src/main/resources/application.properties)

```properties
ticket.default-channel=01
ticket.default-code-status=03
ticket.default-gate-status=00
ticket.default-last-txn-station=0000
```

**缺失配置：** 没有 `ticket.default-gate-in-station` 配置项。

**4. 数据库字段定义**

**文件:** [ticket-server-schema.sql](file:///d:/workspace/zr/qditp/ticket-server/src/main/resources/sql/ticket-server-schema.sql)

```sql
GATE_IN_STATION   VARCHAR2(4),  -- 进站车站
```

## 问题根因

| 问题 | 说明 |
|------|------|
| 变量复用错误 | `GATE_IN_STATION` 被赋值为 `defaultGateStatus`（进出站状态），而非车站编码 |
| 缺少独立配置 | 配置文件中没有 `ticket.default-gate-in-station` 配置项 |
| 级联影响 | `gateInStation="00"` 导致 fep-app-server 中 `lastStationCode` 为 `"0000"` 而非 `"FFFF"` |

## 拟议变更

### 变更 1: 修改 ticket-server 初始化逻辑，为 GATE_IN_STATION 使用独立默认值

**文件:** [TicketRideStatusServiceImpl.java](file:///d:/workspace/zr/qditp/ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java)

**变更内容:**
```java
// 新增配置注入
@Value("${ticket.default-gate-in-station:FFFF}")
private String defaultGateInStation;

// 修改初始化逻辑
// 修改前
qrCodeStatus.setGateInStation(defaultGateStatus);

// 修改后
qrCodeStatus.setGateInStation(defaultGateInStation);
```

**原因:** `GATE_IN_STATION` 是进站车站编码，应使用车站默认值 `"FFFF"`，而非进出站状态 `"00"`。

### 变更 2: 新增 ticket-server 配置项

**文件:** [ticket-server/src/main/resources/application.properties](file:///d:/workspace/zr/qditp/ticket-server/src/main/resources/application.properties)

**变更内容:**
```properties
# 新增配置
ticket.default-gate-in-station=FFFF
```

**原因:** 为 `GATE_IN_STATION` 提供独立的配置项，便于后续调整。

### 变更 3: 同步修改 ticket-server 版本号

**文件:** [ticket-server/pom.xml](file:///d:/workspace/zr/qditp/ticket-server/pom.xml)

**变更内容:** 版本号升级（当前 `1.4` → `1.5`）。

## 假设与决策

| 决策 | 说明 |
|------|------|
| GATE_IN_STATION 默认值为 FFFF | 与 lastTxnStation 默认值保持一致，表示"无车站信息" |
| 新增独立配置项 | 不修改现有 `ticket.default-gate-status` 的语义，避免影响 GATE_STATUS |
| 不修改 fep-app-server | fep-app-server 的逻辑是正确的，问题根源在 ticket-server 的初始化值 |

## 验证步骤

1. 修改后编译 ticket-server
2. 调用 IF8A-01 请求开户接口
3. 查询数据库 QRCODE_STATUS 表，确认新记录的 `GATE_IN_STATION=FFFF`
4. 调用 IF8A-03 请求行业数据接口，确认返回的 `lastStationCode` 为 `FFFF`

## 影响范围

| 服务 | 影响 | 版本升级 |
|------|------|----------|
| ticket-server | TicketRideStatusServiceImpl.java 增加 defaultGateInStation 配置，修改初始化逻辑；application.properties 新增配置 | 1.4 → 1.5 |
| fep-app-server | 无影响 | 不升级 |

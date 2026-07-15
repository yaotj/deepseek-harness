# CODE_STATUS 初始化值修复计划

## 摘要

用户反馈入库的 `CODE_STATUS` 为 `00` 而非期望的 `03`。经排查，问题根因在 [ticket-server/application.properties](file:///d:/workspace/zr/qditp/ticket-server/src/main/resources/application.properties#L14) 中 `ticket.default-code-status=00` 覆盖了 Java 代码中的默认值 `03`。

## 当前状态分析

### 问题根因

**文件:** [ticket-server/src/main/resources/application.properties](file:///d:/workspace/zr/qditp/ticket-server/src/main/resources/application.properties#L14)

```properties
ticket.default-code-status=00
```

该配置将 `CODE_STATUS` 的默认值设为 `00`，覆盖了 [TicketRideStatusServiceImpl.java](file:///d:/workspace/zr/qditp/ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java#L28) 中的 `@Value("${ticket.default-code-status:03}")` 默认值 `03`。

### 代码逻辑

**文件:** [TicketRideStatusServiceImpl.java](file:///d:/workspace/zr/qditp/ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java#L58-L60)

```java
@Value("${ticket.default-code-status:03}")
private String defaultCodeStatus;

// 在 registerRideStatus 方法中:
qrCodeStatus.setCodeStatus(defaultCodeStatus);  // 实际值为 "00"
```

### 其他相关配置

同一配置文件中还有其他默认值可能需要同步检查：
- `ticket.default-last-txn-station=0000`（用户之前要求改为 `FFFF`）

## 拟议变更

### 变更 1: 修改 ticket-server 配置

**文件:** [ticket-server/src/main/resources/application.properties](file:///d:/workspace/zr/qditp/ticket-server/src/main/resources/application.properties#L14)

**变更内容:**
```properties
# 修改前
ticket.default-code-status=00

# 修改后
ticket.default-code-status=03
```

**原因:** 将 `CODE_STATUS` 的初始化默认值从 `00` 改为 `03`，与业务要求的初始化状态一致。

### 变更 2: 同步修改 ticket-server 版本号

**文件:** [ticket-server/pom.xml](file:///d:/workspace/zr/qditp/ticket-server/pom.xml)

**变更内容:** 版本号升级（当前版本需确认）。

## 假设与决策

| 决策 | 说明 |
|------|------|
| 只修改配置文件 | Java 代码中的默认值已经是 `03`，无需修改 |
| 同步检查 last-txn-station | `ticket.default-last-txn-station=0000` 可能也需要改为 `FFFF`，但需用户确认 |

## 验证步骤

1. 修改配置后重新部署 ticket-server
2. 调用 IF8A-01 请求开户接口
3. 查询数据库 QRCODE_STATUS 表，确认新记录的 `CODE_STATUS=03`
4. 调用 IF8A-03 请求行业数据接口，确认返回的 `qrStatusHex` 为 `03`

## 影响范围

| 服务 | 影响 | 版本升级 |
|------|------|----------|
| ticket-server | application.properties 配置变更 | 需升级 |
| fep-app-server | 无影响 | 不升级 |

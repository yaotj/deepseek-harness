# Plan: 修改推送行业数据时 TXN_SEQ 的计算逻辑

## Summary

修改 `TicketRideStatusServiceImpl.buildNextStatus()` 中 `TXN_SEQ` 的赋值逻辑，使其符合进出站业务语义：
- 进站（trxType=01）：TXN_SEQ = 上一个行程的 TXN_SEQ + 1
- 出站（trxType=02/03）：TXN_SEQ = 保持进站时的值（不覆盖）

## Current State Analysis

当前 `buildNextStatus()` 中：
```java
nextStatus.setTxnSeq(request.getTicketTransSeq()); // 直接用闸机当前交易的 seq
```

问题：
- 进站时：用进站 seq（碰巧正确，但语义上应是「上一完整行程 + 1」）
- 出站时：用出站 seq（错误，应保持进站 seq）

## Proposed Changes

### 1. 修改 `buildNextStatus()` 方法

**文件**：`ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java`

**变更**：
- 将 `nextStatus.setTxnSeq(request.getTicketTransSeq())` 改为基于 `currentStatus` 计算
- 进站：`incrementTxnSeq(currentStatus.getTxnSeq())`
- 出站：`currentStatus.getTxnSeq()`（保持原值）

### 2. 新增 `incrementTxnSeq()` 辅助方法

```java
private String incrementTxnSeq(String txnSeq) {
    if (!StringUtils.hasText(txnSeq)) {
        return "1";
    }
    try {
        return new BigInteger(txnSeq.trim()).add(BigInteger.ONE).toString();
    } catch (NumberFormatException e) {
        log.warn("交易序列号不是数字，使用1作为下一序列号, txnSeq={}", txnSeq);
        return "1";
    }
}
```

## Assumptions & Decisions

1. **QRCODE_STATUS.TXN_SEQ 保持不变**：字段本身表示「当前行程的 txnSeq」，推送行业数据时直接读取即可。
2. **出站保持进站值**：出站时不重新计算，直接保持进站时设置的值。
3. **不影响其他逻辑**：`requestExcessFare`、`pushAlipayTripData` 等不受影响。

## Verification Steps

1. 编译通过：`mvn compile -pl ticket-server`
2. 日志验证：在 `buildNextStatus` 中打印计算后的 `txnSeq`
3. 业务验证：
   - 进站：检查 QRCODE_STATUS.TXN_SEQ = 上一行程 seq + 1
   - 出站：检查 QRCODE_STATUS.TXN_SEQ = 进站时的 seq（不变）

## Files Changed

| File | Change Type | Description |
|------|-------------|-------------|
| `ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java` | Modify | 修改 `buildNextStatus()` 中 txnSeq 赋值逻辑，新增 `incrementTxnSeq()` 方法 |

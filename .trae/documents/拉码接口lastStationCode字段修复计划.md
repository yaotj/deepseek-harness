# 拉码接口 lastStationCode 字段修复计划

## 摘要

用户反馈拉码接口（IF8A-03）返回的 `cardData` 中，第三个字段 `lastStationCode` 老报文为 `0000`，新报文应为 `FFFF`。经代码分析，当前 [buildUnsignedIndustryData](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L168) 中 `lastStationCode` 的默认值已经是 `"FFFF"`，但 `normalizeHex` 的截取逻辑可能导致当 `qrStatus.getLastTxnStation()` 或 `qrStatus.getGateInStation()` 返回 `"0000"` 时，直接透传 `"0000"` 而非回退到默认值 `"FFFF"`。

## 当前状态分析

### 报文结构对比

```
老报文: 000005CC 05 08333144 0E0831B4 369A0418 06110934 29190441 0000002A 01 03 01
        用户ID   状态 站点码    处理时间  时间戳    卡号      票类型    序列号  渠道 签渠
        
新报文: 0007FA71 00 00000031 B40A8831 B4774904 18061109 74842504 41000000 01 03 01
        用户ID   状态 站点码    处理时间  时间戳    卡号      票类型    序列号  渠道 签渠
```

用户指出第三个字段（站点码）应从 `0000` 改为 `FFFF`。

### 当前代码逻辑

**文件:** [IndustryDataServiceImpl.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L168)

```java
String lastStationCode = normalizeHex(
    firstNonBlank(qrStatus.getLastTxnStation(), qrStatus.getGateInStation()), 
    4, 
    "FFFF"
);
```

**`firstNonBlank` 方法**（[IndustryDataServiceImpl.java:284-293](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L284-L293)）：
```java
private String firstNonBlank(String first, String second) {
    if (StringUtils.hasText(first)) {
        return first.trim();
    }
    if (StringUtils.hasText(second)) {
        return second.trim();
    }
    return null;  // 两者都为空时返回 null，此时 normalizeHex 会使用默认值 "FFFF"
}
```

**`normalizeHex` 方法**（[IndustryDataServiceImpl.java:270-282](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L270-L282)）：
```java
private String normalizeHex(String value, int length, String defaultValue) {
    String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : defaultValue;
    // ... 长度处理
}
```

### 问题分析

当 `qrStatus.getLastTxnStation()` 返回 `"0000"` 时：
1. `firstNonBlank("0000", ...)` 返回 `"0000"`（因为 `"0000"` 不是空白字符串）
2. `normalizeHex("0000", 4, "FFFF")` 返回 `"0000"`（因为值非空，直接使用）

但用户期望的是：当站点码为 `"0000"` 时，应视为"无站点信息"，回退到 `"FFFF"`。

## 拟议变更

### 变更 1: 修改 lastStationCode 生成逻辑，将 "0000" 视为空值

**文件:** [IndustryDataServiceImpl.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L168)

**变更内容:**
```java
// 修改前
String lastStationCode = normalizeHex(firstNonBlank(qrStatus.getLastTxnStation(), qrStatus.getGateInStation()), 4, "FFFF");

// 修改后
String lastStationCode = normalizeHex(firstNonBlankExcludeZero(qrStatus.getLastTxnStation(), qrStatus.getGateInStation()), 4, "FFFF");
```

**新增方法:**
```java
private String firstNonBlankExcludeZero(String first, String second) {
    if (StringUtils.hasText(first) && !isAllZeros(first)) {
        return first.trim();
    }
    if (StringUtils.hasText(second) && !isAllZeros(second)) {
        return second.trim();
    }
    return null;
}
```

**原因:** 当 `lastTxnStation` 或 `gateInStation` 为 `"0000"`（或全0）时，视为无效值，回退到默认值 `"FFFF"`。

## 假设与决策

| 决策 | 说明 |
|------|------|
| 全0视为空值 | `"0000"`、`"00"`、`"00000000"` 等全0字符串视为无效站点码，回退到 `"FFFF"` |
| 复用现有 isAllZeros 方法 | 已有 `isAllZeros` 方法，直接复用 |
| 只修改 lastStationCode | 其他字段（如 qrHandleDate）的全0处理已有兼容逻辑，不改动 |

## 验证步骤

1. 修改后编译 fep-app-server
2. 模拟 `qrStatus.getLastTxnStation()="0000"` 且 `qrStatus.getGateInStation()=null` 的场景，验证生成的 `lastStationCode` 为 `"FFFF"`
3. 模拟正常场景（站点码非全0），验证功能不受影响

## 影响范围

| 服务 | 影响 | 版本升级 |
|------|------|----------|
| fep-app-server | IndustryDataServiceImpl.java 增加 firstNonBlankExcludeZero 方法，修改 lastStationCode 生成逻辑 | 1.7 -> 1.8 |
| ticket-server | 无影响 | 不升级 |

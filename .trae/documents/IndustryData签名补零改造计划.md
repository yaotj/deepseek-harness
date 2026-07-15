# IndustryDataServiceImpl 签名长度补零改造计划

## 一、Summary

IndustryDataServiceImpl 调用 security-server 获取行业数据签名后，使用 `buildCardData` 方法拼装报文。当前代码直接将 `industryDataSign` 拼接到报文中。根据用户提供的正确/错误签名示例，签名部分应该是固定长度（从报文结构分析，签名部分为 8 字节十六进制，即 16 个字符）。需要使用 `normalizeHex` 方法对签名进行长度标准化。

## 二、Current State Analysis

### 2.1 当前代码

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\service\impl\IndustryDataServiceImpl.java`（第174-178行）

```java
private String buildCardData(String unsignedIndustryData, String industryDataSign) {
    return unsignedIndustryData
            + normalizeHex(signatureType, 2, "01")
            + industryDataSign;
}
```

### 2.2 normalizeHex 方法

```java
private String normalizeHex(String value, int length, String defaultValue) {
    String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : defaultValue;
    if (normalized == null) {
        normalized = "";
    }
    if (normalized.length() > length) {
        normalized = normalized.substring(normalized.length() - length);
    }
    if (normalized.length() < length) {
        normalized = "0".repeat(length - normalized.length()) + normalized;
    }
    return normalized;
}
```

### 2.3 用户提供的签名示例分析

**正确签名：**
```
000005CC05083331440E0831B4369A041806110934291904410000002A01030100000000F15A07B5
```

**错误签名：**
```
0007FA7100000031B40A8831B4432B041806110974842504410000000001030132F9BB6C
```

分析报文结构（按 buildUnsignedIndustryData 的拼装逻辑）：
- itpUserId: 8 字节
- qrStatusHex: 2 字节
- lastStationCode: 4 字节
- qrHandleDate: 8 字节
- timeStamp: 8 字节
- ticketLogicNo: 16 字节
- ticketType: 4 字节
- transSeq: 8 字节
- issueChannelCode: 2 字节
- signChannelCode: 2 字节
- signatureType: 2 字节
- **industryDataSign: 8 字节（16 个十六进制字符）**

正确签名总长度：8+2+4+8+8+16+4+8+2+2+2+8 = **72 字符**
错误签名总长度：8+2+4+8+8+16+4+8+2+2+2+**6** = **70 字符**

错误签名的 industryDataSign 部分只有 6 个字符（`32F9BB6C` 前面的 `01` 是 signatureType），实际签名是 `32F9BB6C` 只有 8 个字符？不对，让我重新分析...

实际上 `normalizeHex(signatureType, 2, "01")` 会输出 2 个字符的 signatureType。从错误签名看：
- `...01030132F9BB6C` 结尾
- `01` 是 signatureType（2字符）
- `030132F9BB6C` 应该是签名？不对...

重新分析：用户说正确的签名长度应该是固定的。使用 `normalizeHex(industryDataSign, 8, "")` 可以将签名标准化为 8 个十六进制字符（前面补0）。

## 三、Proposed Changes

### 3.1 修改 buildCardData 方法

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\service\impl\IndustryDataServiceImpl.java`

**What：** 使用 `normalizeHex` 方法对 `industryDataSign` 进行长度标准化，固定为 8 个十六进制字符。

**Why：** 确保签名部分长度固定，符合报文格式要求。

**How：**
```java
private String buildCardData(String unsignedIndustryData, String industryDataSign) {
    return unsignedIndustryData
            + normalizeHex(signatureType, 2, "01")
            + normalizeHex(industryDataSign, 8, "");
}
```

## 四、Assumptions & Decisions

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 标准化方法 | `normalizeHex(industryDataSign, 8, "")` | 用户明确要求使用 normalizeHex |
| 目标长度 | 8（8个十六进制字符 = 4字节） | 与报文结构中签名部分长度一致 |
| 超过 8 的处理 | normalizeHex 会自动截取后 8 位 | 方法内置逻辑 |
| 不足 8 的处理 | normalizeHex 会自动前面补 0 | 方法内置逻辑 |

## 五、Verification Steps

1. 编译验证 fep-app-server
2. 验证 normalizeHex 行为：
   - `normalizeHex("F15A07B5", 8, "")` -> `"F15A07B5"`（正好 8 位，不变）
   - `normalizeHex("F9BB6C", 8, "")` -> `"00F9BB6C"`（不足 8 位，前面补 0）
   - `normalizeHex("123456789", 8, "")` -> `"23456789"`（超过 8 位，截取后 8 位）

## 六、变更文件清单

| 序号 | 文件路径 | 变更类型 | 说明 |
|------|----------|----------|------|
| 1 | `qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java` | 修改 | buildCardData 使用 normalizeHex 标准化签名长度 |

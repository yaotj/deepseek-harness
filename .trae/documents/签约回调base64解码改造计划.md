# 签约回调接口 Base64 解码改造计划

## 一、Summary

支付平台的签约结果回调（`/ci/app/receiveSignResult`）中，`bizData` 字段使用了 Base64 编码。当前代码直接将 `bizData` 作为 JSON 字符串解析，导致解析失败。需要修改 fep-app-server 的回调处理逻辑，先对 `bizData` 进行 Base64 解码，再解析为 JSON。

## 二、Current State Analysis

### 2.1 当前回调处理代码

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\controller\FepAppController.java`（第150-162行）

```java
@PostMapping("/receiveSignResult")
public PaySignCallbackResult receiveSignResult(@RequestBody PayCenterCommonRequest request) {
    if (request == null || request.getBizData() == null) {
        PaySignCallbackResult result = new PaySignCallbackResult();
        result.setRetCode("8001");
        result.setRetMsg("无效的参数");
        return result;
    }

    ReceiveSignResultReqDTO bizData = JSON.parseObject(request.getBizData(), ReceiveSignResultReqDTO.class);
    log.info("IPD02 签约结果通知,支付中心公共请求参数：{}", JSON.toJSONString(request));
    return appService.receiveSignResult(bizData);
}
```

### 2.2 问题分析

支付平台回调报文示例：
```json
{
  "merchantNo": "JOPJ490HLK9Z",
  "apiVersion": "1.0",
  "signType": "RSA2",
  "charset": "UTF-8",
  "bizData": "eyJwYXlBZ3JlZW1lbnRObyI6IjIwMjY1NDAzMzAwODk0NjcyNDQzIiwicGF5VXNlcklkIjoiMjA4ODQzMjMxOTI3OTQzNyIsInBheW1lbnRWZW5kb3IiOiIwMyIsInJlcXVlc3RTaWduU2VxIjoiMDA1MjI4NjUwMTUyMjQ3MiIsInNpZ25UaW1lIjoiMjAyNjA2MDMxNzE5NTMiLCJzdGF0dXMiOiJTVUNDRVNTIn0=",
  "sign": "PcRoeECum2PMFOtRfuJo2gb+jyQxCDhqDtGk9SHphMEnqDoWcrd9ZhJuL90f/H2S4HZS41mqomgu47Bsjmb6YEuRt8p8fcybVzzkLJhSJmaBKGz8K0bW9lQobi3dTQEP6257iKJXCFUARKu+0eGZfJqEqESCQzgYWny7ElVarcbov8pUvN7tGHEoeaUHT2gfcRz8Sj39mA6xYfQ+2klIvB1014qsKtm1xxOLJRVtTjQey1lr2MAMhyOrzy23tEVpR7w+m8UD5FcZ7NXp8LPTZnRFE0/Gk+m4yACZ/OmR9XZUciEYEEBAxoYl8kEsl8NPEUenpfop9KDqQwPfJJnsUA=="
}
```

`bizData` 是 Base64 编码的 JSON 字符串，解码后为：
```json
{
  "payAgreementNo": "20265403300894672443",
  "payUserId": "2088432319279437",
  "paymentVendor": "03",
  "requestSignSeq": "0052286501522472",
  "signTime": "20260603171953",
  "status": "SUCCESS"
}
```

当前代码 `JSON.parseObject(request.getBizData(), ReceiveSignResultReqDTO.class)` 会解析失败，因为 `bizData` 是 Base64 字符串而非 JSON。

## 三、Proposed Changes

### 3.1 修改 receiveSignResult 方法

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\controller\FepAppController.java`

**What：** 在 `JSON.parseObject` 之前，先对 `request.getBizData()` 进行 Base64 解码。

**Why：** 支付平台的 `bizData` 是 Base64 编码的，需要先解码才能解析为 JSON。

**How：**
```java
@PostMapping("/receiveSignResult")
public PaySignCallbackResult receiveSignResult(@RequestBody PayCenterCommonRequest request) {
    if (request == null || request.getBizData() == null) {
        PaySignCallbackResult result = new PaySignCallbackResult();
        result.setRetCode("8001");
        result.setRetMsg("无效的参数");
        return result;
    }

    // Base64 解码 bizData
    String bizDataJson;
    try {
        bizDataJson = new String(Base64.getDecoder().decode(request.getBizData()), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
        // 如果解码失败，尝试直接作为 JSON 解析（兼容非 Base64 格式）
        bizDataJson = request.getBizData();
    }

    ReceiveSignResultReqDTO bizData = JSON.parseObject(bizDataJson, ReceiveSignResultReqDTO.class);
    log.info("IPD02 签约结果通知,支付中心公共请求参数：{}", JSON.toJSONString(request));
    return appService.receiveSignResult(bizData);
}
```

### 3.2 添加 import

需要添加以下 import：
```java
import java.util.Base64;
import java.nio.charset.StandardCharsets;
```

## 四、Assumptions & Decisions

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 解码失败处理 | 兼容非 Base64 格式 | 防止影响其他回调方 |
| 字符集 | UTF-8 | 与支付平台约定一致 |
| 影响范围 | 仅 receiveSignResult 接口 | 其他接口不涉及 Base64 |

## 五、Verification Steps

1. 编译验证 fep-app-server
2. 使用用户提供的回调报文测试：
   ```bash
   curl -X POST http://<host>:<port>/ci/app/receiveSignResult \
     -H "Content-Type: application/json" \
     -d '{
       "merchantNo": "JOPJ490HLK9Z",
       "apiVersion": "1.0",
       "signType": "RSA2",
       "charset": "UTF-8",
       "bizData": "eyJwYXlBZ3JlZW1lbnRObyI6IjIwMjY1NDAzMzAwODk0NjcyNDQzIiwicGF5VXNlcklkIjoiMjA4ODQzMjMxOTI3OTQzNyIsInBheW1lbnRWZW5kb3IiOiIwMyIsInJlcXVlc3RTaWduU2VxIjoiMDA1MjI4NjUwMTUyMjQ3MiIsInNpZ25UaW1lIjoiMjAyNjA2MDMxNzE5NTMiLCJzdGF0dXMiOiJTVUNDRVNTIn0=",
       "sign": "..."
     }'
   ```
3. 验证 bizData 被正确解码并解析为 ReceiveSignResultReqDTO

## 六、变更文件清单

| 序号 | 文件路径 | 变更类型 | 说明 |
|------|----------|----------|------|
| 1 | `qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppController.java` | 修改 | receiveSignResult 添加 Base64 解码 |

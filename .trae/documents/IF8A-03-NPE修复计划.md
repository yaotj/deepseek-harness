# IF8A-03 请求行业数据 NPE 修复计划

## 摘要

修复 `IndustryDataServiceImpl.requestIndustryData` 方法中的空指针异常（NPE）。当 `ticketClient.queryQrCodeStatus` 返回 `null` 时，第 85 行直接调用 `qrStatus.getRetCode()` 导致 NPE，异常被外层的 `catch` 捕获后返回 "系统内部错误"（9999），掩盖了真实的调用失败原因。

## 当前状态分析

### 问题根因

**文件:** [IndustryDataServiceImpl.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L84-L89)

```java
QueryStatusRespDTO qrStatus = queryTicketStatus(request);
if (!"0000".equals(qrStatus.getRetCode())) {  // ← 第85行 NPE，qrStatus 为 null
    response.setRetCode(qrStatus.getRetCode());
    response.setRetMsg(qrStatus.getRetMsg());
    return response;
}
```

**调用链:**
```
IndustryDataServiceImpl.queryTicketStatus()
    └── TicketClient.queryQrCodeStatus()
        └── ProxyWebClient.postJsonAndGetResponse()
            └── handleResponse() 异常时返回 null
```

**ProxyWebClient.handleResponse 方法**（[ProxyWebClient.java:115-125](file:///d:/workspace/zr/micro/web/src/main/java/com/chinasofti/huateng/micro/web/client/ProxyWebClient.java#L115-L125)）：
```java
private <T> T handleResponse(Mono<ResponseEntity<T>> result) {
    ResponseEntity<T> res = null;
    try {
        res = result.block();
        logResponse(res.getStatusCode().value(), res.toString());
    } catch (Exception e) {
        log.error("{}", e.getMessage(), e);
        return null;  // ← 异常时返回 null
    }
    return res.getBody();
}
```

**日志证据:**
```
IF8A-03查询二维码状态结果, request={...}, response=null
java.lang.NullPointerException: Cannot invoke "...getRetCode()" because "qrStatus" is null
```

`response=null` 说明 ticket-server 调用失败（连接异常、超时、或返回非 200 状态码），`handleResponse` 捕获异常后返回 `null`。

### 同样存在 NPE 风险的位置

| 位置 | 代码 | 风险 |
|------|------|------|
| 第 70 行 | `userInfo.getRetCode()` | `queryUserInfo` 内部调用了 `accountClient.queryUserInfo`，同样可能返回 null，但当前被外层 try-catch 包裹 |
| 第 95-100 行 | `requestSecuritySign` | `signResp` 已做 null 判断（`signResp == null ? "9999"`），处理正确 |

## 拟议变更

### 变更 1: 修复 queryTicketStatus 返回 null 的 NPE

**文件:** [IndustryDataServiceImpl.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L84-L89)

**变更内容:**
```java
// 修改前
QueryStatusRespDTO qrStatus = queryTicketStatus(request);
if (!"0000".equals(qrStatus.getRetCode())) {
    response.setRetCode(qrStatus.getRetCode());
    response.setRetMsg(qrStatus.getRetMsg());
    return response;
}

// 修改后
QueryStatusRespDTO qrStatus = queryTicketStatus(request);
if (qrStatus == null) {
    response.setRetCode("9999");
    response.setRetMsg("ticket-server调用失败，返回为空");
    log.warn("IF8A-03查询二维码状态返回null, request={}", JSON.toJSONString(request));
    return response;
}
if (!"0000".equals(qrStatus.getRetCode())) {
    response.setRetCode(qrStatus.getRetCode());
    response.setRetMsg(qrStatus.getRetMsg());
    return response;
}
```

**原因:** 在调用 `qrStatus.getRetCode()` 之前增加 null 检查，避免 NPE，并返回明确的错误信息。

### 变更 2: 修复 queryUserInfo 返回 null 的潜在 NPE

**文件:** [IndustryDataServiceImpl.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java#L69-L74)

**变更内容:**
```java
// 修改前
QueryUserInfoResult userInfo = queryUserInfo(request);
if (!"0000".equals(userInfo.getRetCode())) {
    response.setRetCode(userInfo.getRetCode());
    response.setRetMsg(userInfo.getRetMsg());
    return response;
}

// 修改后
QueryUserInfoResult userInfo = queryUserInfo(request);
if (userInfo == null) {
    response.setRetCode("9999");
    response.setRetMsg("account-server调用失败，返回为空");
    log.warn("IF8A-03查询用户信息返回null, request={}", JSON.toJSONString(request));
    return response;
}
if (!"0000".equals(userInfo.getRetCode())) {
    response.setRetCode(userInfo.getRetCode());
    response.setRetMsg(userInfo.getRetMsg());
    return response;
}
```

**原因:** `queryUserInfo` 同样通过 RPC 客户端调用，存在返回 null 的风险，统一做防御性处理。

## 假设与决策

| 决策 | 说明 |
|------|------|
| 返回 9999 错误码 | 与现有异常处理保持一致，外层 catch 也是返回 9999 |
| 添加 warn 日志 | 便于排查 ticket-server/account-server 调用失败问题 |
| 不修改 ProxyWebClient | handleResponse 返回 null 的设计在其他地方也有使用，保持现状，在调用方做 null 检查 |
| 不修改 ticket-server | 当前问题是 fep-app-server 对 null 返回值处理不当，ticket-server 本身可能正常（只是网络/连接问题） |

## 验证步骤

1. 修改后编译 fep-app-server，确保无编译错误
2. 模拟 ticket-server 不可达的场景，调用 IF8A-03 接口，验证返回 `{"retCode":"9999","retMsg":"ticket-server调用失败，返回为空"}` 而非 NPE
3. 模拟 account-server 不可达的场景，验证返回相应错误信息
4. 正常场景下验证功能不受影响

## 影响范围

| 服务 | 影响 | 版本升级 |
|------|------|----------|
| fep-app-server | IndustryDataServiceImpl.java 增加 null 防御 | 1.6 -> 1.7 |
| ticket-server | 无影响 | 不升级 |
| account-server | 无影响 | 不升级 |
| rpc/micro 模块 | 无影响 | 不升级 |

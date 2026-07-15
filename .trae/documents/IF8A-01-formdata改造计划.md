# IF8A-01 请求开户接口 FormData 改造计划

## 一、Summary

将 IF8A-01 请求开户接口（`/ci/app/requestApplication`）从 JSON 请求格式改造为 `application/x-www-form-urlencoded` 格式。

**约束条件：只修改 fep-app-server 的前端适配层，其他服务（account-server、rpc 模块等）保持现有 JSON 接口不变。**

这意味着：
- fep-app-server **接收** APP 的 formdata 请求
- fep-app-server **内部**将 formdata 转换为 JSON DTO，通过现有 RPC JSON 方式调用 account-server
- account-server、rpc 模块、ticket-server **完全不做任何修改**

## 二、Current State Analysis

### 2.1 当前调用链

```
APP 端
  ↓ POST /ci/app/requestApplication (Content-Type: application/json)
fep-app-server: FepAppController (@RequestBody CommonRequest<RequestApplicationReqDTO>)
  ↓ 提取 bizData → AppService.userRegister(RequestApplicationReqDTO)
AppServiceImpl
  ↓ accountClient.requestApplication(RequestApplicationReqDTO)
rpc: AccountClient (postJsonAndGetResponse)
  ↓ POST /requestApplication (Content-Type: application/json)
account-server: RequestApplicationController (@RequestBody RequestApplicationReqDTO)
  ↓ accountApplicationService.requestApplication(RequestApplicationReqDTO)
AccountApplicationServiceImpl (业务处理)
```

### 2.2 当前代码状态

| 层级 | 文件 | 当前方式 |
|------|------|----------|
| FepAppController | [FepAppController.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppController.java) | `@RequestBody CommonRequest<RequestApplicationReqDTO>` 接收 JSON |
| CommonRequest | [CommonRequest.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/model/CommonRequest.java) | 泛型 `bizData` 字段，JSON 反序列化 |
| AppServiceImpl | [AppServiceImpl.java](file:///d:/workspace/zr/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/AppServiceImpl.java) | 透传 DTO 给 AccountClient |
| AccountClient | [AccountClient.java](file:///d:/workspace/zr/qditp/rpc/src/main/java/com/chinasofti/huateng/rpc/account/AccountClient.java) | `postJsonAndGetResponse("/requestApplication", request)` |
| RequestApplicationController | [RequestApplicationController.java](file:///d:/workspace/zr/qditp/account-server/src/main/java/com/chinasofti/huateng/account/controller/ci/app/RequestApplicationController.java) | `@RequestBody RequestApplicationReqDTO` 接收 JSON |

### 2.3 目标请求格式

APP 发送的 formdata 格式：

```
POST /ci/app/requestApplication
Content-Type: application/x-www-form-urlencoded

bizData={"thirdUserId":"00000018","companionFlag":"Y","extend2":"","channel":"","reqContractNo":"","cardType":"02","extend1":"","thirdPayId":"","msisdn":"13047412310","userName":"","userId":"","ticketCard":"01"}
&charset=UTF-8
&format=json
&providerId=01
&signType=00
&timestamp=20260519111830
&sign=YL5DUw5OO5Dyjm8rZxzh0nyQ9Yz1...
```

## 三、Proposed Changes

### 3.1 新增 CommonFormRequest 类（formdata 专用公共请求包装）

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\model\CommonFormRequest.java`

**What：** 新增一个专门用于接收 formdata 的公共请求类，bizData 字段改为 String 类型（formdata 中 bizData 是 JSON 字符串）。

**Why：** 原 `CommonRequest<T>` 的 `bizData` 是泛型，Spring 无法直接将 formdata 中的字符串值绑定到泛型字段。需要一个新的类来接收 formdata 参数。

**How：**
```java
package com.chinasofti.huateng.fep.app.model;

public class CommonFormRequest {
    private String providerId;
    private String charset;
    private String format;
    private String timestamp;
    private String deviceId;
    private String signType;
    private String sign;
    private String bizData;  // String 类型，接收 JSON 字符串

    // getter/setter
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getCharset() { return charset; }
    public void setCharset(String charset) { this.charset = charset; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
    public String getBizData() { return bizData; }
    public void setBizData(String bizData) { this.bizData = bizData; }
}
```

### 3.2 修改 FepAppController.requestApplication 方法

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\controller\FepAppController.java`

**What：** 将 `@RequestBody` 改为 `@ModelAttribute`，使用 `CommonFormRequest` 接收 formdata，然后将 `bizData` JSON 字符串反序列化为 `RequestApplicationReqDTO`，后续调用链路完全不变。

**Why：** `@ModelAttribute` 支持将 formdata 字段自动绑定到对象属性。`bizData` 在 formdata 中是 JSON 字符串，需要手动反序列化为 DTO 后传给 Service 层。Service 层、RPC 层、account-server 均保持现有 JSON 调用方式不变。

**How：**
```java
@PostMapping("/requestApplication")
public RequestApplicationResult requestApplication(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-01 请求开户，请求参数：{}", JSON.toJSONString(request));
    // 将 bizData JSON 字符串反序列化为 RequestApplicationReqDTO
    RequestApplicationReqDTO bizData = JSON.parseObject(request.getBizData(), RequestApplicationReqDTO.class);
    RequestApplicationResult result = appService.userRegister(bizData);
    return result;
}
```

## 四、Assumptions & Decisions

### 4.1 决策说明

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 改造范围 | 仅 fep-app-server | 用户明确要求只修改前端适配层 |
| fep-app 接收 formdata 方式 | `@ModelAttribute` + 新增 CommonFormRequest | Spring 原生支持，代码简洁 |
| bizData 处理方式 | Controller 层手动 JSON.parseObject | formdata 中 bizData 是 JSON 字符串，需要反序列化 |
| RPC 传输方式 | **保持不变**（JSON） | account-server 无需修改 |
| account-server 接收方式 | **保持不变**（@RequestBody JSON） | 用户要求不修改其他服务 |

### 4.2 假设条件

1. **ProxyWebClient、AccountClient、account-server 均不做任何修改**：按用户要求，只改 fep-app-server 的 Controller 层。
2. **RequestApplicationReqDTO 字段全为 String**：经确认，[RequestApplicationReqDTO.java](file:///d:/workspace/zr/qditp/model/src/main/java/com/chinasofti/huateng/model/app/RequestApplicationReqDTO.java) 所有字段都是 String，支持 JSON 反序列化。
3. **Service 层、RPC 层无需改动**：`AppServiceImpl` 和 `AccountClient` 保持现有 JSON 调用方式。

## 五、Verification Steps

### 5.1 单元测试验证

1. **fep-app formdata 接口测试**：使用 curl 发送 formdata 请求，验证能正确接收并解析
   ```bash
   curl -X POST http://127.0.0.1:9101/ci/app/requestApplication \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d 'bizData={"thirdUserId":"TEST001","cardType":"02","msisdn":"13800138000"}' \
     -d 'providerId=01' \
     -d 'charset=UTF-8' \
     -d 'format=json' \
     -d 'timestamp=20250603120000' \
     -d 'signType=00' \
     -d 'sign='
   ```

2. **验证 account-server 仍接收 JSON**：确认 account-server 的 `/requestApplication` 接口仍可通过 JSON 直接调用
   ```bash
   curl -X POST http://127.0.0.1:9098/requestApplication \
     -H "Content-Type: application/json" \
     -d '{"thirdUserId":"TEST002","cardType":"02","msisdn":"13900139000"}'
   ```

### 5.2 集成测试验证

1. 完整链路测试：APP(formdata) -> fep-app -> account-server(JSON) -> ticket-server(JSON)
2. 验证数据库记录：确认 USER_ITP_REG_INFO、USER_ITP_REG_LOG、QR_CODE_STATUS 正常写入
3. 验证日志输出：确认 fep-app 日志正常打印 formdata 参数

### 5.3 回归测试验证

1. 确认 fep-app 的其他 IF8A 接口不受影响（只改 IF8A-01）
2. 确认 account-server 的所有接口不受影响

## 六、变更文件清单

| 序号 | 文件路径 | 变更类型 | 说明 |
|------|----------|----------|------|
| 1 | `qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/model/CommonFormRequest.java` | 新增 | formdata 专用公共请求类 |
| 2 | `qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppController.java` | 修改 | IF8A-01 接口改为接收 formdata，内部仍转 JSON 调用下游 |

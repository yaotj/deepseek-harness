# fep-app-server 全部接口 FormData 改造计划

## 一、Summary

将 fep-app-server 的所有 APP 业务接口（IF8A 系列）从 JSON 请求格式统一改造为 `application/x-www-form-urlencoded` 格式。

**约束条件：只修改 fep-app-server 的前端适配层，其他服务（account-server、pay-sign-server、key-server、blacklist-server、rpc 模块等）保持现有 JSON 接口不变。**

回调接口（`receiveSignResult`、`receiveTerminationResult`）保持原有格式不变，因为它们是支付中心回调，不是 APP 主动请求。

## 二、Current State Analysis

### 2.1 需要改造的接口清单

| 接口编号 | 接口路径 | Controller | 当前方式 | 改造方式 |
|----------|----------|------------|----------|----------|
| IF8A-01 | `/ci/app/requestApplication` | FepAppController | **已改** `@ModelAttribute CommonFormRequest` | ✅ 已完成 |
| IF8A-02 | `/ci/app/requestKeyList` | FepAppController | `@RequestBody CommonRequest<RequestKeyListReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |
| IF8A-23 | `/ci/app/requestAddPayChannel` | FepAppController | `@RequestBody CommonRequest<RequestAddPayChannelReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |
| IF8A-24 | `/ci/app/requestSetDefaultPayChannel` | FepAppController | `@RequestBody CommonRequest<RequestSetDefaultPayChannelReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |
| IF8A-03 | `/ci/app/requestIndustryData` | FepAppController | `@RequestBody CommonRequest<RequestIndustryDataReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |
| IF8A-16 | `/ci/app/requestSignInfo` | FepAppController | `@RequestBody CommonRequest<RequestSignInfoReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |
| IF8A-06 | `/ci/app/requestTermination` | FepAppController | `@RequestBody CommonRequest<RequestTerminationReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |
| IF8A-73 | `/ci/app/ticket/queryBlackList` | FepAppController | `@RequestBody CommonRequest<QueryBlackListReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |
| IF8A-73 | `/app/ticket/queryBlackList` | FepAppTicketController | `@RequestBody CommonRequest<QueryBlackListReqDTO>` | 改为 `@ModelAttribute CommonFormRequest` |

### 2.2 保持不变的接口

| 接口 | 路径 | 理由 |
|------|------|------|
| IPD02 签约结果通知 | `/ci/app/receiveSignResult` | 支付中心回调，非 APP 请求 |
| 5.3 解约回调 | `/ci/app/receiveTerminationResult` | 支付中心回调，非 APP 请求 |

### 2.3 当前调用链（以 IF8A-02 为例）

```
APP 端
  ↓ POST /ci/app/requestKeyList (Content-Type: application/json)
fep-app-server: FepAppController (@RequestBody CommonRequest<RequestKeyListReqDTO>)
  ↓ 提取 bizData → AppService.requestKeyList(RequestKeyListReqDTO)
AppServiceImpl
  ↓ keyClient.requestKeyList(RequestKeyListReqDTO)
rpc: KeyClient (postJsonAndGetResponse)
  ↓ POST /requestKeyList (Content-Type: application/json)
key-server: KeyController (@RequestBody RequestKeyListReqDTO)
```

## 三、Proposed Changes

### 3.1 统一改造模式

所有 IF8A 接口采用统一的改造模式：

1. **Controller 方法签名**：`@RequestBody CommonRequest<XXXReqDTO>` → `@ModelAttribute CommonFormRequest`
2. **bizData 反序列化**：`JSON.parseObject(request.getBizData(), XXXReqDTO.class)`
3. **Service 调用**：保持不变，继续透传反序列化后的 DTO
4. **RPC 调用**：保持不变，继续以 JSON 方式调用下游服务

### 3.2 FepAppController 改造

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\controller\FepAppController.java`

#### IF8A-02 请求同步密钥

```java
@PostMapping("/requestKeyList")
public RequestKeyListResult requestKeyList(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-02 请求同步密钥,请求参数: {}", request);
    RequestKeyListReqDTO bizData = JSON.parseObject(request.getBizData(), RequestKeyListReqDTO.class);
    return appService.requestKeyList(bizData);
}
```

#### IF8A-23 请求添加支付通道

```java
@PostMapping("/requestAddPayChannel")
public RequestAddPayChannelResult requestAddPayChannel(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-23 请求添加支付通道, 请求参数: {}", request);
    RequestAddPayChannelReqDTO bizData = JSON.parseObject(request.getBizData(), RequestAddPayChannelReqDTO.class);
    return appService.requestAddPayChannel(bizData);
}
```

#### IF8A-24 请求设置默认支付通道

```java
@PostMapping("/requestSetDefaultPayChannel")
public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-24 请求设置默认支付通道, 请求参数: {}", request);
    RequestSetDefaultPayChannelReqDTO bizData = JSON.parseObject(request.getBizData(), RequestSetDefaultPayChannelReqDTO.class);
    return appService.requestSetDefaultPayChannel(bizData);
}
```

#### IF8A-03 请求行业数据

```java
@PostMapping("/requestIndustryData")
public RequestIndustryDataResult requestIndustryData(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-03 请求行业数据, 请求参数: {}", request);
    RequestIndustryDataReqDTO bizData = JSON.parseObject(request.getBizData(), RequestIndustryDataReqDTO.class);
    return appService.requestIndustryData(bizData);
}
```

#### IF8A-16 请求签约信息

```java
@PostMapping("/requestSignInfo")
public RequestSignInfoResult requestSignInfo(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-16 请求签约信息,请求参数：{}", request);
    RequestSignInfoReqDTO bizData = JSON.parseObject(request.getBizData(), RequestSignInfoReqDTO.class);
    RequestSignInfoResult result = appService.requestSignInfo(bizData);
    return result;
}
```

#### IF8A-06 请求解约

```java
@PostMapping("/requestTermination")
public RequestTerminationResult requestTermination(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-06 请求解约,请求参数: {}", request);
    RequestTerminationReqDTO bizData = JSON.parseObject(request.getBizData(), RequestTerminationReqDTO.class);
    return appService.requestTermination(bizData);
}
```

#### IF8A-73 查询黑名单（/ci/app/ticket 前缀）

```java
@PostMapping("/ticket/queryBlackList")
public QueryBlackListResult queryBlackList(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-73 查询黑名单,请求参数: {}", request);
    QueryBlackListReqDTO bizData = JSON.parseObject(request.getBizData(), QueryBlackListReqDTO.class);
    return appService.queryBlackList(bizData);
}
```

### 3.3 FepAppTicketController 改造

**文件：** `d:\workspace\zr\qditp\fep-app-server\src\main\java\com\chinasofti\huateng\fep\app\controller\FepAppTicketController.java`

#### IF8A-73 查询黑名单（/app/ticket 前缀）

```java
@PostMapping("/queryBlackList")
public QueryBlackListResult queryBlackList(@ModelAttribute CommonFormRequest request) {
    log.info("IF8A-73 查询黑名单,请求参数: {}", request);
    QueryBlackListReqDTO bizData = JSON.parseObject(request.getBizData(), QueryBlackListReqDTO.class);
    return appService.queryBlackList(bizData);
}
```

### 3.4 清理未使用的 import

改造后，`FepAppController` 中的 `CommonRequest` 类可能不再被使用（如果回调接口不使用它），需要检查并清理未使用的 import。

## 四、Assumptions & Decisions

### 4.1 决策说明

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 改造范围 | 仅 fep-app-server | 用户明确要求只修改前端适配层 |
| 统一接收类 | `CommonFormRequest`（已有） | IF8A-01 已新增，所有接口复用 |
| 回调接口 | 保持不变 | 支付中心回调，非 APP 请求 |
| RPC 传输方式 | **保持不变**（JSON） | 下游服务无需修改 |
| bizData 为空处理 | 由下游 Service 校验 | 保持与现有逻辑一致 |

### 4.2 假设条件

1. **所有下游服务的 Controller 保持 `@RequestBody` 不变**：按用户要求，account-server、pay-sign-server、key-server、blacklist-server 均不做修改。
2. **所有 bizData DTO 支持 JSON 反序列化**：所有 `XXXReqDTO` 都是 POJO，fastjson2 可以正常反序列化。
3. **CommonFormRequest 已存在**：IF8A-01 改造时已新增，本次直接复用。

## 五、Verification Steps

### 5.1 编译验证

```bash
cd d:\workspace\zr\qditp\fep-app-server && mvn compile
```

### 5.2 接口测试验证

对每个改造后的接口，使用 curl 测试 formdata 格式：

```bash
# IF8A-02 请求同步密钥
curl -X POST http://127.0.0.1:9101/ci/app/requestKeyList \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'bizData={"thirdUserId":"TEST001","cardId":"xxx"}' \
  -d 'providerId=01' -d 'charset=UTF-8' -d 'format=json' \
  -d 'timestamp=20250603120000' -d 'signType=00' -d 'sign='

# IF8A-73 查询黑名单（两个入口都要测）
curl -X POST http://127.0.0.1:9101/ci/app/ticket/queryBlackList \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'bizData={"cardId":"xxx"}' \
  -d 'providerId=01' -d 'charset=UTF-8' -d 'format=json' \
  -d 'timestamp=20250603120000' -d 'signType=00' -d 'sign='

curl -X POST http://127.0.0.1:9101/app/ticket/queryBlackList \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'bizData={"cardId":"xxx"}' \
  -d 'providerId=01' -d 'charset=UTF-8' -d 'format=json' \
  -d 'timestamp=20250603120000' -d 'signType=00' -d 'sign='
```

### 5.3 回归测试验证

1. 确认回调接口（`receiveSignResult`、`receiveTerminationResult`）不受影响
2. 确认所有下游服务接口仍可通过 JSON 直接调用

## 六、变更文件清单

| 序号 | 文件路径 | 变更类型 | 说明 |
|------|----------|----------|------|
| 1 | `qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppController.java` | 修改 | 7 个 IF8A 接口改为 `@ModelAttribute CommonFormRequest` |
| 2 | `qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppTicketController.java` | 修改 | IF8A-73 接口改为 `@ModelAttribute CommonFormRequest` |

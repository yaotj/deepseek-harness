# fep-dev-server 微服务文档

> **模块路径**: `fep-dev-server/`
> **端口**: 9104
> **职责**: 开发环境 AGM 设备接入网关，处理开发/测试环境下 AGM 设备的闸机检票通知、密钥同步、票卡状态查询和设备心跳
> **源码阅读范围**: `fep-dev-server/src/main/java/`、`fep-dev-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

fep-dev-server 是开发/测试环境的 AGM 设备前置接入网关，承担以下核心职责：

1. **闸机检票通知** - IF1A-01 接收 AGM 设备检票通知
2. **密钥同步** - IF1A-02 接收 AGM 设备密钥同步请求
3. **票卡状态查询** - IF1A-04 接收 AGM 设备票卡状态查询
4. **设备心跳** - IF1A-03 接收 AGM 设备心跳

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| FastJSON | JSON 序列化 |
| OpenFeign | RPC 服务调用 |

### 1.3 端口配置

```properties
server.port=9104
```

### 1.4 依赖下游服务

| 服务 | 调用时机 | 调用方法 |
|------|---------|---------|
| ticket-server | 检票通知、票卡状态查询 | `ticketClient.*` |

---

## 二、接口清单

### 2.1 AGM 设备接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求格式 | 说明 |
|---------|---------|---------|---------|---------|------|
| IF1A-01 | 闸机检票通知 | `/ci/agm/notiVerifyResult` | POST | FormData | `CommonFormRequest` + `NotifyVerifyResultReqDTO` |
| IF1A-02 | 密钥同步 | `/ci/agm/requestSynKeyList` | POST | FormData | `CommonFormRequest` + `RequestSynKeyListReqDTO` |
| IF1A-03 | 设备心跳 | `/ci/agm/deviceHeartbeat` | POST | FormData | `CommonFormRequest`，只确认链路可达，不解析 bizData |
| IF1A-04 | 查询票卡状态 | `/ci/agm/requestQrCodeStatus` | POST | FormData | `CommonFormRequest` + `RequestQrCodeStatusReqDTO` |

**请求格式说明**：
- 所有接口均使用 `@ModelAttribute CommonFormRequest` 接收 FormData 格式请求
- `bizData` 字段为 JSON 字符串，Controller 层通过 FastJSON 解析为具体业务 DTO
- `deviceId` 从公共请求头获取，补充到业务参数中

---

## 三、核心业务流程

### 3.1 IF1A-01 闸机检票通知流程

**业务流程**：

1. **参数校验** - 校验 `bizData` 是否为空
2. **解析业务参数** - 解析 `NotifyVerifyResultReqDTO`
3. **补充 deviceId** - 将公共请求头的 `deviceId` 补充到业务参数
4. **非成功状态处理** - 如果 `handleResultCode != "000"`，直接返回成功（只记录日志）
5. **调用 ticket-server** - 调用 `devService.notifyVerifyResult()` 处理检票业务
6. **返回结果** - 返回处理结果

**关键代码**：
```java
// FepAgmController.java:42-71
@PostMapping("/notiVerifyResult")
public NotifyVerifyResultRespDTO notifyVerifyResult(@ModelAttribute CommonFormRequest request) {
    // 1. 参数校验
    // 2. 解析 bizData
    bizData = JSON.parseObject(request.getBizData(), NotifyVerifyResultReqDTO.class);
    // 3. 补充 deviceId
    bizData.setDeviceId(request.getDeviceId());
    // 4. 非成功状态直接返回
    if (!"000".equals(bizData.getHandleResultCode())) {
        return successResponse();
    }
    // 5. 调用 ticket-server
    return devService.notifyVerifyResult(bizData);
}
```

### 3.2 IF1A-02 密钥同步流程

**业务流程**：

1. **参数校验** - 校验 `bizData` 是否为空
2. **解析业务参数** - 解析 `RequestSynKeyListReqDTO`
3. **调用 ticket-server** - 调用 `devService.requestSynKeyList()` 处理密钥同步
4. **返回结果** - 返回密钥列表

### 3.3 IF1A-04 查询票卡状态流程

**业务流程**：

1. **参数校验** - 校验 `bizData` 是否为空
2. **解析业务参数** - 解析 `RequestQrCodeStatusReqDTO`
3. **调用 ticket-server** - 调用 `devService.requestQrCodeStatus()` 查询票卡状态
4. **返回结果** - 返回票卡状态

### 3.4 IF1A-03 设备心跳流程

**业务流程**：

1. **接收心跳** - 接收设备心跳请求
2. **返回成功** - 直接返回成功响应，不解析 bizData，不调用后端服务

**关键代码**：
```java
// FepAgmController.java:141-149
@PostMapping({"/deviceHeartbeat", "/notiDeviceHeard"})
public DeviceHeartbeatRespDTO deviceHeartbeat(@ModelAttribute CommonFormRequest request) {
    DeviceHeartbeatRespDTO response = new DeviceHeartbeatRespDTO();
    response.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());
    response.setRetMsg(FepDevErrorCodeEnum.SUCCESS.getMessage());
    return response;
}
```

---

## 四、数据模型

### 4.1 CommonFormRequest

位置：`com.chinasofti.huateng.fep.dev.model.CommonFormRequest`

用于接收 AGM 设备 FormData 格式的公共请求报文。

| 字段 | 类型 | 描述 |
|------|------|------|
| `providerId` | String | 提供者 ID |
| `charset` | String | 字符集 |
| `format` | String | 报文格式 |
| `timestamp` | String | 时间戳 |
| `deviceId` | String | 设备 ID |
| `signType` | String | 签名类型 |
| `sign` | String | 签名 |
| `bizData` | String | 业务参数（JSON 字符串） |

### 4.2 业务 DTO

| DTO 类 | 路径 | 说明 |
|--------|------|------|
| `NotifyVerifyResultReqDTO` | `com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO` | 闸机检票通知请求 |
| `NotifyVerifyResultRespDTO` | `com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO` | 闸机检票通知响应 |
| `RequestSynKeyListReqDTO` | `com.chinasofti.huateng.fep.dev.model.RequestSynKeyListReqDTO` | 密钥同步请求 |
| `RequestSynKeyListRespDTO` | `com.chinasofti.huateng.fep.dev.model.RequestSynKeyListRespDTO` | 密钥同步响应 |
| `RequestQrCodeStatusReqDTO` | `com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO` | 票卡状态查询请求 |
| `RequestQrCodeStatusRespDTO` | `com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO` | 票卡状态查询响应 |
| `DeviceHeartbeatRespDTO` | `com.chinasofti.huateng.fep.dev.model.DeviceHeartbeatRespDTO` | 设备心跳响应 |
| `KeyCurVerReqDTO` | `com.chinasofti.huateng.fep.dev.model.KeyCurVerReqDTO` | 密钥版本请求 |
| `KeyCurVerRespDTO` | `com.chinasofti.huateng.fep.dev.model.KeyCurVerRespDTO` | 密钥版本响应 |

### 4.3 FepDevErrorCodeEnum

位置：`com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum`

| 错误码 | 含义 | 说明 |
|--------|------|------|
| `0000` | SUCCESS | 成功 |
| `9999` | FAIL | 失败 |
| `8001` | INVALID_PARAM | 无效参数 |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/controller/FepAgmController.java` | AGM 设备接口入口 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/service/DevService.java` | 设备服务接口 |
| `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/service/impl/DevServiceImpl.java` | 设备服务实现 |

### 5.3 模型层

| 文件路径 | 说明 |
|---------|------|
| `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/CommonFormRequest.java` | FormData 公共请求 |
| `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/*.java` | 业务 DTO |

### 5.4 资源配置

| 文件路径 | 说明 |
|---------|------|
| `fep-dev-server/src/main/resources/application.properties` | 应用配置 |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 调用方法 |
|--------|-----------|---------|---------|
| fep-dev-server | ticket-server | 检票通知、票卡状态查询 | `ticketClient.*` |

---

## 七、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 八、源码文件索引

### 8.1 Java 源文件（共 8 个）

**控制器层 (1 个)**:
1. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/controller/FepAgmController.java`

**服务层 (2 个)**:
2. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/service/DevService.java`
3. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/service/impl/DevServiceImpl.java`

**模型层 (7 个)**:
4. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/CommonFormRequest.java`
5. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/DeviceHeartbeatRespDTO.java`
6. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/KeyCurVerReqDTO.java`
7. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/KeyCurVerRespDTO.java`
8. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestQrCodeStatusReqDTO.java`
9. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestQrCodeStatusRespDTO.java`
10. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestSynKeyListReqDTO.java`
11. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestSynKeyListRespDTO.java`

**常量类 (1 个)**:
12. `fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/constant/FepDevErrorCodeEnum.java`

**启动类 (1 个)**:
13. `fep-dev-server/src/main/java/com/chinasofti/huateng/FepDevServer.java`

### 8.2 资源文件

1. `fep-dev-server/src/main/resources/application.properties`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/fep-dev-server` 模块源码。

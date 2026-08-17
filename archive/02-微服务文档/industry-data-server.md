# industry-data-server 微服务文档

> **模块路径**: `industry-data-server/`
> **端口**: 9105
> **职责**: 行业数据服务，构建并推送二维码行业数据、离线码数据，支持日票激活和订单号下发
> **源码阅读范围**: `industry-data-server/src/main/java/`、`industry-data-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

industry-data-server 是 ITP 平台的行业数据服务，承担以下核心职责：

1. **行业数据构建** - 组装二维码行业数据（平台二维码补建、支付宝出行新增）
2. **行业数据推送** - 推送至 ACC/行业服务，供闸机/终端下发到手机
3. **离线码数据** - 生成在站内/不在站内双方向离线码数据
4. **日票激活** - 日票激活请求处理
5. **订单号下发** - 下发订单号接口

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
server.port=9105
```

### 1.4 依赖下游服务

| 服务 | 调用时机 | 调用方法 |
|------|---------|---------|
| ticket-server | 获取票卡状态 | 查询 `QRCODE_STATUS` |
| acc-server | 推送行业数据 | 推送至 ACC/行业服务 |

---

## 二、接口清单

### 2.1 行业数据接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| IF8D_03 | 请求行业数据 | `/ci/app/requestIndustryData` | POST | `RequestIndustryDataReqDTO` | `RequestIndustryDataResult` | |
| IF8D_03 | 请求离线码数据 | `/ci/app/requestNoSignalData` | POST | `RequestNoSignalDataReqDTO` | `RequestNoSignalDataResult` | |
| - | 构建行业卡数据 | `/industryCardDataBuild` | POST | `IndustryCardDataBuildReqDTO` | `IndustryCardDataBuildRespDTO` | 内部接口 |
| - | 支付宝出行-获取行业数据 | `/channel/requestIndustryData` | POST | `AlipayTripRequestIndustryDataReqDTO` | `AlipayTripRequestIndustryDataRespDTO` | |
| - | 支付宝出行-添加签约信息 | `/channel/addContract` | POST | `AlipayTripAddContractReqDTO` | `AlipayTripAddContractRespDTO` | |
| - | 支付宝出行-获取离线码数据 | `/channel/requestNoSignalData` | POST | `AlipayTripRequestNoSignalDataReqDTO` | `AlipayTripRequestNoSignalDataRespDTO` | |

### 2.2 日票相关接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| - | 日票激活 | `/ci/app/dailyTicket/activate` | POST | `DailyTicketActivateReqDTO` | `DailyTicketBaseResult` | |
| - | 日票下单 | `/ci/app/dailyTicket/order` | POST | `DailyTicketOrderReqDTO` | `DailyTicketBaseResult` | |
| - | 日票订单号 | `/ci/app/dailyTicket/orderNo` | POST | `DailyTicketOrderNoReqDTO` | `DailyTicketBaseResult` | |

---

## 三、核心业务流程

### 3.1 IF8D_03 请求行业数据流程

**调用链路**：
```
APP
  → fep-app-server (/ci/app/requestIndustryData)
    → IndustryDataClient.requestIndustryData()
      → industry-data-server (/ci/app/requestIndustryData)
        → IndustryCardDataServiceImpl.requestIndustryData()
          → 1. 参数校验
          → 2. 查询 QRCodeStatus
          → 3. 构建行业卡数据
          → 4. 推送至 ACC/行业服务
          → 5. 返回结果
```

**业务逻辑**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType` 是否为空
2. **查询票卡状态** - 根据 `cardId` 查询 `QRCODE_STATUS` 获取当前状态
3. **构建行业卡数据** - 根据 `QRCODE_STATUS` 构建 `INDUSTRY_CARD_DATA` 数据
4. **推送至 ACC** - 推送至 ACC/行业服务供闸机/终端下发到手机
5. **返回结果** - 返回行业卡数据、状态、二维码地址等

### 3.2 IF8D_03 请求离线码数据流程

**业务逻辑**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType`、`senceType` 是否为空
2. **查询票卡状态** - 根据 `cardId` 查询 `QRCODE_STATUS`
3. **判断场景** - 根据 `senceType` 判断在站内/不在站内
4. **构建离线码数据** - 构建不同场景的离线码数据
5. **返回结果** - 返回离线码数据

### 3.3 支付宝出行-获取行业数据流程

**业务逻辑**：

1. **参数校验** - 校验 `thirdUserId`、`cardId` 是否为空
2. **查询票卡状态** - 根据 `cardId` 查询 `QRCODE_STATUS`
3. **构建支付宝行业数据** - 构建符合支付宝出行规范的行业数据
4. **返回结果** - 返回支付宝行业数据

### 3.4 日票激活流程

**业务逻辑**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType` 是否为空
2. **检查日票状态** - 查询日票状态
3. **激活日票** - 更新日票状态为已激活
4. **返回结果** - 返回激活结果

---

## 四、数据模型

### 4.1 INDUSTRY_CARD_DATA（行业卡数据表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| CARD_ID | VARCHAR2(128) | 卡号 |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID |
| CARD_TYPE | VARCHAR2(64) | 卡类型 |
| INDUSTRY_DATA | CLOB | 行业数据 JSON |
| STATUS | VARCHAR2(32) | 状态 |
| CREATE_TIME | TIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | 更新时间 |

### 4.2 DAILY_TICKET_ORDER（日票订单表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ORDER_NO | VARCHAR2(128) | 订单号 |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID |
| CARD_ID | VARCHAR2(128) | 卡号 |
| STATUS | VARCHAR2(32) | 订单状态 |
| ACTIVATE_TIME | TIMESTAMP | 激活时间 |
| CREATE_TIME | TIMESTAMP | 创建时间 |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `industry-data-server/src/main/java/com/chinasofti/huateng/industry/controller/IndustryDataController.java` | 行业数据接口入口 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/IndustryCardDataService.java` | 行业卡数据服务接口 |
| `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/impl/IndustryCardDataServiceImpl.java` | 行业卡数据服务实现 |
| `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/DailyTicketAppService.java` | 日票APP服务接口 |
| `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/impl/DailyTicketAppServiceImpl.java` | 日票APP服务实现 |
| `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/AlipayTripService.java` | 支付宝出行服务接口 |
| `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/impl/AlipayTripServiceImpl.java` | 支付宝出行服务实现 |

### 5.3 资源配置

| 文件路径 | 说明 |
|---------|------|
| `industry-data-server/src/main/resources/application.properties` | 应用配置 |
| `industry-data-server/src/main/resources/mapper/*.xml` | MyBatis Mapper XML |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| industry-data-server | ticket-server | 获取票卡状态 | 查询 `QRCODE_STATUS` |
| industry-data-server | acc-server | 推送行业数据 | 推送至 ACC/行业服务 |

### 6.2 与 fep-app-server 的关系

industry-data-server 的接口通过 fep-app-server 聚合暴露给 APP：
- fep-app-server 的 `IndustryDataController` 通过 RPC 调用 industry-data-server
- 支付宝出行接口通过 fep-alipay-server 转发

---

## 七、相关文档

- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流
- `docs/02-微服务文档/fep-app-server.md` - APP 接入网关

---

## 八、源码文件索引

### 8.1 Java 源文件（共 6 个）

1. `industry-data-server/src/main/java/com/chinasofti/huateng/industry/controller/IndustryDataController.java`
2. `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/IndustryCardDataService.java`
3. `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/impl/IndustryCardDataServiceImpl.java`
4. `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/impl/AlipayTripServiceImpl.java`
5. `industry-data-server/src/main/java/com/chinasofti/huateng/industry/service/AlipayTripService.java`
6. `industry-data-server/src/main/java/com/chinasofti/huateng/IndustryDataServer.java`

### 8.2 资源文件

1. `industry-data-server/src/main/resources/application.properties`
2. `industry-data-server/src/main/resources/mapper/*.xml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/industry-data-server` 模块源码。

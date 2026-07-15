# 支付宝出行 vs 地铁APP 接口实现对比

## 1. 架构概览

| 维度 | 地铁APP | 支付宝出行 |
|------|---------|------------|
| 入口服务 | fep-app-server (9101) | fep-alipay-server (9105) |
| 账户服务 | account-server (9098) | alipay-account-server (9106) |
| 支付签约服务 | pay-sign-server (9107) | alipay-pay-sign-server (9108) |
| 行业数据服务 | industry-data-server | industry-data-server (复用) |
| 票务服务 | ticket-server (9103) | ticket-server (9103，复用) |
| 黑名单服务 | blacklist-server | blacklist-server (复用) |
| 数据库 | Oracle | Oracle |
| 接口规范 | IF8A/IF8B | 支付宝出行独立规范 |

## 2. 开户接口对比

### 2.1 接口信息

| 维度 | 地铁APP | 支付宝出行 |
|------|---------|------------|
| 接口路径 | `/requestApplication` | `/channel/requestApplication` |
| 请求方法 | POST | POST |
| 请求DTO | `RequestApplicationReqDTO` | `AlipayTripRequestApplicationReqDTO` |
| 响应DTO | `RequestApplicationResult` | `AlipayTripRequestApplicationRespDTO` |
| 服务实现 | `AccountApplicationServiceImpl.requestApplication()` | `AlipayAccountServiceImpl.requestApplication()` |
| 控制器 | `RequestApplicationController` | `FepAlipayTripRequestApplicationController` |

### 2.2 请求参数对比

| 参数 | 地铁APP | 支付宝出行 | 说明 |
|------|---------|------------|------|
| thirdUserId | ✓ | ✓ | 第三方用户ID |
| thirdPayId | ✓ | - | 支付账户ID，支付宝开户时不提供 |
| channel | ✓ | - | 支付通道编码，支付宝开户时不提供 |
| reqContractNo | ✓ | - | 签约请求号，支付宝开户时不提供 |
| cardType | ✓ | ✓ | 卡类型编码 |
| msisdn | ✓ | ✓ | 手机号 |
| userName | ✓ | - | 姓名，支付宝暂不提供 |
| userId | ✓ | - | 身份证号，支付宝暂不提供 |
| extend1 | ✓ | ✓ | 扩展字段1 |
| extend2 | ✓ | ✓ | 扩展字段2 |
| ticketCard | ✓ | - | NFC开卡字段 |
| companionFlag | ✓ | - | 通行票标识 |
| ticketLimit | ✓ | - | 车票显示限制 |
| cardIssueCode | ✓ | ✓ | 发卡渠道代码 |
| **signType** | **✓** | **-** | **签名类型，支付宝不返回** |
| **sign** | **✓** | **-** | **签名值，支付宝不返回** |

### 2.3 响应参数对比

| 参数 | 地铁APP | 支付宝出行 | 说明 |
|------|---------|------------|------|
| retCode | ✓ | ✓ | 返回码 |
| retMsg | ✓ | ✓ | 返回消息 |
| cardId | ✓ | ✓ | 逻辑卡号 |
| cardType | ✓ | ✓ | 卡类型 |
| **signType** | **✓** | **-** | **签名类型，支付宝不需要** |
| **sign** | **✓** | **-** | **签名值，支付宝不需要** |
| **status** | **-** | **✓** | **用户状态，支付宝独立字段** |

## 3. 数据库设计对比

### 3.1 用户主表

| 维度 | 地铁APP (USER_ITP_REG_INFO) | 支付宝出行 (ALIPAY_USER_INFO) |
|------|------------------------------|-------------------------------|
| 主键 | id (Integer, 自增) | thirdUserId (String, 业务主键) |
| cardId | ✓ | ✓ |
| cardType | ✓ | ✓ |
| thirdUserId | ✓ | ✓ |
| msisdn | ✓ | ✓ |
| regTms | ✓ | - |
| delYn | ✓ (Integer) | - |
| status | - | ✓ (String) |
| deleteFlag | - | ✓ (String) |
| channel | ✓ | ✓ |
| thirdPayId | ✓ | ✓ |
| reqContractNo | ✓ | ✓ |
| userName | ✓ | - |
| userId | ✓ | - |
| cardIssueCode | ✓ | ✓ |
| extend1 | - | ✓ |
| extend2 | - | ✓ |
| version | - | ✓ |
| createTime | - | ✓ |
| updateTime | - | ✓ |
| createBy | - | ✓ |

### 3.2 流水表

| 维度 | 地铁APP (USER_ITP_REG_LOG) | 支付宝出行 (ALIPAY_REG_LOG) |
|------|-----------------------------|-----------------------------|
| 主键 | id (Integer, 自增) | requestSeq (String, UUID) |
| cardId | ✓ | ✓ |
| cardType | ✓ | ✓ |
| thirdUserId | ✓ | ✓ |
| msisdn | ✓ | ✓ |
| operDateTime | ✓ | - |
| operType | ✓ | - |
| responseSeq | - | ✓ |
| cardIssueCode | - | ✓ |
| channel | - | ✓ |
| extend1 | - | ✓ |
| extend2 | - | ✓ |
| requestBody | - | ✓ (CLOB) |
| responseBody | - | ✓ (CLOB) |
| resultCode | - | ✓ |
| resultMsg | - | ✓ |
| operator | - | ✓ |
| ip | - | ✓ |
| remark | - | ✓ |
| version | - | ✓ |
| createTime | - | ✓ |
| createBy | - | ✓ |

### 3.3 卡号池表

| 维度 | 地铁APP (USER_ACC_TICKET_NO) | 支付宝出行 (ALIPAY_CARD_POOL) |
|------|-------------------------------|-------------------------------|
| 主键 | id (Integer, 自增) | cardId (String, 业务主键) |
| cardId | ✓ | ✓ |
| thirdUserId | ✓ | ✓ |
| insertTms | ✓ | ✓ |
| regTms | ✓ | ✓ |
| status | - | ✓ (UNASSIGNED/ASSIGNED/USED) |
| deleteFlag | - | ✓ |
| version | - | ✓ |
| createTime | - | ✓ |
| updateTime | - | ✓ |

## 4. 业务逻辑对比

### 4.1 开户流程

| 步骤 | 地铁APP | 支付宝出行 |
|------|---------|------------|
| 1. 参数校验 | ✓ | ✓ |
| 2. 重复开户检查 | delYn == 1 | AlipayUserInfo 存在即返回 |
| 3. cardIssueCode匹配 | ✓ | ✓ |
| 4. 卡号分配 | CardPoolService.allocateNextCard | AlipayCardPoolMapper.selectNextAvailableCard |
| 5. 插入用户表 | UserItpRegInfo | AlipayUserInfo |
| 6. 插入流水表 | UserItpRegLog | AlipayRegLog |
| 7. 调用ticket-server | ✓ | ✓ |
| 8. ticket失败处理 | 事务回滚，返回错误码 | 仅记录警告，不中断开户 |
| 9. 返回响应 | RequestApplicationResult | AlipayTripRequestApplicationRespDTO |

### 4.2 关键差异

1. **ticket-server 失败处理**
   - 地铁APP：失败时事务回滚，开户失败
   - 支付宝出行：仅记录警告日志，开户仍成功

2. **重复开户判断**
   - 地铁APP：`delYn == 1` 且 `cardIssueCode` 匹配
   - 支付宝出行：`AlipayUserInfo` 存在即返回已开户

3. **签约信息处理**
   - 地铁APP：开户时写入 `channel/thirdPayId/reqContractNo`
   - 支付宝出行：开户阶段留空，后续签约流程更新

## 5. 技术实现对比

### 5.1 微服务调用

| 维度 | 地铁APP | 支付宝出行 |
|------|---------|------------|
| 调用方式 | 同进程内调用 | RPC远程调用 |
| RPC客户端 | 无 | AlipayAccountClient |
| 服务发现 | 无 | ProxyWebClient + WebClient |
| 配置前缀 | - | service.alipay-account.url |

### 5.2 配置对比

| 配置项 | 地铁APP | 支付宝出行 |
|--------|---------|------------|
| 服务端口 | 9098 | 9106 |
| 应用名称 | account | alipay-account |
| 数据库Host | 127.0.0.1:1521 | 172.20.222.3 |
| 数据库名 | orcl | AFCITPDB |
| 卡号池调试模式 | account.card-pool.debug-manual-allocate=true | alipay.card-pool.debug-manual-allocate=false |
| 调试卡号 | 9900000000000001 | - |
| Knife4j | production=true | production=false |

### 5.3 错误码

| 维度 | 地铁APP | 支付宝出行 |
|------|---------|------------|
| 错误码枚举 | AccountErrorCodeEnum | FepAppErrorCodeEnum |
| 位置 | account-server | model模块共享 |
| 适用场景 | account-server专用 | 所有支付宝服务共享 |

## 6. 独立部署特性

支付宝出行系统具有以下独立特性：

1. **独立数据库表**：所有表使用 `ALIPAY_*` 前缀，不与地铁APP共享
2. **独立接口路径**：统一在 `/channel/*` 下
3. **独立错误码**：使用 `FepAppErrorCodeEnum`
4. **独立配置**：`alipay.card-pool.*` 配置前缀
5. **独立服务端口**：不与其他服务冲突
6. **主键策略**：业务字段做主键，无自增ID
7. **状态管理**：使用 `status` + `deleteFlag` 双字段管理

## 7. 代码复用情况

### 7.1 直接复用

| 模块 | 复用内容 |
|------|----------|
| model模块 | CommonResult、FepAppErrorCodeEnum、RegisterRideStatusReqDTO/RespDTO |
| rpc模块 | ProxyWebClient、TicketClient |
| ticket-server | 乘车状态注册接口 |
| industry-data-server | 行业数据加密能力 |
| blacklist-server | 黑名单数据（通过thirdUserId+cardId天然隔离） |

### 7.2 独立实现

| 模块 | 实现内容 |
|------|----------|
| alipay-account-server | 独立Entity、Mapper、Service、Controller |
| alipay-pay-sign-server | 签约/解约/支付/退款独立逻辑 |
| fep-alipay-server | 入口路由、签名验证、响应封装 |

## 8. 测试要点

### 8.1 开户接口测试

**地铁APP**
```bash
POST http://127.0.0.1:9098/requestApplication
Content-Type: application/json

{
  "thirdUserId": "APP_123456",
  "cardType": "02",
  "msisdn": "74955953457",
  "cardIssueCode": "0001"
}
```

**支付宝出行**
```bash
POST http://127.0.0.1:9106/channel/requestApplication
Content-Type: application/json

{
  "thirdUserId": "ALIPAY_8823456789012345",
  "cardType": "02",
  "msisdn": "74955953457",
  "extend1": "",
  "extend2": "",
  "cardIssueCode": "0007"
}
```

### 8.2 预期响应

**地铁APP**
```json
{
  "retCode": "0000",
  "retMsg": "成功",
  "cardId": "9900000000000001",
  "cardType": "02",
  "signType": "00",
  "sign": ""
}
```

**支付宝出行**
```json
{
  "retCode": "0000",
  "retMsg": "成功",
  "cardId": "9900000000000001",
  "cardType": "02",
  "status": "ACTIVE"
}
```

## 9. 待完善接口

以下接口在支付宝出行中待实现：

| 接口 | 地铁APP状态 | 支付宝出行状态 |
|------|-------------|----------------|
| 开户申请 | ✓ 已实现 | ✓ 已实现 |
| 添加支付通道 | ✓ 已实现 | 待实现 |
| 设置默认支付通道 | ✓ 已实现 | 待实现 |
| 删除支付通道 | ✓ 已实现 | 待实现 |
| 查询用户信息 | ✓ 已实现 | 待实现 |
| 签约登记 | ✓ 已实现 | 待实现 |
| 解约登记 | ✓ 已实现 | 待实现 |
| 支付申请 | ✓ 已实现 | 待实现 |
| 退款申请 | ✓ 已实现 | 待实现 |
| 支付结果查询 | ✓ 已实现 | 待实现 |
| 乘车记录查询 | ✓ 已实现 | 待实现 |
| 乘车记录详情 | ✓ 已实现 | 待实现 |

## 10. 总结

支付宝出行接口在以下方面与地铁APP保持一致性：

1. 使用相同的技术栈（Spring Boot 3.2.6、Oracle、MyBatis）
2. 复用相同的底层组件（ProxyWebClient、TicketClient）
3. 保持相同的业务流程（开户→签约→支付→退款）
4. 遵循相同的错误码规范（0000成功、9999系统错误、8001参数错误）

支付宝出行接口在以下方面实现独立：

1. 完全独立的数据库表结构
2. 独立的微服务部署
3. 独立的接口路径和DTO定义
4. 更完整的流水记录和审计字段
5. 更灵活的主键策略（业务字段做主键）
6. 更宽松的ticket-server失败处理策略

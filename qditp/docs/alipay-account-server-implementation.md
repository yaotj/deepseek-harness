# 支付宝出行开卡申请接口实现总结

> 本文档记录支付宝出行 `alipay-account-server` 开卡申请接口的实现过程、遇到的问题及解决方案，供后续开发参考。

---

## 1. 架构概览

| 组件 | 说明 |
|------|------|
| 入口服务 | `fep-alipay-server` (9105) |
| 账户服务 | `alipay-account-server` (8080) |
| 票务服务 | `ticket-server` (9103，复用) |
| 数据库 | Oracle (AFCITPDB) |
| RPC 客户端 | `AlipayAccountClient extends ProxyWebClient` |

---

## 2. 实现时间线

1. 创建 `alipay-account-server` 模块，参考 `account-server` 改为 Oracle 依赖
2. 创建 Entity：`AlipayUserInfo`、`AlipayRegLog`、`AlipayCardPool`
3. 创建 Mapper + XML，使用 Oracle 语法（`SYSDATE`、`fetch first 1 rows only`）
4. 创建 Service + Controller，实现开卡申请逻辑
5. 创建 `AlipayAccountClient` RPC 客户端
6. 更新 `fep-alipay-server` 调用 `AlipayAccountClient`
7. 参考地铁APP实现卡号池自动监控、自动申请、3次重试、乐观锁

---

## 3. 遇到的问题及解决方案

### 3.1 编译错误：找不到包 `com.chinasofti.huateng.common.constant`

**原因**：`FepAppErrorCodeEnum` 在 `model` 模块，`alipay-account-server` 编译时 `model` 还未安装到本地仓库。

**解决**：
```bash
# 先在根目录完整安装
mvn clean install -DskipTests

# 再编译 alipay-account-server
cd alipay-account-server
mvn clean compile
```

### 3.2 编译错误：`setCardType/setStatus/setCardNum/setDeleteFlag` 找不到符号

**原因**：
- `AlipayTripRequestApplicationRespDTO` 缺少 `cardType`、`status` 字段
- `RegisterRideStatusReqDTO` 没有 `setCardNum` 方法，应该是 `setCardId`
- `AlipayRegLog` 没有 `deleteFlag` 字段

**解决**：
- 补充 `AlipayTripRequestApplicationRespDTO#cardType`、`status`
- `AlipayAccountServiceImpl` 中将 `registerReq.setCardNum(cardId)` 改为 `registerReq.setCardId(cardId)`
- 去掉 `AlipayRegLog#setDeleteFlag`

### 3.3 启动失败：`TicketClient` Bean 找不到

**原因**：`TicketClient` 在 `com.chinasofti.huateng.rpc.ticket` 包下，需要 `@EnableRpcTicket` 注解才能被扫描。

**解决**：在 `AlipayAccountServer` 启动类添加：
```java
@EnableRpcTicket
@ComponentScan(basePackages = {"com.chinasofti.huateng.alipay.account", "com.chinasofti.huateng.rpc"})
```

### 3.4 接口返回 `Required request body is missing`

**原因**：Apifox/Postman 请求体为空（`content-length=0`），虽然 `Content-Type: application/json` 设置正确。

**解决**：检查请求配置，确保 Body 类型为 `raw → JSON`，并填写正确的 JSON 内容。

---

## 4. 接口测试

### 4.1 请求示例

```bash
POST http://127.0.0.1:8080/channel/requestApplication
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

### 4.2 成功响应

```json
{
  "retCode": "0000",
  "retMsg": "成功",
  "cardId": "260701093052000001",
  "cardType": "02",
  "status": "ACTIVE"
}
```

### 4.3 失败响应（无可分配卡资源）

```json
{
  "retCode": "9999",
  "retMsg": "无可分配卡资源",
  "cardId": null,
  "cardType": null,
  "status": null
}
```

### 4.4 重复开户响应

```json
{
  "retCode": "0000",
  "retMsg": "用户已开户",
  "cardId": "260701093052000001",
  "cardType": "02",
  "status": "ACTIVE"
}
```

---

## 5. 与地铁APP的关键差异

| 维度 | 地铁APP | 支付宝出行 |
|------|---------|------------|
| 服务端口 | 9098 | 8080 |
| 接口路径 | `/requestApplication` | `/channel/requestApplication` |
| 主键策略 | 独立 `id` 自增 | 业务字段做主键 |
| 用户表 | `USER_ITP_REG_INFO` | `ALIPAY_USER_INFO` |
| 流水表 | `USER_ITP_REG_LOG` | `ALIPAY_REG_LOG` |
| 卡号池表 | `USER_ACC_TICKET_NO` | `ALIPAY_CARD_POOL` |
| ticket 失败处理 | 事务回滚 | 仅警告，不中断开户 |
| 签约信息 | 开户时写入 | 开户阶段留空，后续签约更新 |
| 签名字段 | 返回 `signType/sign` | 不返回签名 |
| 状态字段 | `delYn` (Integer) | `status` + `deleteFlag` (String) |
| 审计字段 | 无 | `createTime/updateTime/createBy` |
| 乐观锁 | 无 | `version` |

---

## 6. 卡号池实现

### 6.1 卡号格式

```
QD + YYMMDD + HHMMSS + 6位序列
示例：260701093052000001
```

### 6.2 分配流程

1. `allocateNextCard(thirdUserId)`
2. 如果开启调试模式且配置了调试卡号，直接返回调试卡号
3. 调用 `monitorCardPool()` 检查卡号池数量
4. 低于阈值 `threshold=10` 时自动申请 `batch-size=20` 张新卡
5. 最多重试 3 次：
   - 查询未使用卡号 `selectUnusedCard()`
   - 如果为空，自动申请新卡号 `requestLogicalCardNo()`
   - 乐观锁更新 `updateAllocateCard()`，防止并发分配

### 6.3 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `alipay.card-pool.threshold` | 10 | 卡号池监控阈值 |
| `alipay.card-pool.batch-size` | 20 | 自动申请卡号数量 |
| `alipay.card-pool.debug-manual-allocate` | true | 调试模式，直接返回配置的卡号 |
| `alipay.card-pool.debug-manual-card-id` | 9900000000000001 | 调试卡号 |

---

## 7. 待完善接口

| 接口 | 状态 | 说明 |
|------|------|------|
| 开户申请 | ✓ 已完成 | `/channel/requestApplication` |
| 查询用户信息 | 待实现 | `/channel/queryUserInfo` |
| 签约登记 | 待实现 | `/channel/signContract` |
| 解约登记 | 待实现 | `/channel/cancelContract` |
| 支付申请 | 待实现 | `/api/payment/pay` |
| 退款申请 | 待实现 | `/api/payment/refund` |
| 支付结果查询 | 待实现 | `/api/payment/query` |
| 乘车记录查询 | 待实现 | `/channel/queryTripRecord` |

---

## 8. 相关文档

- [支付宝接口开发计划.md](docs/支付宝接口开发计划.md)
- [alipay-vs-metro-comparison.md](docs/alipay-vs-metro-comparison.md)
- [alipay-account-server-openapi.json](docs/alipay-account-server-openapi.json)

---

## 9. 参考实现

- `account-server`：地铁APP账户服务，Oracle 配置参考
- `CardPoolServiceImpl`：地铁APP卡号池实现，支付宝参考了其自动监控、自动申请、3次重试、乐观锁机制

---

## 10. 卡号生成逻辑修改记录

### 10.1 问题描述

服务重启后，`AtomicInteger idGenerator` 重置为 1，导致同一秒内生成重复卡号：
```
260701093052000001
260701093052000001  // 重复
```

### 10.2 旧实现（已注释保留）

```java
private String buildCardId() {
    return "QD" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
            + String.format("%06d", idGenerator.getAndIncrement());
}
```

**问题**：`idGenerator` 是实例级别的 `AtomicInteger`，服务重启后重置为 1。

### 10.3 新实现（当前使用）

```java
private String buildCardId() {
    // 旧实现：依赖 AtomicInteger 序列号，服务重启后会重复
    // return "QD" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
    //         + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
    //         + String.format("%06d", idGenerator.getAndIncrement());

    // 新实现：6位完全随机数，避免重启后重复
    return "QD" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
            + String.format("%06d", (int)(Math.random() * 900000 + 100000));
}
```

**改进**：6位随机数范围 `100000~999999`，避免重启后重复。

### 10.4 地铁APP对比

地铁APP使用相同的 `AtomicInteger` 实现，但通过以下方式降低影响：
- 卡号池预填充，大部分请求分配已有卡号
- 使用自增 `ID` 作为主键，`CARD_ID` 重复不会报主键冲突
- 服务长期运行，重启频率低

支付宝采用随机数方案，更简洁可靠。

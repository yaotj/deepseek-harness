# 支付宝出行-支付结果查询 响应字段与数据库字段映射

## 1. 接口信息

| 项目 | 内容 |
|------|------|
| 接口名称 | 支付宝出行-支付结果查询 |
| 接口路径 | `POST /api/payment/payQuery` |
| 服务模块 | alipay-pay-sign-server |
| 实现方法 | `AlipayPaySignServiceImpl.payQuery()` |
| 调用方 | fep-alipay-server |

## 2. 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `orderNo` | string | 是 | 原订单号 |
| `cardIssueCode` | string | 否 | 卡机构编号，固定传 `0007` |
| `cardNum` | string | 否 | 逻辑卡号，从数据库 `ALIPAY_PAY_LOG.CARD_ID` 获取 |
| `channelAgreementNo` | string | 否 | 渠道协议号，由 fep-alipay-server 传入 |

## 3. 响应字段与数据库字段映射

### 3.1 响应字段定义

| 响应字段 | 类型 | 说明 | 数据库来源 |
|----------|------|------|------------|
| `retCode` | string | 返回码 | 系统级返回码，非数据库字段 |
| `retMsg` | string | 返回消息 | 系统级返回消息，非数据库字段 |
| `outTradeNo` | string | 订单号 | `ALIPAY_PAY_LOG.ORDER_NO` |
| `paymentTime` | string | 支付时间 | `ALIPAY_PAY_LOG.TRANS_TIME` |
| `tradeStatus` | string | 支付状态 | `ALIPAY_PAY_LOG.PAY_STATUS` |
| `totalAmount` | string | 支付金额 | `ALIPAY_PAY_LOG.PAY_AMOUNT` |
| `tradeNo` | string | 支付渠道订单号 | `ALIPAY_PAY_LOG.TRADE_NO` |
| `tradeDesc` | string | 支付结果 | `ALIPAY_PAY_LOG.RESULT_MSG` |

### 3.2 数据库字段详细映射

| 数据库字段 | 字段类型 | 说明 | 响应字段 | 映射规则 |
|------------|----------|------|----------|----------|
| `ORDER_NO` | VARCHAR | 原订单号 | `outTradeNo` | 直接映射 |
| `TRANS_TIME` | VARCHAR2(20) | 交易时间 yyyy-MM-dd HH:mm:ss | `paymentTime` | 直接映射 |
| `PAY_STATUS` | VARCHAR | 支付状态 SUCCESS/FAIL | `tradeStatus` | 直接映射 |
| `PAY_AMOUNT` | VARCHAR | 支付金额，单位分 | `totalAmount` | 直接映射 |
| `TRADE_NO` | VARCHAR | 支付宝交易号/渠道订单号 | `tradeNo` | 直接映射 |
| `RESULT_MSG` | VARCHAR | 支付结果描述 | `tradeDesc` | 直接映射 |

### 3.3 支付中心响应字段映射

支付中心 `payQuery` 响应 `data` 字段 Base64 解码后的 JSON 字段映射：

| 支付中心字段 | 类型 | 说明 | 映射到数据库字段 | 更新规则 |
|--------------|------|------|------------------|----------|
| `channelOrderNo` | string | 支付渠道订单号 | `TRADE_NO` | 支付中心返回非空时覆盖本地值 |
| `transAmount` | string | 支付金额，单位分 | `PAY_AMOUNT` | 支付中心返回非空时覆盖本地值 |
| `transTime` | string | 交易时间 yyyy-MM-dd HH:mm:ss | `TRANS_TIME` | 支付中心返回非空时覆盖本地值 |
| `transStatus` | string | 交易状态 1-成功 2-失败 | `PAY_STATUS` | `1` → `SUCCESS`，`2` → `FAIL` |

## 4. 更新逻辑

### 4.1 正常流程

1. **查询本地支付流水**
   ```sql
   SELECT * FROM ALIPAY_PAY_LOG WHERE ORDER_NO = #{orderNo}
   ```

2. **调用支付中心查询**
   ```java
   payCenterClient.payQuery(bizDataMap)
   ```

3. **解析支付中心响应**
   - 若 `success=true`：
     - 解析 `channelOrderNo`、`transAmount`、`transTime`、`transStatus`
     - 映射 `transStatus`：`1` → `PAY_STATUS_SUCCESS`，`2` → `PAY_STATUS_FAIL`
   - 若 `success=false`：
     - 使用响应 `msg` 作为 `RESULT_MSG`
     - 设置 `PAY_STATUS = PAY_STATUS_FAIL`

4. **更新本地支付流水**
   ```sql
   UPDATE ALIPAY_PAY_LOG
   SET PAY_STATUS = #{payStatus},
       TRADE_NO = #{tradeNo},
       TRANS_TIME = #{transTime},
       PAY_AMOUNT = #{payAmount},
       RESULT_CODE = #{resultCode},
       RESULT_MSG = #{resultMsg}
   WHERE ORDER_NO = #{orderNo}
   ```

5. **重新查询最新状态并构建响应**

### 4.2 字段覆盖规则

| 场景 | 字段更新策略 |
|------|--------------|
| 支付中心返回 `transStatus=1` | 更新 `PAY_STATUS=SUCCESS`，`RESULT_CODE=0000`，`RESULT_MSG=支付成功` |
| 支付中心返回 `transStatus=2` | 更新 `PAY_STATUS=FAIL`，`RESULT_CODE=9999`，`RESULT_MSG=支付失败` |
| 支付中心返回 `channelOrderNo` | 覆盖本地 `TRADE_NO` |
| 支付中心返回 `transAmount` | 覆盖本地 `PAY_AMOUNT` |
| 支付中心返回 `transTime` | 覆盖本地 `TRANS_TIME` |
| 支付中心调用失败 | 保留本地字段值，`PAY_STATUS` 不变 |

## 5. 响应示例

### 5.1 支付成功

```json
{
  "retCode": "0000",
  "retMsg": "查询成功",
  "outTradeNo": "20260101000001",
  "paymentTime": "2026-01-01 12:34:56",
  "tradeStatus": "SUCCESS",
  "totalAmount": "200",
  "tradeNo": "CHANNEL_20260101_ABC123",
  "tradeDesc": "支付成功"
}
```

### 5.2 支付失败

```json
{
  "retCode": "0000",
  "retMsg": "查询成功",
  "outTradeNo": "20260101000001",
  "paymentTime": "2026-01-01 12:34:56",
  "tradeStatus": "FAIL",
  "totalAmount": "200",
  "tradeNo": "CHANNEL_20260101_ABC123",
  "tradeDesc": "支付失败"
}
```

## 6. 相关文件

| 文件 | 说明 |
|------|------|
| `AlipayPaySignServiceImpl.java` | 支付结果查询业务实现 |
| `AlipayPayLogMapper.java` | 支付流水 Mapper 接口 |
| `AlipayPayLogMapper.xml` | 支付流水 SQL 映射 |
| `AlipayPayLog.java` | 支付流水实体 |
| `AlipayTripPayQueryReqDTO.java` | 支付结果查询请求 DTO |
| `AlipayTripPayQueryRespDTO.java` | 支付结果查询响应 DTO |
| `PayCenterClient.java` | 支付中心调用工具类 |

## 7. 审核要点

1. **响应字段完整性**：是否包含所有要求的 8 个响应字段
2. **数据库字段映射正确性**：响应字段是否准确映射到对应的数据库字段
3. **支付中心字段解析**：`channelOrderNo`、`transAmount`、`transTime`、`transStatus` 是否正确解析
4. **更新逻辑**：支付中心响应后是否正确更新本地数据库
5. **边界处理**：支付中心调用失败、返回空值等异常场景的处理

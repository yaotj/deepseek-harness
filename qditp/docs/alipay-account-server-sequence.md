# 支付宝出行开卡申请接口时序图与副作用

## 1. 时序图

```mermaid
sequenceDiagram
    participant A as 支付宝APP/客户端
    participant F as fep-alipay-server<br/>(9105)
    participant C as AlipayAccountClient<br/>(RPC)
    participant S as alipay-account-server<br/>(8080)
    participant DB as Oracle数据库<br/>(AFCITPDB)
    participant T as ticket-server<br/>(9103)

    A->>F: POST /channel/requestApplication<br/>{thirdUserId, cardType, msisdn, ...}
    F->>C: alipayTripRequestApplication(req)
    C->>S: POST /channel/requestApplication<br/>(ProxyWebClient转发)

    Note over S: 1. 参数校验
    S->>DB: SELECT * FROM ALIPAY_USER_INFO<br/>WHERE THIRD_USER_ID = ?
    DB-->>S: 查询结果

    alt 用户已存在
        S-->>C: 返回已开户响应
        C-->>F: 返回已开户响应
        F-->>A: {"retCode":"0000","retMsg":"用户已开户"}
    else 用户不存在
        Note over S: 2. 卡号分配
        S->>DB: SELECT * FROM ALIPAY_CARD_POOL<br/>WHERE STATUS='UNASSIGNED'<br/>ORDER BY CARD_ID<br/>FETCH FIRST 1 ROWS ONLY
        DB-->>S: 返回可用卡号

        alt 无可用卡号
            S->>S: requestLogicalCardNo()<br/>批量生成新卡号
            S->>DB: INSERT INTO ALIPAY_CARD_POOL<br/>(批量插入)
            S->>DB: 再次查询可用卡号
            DB-->>S: 返回可用卡号
        end

        Note over S: 3. 乐观锁更新卡号状态
        S->>DB: UPDATE ALIPAY_CARD_POOL<br/>SET STATUS='ASSIGNED', THIRD_USER_ID=?,<br/>REG_TMS=SYSDATE<br/>WHERE CARD_ID=? AND STATUS='UNASSIGNED'
        DB-->>S: 更新结果

        Note over S: 4. 插入用户信息
        S->>DB: INSERT INTO ALIPAY_USER_INFO<br/>(THIRD_USER_ID, CARD_ID, ...)
        DB-->>S: 插入成功

        Note over S: 5. 插入注册流水
        S->>DB: INSERT INTO ALIPAY_REG_LOG<br/>(REQUEST_SEQ, THIRD_USER_ID, ...)
        DB-->>S: 插入成功

        Note over S: 6. 调用ticket-server
        S->>T: registerRideStatus(thirdUserId, cardId, cardType)
        T-->>S: 返回结果

        alt ticket-server调用失败
            Note over S: 仅记录警告，不中断开户
        end

        S-->>C: 返回成功响应
        C-->>F: 返回成功响应
        F-->>A: {"retCode":"0000","retMsg":"成功","cardId":"...","cardType":"02","status":"ACTIVE"}
    end
```

## 2. 副作用清单

### 2.1 数据库写入（Oracle AFCITPDB）

| 表名 | 操作 | 时机 | 说明 |
|------|------|------|------|
| ALIPAY_USER_INFO | INSERT | 开户成功时 | 写入用户主表信息 |
| ALIPAY_REG_LOG | INSERT | 开户成功时 | 写入开卡申请流水 |
| ALIPAY_CARD_POOL | UPDATE | 分配卡号时 | 状态从 UNASSIGNED → ASSIGNED |
| ALIPAY_CARD_POOL | INSERT | 卡号池不足时 | 批量生成新卡号（260701093052000001格式） |

### 2.2 远程调用

| 调用方向 | 目标服务 | 接口 | 说明 |
|----------|----------|------|------|
| fep-alipay-server → alipay-account-server | /channel/requestApplication | RPC调用 | 通过AlipayAccountClient |
| alipay-account-server → ticket-server | registerRideStatus | RPC调用 | 注册乘车状态 |

### 2.3 副作用说明

1. **卡号池自动补充**
   - 触发条件：`ALIPAY_CARD_POOL` 中 `STATUS='UNASSIGNED'` 的记录数 ≤ 10
   - 执行动作：批量生成 20 张新卡号，格式为 `QD` + YYMMDD + HHMMSS + 6位序列
   - 插入表：`ALIPAY_CARD_POOL`

2. **乐观锁更新**
   - 防止并发分配同一张卡号
   - 更新条件：`WHERE CARD_ID=? AND STATUS='UNASSIGNED'`
   - 如果更新失败（updated=0），重试最多3次

3. **ticket-server 失败处理**
   - 失败时**不中断开户流程**
   - 仅记录警告日志：`调用 ticket-server 注册乘车状态失败`
   - 用户表和流水表已经写入成功

4. **重复开户判断**
   - 查询 `ALIPAY_USER_INFO` 是否存在 `THIRD_USER_ID`
   - 存在则直接返回已开户，不再分配新卡号

## 3. 调用链路总览

```
支付宝APP
  ↓ POST /channel/requestApplication
fep-alipay-server (9105)
  ↓ RPC: AlipayAccountClient.alipayTripRequestApplication()
alipay-account-server (8080)
  ↓ 1. 参数校验
  ↓ 2. 重复开户检查 → ALIPAY_USER_INFO
  ↓ 3. 卡号分配 → ALIPAY_CARD_POOL (UPDATE)
  ↓ 4. 插入用户 → ALIPAY_USER_INFO (INSERT)
  ↓ 5. 插入流水 → ALIPAY_REG_LOG (INSERT)
  ↓ 6. 调用ticket → ticket-server
  ↓ 返回响应
fep-alipay-server
  ↓ 返回响应
支付宝APP
```

## 4. 数据流向

```
请求参数 (thirdUserId, cardType, msisdn, ...)
  ↓
ALIPAY_USER_INFO (用户主表)
ALIPAY_REG_LOG (流水表)
ALIPAY_CARD_POOL (卡号池表，状态更新)
  ↓
响应 (retCode, retMsg, cardId, cardType, status)
```

## 5. 错误处理

| 错误场景 | 错误码 | 处理方式 |
|----------|--------|----------|
| 参数校验失败 | 8001 | 直接返回，不操作数据库 |
| 用户已开户 | 0000 | 返回已开户信息，不重复开户 |
| 无可分配卡资源 | 9999 | 返回失败，记录警告 |
| ticket-server调用失败 | 0000 | 仅记录警告，开户仍成功 |
| 系统异常 | 9001 | 返回系统错误 |

## 6. 配置项影响

| 配置项 | 影响范围 | 说明 |
|--------|----------|------|
| alipay.card-pool.debug-manual-allocate | 卡号分配 | true时直接返回调试卡号，不查卡池 |
| alipay.card-pool.debug-manual-card-id | 卡号分配 | 调试模式下使用的固定卡号 |
| alipay.card-pool.threshold | 卡号池监控 | 未使用卡号≤此值时自动申请 |
| alipay.card-pool.batch-size | 卡号池补充 | 自动申请时一次性补充的数量 |
| service.ticket.url | ticket调用 | ticket-server地址 |

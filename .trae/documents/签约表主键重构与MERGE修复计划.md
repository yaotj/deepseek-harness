# 签约表主键重构与 MERGE 修复计划

## 操作流图

### 1. 请求签约流程 (requestSignInfo)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      请求签约流程 (IF8A-71)                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ │
│  │  APP/前端 │───▶│ fep-app-server│───▶│ pay-sign-server│───▶│  支付平台     │ │
│  └──────────┘    └──────────────┘    └──────────────┘    └──────────────┘ │
│       │                │                  │                  │             │
│       │ 1.请求签约      │ 2.RPC转发        │ 3.参数处理        │ 4.获取签约参数 │
│       │ requestSignInfo│ requestSignInfo  │ normalizeVendor  │ contract接口  │
│       │                │                  │ (原样返回)        │              │
│       │                │                  │                  │              │
│       │                │                  │◀─────────────────│ 返回SDK参数   │
│       │                │                  │                  │              │
│       │                │                  ▼                  │              │
│       │                │         ┌─────────────────────┐    │              │
│       │                │         │  APP_PAY_SIGN_INFO  │    │              │
│       │                │         │   (签约成功记录表)   │    │              │
│       │                │         │                     │    │              │
│       │                │         │  查询是否存在记录    │    │              │
│       │                │         │  存在 → 返回重复签约 │    │              │
│       │                │         │  不存在 → 继续流程   │    │              │
│       │                │         └─────────────────────┘    │              │
│       │                │                  │                  │              │
│       │                │                  ▼                  │              │
│       │                │         ┌─────────────────────┐    │              │
│       │                │         │ APP_PAY_SIGN_REQUEST│    │              │
│       │                │         │   (流水表)           │    │              │
│       │                │         │                     │    │              │
│       │                │         │  INSERT 新记录       │    │              │
│       │                │         │  SIGN_STATUS=SIGNING │    │              │
│       │                │         │  记录请求报文+响应    │    │              │
│       │                │         └─────────────────────┘    │              │
│       │                │                  │                  │              │
│       │◀───────────────│◀─────────────────│                  │              │
│       │ 5.返回SDK参数   │ 返回结果          │                  │              │
│       │                │                  │                  │              │
└───────┴────────────────┴──────────────────┴──────────────────┴──────────────┘
```

### 2. 签约成功回调流程 (receiveSignResult)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    签约成功回调流程                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐                      │
│  │  支付平台  │───▶│ fep-app-server│───▶│ pay-sign-server│                      │
│  └──────────┘    └──────────────┘    └──────────────┘                      │
│       │                │                  │                                │
│       │ 1.异步回调      │ 2.透传           │ 3.处理回调                      │
│       │ 签约结果        │ receiveSignResult│ Base64解码bizData              │
│       │                │                  │ 解析签约结果                     │
│       │                │                  │                                │
│       │                │                  ▼                                │
│       │                │         ┌─────────────────────┐                  │
│       │                │         │  APP_PAY_SIGN_INFO  │                  │
│       │                │         │   (签约成功记录表)   │                  │
│       │                │         │                     │                  │
│       │                │         │  INSERT 新记录       │                  │
│       │                │         │  状态=SIGNED        │                  │
│       │                │         │  记录:              │                  │
│       │                │         │  - PAY_ACCOUNT_ID   │                  │
│       │                │         │  - PAY_AGREEMENT_NO │                  │
│       │                │         │  - SIGN_TIME        │                  │
│       │                │         │  - REQUEST_SIGN_SEQ │                  │
│       │                │         └─────────────────────┘                  │
│       │                │                  │                                │
│       │                │                  ▼                                │
│       │                │         ┌─────────────────────┐                  │
│       │                │         │ APP_PAY_SIGN_REQUEST│                  │
│       │                │         │   (流水表)           │                  │
│       │                │         │                     │                  │
│       │                │         │  INSERT 新记录       │                  │
│       │                │         │  SIGN_STATUS=SIGNED │                  │
│       │                │         │  记录回调报文        │                  │
│       │                │         └─────────────────────┘                  │
│       │                │                  │                                │
│       │◀───────────────│◀─────────────────│ 返回处理结果                    │
│       │ 4.返回成功      │ 返回结果          │                                │
│       │                │                  │                                │
└───────┴────────────────┴──────────────────┴────────────────────────────────┘
```

### 3. 签约失败处理流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    签约失败处理流程                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐                      │
│  │  支付平台  │───▶│ fep-app-server│───▶│ pay-sign-server│                      │
│  └──────────┘    └──────────────┘    └──────────────┘                      │
│       │                │                  │                                │
│       │ 1.异步回调      │ 2.透传           │ 3.处理回调                      │
│       │ 签约失败        │ receiveSignResult│ Base64解码bizData              │
│       │                │                  │ 解析失败原因                     │
│       │                │                  │                                │
│       │                │                  ▼                                │
│       │                │         ┌─────────────────────┐                  │
│       │                │         │  APP_PAY_SIGN_INFO  │                  │
│       │                │         │   (签约成功记录表)   │                  │
│       │                │         │                     │                  │
│       │                │         │  不操作（无记录）    │                  │
│       │                │         │  失败不写入          │                  │
│       │                │         └─────────────────────┘                  │
│       │                │                  │                                │
│       │                │                  ▼                                │
│       │                │         ┌─────────────────────┐                  │
│       │                │         │ APP_PAY_SIGN_REQUEST│                  │
│       │                │         │   (流水表)           │                  │
│       │                │         │                     │                  │
│       │                │         │  INSERT 新记录       │                  │
│       │                │         │  SIGN_STATUS=失败    │                  │
│       │                │         │  记录失败原因        │                  │
│       │                │         └─────────────────────┘                  │
│       │                │                  │                                │
│       │◀───────────────│◀─────────────────│ 返回失败响应                    │
│       │ 4.返回失败原因  │ 返回结果          │                                │
│       │                │                  │                                │
└───────┴────────────────┴──────────────────┴────────────────────────────────┘
```

### 4. 请求解约流程 (requestTermination)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      请求解约流程 (IF8A-75)                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ │
│  │  APP/前端 │───▶│ fep-app-server│───▶│ pay-sign-server│───▶│  支付平台     │ │
│  └──────────┘    └──────────────┘    └──────────────┘    └──────────────┘ │
│       │                │                  │                  │             │
│       │ 1.请求解约      │ 2.RPC转发        │ 3.参数处理        │ 4.调用解约接口 │
│       │ requestTermination│ requestTermination│ normalizeVendor  │ termination   │
│       │                │                  │ (原样返回)        │              │
│       │                │                  │                  │              │
│       │                │                  │◀─────────────────│ 返回解约结果   │
│       │                │                  │                  │              │
│       │                │                  ▼                  │              │
│       │                │         ┌─────────────────────┐    │              │
│       │                │         │  APP_PAY_SIGN_INFO  │    │              │
│       │                │         │   (签约成功记录表)   │    │              │
│       │                │         │                     │    │              │
│       │                │         │  不操作（保持原记录）│    │              │
│       │                │         │  不解约成功不改      │    │              │
│       │                │         └─────────────────────┘    │              │
│       │                │                  │                  │              │
│       │                │                  ▼                  │              │
│       │                │         ┌─────────────────────┐    │              │
│       │                │         │ APP_PAY_SIGN_REQUEST│    │              │
│       │                │         │   (流水表)           │    │              │
│       │                │         │                     │    │              │
│       │                │         │  INSERT 新记录       │    │              │
│       │                │         │  SIGN_STATUS=解约中  │    │              │
│       │                │         │  记录请求+响应       │    │              │
│       │                │         └─────────────────────┘    │              │
│       │                │                  │                  │              │
│       │◀───────────────│◀─────────────────│                  │              │
│       │ 5.返回解约结果  │ 返回结果          │                  │              │
│       │                │                  │                  │              │
└───────┴────────────────┴──────────────────┴──────────────────┴──────────────┘
```

### 5. 解约成功回调流程 (receiveTerminationResult)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    解约成功回调流程                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐                      │
│  │  支付平台  │───▶│ fep-app-server│───▶│ pay-sign-server│                      │
│  └──────────┘    └──────────────┘    └──────────────┘                      │
│       │                │                  │                                │
│       │ 1.异步回调      │ 2.透传           │ 3.处理回调                      │
│       │ 解约结果        │ receiveTermination│ 解析解约结果                     │
│       │                │                  │                                │
│       │                │                  ▼                                │
│       │                │         ┌─────────────────────┐                  │
│       │                │         │  APP_PAY_SIGN_INFO  │                  │
│       │                │         │   (签约成功记录表)   │                  │
│       │                │         │                     │                  │
│       │                │         │  DELETE 记录         │                  │
│       │                │         │  彻底删除用户签约记录│                  │
│       │                │         └─────────────────────┘                  │
│       │                │                  │                                │
│       │                │                  ▼                                │
│       │                │         ┌─────────────────────┐                  │
│       │                │         │ APP_PAY_SIGN_REQUEST│                  │
│       │                │         │   (流水表)           │                  │
│       │                │         │                     │                  │
│       │                │         │  INSERT 新记录       │                  │
│       │                │         │  SIGN_STATUS=UNSIGNED│                 │
│       │                │         │  记录回调报文        │                  │
│       │                │         └─────────────────────┘                  │
│       │                │                  │                                │
│       │◀───────────────│◀─────────────────│ 返回处理结果                    │
│       │ 4.返回成功      │ 返回结果          │                                │
│       │                │                  │                                │
└───────┴────────────────┴──────────────────┴────────────────────────────────┘
```

### 6. 状态流转总图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         签约成功记录表状态流转                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   APP_PAY_SIGN_INFO (签约成功记录表)                                         │
│   每个用户 + 每个支付渠道 = 一条记录（仅签约成功时存在）                        │
│                                                                             │
│   不存在记录 ──────签约成功──────▶ 存在记录(SIGNED)                           │
│        ▲                            │                                       │
│        │                            │                                       │
│        └────────解约成功───────────┘                                        │
│              (DELETE记录)                                                   │
│                                                                             │
│   说明:                                                                     │
│   - 签约成功记录表只记录签约成功的用户                                        │
│   - 请求签约时不操作此表（只做存在性校验）                                     │
│   - 签约失败不写入此表                                                       │
│   - 请求解约时不操作此表                                                     │
│   - 解约成功时删除记录                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        流水表记录示例                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   同一用户同一渠道的多笔流水记录:                                             │
│                                                                             │
│   REQUEST_SIGN_SEQ │ OPERATION_TYPE      │ SIGN_STATUS │ CREATE_TMS        │
│   ─────────────────┼─────────────────────┼─────────────┼───────────────────│
│   001              │ REQUEST_SIGN_INFO   │ SIGNING     │ 2026-06-04 10:00  │
│   001              │ RECEIVE_SIGN_RESULT │ SIGNED      │ 2026-06-04 10:05  │
│   002              │ REQUEST_SIGN_INFO   │ 重复签约     │ 2026-06-04 11:00  │
│   003              │ REQUEST_TERMINATION │ 解约中       │ 2026-06-04 14:00  │
│   003              │ RECEIVE_TERMINATION │ UNSIGNED    │ 2026-06-04 14:05  │
│   004              │ REQUEST_SIGN_INFO   │ SIGNING     │ 2026-06-04 15:00  │
│   004              │ RECEIVE_SIGN_RESULT │ SIGNED      │ 2026-06-04 15:03  │
│                                                                             │
│   签约成功记录表最终状态:                                                     │
│   THIRD_USER_ID │ PAYMENT_VENDOR │ SIGN_STATUS │ REQUEST_SIGN_SEQ         │
│   ──────────────┼────────────────┼─────────────┼──────────────────────────│
│   user001       │ 03             │ SIGNED      │ 004                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

表职责说明:
┌──────────────────────────┬──────────────────────────┬─────────────────────┐
│        表名               │         职责              │       操作类型       │
├──────────────────────────┼──────────────────────────┼─────────────────────┤
│ APP\_PAY\_SIGN\_INFO        │ 签约成功记录表            │ INSERT / DELETE     │
│                          │ 只记录签约成功的用户       │                      │
│                          │ 请求签约时不操作          │                      │
│                          │ 解约成功时删除            │                      │
├──────────────────────────┼──────────────────────────┼─────────────────────┤
│ APP\_PAY\_SIGN\_REQUEST     │ 签约请求流水表            │ INSERT              │
│                          │ 每次请求一条记录          │                      │
│                          │ 保留完整历史              │                      │
│                          │ 包含中间状态 SIGNING等    │                      │
├──────────────────────────┼──────────────────────────┼─────────────────────┤
│ APP\_PAY\_SIGN\_LOG         │ 原操作日志表(废弃)        │ UPSERT (覆盖)        │
│                          │ 主键设计有问题            │                      │
└──────────────────────────┴──────────────────────────┴─────────────────────┘

## 摘要

经与用户确认，`APP_PAY_SIGN_INFO` 的定位应为**签约成功记录表**（只记录签约成功的用户，解约成功后删除），而非用户状态表。这要求：

* **保留** **`(THIRD_USER_ID, PAYMENT_VENDOR)`** **唯一约束**：确保每个用户每个渠道只有一条签约记录

* **请求签约时不操作 APP\_PAY\_SIGN\_INFO**：只做存在性校验，已存在则返回重复签约提示

* **签约成功时 INSERT APP\_PAY\_SIGN\_INFO**：回调成功后才写入

* **签约失败时不写入 APP\_PAY\_SIGN\_INFO**：只在流水表记录失败

* **请求解约时不操作 APP\_PAY\_SIGN\_INFO**：保持原记录不变

* **解约成功时 DELETE APP\_PAY\_SIGN\_INFO**：彻底删除记录

* **新增独立的签约请求流水表**：记录每次签约/解约请求的完整历史

## 当前状态分析

### 现有表结构问题

```sql
-- 当前唯一约束（应保留）
CONSTRAINT "UK_APP_PAY_SIGN_INFO_USER_VENDOR" 
UNIQUE ("THIRD_USER_ID", "PAYMENT_VENDOR")
```

**问题场景：**

1. 用户 A 第一次请求签约邮储数币（0802），`REQUEST_SIGN_SEQ=001`，入库成功
2. 用户 A 签约失败，再次请求签约邮储数币，`REQUEST_SIGN_SEQ=002`，因 `(A, 0802)` 已存在，触发唯一约束冲突
3. 用户 A 解约后再次签约，同样因唯一约束无法插入新记录

### 现有 MERGE 语句

```sql
MERGE INTO APP_PAY_SIGN_INFO t
USING (...) s
ON (t.REQUEST_SIGN_SEQ = s.REQUEST_SIGN_SEQ AND t.PAYMENT_VENDOR = s.PAYMENT_VENDOR)
```

**问题：** 匹配条件与唯一约束不一致。MERGE 按 `REQUEST_SIGN_SEQ + PAYMENT_VENDOR` 匹配，但唯一约束是 `THIRD_USER_ID + PAYMENT_VENDOR`。当同一用户同一渠道使用不同 `REQUEST_SIGN_SEQ` 时，MERGE 会尝试 INSERT，触发唯一约束冲突。

### 业务正确语义（签约成功记录表定位）

| 场景             | 期望行为                                   |
| -------------- | -------------------------------------- |
| 同一用户同一渠道重复请求签约 | 查询 APP\_PAY\_SIGN\_INFO，已存在则返回"重复签约"提示 |
| 不同用户或不同渠道      | 继续流程，签约成功后才 INSERT                     |
| 回调更新签约结果       | 成功则 INSERT，失败则不操作                      |
| 请求解约           | 不操作 APP\_PAY\_SIGN\_INFO               |
| 解约成功回调         | DELETE APP\_PAY\_SIGN\_INFO 记录         |
| 查询用户某渠道签约状态    | 查询 APP\_PAY\_SIGN\_INFO 是否存在           |

**状态设计原则：**

* 签约成功记录表只记录签约成功的用户：`SIGNED`

* 请求签约时不写入此表，只做存在性校验

* 签约失败不写入此表

* 请求解约时不操作此表

* 解约成功时删除记录

* 所有中间状态只在流水表记录

### 职责分析

**APP\_PAY\_SIGN\_INFO（签约成功记录表）**

* 职责：记录**签约成功的用户**，每个用户+每个渠道一条记录

* 唯一性：THIRD\_USER\_ID + PAYMENT\_VENDOR

* 字段特点：SIGN\_STATUS、SIGN\_TIME、PAY\_ACCOUNT\_ID、PAY\_AGREEMENT\_NO 等

* 操作类型：INSERT（签约成功）、DELETE（解约成功）、SELECT（校验存在性）

**APP\_PAY\_SIGN\_LOG（操作日志表）**

* 当前问题：主键为 `REQUEST_SIGN_SEQ`，同一流水号多次操作会覆盖历史日志

* 改造方向：改为每次 INSERT，保留完整操作历史

**缺失：签约请求流水表（新增）**

* 职责：记录**每次签约/解约请求的完整历史**

* 唯一性：REQUEST\_SIGN\_SEQ

* 字段特点：请求参数、响应结果、操作时间、操作类型、当前状态

## 拟议变更

### 变更 1: 修改 requestSignInfo 逻辑，增加重复签约校验

**文件:** [PaySignServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java)

**变更内容:**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request) {
    RequestSignInfoResult response = new RequestSignInfoResult();
    try {
        // ... 参数校验 ...
        
        String paymentVendor = normalizeVendor(request.getPayChannelCode());
        
        // 1. 校验是否已签约
        PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
        if (existingSign != null) {
            fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
            writeLog("REQUEST_SIGN_INFO", request.getThirdUserId(), request.getRequestSignSeq(), 
                     paymentVendor, request, response, STATUS_SIGNED);
            return response;
        }
        
        // 2. 调用支付平台获取SDK参数
        // ... 现有逻辑 ...
        
        // 3. 不操作 APP_PAY_SIGN_INFO，只记录流水
        writeLog("REQUEST_SIGN_INFO", request.getThirdUserId(), request.getRequestSignSeq(), 
                 paymentVendor, request, response, STATUS_SIGNING);
        
        fillSuccess(response);
        response.setRequestStartSdkInfo(sdkInfo);
        return response;
    } catch (Exception e) {
        // ... 异常处理 ...
    }
}
```

**原因:** 请求签约时不操作 APP\_PAY\_SIGN\_INFO，只做存在性校验。已签约用户直接返回提示。

### 变更 2: 修改 receiveSignResult 逻辑，成功时 INSERT

**文件:** [PaySignServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java)

**变更内容:**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request) {
    PaySignCallbackResult response = new PaySignCallbackResult();
    try {
        // ... 参数校验 ...
        
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());
        
        if (isSuccess) {
            // 签约成功，INSERT 新记录
            PaySignInfo signInfo = new PaySignInfo();
            signInfo.setRequestSignSeq(request.getRequestSignSeq());
            signInfo.setThirdUserId(request.getThirdUserId());
            signInfo.setPaymentVendor(request.getPaymentVendor());
            signInfo.setDisplayAccount(request.getDisplayAccount());
            signInfo.setPayAccountId(request.getPayUserId());
            signInfo.setPayAgreementNo(request.getPayAgreementNo());
            signInfo.setContractStatus(STATUS_SIGNED);
            signInfo.setSignTime(parseDateTime(request.getSignTime(), null));
            paySignInfoMapper.insert(signInfo);
            
            writeLog("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), 
                     request.getPaymentVendor(), request, response, STATUS_SIGNED);
        } else {
            // 签约失败，不操作 APP_PAY_SIGN_INFO，只记录流水
            writeLog("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), 
                     request.getPaymentVendor(), request, response, STATUS_FAILED);
            fillError(response, PaySignErrorCodeEnum.SIGN_FAILED, "签约失败: " + request.getStatus());
            return response;
        }
        
        fillSuccess(response);
        return response;
    } catch (Exception e) {
        // ... 异常处理 ...
    }
}
```

**原因:** 签约成功时才 INSERT 记录，失败时不操作 APP\_PAY\_SIGN\_INFO。

### 变更 3: 修改 requestTermination 逻辑，不操作 APP\_PAY\_SIGN\_INFO

**文件:** [PaySignServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java)

**变更内容:**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request) {
    RequestTerminationRespDTO response = new RequestTerminationRespDTO();
    try {
        // ... 参数校验 ...
        
        // 1. 查询是否已签约（只做校验，不修改）
        PaySignInfo signInfo = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
        if (signInfo == null) {
            fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "用户未签约");
            writeLog("REQUEST_TERMINATION", request.getThirdUserId(), request.getRequestSignSeq(), 
                     request.getPaymentVendor(), request, response, null);
            return response;
        }
        
        // 2. 调用支付平台解约接口
        // ... 现有逻辑 ...
        
        // 3. 不操作 APP_PAY_SIGN_INFO，只记录流水
        writeLog("REQUEST_TERMINATION", request.getThirdUserId(), request.getRequestSignSeq(), 
                 request.getPaymentVendor(), request, response, STATUS_TERMINATING);
        
        fillSuccess(response);
        return response;
    } catch (Exception e) {
        // ... 异常处理 ...
    }
}
```

**原因:** 请求解约时不操作 APP\_PAY\_SIGN\_INFO，保持原记录不变。

### 变更 4: 修改 receiveTerminationResult 逻辑，成功时 DELETE

**文件:** [PaySignServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java)

**变更内容:**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request) {
    BaseRespDTO response = new BaseRespDTO();
    try {
        // ... 参数校验 ...
        
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());
        
        if (isSuccess) {
            // 解约成功，DELETE 记录
            paySignInfoMapper.deleteByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
            
            // 清理账户支付通道
            removeAccountPayChannel(request.getThirdUserId(), request.getPaymentVendor());
            
            writeLog("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), 
                     request.getPaymentVendor(), request, response, STATUS_UNSIGNED);
        } else {
            // 解约失败，不操作 APP_PAY_SIGN_INFO
            writeLog("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), 
                     request.getPaymentVendor(), request, response, STATUS_TERMINATE_FAILED);
        }
        
        fillSuccess(response);
        return response;
    } catch (Exception e) {
        // ... 异常处理 ...
    }
}
```

**原因:** 解约成功时彻底删除记录，而非更新状态。

### 变更 5: 新增 deleteByUserAndVendor 方法

**文件:** [PaySignInfoMapper.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignInfoMapper.java)

**变更内容:**

```java
int deleteByUserAndVendor(@Param("thirdUserId") String thirdUserId, 
                           @Param("paymentVendor") String paymentVendor);
```

**文件:** [PaySignInfoMapper.xml](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml)

**变更内容:**

```xml
<delete id="deleteByUserAndVendor">
    DELETE FROM APP_PAY_SIGN_INFO
    WHERE THIRD_USER_ID = #{thirdUserId,jdbcType=VARCHAR}
      AND PAYMENT_VENDOR = #{paymentVendor,jdbcType=VARCHAR}
</delete>
```

### 变更 6: 废弃 MERGE，改为 INSERT/DELETE

**文件:** [PaySignInfoMapper.xml](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml)

**变更内容:**

```xml
<!-- 废弃原有的 upsert/MERGE -->
<!-- 新增纯 INSERT -->
<insert id="insert" parameterType="com.chinasofti.huateng.paysign.entity.PaySignInfo">
    INSERT INTO APP_PAY_SIGN_INFO (
        REQUEST_SIGN_SEQ, THIRD_USER_ID, SIGN_STATUS, PAYMENT_VENDOR,
        CARD_ID, CARD_TYPE, PAY_ACCOUNT_ID, PAY_AGREEMENT_NO, DISPLAY_ACCOUNT,
        SIGN_TIME, TERMINATION_TIME
    ) VALUES (
        #{requestSignSeq,jdbcType=VARCHAR},
        #{thirdUserId,jdbcType=VARCHAR},
        #{contractStatus,jdbcType=VARCHAR},
        #{paymentVendor,jdbcType=VARCHAR},
        #{cardId,jdbcType=VARCHAR},
        #{cardType,jdbcType=VARCHAR},
        #{payAccountId,jdbcType=VARCHAR},
        #{payAgreementNo,jdbcType=VARCHAR},
        #{displayAccount,jdbcType=VARCHAR},
        #{signTime,jdbcType=TIMESTAMP},
        #{terminationTime,jdbcType=TIMESTAMP}
    )
</insert>
```

### 变更 7: 新增支付渠道枚举 PaymentVendorEnum

**文件:** [PaymentVendorEnum.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/constant/PaymentVendorEnum.java)

**变更内容:**

```java
package com.chinasofti.huateng.paysign.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付渠道枚举
 * 定义所有支持的支付渠道编码和名称
 */
@Getter
@RequiredArgsConstructor
public enum PaymentVendorEnum {

    ALIPAY("03", "支付宝"),
    WECHAT("04", "微信"),
    ALIPAY_TRAVEL("05", "支付宝出行"),
    LONG_PAY("06", "龙支付"),
    CMB_BANK("0601", "招商银行"),
    BOC_BANK("0602", "中国银行"),
    CBDC_CONSTRUCTION("08", "建行数币"),
    CBDC_BOC("0801", "中行数币"),
    CBDC_PSBC("0802", "邮储数币"),
    CBDC_COMM("0803", "交行数币"),
    WALLET("0B", "钱包"),
    CBDC_APP("0C", "数币APP");

    private final String code;
    private final String name;

    // 静态缓存，提高查询性能
    private static final Map<String, PaymentVendorEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(PaymentVendorEnum::getCode, e -> e));

    /**
     * 根据编码获取枚举
     */
    public static PaymentVendorEnum fromCode(String code) {
        return code == null ? null : CODE_MAP.get(code.trim());
    }

    /**
     * 判断编码是否有效
     */
    public static boolean isValid(String code) {
        return code != null && CODE_MAP.containsKey(code.trim());
    }

    /**
     * 获取所有编码字符串，用于日志或配置
     */
    public static String allCodes() {
        return CODE_MAP.keySet().toString();
    }
}
```

**原因:**

1. **类型安全**：编译时检查，避免传入非法渠道编码
2. **集中管理**：所有渠道定义在一个枚举中，便于维护
3. **性能优化**：使用静态缓存 `CODE_MAP`，O(1) 时间复杂度查询
4. **可读性**：`PaymentVendorEnum.ALIPAY.getCode()` 比硬编码 `"03"` 更清晰
5. **扩展性**：新增渠道只需添加枚举常量，无需修改多处代码

**使用示例：**

```java
// 校验渠道编码是否有效
if (!PaymentVendorEnum.isValid(payChannelCode)) {
    return "不支持的支付渠道";
}

// 获取枚举信息
PaymentVendorEnum vendor = PaymentVendorEnum.fromCode(payChannelCode);
String vendorName = vendor.getName(); // "支付宝"

// 用于日志
log.info("当前渠道: {}({})", vendor.getName(), vendor.getCode());
```

**修改 normalizeVendor 方法：**

**文件:** [PaySignServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java)

```java
private String normalizeVendor(String payChannelCode) {
    if (!StringUtils.hasText(payChannelCode)) {
        return null;
    }
    String code = payChannelCode.trim();
    
    // 使用枚举校验渠道编码有效性
    if (!PaymentVendorEnum.isValid(code)) {
        log.warn("未知的支付渠道编码: {}", code);
        // 可选择返回null或原样返回，根据业务需求决定
        return code; // 或 return null;
    }
    
    return code;
}
```

**数据库存储规范:**

| 层级          | 存储/使用值                               | 示例                            |
| ----------- | ------------------------------------ | ----------------------------- |
| **数据库**     | 枚举的 `code` 字段                        | `03`、`04`、`0802`、`0B`         |
| **Java 枚举** | `PaymentVendorEnum.ALIPAY.getCode()` | `"03"`                        |
| **业务代码**    | 直接使用字符串编码                            | `normalizeVendor()` 返回 `"03"` |

**规范说明:**

* ✅ 数据库存储的是枚举 `code`（如 `"0802"`），不是枚举名称（如 `"CBDC_PSBC"`）

* ✅ 不是存储中文名称（如 `"邮储数币"`）

* ✅ 数据库与枚举通过 `code` 关联，解耦存储与业务逻辑

* ✅ 枚举变更（如改名）不影响历史数据

* ✅ 支付平台接口直接传递 `code`，无需转换

**影响:**

* 数据库中 `PAYMENT_VENDOR` 字段存储枚举 code（如 `03`、`0802`）

* 所有渠道编码集中管理，避免硬编码散落在各处

* 新增渠道只需修改枚举类，无需修改业务逻辑

* 需要确认支付平台是否支持这些编码

### 变更 8: 新增 selectByUserAndVendor 查询方法

**文件:** [PaySignInfoMapper.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignInfoMapper.java)

**变更内容:**

```java
PaySignInfo selectByUserAndVendor(@Param("thirdUserId") String thirdUserId, 
                                   @Param("paymentVendor") String paymentVendor);
```

**文件:** [PaySignInfoMapper.xml](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml)

**变更内容:**

```xml
<select id="selectByUserAndVendor" resultMap="BaseResultMap">
    select <include refid="Base_Column_List"/>
    from APP_PAY_SIGN_INFO
    where THIRD_USER_ID = #{thirdUserId,jdbcType=VARCHAR}
      and PAYMENT_VENDOR = #{paymentVendor,jdbcType=VARCHAR}
</select>
```

### 变更 9: 新增签约请求流水表 APP\_PAY\_SIGN\_REQUEST

**数据库变更（需 DBA 执行）:**

```sql
CREATE TABLE "QDITP"."APP_PAY_SIGN_REQUEST" (
    "REQUEST_SIGN_SEQ" VARCHAR2(64 CHAR) NOT NULL ENABLE,
    "THIRD_USER_ID" VARCHAR2(64 CHAR) NOT NULL ENABLE,
    "PAYMENT_VENDOR" VARCHAR2(16 CHAR),
    "DISPLAY_ACCOUNT" VARCHAR2(128 CHAR),
    "OPERATION_TYPE" VARCHAR2(64 CHAR) NOT NULL ENABLE,
    "REQUEST_BODY" VARCHAR2(4000 CHAR),
    "RESPONSE_BODY" VARCHAR2(4000 CHAR),
    "RESULT_CODE" VARCHAR2(32 CHAR),
    "RESULT_MSG" VARCHAR2(512 CHAR),
    "SIGN_STATUS" VARCHAR2(16 CHAR),
    "CREATE_TMS" TIMESTAMP (6) NOT NULL ENABLE,
    -- 通知相关字段
    "NOTIFY_STATUS" VARCHAR2(16 CHAR) DEFAULT 'PENDING',
    "NOTIFY_RETRY_COUNT" NUMBER(3,0) DEFAULT 0,
    "NOTIFY_TIME" TIMESTAMP (6),
    "NOTIFY_RESULT" VARCHAR2(512 CHAR),
    CONSTRAINT "PK_APP_PAY_SIGN_REQUEST" PRIMARY KEY ("REQUEST_SIGN_SEQ")
);

COMMENT ON TABLE QDITP.APP_PAY_SIGN_REQUEST IS '签约请求流水表';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.REQUEST_SIGN_SEQ IS '签约请求流水号，主键';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.THIRD_USER_ID IS '第三方用户ID';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.PAYMENT_VENDOR IS '支付类型/支付厂商编码';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.OPERATION_TYPE IS '操作类型：REQUEST_SIGN_INFO/REQUEST_CONTRACT_ADVISORY/REQUEST_CONTRACT_RESULT/REQUEST_TERMINATION/RECEIVE_SIGN_RESULT/RECEIVE_TERMINATION_RESULT';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.REQUEST_BODY IS '请求报文JSON';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.RESPONSE_BODY IS '响应报文JSON';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.RESULT_CODE IS '结果码';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.RESULT_MSG IS '结果消息';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.SIGN_STATUS IS '签约状态快照';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.CREATE_TMS IS '创建时间';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_STATUS IS '通知状态: PENDING-待通知/SUCCESS-成功/FAILED-失败';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_RETRY_COUNT IS '通知重试次数';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_TIME IS '最后通知时间';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_RESULT IS '通知结果描述';

CREATE INDEX "QDITP"."IDX_APP_PAY_SIGN_REQUEST_USER_ID" ON "QDITP"."APP_PAY_SIGN_REQUEST" ("THIRD_USER_ID");
CREATE INDEX "QDITP"."IDX_APP_PAY_SIGN_REQUEST_USER_VENDOR" ON "QDITP"."APP_PAY_SIGN_REQUEST" ("THIRD_USER_ID", "PAYMENT_VENDOR");
CREATE INDEX "QDITP"."IDX_APP_PAY_SIGN_REQUEST_NOTIFY" ON "QDITP"."APP_PAY_SIGN_REQUEST" ("NOTIFY_STATUS", "NOTIFY_RETRY_COUNT");
```

**新增实体类:** [PaySignRequest.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PaySignRequest.java)

```java
package com.chinasofti.huateng.paysign.entity;

import java.time.LocalDateTime;

public class PaySignRequest {
    private String requestSignSeq;
    private String thirdUserId;
    private String paymentVendor;
    private String operationType;
    private String requestBody;
    private String responseBody;
    private String resultCode;
    private String resultMsg;
    private String signStatus;
    private LocalDateTime createTms;
    // 通知相关字段
    private String notifyStatus;      // 通知状态: PENDING/SUCCESS/FAILED
    private Integer notifyRetryCount; // 通知重试次数
    private LocalDateTime notifyTime; // 最后通知时间
    private String notifyResult;      // 通知结果描述
    // getters and setters
}
```

**新增 Mapper 接口:** [PaySignRequestMapper.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignRequestMapper.java)

```java
package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaySignRequestMapper {
    int insert(PaySignRequest record);
}
```

**新增 Mapper XML:** [PaySignRequestMapper.xml](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/resources/mapper/PaySignRequestMapper.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper">
    <insert id="insert" parameterType="com.chinasofti.huateng.paysign.entity.PaySignRequest">
        INSERT INTO APP_PAY_SIGN_REQUEST (
            REQUEST_SIGN_SEQ, THIRD_USER_ID, PAYMENT_VENDOR, OPERATION_TYPE,
            REQUEST_BODY, RESPONSE_BODY, RESULT_CODE, RESULT_MSG, SIGN_STATUS, CREATE_TMS
        ) VALUES (
            #{requestSignSeq,jdbcType=VARCHAR},
            #{thirdUserId,jdbcType=VARCHAR},
            #{paymentVendor,jdbcType=VARCHAR},
            #{operationType,jdbcType=VARCHAR},
            #{requestBody,jdbcType=VARCHAR},
            #{responseBody,jdbcType=VARCHAR},
            #{resultCode,jdbcType=VARCHAR},
            #{resultMsg,jdbcType=VARCHAR},
            #{signStatus,jdbcType=VARCHAR},
            #{createTms,jdbcType=TIMESTAMP}
        )
    </insert>
</mapper>
```

### 变更 10: 修改 writeLog 方法，改为 INSERT 到流水表

**文件:** [PaySignServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java)

**变更内容:**

```java
@Autowired
private PaySignRequestMapper paySignRequestMapper;

private void writeLog(String operationType, String thirdUserId, String requestSignSeq,
                      String paymentVendor, Object request, Object response, String signStatus) {
    if (!StringUtils.hasText(requestSignSeq)) {
        return;
    }
    PaySignRequest logRecord = new PaySignRequest();
    logRecord.setRequestSignSeq(requestSignSeq);
    logRecord.setThirdUserId(thirdUserId);
    logRecord.setPaymentVendor(paymentVendor);
    logRecord.setOperationType(convertOperationType(operationType));
    logRecord.setRequestBody(request == null ? null : JSON.toJSONString(request));
    logRecord.setResponseBody(response == null ? null : JSON.toJSONString(response));
    if (response instanceof BaseRespDTO baseRespDTO) {
        logRecord.setResultCode(baseRespDTO.getRetCode());
        logRecord.setResultMsg(baseRespDTO.getRetMsg());
    }
    logRecord.setSignStatus(signStatus);
    logRecord.setCreateTms(LocalDateTime.now());
    paySignRequestMapper.insert(logRecord);
}
```

**原因:** 每次操作都 INSERT 新记录到流水表，保留完整历史。原 `APP_PAY_SIGN_LOG` 表可废弃或保留用于其他用途。

### 变更 11: 废弃原 APP\_PAY\_SIGN\_LOG 的 upsert 逻辑

**文件:** [PaySignLogMapper.xml](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/resources/mapper/PaySignLogMapper.xml)

**方案:** 保留原表和 Mapper 不变，但 `writeLog` 方法不再调用 `PaySignLogMapper.upsert`，改为调用新的 `PaySignRequestMapper.insert`。

### 变更 12: 新增签约结果通知App功能

**背景:** 当前 `receiveSignResult` 和 `receiveTerminationResult` 回调中，通知App是同步调用，失败会影响支付平台回调响应。需要解耦通知流程，确保签约/解约状态更新与App通知分离。

**方案:** 在 `APP_PAY_SIGN_REQUEST` 流水表中增加通知状态字段，采用"异步通知+异步补偿"的简化策略。

**通知流程:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    签约结果通知App流程（异步通知+异步补偿）                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. receiveSignResult 收到支付平台回调                                        │
│       │                                                                     │
│       ▼                                                                     │
│  2. 更新 APP_PAY_SIGN_INFO（INSERT 签约成功记录）                             │
│       │                                                                     │
│       ▼                                                                     │
│  3. 写入 APP_PAY_SIGN_REQUEST 流水表（SIGN_STATUS=SIGNED）                    │
│       │  NOTIFY_STATUS=PENDING（初始状态）                                    │
│       │                                                                     │
│       ▼                                                                     │
│  4. 立即返回成功给支付平台（不等待通知结果）                                   │
│       │                                                                     │
│       ▼                                                                     │
│  5. 异步通知App（新线程/线程池）                                              │
│       │  调用 app.notify.sign-result-url                                      │
│       │                                                                     │
│       ├── 成功 ──▶ 更新 NOTIFY_STATUS=SUCCESS                                 │
│       │              NOTIFY_TIME=当前时间                                     │
│       │              NOTIFY_RESULT="通知成功"                                  │
│       │                                                                     │
│       └── 失败 ──▶ 更新 NOTIFY_STATUS=FAILED                                  │
│                      NOTIFY_RETRY_COUNT=1                                   │
│                      NOTIFY_TIME=当前时间                                     │
│                      NOTIFY_RESULT="通知失败: {原因}"                         │
│                                                                             │
│  6. 定时任务补偿（每5分钟执行）                                               │
│       │  查询 NOTIFY_STATUS=FAILED AND NOTIFY_RETRY_COUNT < 3                │
│       │                                                                     │
│       ├── 异步重试通知App                                                     │
│       ├── 成功 ──▶ 更新 NOTIFY_STATUS=SUCCESS                                 │
│       └── 失败 ──▶ 更新 NOTIFY_RETRY_COUNT+1                                │
│                      超过3次则标记为 FAILED_FINAL                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**新增通知服务:** [AppNotifyService.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/AppNotifyService.java)

```java
package com.chinasofti.huateng.paysign.service;

public interface AppNotifyService {
    /**
     * 异步通知App签约结果
     * @param request 流水记录
     */
    void asyncNotifySignResult(PaySignRequest request);
    
    /**
     * 异步通知App解约结果
     * @param request 流水记录
     */
    void asyncNotifyTerminationResult(PaySignRequest request);
    
    /**
     * 补偿通知（定时任务调用）
     * 重试失败的通知
     */
    void compensateNotify();
}
```

**新增通知服务实现:** [AppNotifyServiceImpl.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/AppNotifyServiceImpl.java)

```java
package com.chinasofti.huateng.paysign.service.impl;

@Service
@Slf4j
public class AppNotifyServiceImpl implements AppNotifyService {
    
    @Autowired
    private PaySignRequestMapper paySignRequestMapper;
    
    @Value("${app.notify.sign-result-url}")
    private String notifySignResultUrl;
    
    @Value("${app.notify.termination-result-url}")
    private String notifyTerminationResultUrl;
    
    // 异步线程池
    private final ExecutorService notifyExecutor = Executors.newFixedThreadPool(4, 
        new ThreadFactoryBuilder().setNameFormat("app-notify-%d").build());
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();
    
    @Override
    public void asyncNotifySignResult(PaySignRequest request) {
        notifyExecutor.submit(() -> {
            try {
                doNotifySignResult(request);
            } catch (Exception e) {
                log.error("异步通知App签约结果异常, requestSignSeq={}", request.getRequestSignSeq(), e);
                updateNotifyStatus(request.getRequestSignSeq(), false, "异步异常:" + e.getMessage());
            }
        });
    }
    
    @Override
    public void asyncNotifyTerminationResult(PaySignRequest request) {
        notifyExecutor.submit(() -> {
            try {
                doNotifyTerminationResult(request);
            } catch (Exception e) {
                log.error("异步通知App解约结果异常, requestSignSeq={}", request.getRequestSignSeq(), e);
                updateNotifyStatus(request.getRequestSignSeq(), false, "异步异常:" + e.getMessage());
            }
        });
    }
    
    private void doNotifySignResult(PaySignRequest request) {
        try {
            Map<String, Object> bizData = new HashMap<>();
            bizData.put("requestSignSeq", request.getRequestSignSeq());
            bizData.put("thirdUserId", request.getThirdUserId());
            bizData.put("paymentVendor", request.getPaymentVendor());
            bizData.put("signStatus", request.getSignStatus());
            bizData.put("notifyTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            
            RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("bizData", JSON.toJSONString(bizData))
                .build();
            
            Request httpRequest = new Request.Builder()
                .url(notifySignResultUrl)
                .post(requestBody)
                .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                boolean success = response.isSuccessful();
                updateNotifyStatus(request.getRequestSignSeq(), success, success ? "通知成功" : "HTTP" + response.code());
            }
        } catch (Exception e) {
            log.error("通知App签约结果异常, requestSignSeq={}", request.getRequestSignSeq(), e);
            updateNotifyStatus(request.getRequestSignSeq(), false, "异常:" + e.getMessage());
        }
    }
    
    private void doNotifyTerminationResult(PaySignRequest request) {
        // 类似实现，调用 notifyTerminationResultUrl
        // ...
    }
    
    @Override
    @Scheduled(fixedDelay = 300000) // 每5分钟执行一次
    public void compensateNotify() {
        log.info("开始执行通知补偿任务");
        List<PaySignRequest> failedList = paySignRequestMapper.selectByNotifyStatus("FAILED", 3);
        for (PaySignRequest request : failedList) {
            log.info("补偿通知, requestSignSeq={}, retryCount={}", 
                request.getRequestSignSeq(), request.getNotifyRetryCount());
            
            // 异步重试
            if ("RECEIVE_SIGN_RESULT".equals(request.getOperationType())) {
                notifyExecutor.submit(() -> doNotifySignResult(request));
            } else {
                notifyExecutor.submit(() -> doNotifyTerminationResult(request));
            }
        }
        log.info("通知补偿任务完成, 处理{}条记录", failedList.size());
    }
    
    private void updateNotifyStatus(String requestSignSeq, boolean success, String result) {
        PaySignRequest update = new PaySignRequest();
        update.setRequestSignSeq(requestSignSeq);
        update.setNotifyStatus(success ? "SUCCESS" : "FAILED");
        update.setNotifyTime(LocalDateTime.now());
        update.setNotifyResult(result);
        if (!success) {
            update.setNotifyRetryCount(1);
        }
        paySignRequestMapper.updateNotifyStatus(update);
    }
    
    @PreDestroy
    public void shutdown() {
        notifyExecutor.shutdown();
    }
}
```

**新增 Mapper 方法:** [PaySignRequestMapper.java](file:///d:/workspace/zr/qditp/pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignRequestMapper.java)

```java
// 查询需要补偿的通知
List<PaySignRequest> selectByNotifyStatus(@Param("notifyStatus") String notifyStatus, 
                                           @Param("maxRetry") int maxRetry);

// 更新通知状态
int updateNotifyStatus(PaySignRequest record);

// 增加重试次数
int increaseRetryCount(@Param("requestSignSeq") String requestSignSeq);
```

**新增 Mapper XML:**

```xml
<select id="selectByNotifyStatus" resultMap="BaseResultMap">
    select <include refid="Base_Column_List"/>
    from APP_PAY_SIGN_REQUEST
    where NOTIFY_STATUS = #{notifyStatus,jdbcType=VARCHAR}
      and NOTIFY_RETRY_COUNT < #{maxRetry,jdbcType=INTEGER}
      and OPERATION_TYPE in ('RECEIVE_SIGN_RESULT', 'RECEIVE_TERMINATION_RESULT')
    order by CREATE_TMS
</select>

<update id="updateNotifyStatus" parameterType="com.chinasofti.huateng.paysign.entity.PaySignRequest">
    update APP_PAY_SIGN_REQUEST
    set NOTIFY_STATUS = #{notifyStatus,jdbcType=VARCHAR},
        NOTIFY_TIME = #{notifyTime,jdbcType=TIMESTAMP},
        NOTIFY_RESULT = #{notifyResult,jdbcType=VARCHAR}
        <if test="notifyRetryCount != null">
            , NOTIFY_RETRY_COUNT = #{notifyRetryCount,jdbcType=INTEGER}
        </if>
    where REQUEST_SIGN_SEQ = #{requestSignSeq,jdbcType=VARCHAR}
</update>

<update id="increaseRetryCount">
    update APP_PAY_SIGN_REQUEST
    set NOTIFY_RETRY_COUNT = NOTIFY_RETRY_COUNT + 1,
        NOTIFY_STATUS = 'FAILED',
        NOTIFY_TIME = sysdate
    where REQUEST_SIGN_SEQ = #{requestSignSeq,jdbcType=VARCHAR}
</update>
```

**修改 receiveSignResult 方法:**

在签约成功回调中，更新 APP\_PAY\_SIGN\_INFO 后，写入流水表并触发通知：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request) {
    PaySignCallbackResult response = new PaySignCallbackResult();
    try {
        // ... 参数校验 ...
        
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());
        
        if (isSuccess) {
            // 1. INSERT 签约成功记录
            PaySignInfo signInfo = new PaySignInfo();
            // ... 设置字段 ...
            paySignInfoMapper.insert(signInfo);
            
            // 2. 写入流水表
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(request.getRequestSignSeq());
            logRecord.setThirdUserId(request.getThirdUserId());
            logRecord.setPaymentVendor(request.getPaymentVendor());
            logRecord.setOperationType("RECEIVE_SIGN_RESULT");
            logRecord.setSignStatus(STATUS_SIGNED);
            logRecord.setCreateTms(LocalDateTime.now());
            logRecord.setNotifyStatus("PENDING");
            logRecord.setNotifyRetryCount(0);
            paySignRequestMapper.insert(logRecord);
            
            // 3. 异步通知App（不阻塞返回）
            appNotifyService.asyncNotifySignResult(logRecord);
            
        } else {
            // 签约失败，记录流水但不通知
            writeLog("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), 
                     request.getPaymentVendor(), request, response, STATUS_FAILED);
        }
        
        fillSuccess(response);
        return response;
    } catch (Exception e) {
        // ... 异常处理 ...
    }
}
```

**修改 receiveTerminationResult 方法（类似）:**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request) {
    BaseRespDTO response = new BaseRespDTO();
    try {
        // ... 参数校验 ...
        
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());
        
        if (isSuccess) {
            // 1. DELETE 签约记录
            paySignInfoMapper.deleteByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
            
            // 2. 写入流水表
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(request.getRequestSignSeq());
            logRecord.setThirdUserId(request.getThirdUserId());
            logRecord.setPaymentVendor(request.getPaymentVendor());
            logRecord.setOperationType("RECEIVE_TERMINATION_RESULT");
            logRecord.setSignStatus(STATUS_UNSIGNED);
            logRecord.setCreateTms(LocalDateTime.now());
            logRecord.setNotifyStatus("PENDING");
            logRecord.setNotifyRetryCount(0);
            paySignRequestMapper.insert(logRecord);
            
            // 3. 异步通知App（不阻塞返回）
            appNotifyService.asyncNotifyTerminationResult(logRecord);
        }
        
        fillSuccess(response);
        return response;
    } catch (Exception e) {
        // ... 异常处理 ...
    }
}
```

**配置说明:**

在 `application.properties` 中已存在：

```properties
app.notify.sign-result-url=https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/receiveSignResult
app.notify.termination-result-url=https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/ticket/receiveTerminationResultFromItp
```

**设计原则（简化）:**

1. **不引入MQ**：使用数据库+定时任务，减少外部依赖
2. **异步通知**：首次通知通过线程池异步执行，不阻塞主线程
3. **立即返回**：支付平台回调立即返回成功，不等待通知结果
4. **定时补偿**：每5分钟扫描失败记录，最多重试3次
5. **状态可追溯**：通过流水表字段记录通知全过程

### 变更 13: 升级 pay-sign-server 版本号

**文件:** [pay-sign-server/pom.xml](file:///d:/workspace/zr/qditp/pay-sign-server/pom.xml)

**变更内容:** 版本号升级（当前 `1.4` → `1.5`）。

## 假设与决策

| 决策                                         | 说明                         |
| ------------------------------------------ | -------------------------- |
| APP\_PAY\_SIGN\_INFO 为签约成功记录表              | 只记录签约成功的用户，解约后删除           |
| 保留 (THIRD\_USER\_ID, PAYMENT\_VENDOR) 唯一约束 | 确保每个用户每个渠道只有一条签约记录         |
| 请求签约时不操作 APP\_PAY\_SIGN\_INFO              | 只做存在性校验，已存在则返回重复签约提示       |
| 签约成功时 INSERT APP\_PAY\_SIGN\_INFO          | 回调成功后才写入                   |
| 签约失败时不写入 APP\_PAY\_SIGN\_INFO              | 只在流水表记录失败                  |
| 请求解约时不操作 APP\_PAY\_SIGN\_INFO              | 保持原记录不变                    |
| 解约成功时 DELETE APP\_PAY\_SIGN\_INFO          | 彻底删除记录                     |
| 渠道编码原样返回（不截取映射）                            | 使用 PaymentVendorEnum 校验有效性 |
| 新增 APP\_PAY\_SIGN\_REQUEST 流水表             | 每次操作 INSERT，保留完整历史         |
| 废弃 APP\_PAY\_SIGN\_LOG 的 upsert            | 不再覆盖历史日志                   |

## 数据库变更SQL

### 1. 新增 APP\_PAY\_SIGN\_REQUEST 流水表

```sql
-- 创建签约请求流水表
CREATE TABLE "QDITP"."APP_PAY_SIGN_REQUEST" (
    "ID" NUMBER(19) GENERATED ALWAYS AS IDENTITY NOT NULL ENABLE,
    "REQUEST_SIGN_SEQ" VARCHAR2(64 CHAR) NOT NULL ENABLE,
    "THIRD_USER_ID" VARCHAR2(64 CHAR) NOT NULL ENABLE,
    "PAYMENT_VENDOR" VARCHAR2(16 CHAR),
    "OPERATION_TYPE" VARCHAR2(64 CHAR) NOT NULL ENABLE,
    "REQUEST_BODY" VARCHAR2(4000 CHAR),
    "RESPONSE_BODY" VARCHAR2(4000 CHAR),
    "RESULT_CODE" VARCHAR2(32 CHAR),
    "RESULT_MSG" VARCHAR2(512 CHAR),
    "SIGN_STATUS" VARCHAR2(16 CHAR),
    "CREATE_TMS" TIMESTAMP (6) NOT NULL ENABLE,
    -- 通知相关字段
    "NOTIFY_STATUS" VARCHAR2(16 CHAR) DEFAULT 'PENDING',
    "NOTIFY_RETRY_COUNT" NUMBER(3,0) DEFAULT 0,
    "NOTIFY_TIME" TIMESTAMP (6),
    "NOTIFY_RESULT" VARCHAR2(512 CHAR),
    CONSTRAINT "PK_APP_PAY_SIGN_REQUEST" PRIMARY KEY ("ID")
);

-- 表注释
COMMENT ON TABLE QDITP.APP_PAY_SIGN_REQUEST IS '签约请求流水表';

-- 列注释
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.ID IS '自增主键';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.REQUEST_SIGN_SEQ IS '签约请求流水号';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.THIRD_USER_ID IS '第三方用户ID';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.PAYMENT_VENDOR IS '支付类型/支付厂商编码：03-支付宝、04-微信、05-支付宝出行、06-龙支付、0601-招商银行、0602-中国银行、08-建行数币、0801-中行数币、0802-邮储数币、0803-交行数币、0B-钱包、0C-数币APP';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.DISPLAY_ACCOUNT IS '签约展示账号，用于签约页面展示，如昵称、手机号、姓名';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.OPERATION_TYPE IS '操作类型：REQUEST_SIGN_INFO/REQUEST_CONTRACT_ADVISORY/REQUEST_CONTRACT_RESULT/REQUEST_TERMINATION/RECEIVE_SIGN_RESULT/RECEIVE_TERMINATION_RESULT';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.REQUEST_BODY IS '请求报文JSON';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.RESPONSE_BODY IS '响应报文JSON';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.RESULT_CODE IS '结果码';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.RESULT_MSG IS '结果消息';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.SIGN_STATUS IS '签约状态快照';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.CREATE_TMS IS '创建时间';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_STATUS IS '通知状态: PENDING-待通知/SUCCESS-成功/FAILED-失败';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_RETRY_COUNT IS '通知重试次数';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_TIME IS '最后通知时间';
COMMENT ON COLUMN QDITP.APP_PAY_SIGN_REQUEST.NOTIFY_RESULT IS '通知结果描述';

-- 创建索引
CREATE INDEX "QDITP"."IDX_APP_PAY_SIGN_REQUEST_SEQ" ON "QDITP"."APP_PAY_SIGN_REQUEST" ("REQUEST_SIGN_SEQ");
CREATE INDEX "QDITP"."IDX_APP_PAY_SIGN_REQUEST_USER_ID" ON "QDITP"."APP_PAY_SIGN_REQUEST" ("THIRD_USER_ID");
CREATE INDEX "QDITP"."IDX_APP_PAY_SIGN_REQUEST_USER_VENDOR" ON "QDITP"."APP_PAY_SIGN_REQUEST" ("THIRD_USER_ID", "PAYMENT_VENDOR");
CREATE INDEX "QDITP"."IDX_APP_PAY_SIGN_REQUEST_NOTIFY" ON "QDITP"."APP_PAY_SIGN_REQUEST" ("NOTIFY_STATUS", "NOTIFY_RETRY_COUNT");
```

### 2. 清理 APP\_PAY\_SIGN\_INFO 历史数据（可选）

```sql
-- 如果历史数据中存在同一用户同一渠道多条记录，需要先清理
-- 保留每条最新的记录
DELETE FROM QDITP.APP_PAY_SIGN_INFO
WHERE ROWID NOT IN (
    SELECT MAX(ROWID)
    FROM QDITP.APP_PAY_SIGN_INFO
    GROUP BY THIRD_USER_ID, PAYMENT_VENDOR
);
```

### 3. 验证表结构

```sql
-- 验证表是否创建成功
SELECT TABLE_NAME, COMMENTS FROM USER_TAB_COMMENTS WHERE TABLE_NAME = 'APP_PAY_SIGN_REQUEST';

-- 验证列注释
SELECT COLUMN_NAME, COMMENTS FROM USER_COL_COMMENTS WHERE TABLE_NAME = 'APP_PAY_SIGN_REQUEST';

-- 验证索引
SELECT INDEX_NAME, INDEX_TYPE, TABLE_NAME FROM USER_INDEXES WHERE TABLE_NAME = 'APP_PAY_SIGN_REQUEST';
```

## 验证步骤

1. DBA 执行新增 APP\_PAY\_SIGN\_REQUEST 表
2. 修改后编译 pay-sign-server
3. 第一次调用 requestSignInfo（用户A，渠道03），验证：

   * APP\_PAY\_SIGN\_INFO 无记录（未签约）

   * 返回 SDK 参数

   * APP\_PAY\_SIGN\_REQUEST INSERT 成功，SIGN\_STATUS=SIGNING
4. 第二次调用 requestSignInfo（同一用户A，同一渠道03），验证：

   * APP\_PAY\_SIGN\_INFO 仍无记录（未回调）

   * 返回"重复签约"提示（如果已签约）或继续流程（如果未签约）

   * APP\_PAY\_SIGN\_REQUEST INSERT 成功
5. 签约成功回调，验证：

   * APP\_PAY\_SIGN\_INFO INSERT 成功，状态=SIGNED

   * APP\_PAY\_SIGN\_REQUEST INSERT 成功，SIGN\_STATUS=SIGNED
6. 请求解约，验证：

   * APP\_PAY\_SIGN\_INFO 记录仍存在

   * APP\_PAY\_SIGN\_REQUEST INSERT 成功，SIGN\_STATUS=解约中
7. 解约成功回调，验证：

   * APP\_PAY\_SIGN\_INFO DELETE 成功

   * APP\_PAY\_SIGN\_REQUEST INSERT 成功，SIGN\_STATUS=UNSIGNED
8. 再次请求签约，验证：

   * APP\_PAY\_SIGN\_INFO 无记录，流程继续

## 对其他接口和流程的影响评估

### 接口影响矩阵

| 接口                                    | 当前查询方式                                       | 改造后查询方式                                             | 影响程度  | 说明                       |
| ------------------------------------- | -------------------------------------------- | --------------------------------------------------- | ----- | ------------------------ |
| **requestSignInfo** (IF8A-71)         | `selectBySeq(requestSignSeq, paymentVendor)` | `selectByUserAndVendor(thirdUserId, paymentVendor)` | **高** | 核心变更，增加重复签约校验            |
| **requestContractAdvisory** (IF8A-21) | 不查库，直接调支付平台                                  | 不变                                                  | **无** | 纯透传接口，不受影晌               |
| **requestContractResult** (IF8A-22)   | `selectBySeq(requestSignSeq, paymentVendor)` | `selectByUserAndVendor(thirdUserId, paymentVendor)` | **高** | 需修改查询逻辑                  |
| **requestTermination** (IF8A-75)      | `selectBySeq(requestSignSeq, paymentVendor)` | `selectByUserAndVendor(thirdUserId, paymentVendor)` | **高** | 不操作 APP\_PAY\_SIGN\_INFO |
| **receiveSignResult** (回调)            | `selectBySeq(requestSignSeq, paymentVendor)` | INSERT 新记录                                          | **高** | 成功时 INSERT               |
| **receiveTerminationResult** (回调)     | `selectBySeq(requestSignSeq, paymentVendor)` | DELETE 记录                                           | **高** | 成功时 DELETE               |

### 详细影响分析

#### 1. requestContractResult (IF8A-22 签约结果查询)

**当前逻辑：**

```java
PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
```

**改造方案：**

```java
PaySignInfo signInfo = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
```

**影响：** 无论用户传入哪个 `requestSignSeq`，都能查到该用户该渠道的签约状态。

#### 2. requestTermination (IF8A-75 请求解约)

**当前逻辑：**

```java
PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
if (signInfo == null) {
    return USER_NOT_SIGNED 错误;
}
```

**改造方案：**

```java
PaySignInfo signInfo = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
if (signInfo == null) {
    return USER_NOT_SIGNED 错误;
}
// 不操作 APP_PAY_SIGN_INFO，只记录流水
```

**影响：** 准确判断用户是否已签约，不修改 APP\_PAY\_SIGN\_INFO。

#### 3. receiveSignResult (签约回调)

**当前逻辑：**

```java
PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
```

**改造方案：**

```java
if ("SUCCESS".equalsIgnoreCase(request.getStatus())) {
    PaySignInfo signInfo = new PaySignInfo();
    // ... 设置字段 ...
    paySignInfoMapper.insert(signInfo);
} else {
    // 不操作 APP_PAY_SIGN_INFO
}
```

**影响：** 成功时 INSERT，失败时不操作。

#### 4. receiveTerminationResult (解约回调)

**当前逻辑：**

```java
PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
```

**改造方案：**

```java
if ("SUCCESS".equalsIgnoreCase(request.getStatus())) {
    paySignInfoMapper.deleteByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
} else {
    // 不操作 APP_PAY_SIGN_INFO
}
```

**影响：** 成功时 DELETE，失败时不操作。

### 数据一致性影响

| 场景         | 改造前                    | 改造后             | 风险 |
| ---------- | ---------------------- | --------------- | -- |
| 同一用户多次请求签约 | 旧记录保留，新记录因唯一约束冲突       | 已签约则返回提示，未签约则继续 | 低  |
| 签约成功回调     | UPDATE 状态              | INSERT 新记录      | 低  |
| 签约失败回调     | 可能误 UPDATE             | 不操作             | 低  |
| 请求解约       | UPDATE 状态              | 不操作             | 低  |
| 解约成功回调     | UPDATE 状态为 NOT\_SIGNED | DELETE 记录       | 低  |
| 解约后再次签约    | 因唯一约束无法插入              | 无记录，流程继续        | 低  |

### 下游服务影响

| 服务             | 影响 | 说明                |
| -------------- | -- | ----------------- |
| fep-app-server | 无  | 只调 RPC 接口，不感知内部实现 |
| account-server | 无  | 不直接访问签约表          |
| ticket-server  | 无  | 不直接访问签约表          |
| 支付平台           | 无  | 接口调用方式不变          |

### 版本兼容性

| 兼容性类型          | 评估  | 说明                           |
| -------------- | --- | ---------------------------- |
| 向前兼容（旧客户端调新服务） | 兼容  | 接口参数不变，只是内部逻辑调整              |
| 向后兼容（新客户端调旧服务） | 不涉及 | 无新客户端                        |
| 数据库兼容          | 兼容  | 不修改 APP\_PAY\_SIGN\_INFO 表结构 |
| 日志兼容           | 不兼容 | 新流水表与旧日志表结构不同，需重新对接          |

## 影响范围

| 服务              | 影响                                                                                                                                                                                                                                              | 版本升级      |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| pay-sign-server | PaySignInfoMapper.xml 废弃MERGE改为INSERT/DELETE；新增selectByUserAndVendor/deleteByUserAndVendor；requestSignInfo增加重复签约校验；receiveSignResult改为INSERT；receiveTerminationResult改为DELETE；requestTermination不操作表；新增PaySignRequest实体/Mapper；writeLog改为INSERT；新增DISPLAY_ACCOUNT字段及兜底逻辑 | 1.4 → 2.0 |
| 数据库             | 新增 APP\_PAY\_SIGN\_REQUEST 表；无需修改 APP\_PAY\_SIGN\_INFO 表结构                                                                                                                                                                                      | -         |
| 其他服务            | 无影响                                                                                                                                                                                                                                             | 不升级       |

## 注意事项

1. **重复签约校验**：同一用户同一渠道已签约时，requestSignInfo 返回"重复签约"提示，不调用支付平台
2. **回调幂等性**：receiveSignResult 和 receiveTerminationResult 需要考虑幂等性，防止重复回调导致数据异常
3. **并发安全**：INSERT 和 DELETE 操作在 Oracle 中是原子的，但并发请求同一用户时可能需要考虑锁机制
4. **历史数据**：改造前已存在的数据需要迁移或清理
5. **流水表数据量**：APP\_PAY\_SIGN\_REQUEST 每次操作都 INSERT，数据量会持续增长，需考虑归档策略
6. **解约后清理**：DELETE 操作会彻底删除记录，如需保留历史需依赖流水表

## 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| **1.5** | 2026-06-05 | 废弃MERGE改为INSERT/DELETE；新增APP_PAY_SIGN_REQUEST流水表；requestSignInfo增加重复签约校验；receiveSignResult改为INSERT；receiveTerminationResult改为DELETE；requestTermination不操作表；新增PaySignRequest实体/Mapper；writeLog改为INSERT；新增PaymentVendorEnum枚举；渠道编码原样返回 |
| **1.6** | 2026-06-05 | 修复writeLog异常导致主事务回滚问题；writeLog改为独立try-catch，异常不影响主事务 |
| **1.7** | 2026-06-05 | APP_PAY_SIGN_REQUEST表主键重构：REQUEST_SIGN_SEQ改为普通字段，新增ID自增主键；修复同一流水号重复插入问题；修改相关Mapper和Service逻辑 |
| **1.8** | 2026-06-05 | 修复支付平台回调不带thirdUserId导致INSERT失败问题；新增resolveThirdUserId方法从流水表补充thirdUserId；receiveSignResult和receiveTerminationResult均添加兜底逻辑 |
| **1.9** | 2026-06-05 | 新增resolveCardInfoFromSignInfo方法；支付平台回调不带cardId/cardType时从签约主表补充；receiveTerminationResult添加cardId/cardType兜底逻辑；整理文档 |
| **2.0** | 2026-06-05 | APP_PAY_SIGN_REQUEST流水表新增DISPLAY_ACCOUNT字段；writeLog自动提取并记录displayAccount；新增resolveDisplayAccount方法从流水表补充；receiveSignResult回调时自动回填displayAccount到APP_PAY_SIGN_INFO |


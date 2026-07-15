# 支付宝乘坐地铁 — 业务流图

## 一、总览架构

```mermaid
graph LR
    subgraph 外部系统
        A[支付宝APP/小程序]
        T[ticket-server 票务系统]
        I[industry-data-server<br/>生码服务]
    end

    subgraph 本项目服务
        B[fep-alipay-server]
        C[alipay-account-server<br/>开户/账户]
        D[alipay-pay-sign-server<br/>签约/支付]
        E[blacklist-server<br/>黑名单]
    end

    A -->|调用| B
    B -->|/channel/requestApplication| C
    B -->|/channel/requestIndustryData| C
    B -->|/api/payment/*| D
    C -->|registerRideStatus| T
    C -->|queryQrCodeStatus| T
    C -->|buildCardData| I
    C -.->|updatePaymentChannel| D
    D -->|selectByThirdUserId| C
    E -.->|查询黑名单| D
    E -.->|查询黑名单| C
```

> **说明：**
> - `fep-alipay-server` 是对外网关，本仓库维护的是其后端三个微服务。
> - `alipay-account-server` 负责开卡、用户信息维护、行业数据获取。
> - `alipay-pay-sign-server` 负责支付宝代扣签约/解约、支付、退款、查询。
> - `industry-data-server` 负责生成乘车码卡数据（HexString）。
> - `blacklist-server` 负责卡号黑名单管理，供其他服务联动查询。

---

## 二、完整业务泳道图（粗粒度）

> 以下泳道图覆盖用户从"开通乘车码 → 打开二维码 → 签约代扣 → 乘车扣款 → 查询 → 退款 → 解约"的完整生命周期。
> 每个参与者独立泳道，只展示主要流程步骤，省略内部细节。

```mermaid
graph TD
    subgraph 用户
        U1[① 开通乘车码]
        U2[② 打开乘车码]
        U3[③ 同意自动扣款]
        U4[④ 刷码进站]
        U5[⑤ 刷码出站]
        U6[⑥ 查询账单]
        U7[⑦ 申请退款]
        U8[⑧ 取消代扣]
    end

    subgraph 支付宝APP/小程序
        A1[调用 requestApplication]
        A2[调用 requestIndustryData]
        A3[调用 addContract]
        A6[调用 payQuery]
        A7[调用 requestRefund]
        A8[调用 terminateContract]
        A9[本地生成二维码展示]
    end

    subgraph 闸机系统
        G1[扫码读取 cardId]
        G2[调用 fep-dev-server<br/>IF1A-01 闸机检票通知]
        G3[开启闸机]
    end

    subgraph fep-dev-server
        F2[调用 ticket-server<br/>闸机检票通知]
        F3{出站交易?<br/>trxType=02/03}
        F4[调用 gate-txn-pay-server<br/>过闸扣费]
    end

    subgraph fep-alipay-server
        F1[参数校验/路由转发]
    end

    subgraph alipay-account-server
        C1[开户/分配卡号]
        C2[查询用户信息]
        C3[更新支付通道]
    end

    subgraph ticket-server
        T1[注册乘车状态]
        T2[查询二维码状态]
        T3[闸机检票通知<br/>更新 QRCodeStatus]
        T4[写入 QRCodeTxnDetail]
    end

    subgraph industry-data-server
        I1[生成卡数据]
    end

    subgraph alipay-pay-sign-server
        D1[签约管理]
        D2[支付/退款]
        D3[结果查询]
        D4[解约登记]
    end

    subgraph gate-txn-pay-server
        GT1[生成过闸订单<br/>GATE_TXN_PAY]
        GT2[调用 pay-sign 扣款]
    end

    subgraph 数据库
        DB1[(数据读写)]
    end

    %% ① 开卡流程
    U1 --> A1
    A1 --> F1
    F1 --> C1
    C1 --> T1
    C1 --> DB1

    %% ② 生码流程
    U2 --> A2
    A2 --> F1
    F1 --> C2
    F1 --> T2
    F1 --> I1
    I1 --> A9
    A9 --> U2

    %% ③ 签约流程
    U3 --> A3
    A3 --> F1
    F1 --> D1
    D1 --> C3
    D1 --> DB1

    %% ④ 进站流程
    U4 --> G1
    G1 --> G2
    G2 --> F2
    F2 --> T3
    T3 --> DB1
    T3 --> T4
    T4 --> DB1
    F2 --> G3
    G3 --> U4

    %% ⑤ 出站扣款流程
    U5 --> G1
    G1 --> G2
    G2 --> F2
    F2 --> T3
    T3 --> DB1
    T3 --> T4
    T4 --> DB1
    F2 --> F3
    F3 -->|是| F4
    F4 --> GT1
    GT1 --> DB1
    GT1 --> GT2
    GT2 --> D2
    D2 --> DB1
    GT2 --> G3
    G3 --> U5
    F3 -->|否| G3

    %% ⑥ 查询流程
    U6 --> A6
    A6 --> F1
    F1 --> D3
    D3 --> DB1

    %% ⑦ 退款流程
    U7 --> A7
    U7 --> A7
    A7 --> F1
    F1 --> D2
    D2 --> DB1

    %% ⑧ 解约流程
    U8 --> A8
    A8 --> F1
    F1 --> D4
    D4 --> DB1
```

### 时序说明

| 序号 | 步骤 | 调用方 | 服务端 | 说明 |
|------|------|--------|--------|------|
| ① | 开通乘车码 | 用户 → 支付宝 | fep → account-server → ticket-server | 首次开通，分配逻辑卡号 |
| ② | 打开乘车码 | 用户 → 支付宝 | fep → account-server → ticket-server → industry-data-server | 获取卡数据，本地生成二维码 |
| ③ | 同意自动扣扣 | 用户 → 支付宝 | fep → pay-sign-server → account-server | 开通免密代扣协议 |
| ④ | 刷码进站 | 闸机系统 | fep-dev-server → ticket-server | 闸机扫码，更新票卡状态为"已进站"（codeStatus=04），记录进站时间、站点 |
| ⑤ | 刷码出站 | 闸机系统 | fep-dev-server → ticket-server → gate-txn-pay-server → pay-sign-server | 闸机扫码，更新票卡状态为"已出站"（codeStatus=05/06），生成过闸订单，发起免密扣款 |
| ⑥ | 查询账单 | 用户 → 支付宝 | fep → pay-sign-server | 查询支付结果 |
| ⑦ | 申请退款 | 用户 → 支付宝 | fep → pay-sign-server | 退款到支付宝余额 |
| ⑧ | 取消代扣 | 用户 → 支付宝 | fep → pay-sign-server | 登记解约请求 |

> **闸机进出站说明**：
> - **进站（trxType=01）**：闸机调用 `fep-dev-server` 的 IF1A-01 接口，`fep-dev-server` 调用 `ticket-server` 的 `/ci/app/notiVerifyResult`，更新 `QRCodeStatus` 状态为 `04`（已进站），记录 `gateInTime`、`gateInStation`，写入 `QRCodeTxnDetail` 交易明细。
> - **出站（trxType=02/03）**：闸机调用 `fep-dev-server` 的 IF1A-01 接口，`fep-dev-server` 先调用 `ticket-server` 更新状态为 `05`/`06`（已出站），若出站交易成功则继续调用 `gate-txn-pay-server` 的 `/ci/gateTxnPay/requestPay` 生成过闸订单 `GATE_TXN_PAY`，由 `gate-txn-pay-server` 调用 `pay-sign-server` 发起免密扣款。

---

## 三、核心业务流程泳道图

### 流程 1：开卡申请（requestApplication）

> 用户首次在支付宝开通地铁乘车码，需完成开户并分配逻辑卡号。

```mermaid
graph TD
    subgraph 用户
        U1[首次开通乘车码]
    end

    subgraph 支付宝APP/小程序
        A1[调用 /channel/requestApplication]
        A2[返回 cardId / ACTIVE]
    end

    subgraph fep-alipay-server
        F1[参数校验]
        F2[路由到 account-server]
    end

    subgraph alipay-account-server
        C1[校验参数<br/>thirdUserId / cardType / cardIssueCode]
        C2{用户已开户?}
        C3[分配逻辑卡号<br/>allocateNextCard]
        C4[生成 requestSeq UUID]
        C5[写入 ALIPAY_USER_INFO<br/>状态=ACTIVE]
        C6[写入 ALIPAY_REG_LOG]
        C7[调用 ticket-server<br/>registerRideStatus]
        C8[返回 cardId / ACTIVE]
    end

    subgraph ticket-server
        T1[注册乘车状态]
        T2[返回成功]
    end

    subgraph 数据库
        D1[查询用户]
        D2[写入用户信息]
        D3[写入开卡流水]
    end

    U1 --> A1
    A1 --> F1
    F1 --> F2
    F2 --> C1
    C1 --> C2
    C2 -->|是| C8
    C2 -->|否| C3
    C3 --> D1
    D1 -->|无卡| C3
    D1 -->|有卡| C4
    C4 --> C5
    C5 --> D2
    D2 --> C6
    C6 --> D3
    D3 --> C7
    C7 --> T1
    T1 --> T2
    T2 --> C8
    C8 --> A2
    A2 --> U1
```

---

### 流程 2：获取行业数据/生码（requestIndustryData）

> 用户在支付宝小程序/APP 打开乘车码时，系统生成乘车码卡数据（HexString），前端基于此数据动态生成二维码。

```mermaid
graph TD
    subgraph 用户
        U1[打开乘车码]
        U2[展示二维码]
    end

    subgraph 支付宝APP/小程序
        A1[调用 /memberContract/channel/<br/>requestIndustryData]
        A2[解析 cardData]
        A3[本地生成二维码]
    end

    subgraph fep-alipay-server
        F1[参数校验<br/>thirdUserId / cardId / cardType]
        F2[路由到 alipay-account-server]
        F3[路由到 ticket-server]
        F4[路由到 industry-data-server]
        F5[返回 cardData + sign]
    end

    subgraph alipay-account-server
        C1[queryUserInfo 查询用户信息]
        C2[resolveSignChannelCode<br/>获取签约渠道编码]
        C3[返回用户信息 + 渠道编码]
    end

    subgraph ticket-server
        T1[queryQrCodeStatus<br/>查询二维码状态]
        T2[返回进出站状态]
    end

    subgraph industry-data-server
        I1[组装 IndustryCardDataBuildReqDTO]
        I2[buildCardData 生成卡数据]
        I3[返回 cardData HexString]
    end

    U1 --> A1
    A1 --> F1
    F1 --> F2
    F2 --> C1
    C1 --> C2
    C2 --> C3
    C3 --> F3
    F3 --> T1
    T1 --> T2
    T2 --> F4
    F4 --> I1
    I1 --> I2
    I2 --> I3
    I3 --> F5
    F5 --> A1
    A1 --> A2
    A2 --> A3
    A3 --> U2
```

---

### 流程 3：签约（addContract）— 支付宝代扣协议开通

> 用户在支付宝内同意开通"自动扣款"协议，系统记录签约信息并补充支付通道。

```mermaid
graph TD
    subgraph 用户
        U1[同意自动扣款协议]
    end

    subgraph 支付宝APP/小程序
        A1[调用 /api/payment/addContract]
        A2[返回 agreementCode]
    end

    subgraph fep-alipay-server
        F1[参数校验]
        F2[路由到 pay-sign-server]
    end

    subgraph alipay-pay-sign-server
        D1[校验参数<br/>thirdUserId / channel /<br/>agreementCode / channelUserAccount]
        D2{是否已签约?}
        D3[写入 ALIPAY_SIGN_INFO<br/>signStatus=SIGNED]
        D4[调用 alipay-account-server<br/>updatePaymentChannel]
        D5[写入 ALIPAY_SIGN_LOG<br/>operationType=SIGN]
        D6[返回 agreementCode]
    end

    subgraph alipay-account-server
        C1[更新 ALIPAY_USER_INFO<br/>thirdPayId / reqContractNo]
        C2[返回成功]
    end

    subgraph 数据库
        DB1[查询签约信息]
        DB2[写入签约主表]
        DB3[写入签约流水]
    end

    U1 --> A1
    A1 --> F1
    F1 --> F2
    F2 --> D1
    D1 --> D2
    D2 -->|是| D6
    D2 -->|否| DB1
    DB1 --> D3
    D3 --> DB2
    DB2 --> D4
    D4 --> C1
    C1 --> C2
    C2 --> D5
    D5 --> DB3
    DB3 --> D6
    D6 --> A2
    A2 --> U1
```

---

### 流程 4：解约登记（terminateContract）

> 用户在支付宝端取消代扣协议，系统登记解约请求（状态 PENDING，由外部系统后续处理实际解约）。

```mermaid
graph TD
    subgraph 用户
        U1[取消代扣协议]
    end

    subgraph 支付宝APP/小程序
        A1[调用 /api/payment/terminateContract]
        A2[返回 agreementCode]
    end

    subgraph fep-alipay-server
        F1[参数校验]
        F2[路由到 pay-sign-server]
    end

    subgraph alipay-pay-sign-server
        D1[校验 agreementCode]
        D2[查询 ALIPAY_SIGN_INFO<br/>by agreementCode]
        D3{签约是否存在?}
        D4[写入 ALIPAY_TERMINATION_REQUEST<br/>status=PENDING]
        D5[写入 ALIPAY_SIGN_LOG<br/>operationType=TERMINATE]
        D6[返回 agreementCode]
    end

    subgraph 数据库
        DB1[查询签约信息]
        DB2[写入解约登记表]
        DB3[写入解约流水]
    end

    U1 --> A1
    A1 --> F1
    F1 --> F2
    F2 --> D1
    D1 --> D2
    D2 --> DB1
    DB1 --> D3
    D3 -->|否| D6
    D3 -->|是| D4
    D4 --> DB2
    DB2 --> D5
    D5 --> DB3
    DB3 --> D6
    D6 --> A2
    A2 --> U1
```

---

### 流程 5：闸机进站（IF1A-01）— 更新票卡状态

> 用户刷码进站，闸机调用 ticket-server 更新票卡状态为"已进站"，记录进站时间、站点，写入交易明细。

```mermaid
graph TD
    subgraph 用户
        U1[刷码进站]
    end

    subgraph 闸机系统
        G1[扫码读取 cardId]
        G2[调用 ticket-server<br/>/ci/app/notiVerifyResult<br/>trxType=01]
        G3[开启闸机]
    end

    subgraph ticket-server
        T1[查询当前 QRCodeStatus]
        T2{卡状态是否存在?}
        T3[填充上次交易字段<br/>lastTicketStatus / lastHandleStation<br/>/ lastHandleDateTime]
        T4[构建 QRCodeTxnDetail]
        T5[写入交易明细]
        T6[构建 nextStatus<br/>codeStatus=04 已进站]
        T7[更新 QRCodeStatus<br/>gateInTime / gateInStation]
        T8[返回成功]
    end

    subgraph 数据库
        DB1[查询 QRCodeStatus]
        DB2[写入 QRCodeTxnDetail]
        DB3[更新 QRCodeStatus]
    end

    U1 --> G1
    G1 --> G2
    G2 --> T1
    T1 --> DB1
    DB1 --> T2
    T2 -->|否| G2
    T2 -->|是| T3
    T3 --> T4
    T4 --> T5
    T5 --> DB2
    T5 --> T6
    T6 --> T7
    T7 --> DB3
    DB3 --> T8
    T8 --> G3
    G3 --> U1
```

**涉及接口与表：**
| 接口 | 服务 | 数据库表 |
|------|------|----------|
| `POST /ci/app/notiVerifyResult` | ticket-server | `qrcode_status`, `qrcode_txn_detail` |

**关键字段说明：**
| 字段 | 说明 |
|------|------|
| `trxType=01` | 进站交易 |
| `codeStatus=04` | 已进站 |
| `gateInTime` | 进站时间 |
| `gateInStation` | 进站站点 |
| `lastTicketStatus` | 上次票卡状态 |
| `lastHandleStationCode` | 上次处理站点 |
| `lastHandleDateTime` | 上次处理时间 |

---

### 流程 6：闸机出站扣款（IF1A-01 + gate-txn-pay）— 出站扣费

> 用户刷码出站，闸机先调用 ticket-server 更新票卡状态，再调用 gate-txn-pay-server 生成过闸订单并发起免密扣款。

```mermaid
graph TD
    subgraph 用户
        U1[刷码出站]
    end

    subgraph 闸机系统
        G1[扫码读取 cardId]
        G2[调用 fep-dev-server<br/>IF1A-01 闸机检票通知]
        G3[开启闸机]
    end

    subgraph fep-dev-server
        F1[调用 ticket-server<br/>闸机检票通知]
        F2{出站交易?<br/>trxType=02/03}
        F3[调用 gate-txn-pay-server<br/>过闸扣费]
    end

    subgraph ticket-server
        T1[查询当前 QRCodeStatus]
        T2{卡状态是否存在?}
        T3[填充上次交易字段]
        T4[构建 QRCodeTxnDetail]
        T5[写入交易明细]
        T6[构建 nextStatus<br/>codeStatus=05/06 已出站]
        T7[更新 QRCodeStatus]
        T8[返回成功/失败]
    end

    subgraph gate-txn-pay-server
        GT1[参数校验<br/>只处理出站 trxType=02/03]
        GT2[生成过闸订单<br/>GATE_TXN_PAY]
        GT3[写入数据库]
        GT4[调用 pay-sign-server<br/>requestPay 免密扣款]
        GT5[更新订单状态<br/>PROCESSING/RETRY]
        GT6[返回 orderNo / payStatus]
    end

    subgraph alipay-pay-sign-server
        D1[处理支付请求]
        D2[写入 ALIPAY_PAY_LOG]
        D3[返回扣款结果]
    end

    subgraph 数据库
        DB1[查询 QRCodeStatus]
        DB2[写入 QRCodeTxnDetail]
        DB3[更新 QRCodeStatus]
        DB4[写入 GATE_TXN_PAY]
        DB5[更新订单状态]
    end

    U1 --> G1
    G1 --> G2
    G2 --> F1
    F1 --> T1
    T1 --> DB1
    DB1 --> T2
    T2 -->|否| T8
    T2 -->|是| T3
    T3 --> T4
    T4 --> T5
    T5 --> DB2
    T5 --> T6
    T6 --> T7
    T7 --> DB3
    DB3 --> T8
    T8 --> F2
    F2 -->|是| F3
    F3 --> GT1
    GT1 --> GT2
    GT2 --> GT3
    GT3 --> DB4
    GT3 --> GT4
    GT4 --> D1
    D1 --> D2
    D2 --> DB5
    DB5 --> D3
    D3 --> GT5
    GT5 --> GT6
    GT6 --> G3
    G3 --> U1
    F2 -->|否| G3
    T8 -->|失败| G3
```

**涉及接口与表：**
| 接口 | 服务 | 数据库表 |
|------|------|----------|
| `POST /ci/app/notiVerifyResult` | ticket-server | `qrcode_status`, `qrcode_txn_detail` |
| `POST /ci/gateTxnPay/requestPay` | gate-txn-pay-server | `gate_txn_pay` |

**关键字段说明：**
| 字段 | 说明 |
|------|------|
| `trxType=02` | 出站交易 |
| `trxType=03` | 超时出站交易 |
| `codeStatus=05/06` | 已出站 |
| `orderNo=GT + 时间戳 + cardId后6位` | 过闸订单号 |
| `debitStatus=PROCESSING` | 扣款处理中 |
| `debitStatus=RETRY` | 扣款失败待重试 |

---

### 流程 7：支付申请（requestPay）— 支付宝主动支付

> 用户通过支付宝主动发起支付（非闸机扫码场景），系统校验签约状态后发起扣款。

```mermaid
graph TD
    subgraph 用户
        U1[主动发起支付]
    end

    subgraph 支付宝APP/小程序
        A1[调用 /api/payment/requestPay]
        A2[返回扣款结果]
    end

    subgraph fep-alipay-server
        F1[参数校验]
        F2[路由到 pay-sign-server]
    end

    subgraph alipay-pay-sign-server
        D1[校验参数<br/>orderNo / amount / industryType<br/>/ subject / body / industryDetail]
        D2{免密场景?}
        D3[校验 requestSignSeq]
        D4{用户已签约?}
        D5{幂等检查<br/>订单号已存在?}
        D6{原订单状态?}
        D7[调用支付宝支付接口<br/>生成 tradeNo]
        D8[写入 ALIPAY_PAY_LOG<br/>payStatus=SUCCESS]
        D9[返回 SUCCESS / tradeNo]
    end

    subgraph alipay-account-server
        C1[selectByThirdUserId<br/>查询用户信息]
        C2[返回用户信息]
    end

    subgraph 数据库
        DB1[查询支付记录]
        DB2[写入支付流水]
    end

    U1 --> A1
    A1 --> F1
    F1 --> F2
    F2 --> D1
    D1 --> D2
    D2 -->|是| D3
    D3 -->|缺失| D1
    D3 -->|有值| D4
    D2 -->|否| D4
    D4 -->|否| A1
    D4 -->|是| D5
    D5 -->|是| D6
    D6 -->|SUCCESS| A1
    D6 -->|FAIL| A1
    D5 -->|否| C1
    C1 --> C2
    C2 --> D7
    D7 --> D8
    D8 --> DB2
    DB2 --> D9
    D9 --> A2
    A2 --> U1
```

---

### 流程 8：支付结果查询（payQuery）

> 查询某订单号的支付状态。

```mermaid
graph TD
    subgraph 用户/支付宝
        U1[查询支付结果]
    end

    subgraph 支付宝APP/小程序
        A1[调用 /api/payment/payQuery]
        A2[返回支付结果]
    end

    subgraph fep-alipay-server
        F1[参数校验 orderNo]
        F2[路由到 pay-sign-server]
    end

    subgraph alipay-pay-sign-server
        D1[查询 ALIPAY_PAY_LOG<br/>by orderNo]
        D2{记录存在?}
        D3[返回 tradeStatus / paymentTime<br/>/ tradeNo / totalAmount]
    end

    subgraph 数据库
        DB1[查询支付记录]
    end

    U1 --> A1
    A1 --> F1
    F1 --> F2
    F2 --> D1
    D1 --> DB1
    DB1 --> D2
    D2 -->|否| A1
    D2 -->|是| D3
    D3 --> A2
    A2 --> U1
```

---

### 流程 9：退款申请（requestRefund）

> 对已支付成功的订单发起退款（支持部分退款，累加退款金额不超过原订单金额）。

```mermaid
graph TD
    subgraph 用户
        U1[申请退款]
    end

    subgraph 支付宝APP/小程序
        A1[调用 /api/payment/requestRefund]
        A2[返回退款结果]
    end

    subgraph fep-alipay-server
        F1[参数校验]
        F2[路由到 pay-sign-server]
    end

    subgraph alipay-pay-sign-server
        D1[校验参数<br/>orderNo / cardIssueCode / cardNum<br/>/ channelAgreementNo / refundAmount<br/>/ refundOrderNo]
        D2[查询 ALIPAY_PAY_LOG<br/>by orderNo]
        D3{原支付记录存在?}
        D4{原订单支付成功?}
        D5{退款金额 <= 可退金额?}
        D6[调用支付宝退款接口]
        D7[写入 ALIPAY_REFUND_LOG<br/>refundStatus=SUCCESS]
        D8{退款成功?}
        D9[更新 ALIPAY_PAY_LOG<br/>refundSummary]
        D10[返回 SUCCESS]
        D11[退款记录标记 FAIL]
        D12[返回 FAIL]
    end

    subgraph 数据库
        DB1[查询原支付记录]
        DB2[写入退款流水]
        DB3[更新退款汇总]
    end

    U1 --> A1
    A1 --> F1
    F1 --> F2
    F2 --> D1
    D1 --> D2
    D2 --> DB1
    DB1 --> D3
    D3 -->|否| A1
    D3 -->|是| D4
    D4 -->|否| A1
    D4 -->|是| D5
    D5 -->|否| A1
    D5 -->|是| D6
    D6 --> D7
    D7 --> DB2
    DB2 --> D8
    D8 -->|是| D9
    D9 --> DB3
    DB3 --> D10
    D10 --> A2
    D8 -->|否| D11
    D11 --> D12
    D12 --> A2
    A2 --> U1
```

---

### 流程 10：黑名单管理（Blacklist）

> 管理异常卡号的黑名单，供其他服务联动拦截。

```mermaid
graph TD
    subgraph 运营人员
        U1[新增黑名单]
        U2[查询黑名单]
        U3[删除黑名单]
    end

    subgraph 管理后台
        A1[调用 /addBlackList]
        A2[调用 /queryBlackList]
        A3[调用 /deleteBlackList]
    end

    subgraph blacklist-server
        E1[校验参数]
        E2[查询是否已存在]
        E3[写入 BLACKLIST]
        E4[写入 BLACKLIST_OPERATE_LOG]
        E5[解析卡号列表]
        E6[查询 BLACKLIST countByCardIds]
        E7[返回 inBlack=1/0]
        E8[查询 BLACKLIST by cardIds]
        E9[物理删除 BLACKLIST]
        E10[写入操作日志]
    end

    subgraph 数据库
        DB1[查询黑名单]
        DB2[写入黑名单]
        DB3[写入操作日志]
        DB4[删除黑名单]
    end

    U1 --> A1
    A1 --> E1
    E1 --> E2
    E2 --> DB1
    DB1 -->|不存在| E3
    E3 --> DB2
    DB2 --> E4
    E4 --> DB3
    E2 -->|已存在| E4
    E4 --> A1
    A1 --> U1

    U2 --> A2
    A2 --> E5
    E5 --> E6
    E6 --> DB1
    DB1 --> E7
    E7 --> A2
    A2 --> U2

    U3 --> A3
    A3 --> E1
    E1 --> E5
    E5 --> E8
    E8 --> DB1
    DB1 --> E9
    E9 --> DB4
    DB4 --> E10
    E10 --> DB3
    E10 --> A3
    A3 --> U3
```

---

## 三、完整业务时序图

以下时序图覆盖从"用户开通乘车码 → 打开二维码 → 乘车扣款 → 退款"的完整链路。

```mermaid
sequenceDiagram
    actor User as 用户
    participant Alipay as 支付宝APP/小程序
    participant Fep as fep-alipay-server
    participant Acct as alipay-account-server
    participant Sign as alipay-pay-sign-server
    participant Industry as industry-data-server
    participant Ticket as ticket-server
    participant FepDev as fep-dev-server
    participant Gate as gate-txn-pay-server
    participant DB as 数据库

    %% 开卡
    User->>Alipay: 1. 首次开通乘车码
    Alipay->>Fep: 2. 调用 /channel/requestApplication
    Fep->>Acct: 3. 开卡申请
    Acct->>DB: 4. 校验用户/分配卡号/写入用户信息
    Acct->>Ticket: 5. registerRideStatus 注册乘车状态
    Ticket-->>Acct: 6. 返回成功
    Acct-->>Fep: 7. 返回 cardId / ACTIVE
    Fep-->>Alipay: 8. 开卡成功

    %% 签约
    User->>Alipay: 9. 同意自动扣款协议
    Alipay->>Fep: 10. 调用 /api/payment/addContract
    Fep->>Sign: 11. 添加签约
    Sign->>DB: 12. 写入 ALIPAY_SIGN_INFO / SIGN_LOG
    Sign->>Acct: 13. updatePaymentChannel
    Acct->>DB: 14. 更新 ALIPAY_USER_INFO.thirdPayId
    Acct-->>Sign: 15. 返回成功
    Sign-->>Fep: 16. 返回 agreementCode
    Fep-->>Alipay: 17. 签约成功

    %% 获取行业数据/生码
    User->>Alipay: 18. 打开乘车码
    Alipay->>Fep: 19. 调用 /memberContract/channel/requestIndustryData
    Fep->>Acct: 20. queryUserInfo 查询用户信息
    Acct->>DB: 21. 查询 ALIPAY_USER_INFO
    Acct-->>Fep: 22. 返回用户信息 + 签约渠道
    Fep->>Ticket: 23. queryQrCodeStatus 查询二维码状态
    Ticket-->>Fep: 24. 返回进出站状态
    Fep->>Industry: 25. buildCardData 生成卡数据
    Industry-->>Fep: 26. 返回 cardData HexString
    Fep-->>Alipay: 27. 返回 cardData + sign
    Alipay->>Alipay: 28. 本地生成二维码展示

    %% 闸机进站
    User->>Ticket: 29. 刷码进站<br/>闸机调用 fep-dev-server IF1A-01
    Ticket->>FepDev: 29. 闸机调用 fep-dev-server IF1A-01
    FepDev->>Ticket: 30. 调用 /ci/app/notiVerifyResult trxType=01
    Ticket->>DB: 31. 查询当前 QRCodeStatus
    Ticket->>DB: 32. 写入 QRCodeTxnDetail
    Ticket->>DB: 33. 更新 QRCodeStatus<br/>codeStatus=04 已进站
    Ticket-->>FepDev: 34. 返回成功
    FepDev-->>User: 35. 闸机开启

    %% 闸机出站扣款
    User->>Ticket: 36. 刷码出站<br/>闸机调用 fep-dev-server IF1A-01
    Ticket->>FepDev: 36. 闸机调用 fep-dev-server IF1A-01
    FepDev->>Ticket: 37. 调用 /ci/app/notiVerifyResult trxType=02/03
    Ticket->>DB: 38. 查询当前 QRCodeStatus
    Ticket->>DB: 39. 写入 QRCodeTxnDetail
    Ticket->>DB: 40. 更新 QRCodeStatus<br/>codeStatus=05/06 已出站
    Ticket-->>FepDev: 41. 返回成功
    FepDev->>FepDev: 42. 判断出站交易需扣款
    FepDev->>Gate: 43. 调用 /ci/gateTxnPay/requestPay
    Gate->>Gate: 44. 生成过闸订单 GATE_TXN_PAY
    Gate->>Sign: 45. 调用 requestPay 免密扣款
    Sign->>DB: 46. 写入 ALIPAY_PAY_LOG
    Sign-->>Gate: 47. 返回 SUCCESS / tradeNo
    Gate-->>FepDev: 48. 返回 orderNo / payStatus
    FepDev-->>User: 49. 闸机开启

    %% 查询
    Alipay->>Fep: 50. 调用 /api/payment/payQuery
    Fep->>Sign: 51. 支付查询
    Sign->>DB: 52. 查询 ALIPAY_PAY_LOG
    Sign-->>Fep: 53. 返回支付结果
    Fep-->>Alipay: 54. 返回结果

    %% 退款
    User->>Alipay: 55. 申请退款
    Alipay->>Fep: 56. 调用 /api/payment/requestRefund
    Fep->>Sign: 57. 退款申请
    Sign->>DB: 58. 校验原订单/写入 ALIPAY_REFUND_LOG
    Sign-->>Fep: 59. 返回 SUCCESS
    Fep-->>Alipay: 60. 退款成功
    Alipay-->>User: 61. 提示退款成功
```

---

## 四、数据库实体关系概览

```mermaid
erDiagram
    ALIPAY_USER_INFO ||--o| ALIPAY_SIGN_INFO : "三方用户ID"
    ALIPAY_USER_INFO ||--o| ALIPAY_CARD_POOL : "分配卡号"
    ALIPAY_USER_INFO ||--o{ ALIPAY_REG_LOG : "开卡流水"
    ALIPAY_SIGN_INFO ||--o| ALIPAY_TERMINATION_REQUEST : "解约"
    ALIPAY_SIGN_INFO ||--o{ ALIPAY_SIGN_LOG : "签约流水"
    ALIPAY_SIGN_INFO ||--o{ ALIPAY_PAY_LOG : "支付记录"
    ALIPAY_PAY_LOG ||--o{ ALIPAY_REFUND_LOG : "退款记录"
    BLACKLIST ||--o{ BLACKLIST_OPERATE_LOG : "操作流水"

    ALIPAY_USER_INFO {
        string thirdUserId PK
        string cardId FK
        string cardType
        string status
        string thirdPayId
        string reqContractNo
    }
    ALIPAY_SIGN_INFO {
        string agreementCode PK
        string thirdUserId FK
        string signStatus
        string channel
    }
    ALIPAY_CARD_POOL {
        string cardId PK
        string status
        string thirdUserId FK
    }
    ALIPAY_PAY_LOG {
        string orderNo PK
        string thirdUserId
        string cardId
        string payStatus
        string tradeNo
    }
    ALIPAY_REFUND_LOG {
        string refundSeq PK
        string orderNo FK
        string refundStatus
    }
    BLACKLIST {
        string cardId PK
        string thirdUserId
        string reason
    }
```

---

## 五、核心接口清单

| 接口路径 | HTTP 方法 | 服务 | 功能 |
|----------|-----------|------|------|
| `/channel/requestApplication` | POST | alipay-account-server | 开卡申请 |
| `/memberContract/channel/requestIndustryData` | POST | fep-alipay-server | 获取行业数据/生码 |
| `/api/payment/addContract` | POST | alipay-pay-sign-server | 签约（开通代扣） |
| `/api/payment/terminateContract` | POST | alipay-pay-sign-server | 解约登记 |
| `/api/payment/requestPay` | POST | alipay-pay-sign-server | 支付申请（扣款） |
| `/api/payment/payQuery` | POST | alipay-pay-sign-server | 支付结果查询 |
| `/api/payment/requestRefund` | POST | alipay-pay-sign-server | 退款申请 |
| `/queryBlackList` | POST | blacklist-server | 查询黑名单 |
| `/addBlackList` | POST | blacklist-server | 新增黑名单 |
| `/deleteBlackList` | POST | blacklist-server | 删除黑名单 |

---

## 六、补充说明

### 6.1 获取行业数据接口详解

`/memberContract/channel/requestIndustryData` 是支付宝出行渠道的核心接口，用于在用户打开乘车码时获取卡数据。

**调用时机：**
- 用户打开支付宝乘车码页面时
- 用户下拉刷新二维码时
- 二维码过期需要重新生成时

**返回数据用途：**
```
cardData (HexString) → 支付宝前端解析 → 生成动态二维码 → 用户刷码
```

**关键流程：**
1. 查询用户信息（account-server）
2. 查询二维码状态（ticket-server）
3. 生成卡数据（industry-data-server）
4. 返回 `cardData` + `sign`

### 6.2 二维码生成机制

| 环节 | 说明 |
|------|------|
| **后端生成** | `industry-data-server` 生成 `cardData`（HexString），包含卡号、状态、时间戳等 |
| **前端生成** | 支付宝小程序/APP 基于 `cardData` 本地生成二维码图片 |
| **闸机识别** | 闸机扫码后解析二维码内容，获取 cardId 进行验证 |
| **动态刷新** | 二维码定期刷新（通常60秒），每次刷新重新调用 `requestIndustryData` |

---

*文档生成时间：2026-07-02*
*基于代码仓库 `/qditp` 中 `alipay-account-server`、`alipay-pay-sign-server`、`blacklist-server`、`fep-alipay-server` 等模块梳理。*

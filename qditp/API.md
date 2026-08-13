# qd-itp 接口文档（源码自动提取 + 示例）

> 基于 `qd-itp` 源码中 `*Controller.java` 提取，共 296 个接口。

---

## account-server

共 8 个接口。

### FepAlipayTripRequestApplicationController

#### 1. requestApplication

- **路径**：`/channel/requestApplication`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripRequestApplicationRespDTO
- **参数**：request: AlipayTripRequestApplicationReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardType": "示例值",
        "msisdn": "示例值",
        "extend1": "示例值",
        "extend2": "示例值",
        "cardIssueCode": "示例值"
    }
```

**响应示例**：
```json
    {
        "cardId": "示例值",
        "cardType": "示例值",
        "status": "示例值"
    }
```

### RequestApplicationController

#### 1. requestApplication

- **路径**：`/requestApplication`
- **HTTP**：PostMapping
- **返回类型**：RequestApplicationResult
- **参数**：request: RequestApplicationReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "00522906",
        "thirdPayId": null,
        "channel": null,
        "reqContractNo": null,
        "cardType": "02",
        "msisdn": null,
        "userName": null,
        "userId": null,
        "extend1": null,
        "extend2": null,
        "ticketCard": null,
        "companionFlag": null,
        "ticketLimit": null,
        "cardIssueCode": "0007"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "cardId": "0418061109608453",
        "cardType": "02",
        "signType": "01",
        "sign": "[REDACTED]"
    }
```

#### 2. requestAddPayChannel

- **路径**：`/requestAddPayChannel`
- **HTTP**：PostMapping
- **返回类型**：RequestAddPayChannelResult
- **参数**：request: RequestAddPayChannelReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "00522906",
        "cardId": "0418061109608453",
        "cardType": "02",
        "channel": "ALIPAY",
        "thirdPayId": "2088302232551032",
        "reqContractNo": "070000144856188719"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功"
    }
```

#### 3. queryUserInfo

- **路径**：`/queryUserInfo`
- **HTTP**：PostMapping
- **返回类型**：QueryUserInfoResult
- **参数**：request: QueryUserInfoReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "00522906",
        "cardId": "0418061109608453",
        "cardType": "02"
    }
```

**响应示例 1**（无有效账户）：
```json
    {
        "retCode": "8004",
        "retMsg": "没有账号卡片数据",
        "thirdUserId": null,
        "cardId": null,
        "cardType": null,
        "itpCardType": null,
        "channel": null,
        "thirdPayId": null,
        "reqContractNo": null,
        "hceData": null
    }
```

**响应示例 2**（查询成功）：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "thirdUserId": "0700001448",
        "cardId": "2607031119542741",
        "cardType": "02",
        "itpCardType": "03",
        "channel": "ALIPAY",
        "thirdPayId": "2088302232551032",
        "reqContractNo": "070000144856188719",
        "hceData": null
    }
```

- **路径**：`/requestRemovePayChannel`
- **HTTP**：PostMapping
- **返回类型**：RequestRemovePayChannelResult
- **参数**：request: RequestRemovePayChannelReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "channel": "示例值"
    }
```

**响应示例**：
```json
    // RequestRemovePayChannelResult 未找到定义
```

#### 5. queryUserInfo

- **路径**：`/queryUserInfo`
- **HTTP**：PostMapping
- **返回类型**：QueryUserInfoResult
- **参数**：request: QueryUserInfoReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值"
    }
```

**响应示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "itpCardType": "示例值",
        "channel": "示例值",
        "thirdPayId": "示例值",
        "reqContractNo": "示例值",
        "hceData": "示例值"
    }
```

#### 6. queryCardTypeByCardId

- **路径**：`/queryCardTypeByCardId`
- **HTTP**：GetMapping
- **返回类型**：QueryUserInfoResult
- **参数**：cardId: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "itpCardType": "示例值",
        "channel": "示例值",
        "thirdPayId": "示例值",
        "reqContractNo": "示例值",
        "hceData": "示例值"
    }
```

#### 7. updateHceData

- **路径**：`/updateHceData`
- **HTTP**：PostMapping
- **返回类型**：UpdateHceDataResult
- **参数**：request: UpdateHceDataReqDTO

**请求示例**：
```json
    {
        "cardId": "示例值",
        "hceData": "示例值"
    }
```

**响应示例**：
```json
    // UpdateHceDataResult 未找到定义
```

## acc-secure-server

共 8 个接口。

### AccSecureController

- 类路径：`/ci/acc/secure`

#### 1. requestQrLogicNumList

- **路径**：`/ci/acc/secure/ci/acc/secure`
- **HTTP**：RequestMapping
- **返回类型**：RequestQrLogicNumListRespDTO
- **参数**：request: RequestQrLogicNumListReqDTO

**请求示例**：
```json
    {
        "requestNum": "示例值"
    }
```

**响应示例**：
```json
    // RequestQrLogicNumListRespDTO 未找到定义
```

#### 2. requestCaKey

- **路径**：`/ci/acc/secure/requestQrLogicNumList`
- **HTTP**：PostMapping
- **返回类型**：RequestCaKeyRespDTO
- **参数**：request: RequestCaKeyReqDTO

**请求示例**：
```json
    {
        "keyIdx": "示例值"
    }
```

**响应示例**：
```json
    {
        "privateKey": "示例值",
        "publicKey": "示例值",
        "sm2KeyPair": "示例值"
    }
```

#### 3. requestUserSm2Key

- **路径**：`/ci/acc/secure/requestCaKey`
- **HTTP**：PostMapping
- **返回类型**：RequestUserSm2KeyRespDTO
- **参数**：request: RequestUserSm2KeyReqDTO

**请求示例**：
```json
    {
        "logicNum": "示例值",
        "logicNum": "示例值"
    }
```

**响应示例**：
```json
    {
        "privateKey": "示例值",
        "publicKey": "示例值",
        "sm2KeyPair": "示例值",
        "privatePrivateKey": "示例值",
        "publicKey": "示例值",
        "sm2KeyPair": "示例值"
    }
```

#### 4. requestSignPubkey

- **路径**：`/ci/acc/secure/requestUserSm2Key`
- **HTTP**：PostMapping
- **返回类型**：RequestSignPubkeyRespDTO
- **参数**：request: RequestSignPubkeyReqDTO

**请求示例**：
```json
    {
        "publicKeyX": "示例值",
        "userId": "示例值",
        "publicKeyEffectiveDate": "示例值",
        "caPrivateKey": "示例值",
        "caPublicKey": "示例值",
        "caSm2KeyPair": "示例值",
        "publicKeyX": "示例值",
        "userId": "示例值",
        "publicKeyEffectiveDate": "示例值",
        "caPrivateKey": "示例值",
        "caPublicKey": "示例值",
        "caSm2KeyPair": "示例值"
    }
```

**响应示例**：
```json
    {
        "signData": "示例值",
        "signData": "示例值"
    }
```

#### 5. requestExportUserPriKey

- **路径**：`/ci/acc/secure/requestSignPubkey`
- **HTTP**：PostMapping
- **返回类型**：RequestExportUserPriKeyRespDTO
- **参数**：request: RequestExportUserPriKeyReqDTO

**请求示例**：
```json
    {
        "privateKey": "示例值",
        "publicKey": "示例值",
        "kekIdx": "示例值",
        "privatePrivateKey": "示例值",
        "kekIdx": "示例值"
    }
```

**响应示例**：
```json
    {
        "userPrivateKeyByKes": "示例值",
        "userPrivateKeyByKes": "示例值"
    }
```

#### 6. requestSignInsData

- **路径**：`/ci/acc/secure/requestExportUserPriKey`
- **HTTP**：PostMapping
- **返回类型**：RequestSignInsDataRespDTO
- **参数**：request: RequestSignInsDataReqDTO

**请求示例**：
```json
    {
        "industryData": "示例值",
        "logicNum": "示例值",
        "industryData": "示例值",
        "logicNum": "示例值"
    }
```

**响应示例**：
```json
    {
        "industryDataSign": "示例值",
        "industryDataSign": "示例值"
    }
```

#### 7. requestDpk

- **路径**：`/ci/acc/secure/requestSignInsData`
- **HTTP**：PostMapping
- **返回类型**：RequestDpkRespDTO
- **参数**：request: RequestDpkReqDTO

**请求示例**：
```json
    {
        "logicNum": "示例值",
        "logicNum": "示例值"
    }
```

**响应示例**：
```json
    {
        "dpkByKek": "示例值",
        "dpkByKek": "示例值"
    }
```

#### 8. requestHecCardDate

- **路径**：`/ci/acc/secure/requestDpk`
- **HTTP**：PostMapping
- **返回类型**：RequestHecCardDateRespDTO
- **参数**：request: RequestHecCardDateReqDTO

**请求示例**：
```json
    {
        "otp": "示例值"
    }
```

**响应示例**：
```json
    {
        "hecData": "示例值",
        "logicNum": "示例值"
    }
```

## acc-security-server

共 14 个接口。

### CertificateController

#### 1. getPublicKey

- **路径**：`/get/card/certificate`
- **HTTP**：PostMapping
- **返回类型**：ResultVO<CardCertificateResult>
- **参数**：parm: CardCertificateParam

**请求示例**：
```json
    {
        "begin_Identifier": "示例值",
        "service_id": "示例值"
    }
```

**响应示例**：
```json
    {
        "begin_Identifier": "示例值",
        "service_id": "示例值",
        "cert_index": "示例值",
        "cert_sign": "示例值"
    }
```

### CommonMacController

#### 1. verifyMac1

- **路径**：`/verify/mac1`
- **HTTP**：PostMapping
- **返回类型**：ResultVO<Boolean>
- **参数**：param: InvestMac1Param

**请求示例**：
```json
    {
        "beforeAmt": 0,
        "txnAmt": 0,
        "devNodeId": "示例值",
        "randomNum": "示例值",
        "ticketCount": 0,
        "cardNo": "示例值",
        "mac": "示例值"
    }
```

**响应示例**：
```json
    // Boolean 未找到定义
```

#### 2. getMac2

- **路径**：`/get/mac2`
- **HTTP**：PostMapping
- **返回类型**：ResultVO<String>
- **参数**：param: InvestMac2Param

**请求示例**：
```json
    {
        "txnAmt": 0,
        "randomNum": "示例值",
        "devNodeId": "示例值",
        "ticketCount": 0,
        "cardNo": "示例值",
        "centerTime": "示例值"
    }
```

**响应示例**：
```json
    // String 未找到定义
```

### CommonSaleAndRefundController

#### 1. getSaleKey

- **路径**：`/get/saleAndRefund/key`
- **HTTP**：PostMapping
- **返回类型**：ResultVO<String>
- **参数**：param: SaleAndRefundParam

**请求示例**：
```json
    {
        "cardNo": "示例值",
        "randomNum": "示例值"
    }
```

**响应示例**：
```json
    // String 未找到定义
```

### CommonTacController

#### 1. verifyUlTac

- **路径**：`/singleticket/check`
- **HTTP**：PostMapping
- **返回类型**：ResultVO<Boolean>
- **参数**：param: SingleTicketTacParam

**请求示例**：
```json
    {
        "cardNo": "示例值",
        "commonTacStr": "示例值",
        "tac": "示例值"
    }
```

**响应示例**：
```json
    // Boolean 未找到定义
```

#### 2. verifyCpuTac

- **路径**：`/cpu/check`
- **HTTP**：PostMapping
- **返回类型**：ResultVO<Boolean>
- **参数**：param: CpuTacParam

**请求示例**：
```json
    {
        "cardNo": "示例值",
        "commonTacStr": "示例值",
        "tac": "示例值"
    }
```

**响应示例**：
```json
    // Boolean 未找到定义
```

### ItpRemainingController

- 类路径：`/ci/itp`

#### 1. requestCaKey

- **路径**：`/ci/itp/ci/itp`
- **HTTP**：RequestMapping
- **返回类型**：ResultVO
- **参数**：param: RequestCaKeyParam

**请求示例**：
```json
    {
        "keyIdx": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

#### 2. requestUserSm2Key

- **路径**：`/ci/itp/requestCaKey`
- **HTTP**：PostMapping
- **返回类型**：ResultVO
- **参数**：param: RequestUserSm2KeyParam

**请求示例**：
```json
    {
        "logicNum": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

#### 3. requestExportUserPriKey

- **路径**：`/ci/itp/requestUserSm2Key`
- **HTTP**：PostMapping
- **返回类型**：ResultVO
- **参数**：param: RequestExportUserPriKeyParam

**请求示例**：
```json
    {
        "privateKey": "示例值",
        "publicKey": "示例值",
        "kekIdx": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

#### 4. requestDPK

- **路径**：`/ci/itp/requestExportUserPriKey`
- **HTTP**：PostMapping
- **返回类型**：ResultVO
- **参数**：param: RequestDPKParam

**请求示例**：
```json
    {
        "logicNum": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

#### 5. requestHceCardData

- **路径**：`/ci/itp/requestDPK`
- **HTTP**：PostMapping
- **返回类型**：ResultVO
- **参数**：param: RequestHceCardDataParam

**请求示例**：
```json
    {
        "ticketCard": "示例值",
        "iptUserId": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

### ItpSignPubkeyController

- 类路径：`/ci/itp`

#### 1. requestSignPubkey

- **路径**：`/ci/itp/ci/itp`
- **HTTP**：RequestMapping
- **返回类型**：ResultVO
- **参数**：param: RequestSignPubkeyParam

**请求示例**：
```json
    {
        "publicKeyX": "示例值",
        "userId": "示例值",
        "publicKeyEffectiveDate": "示例值",
        "caPrivateKey": "示例值",
        "caPublicKey": "示例值",
        "caSm2KeyPair": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

#### 2. requestSignInsData

- **路径**：`/ci/itp/requestSignPubkey`
- **HTTP**：PostMapping
- **返回类型**：ResultVO
- **参数**：request: HttpServletRequest

**请求示例**：
```json
    // HttpServletRequest 未找到定义
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

#### 3. requestSignInsData

- **路径**：`/ci/itp/requestSignInsData`
- **HTTP**：PostMapping
- **返回类型**：ResultVO
- **参数**：param: RequestSignInsDataParam

**请求示例**：
```json
    {
        "industryData": "示例值",
        "logicNum": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": "示例值",
        "msg": "示例值",
        "data": null
    }
```

## collect-ticket-server

共 8 个接口。

### TicketCollectController

- 类路径：`/ci/app`

#### 1. requestBuySinlgeTicketMaxNum

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：RequestBuySingleTicketMaxNumRespDTO
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "buySinlgeTicketMaxNum": "示例值"
    }
```

#### 2. requestTicketPriceByStation

- **路径**：`/ci/app/requestBuySinlgeTicketMaxNum`
- **HTTP**：PostMapping
- **返回类型**：RequestTicketPriceByStationRespDTO
- **参数**：request: RequestTicketPriceByStationReqDTO

**请求示例**：
```json
    {
        "entryStationCode": "示例值",
        "exitStationCode": "示例值",
        "entryStationCode": "示例值",
        "exitStationCode": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "ticketPrice": "示例值"
    }
```

#### 3. requestPaymentInfo

- **路径**：`/ci/app/requestTicketPriceByStation`
- **HTTP**：PostMapping
- **返回类型**：RequestPaymentInfoRespDTO
- **参数**：request: RequestPaymentInfoReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "payChannelCode": "示例值",
        "channelType": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "payChannelCode": "示例值",
        "paymentInfo": "示例值",
        "signType": "示例值",
        "sign": "示例值"
    }
```

#### 4. requestOrder

- **路径**：`/ci/app/requestPaymentInfo`
- **HTTP**：PostMapping
- **返回类型**：CreateTicketCollectOrderRespDTO
- **参数**：request: CreateTicketCollectOrderReqDTO

**请求示例**：
```json
    {
        "userId": "示例值",
        "entryStationCode": "示例值",
        "exitStationCode": "示例值",
        "singelTicketNum": 0,
        "ticketPrice": 0,
        "singleTicketType": 0,
        "channelCode": "示例值",
        "channelType": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值",
        "orderStatus": 0,
        "ticketPrice": 0,
        "qrcodeGenDate": "示例值",
        "randomFact": "示例值",
        "singleTicketType": 0
    }
```

#### 5. queryTicketCollectOrder

- **路径**：`/ci/app/requestOrder`
- **HTTP**：PostMapping
- **返回类型**：QueryTicketCollectOrderRespDTO
- **参数**：request: QueryTicketCollectOrderReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "userId": "示例值",
        "qrcodeGenDate": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值",
        "userId": "示例值",
        "entryStationCode": "示例值",
        "exitStationCode": "示例值",
        "ticketPrice": 0,
        "singelTicketNum": 0,
        "channelCode": "示例值",
        "orderStatus": 0,
        "collectStatus": 0,
        "deviceId": "示例值",
        "qrcodeGenDate": "示例值",
        "randomFact": "示例值",
        "payResult": 0,
        "payAmount": 0,
        "payDate": "示例值",
        "actualTakeTicketNum": 0,
        "errorCode": "示例值",
        "errorMessage": "示例值"
    }
```

#### 6. ticketCollectNotify

- **路径**：`/ci/app/queryTicketCollectOrder`
- **HTTP**：PostMapping
- **返回类型**：TicketCollectNotifyRespDTO
- **参数**：request: TicketCollectNotifyReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "userId": "示例值",
        "collectStatus": 0,
        "deviceId": "示例值",
        "actualTakeTicketNum": 0,
        "faultSlipSeq": "示例值",
        "errorCode": "示例值",
        "errorMessage": "示例值",
        "ticketDetails": null,
        "inOrderNo": 0,
        "takeTicketDate": "示例值",
        "ticketLogicNum": "示例值",
        "transDate": "示例值",
        "transAmount": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 7. cancelTicketCollectOrder

- **路径**：`/ci/app/ticketCollectNotify`
- **HTTP**：PostMapping
- **返回类型**：CancelTicketCollectOrderRespDTO
- **参数**：request: CancelTicketCollectOrderReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "userId": "示例值",
        "cancelReason": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值",
        "orderStatus": 0
    }
```

#### 8. ticketCollectPayNotify

- **路径**：`/ci/app/cancelTicketCollectOrder`
- **HTTP**：PostMapping
- **返回类型**：TicketCollectPayNotifyRespDTO
- **参数**：request: TicketCollectPayNotifyReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "merchantOrderNo": "示例值",
        "channelOrderNo": "示例值",
        "status": "示例值",
        "payTime": "示例值",
        "totalAmount": 0,
        "cashAmount": 0,
        "couponAmount": 0,
        "payUserId": "示例值",
        "paymentVendor": "示例值",
        "options": "示例值",
        "sign": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

## daily-ticket-server

共 8 个接口。

### DailyTicketController

- 类路径：`/ci/daily-ticket`

#### 1. requestCountingOrder

- **路径**：`/ci/daily-ticket/ci/daily-ticket`
- **HTTP**：RequestMapping
- **返回类型**：DailyTicketOrderResult
- **参数**：request: DailyTicketOrderReqDTO

**请求示例**：
```json
    {
        "orderSource": "示例值",
        "ticketPrice": 0,
        "cardType": "示例值",
        "showType": "示例值",
        "userId": "示例值"
    }
```

**响应示例**：
```json
    {
        "orderNo": "示例值"
    }
```

#### 2. requestPay

- **路径**：`/ci/daily-ticket/requestCountingOrder`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketPayResult
- **参数**：request: DailyTicketPayReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "orderType": "示例值",
        "orderNo": "示例值",
        "payChannelCode": "示例值",
        "phone": "示例值",
        "channelType": "示例值",
        "cardId": "示例值"
    }
```

**响应示例**：
```json
    {
        "signType": "示例值",
        "sign": "示例值",
        "payChannelCode": "示例值",
        "paymentInfo": "示例值",
        "discountInfo": "示例值"
    }
```

#### 3. requestPayResult

- **路径**：`/ci/daily-ticket/payment/requestPay`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketPayQueryResult
- **参数**：request: DailyTicketOrderNoReqDTO

**请求示例**：
```json
    {
        "orderType": "示例值",
        "orderNo": "示例值"
    }
```

**响应示例**：
```json
    {
        "tradeNo": "示例值",
        "payResult": "示例值",
        "payAmount": 0,
        "payDate": null,
        "discountInfo": "示例值",
        "payChannel": "示例值",
        "channelDiscount": 0,
        "couponDiscount": 0
    }
```

#### 4. requestRefundTicket

- **路径**：`/ci/daily-ticket/payment/requestPayResult`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketRefundResult
- **参数**：request: DailyTicketOrderNoReqDTO

**请求示例**：
```json
    {
        "orderType": "示例值",
        "orderNo": "示例值"
    }
```

**响应示例**：
```json
    {
        "refundType": "示例值",
        "orderNo": "示例值",
        "refundResultDesc": "示例值",
        "refundResult": "示例值",
        "refundDate": "示例值",
        "refundAmount": "示例值"
    }
```

#### 5. cancelOrder

- **路径**：`/ci/daily-ticket/payment/requestRefundTicket`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：request: DailyTicketOrderNoReqDTO

**请求示例**：
```json
    {
        "orderType": "示例值",
        "orderNo": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 6. updateTicket

- **路径**：`/ci/daily-ticket/ticket/cancelOrder`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：request: DailyTicketActivateReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "operationDate": "示例值",
        "period": 0,
        "orderNo": "示例值",
        "cardIssue": "示例值",
        "discountAmount": 0,
        "ticketType": "示例值",
        "actualTimes": 0,
        "transSeq": 0,
        "transAmount": 0,
        "cardNum": "示例值",
        "countingStart": 0,
        "transDate": 0,
        "showType": "示例值",
        "payChannel": "示例值",
        "ticketCode": "示例值",
        "ticketName": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 7. updateAndNotice

- **路径**：`/ci/daily-ticket/ticket/updateTicket`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：request: DailyTicketUsedNoticeReqDTO

**请求示例**：
```json
    {
        "cardNum": "示例值",
        "period": 0,
        "countingEnd": 0,
        "discountAmount": 0,
        "ticketName": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 8. receivePayResult

- **路径**：`/ci/daily-ticket/ticket/updateAndNotice`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：request: DailyTicketPayCallbackReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "tradeNo": "示例值",
        "paymentOrderNo": "示例值",
        "payResult": "示例值",
        "payAmount": 0,
        "payDate": null,
        "payChannel": "示例值",
        "rawBody": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

## collect-pay-server

共 24 个接口。

### BomOrderController

- 类路径：`/itpbom/ci/bom`

#### 1. notiDeviceHeard

- **路径**：`/itpbom/ci/bom/notiDeviceHeard`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "03",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727141449",
        "deviceId": "06220E01",
        "signType": "00",
        "sign": "null",
        "bizData": "null"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功"
    }
```

#### 2. copyBaseParams

- **路径**：`/itpbom/ci/bom/notiDeviceHeard`
- **HTTP**：PostMapping
- **返回类型**：<T extends BaseRequestDTO> T
- **参数**：baseRequest: BaseRequestDTO; clazz: Class<T>

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // <T extends BaseRequestDTO T 未找到定义
```

#### 3. requestGenNoCashOrder

- **路径**：`/itpbom/ci/bom/requestGenNoCashOrder`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 4. requestPayment

- **路径**：`/itpbom/ci/bom/requestPayment`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 5. requestGetPayResult

- **路径**：`/itpbom/ci/bom/requestGetPayResult`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 6. notiBusResult

- **路径**：`/itpbom/ci/bom/notiBusResult`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

### CollectPayController

- 类路径：`/ci/app`

#### 1. requestPay

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：RequestPayRespDTO
- **参数**：request: RequestPayReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "scene": "示例值",
        "paymentVendor": "示例值",
        "amount": 0,
        "industryType": "示例值",
        "subject": "示例值",
        "body": "示例值",
        "requestSignSeq": "示例值",
        "thirdUserId": "示例值",
        "industryDetail": "示例值",
        "orderTimeOut": 0,
        "authCode": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "ipAddress": "示例值",
        "remark": "示例值",
        "payType": "示例值",
        "orderNo": "示例值",
        "scene": "示例值",
        "paymentVendor": "示例值",
        "amount": 0,
        "industryType": "示例值",
        "subject": "示例值",
        "body": "示例值",
        "requestSignSeq": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "industryDetail": "示例值",
        "orderTimeOut": 0,
        "authCode": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "ipAddress": "示例值",
        "remark": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值",
        "merchantOrderNo": "示例值",
        "channelOrderNo": "示例值",
        "data": "示例值"
    }
```

#### 2. payQuery

- **路径**：`/ci/app/requestPay`
- **HTTP**：PostMapping
- **返回类型**：PayQueryRespDTO
- **参数**：request: PayQueryReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "merchantOrderNo": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值",
        "merchantOrderNo": "示例值",
        "channelOrderNo": "示例值",
        "status": "示例值",
        "payDate": "示例值",
        "totalAmount": 0,
        "cashAmount": 0,
        "couponAmount": 0,
        "channelAccount": "示例值",
        "paymentVendor": "示例值"
    }
```

#### 3. requestRefund

- **路径**：`/ci/app/payQuery`
- **HTTP**：PostMapping
- **返回类型**：RequestRefundRespDTO
- **参数**：request: RequestRefundReqDTO

**请求示例**：
```json
    {
        "refundOrderNo": "示例值",
        "merchantOrderNo": "示例值",
        "orderNo": "示例值",
        "refundAmount": 0,
        "orderNo": "示例值",
        "refundReason": "示例值",
        "refundAmt": "示例值",
        "orderNo": "示例值",
        "refundAmount": 0,
        "refundReason": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "merchantRefundNo": "示例值",
        "refundNo": "示例值",
        "channelRefundNo": "示例值",
        "refundTime": "示例值",
        "retCode": "示例值",
        "retMsg": "示例值",
        "refundResult": "示例值",
        "refundResultDesc": "示例值",
        "refundNo": "示例值"
    }
```

#### 4. refundQuery

- **路径**：`/ci/app/requestRefund`
- **HTTP**：PostMapping
- **返回类型**：RefundQueryRespDTO
- **参数**：request: RefundQueryReqDTO

**请求示例**：
```json
    {
        "refundOrderNo": "示例值",
        "merchantRefundNo": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "refundOrderNo": "示例值",
        "merchantRefundNo": "示例值",
        "channelRefundNo": "示例值",
        "orderNo": "示例值",
        "status": "示例值",
        "refundAmount": 0,
        "refundTime": "示例值"
    }
```

### TvmOrderController

- 类路径：`/itptvm/ci/tvm`

#### 1. notiDeviceHeard

- **路径**：`/itptvm/ci/tvm/itptvm/ci/tvm`
- **HTTP**：RequestMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 2. copyBaseParams

- **路径**：`/itptvm/ci/tvm/notiDeviceHeard`
- **HTTP**：PostMapping
- **返回类型**：<T extends BaseRequestDTO> T
- **参数**：baseRequest: BaseRequestDTO; clazz: Class<T>

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // <T extends BaseRequestDTO T 未找到定义
```

#### 3. requestGenSjtOrder

- **路径**：`/itptvm/ci/tvm/requestGenSjtOrder`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 4. requestPayment

- **路径**：`/itptvm/ci/tvm/requestPayment`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 5. requestPayResult

- **路径**：`/itptvm/ci/tvm/requestPayResult`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 6. notiTakeTicketResult

- **路径**：`/itptvm/ci/tvm/notiTakeTicketResult`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 7. notiTakeTicketFailResult

- **路径**：`/itptvm/ci/tvm/notiTakeTicketFailResult`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 8. requestRefund

- **路径**：`/itptvm/ci/tvm/requestRefund`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 9. requestActiveTicket

- **路径**：`/itptvm/ci/tvm/requestActiveTicket`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 10. requestTakeTicketAuth

- **路径**：`/itptvm/ci/tvm/requestTakeTicketAuth`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 11. requestTopup

- **路径**：`/itptvm/ci/tvm/requestTopup`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: BaseRequestDTO

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 12. topupCardResultNoti

- **路径**：`/itptvm/ci/tvm/topupCardResultNoti`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: TopupCardResultNotiReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "ticketLogicNum": "示例值",
        "ticketPhysicsNum": "示例值",
        "transDate": "示例值",
        "transAmount": "示例值",
        "afterAmount": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 13. topupCardFailNoti

- **路径**：`/itptvm/ci/tvm/topupCardFailNoti`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：baseRequest: TopupCardFailNotiReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "ticketLogicNum": "示例值",
        "ticketPhysicsNum": "示例值",
        "topupStatus": "示例值",
        "faultOccurDate": "示例值",
        "faultSlipSeq": "示例值",
        "errorCode": "示例值",
        "errorMessage": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

#### 14. requestPayOrderDetail

- **路径**：`/itptvm/ci/tvm/requestPayOrderDetail`
- **HTTP**：PostMapping
- **返回类型**：JSONObject
- **参数**：request: RequestPayResultReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值"
    }
```

**响应示例**：
```json
    // JSONObject 未找到定义
```

## pay-sign-server

共 17 个接口。

### PaySignAlipayTripController

- 类路径：`/channel`

#### 1. requestContractAdvisory

- **路径**：`/channel/channel`
- **HTTP**：RequestMapping
- **返回类型**：RequestContractAdvisoryRespDTO
- **参数**：request: RequestContractAdvisoryReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值"
    }
```

**响应示例**：
```json
    // RequestContractAdvisoryRespDTO 未找到定义
```

#### 2. requestContractResult

- **路径**：`/channel/requestContractAdvisory`
- **HTTP**：PostMapping
- **返回类型**：RequestContractResultRespDTO
- **参数**：request: RequestContractResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值"
    }
```

**响应示例**：
```json
    {
        "status": "示例值",
        "payUserId": "示例值",
        "payAccountId": "示例值",
        "payAgreementNo": "示例值"
    }
```

#### 3. requestTermination

- **路径**：`/channel/requestContractResult`
- **HTTP**：PostMapping
- **返回类型**：RequestTerminationRespDTO
- **参数**：request: RequestTerminationReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值"
    }
```

**响应示例**：
```json
    // RequestTerminationRespDTO 未找到定义
```

#### 4. addContract

- **路径**：`/channel/requestTermination`
- **HTTP**：PostMapping
- **返回类型**：RequestSignInfoResult
- **参数**：request: AlipayTripAddContractReqDTO

**请求示例**：
```json
    {
        "channel": "示例值",
        "thirdUserId": "示例值",
        "agreementCode": "示例值",
        "channelAgreementCode": "示例值",
        "channelUserAccount": "示例值",
        "cardIssueCode": "示例值",
        "thirdUserId": "示例值",
        "channel": "示例值",
        "agreementCode": "示例值",
        "channelAgreementCode": "示例值",
        "channelUserAccount": "示例值",
        "cardIssueCode": "示例值"
    }
```

**响应示例**：
```json
    {
        "requestStartSdkInfo": "示例值"
    }
```

### PaySignAlipayTripNotifyController

- 类路径：`/notify`

#### 1. receiveSignResult

- **路径**：`/notify/notify`
- **HTTP**：RequestMapping
- **返回类型**：Object
- **参数**：request: ReceiveSignResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "paymentVendor": "示例值",
        "payUserId": "示例值",
        "payAgreementNo": "示例值",
        "status": "示例值",
        "displayAccount": "示例值",
        "options": "示例值",
        "signTime": "示例值"
    }
```

**响应示例**：
```json
    // Object 未找到定义
```

#### 2. receiveTerminationResult

- **路径**：`/notify/receiveSignResult`
- **HTTP**：PostMapping
- **返回类型**：Object
- **参数**：request: ReceiveTerminationResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "payAgreementNo": "示例值",
        "paymentVendor": "示例值",
        "status": "示例值",
        "dismissalTime": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "payAgreementNo": "示例值",
        "paymentVendor": "示例值",
        "status": "示例值",
        "dismissalTime": "示例值"
    }
```

**响应示例**：
```json
    // Object 未找到定义
```

### PaySignAppController

- 类路径：`/ci/app`

#### 1. requestContractAdvisory

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：RequestContractAdvisoryRespDTO
- **参数**：request: RequestContractAdvisoryReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值"
    }
```

**响应示例**：
```json
    // RequestContractAdvisoryRespDTO 未找到定义
```

#### 2. requestContractResult

- **路径**：`/ci/app/requestContractAdvisory`
- **HTTP**：PostMapping
- **返回类型**：RequestContractResultRespDTO
- **参数**：request: RequestContractResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值"
    }
```

**响应示例**：
```json
    {
        "status": "示例值",
        "payUserId": "示例值",
        "payAccountId": "示例值",
        "payAgreementNo": "示例值"
    }
```

#### 3. requestTermination

- **路径**：`/ci/app/requestContractResult`
- **HTTP**：PostMapping
- **返回类型**：RequestTerminationRespDTO
- **参数**：request: RequestTerminationReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "paymentVendor": "示例值"
    }
```

**响应示例**：
```json
    // RequestTerminationRespDTO 未找到定义
```

#### 4. requestPay

- **路径**：`/ci/app/requestTermination`
- **HTTP**：PostMapping
- **返回类型**：RequestPayResult
- **参数**：request: RequestPayReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "scene": "示例值",
        "paymentVendor": "示例值",
        "amount": 0,
        "industryType": "示例值",
        "subject": "示例值",
        "body": "示例值",
        "requestSignSeq": "示例值",
        "thirdUserId": "示例值",
        "industryDetail": "示例值",
        "orderTimeOut": 0,
        "authCode": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "ipAddress": "示例值",
        "remark": "示例值",
        "payType": "示例值",
        "orderNo": "示例值",
        "scene": "示例值",
        "paymentVendor": "示例值",
        "amount": 0,
        "industryType": "示例值",
        "subject": "示例值",
        "body": "示例值",
        "requestSignSeq": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "industryDetail": "示例值",
        "orderTimeOut": 0,
        "authCode": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "ipAddress": "示例值",
        "remark": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "code": 0,
        "msg": "示例值",
        "success": false,
        "data": null,
        "orderNo": "示例值",
        "merchantOrderNo": "示例值",
        "channelOrderNo": "示例值",
        "payData": "示例值"
    }
```

#### 5. requestRefund

- **路径**：`/ci/app/requestPay`
- **HTTP**：PostMapping
- **返回类型**：RequestRefundResult
- **参数**：request: RequestRefundReqDTO

**请求示例**：
```json
    {
        "refundOrderNo": "示例值",
        "merchantOrderNo": "示例值",
        "orderNo": "示例值",
        "refundAmount": 0,
        "orderNo": "示例值",
        "refundReason": "示例值",
        "refundAmt": "示例值",
        "orderNo": "示例值",
        "refundAmount": 0,
        "refundReason": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "code": 0,
        "msg": "示例值",
        "success": false,
        "data": null,
        "orderNo": "示例值",
        "refundOrderNo": "示例值",
        "merchantRefundNo": "示例值",
        "refundNo": "示例值",
        "channelRefundNo": "示例值",
        "refundTime": "示例值"
    }
```

#### 6. receiveSignResult

- **路径**：`/ci/app/requestRefund`
- **HTTP**：PostMapping
- **返回类型**：PaySignCallbackResult
- **参数**：request: ReceiveSignResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "paymentVendor": "示例值",
        "payUserId": "示例值",
        "payAgreementNo": "示例值",
        "status": "示例值",
        "displayAccount": "示例值",
        "options": "示例值",
        "signTime": "示例值"
    }
```

**响应示例**：
```json
    // PaySignCallbackResult 未找到定义
```

#### 7. receivePayResult

- **路径**：`/ci/app/receiveSignResult`
- **HTTP**：PostMapping
- **返回类型**：PaySignCallbackResult
- **参数**：request: ReceivePayResultReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "merchantOrderNo": "示例值",
        "channelOrderNo": "示例值",
        "status": "示例值",
        "payTime": "示例值",
        "totalAmount": 0,
        "cashAmount": 0,
        "couponAmount": 0,
        "payUserId": "示例值",
        "paymentVendor": "示例值",
        "options": "示例值"
    }
```

**响应示例**：
```json
    // PaySignCallbackResult 未找到定义
```

#### 8. receiveTerminationResult

- **路径**：`/ci/app/receivePayResult`
- **HTTP**：PostMapping
- **返回类型**：Object
- **参数**：request: ReceiveTerminationResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "payAgreementNo": "示例值",
        "paymentVendor": "示例值",
        "status": "示例值",
        "dismissalTime": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "payAgreementNo": "示例值",
        "paymentVendor": "示例值",
        "status": "示例值",
        "dismissalTime": "示例值"
    }
```

**响应示例**：
```json
    // Object 未找到定义
```

### PaySignController

#### 1. requestSignInfo

- **路径**：`/requestSignInfo`
- **HTTP**：PostMapping
- **返回类型**：RequestSignInfoResult
- **参数**：request: RequestSignInfoReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "displayAccount": "示例值",
        "payChannelCode": "示例值",
        "requestSignSeq": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "options": "示例值",
        "authCode": "示例值",
        "mobilePhone": "示例值",
        "certNo": "示例值",
        "custName": "示例值",
        "token": "示例值",
        "payUserId": "示例值",
        "bankCardNo": "示例值",
        "thirdUserId": "示例值",
        "certNo": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "options": "示例值",
        "authCode": "示例值",
        "mobilePhone": "示例值",
        "payChannelCode": "示例值",
        "requestSignSeq": "示例值",
        "displayAccount": "示例值",
        "token": "示例值",
        "payUserId": "示例值",
        "bankCardNo": "示例值",
        "custName": "示例值",
        "other": "示例值"
    }
```

**响应示例**：
```json
    {
        "requestStartSdkInfo": "示例值"
    }
```

### PaySignNotifyController

- 类路径：`/app`

#### 1. receiveSignResult

- **路径**：`/app/app`
- **HTTP**：RequestMapping
- **返回类型**：PaySignCallbackResult
- **参数**：request: ReceiveSignResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "paymentVendor": "示例值",
        "payUserId": "示例值",
        "payAgreementNo": "示例值",
        "status": "示例值",
        "displayAccount": "示例值",
        "options": "示例值",
        "signTime": "示例值"
    }
```

**响应示例**：
```json
    // PaySignCallbackResult 未找到定义
```

### TerminationNotifyController

- 类路径：`/ticket`

#### 1. receiveTerminationResult

- **路径**：`/ticket/ticket`
- **HTTP**：RequestMapping
- **返回类型**：BaseRespDTO
- **参数**：request: ReceiveTerminationResultReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "payAgreementNo": "示例值",
        "paymentVendor": "示例值",
        "status": "示例值",
        "dismissalTime": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "requestSignSeq": "示例值",
        "agreementNo": "示例值",
        "payAgreementNo": "示例值",
        "paymentVendor": "示例值",
        "status": "示例值",
        "dismissalTime": "示例值"
    }
```

**响应示例**：
```json
    {
        "code": 0,
        "msg": "示例值",
        "success": false,
        "data": "示例值",
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

## ticket-server

共 10 个接口。

### AlipayTripController

- 类路径：`/ci/channel`

#### 1. findTravelList

- **路径**：`/ci/channel/ci/channel`
- **HTTP**：RequestMapping
- **返回类型**：AlipayTripFindTravelListRespDTO
- **参数**：request: AlipayTripFindTravelListReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "0700001448",
        "page": "1",
        "size": "10",
        "debitRequestResult": "",
        "invoice": "",
        "startDate": "20260711111500",
        "endDate": "20260727111833"
    }
```

**响应示例**：
```json
    {
        "pageNumber": 1,
        "pageSize": 10,
        "totalPage": 1,
        "totalCount": 2,
        "ticketTransRecord": [
            {
                "orderNo": "GT20260727111833863542741",
                "tradeOrderNo": "2026072722001451031452868663",
                "payTradeOrderNo": "2026072722001451031452868663",
                "payOrderNoDate": "20260727111836",
                "payStatus": "SUCCESS",
                "payAmount": "8",
                "entryId": "07000014482026072711182001",
                "exitId": "07000014482026072711183302",
                "cardId": "2607031119542741",
                "transTime": "2026-07-27 11:18:36",
                "ticketType": "0441",
                "discountFee": null,
                "discountInfo": null,
                "invoice": null,
                "countingTimes": null,
                "countingFlag": null,
                "entryStationName": "山东大学",
                "exitStationName": "山东大学",
                "payChannelCode": "07",
                "debitRequestResult": null
            },
            {
                "orderNo": "GT20260727111454140542741",
                "tradeOrderNo": "2026072722001451031453263852",
                "payTradeOrderNo": "2026072722001451031453263852",
                "payOrderNoDate": "20260727111454",
                "payStatus": "SUCCESS",
                "payAmount": "4",
                "entryId": "07000014482026072711135501",
                "exitId": "07000014482026072711145402",
                "cardId": "2607031119542741",
                "transTime": "2026-07-27 11:15:00",
                "ticketType": "0441",
                "discountFee": null,
                "discountInfo": null,
                "invoice": null,
                "countingTimes": null,
                "countingFlag": null,
                "entryStationName": "山东大学",
                "exitStationName": "山东大学",
                "payChannelCode": "07",
                "debitRequestResult": null
            }
        ]
    }
```

#### 2. findTravelDetail

- **路径**：`/ci/channel/findTravelDetail`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripFindTravelDetailRespDTO
- **参数**：request: AlipayTripFindTravelDetailReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "0700001448",
        "orderNo": null,
        "handleDateTime": "20260727111355",
        "trxType": "01",
        "cardId": "2607031119542741"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "entryStationName": "山东大学",
        "entryDate": "20260727111355",
        "exitStationName": "山东大学",
        "exitDate": "20260727111355",
        "payAmount": "0",
        "totalAmount": "0",
        "orderExpType": "0",
        "tradeOrderNo": "1",
        "payTradeOrderNo": null,
        "payOrderNoDate": "20260727111355",
        "payChannelCode": "07",
        "debitRequestResult": null,
        "discountFee": null,
        "discountInfo": "[]",
        "companionFlag": "",
        "cardNum": "2607031119542741",
        "ticketCode": "0441",
        "countingTimes": null,
        "countingFlag": "",
        "invoice": null
    }
```

### TicketAgmController

- 类路径：`/ci/agm`

#### 1. notifyVerifyResult

- **路径**：`/ci/agm/notiVerifyResult`
- **HTTP**：PostMapping
- **返回类型**：NotifyVerifyResultRespDTO
- **参数**：request: NotifyVerifyResultReqDTO

**请求示例**：
```json
    {
        "deviceId": "11380406",
        "itpUserId": "0700001448",
        "trxType": "01",
        "issueChannelCode": "07",
        "signChannelCode": "07",
        "cardId": "2607031119542741",
        "cardType": "0441",
        "handleDateTime": "20260727111355",
        "handleStationCode": "1134",
        "trxAmount": "0",
        "overtimeAmount": "0",
        "lastTicketStatus": "03",
        "handleResultCode": "000",
        "lastHandleStationCode": "FFFF",
        "lastHandleDateTime": "20260727111301",
        "ticketTransSeq": "1",
        "reserve1": null,
        "reserve2": null
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功"
    }
```

### TicketRideStatusController

- 类路径：`/ci/app`

#### 1. registerRideStatus

- **路径**：`/ci/app/registerRideStatus`
- **HTTP**：PostMapping
- **返回类型**：RegisterRideStatusRespDTO
- **参数**：request: RegisterRideStatusReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "0700001448",
        "cardId": "2607031119542741",
        "cardType": "02",
        "msisdn": null,
        "cardIssueCode": null,
        "channel": null,
        "thirdPayId": null
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "cardId": "2607031119542741",
        "itpUserId": "0700001448",
        "cardStatus": "03"
    }
```

#### 2. queryQrCodeStatus

- **路径**：`/ci/app/queryQrCodeStatus`
- **HTTP**：PostMapping
- **返回类型**：QueryStatusRespDTO
- **参数**：request: QueryStatusReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "0700001448",
        "cardId": "2607031119542741"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "thirdUserId": "0700001448",
        "cardId": "2607031119542741",
        "status": "03",
        "gateInStation": "FFFF",
        "gateInTime": "00000000000000",
        "lastTxnStation": "FFFF",
        "lastTxnTime": "00000000000000",
        "txnSeq": "0"
    }
```

#### 3. queryUserItinerary

- **路径**：`/ci/app/queryUserItinerary`
- **HTTP**：PostMapping
- **返回类型**：QueryUserItineraryResult
- **参数**：request: QueryUserItineraryReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 4. requestExcessFare

- **路径**：`/ci/app/requestExcessFare`
- **HTTP**：PostMapping
- **返回类型**：RequestExcessFareResult
- **参数**：request: RequestExcessFareReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 5. queryEntryDevice

- **路径**：`/ci/app/queryEntryDevice`
- **HTTP**：GetMapping
- **返回类型**：String
- **参数**：cardId: String

**请求示例**：
```json
    // GET 参数示例：cardId=2607031119542741
```

**响应示例**：
```json
    "11380409"
```

### TicketTransController

- 类路径：`/ci/app`

#### 1. requestTransList

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：RequestTransListResult
- **参数**：request: RequestTransListReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "pageNumber": 0,
        "pageSize": 0,
        "totalPage": 0,
        "startDate": "示例值",
        "endDate": "示例值",
        "debitRequestResult": "示例值",
        "ticketCode": "示例值",
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "pageNumber": 0,
        "pageSize": 0,
        "totalPage": 0,
        "startDate": "示例值",
        "endDate": "示例值",
        "debitRequestResult": "示例值",
        "ticketCode": "示例值",
        "offset": 0,
        "limit": 0
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "pageNumber": "示例值",
        "pageSize": "示例值",
        "totalPage": "示例值",
        "ticketTransRecord": null,
        "retCode": "示例值",
        "retMsg": "示例值",
        "pageNumber": "示例值",
        "pageSize": "示例值",
        "totalPage": "示例值",
        "ticketTransRecord": null
    }
```

## industry-data-server

共 1 个接口。

### IndustryDataController

- 类路径：`/ci/industry`

#### 1. buildCardData

- **路径**：`/ci/industry/ci/industry`
- **HTTP**：RequestMapping
- **返回类型**：IndustryCardDataBuildRespDTO
- **参数**：request: IndustryCardDataBuildReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "0700001448",
        "cardId": "2607031119542741",
        "cardType": "0441",
        "ticketStatus": "04",
        "lastTxnStation": "0234",
        "lastTxnTime": "20260727144025",
        "gateInStation": "0234",
        "gateInTime": "20260727144025",
        "txnSeq": "1",
        "issueChannelCode": "07",
        "signChannelCode": "07"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "cardData": "29B92CA804023431FA2A5931FA62992607031119542741044100000001070701000000007F1309C6",
        "unsignedIndustryData": "29B92CA804023431FA2A5931FA62992607031119542741044100000001070701"
    }
```

## fep-app-server

共 30 个接口。

### AppAccountController

- 类路径：`/ci/app`

#### 1. requestApplication

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：RequestApplicationResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "cardId": "示例值",
        "cardType": "示例值",
        "signType": "示例值",
        "sign": "示例值"
    }
```

#### 2. requestKeyList

- **路径**：`/ci/app/requestApplication`
- **HTTP**：PostMapping
- **返回类型**：RequestKeyListResult
- **参数**：request: CommonFormRequest

**请求示例 1**：
```json
    {
        "providerId": "01",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727103339",
        "deviceId": "06220E01",
        "signType": "00",
        "sign": "caQHtU6doaVvigTjrTIH0lu8SnwUIstxQjv0PzGAqyjtOWxlbEpnJhBnHbKJ7xPyI7rShFWpGvIqy1hS/BHE/a+T6WcGGzp+bOg3haxj6mexbEsxOxBsPUOXlBWAmPk7VA3MdbgiJ+om/tjBjjT1peZ7dGRhC6UzE8GtKyWipTS2BTd7ogejf41J4FJvO47AwMFBSheWbFVetjWJxbDQ4Wlcn2BQXf5sslnLGas0QEZxL4zRsibTxzAKlPOX+trphc7V61PELe2NNicxEabVt/3YR4+TAstscxdYsi3+OWhRKAbEDfWA3uD6y1+Wy0Mg+V5G3b4QYogsiEO7uIv88Q==",
        "bizData": "{\"thirdUserId\":\"00522906\",\"cardId\":\"0418061109608453\",\"cardType\":\"02\"}"
    }
```

**请求示例 2**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727111301",
        "deviceId": "06220801",
        "signType": "00",
        "sign": "KufXLsA+xCnHKPNFO+rPhU7UQ1JjkTig4s5Uyu+aoF9/hypXzi1v0lavbS+hoJX2mINcpsXUHBI1GvrlCzaNm5KbV3eVOQDwkJSZ6AegRbn7KCl6i/jQp3J9Wwf1eQCln1akHIeXrPq5MB/Gt0p1RokM7Tf64i4f6zOkPjWTcDiEw16+auxnBIOu/FXupKepcnHa7ADZ67/eWls6OvF5Yl08gSPsWmzv4a9x7mvH6mgehCFF4FLLZwLiPf+0O+ECIBSvrH6EODg+PMSIX3AMFjdmSpYkxvVIHzaP8CbnKtdXZdEyoP4aVRhRhieJy5rpcyrh7Tzy0baXozTJ+4p0jw==",
        "bizData": "{\"thirdUserId\":\"0700001448\",\"cardId\":\"2607031119542741\",\"cardType\":\"02\"}"
    }
```

**响应示例 1**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "signType": "01",
        "sign": "",
        "keyList": [
            {
                "keyId": "01",
                "keyType": "1",
                "keyUserId": "29B92CA8",
                "keyPrivate": "mNL8de2vFZf7ncsSeWHs4jIDs7/3Ryt7+cGWDCn8yXmeXDpW8mfUrRmZWFHQI/eQG36FB86xGGTpIAXCnB9CHvddhKFIfMml",
                "keyPublic": "89E2CFD4E075A2D3C412744FCCEBE374650110FC60AB5FD09D9C8E7DBAA7653BE56A7B475174C5B8780CCF745670F4C4D7EB1214247E8D3939F78896D59ED655",
                "keyPublicEffectiveDate": "3203343D",
                "signData": "C13983BB4FED50E99E625BF2487AD91C488A87256DB1923F6EA3760AE59377A3C251820E6917C08275C7740EEE4559823FAE2BA553BF430FD77C1FF4D1F2AF63",
                "caIdx": "07",
                "keyWrapValue": null,
                "keyEffectiveDate": null,
                "kvc": null,
                "reserve": null
            }
        ]
    }
```

#### 3. requestAddPayChannel

- **路径**：`/ci/app/requestKeyList`
- **HTTP**：PostMapping
- **返回类型**：RequestAddPayChannelResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // RequestAddPayChannelResult 未找到定义
```

#### 4. requestSetDefaultPayChannel

- **路径**：`/ci/app/requestAddPayChannel`
- **HTTP**：PostMapping
- **返回类型**：RequestSetDefaultPayChannelResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // RequestSetDefaultPayChannelResult 未找到定义
```

### AppIndustryDataController

- 类路径：`/ci/app`

#### 1. requestIndustryData

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：RequestIndustryDataResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "01",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727103340",
        "deviceId": "06220E01",
        "signType": "00",
        "sign": "dS1085EFofGsyGgysrYiEddYjHW7J/1cnlUQh16jCKGGi4lFePstm1yS+A4Nl7Xil3GS8kKUsj2OkU8yuaJ5it5UkdrMZf2CTpgpb2wfvX2R+d4Okj5+WAczlew+tqQeYWDqfhmprKyAsLc/+ZjhaWdIdx9prjQPq3L4G1SALukHLNzvfXCnQNJNwa06koXXChnewraclnf0gkZS4D3T7NgRbCR4L72yyt/KxmLee4oHOohqagRyEA0S7QtCz4aAec55tgJoNWi5ejoRDwRGAfrZYxnfGwrKiYiwyaDskckk0PRgmLmrcm5wFPwHv9EA095MlE/JEvSiIqWtNbh1zw==",
        "bizData": "{\"thirdUserId\":\"00522906\",\"cardId\":\"0418061109608453\",\"cardType\":\"02\"}"
    }
```

**响应示例**：
```json
    {
        "retCode": "8004",
        "retMsg": "没有账号卡片数据",
        "cardData": null,
        "signType": "00",
        "sign": ""
    }
```

#### 2. requestNoSignalData

- **路径**：`/ci/app/requestIndustryData`
- **HTTP**：PostMapping
- **返回类型**：RequestNoSignalDataResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "01",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727103339",
        "deviceId": "06220E01",
        "signType": "00",
        "sign": "dS1085EFofGsyGgysrYiEddYjHW7J/1cnlUQh16jCKGGi4lFePstm1yS+A4Nl7Xil3GS8kKUsj2OkU8yuaJ5it5UkdrMZf2CTpgpb2wfvX2R+d4Okj5+WAczlew+tqQeYWDqfhmprKyAsLc/+ZjhaWdIdx9prjQPq3L4G1SALukHLNzvfXCnQNJNwa06koXXChnewraclnf0gkZS4D3T7NgRbCR4L72yyt/KxmLee4oHOohqagRyEA0S7QtCz4aAec55tgJoNWi5ejoRDwRGAfrZYxnfGwrKiYiwyaDskckk0PRgmLmrcm5wFPwHv9EA095MlE/JEvSiIqWtNbh1zw==",
        "bizData": "{\"thirdUserId\":\"00522906\",\"cardId\":\"0418061109608453\",\"cardType\":\"02\"}"
    }
```

**响应示例**：
```json
    {
        "retCode": "8004",
        "retMsg": "没有账号卡片数据",
        "exitData": null,
        "entryData": null,
        "channel": null
    }
```

### AppNotifyController

- 类路径：`/app`

#### 1. receiveBlackListFromItp

- **路径**：`/app/app`
- **HTTP**：RequestMapping
- **返回类型**：CommonResult
- **参数**：request: ReceiveBlackListFromItpReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值",
        "blackListType": "示例值",
        "optionDate": "示例值",
        "expireTime": "示例值",
        "signType": "示例值",
        "sign": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

### AppParaController

- 类路径：`/ci/app`

#### 1. requestLineCodeList

- **路径**：`/ci/app/requestLineCodeList`
- **HTTP**：PostMapping
- **返回类型**：RequestLineCodeListResult
- **参数**：request: RequestLineCodeListReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 2. requestStationCodeList

- **路径**：`/ci/app/requestStationCodeList`
- **HTTP**：PostMapping
- **返回类型**：RequestStationCodeListResult
- **参数**：request: RequestStationCodeListReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 3. requestTicketPriceByStation

- **路径**：`/ci/app/requestTicketPriceByStation`
- **HTTP**：PostMapping
- **返回类型**：RequestTicketPriceByStationResult
- **参数**：request: RequestTicketPriceByStationReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 4. requestLineStationCodeVersion

- **路径**：`/ci/app/requestLineStationCodeVersion`
- **HTTP**：PostMapping
- **返回类型**：RequestLineStationCodeVersionResult
- **参数**：request: RequestLineStationCodeVersionReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 5. requestStationName

- **路径**：`/ci/app/requestStationName`
- **HTTP**：PostMapping
- **返回类型**：RequestStationNameResult
- **参数**：request: RequestStationNameReqDTO

**请求示例**：
```json
    {
        "stationCode": "1131"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "stationCode": "1131",
        "stationName": "庙石"
    }
```

### AppPaySignController

- 类路径：`/ci/app`

#### 1. requestSignInfo

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：RequestSignInfoResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "requestStartSdkInfo": "示例值"
    }
```

#### 2. requestTermination

- **路径**：`/ci/app/requestSignInfo`
- **HTTP**：PostMapping
- **返回类型**：RequestTerminationResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "code": 0,
        "msg": "示例值",
        "success": false,
        "data": "示例值"
    }
```

#### 3. receiveSignResult

- **路径**：`/ci/app/requestTermination`
- **HTTP**：PostMapping
- **返回类型**：PaySignCallbackResult
- **参数**：request: PayCenterCommonRequest

**请求示例**：
```json
    {
        "merchantNo": "示例值",
        "apiVersion": "示例值",
        "signType": "示例值",
        "charset": "示例值",
        "bizData": "示例值",
        "sign": "示例值"
    }
```

**响应示例**：
```json
    // PaySignCallbackResult 未找到定义
```

#### 4. receiveTerminationResult

- **路径**：`/ci/app/receiveSignResult`
- **HTTP**：PostMapping
- **返回类型**：PaySignCallbackResult
- **参数**：requestBody: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // PaySignCallbackResult 未找到定义
```

#### 5. receivePayResult

- **路径**：`/ci/app/receiveTerminationResult`
- **HTTP**：PostMapping
- **返回类型**：PaySignCallbackResult
- **参数**：requestBody: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // PaySignCallbackResult 未找到定义
```

#### 6. invalidCallback

- **路径**：`/ci/app/receivePayResult`
- **HTTP**：PostMapping
- **返回类型**：PaySignCallbackResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // PaySignCallbackResult 未找到定义
```

### AppTicketController

- 类路径：`/ci/app`

#### 1. queryBlackList

- **路径**：`/ci/app/ci/app`
- **HTTP**：RequestMapping
- **返回类型**：QueryBlackListResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "inBlack": "示例值",
        "failedCount": 0
    }
```

#### 2. queryUserItinerary

- **路径**：`/ci/app/ticket/queryBlackList`
- **HTTP**：PostMapping
- **返回类型**：QueryUserItineraryResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "memberItinerary": {
            "payStatus": "示例值",
            "thisStationName": "示例值",
            "thisTransTime": "示例值",
            "thisStationCode": "示例值",
            "lastStationName": "示例值",
            "lastTransTime": "示例值",
            "lastStationCode": "示例值",
            "transSeq": "示例值",
            "transValue": 0,
            "overtimeTransValue": 0,
            "ticketStatus": "示例值",
            "payChannel": "示例值",
            "oriTicketAmt": 0,
            "debitAmt": 0,
            "orderExpType": 0,
            "discountInfo": "示例值",
            "orderNo": "示例值",
            "carbonDiscount": 0
        }
    }
```

#### 3. requestExcessFare

- **路径**：`/ci/app/queryUserItinerary`
- **HTTP**：PostMapping
- **返回类型**：RequestExcessFareResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 4. requestTransList

- **路径**：`/ci/app/requestExcessFare`
- **HTTP**：PostMapping
- **返回类型**：RequestTransListResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "pageNumber": "示例值",
        "pageSize": "示例值",
        "totalPage": "示例值",
        "ticketTransRecord": null,
        "retCode": "示例值",
        "retMsg": "示例值",
        "pageNumber": "示例值",
        "pageSize": "示例值",
        "totalPage": "示例值",
        "ticketTransRecord": null
    }
```

### FepAppDailyTicketController

- 类路径：`/app`

#### 1. requestCountingOrder

- **路径**：`/app/app`
- **HTTP**：RequestMapping
- **返回类型**：DailyTicketOrderResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "orderNo": "示例值"
    }
```

#### 2. requestPay

- **路径**：`/app/requestCountingOrder`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketPayResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "signType": "示例值",
        "sign": "示例值",
        "payChannelCode": "示例值",
        "paymentInfo": "示例值",
        "discountInfo": "示例值"
    }
```

#### 3. receivePayResult

- **路径**：`/app/payment/requestPay`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：requestBody: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 4. requestPayResult

- **路径**：`/app/payment/receivePayResult`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketPayQueryResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "tradeNo": "示例值",
        "payResult": "示例值",
        "payAmount": 0,
        "payDate": null,
        "discountInfo": "示例值",
        "payChannel": "示例值",
        "channelDiscount": 0,
        "couponDiscount": 0
    }
```

#### 5. requestRefundTicket

- **路径**：`/app/payment/requestPayResult`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketRefundResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "refundType": "示例值",
        "orderNo": "示例值",
        "refundResultDesc": "示例值",
        "refundResult": "示例值",
        "refundDate": "示例值",
        "refundAmount": "示例值"
    }
```

#### 6. cancelOrder

- **路径**：`/app/payment/requestRefundTicket`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 7. updateTicket

- **路径**：`/app/ticket/cancelOrder`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 8. updateAndNotice

- **路径**：`/app/ticket/updateTicket`
- **HTTP**：PostMapping
- **返回类型**：DailyTicketBaseResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

### FepAppTicketController

- 类路径：`/app/ticket`

#### 1. queryBlackList

- **路径**：`/app/ticket/app/ticket`
- **HTTP**：RequestMapping
- **返回类型**：QueryBlackListResult
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "inBlack": "示例值",
        "failedCount": 0
    }
```

## fep-alipay-server

共 12 个接口。

### FepAlipayTripController

- 类路径：`/channel`

#### 1. addContract

- **路径**：`/channel/addContract`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripAddContractRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727111126",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "agreementCode": "070000144856188719",
            "cardIssueCode": "0007",
            "channel": "05",
            "channelAgreementCode": "2088302232551032",
            "channelUserAccount": "2088302232551032",
            "thirdUserId": "0700001448"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "agreementCode": "070000144856188719"
    }
```

#### 2. terminateContract

- **路径**：`/channel/terminateContract`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripTerminateContractRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "示例值",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "agreementCode": "示例值",
            "cardIssueCode": "示例值",
            "thirdUserId": "示例值"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "agreementCode": "示例值"
    }
```

#### 3. requestApplication

- **路径**：`/channel/requestApplication`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripRequestApplicationRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727105437",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "thirdUserId": "0700001448",
            "extend2": "",
            "cardIssueCode": "0007",
            "cardType": "02",
            "showType": "1",
            "extend1": "3004",
            "msisdn": "15064255197"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "用户已开户",
        "cardId": "2607031119542741",
        "cardType": "02",
        "status": "ACTIVE"
    }
```

#### 4. requestIndustryData

- **路径**：`/channel/requestIndustryData`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripRequestIndustryDataRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727111301",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "thirdUserId": "0700001448",
            "cardId": "2607031119542741",
            "cardType": "02"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "cardData": "29B92CA803FFFF31F9F9BD31FA31FD26070311195427410441000000010707010000000051988D93",
        "signType": "00",
        "sign": ""
    }
```

#### 5. findTravelList

- **路径**：`/channel/findTravelList`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripFindTravelListRespDTO
- **参数**：request: CommonFormRequest

**请求示例 1**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727111937",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "thirdUserId": "0700001448",
            "debitRequestResult": "1"
        }
    }
```

**请求示例 2**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727133343",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "thirdUserId": "0700001448",
            "debitRequestResult": "0",
            "startDate": "2026-07-26",
            "endDate": "2026-07-27"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "pageNumber": 0,
        "pageSize": 10,
        "totalPage": 1,
        "totalCount": 2,
        "ticketTransRecord": [
            {
                "cardNum": "2607031119542741",
                "countingFlag": "N",
                "debitRequestResult": "0",
                "entryDate": "20260727111820",
                "entryStationName": "温泉东",
                "exitDate": "20260727111833",
                "exitStationName": "薛家岛",
                "orderExpType": "0",
                "payAmount": "8",
                "payChannelCode": "07",
                "payOrderNoDate": "2026-07-27 11:18:36",
                "payTradeOrderNo": "2026072722001451031452868663",
                "totalAmount": "8",
                "tradeOrderNo": "GT20260727111833863542741"
            },
            {
                "cardNum": "2607031119542741",
                "countingFlag": "N",
                "debitRequestResult": "0",
                "entryDate": "20260727111355",
                "entryStationName": "山东大学",
                "exitDate": "20260727111454",
                "exitStationName": "庙石",
                "orderExpType": "0",
                "payAmount": "4",
                "payChannelCode": "07",
                "payOrderNoDate": "2026-07-27 11:15:00",
                "payTradeOrderNo": "2026072722001451031453263852",
                "totalAmount": "4",
                "tradeOrderNo": "GT20260727111454140542741"
            }
        ]
    }
```

#### 6. findTravelDetail

- **路径**：`/channel/findTravelDetail`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripFindTravelDetailRespVO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727111607",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "thirdUserId": "0700001448",
            "orderNo": "GT20260727111454140542741"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "data": {
            "retCode": "0000",
            "retMsg": "成功",
            "entryStationName": "山东大学",
            "entryDate": "20260727111355",
            "exitStationName": "庙石",
            "exitDate": "20260727111454",
            "payAmount": "4",
            "totalAmount": "4",
            "orderExpType": "0",
            "tradeOrderNo": "GT20260727111454140542741",
            "payTradeOrderNo": null,
            "payOrderNoDate": "2026-07-27 11:15:00",
            "payChannelCode": "07",
            "debitRequestResult": "0",
            "discountFee": null,
            "discountInfo": null,
            "companionFlag": "",
            "cardNum": "2607031119542741",
            "ticketCode": null,
            "countingTimes": null,
            "countingFlag": "N",
            "invoice": null
        }
    }
```

### FepAlipayTripMemberContractController

- 类路径：`/memberContract/channel`

#### 1. requestIndustryData

- **路径**：`/memberContract/channel/requestIndustryData`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripRequestIndustryDataRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "07",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727111301",
        "deviceId": null,
        "signType": "00",
        "sign": "",
        "bizData": {
            "thirdUserId": "0700001448",
            "cardId": "2607031119542741",
            "cardType": "02"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "cardData": "29B92CA803FFFF31F9F9BD31FA31FD26070311195427410441000000010707010000000051988D93",
        "signType": "00",
        "sign": ""
    }
```

### FepAlipayTripNotifyController

- 类路径：`/notify`

#### 1. paymentPayNotify

- **路径**：`/notify/payment/payNotify`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripPayNotifyRespDTO
- **参数**：bizData: String

**请求示例**：
```json
    {
        "charset": "UTF-8",
        "providerId": "07",
        "format": "json",
        "sign": "HlHTW1X8R9zs8QGYYrp+ll8T75JyK6lJJkBjtsE7vCLRbbZ+hRBiZFa216h4HwDj6/IaDpDI0wZ/ZsmgILVcNJlLdDDzPluOcLNvda9cIyv2ZdpdeNSWZAE+v2I0s+cZUygwKPbU6rMOl81o1dL1WEZO85HwglMf0LgJIAilcWGTg9piB6Y6cDpClf0Dkm4C2pv490DZX150TtG0uqNBH8td0I9QCFMjOwWZKstzYPDiVdC+0/0mxklQGFdmO2w9Wjsi+JNNSJ2/lR/VIY4KPlhHzLtlWpIHSBHuAcCxi6RhjIAD8whgeJaZPv/iRUtKm8X2BGzWig32a3Eu9pMmuw==",
        "signType": "00",
        "bizData": {
            "transAmount": 4,
            "orderNo": "GT20260727111454140542741",
            "transTime": "2026-07-27 11:15:00",
            "channelVoucherId": "2026072722001451031453263852",
            "cardNo": "00072607031119542741",
            "transStatus": "1"
        },
        "timestamp": "20260727111518"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功"
    }
```

#### 2. closeResultForAlipay

- **路径**：`/notify/closeResultForAlipay`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripCloseResultRespDTO
- **参数**：request: AlipayTripCloseResultReqDTO

**请求示例**：
```json
    {
        "result": false,
        "agreementNo": "示例值"
    }
```

**响应示例**：
```json
    // AlipayTripCloseResultRespDTO 未找到定义
```

### FepAlipayTripPaymentController

- 类路径：`/admin/payment`

#### 1. payQuery

- **路径**：`/admin/payment/admin/payment`
- **HTTP**：RequestMapping
- **返回类型**：AlipayTripPayQueryRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "outTradeNo": "示例值",
        "paymentTime": "示例值",
        "tradeStatus": "示例值",
        "totalAmount": "示例值",
        "tradeNo": "示例值",
        "tradeDesc": "示例值",
        "retCode": "示例值",
        "retMsg": "示例值",
        "outTradeNo": "示例值",
        "paymentTime": "示例值",
        "tradeStatus": "示例值",
        "totalAmount": "示例值",
        "tradeNo": "示例值",
        "tradeDesc": "示例值"
    }
```

#### 2. requestRefund

- **路径**：`/admin/payment/payQuery`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripRequestRefundRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 3. addBlackList

- **路径**：`/admin/payment/requestRefund`
- **HTTP**：PostMapping
- **返回类型**：BlackListOperateResult
- **参数**：request: AddBlackListReqDTO

**请求示例**：
```json
    {
        "cardId": "示例值",
        "thirdUserId": "示例值",
        "cardType": "示例值",
        "reason": "示例值"
    }
```

**响应示例**：
```json
    // BlackListOperateResult 未找到定义
```

## fep-dev-server

共 4 个接口。

### FepAgmController

- 类路径：`/ci/agm`

#### 1. notifyVerifyResult

- **路径**：`/ci/agm/notiVerifyResult`
- **HTTP**：PostMapping
- **返回类型**：NotifyVerifyResultRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "04",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727111454",
        "deviceId": "11310503",
        "signType": "00",
        "sign": "",
        "bizData": {
            "cardId": "2607031119542741",
            "cardType": "0441",
            "channelType": "00",
            "deviceId": "11310503",
            "handleDateTime": "20260727111454",
            "handleResultCode": "000",
            "handleStationCode": "1131",
            "issueChannelCode": "07",
            "itpUserId": "29B92CA8",
            "lastHandleDateTime": "20260727111355",
            "lastHandleStationCode": "1134",
            "lastTicketStatus": "04",
            "overtimeAmount": "0",
            "signChannelCode": "07",
            "ticketTransSeq": "1",
            "trxAmount": "4",
            "trxType": "02"
        }
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功"
    }
```

#### 2. requestSynKeyList

- **路径**：`/ci/agm/requestSynKeyList`
- **HTTP**：PostMapping
- **返回类型**：RequestSynKeyListRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "03",
        "charset": "UTF-8",
        "format": "json",
        "timestamp": "20260727081656",
        "deviceId": "06220801",
        "signType": "00",
        "sign": "",
        "bizData": {
            "keyCurVerList": [
                {
                    "issueChannelCode": "01",
                    "keyBathNumber": "2",
                    "keyId": "01"
                }
            ]
        }
    }
```

> 说明：设备侧以 form-data 提交时，`bizData` 实际为 JSON 字符串；上例按业务结构展开展示。

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "keyCurVerList": [
            {
                "issueChannelCode": "01",
                "keyId": "01",
                "keyBathNumber": "2",
                "needUpdateYN": "N",
                "keyList": [
                    {
                        "keyId": "01",
                        "keyType": "1",
                        "keyUserId": "005EEC01",
                        "keyPrivate": "9QMH3C6sl2mNGJwlSezhdGZUHdv2qup6uRfT+4BoPDUjA35l6G4awpTzraaOyjY4gam6J5bf9H9EPKRGVMZPiPddhKFIfMml",
                        "keyPublic": "408A94DA5157FFC1D3FBBD53B11CB891BB9BBBB6BA0FC95A1D23717BB855FAD6F3973CDBB76AE5EE044B3BDD75895FBFD56DDBFE7CA8AF1855B6476959059873",
                        "keyPublicEffectiveDate": "32030AF8",
                        "signData": "1B8E7F44CEED59C6BB3B2656A3D1762C1532E4980AC30DF414B08E7DAE9B84CB62B94B035ABFB7C12DBFAC3CB9ECD7D81B9184F5F9EA8B410A97463E9B310B91",
                        "caIdx": "02",
                        "keyWrapValue": null,
                        "keyEffectiveDate": null,
                        "kvc": null,
                        "reserve": null
                    }
                ]
            }
        ]
    }
```

#### 3. requestQrCodeStatus

- **路径**：`/ci/agm/requestSynKeyList`
- **HTTP**：PostMapping
- **返回类型**：RequestQrCodeStatusRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    {
        "itpUserId": "示例值",
        "cardId": "示例值",
        "lastTicketStatus": "示例值",
        "lastHandleDateTime": "示例值"
    }
```

#### 4. deviceHeartbeat

- **路径**：`/ci/agm/requestQrCodeStatus`
- **HTTP**：PostMapping
- **返回类型**：DeviceHeartbeatRespDTO
- **参数**：request: CommonFormRequest

**请求示例**：
```json
    {
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值",
        "providerId": "示例值",
        "charset": "示例值",
        "format": "示例值",
        "timestamp": "示例值",
        "deviceId": "示例值",
        "signType": "示例值",
        "sign": "示例值",
        "bizData": "示例值"
    }
```

**响应示例**：
```json
    // DeviceHeartbeatRespDTO 未找到定义
```

## gate-txn-pay-server

共 1 个接口。

### GateTxnPayController

- 类路径：`/ci/gateTxnPay`

#### 1. requestPay

- **路径**：`/ci/gateTxnPay/ci/gateTxnPay`
- **HTTP**：RequestMapping
- **返回类型**：GateTxnPayRespDTO
- **参数**：request: GateTxnPayReqDTO

**请求示例**：
```json
    // GateTxnPayReqDTO 未找到定义
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值",
        "payStatus": "示例值"
    }
```

## blacklist-server

共 3 个接口。

### BlacklistController

#### 1. queryBlackList

- **路径**：`/queryBlackList`
- **HTTP**：PostMapping
- **返回类型**：QueryBlackListResult
- **参数**：request: QueryBlackListReqDTO

**请求示例**：
```json
    {
        "cardId": "0418061109608453"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "inBlack": "0",
        "failedCount": 0
    }
```

#### 2. addBlackList

- **路径**：`/addBlackList`
- **HTTP**：PostMapping
- **返回类型**：BlackListOperateResult
- **参数**：request: AddBlackListReqDTO

**请求示例**：
```json
    {
        "cardId": "示例值",
        "thirdUserId": "示例值",
        "cardType": "示例值",
        "reason": "示例值"
    }
```

**响应示例**：
```json
    // BlackListOperateResult 未找到定义
```

#### 3. deleteBlackList

- **路径**：`/deleteBlackList`
- **HTTP**：PostMapping
- **返回类型**：BlackListOperateResult
- **参数**：request: DeleteBlackListReqDTO

**请求示例**：
```json
    {
        "cardId": "示例值"
    }
```

**响应示例**：
```json
    // BlackListOperateResult 未找到定义
```

## key-server

共 1 个接口。

### KeyController

#### 1. requestKeyList

- **路径**：`/requestKeyList`
- **HTTP**：PostMapping
- **返回类型**：RequestKeyListResult
- **参数**：request: RequestKeyListReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "cardId": "示例值",
        "cardType": "示例值"
    }
```

**响应示例**：
```json
    {
        "signType": "示例值",
        "sign": "示例值",
        "keyList": null
    }
```

## para-server

共 5 个接口。

### AppParaController

- 类路径：`/ci/app`

#### 1. requestLineCodeList

- **路径**：`/ci/app/requestLineCodeList`
- **HTTP**：PostMapping
- **返回类型**：RequestLineCodeListResult
- **参数**：request: RequestLineCodeListReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 2. requestStationCodeList

- **路径**：`/ci/app/requestStationCodeList`
- **HTTP**：PostMapping
- **返回类型**：RequestStationCodeListResult
- **参数**：request: RequestStationCodeListReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 3. requestTicketPriceByStation

- **路径**：`/ci/app/requestTicketPriceByStation`
- **HTTP**：PostMapping
- **返回类型**：RequestTicketPriceByStationResult
- **参数**：request: RequestTicketPriceByStationReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 4. requestLineStationCodeVersion

- **路径**：`/ci/app/requestLineStationCodeVersion`
- **HTTP**：PostMapping
- **返回类型**：RequestLineStationCodeVersionResult
- **参数**：request: RequestLineStationCodeVersionReqDTO

**请求示例**：
```json
    // 本次日志未覆盖
```

**响应示例**：
```json
    // 本次日志未覆盖
```

#### 5. requestStationName

- **路径**：`/ci/app/requestStationName`
- **HTTP**：PostMapping
- **返回类型**：RequestStationNameResult
- **参数**：request: RequestStationNameReqDTO

**请求示例**：
```json
    {
        "stationCode": "1131"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "stationCode": "1131",
        "stationName": "庙石"
    }
```

### ParaPageController

- 类路径：`/page`

#### 1. line-info

- **路径**：`/page/line-info`
- **HTTP**：GetMapping
- **返回类型**：ResultVO<PageInfo<LineInfo>>
- **参数**：pageNum: Integer, pageSize: Integer

**请求示例**：
```json
    // GET 参数示例：pageNum=1&pageSize=10
```

**响应示例**：
```json
    // ResultVO<PageInfo<LineInfo>> 未找到定义
```

#### 2. station-info

- **路径**：`/page/station-info`
- **HTTP**：GetMapping
- **返回类型**：ResultVO<PageInfo<StationInfo>>
- **参数**：pageNum: Integer, pageSize: Integer

**请求示例**：
```json
    // GET 参数示例：pageNum=1&pageSize=10
```

**响应示例**：
```json
    // ResultVO<PageInfo<StationInfo>> 未找到定义
```

#### 3. line-station-version

- **路径**：`/page/line-station-version`
- **HTTP**：GetMapping
- **返回类型**：ResultVO<PageInfo<LineStationVersion>>
- **参数**：pageNum: Integer, pageSize: Integer

**请求示例**：
```json
    // GET 参数示例：pageNum=1&pageSize=10
```

**响应示例**：
```json
    // ResultVO<PageInfo<LineStationVersion>> 未找到定义
```

### ParaImportController

- 类路径：`/para/import`

#### 1. importDirectory

- **路径**：`/para/import/para/import`
- **HTTP**：RequestMapping
- **返回类型**：DirectoryImportResponse
- **参数**：@RequestParam(directory) directory: String; @RequestParam(file) file: MultipartFile

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // DirectoryImportResponse 未找到定义
```

#### 2. importDirectoryPath

- **路径**：`/para/import/directory`
- **HTTP**：PostMapping
- **返回类型**：DirectoryImportResponse
- **参数**：directory: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // DirectoryImportResponse 未找到定义
```

#### 3. resolveUploadFileName

- **路径**：`/para/import/directory`
- **HTTP**：GetMapping
- **返回类型**：String
- **参数**：file: MultipartFile

**请求示例**：
```json
    // MultipartFile 未找到定义
```

**响应示例**：
```json
    // String 未找到定义
```

#### 4. deleteTemporaryUpload

- **路径**：`/para/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DirectoryImportResponse importFile(@RequestParam("file`
- **HTTP**：PostMapping
- **返回类型**：void
- **参数**：tempFile: Path; tempDirectory: Path

**请求示例**：
```json
    // Path 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

## alipay-account-server

共 3 个接口。

### FepAlipayTripRequestApplicationController

- 类路径：`/channel`

#### 1. requestApplication

- **路径**：`/channel/requestApplication`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripRequestApplicationRespDTO
- **参数**：request: AlipayTripRequestApplicationReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "0700001448",
        "cardType": "02",
        "msisdn": "15064255197",
        "extend1": "3004",
        "extend2": "",
        "cardIssueCode": "0007"
    }
```

**响应示例 1**（新开卡）：
```json
    {
        "retCode": "0000",
        "retMsg": "成功",
        "cardId": "2607031119542741",
        "cardType": "02",
        "status": "ACTIVE"
    }
```

**响应示例 2**（已开户）：
```json
    {
        "retCode": "0000",
        "retMsg": "用户已开户",
        "cardId": "2607031119542741",
        "cardType": "02",
        "status": "ACTIVE"
    }
```

#### 2. queryUserInfo

- **路径**：`/channel/queryUserInfo`
- **HTTP**：GetMapping
- **返回类型**：AlipayUserInfoDTO
- **参数**：thirdUserId: String

**请求示例**：
```
GET /channel/queryUserInfo?thirdUserId=0700001448
```

**响应示例**：
```json
    {
        "thirdUserId": "0700001448",
        "cardId": "2607031119542741",
        "cardType": "02",
        "thirdPayId": null,
        "reqContractNo": null,
        "channel": "ALIPAY"
    }
```

#### 3. updatePaymentChannel

- **路径**：`/channel/updatePaymentChannel`
- **HTTP**：GetMapping
- **返回类型**：Boolean
- **参数**：thirdUserId: String; thirdPayId: String; reqContractNo: String

**请求示例**：
```
GET /channel/updatePaymentChannel?thirdUserId=0700001448&thirdPayId=2088302232551032&reqContractNo=070000144856188719
```

**响应示例**：
```json
    true
```

## alipay-pay-sign-server

共 16 个接口。

### AlipayPayLogController

- 类路径：`/api/payment`

#### 1. listPost

- **路径**：`/api/payment/api/payment`
- **HTTP**：RequestMapping
- **返回类型**：PageResult<AlipayPayLogVO>
- **参数**：params: Object>

**请求示例**：
```json
    // Object> 未找到定义
```

**响应示例**：
```json
    {
        "orderNo": "示例值",
        "tradeNo": "示例值",
        "channelOrderNo": "示例值",
        "payStatus": "示例值",
        "payAmount": "示例值",
        "entryId": "示例值",
        "exitId": "示例值",
        "cardId": "示例值",
        "transTime": "示例值"
    }
```

#### 2. detail

- **路径**：`/api/payment/payLog/list`
- **HTTP**：GetMapping
- **返回类型**：AlipayPayLogVO
- **参数**：orderNo: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "orderNo": "示例值",
        "tradeNo": "示例值",
        "channelOrderNo": "示例值",
        "payStatus": "示例值",
        "payAmount": "示例值",
        "entryId": "示例值",
        "exitId": "示例值",
        "cardId": "示例值",
        "transTime": "示例值"
    }
```

#### 3. entryId

- **路径**：`/api/payment/payLog/list`
- **HTTP**：PostMapping
- **返回类型**：AlipayPayLogVO
- **参数**：entryId: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "orderNo": "示例值",
        "tradeNo": "示例值",
        "channelOrderNo": "示例值",
        "payStatus": "示例值",
        "payAmount": "示例值",
        "entryId": "示例值",
        "exitId": "示例值",
        "cardId": "示例值",
        "transTime": "示例值"
    }
```

#### 4. exitId

- **路径**：`/api/payment/payLog/detail`
- **HTTP**：GetMapping
- **返回类型**：AlipayPayLogVO
- **参数**：exitId: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "orderNo": "示例值",
        "tradeNo": "示例值",
        "channelOrderNo": "示例值",
        "payStatus": "示例值",
        "payAmount": "示例值",
        "entryId": "示例值",
        "exitId": "示例值",
        "cardId": "示例值",
        "transTime": "示例值"
    }
```

#### 5. queryByTravelRecord

- **路径**：`/api/payment/payLog/entryId`
- **HTTP**：GetMapping
- **返回类型**：AlipayPayLogVO
- **参数**：params: String>

**请求示例**：
```json
    // String> 未找到定义
```

**响应示例**：
```json
    {
        "orderNo": "示例值",
        "tradeNo": "示例值",
        "channelOrderNo": "示例值",
        "payStatus": "示例值",
        "payAmount": "示例值",
        "entryId": "示例值",
        "exitId": "示例值",
        "cardId": "示例值",
        "transTime": "示例值"
    }
```

#### 6. travelListPost

- **路径**：`/api/payment/payLog/exitId`
- **HTTP**：GetMapping
- **返回类型**：PageResult<AlipayPayLogVO>
- **参数**：params: Object>

**请求示例**：
```json
    // Object> 未找到定义
```

**响应示例**：
```json
    {
        "orderNo": "示例值",
        "tradeNo": "示例值",
        "channelOrderNo": "示例值",
        "payStatus": "示例值",
        "payAmount": "示例值",
        "entryId": "示例值",
        "exitId": "示例值",
        "cardId": "示例值",
        "transTime": "示例值"
    }
```

### AlipayPaySignController

- 类路径：`/channel`

#### 1. addContract

- **路径**：`/channel/channel`
- **HTTP**：RequestMapping
- **返回类型**：AlipayTripAddContractRespDTO
- **参数**：request: AlipayTripAddContractReqDTO

**请求示例**：
```json
    {
        "channel": "示例值",
        "thirdUserId": "示例值",
        "agreementCode": "示例值",
        "channelAgreementCode": "示例值",
        "channelUserAccount": "示例值",
        "cardIssueCode": "示例值",
        "thirdUserId": "示例值",
        "channel": "示例值",
        "agreementCode": "示例值",
        "channelAgreementCode": "示例值",
        "channelUserAccount": "示例值",
        "cardIssueCode": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "agreementCode": "示例值"
    }
```

#### 2. terminateContract

- **路径**：`/channel/addContract`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripTerminateContractRespDTO
- **参数**：request: AlipayTripTerminateContractReqDTO

**请求示例**：
```json
    {
        "agreementCode": "示例值",
        "merchantNo": "示例值",
        "agreementCode": "示例值",
        "merchantNo": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "agreementCode": "示例值"
    }
```

#### 3. selectSignInfo

- **路径**：`/channel/terminateContract`
- **HTTP**：PostMapping
- **返回类型**：AlipaySignInfoDTO
- **参数**：thirdUserId: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "thirdUserId": "示例值",
        "channelAgreementCode": "示例值",
        "reqContractNo": "示例值",
        "cardId": "示例值",
        "cardType": "示例值"
    }
```

#### 4. executeTermination

- **路径**：`/channel/selectSignInfo`
- **HTTP**：GetMapping
- **返回类型**：AlipayCommonResponse
- **参数**：agreementCode: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 5. findTravelDetail

- **路径**：`/channel/executeTermination`
- **HTTP**：GetMapping
- **返回类型**：AlipayTripFindTravelDetailRespDTO
- **参数**：request: AlipayTripFindTravelDetailReqDTO

**请求示例**：
```json
    {
        "thirdUserId": "示例值",
        "orderNo": "示例值",
        "handleDateTime": "示例值",
        "trxType": "示例值",
        "cardId": "示例值"
    }
```

**响应示例**：
```json
    {
        "entryStationName": "示例值",
        "entryDate": "示例值",
        "exitStationName": "示例值",
        "exitDate": "示例值",
        "payAmount": "示例值",
        "totalAmount": "示例值",
        "orderExpType": "示例值",
        "tradeOrderNo": "示例值",
        "payTradeOrderNo": "示例值",
        "payOrderNoDate": "示例值",
        "payChannelCode": "示例值",
        "debitRequestResult": "示例值",
        "discountFee": "示例值",
        "discountInfo": "示例值",
        "companionFlag": "示例值",
        "cardNum": "示例值",
        "ticketCode": "示例值",
        "countingTimes": "示例值",
        "countingFlag": "示例值",
        "invoice": "示例值"
    }
```

#### 6. notifyBlackListChange

- **路径**：`/channel/findTravelDetail`
- **HTTP**：PostMapping
- **返回类型**：AlipayCommonResponse
- **参数**：request: AlipayBlackListNotifyReqDTO

**请求示例**：
```json
    {
        "cardId": "示例值",
        "thirdUserId": "示例值",
        "cardType": "示例值",
        "blackListType": "示例值",
        "optionDate": "示例值",
        "expireTime": "示例值",
        "reason": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

### AlipayTripPaymentController

- 类路径：`/api/payment`

#### 1. requestPay

- **路径**：`/api/payment/api/payment`
- **HTTP**：RequestMapping
- **返回类型**：AlipayTripRequestPayRespDTO
- **参数**：request: AlipayTripRequestPayReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "scene": "示例值",
        "paymentVendor": "示例值",
        "amount": 0,
        "industryType": "示例值",
        "subject": "示例值",
        "body": "示例值",
        "requestSignSeq": "示例值",
        "thirdUserId": "示例值",
        "orderTimeOut": 0,
        "authCode": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "ipAddress": "示例值",
        "remark": "示例值",
        "industryDetail": "示例值",
        "orderNo": "示例值",
        "scene": "示例值",
        "paymentVendor": "示例值",
        "amount": 0,
        "industryType": "示例值",
        "subject": "示例值",
        "body": "示例值",
        "requestSignSeq": "示例值",
        "thirdUserId": "示例值",
        "orderTimeOut": 0,
        "authCode": "示例值",
        "notifyUrl": "示例值",
        "returnUrl": "示例值",
        "ipAddress": "示例值",
        "remark": "示例值",
        "industryDetail": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值",
        "retCode": "示例值",
        "retMsg": "示例值",
        "orderNo": "示例值"
    }
```

#### 2. payQuery

- **路径**：`/api/payment/requestPay`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripPayQueryRespDTO
- **参数**：request: AlipayTripPayQueryReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "cardIssueCode": "示例值",
        "cardNum": "示例值",
        "channelAgreementNo": "示例值",
        "orderNo": "示例值",
        "cardIssueCode": "示例值",
        "cardNum": "示例值",
        "channelAgreementNo": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "outTradeNo": "示例值",
        "paymentTime": "示例值",
        "tradeStatus": "示例值",
        "totalAmount": "示例值",
        "tradeNo": "示例值",
        "tradeDesc": "示例值",
        "retCode": "示例值",
        "retMsg": "示例值",
        "outTradeNo": "示例值",
        "paymentTime": "示例值",
        "tradeStatus": "示例值",
        "totalAmount": "示例值",
        "tradeNo": "示例值",
        "tradeDesc": "示例值"
    }
```

#### 3. requestRefund

- **路径**：`/api/payment/payQuery`
- **HTTP**：PostMapping
- **返回类型**：AlipayTripRequestRefundRespDTO
- **参数**：request: AlipayTripRequestRefundReqDTO

**请求示例**：
```json
    {
        "orderNo": "示例值",
        "cardIssueCode": "示例值",
        "cardNum": "示例值",
        "channelAgreementNo": "示例值",
        "refundAmount": "示例值",
        "refundOrderNo": "示例值",
        "orderNo": "示例值",
        "refundAmount": "示例值"
    }
```

**响应示例**：
```json
    {
        "retCode": "示例值",
        "retMsg": "示例值",
        "retCode": "示例值",
        "retMsg": "示例值"
    }
```

#### 4. payNotify

- **路径**：`/api/payment/payNotify`
- **HTTP**：PostMapping
- **返回类型**：AlipayCommonResponse
- **参数**：request: AlipayTripPayNotifyReqDTO

**请求示例**：
```json
    {
        "orderNo": "GT20260727144040911542741",
        "channelVoucherId": "2026072722001451031459194861",
        "transAmount": "7",
        "transTime": "2026-07-27 14:40:44",
        "transStatus": "1",
        "cardNo": "00072607031119542741"
    }
```

**响应示例**：
```json
    {
        "retCode": "0000",
        "retMsg": "成功"
    }
```

## web-server

共 121 个接口。

### CacheController

- 类路径：`/monitor/cache`

#### 1. getInfo

- **路径**：`/monitor/cache/monitor/cache`
- **HTTP**：RequestMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 2. cache

- **路径**：`/monitor/cache/getNames`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 3. getCacheKeys

- **路径**：`/monitor/cache/getKeys/{cacheName}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：cacheName: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. getCacheValue

- **路径**：`/monitor/cache/getValue/{cacheName}/{cacheKey}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：cacheName: String; cacheKey: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. clearCacheName

- **路径**：`/monitor/cache/clearCacheName/{cacheName}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：cacheName: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. clearCacheKey

- **路径**：`/monitor/cache/clearCacheKey/{cacheKey}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：cacheKey: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 7. clearCacheAll

- **路径**：`/monitor/cache/clearCacheAll`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### CaptchaController

#### 1. getCode

- **路径**：`/captchaImage`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：response: HttpServletResponse

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### CommonController

- 类路径：`/common`

#### 1. fileDownload

- **路径**：`/common/common`
- **HTTP**：RequestMapping
- **返回类型**：void
- **参数**：fileName: String; delete: Boolean; response: HttpServletResponse; request: HttpServletRequest

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 2. uploadFile

- **路径**：`/common/download`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：file: MultipartFile

**请求示例**：
```json
    // MultipartFile 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 3. uploadFiles

- **路径**：`/common/upload`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：files: List<MultipartFile>

**请求示例**：
```json
    // List<MultipartFile> 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. resourceDownload

- **路径**：`/common/uploads`
- **HTTP**：PostMapping
- **返回类型**：void
- **参数**：resource: String; request: HttpServletRequest; response: HttpServletResponse

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

### GenController

- 类路径：`/tool/gen`

#### 1. genList

- **路径**：`/tool/gen/tool/gen`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：genTable: GenTable

**请求示例**：
```json
    {
        "tableId": 0,
        "tableName": "示例值",
        "tableComment": "示例值",
        "subTableName": "示例值",
        "subTableFkName": "示例值",
        "className": "示例值",
        "tplCategory": "示例值",
        "tplWebType": "示例值",
        "packageName": "示例值",
        "moduleName": "示例值",
        "businessName": "示例值",
        "functionName": "示例值",
        "functionAuthor": "示例值",
        "genType": "示例值",
        "genPath": "示例值",
        "pkColumn": {
            "columnId": 0,
            "tableId": 0,
            "columnName": "示例值",
            "columnComment": "示例值",
            "columnType": "示例值",
            "javaType": "示例值",
            "javaField": "示例值",
            "isPk": "示例值",
            "isIncrement": "示例值",
            "isRequired": "示例值",
            "isInsert": "示例值",
            "isEdit": "示例值",
            "isList": "示例值",
            "isQuery": "示例值",
            "queryType": "示例值",
            "htmlType": "示例值",
            "dictType": "示例值",
            "sort": 0
        },
        "subTable": null,
        "columns": null,
        "options": "示例值",
        "treeCode": "示例值",
        "treeParentCode": "示例值",
        "treeName": "示例值",
        "parentMenuId": 0,
        "parentMenuName": "示例值"
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. getInfo

- **路径**：`/tool/gen/list`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：tableId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 3. dataList

- **路径**：`/tool/gen/{tableId}`
- **HTTP**：GetMapping
- **返回类型**：TableDataInfo
- **参数**：genTable: GenTable

**请求示例**：
```json
    {
        "tableId": 0,
        "tableName": "示例值",
        "tableComment": "示例值",
        "subTableName": "示例值",
        "subTableFkName": "示例值",
        "className": "示例值",
        "tplCategory": "示例值",
        "tplWebType": "示例值",
        "packageName": "示例值",
        "moduleName": "示例值",
        "businessName": "示例值",
        "functionName": "示例值",
        "functionAuthor": "示例值",
        "genType": "示例值",
        "genPath": "示例值",
        "pkColumn": {
            "columnId": 0,
            "tableId": 0,
            "columnName": "示例值",
            "columnComment": "示例值",
            "columnType": "示例值",
            "javaType": "示例值",
            "javaField": "示例值",
            "isPk": "示例值",
            "isIncrement": "示例值",
            "isRequired": "示例值",
            "isInsert": "示例值",
            "isEdit": "示例值",
            "isList": "示例值",
            "isQuery": "示例值",
            "queryType": "示例值",
            "htmlType": "示例值",
            "dictType": "示例值",
            "sort": 0
        },
        "subTable": null,
        "columns": null,
        "options": "示例值",
        "treeCode": "示例值",
        "treeParentCode": "示例值",
        "treeName": "示例值",
        "parentMenuId": 0,
        "parentMenuName": "示例值"
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 4. columnList

- **路径**：`/tool/gen/db/list`
- **HTTP**：GetMapping
- **返回类型**：TableDataInfo
- **参数**：tableId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 5. importTableSave

- **路径**：`/tool/gen/column/{tableId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：tables: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. createTableSave

- **路径**：`/tool/gen/importTable`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：sql: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 7. editSave

- **路径**：`/tool/gen/createTable`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(tableId) tableId: Long; @PathVariable(tableName) tableName: String

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 8. remove

- **路径**：`/tool/gen/{tableIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(tableId) tableId: Long; @PathVariable(tableName) tableName: String; @PathVariable(tableName) tableName: String

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 9. batchGenCode

- **路径**：`/tool/gen/preview/{tableId}`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; tables: String

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 10. genCode

- **路径**：`/tool/gen/download/{tableName}`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; data: byte[]

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

### ServerController

- 类路径：`/monitor/server`

#### 1. getInfo

- **路径**：`/monitor/server/monitor/server`
- **HTTP**：RequestMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysConfigController

- 类路径：`/system/config`

#### 1. list

- **路径**：`/system/config/system/config`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：config: SysConfig

**请求示例**：
```json
    {
        "configId": 0,
        "configName": "示例值",
        "configKey": "示例值",
        "configValue": "示例值",
        "configType": "示例值"
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/system/config/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; config: SysConfig

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. getInfo

- **路径**：`/system/config/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：configId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. getConfigKey

- **路径**：`/system/config/{configId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：configKey: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. add

- **路径**：`/system/config/configKey/{configKey}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：config: SysConfig

**请求示例**：
```json
    {
        "configId": 0,
        "configName": "示例值",
        "configKey": "示例值",
        "configValue": "示例值",
        "configType": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. remove

- **路径**：`/system/config/{configIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：configIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 7. refreshCache

- **路径**：`/system/config/refreshCache`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysDeptController

- 类路径：`/system/dept`

#### 1. list

- **路径**：`/system/dept/system/dept`
- **HTTP**：RequestMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(deptId) deptId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 2. getInfo

- **路径**：`/system/dept/list`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：deptId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 3. add

- **路径**：`/system/dept/list/exclude/{deptId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：dept: SysDept

**请求示例**：
```json
    {
        "deptId": 0,
        "parentId": 0,
        "ancestors": "示例值",
        "deptName": "示例值",
        "orderNum": 0,
        "leader": "示例值",
        "phone": "示例值",
        "email": "示例值",
        "status": "示例值",
        "delFlag": "示例值",
        "parentName": "示例值",
        "children": null
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. edit

- **路径**：`/system/dept/{deptId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：dept: SysDept

**请求示例**：
```json
    {
        "deptId": 0,
        "parentId": 0,
        "ancestors": "示例值",
        "deptName": "示例值",
        "orderNum": 0,
        "leader": "示例值",
        "phone": "示例值",
        "email": "示例值",
        "status": "示例值",
        "delFlag": "示例值",
        "parentName": "示例值",
        "children": null
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. remove

- **路径**：`/system/dept/{deptId}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：deptId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysDictDataController

- 类路径：`/system/dict/data`

#### 1. list

- **路径**：`/system/dict/data/system/dict/data`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：dictData: SysDictData

**请求示例**：
```json
    {
        "dictCode": 0,
        "dictSort": 0,
        "dictLabel": "示例值",
        "dictValue": "示例值",
        "dictType": "示例值",
        "cssClass": "示例值",
        "listClass": "示例值",
        "isDefault": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/system/dict/data/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; dictData: SysDictData

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. getInfo

- **路径**：`/system/dict/data/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：dictCode: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. dictType

- **路径**：`/system/dict/data/{dictCode}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：dictType: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. add

- **路径**：`/system/dict/data/type/{dictType}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：dict: SysDictData

**请求示例**：
```json
    {
        "dictCode": 0,
        "dictSort": 0,
        "dictLabel": "示例值",
        "dictValue": "示例值",
        "dictType": "示例值",
        "cssClass": "示例值",
        "listClass": "示例值",
        "isDefault": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. remove

- **路径**：`/system/dict/data/{dictCodes}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：dictCodes: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysDictTypeController

- 类路径：`/system/dict/type`

#### 1. list

- **路径**：`/system/dict/type/system/dict/type`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：dictType: SysDictType

**请求示例**：
```json
    {
        "dictId": 0,
        "dictName": "示例值",
        "dictType": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/system/dict/type/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; dictType: SysDictType

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. getInfo

- **路径**：`/system/dict/type/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：dictId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. add

- **路径**：`/system/dict/type/{dictId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：dict: SysDictType

**请求示例**：
```json
    {
        "dictId": 0,
        "dictName": "示例值",
        "dictType": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. remove

- **路径**：`/system/dict/type/{dictIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：dictIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. refreshCache

- **路径**：`/system/dict/type/refreshCache`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 7. optionselect

- **路径**：`/system/dict/type/optionselect`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysIndexController

#### 1. index

- **路径**：`/`
- **HTTP**：RequestMapping
- **返回类型**：String
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // String 未找到定义
```

### SysJobController

- 类路径：`/monitor/job`

#### 1. list

- **路径**：`/monitor/job/monitor/job`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：@PathVariable(jobId) jobId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/monitor/job/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：@PathVariable(jobId) jobId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. add

- **路径**：`/monitor/job/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：job: SysJob

**请求示例**：
```json
    {
        "jobId": 0,
        "jobName": "示例值",
        "jobGroup": "示例值",
        "invokeTarget": "示例值",
        "cronExpression": "示例值",
        "misfirePolicy": "示例值",
        "concurrent": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. edit

- **路径**：`/monitor/job/{jobId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：job: SysJob

**请求示例**：
```json
    {
        "jobId": 0,
        "jobName": "示例值",
        "jobGroup": "示例值",
        "invokeTarget": "示例值",
        "cronExpression": "示例值",
        "misfirePolicy": "示例值",
        "concurrent": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. changeStatus

- **路径**：`/monitor/job/changeStatus`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：job: SysJob

**请求示例**：
```json
    {
        "jobId": 0,
        "jobName": "示例值",
        "jobGroup": "示例值",
        "invokeTarget": "示例值",
        "cronExpression": "示例值",
        "misfirePolicy": "示例值",
        "concurrent": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. run

- **路径**：`/monitor/job/run`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：job: SysJob

**请求示例**：
```json
    {
        "jobId": 0,
        "jobName": "示例值",
        "jobGroup": "示例值",
        "invokeTarget": "示例值",
        "cronExpression": "示例值",
        "misfirePolicy": "示例值",
        "concurrent": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 7. remove

- **路径**：`/monitor/job/{jobIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：jobIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysJobLogController

- 类路径：`/monitor/jobLog`

#### 1. list

- **路径**：`/monitor/jobLog/monitor/jobLog`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：sysJobLog: SysJobLog

**请求示例**：
```json
    {
        "jobLogId": 0,
        "jobName": "示例值",
        "jobGroup": "示例值",
        "invokeTarget": "示例值",
        "jobMessage": "示例值",
        "status": "示例值",
        "exceptionInfo": "示例值",
        "startTime": null,
        "stopTime": null
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/monitor/jobLog/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; sysJobLog: SysJobLog

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. getInfo

- **路径**：`/monitor/jobLog/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：jobLogId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. remove

- **路径**：`/monitor/jobLog/{jobLogId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：jobLogIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. clean

- **路径**：`/monitor/jobLog/{jobLogIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysLoginController

#### 1. login

- **路径**：`/login`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：loginBody: LoginBody

**请求示例**：
```json
    {
        "username": "示例值",
        "password": "示例值",
        "code": "示例值",
        "uuid": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 2. getInfo

- **路径**：`/getInfo`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 3. getRouters

- **路径**：`/getRouters`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysLogininforController

- 类路径：`/monitor/logininfor`

#### 1. list

- **路径**：`/monitor/logininfor/monitor/logininfor`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：logininfor: SysLogininfor

**请求示例**：
```json
    {
        "infoId": 0,
        "userName": "示例值",
        "status": "示例值",
        "ipaddr": "示例值",
        "loginLocation": "示例值",
        "browser": "示例值",
        "os": "示例值",
        "msg": "示例值",
        "loginTime": null
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/monitor/logininfor/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：@PathVariable(userName) userName: String

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. remove

- **路径**：`/monitor/logininfor/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(userName) userName: String

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. clean

- **路径**：`/monitor/logininfor/{infoIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(userName) userName: String

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysMenuController

- 类路径：`/system/menu`

#### 1. list

- **路径**：`/system/menu/system/menu`
- **HTTP**：RequestMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(roleId) roleId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 2. getInfo

- **路径**：`/system/menu/list`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(roleId) roleId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 3. treeselect

- **路径**：`/system/menu/{menuId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(roleId) roleId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. add

- **路径**：`/system/menu/treeselect`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：menu: SysMenu

**请求示例**：
```json
    {
        "menuId": 0,
        "menuName": "示例值",
        "parentName": "示例值",
        "parentId": 0,
        "orderNum": 0,
        "path": "示例值",
        "component": "示例值",
        "query": "示例值",
        "routeName": "示例值",
        "isFrame": "示例值",
        "isCache": "示例值",
        "menuType": "示例值",
        "visible": "示例值",
        "status": "示例值",
        "perms": "示例值",
        "icon": "示例值",
        "children": null
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. edit

- **路径**：`/system/menu/roleMenuTreeselect/{roleId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(menuId) menuId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysNoticeController

- 类路径：`/system/notice`

#### 1. list

- **路径**：`/system/notice/system/notice`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：notice: SysNotice

**请求示例**：
```json
    {
        "noticeId": 0,
        "noticeTitle": "示例值",
        "noticeType": "示例值",
        "noticeContent": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. getInfo

- **路径**：`/system/notice/list`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：noticeId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 3. add

- **路径**：`/system/notice/{noticeId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：notice: SysNotice

**请求示例**：
```json
    {
        "noticeId": 0,
        "noticeTitle": "示例值",
        "noticeType": "示例值",
        "noticeContent": "示例值",
        "status": "示例值"
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. remove

- **路径**：`/system/notice/{noticeIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：noticeIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysOperlogController

- 类路径：`/monitor/operlog`

#### 1. list

- **路径**：`/monitor/operlog/monitor/operlog`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：operLog: SysOperLog

**请求示例**：
```json
    {
        "operId": 0,
        "title": "示例值",
        "businessType": 0,
        "businessTypes": 0,
        "method": "示例值",
        "requestMethod": "示例值",
        "operatorType": 0,
        "operName": "示例值",
        "deptName": "示例值",
        "operUrl": "示例值",
        "operIp": "示例值",
        "operLocation": "示例值",
        "operParam": "示例值",
        "jsonResult": "示例值",
        "status": 0,
        "errorMsg": "示例值",
        "operTime": null,
        "costTime": 0
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/monitor/operlog/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; operLog: SysOperLog

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. remove

- **路径**：`/monitor/operlog/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：operIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. clean

- **路径**：`/monitor/operlog/{operIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysPostController

- 类路径：`/system/post`

#### 1. list

- **路径**：`/system/post/system/post`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：post: SysPost

**请求示例**：
```json
    {
        "postId": 0,
        "postCode": "示例值",
        "postName": "示例值",
        "postSort": 0,
        "status": "示例值",
        "flag": false
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/system/post/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; post: SysPost

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. getInfo

- **路径**：`/system/post/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：postId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. add

- **路径**：`/system/post/{postId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：post: SysPost

**请求示例**：
```json
    {
        "postId": 0,
        "postCode": "示例值",
        "postName": "示例值",
        "postSort": 0,
        "status": "示例值",
        "flag": false
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. remove

- **路径**：`/system/post/{postIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：postIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. optionselect

- **路径**：`/system/post/optionselect`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysProfileController

- 类路径：`/system/user/profile`

#### 1. profile

- **路径**：`/system/user/profile/system/user/profile`
- **HTTP**：RequestMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 2. updatePwd

- **路径**：`/system/user/profile/updatePwd`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：params: String>

**请求示例**：
```json
    // String> 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysRegisterController

#### 1. register

- **路径**：`/register`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：user: RegisterBody

**请求示例**：
```json
    // RegisterBody 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysRoleController

- 类路径：`/system/role`

#### 1. list

- **路径**：`/system/role/system/role`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：role: SysRole

**请求示例**：
```json
    {
        "roleId": 0,
        "roleName": "示例值",
        "roleKey": "示例值",
        "roleSort": 0,
        "dataScope": "示例值",
        "menuCheckStrictly": false,
        "deptCheckStrictly": false,
        "status": "示例值",
        "delFlag": "示例值",
        "flag": false,
        "menuIds": 0,
        "deptIds": 0,
        "permissions": null
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/system/role/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; role: SysRole

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. getInfo

- **路径**：`/system/role/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：roleId: Long

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. add

- **路径**：`/system/role/{roleId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：role: SysRole

**请求示例**：
```json
    {
        "roleId": 0,
        "roleName": "示例值",
        "roleKey": "示例值",
        "roleSort": 0,
        "dataScope": "示例值",
        "menuCheckStrictly": false,
        "deptCheckStrictly": false,
        "status": "示例值",
        "delFlag": "示例值",
        "flag": false,
        "menuIds": 0,
        "deptIds": 0,
        "permissions": null
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 5. dataScope

- **路径**：`/system/role/dataScope`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：role: SysRole

**请求示例**：
```json
    {
        "roleId": 0,
        "roleName": "示例值",
        "roleKey": "示例值",
        "roleSort": 0,
        "dataScope": "示例值",
        "menuCheckStrictly": false,
        "deptCheckStrictly": false,
        "status": "示例值",
        "delFlag": "示例值",
        "flag": false,
        "menuIds": 0,
        "deptIds": 0,
        "permissions": null
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. changeStatus

- **路径**：`/system/role/changeStatus`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：role: SysRole

**请求示例**：
```json
    {
        "roleId": 0,
        "roleName": "示例值",
        "roleKey": "示例值",
        "roleSort": 0,
        "dataScope": "示例值",
        "menuCheckStrictly": false,
        "deptCheckStrictly": false,
        "status": "示例值",
        "delFlag": "示例值",
        "flag": false,
        "menuIds": 0,
        "deptIds": 0,
        "permissions": null
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 7. remove

- **路径**：`/system/role/{roleIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：roleIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 8. optionselect

- **路径**：`/system/role/optionselect`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 9. allocatedList

- **路径**：`/system/role/authUser/allocatedList`
- **HTTP**：GetMapping
- **返回类型**：TableDataInfo
- **参数**：user: SysUser

**请求示例**：
```json
    {
        "userId": 0,
        "deptId": 0,
        "userName": "示例值",
        "nickName": "示例值",
        "email": "示例值",
        "phonenumber": "示例值",
        "sex": "示例值",
        "avatar": "示例值",
        "password": "示例值",
        "status": "示例值",
        "delFlag": "示例值",
        "loginIp": "示例值",
        "loginDate": null,
        "pwdUpdateDate": null,
        "dept": {
            "deptId": 0,
            "parentId": 0,
            "ancestors": "示例值",
            "deptName": "示例值",
            "orderNum": 0,
            "leader": "示例值",
            "phone": "示例值",
            "email": "示例值",
            "status": "示例值",
            "delFlag": "示例值",
            "parentName": "示例值",
            "children": null
        },
        "roles": null,
        "roleIds": 0,
        "postIds": 0,
        "roleId": 0
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 10. unallocatedList

- **路径**：`/system/role/authUser/unallocatedList`
- **HTTP**：GetMapping
- **返回类型**：TableDataInfo
- **参数**：user: SysUser

**请求示例**：
```json
    {
        "userId": 0,
        "deptId": 0,
        "userName": "示例值",
        "nickName": "示例值",
        "email": "示例值",
        "phonenumber": "示例值",
        "sex": "示例值",
        "avatar": "示例值",
        "password": "示例值",
        "status": "示例值",
        "delFlag": "示例值",
        "loginIp": "示例值",
        "loginDate": null,
        "pwdUpdateDate": null,
        "dept": {
            "deptId": 0,
            "parentId": 0,
            "ancestors": "示例值",
            "deptName": "示例值",
            "orderNum": 0,
            "leader": "示例值",
            "phone": "示例值",
            "email": "示例值",
            "status": "示例值",
            "delFlag": "示例值",
            "parentName": "示例值",
            "children": null
        },
        "roles": null,
        "roleIds": 0,
        "postIds": 0,
        "roleId": 0
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 11. cancelAuthUser

- **路径**：`/system/role/authUser/cancel`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(roleId) roleId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 12. cancelAuthUserAll

- **路径**：`/system/role/authUser/cancelAll`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(roleId) roleId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 13. selectAuthUserAll

- **路径**：`/system/role/authUser/selectAll`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(roleId) roleId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysUserController

- 类路径：`/system/user`

#### 1. list

- **路径**：`/system/user/system/user`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：user: SysUser

**请求示例**：
```json
    {
        "userId": 0,
        "deptId": 0,
        "userName": "示例值",
        "nickName": "示例值",
        "email": "示例值",
        "phonenumber": "示例值",
        "sex": "示例值",
        "avatar": "示例值",
        "password": "示例值",
        "status": "示例值",
        "delFlag": "示例值",
        "loginIp": "示例值",
        "loginDate": null,
        "pwdUpdateDate": null,
        "dept": {
            "deptId": 0,
            "parentId": 0,
            "ancestors": "示例值",
            "deptName": "示例值",
            "orderNum": 0,
            "leader": "示例值",
            "phone": "示例值",
            "email": "示例值",
            "status": "示例值",
            "delFlag": "示例值",
            "parentName": "示例值",
            "children": null
        },
        "roles": null,
        "roleIds": 0,
        "postIds": 0,
        "roleId": 0
    }
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. export

- **路径**：`/system/user/list`
- **HTTP**：GetMapping
- **返回类型**：void
- **参数**：response: HttpServletResponse; user: SysUser

**请求示例**：
```json
    // HttpServletResponse 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 3. importData

- **路径**：`/system/user/export`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(userId) userId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 4. importTemplate

- **路径**：`/system/user/importData`
- **HTTP**：PostMapping
- **返回类型**：void
- **参数**：@PathVariable(userId) userId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // void 未找到定义
```

#### 5. add

- **路径**：`/system/user/importTemplate`
- **HTTP**：PostMapping
- **返回类型**：AjaxResult
- **参数**：user: SysUser

**请求示例**：
```json
    {
        "userId": 0,
        "deptId": 0,
        "userName": "示例值",
        "nickName": "示例值",
        "email": "示例值",
        "phonenumber": "示例值",
        "sex": "示例值",
        "avatar": "示例值",
        "password": "示例值",
        "status": "示例值",
        "delFlag": "示例值",
        "loginIp": "示例值",
        "loginDate": null,
        "pwdUpdateDate": null,
        "dept": {
            "deptId": 0,
            "parentId": 0,
            "ancestors": "示例值",
            "deptName": "示例值",
            "orderNum": 0,
            "leader": "示例值",
            "phone": "示例值",
            "email": "示例值",
            "status": "示例值",
            "delFlag": "示例值",
            "parentName": "示例值",
            "children": null
        },
        "roles": null,
        "roleIds": 0,
        "postIds": 0,
        "roleId": 0
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 6. remove

- **路径**：`/system/user/{userIds}`
- **HTTP**：DeleteMapping
- **返回类型**：AjaxResult
- **参数**：userIds: Long[]

**请求示例**：
```json
    // Long[] 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 7. resetPwd

- **路径**：`/system/user/resetPwd`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(userId) userId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 8. changeStatus

- **路径**：`/system/user/changeStatus`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：@PathVariable(userId) userId: Long

**请求示例**：
```json
    // 无请求体
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 9. insertAuthRole

- **路径**：`/system/user/authRole/{userId}`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：userId: Long; roleIds: Long[]

**请求示例**：
```json
    // Long 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

#### 10. deptTree

- **路径**：`/system/user/authRole`
- **HTTP**：PutMapping
- **返回类型**：AjaxResult
- **参数**：dept: SysDept

**请求示例**：
```json
    {
        "deptId": 0,
        "parentId": 0,
        "ancestors": "示例值",
        "deptName": "示例值",
        "orderNum": 0,
        "leader": "示例值",
        "phone": "示例值",
        "email": "示例值",
        "status": "示例值",
        "delFlag": "示例值",
        "parentName": "示例值",
        "children": null
    }
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### SysUserOnlineController

- 类路径：`/monitor/online`

#### 1. list

- **路径**：`/monitor/online/monitor/online`
- **HTTP**：RequestMapping
- **返回类型**：TableDataInfo
- **参数**：ipaddr: String; userName: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    {
        "total": 0,
        "code": 0,
        "msg": "示例值"
    }
```

#### 2. forceLogout

- **路径**：`/monitor/online/list`
- **HTTP**：GetMapping
- **返回类型**：AjaxResult
- **参数**：tokenId: String

**请求示例**：
```json
    // String 未找到定义
```

**响应示例**：
```json
    // AjaxResult 未找到定义
```

### TestController

- 类路径：`/test/user`

#### 1. userList

- **路径**：`/test/user/test/user`
- **HTTP**：RequestMapping
- **返回类型**：R<List<UserEntity>>
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // R<List<UserEntity 未找到定义
```

#### 2. save

- **路径**：`/test/user/list`
- **HTTP**：GetMapping
- **返回类型**：R<String>
- **参数**：user: UserEntity

**请求示例**：
```json
    // UserEntity 未找到定义
```

**响应示例**：
```json
    // R<String 未找到定义
```

#### 3. update

- **路径**：`/test/user/{userId}`
- **HTTP**：GetMapping
- **返回类型**：R<String>
- **参数**：user: UserEntity

**请求示例**：
```json
    // UserEntity 未找到定义
```

**响应示例**：
```json
    // R<String 未找到定义
```

#### 4. getUserId

- **路径**：`/test/user/save`
- **HTTP**：PostMapping
- **返回类型**：Integer
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // Integer 未找到定义
```

#### 5. setUserId

- **路径**：`/test/user/update`
- **HTTP**：PutMapping
- **返回类型**：void
- **参数**：userId: Integer

**请求示例**：
```json
    // Integer 未找到定义
```

**响应示例**：
```json
    // void 未找到定义
```

#### 6. getUsername

- **路径**：`/test/user/{userId}`
- **HTTP**：DeleteMapping
- **返回类型**：String
- **参数**：-

**请求示例**：
```json
    // 无请求体或仅查询参数
```

**响应示例**：
```json
    // String 未找到定义
```

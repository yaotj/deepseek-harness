# 03 fep-dev-server (AGM 闸机前置) + fep-acc-server (ACC 员工卡通知)

> 公共报文骨架（`ItpCommonFormRequest` 的 `sign` / `charset` / `format` / `timestamp` / `deviceId` / `signType` / `bizData`）见 [README.md](README.md) &sect;2，本文件不重复列出。
>
> 两个模块的共同特征：**纯转发、无 DB、不验签**。`fep-dev-server` 与 `fep-acc-server` 自身都没有验签代码，`sign` 字段原样透传给下游。fep-acc 的真实验签发生在 account-server 的 `AccountRequestVerifier`（摘要式，`signType=00` 免签）。因此压测时对这两个模块的所有端点可以送**任意 `sign`**。

## 0. 端点总览

| # | 接口编号 | 中文名 | 容器内路径 | 模块 | 下游 | 写库 |
|---|---|---|---|---|---|---|
| 1 | IF1A-01 | 闸机检票通知 | `/ci/agm/notiVerifyResult` | fep-dev-server | ticket-server | 否（本模块）/ 是（下游） |
| 2 | IF1A-02 | 密钥同步 | `/ci/agm/requestSynKeyList` | fep-dev-server | key-server | 否（本模块）/ 是（下游） |
| 3 | IF1A-04 | 票卡状态查询 | `/ci/agm/requestQrCodeStatus` | fep-dev-server | ticket-server | 否 |
| 4 | IF1A-03 | 设备心跳 | `/ci/agm/deviceHeartbeat` 或 `/ci/agm/notiDeviceHeard` | fep-dev-server | 无 | 否 |
| 5 | IF3A 族 | 员工码开卡通知 | `/employee_card/notify` | fep-acc-server | account-server | 否（本模块）/ 是（下游） |
| 6 | IF3A 族 | 员工信息变更通知 | `/employee_card/update_notify` | fep-acc-server | account-server | 否（本模块）/ 是（下游） |

fep-acc 两条只能定到 `IF3A`（员工卡）这个编号族：代码与注释里都没有标注具体小号，**NEVER 凭猜给它们编号**。

**覆盖缺口**：规格里属于 AGM 的数字人民币硬钱包端点 IF1A-06 `requestEncyWalletStatus` 与 IF1A-07 `ecnyWalletTranUpload` 本应在 `/ci/agm` 前缀下，但代码里不存在（`FepAgmController` 只有 4 个方法 / 5 个 URL 映射，已 grep 确认），因此不能给它们写测试用例。

---

## A. fep-dev-server -- AGM 闸机前置（port 9104）

唯一 Controller：`com.chinasofti.huateng.fep.dev.controller.FepAgmController`，类级 `@RequestMapping("/ci/agm")`。

错误码枚举 `FepDevErrorCodeEnum`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/constant/FepDevErrorCodeEnum.java:4`）：
- `SUCCESS` = `"0000"`
- `INVALID_PARAM` = `"1001"`
- `SYSTEM_ERROR` = `"9999"`

---

### 1. 闸机检票通知（IF1A-01）

- **对外 URL**：`POST /fep-dev/ci/agm/notiVerifyResult` 或 `POST /itpagm/ci/agm/notiVerifyResult`（两个网关前缀 `/fep-dev/` 与 `/itpagm/` rewrite 都是 `/`，都落到同一个端点）
- **容器内路径**：`/ci/agm/notiVerifyResult`
- **Controller**：`FepAgmController#notifyVerifyResult`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/controller/FepAgmController.java:47`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：ticket-server（`service.ticket.url`，默认 `http://ticket-server-bsyju-svc.itp.svc:9100`），出向路径 `POST /ci/agm/notiVerifyResult`（`TicketClient:113`，**与入向同名**）

**bizData 请求字段** -- DTO `com.chinasofti.huateng.fep.dev.model.NotifyVerifyResultDeviceReqDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/NotifyVerifyResultDeviceReqDTO.java:4`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| deviceId | String | 无（代码无 Bean Validation，空值不会 400） | 设备编号（Controller :61 会把公共参数的 deviceId 覆盖写入此字段） |
| itpUserId | String | 无 | ITP 用户编号（十六进制，`DeviceUserIdCodec` 会按十六进制转十进制并按渠道补零位） |
| trxType | String | 无 | 交易类型：01=进站，02=出站，03=超时出站 |
| issueChannelCode | String | 无 | 码体发行渠道 |
| signChannelCode | String | 无 | 签约渠道 |
| cardId | String | 无 | 卡号（**handler 会校验非空白**） |
| cardType | String | 无 | 卡类型编码 |
| handleDateTime | String | 无 | 闸机处理时间 |
| handleStationCode | String | 无 | 处理车站编码 |
| trxAmount | String | 无 | 交易金额（单位分） |
| overtimeAmount | String | 无 | 超时金额（单位分） |
| lastTicketStatus | String | 无 | 上次票卡状态 |
| handleResultCode | String | 无 | 读写器处理结果码（`"000"` 为成功） |
| lastHandleStationCode | String | 无 | 上次处理车站编码 |
| lastHandleDateTime | String | 无 | 上次处理时间 |
| ticketTransSeq | String | 无 | 票卡交易序号（兼容拼写错误 `tikcetTransSeq`，:70 有额外 setter） |
| excessFareType | String | 无 | 补站类型：空=真实检票，01=补进站，02=补出站 |
| adviceOpt | String | 无 | BOM 操作类型：空=真实闸机检票/补站，018=补进站，005=免费更新，006=付费更新 |
| reserve1 | String | 无 | 预留字段1 |
| reserve2 | String | 无 | 预留字段2 |
| companionFlag | String | 无 | 同行票标识：Y=同行票，C=第三方票，空=普通票 |
| paymentVendor | String | 无 | 支付渠道编码（如 ALIPAY） |
| requestSignSeq | String | 无 | 签约流水号 |
| channelType | String | 无 | 交易渠道类型：00 闸机、01 蓝牙、02 BOM、03 自助补站 |

**设备侧 -> ticket-server 字段映射**（`GateTransactionHandler#toTicketRequest` :70~97，24 个字段逐字搬运，字段名两侧完全一致）

| 设备侧字段 (NotifyVerifyResultDeviceReqDTO) | ticket-server 侧字段 (NotifyVerifyResultReqDTO) |
|---|---|
| deviceId | deviceId |
| itpUserId | itpUserId（**经 `DeviceUserIdCodec.normalize` 转换后写入**） |
| trxType | trxType |
| issueChannelCode | issueChannelCode |
| signChannelCode | signChannelCode |
| cardId | cardId |
| cardType | cardType |
| handleDateTime | handleDateTime |
| handleStationCode | handleStationCode |
| trxAmount | trxAmount |
| overtimeAmount | overtimeAmount |
| lastTicketStatus | lastTicketStatus |
| handleResultCode | handleResultCode |
| lastHandleStationCode | lastHandleStationCode |
| lastHandleDateTime | lastHandleDateTime |
| ticketTransSeq | ticketTransSeq |
| excessFareType | excessFareType |
| adviceOpt | adviceOpt |
| reserve1 | reserve1 |
| reserve2 | reserve2 |
| companionFlag | companionFlag |
| paymentVendor | paymentVendor |
| requestSignSeq | requestSignSeq |
| channelType | channelType |

**`DeviceUserIdCodec` 转换规则**（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/device/DeviceUserIdCodec.java:17`）：将 `itpUserId` 按**十六进制**解析为十进制（`new BigInteger(itpUserId.trim(), 16).toString(10)`），再按渠道要求的位数左侧补零。造数时必须知道这个转换，否则对不上库里的值。转换失败（非法十六进制字符串）时返回原值。

**响应字段** -- `NotifyVerifyResultAckDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/NotifyVerifyResultAckDTO.java:6`，extends `CommonResult`，仅 retCode + retMsg）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码，`"0000"` 为成功 |
| retMsg | String | 返回信息 |

**业务短路与异常分支**（`GateTransactionHandler`，`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/gate/GateTransactionHandler.java:33`）
- `request == null` 且 `bizData` 为空/空白 -> Controller :50 返回 `retCode="1001"`, `retMsg="bizData不能为空"`
- `bizData` JSON 解析失败 -> Controller :58 返回 `retCode="1001"`, `retMsg="bizData格式错误"`
- `handleResultCode != "000"`（读写器非成功） -> **handler :35 直接短路，返回 `retCode="0000"` `retMsg="接收成功"`**，不调下游。测试必须覆盖这一支
- `cardId` 为空白 -> handler :44 返回 `retCode="1001"`, `retMsg="cardId不能为空"`
- ticket-server 返回 null -> handler :60 返回 `retCode="9999"`, `retMsg="闸机检票通知服务异常"`
- ticket-server 正常返回 -> 原样透传 `retCode` + `retMsg`

**压测备注**：本模块不写库、不验签。调下游 ticket-server（HTTP 直连，`service.ticket.url`），ticket-server 会写 `QRCODE_TXN_DETAIL` 与 `QRCODE_STATUS` 表、触发扣费链路。压测需关注下游 ticket-server 的数据库连接池与表锁压力。

---

### 2. 密钥同步（IF1A-02）

- **对外 URL**：`POST /fep-dev/ci/agm/requestSynKeyList` 或 `POST /itpagm/ci/agm/requestSynKeyList`
- **容器内路径**：`/ci/agm/requestSynKeyList`
- **Controller**：`FepAgmController#requestSynKeyList`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/controller/FepAgmController.java:70`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：key-server（`service.key.url`，默认 `http://172.20.211.200:30015`），出向路径 `POST /requestAgmSynKeyList`（`KeyClient:44`，**无类级前缀**）

**bizData 请求字段** -- DTO `com.chinasofti.huateng.fep.dev.model.RequestSynKeyListReqDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestSynKeyListReqDTO.java:6`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| keyCurVerList | List\<KeyCurVerReqDTO\> | 无（代码无 Bean Validation，空值不会 400） | 密钥当前版本信息列表（**handler 会校验非空**） |

**KeyCurVerReqDTO** 子对象（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/KeyCurVerReqDTO.java:4`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| issueChannelCode | String | 无 | 码体发行渠道 |
| keyId | String | 无 | 密钥标识 |
| keyBathNumber | String | 无 | 密钥批次号 |

**响应字段** -- `RequestSynKeyListRespDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestSynKeyListRespDTO.java:8`，extends `CommonResult`）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| keyCurVerList | List\<KeyCurVerRespDTO\> | 密钥版本响应列表 |

**KeyCurVerRespDTO** 子对象（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/KeyCurVerRespDTO.java:8`）

| 字段 | 类型 | 说明 |
|---|---|---|
| issueChannelCode | String | 码体发行渠道 |
| keyId | String | 密钥标识 |
| keyBathNumber | String | 密钥批次号 |
| needUpdateYN | String | 是否需要更新：Y/N |
| keyList | List\<AgmKeyItemDTO\> | 密钥材料列表 |

**AgmKeyItemDTO** 子对象（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/agm/AgmKeyItemDTO.java:6`）

| 字段 | 类型 | 说明 |
|---|---|---|
| keyBathNumber | String | 密钥批次号 |
| keyIdx | String | 密钥索引 |
| keyValue | String | 密钥值 |
| keyEffectiveDate | String | 密钥生效日期 |
| kvc | String | KVC 校验值 |
| reserve | String | 预留 |

**业务短路与异常分支**（`KeySyncHandler`，`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/keysync/KeySyncHandler.java:40`）
- `bizData` 为空/空白 -> Controller :74 返回 `retCode="1001"`, `retMsg="bizData不能为空"`
- `bizData` JSON 解析失败 -> Controller :80 返回 `retCode="1001"`, `retMsg="bizData格式错误"`
- `keyCurVerList` 为 null 或 empty -> handler :43 返回 `retCode="1001"`, `retMsg="keyCurVerList不能为空"`
- key-server 返回 null -> handler :57 返回 `retCode="9999"`, `retMsg="密钥服务无响应"`
- **调用 key-server 抛任何异常** -> handler :67 catch 全部异常，返回 `retCode="9999"`, `retMsg="密钥同步服务异常"`
- key-server 正常返回 -> 透传 retCode/retMsg，并把密钥版本列表从 key-server 的 `AgmKeyCurVerDTO` 转换回 `KeyCurVerRespDTO`

**压测备注**：本模块不写库、不验签。调下游 key-server（HTTP 直连，`service.key.url`）。key-server 会查密钥池表。handler 内 catch 全部异常返回 `SYSTEM_ERROR` 而不抛出，因此 key-server 不通时不会触发全局异常处理器。

---

### 3. 票卡状态查询（IF1A-04）

- **对外 URL**：`POST /fep-dev/ci/agm/requestQrCodeStatus` 或 `POST /itpagm/ci/agm/requestQrCodeStatus`
- **容器内路径**：`/ci/agm/requestQrCodeStatus`
- **Controller**：`FepAgmController#requestQrCodeStatus`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/controller/FepAgmController.java:92`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：ticket-server（`service.ticket.url`），出向路径 `POST /ci/app/queryQrCodeStatus`（`TicketClient:59`，**入向是 `/ci/agm/requestQrCodeStatus`、出向是 `/ci/app/queryQrCodeStatus`，两侧既不同名也不同前缀**）

**bizData 请求字段** -- DTO `com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestQrCodeStatusReqDTO.java:4`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| itpUserId | String | 无（代码无 Bean Validation，空值不会 400） | ITP 用户编号（十六进制） |
| cardId | String | 无 | 卡号（**handler 会校验非空白**） |
| trxType | String | 无 | 交易类型 |
| ticketTransSeq | String | 无 | 票卡交易序号 |
| qrType | String | 无 | 二维码类型 |

**响应字段** -- `RequestQrCodeStatusRespDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/RequestQrCodeStatusRespDTO.java:6`，extends `CommonResult`）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| itpUserId | String | ITP 用户编号（**回填的是请求原值，未经 codec 转换**） |
| cardId | String | 卡号（回填请求原值） |
| lastTicketStatus | String | 上次票卡状态（**ticket-server 响应的 `status` 改名为 `lastTicketStatus`**） |
| lastHandleDateTime | String | 上次处理时间（**ticket-server 响应的 `lastTxnTime` 改名为 `lastHandleDateTime`**） |

**响应字段改名映射**（`QrCodeStatusHandler` :61~62）：

| ticket-server 返回字段 | fep-dev 对外字段 |
|---|---|
| status | lastTicketStatus |
| lastTxnTime | lastHandleDateTime |

**业务短路与异常分支**（`QrCodeStatusHandler`，`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/qrcode/QrCodeStatusHandler.java:35`）
- `bizData` 为空/空白 -> Controller :96 返回 `retCode="1001"`, `retMsg="bizData不能为空"`
- `bizData` JSON 解析失败 -> Controller :102 返回 `retCode="1001"`, `retMsg="bizData格式错误"`
- `cardId` 为空白 -> handler :37 返回 `retCode="1001"`, `retMsg="cardId不能为空"`
- ticket-server 返回 null -> handler :49 返回 `retCode="9999"`, `retMsg="票卡状态查询服务异常"`
- ticket-server 正常返回 -> 透传 retCode/retMsg，补充 itpUserId、cardId、lastTicketStatus、lastHandleDateTime

**压测备注**：本模块不写库、不验签。调下游 ticket-server（只读查询），压力取决于 ticket-server 的 `QRCODE_STATUS` 表查询性能。`itpUserId` 同样经过 `DeviceUserIdCodec.normalize` 转换（十六进制 -> 十进制 + 补零位）后再传给 ticket-server。

---

### 4. 设备心跳（IF1A-03）

- **对外 URL**：`POST /fep-dev/ci/agm/deviceHeartbeat` 或 `POST /fep-dev/ci/agm/notiDeviceHeard`（两个别名同一方法）；另有 `/itpagm/ci/agm/deviceHeartbeat` 与 `/itpagm/ci/agm/notiDeviceHeard`
- **容器内路径**：`/ci/agm/deviceHeartbeat` 与 `/ci/agm/notiDeviceHeard`（同一方法的两个 URL 映射）
- **Controller**：`FepAgmController#deviceHeartbeat`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/controller/FepAgmController.java:112`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：无

**bizData 请求字段** -- 无。本端点不解析 bizData，甚至不检查 request 是否为 null。直接构造成功响应返回。

**响应字段** -- `DeviceHeartbeatRespDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-dev-server/src/main/java/com/chinasofti/huateng/fep/dev/model/DeviceHeartbeatRespDTO.java:6`，extends `CommonResult`，无额外字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 固定 `"0000"` |
| retMsg | String | 固定 `"成功"` |

**业务短路与异常分支**
- 无任何校验、无任何分支。**无条件返回 `retCode="0000"` `retMsg="成功"`**。

**压测备注**：不调任何下游、不落库、不解析 bizData、无条件返回 SUCCESS。这是整个 ITP 系统里最适合做**纯网关 + 纯应用吞吐基准**的端点。可以用来测量 istio 网关开销、Tomcat 虚拟线程调度能力与 Spring MVC 的裸吞吐上限。压测时 `bizData` 可以为空或任意字符串，`sign` 可以为任意值。两个 URL 别名 `/deviceHeartbeat` 与 `/notiDeviceHeard` 行为完全一致。

---

## B. fep-acc-server -- ACC 员工卡通知前置（port 9110）

唯一 Controller：`com.chinasofti.huateng.fep.acc.controller.EmployeeCardController`，**无类级 `@RequestMapping`**。继承 `BaseAccController`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-acc-server/src/main/java/com/chinasofti/huateng/fep/acc/controller/BaseAccController.java:9`），其 `parseBizData` 在 bizData 为 null 或空白时按 `"{}"` 处理（不会抛异常，会得到一个空字段的 DTO）。

网关前缀 `/fep-acc/` rewrite 为 `/`。

**两条入向 URL 是绝对路径字面量**（类上只有 `@RestController`），**NEVER 给这个类加类级前缀** —— 加了等于 ACC 侧全部 404。

**入向用下划线、出向用驼峰，两套命名都真实存在**：

| 入向（ACC -> fep-acc） | 出向（fep-acc -> account-server） |
|---|---|
| `POST /employee_card/notify` | `POST /employeeCard/notify`（`AccountClient:159`） |
| `POST /employee_card/update_notify` | `POST /employeeCard/updateNotify`（`AccountClient:177`） |

**已知环境问题（压测前必读）**：`consumes = multipart/form-data` 是**硬约束**。2026-08-20 日志里 ACC 侧按 `application/x-www-form-urlencoded` 发送，fep-acc 返回 `HttpMediaTypeNotSupportedException`，且 `GlobalControllerExceptionHandler` 自身在返回时二次抛出 `HttpMediaTypeNotAcceptableException: No acceptable representation`（`accept=text/html` 时无可用 message converter），最终落到 `/error`。因此这两个端点用错 Content-Type 时**拿不到 `retCode`、拿到的是 `/error` 页面**，压测脚本的断言必须能区分这一种失败（见 `docs/ops/生产环境清单.md` §P2）。

---

### 5. 员工码开卡通知（IF3A）

- **对外 URL**：`POST /fep-acc/employee_card/notify`
- **容器内路径**：`/employee_card/notify`
- **Controller**：`EmployeeCardController#employeeCardNotify`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-acc-server/src/main/java/com/chinasofti/huateng/fep/acc/controller/EmployeeCardController.java:34`）
- **Content-Type**：`multipart/form-data`（注意：与 fep-dev 的 `x-www-form-urlencoded` 不同）
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`AccountClient.notifyEmployeeCardStatus`）

**bizData 请求字段** -- DTO `com.chinasofti.huateng.model.employee.EmployeeCardNotifyReqDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/employee/EmployeeCardNotifyReqDTO.java:8`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| cardList | List\<EmployeeCardInfoDTO\> | 无（代码无 Bean Validation，空值不会 400） | 员工码信息列表 |

**EmployeeCardInfoDTO** 子对象（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/employee/EmployeeCardInfoDTO.java:6`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| phone | String | 无 | 手机号 |
| cardNo | String | 无 | 员工卡号 |
| cardStatus | Integer | 无 | 卡状态 |
| employeeName | String | 无 | 员工姓名 |
| idCardNo | String | 无 | 身份证号 |
| company | String | 无 | 公司 |
| center | String | 无 | 中心 |
| department | String | 无 | 部门 |
| position | String | 无 | 岗位 |
| photoUrl | String | 无 | 照片 URL |

**响应字段** -- `EmployeeCardNotifyResult`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/employee/EmployeeCardNotifyResult.java:11`，extends `CommonResult`）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| failList | List\<FailureItem\> | 处理失败的卡号列表 |

**FailureItem** 子对象

| 字段 | 类型 | 说明 |
|---|---|---|
| cardNo | String | 失败的卡号 |
| reason | String | 失败原因 |

**业务短路与异常分支**
- `BaseAccController.parseBizData` 在 bizData 为空时按 `"{}"` 处理，会得到 `cardList=null` 的 DTO，不会抛异常
- Controller 直接调 `accountClient.notifyEmployeeCardStatus(bizData)` 并原样返回结果
- 本模块没有任何额外校验逻辑，所有业务校验在 account-server 侧完成
- account-server 的验签由 `AccountRequestVerifier` 执行（`signType=00` 免签），fep-acc 本身不验签

**压测备注**：本模块不写库、不验签、纯转发。注意 Content-Type 是 `multipart/form-data`，压测脚本 MUST 用 multipart 形式发送，不能用 `x-www-form-urlencoded`。调下游 account-server，account-server 会写 `USER_ITP_REG_INFO` 等表。

---

### 6. 员工信息变更通知（IF3A）

- **对外 URL**：`POST /fep-acc/employee_card/update_notify`
- **容器内路径**：`/employee_card/update_notify`
- **Controller**：`EmployeeCardController#employeeInfoUpdateNotify`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-acc-server/src/main/java/com/chinasofti/huateng/fep/acc/controller/EmployeeCardController.java:43`）
- **Content-Type**：`multipart/form-data`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`AccountClient.updateEmployeeInfo`）

**bizData 请求字段** -- DTO `com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/employee/EmployeeInfoUpdateNotifyReqDTO.java:6`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| company | String | 无（代码无 Bean Validation，空值不会 400） | 公司 |
| center | String | 无 | 中心 |
| department | String | 无 | 部门 |
| position | String | 无 | 岗位 |
| cardNo | String | 无 | 员工卡号 |
| photo | String | 无 | 照片（Base64 或 URL） |

**响应字段** -- `CommonResult`（`com.chinasofti.huateng.common.response.CommonResult`）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |

**业务短路与异常分支**
- 与端点 5 相同：`BaseAccController.parseBizData` 在 bizData 为空时按 `"{}"` 处理
- 本模块没有任何额外校验逻辑，直接调 `accountClient.updateEmployeeInfo(bizData)` 并原样返回
- 验签在 account-server 的 `AccountRequestVerifier`，fep-acc 不验签

**压测备注**：本模块不写库、不验签、纯转发。Content-Type 是 `multipart/form-data`。调下游 account-server。

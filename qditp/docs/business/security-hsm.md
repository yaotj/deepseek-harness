---
业务域: 安全服务与加密机
模块: acc-security-server, acc-secure-server
---

# 提示词：安全服务与加密机（HSM）

## 何时读本文件
密钥申请、SM2/DPK、MAC/TAC 校验、卡证书、签名指令数据、逻辑卡号，以及 ITP 与 ACC 之间安全类接口（IF7B-xx）相关改动。

## ⚠️ 两个模块名字极像，职责完全不同

| | acc-security-server | acc-secure-server |
|---|---|---|
| 角色 | **本地安全服务**，TCP 直连加密机（HSM），真正做密码运算 | **出向 HTTP 代理**，把请求转发给 ACC 清分中心，自身不做任何密码运算 |
| 端口 | **9012**（Undertow） | 9099 |
| `spring.application.name` | `acc-security` | `acc-secure` |
| 是否在用 | **在用**，`service.security.url=http://127.0.0.1:9012` 被 account-server / key-server / industry-data-server / fep-dev-server 引用 | **未被任何模块调用**（全仓无 `AccSecureClient`、无 9099 引用，`rpc` 模块无对应 Client），属骨架/预留的死模块，但已在根 `pom.xml` 参与构建 |

**IF7B-01（请求逻辑卡号）已于 2026-09-09 迁出**：实现搬到 `card-pool-server` 的
`AccLogicNumClient` + `AccSignUtils`，由该模块用 `acc.secure.*` 配置**直连 ACC**，
`rpc` 侧 `AccSecureClient` / `@EnableRpcAccSecure` 已删除。本模块内的 IF7B-01 代码仍在，
但**没有任何调用方**；改 IF7B-01 报文 **MUST 改 card-pool-server 那一份**。

**MUST**：需要密码运算时用 `SecurityClient` 指向 **acc-security-server**。
**NEVER** 误改 acc-secure-server 期望生效，除非用户明确要接通 ACC 直连通路。

---

## acc-security-server（9012）
启动类 `acc-security-server/.../SecurityServerApplication.java`
启动时按 `thread.corePoolSize` 拉起 N 条到加密机的 TCP 长连（`SocketClient`），业务经 `ClientSendMsg.sendRawMsg` 发原始报文（`B0xx` 指令）。
加密机地址是**两个独立配置键**：`security.firstIp`（IP）与 `security.firstPort`（端口），另有 `security.secondPort`；勿写成 `firstIp:port` 单键形式。

Controller（**均无接口编号标注**）：
- `ItpRemainingController`（`/ci/itp`）：`/requestCaKey`、`/requestUserSm2Key`、`/requestExportUserPriKey`、`/requestDPK`、`/requestHceCardData`
- `ItpSignPubkeyController`（`/ci/itp`）：`/requestSignPubkey`、`/requestSignInsData`
- `CommonMacController`：`/verify/mac1`、`/get/mac2`
- `CommonTacController`：`/singleticket/check`、`/cpu/check`
- `CommonSaleAndRefundController`：`/get/saleAndRefund/key`
- `CertificateController`：`get/card/certificate`

核心实现：`itp/service/impl/ItpServiceImpl.java`、`service/impl/CommonMacServiceImpl`、`CommonTacServiceImpl`、`CommonSaleAndRefundServiceImpl`、`CertificateServiceImpl`
入向验签：`itp/service/ItpRequestSignVerifier.java`（参数排序 + `key=signKey`，SHA1/MD5）
逻辑卡号：`ItpLogicNumberService.java` —— **内存自增**，重启不持久化。涉及逻辑卡号发号的需求 **MUST** 提示这一限制。
异常：`HsmUnavailableException`
无数据库（无 mapper、无数据源）。

**死代码警告**：`itp/service/ItpRemainingService.java` 与 `itp/service/ItpRequestService.java` 方法体与 `ItpServiceImpl` 几乎逐行相同，但 controller 只注入 `ItpService`，二者模块内零引用。
改逻辑 **MUST** 只改 `ItpServiceImpl`，**NEVER** 改死类，也 **NEVER** 在两处重复维护。

---

## acc-secure-server（9099，当前未接线）
启动类 `acc-secure-server/.../AccSecureServer.java`
`accsecure/controller/AccSecureController.java`（前缀 `/ci/acc/secure`）：
- IF7B-01 `/requestQrLogicNumList`、IF7B-02 `/requestCaKey`、IF7B-03 `/requestUserSm2Key`、IF7B-04 `/requestSignPubkey`
- IF7B-05 `/requestExportUserPriKey`、IF7B-06 `/requestSignInsData`、IF7B-07 `/requestDpk`、IF7B-08 `/requestHecCardDate`

实现 `service/impl/AccSecureServiceImpl.java` 只有一个通用 `post()`：OkHttp 拼 ACC 公共报文（providerId/charset/format/deviceId/signType/sign，`AccSecureSignUtils`）发到 `acc.secure.base-url` + 配置路径，再兼容两种响应结构解析。

已知问题（接通前 **MUST** 先修）：
- 配置路径拼写错误：`requestQrLoigcNumList`（应为 `QrLogic`）、`requestHecCardDate`（acc-security 侧实际为 `requestHceCardData`）
- 部分 DTO 在 `accsecure.model.*` 与 `model` 模块**各存一份**（实测重复 5 组：`RequestSignInsData*` 在 `model/app`，`RequestUserSm2Key*` / `RequestExportUserPriKey*` / `RequestDpk*` / `RequestSignPubkey*` 在 `model/security`），违反"复用 model"约定。注意 `RequestCaKeyReqDTO/RespDTO`、`RequestQrLogicNumList*`、`RequestHecCardDate*` **只存在于 `accsecure.model.*`，`model` 模块中没有**，勿去 `model` 里找。
- 默认 `acc.secure.base-url=http://127.0.0.1:8080`、`sign-key` 为空、日志路径写死为个人目录 `/Users/zhoucong/logs`

## 编码约束（安全红线）
- 加密、签名、MAC/TAC、密钥派生逻辑 **NEVER** 擅自修改，**MUST** 提示人工复核安全合规性
- **NEVER** 在日志或对话中输出密钥、私钥、明文报文
- 新增 DTO **MUST** 放 `model` 模块复用，**NEVER** 在 `accsecure.model.*` 继续复制

## 参考原始文档
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`
- 技术规范第 4 部分（`0x2001~0x9004` 报文码）：**源文档不在仓库内**，需向甲方索取。

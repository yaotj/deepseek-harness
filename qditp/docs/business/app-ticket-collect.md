---
业务域: APP 扫码取票（单程票）
模块: collect-ticket-server
---

# 提示词：APP 扫码取票

## 何时读本文件
APP 端单程票购买张数限制、票价试算、下单、请求支付、取票订单查询、TVM 取票通知、取消订单、支付回调相关改动。

## 模块定位
`collect-ticket-server`，端口 **9098**，`spring.application.name=collect-ticket`
启动类 `collect-ticket-server/.../CollectTicketServer.java`

⚠️ 两个部署风险，改动时 **MUST** 提醒用户：
1. 端口 **9098 与 account-server 冲突**。
2. `application.properties` **未配置 `other.sql.*` 数据源**，但模块内存在 MyBatis mapper XML —— 数据源依赖外部环境注入，仓库内无其他配置文件。本地跑通前 **MUST** 先确认数据源来源。
   该文件实际含有的键：`service.token.url`、`collect.ticket.*`、`other.web.*`、`knife4j.production`、`service.route.mapping.default`，以及写死个人目录的 `server.tomcat.basedir` 与 `logging.file.path`（`/Users/zhoucong/logs`，容器内不可用，另见 `../ops/生产环境清单.md` §六 P1）。

## 接口清单
唯一 controller：`collect-ticket-server/.../controller/ci/app/TicketCollectController.java`（前缀 `/ci/app`）
- IF8A-09 `requestBuySinlgeTicketMaxNum`
- IF8A-10 `requestTicketPriceByStation`
- IF8A-11 `requestPaymentInfo`
- IF8A-20 `requestOrder`
- IF2A-02 `queryTicketCollectOrder`
- IF2A-03 `ticketCollectNotify`
- IF2A-05 `cancelTicketCollectOrder`
- `ticketCollectPayNotify`（支付结果回调，对应 collect-pay 的 `pay.center.callback-url`）

⚠️ IF8A-09/10 同时被 `para-server` 与 `fep-app-server/AppParaController` 实现，IF8A-11/20 同时被 `collect-pay-server/TvmAppOrderController` 实现。定位实现 **MUST** 确认调用方实际路由到哪个服务，**NEVER** 假设编号唯一对应一处代码。

## 核心类与流程
- `service/impl/TicketCollectServiceImpl.java`（下单→支付→取票主流程集中在此）
- `client/CollectPayClient.java`（调支付）
- `util/PaySignUtils.java`（**SHA256WithRSA** 签名，本域唯一符合 AGENTS.md 签名描述的地方）

流程：创建 `Ticket_Collect_Info`（未支付）+ `Ticket_Collect_Logs` → 请求支付 → 支付回调置支付结果 → 取票通知更新 `collectStatus` → 取消置 99 → 回执 `SUCCESS` / `FAIL`

## 状态取值（常量在 `TicketCollectServiceImpl` 顶部，无枚举）
- 订单：`ORDER_STATUS_UNPAID=0`、`ORDER_STATUS_PAID=100`、取消置 `99`
- 取票：`COLLECT_STATUS_NOT_COLLECTED=0`、`COLLECT_STATUS_SUCCESS=100`
- 支付结果：`PAY_RESULT_UNPAID=0`、`PAY_RESULT_PROCESSING=1`、`PAY_RESULT_SUCCESS=100`
- 二维码有效期 `QRCODE_EXPIRE_SECONDS=300`，过期错误码 `2101`
- 错误码枚举：`constant/CollectTicketErrorCodeEnum.java`

## 幂等（本仓最薄的一处，改动重点）
无 Redis 锁、无防重表、无可确认的唯一索引，仅靠"先查 `orderStatus`/`collectStatus` 再更新"+ 订单号唯一性。
在此模块新增支付/取票写入路径 **MUST**：
1. 先查当前状态并对终态短路返回；
2. 提示用户为关键表补唯一索引；
3. **NEVER** 引入 Redis 锁作为替代（与全项目约定不符）。

## 数据表
`Ticket_Collect_Info`、`Ticket_Collect_Logs`、`Ticket_Collect_Log_Detail`（mapper 在 `collect-ticket-server/src/main/resources/mapper/`）

## 参考原始文档
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（IF8A-09/10/11/20）
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`（IF2A-02/03/05）

## 附：collect-ticket-server 源码注释知识抽取（2026-09-16，阶段一）

> **行号会漂**。下面每条都带 `相对路径:行号`，那是 2026-09-16 抽取当时的位置；引用前 **MUST** 先 `grep` 原文串确认，**NEVER** 直接按行号跳。
> 抽取范围：`collect-ticket-server/src/main/java/**/*.java`（30 个文件）、`src/main/resources/mapper/*.xml`（3 个）、`src/main/resources/application.properties`（1 个），共 **34 个文件**。
> **本模块注释总量极少、且以字段级普通 Javadoc 为主**：mapper XML 与 `application.properties` 里**一条注释都没有**（`grep '<!--'`、`grep '^#'` 均 0 命中）；Java 侧绝大多数是复述字段名的一行 Javadoc，已按要求丢弃。真正带知识的只有下面这些 —— **数量远小于前几个模块，这是实测结果、不是抽取遗漏**。

### 取票主链路

- `collect-ticket-server` + `TicketCollectController`（类级，`collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/controller/ci/app/TicketCollectController.java:27~31`）：「取票服务 APP 接口。」+ 前缀 `/ci/app`。本模块对外只有这一个 Controller、8 个 `@PostMapping`。
- 八条端点的接口编号只在方法级注释里（同文件）：
  - `:38~41` 「IF8A-09 获取购买最多张数。」→ `requestBuySinlgeTicketMaxNum`（URL 与方法名同样拼作 `Sinlge`）
  - `:47~50` 「IF8A-10 计算票价。」→ `requestTicketPriceByStation`
  - `:57~60` 「IF8A-11 请求支付。」→ `requestPaymentInfo`
  - `:66~69` 「IF8A-20 请求下单。」→ `requestOrder`
  - `:75~78` 「IF2A-02 查询取票订单状态。」→ `queryTicketCollectOrder`
  - `:84~87` 「IF2A-03 取票订单通知，接收 TVM 设备通知。」→ `ticketCollectNotify`（**注释明确来源方是 TVM 设备**，不是 APP）
  - `:93~96` 「IF2A-05 取消取票订单。」→ `cancelTicketCollectOrder`
  - `:102~105` 「支付中心回调取票订单支付结果。」→ `ticketCollectPayNotify`（**注释没给编号**，这条不属于 IF8A / IF2A 任何一个）
- `TicketCollectController.ticketCollectPayNotify` 的入向 DTO 自带反混淆判据（`collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/model/request/TicketCollectPayNotifyReqDTO.java:4~6`）：「支付中心回调取票订单支付结果请求报文。**注意：这里不是 IF8A-11 请求支付报文。**」
- `TicketCollectServiceImpl.requestPaymentInfo` 的出向报文里带溯源常量（`service/impl/TicketCollectServiceImpl.java:143`）：`payRequest.put("remark", "IF8A-11 requestPaymentInfo")`。这是代码常量不是注释，但它是「支付服务侧日志里怎么认出这笔来自取票」的唯一线索，一并记下。
- `PaySignUtils.buildSignData` 的签名串构造规则写在注释里（`util/PaySignUtils.java:78~81`）：「Build signature data. Sort by parameter name alphabetically, concatenate as key=value&key=value.」算法常量 `SIGN_ALGORITHM = "SHA256WithRSA"`（`:33`）。改这里属 AGENTS.md §5.2「安全红线」，**MUST** 提示人工复核。
- `TicketCollectResultNotifyReqDTO`（`model/request/TicketCollectResultNotifyReqDTO.java:4`）注释写的是「**IF2A-04 取票结果通知（ITP通知APP）**」—— 注释本身声明了这是**出向**通知契约，与 Controller 那 8 条入向端点不同类；该 DTO 及其响应 DTO 在本模块存在，但 Controller 里没有对应端点。

### 订单与票状态

状态取值在本模块**只有注释这一处成文**（服务实现里是 `private static final int` 常量、entity 里是裸 `Integer`，**没有枚举**）。四组取值原文如下，改动任何一个 **MUST** 全局 grep 所有比较点。

- **订单状态 `ORDER_STATUS`**（`entity/TicketCollectInfo.java:50~53`）：「0-未支付，100-支付成功，1-99-支付失败或异常（其他支付状态定义）。」`entity/TicketCollectLogs.java:50~53` 是同一句但**去掉了括号里那半句**。
- **取票状态 `COLLECT_STATUS` 两张表的注释不一致，这是本次抽取最值得记的一条**：
  - `entity/TicketCollectInfo.java:80~83`：「0-未取票（待支付或已支付待处理），**1-已计次**，100-取票成功，1-99-取票失败，**110-重复取票成功**。」
  - `entity/TicketCollectLogs.java:65~68`：「0-未取票（待支付或已支付待处理），100-取票成功，1-99-取票失败，110-重复取票成功。」—— **少了「1-已计次」**。
  - `model/response/QueryTicketCollectOrderRespDTO.java:57~59`（对外响应契约）：「0-未取票，100-取票成功，1-99-取票失败，110-重复取票成功。」同样没有「1-已计次」。
  - 因此「**110-重复取票成功**」是重复取票的约定取值、「**1-已计次**」只在 `Ticket_Collect_Info` 一侧被记载；引用取票状态语义 **MUST** 指明是哪张表 / 哪个 DTO 的注释，**NEVER** 把三处当成同一份清单。
- **支付状态 `PAY_RESULT`**（`entity/TicketCollectInfo.java:100~103` 与 `model/response/QueryTicketCollectOrderRespDTO.java:77~79`，两处一致）：「0-未支付，1-支付中，100-SUCCESS，1-99-FAIL。」
- **退款状态 `REFUND_RESULT`**（`entity/TicketCollectInfo.java:125~128`）：「0-未退款，1-退款中，100-SUCCESS，1-99-FAIL。」注意 `Ticket_Collect_Info` 有 `REFUND_TMS` / `REFUND_AMT` / `REFUND_RESULT` / `REFUND_REASON` 四个退款列（`:115~133`）与之配套，而本模块 Controller **没有任何退款端点**。
- **渠道类型 `CHANNEL_TYPE`**（`entity/TicketCollectInfo.java:95~98`）：「1-APP端（tradeType03），2-ETC端（**已弃用**，tradeType06）。」`model/request/CreateTicketCollectOrderReqDTO.java:46` 的对外契约注释只写「1-APP，2-ETC」、**不带弃用说明**。`tradeType03` / `tradeType06` 这组映射只在这一行注释里出现过。
- **单程票类型 `SINGLE_TICKET_TYPE`**（`entity/TicketCollectInfo.java:40~43`、`entity/TicketCollectLogs.java:40~43`）：「0-非本站进本站出。」注释只给了 `0` 一个取值。
- **取票失败错误码**：「2101-取票二维码超时等」（`entity/TicketCollectLogs.java:95~98` 的 `ERROR_CODE` 列注释、`model/request/TicketCollectResultNotifyReqDTO.java:32~34`）。与实现里的 `ERROR_CODE_QRCODE_EXPIRED = "2101"` 及 `QRCODE_EXPIRE_SECONDS = 300`（`service/impl/TicketCollectServiceImpl.java:61~62`）对应。
- **取票状态入向取值**（`model/request/TicketCollectNotifyReqDTO.java:19~21`、`model/request/TicketCollectResultNotifyReqDTO.java:17~19`）：「100-取票成功，1-99-取票失败。」这是 TVM 上报与 ITP 通知 APP 共用的两段划分。
- **明细表的时间格式写在注释里**（`entity/TicketCollectLogDetail.java:24~27` 与 `:34~37`）：`TAKE_TICKE_DATE` / `TRANS_DATE` 均标「格式：YYYYMMDDHHMMSS」，字段类型却是 `LocalDateTime`；实现侧确实按 `yyyyMMddHHmmss` 解析（`service/impl/TicketCollectServiceImpl.java:648`）。列名 `takeTickeDate` 的拼写（缺一个 `t`）与注释一致，**NEVER 顺手改名**。
- **金额单位**：`TICKET_PRICE` 注释统一标「单位：分」（`entity/TicketCollectInfo.java:30~33`、`entity/TicketCollectLogs.java:30~33`、`model/request/CreateTicketCollectOrderReqDTO.java:29`、`model/request/TicketCollectPayNotifyReqDTO.java:33~34` / `:38~39` / `:43~44` 的 `totalAmount` / `cashAmount` / `couponAmount`）。`TicketCollectLogDetail.transAmount` 是 `String`、注释只写「交易金额」**没有单位**（`entity/TicketCollectLogDetail.java:38~42`）。

### 与相邻模块的边界

- **本模块与 collect-pay 的分工只在 `CollectPayClient` 的类注释与四条路径常量里成文**（`client/CollectPayClient.java:13~15`「取票服务调用支付服务的客户端。」）。四条出向路径都硬编码在方法体内、**不是 `service.*.url` 形态**：
  - `:28` `/collect-pay-server/ci/app/requestPay`
  - `:48` `/collect-pay-server/ci/app/payQuery`
  - `:60` `/collect-pay-server/ci/app/requestRefund`
  - `:72` `/collect-pay-server/ci/app/ticketCollectPayNotify`
  走的是 `rpc` 的 `URLDynamicRouter.postProxy(path, request, null)`，**目标由路径首段 `/collect-pay-server/` 经动态路由解析**，而 `application.properties:17` 的 `service.route.mapping.default` 是**空值**。
  连带两条：`payQuery`（`:46~56`）与 `requestRefund`（`:58~68`）**在本模块零调用方**（`TicketCollectServiceImpl` 只用到 `requestPayForJson` 与 `notifyPayResult`），但退款相关列在 `Ticket_Collect_Info` 里齐全 —— 判断「取票单退款走谁」**MUST** 现查 collect-pay / face-pay 侧，**NEVER** 据本模块有 `requestRefund` 方法就认为链路在这里。
  `CollectPayClient` 四个方法**全部 `catch (Exception) { return null; }`**（`:32~35`、`:52~55`、`:65~67`、`:79~82`），符合 AGENTS.md §5.2「返回 boolean / null 的 RPC 包装方法」那条：调用点 **MUST** 显式判空，**NEVER** 假定「没抛异常就是成功」。`TicketCollectServiceImpl.requestPaymentInfo:149~153` 确实把 `null` 映射成 `SERVICE_PROVIDER_UNAVAILABLE`。
- **支付结果回调的方向是双向的**，边界容易看反：入向是支付中心 → 本模块 `ticketCollectPayNotify`（`TicketCollectPayNotifyReqDTO` 那条「注意：这里不是 IF8A-11」），出向是本模块 → collect-pay 的同名端点 `notifyPayResultToPayServer`（`service/impl/TicketCollectServiceImpl.java:479~492`），**两个方向复用同一个 DTO 类**。改这个 DTO 的字段会同时动两个方向的契约。
- 错误码是**本模块自己的一套**（`constant/CollectTicketErrorCodeEnum.java:3~5`「取票服务错误码定义。」）：`0000` 成功 / `9999` 失败 / `9001` 系统内部错误 / `8001` 无效参数 / `8002` 订单不存在 / `8003` 订单状态异常 / `8004` 不允许取票 / `8005` 已取票完成 / `8006` 二维码已过期 / `8007` 设备不存在 / `8008` 服务提供商不可用 / `8009` 签名验证失败。注意 **`8003` 在本模块是「订单状态异常」**，与卡池那边的 `8003 无卡资源` 不是同一套编码，跨模块比对 retCode **MUST** 带上模块名。
- **两张主表是 1:1 关系，写在两个 entity 的类注释里**（`entity/TicketCollectInfo.java:5~8`「Ticket_Collect_Info 铁运维保取票订单信息表实体。**订单号与 Ticket_Collect_Logs 一一对应**。」；`entity/TicketCollectLogs.java:5~8` 是对称的一句）。`entity/TicketCollectLogDetail.java:5~7` 只说明「Ticket_Collect_Log_Detail 取票明细记录表实体。」，1:N 关系没有写进注释。

### 配置与部署

- `application.properties`（`collect-ticket-server/src/main/resources/application.properties`，共 20 行）**一条注释都没有**，因此本小节没有可归档的注释知识；下面三条是抽取过程中为了核对注释而读到的**配置事实**，明确标注非注释来源：
  - `:1` `server.port=9098`、`:2` `spring.application.name=collect-ticket`。**9098 与 account-server 撞端口**（AGENTS.md §2.2.1 端口冲突清单里的第一组），同机部署 **MUST** 先确认。
  - `:17` `service.route.mapping.default=` 为空、且整个文件**没有任何 `service.*.url`**，而 `CollectPayClient` 依赖 `URLDynamicRouter` 出网 —— 属 AGENTS.md §8「`service.*.url` 键缺失」那一类形态（仓库里连一个写错的键都没有），**判断线上真实目标 MUST 查 Deployment env**。
  - `:18~19` 两个业务开关 `collect.ticket.buy-single-ticket-max-num=10` / `collect.ticket.default-ticket-price=0`，分别被 IF8A-09 与 IF8A-10 直接读取（`service/impl/TicketCollectServiceImpl.java:67~71`）；`:14~15` 的 `server.tomcat.basedir` / `logging.file.path` 现值均为 `./logs`。另外 `collect.ticket.pay.callback-url`（`TicketCollectServiceImpl.java:64`）与 `pay.center.paycenter-public-key` / `pay.center.merchant-private-key`（`util/PaySignUtils.java:27~31`）三个键**在 properties 里根本不存在**，全部依赖默认空值或 env 注入。
- **`mvn package` 就会推镜像**：`collect-ticket-server/pom.xml:97~108` 的 jkube execution id 是 `build-image-after-package`、`<phase>package</phase>`，`<goal>build</goal>` + `<goal>push</goal>` **未注释**（`:106` 只有 `deploy` 被 XML 注释掉 —— 这是本模块**范围外但唯一带判据的注释**）。镜像名取 `<image.prefix>collect-ticket-server</image.prefix>`（`:31`）、tag 取 `<version>`（`:8`，当时 **2.0.6**）。只想拿 jar **MUST** 加 `-Djkube.skip=true`。

### 持久层与 mapper

- 三个 mapper XML（`src/main/resources/mapper/TicketCollectInfoMapper.xml`、`TicketCollectLogsMapper.xml`、`TicketCollectLogDetailMapper.xml`）**一条 `<!-- -->` 注释都没有**，因此没有可归档的注释知识。顺带记两条**核查结论**（非注释来源，供后续改动做基线）：三个文件里**没有** `where 1 = 1`、也**没有任何 `<if>` / `<where>` 动态标签**（全是固定谓词的 `select` / `insert` / 全列 `update`），SQL 正文里**没有 `--` 或 `/* */`**，因此 AGENTS.md §5.1 那两条 Druid WallFilter 陷阱当前**都没踩**；新增动态查询时才会撞上，**MUST 用 `<where>` 标签**。
- mapper 接口的 Javadoc 全是复述方法名的一行（如 `mapper/TicketCollectInfoMapper.java:9~11`「根据订单号查询取票订单信息。」），已丢弃。唯一带信息量的是 `:14~18` `selectByDeviceIdAndQrcodeDate`「根据设备ID和二维码生成时间查询取票订单信息。」—— 该方法**在本模块零调用方**，但它揭示了「设备号 + 二维码生成时间」曾被当作一组查询键（对应 `TicketCollectServiceImpl` 用 `orderNo` / `userId` 查的现状，见 `findCollectInfo:593~604`）。同类零调用方法还有 `mapper/TicketCollectLogDetailMapper.java:19~22` 的 `insertBatch` 与 `mapper/TicketCollectLogsMapper.java:14~17` 的 `selectByUserId`。
- `updateByOrderNo` 是**全列覆盖式 UPDATE**（`mapper/TicketCollectInfoMapper.xml:94~120`，23 个 `set` 列 + `where ORDER_NO`），**没有** `<if>` 收窄、也**没有**状态 CAS 谓词。这解释了本文件前面「幂等（本仓最薄的一处）」那节的成因：并发两笔回调各自 `selectByOrderNo` → 改内存对象 → 全列写回，后写者会把前写者的列一起覆盖。新增写入路径 **MUST** 按那节的三条执行。

### 墓碑注释清单（建议转为断言测试）

**严格意义的墓碑注释（「已迁走 / NEVER 加回 / NEVER 按旧记载找」）本模块 0 条。** 下面 3 条是形态最接近、且可以断言化的「防误用」注释，按同一格式列出，供后续阶段判断是否值得固化为测试：

| 序号 | 文件:行号 | 注释禁止的事 | 能否断言化 |
|---|---|---|---|
| 1 | `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/model/request/TicketCollectPayNotifyReqDTO.java:5` | 「注意：这里不是 IF8A-11 请求支付报文。」—— 禁止把支付中心回调 DTO 当成 IF8A-11 的请求体复用 | **能**（弱形式）。可断言 `TicketCollectPayNotifyReqDTO` 与 `RequestPaymentInfoReqDTO` 的字段名集合无交集、且两者不共享父类；无法断言「人不会混用」 |
| 2 | `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/entity/TicketCollectInfo.java:96` | 「2-ETC端（**已弃用**，tradeType06）」—— 禁止新链路继续产出 `channelType='2'` | **勉强能**。可断言「本模块 `src/main` 内不存在写入 `channelType="2"` 的常量」；但入向 `channelType` 来自 APP 报文、运行期挡不住，真要挡需在 `validateCreateOrder` 加白名单（属改行为，不在本阶段） |
| 3 | `collect-ticket-server/pom.xml:106`（**范围外，仅记录**） | `<!-- <goal>deploy</goal> -->` —— 被刻意注释掉的 jkube deploy goal，禁止误以为 `mvn package` 会自动 `k8s:deploy` | **能**。可断言 pom 里 `build` / `push` 未注释而 `deploy` 仍在注释内（照 AGENTS.md §7「MUST 看 `<goals>` 里有没有未注释的 goal」那条判据） |

### 本次抽取没把握归类的条目

以下 3 条注释**与同文件代码不一致**，抽取时按原文归档，但**引用前 MUST 先核对代码**，不要直接采信注释：

1. `model/request/CreateTicketCollectOrderReqDTO.java:30`「票价…接口文档 IF8A-20 中定义了该字段，**当前服务实现尚未使用**。」—— 但 `service/impl/TicketCollectServiceImpl.java:519~521` 把 `ticketPrice` 列为必填校验项、`:613` 与 `:634` 都写入了库。归类介于「决策理由」与「过期注释」之间。
2. `model/request/CreateTicketCollectOrderReqDTO.java:36`「单程票类型…**当前服务实现固定按 0 处理**。」—— 实现是「传了就用、没传才补 0」（`service/impl/TicketCollectServiceImpl.java:615` 与 `:636` 的 `request.getSingleTicketType() != null ? ... : 0`），不是「固定 0」。
3. `model/request/CreateTicketCollectOrderReqDTO.java:5`「**当前 DTO 尚未完全覆盖接口文档字段**。」—— 是明确的决策/现状声明，但没写「缺哪些字段」，无法据此判断缺口范围；补字段前 **MUST** 回到 `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx` 的 IF8A-20 逐条比对。

另记一条**本文件既有内容与现状的差异**（只记录、不修改上文）：本文件「模块定位」小节写 `application.properties` 的 `server.tomcat.basedir` / `logging.file.path` 是 `/Users/zhoucong/logs`，而 2026-09-16 实读该文件 `:14~15` 两行均为 `./logs`。核对该 P1 项 **MUST** 现查文件。

## 附：collect-ticket-server 注释知识迁移（2026-09-16，阶段二·完整）

> **本节是「注释 → 文档」的收口版**，并**复核阶段一「零陷阱零墓碑」那个判断**（结论见 §零）。此后 `collect-ticket-server/src/main` 只保留标准 Javadoc 与 1 条一行式护栏（mapper XML 的「SQL 正文禁写注释」），**其余知识的唯一权威副本就是本节**。
>
> **行号会漂**。每条都带 `路径:行号` + 可 grep 的原文短语；引用前 **MUST** 先 grep，**NEVER** 按行号跳。
>
> 抽取范围（2026-09-16 实测）：`collect-ticket-server/src/main` 共 34 个文件 —— Java 30 个（3596 行）、mapper XML 3 个（275 行）、`application.properties` 1 个（20 行）。注释行合计 **581 行，全部在 Java 侧**；mapper XML 与 properties **一条注释都没有**；**没有 `sql/` 目录、没有任何 DDL**。

### 零、对阶段一「零陷阱零墓碑」的复核结论

**结论：那个判断在「注释里」当时基本成立，但现在已不成立；而且它掩盖了一个更重要的事实 —— 本模块真正的陷阱不在注释里，在代码里。** 三条依据：

1. **注释侧已出现 3 条墓碑型注释**（阶段一之后补写的，见 §五）：`CreateTicketCollectOrderReqDTO` 的类注释与 `ticketPrice` / `singleTicketType` 两个字段注释，现在各自带「**本注释此前写…是错的**」+「**NEVER 回退**」。阶段一「没把握归类」那 3 条正是它们的旧文本，**现已在代码里被逐条纠正、并留下墓碑**。因此「墓碑 0 条」已过期。
2. **注释侧还有 1 条现状告警**（`singleTicketType` 的注释）：甲方 IF8A-20 只定义取值 `0`，而实现**没有取值白名单校验**，上游送 `1` 会被原样落库 —— 这是对 AGENTS.md §5.2「状态校验用白名单」的**已知偏离点**，属陷阱型知识。
3. **581 行注释里确实几乎没有陷阱，原因可以精确定位**：注释的分布是「**字段级 Javadoc 占绝大多数、业务实现类零注释**」—— `service/impl/TicketCollectServiceImpl.java` **699 行、0 条注释**（`python` 剥注释实测），`service/TicketCollectService.java`、`resources/mapper/*.xml`、`application.properties` 同样 0 条。**陷阱本来就发生在实现里，而实现里没有人写注释**，所以「注释里没陷阱」不等于「模块没陷阱」。本节 §四把从代码读出的 8 条**风险现状**单列出来，并明确标注**来源是代码而不是注释** —— 这是本轮复核的主要增量。

### 一、取票链路（APP 扫码取票 · 单程票）

- 唯一 Controller `TicketCollectController`（类前缀 `/ci/app`，`collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/controller/ci/app/TicketCollectController.java:31`，grep `@RequestMapping("/ci/app")`），8 个 `@PostMapping`；编号只写在方法级注释里（阶段一已逐条列出，此处不重复）。
- **主链路（读实现得出，`service/impl/TicketCollectServiceImpl.java`）**：
  1. **IF8A-09 `requestBuySinlgeTicketMaxNum`**（`:85`）：直接回 `collect.ticket.buy-single-ticket-max-num`（默认 10），**不查任何库、不认用户**。
  2. **IF8A-10 `requestTicketPriceByStation`**（`:94` → `resolveTicketPrice:682~687`）：**同站进出返 0，否则返回配置里的 `collect.ticket.default-ticket-price`（默认 0）** —— **既不查票价矩阵、也不调 para-server**。这是本域最大的现状缺口，见 §四-1。
  3. **IF8A-20 `requestOrder`**（`:177`）：校验 → `generateOrderNo()` = `"ORD" + System.currentTimeMillis() + UUID 前 8 位`（`:674~676`）、`generateRandomFact()` = UUID 去横线前 16 位（`:678~680`）→ 同一事务内 `TicketCollectInfo` + `TicketCollectLogs` **双表 insert**（`buildCollectInfo:606` / `buildCollectLogs:627`），落 `ORDER_STATUS=0` / `COLLECT_STATUS=0` / `PAY_RESULT=0`。注意 `TicketCollectLogs.deviceId` 在下单时被写成 **`request.getChannelCode()`**（`:641`），取票通知时才被真正的设备号覆盖（`:322`）。
  4. **IF8A-11 `requestPaymentInfo`**（`:112`）：查单 → **已支付（`ORDER_STATUS=100`）直接回 `8003 订单状态异常`** → 组 `payRequest`（`orderNo` / `scene=channelType` / `paymentVendor=payChannelCode` / `amount=calculateOrderAmount` / `industryType="1"` / `subject="取票订单"` / `body="单程票取票支付"` / `thirdUserId=userId` / `remark="IF8A-11 requestPaymentInfo"`，`collect.ticket.pay.callback-url` 非空时才带 `notifyUrl`）→ 调 collect-pay → 回写 `PAY_CHANNEL_CODE` / `CHANNEL_TYPE` / `PAY_RESULT=1`（支付中）→ 把渠道返回的 `data` / `signType` / `sign` 原样透传给 APP。金额 = `ticketPrice × singelTicketNum`，张数 ≤ 0 时按 1 算（`calculateOrderAmount:689~693`）。
  5. **支付回调 `ticketCollectPayNotify`**（`:397`）：验签（`verifyPayNotifySign:454`，参数集固定为 `orderNo` / `merchantOrderNo` / `channelOrderNo` / `status` / `payTime` + 三个金额 + 可选 `payUserId` / `paymentVendor`）→ `status` 大小写不敏感等于 `SUCCESS` 时置 `ORDER_STATUS=100` / `PAY_RESULT=100` 并写 `PAY_AMOUNT` / `PAY_DATE` / `PAY_CHANNEL_CODE` / `TRADE_NO`；**否则一律置回 `0` / `0`**（见 §四-4）→ 同步 `TicketCollectLogs.ORDER_STATUS` → **再出网通知 collect-pay 一次**（`notifyPayResultToPayServer:479`）。
  6. **IF2A-02 `queryTicketCollectOrder`**（`:219`）：`orderNo` 优先，否则按 `userId` 取列表**第一条**（`findCollectInfo:593~604`，**没有排序保证**）；二维码超过 `QRCODE_EXPIRE_SECONDS=300` 且仍未支付时，**只在响应里给 `errorCode=2101` + 「二维码已超时，请重新下单」，并把内存对象的 `orderStatus` 改成 99 —— 但不落库**（`:238~245`，见 §四-3）。
  7. **IF2A-03 `ticketCollectNotify`**（`:288`，**来源方是 TVM 设备**）：`COLLECT_STATUS=100` 已取票即回 `8005 已取票完成`（终态短路）→ 写 `COLLECT_TMS` / `COLLECT_STATUS`（**直接取上游 `request.getCollectStatus()`，无白名单**）/ `DEVICE_ID` → 同步 `TicketCollectLogs`（另补 `ACTUAL_TAKE_TICKET_NUM` / `FAULT_SLIP_SEQ` / `ERROR_CODE` / `ERROR_MESSAGE`）→ **仅当 `collectStatus==100` 且明细非空**才逐条 insert `Ticket_Collect_Log_Detail`（`saveTicketDetails:647~672`，时间串按 `yyyyMMddHHmmss` 解析，解析失败只 `log.warn` 并留 null）。
  8. **IF2A-05 `cancelTicketCollectOrder`**（`:349`）：已支付回 `8003`、已取票回 `8004 不允许取票`，否则把 `ORDER_STATUS` **置回 `0`（未支付）** —— 本模块**没有独立的「已取消」状态**（见 §四-5）。
- **出向通知 DTO 存在但无端点**：`TicketCollectResultNotifyReqDTO` 注释写「**IF2A-04 取票结果通知（ITP 通知 APP）**」，它与响应 DTO 都在，但 Controller 里**没有对应端点**、`TicketCollectServiceImpl` 里也没有调用 —— **IF2A-04 当前未接线**，NEVER 因为 DTO 在就认为链路通。

### 二、`CreateTicketCollectOrderReqDTO` 三处已修正的字段口径（本模块唯一的墓碑型注释）

阶段一把这三条列进「没把握归类」，因为当时注释与代码不一致；**2026-09-16 已按代码逐条纠正，并在注释里留下墓碑**。三条的现行口径如下，**NEVER 回退成旧文本**：

| # | 位置（`model/request/CreateTicketCollectOrderReqDTO.java`） | 旧文本（**已作废**） | 现行事实 |
|---|---|---|---|
| 1 | 类注释 `:3~14`（grep `已全部覆盖，缺失数为 0`） | 「当前 DTO 尚未完全覆盖接口文档字段」 | 甲方 IF8A-20 请求侧 **7 个字段全覆盖、缺失数 0**（`userId` / `entryStationCode` / `exitStationCode` / `ticketPrice` / `singelTicketNum` / `singleTicketType` / `channelCode`），另有本项目**自加**的 `channelType`（规格里没有，落库在 `buildCollectInfo`）。旧文本**方向正好相反**，会诱发「照文档补字段」的无意义返工 |
| 2 | `ticketPrice` `:36~46`（grep `此前写「当前服务实现尚未使用」是错的`） | 「当前服务实现尚未使用」 | **三处在用**：`validateCreateOrder` 必填 + 非负校验（为空或 <0 返「ticketPrice不能为空」，`TicketCollectServiceImpl:519~521`）；`buildCollectInfo:613` 与 `buildCollectLogs:634` 双表写库；`calculateOrderAmount:689` 乘张数算请求支付金额。**NEVER 因为旧注释说没用就删字段** —— 删掉会同时打挂下单校验与请求支付的金额计算 |
| 3 | `singleTicketType` `:48~58`（grep `此前写「固定按 0 处理」是错的`） | 「当前服务实现固定按 0 处理」 | 实际是「**传了就用、没传补 0**」：`buildCollectInfo:615` 与 `buildCollectLogs:636` 都是 `request.getSingleTicketType() != null ? ... : 0`。**同处还留了一条现状告警**：甲方只定义取值 `0`，而实现**没有取值白名单校验**，上游送 `1` 会被原样落库 —— 对 AGENTS.md §5.2「状态校验用白名单」的偏离点，取值范围待与甲方澄清后再补校验 |

### 三、与相邻模块的边界

- **与 collect-pay**：出向四条路径**硬编码在 `client/CollectPayClient.java` 方法体内**、不是 `service.*.url` 形态 —— `:28` `/collect-pay-server/ci/app/requestPay`、`:48` `.../payQuery`、`:60` `.../requestRefund`、`:72` `.../ticketCollectPayNotify`；走 `rpc` 的 `URLDynamicRouter.postProxy(path, request, null)`，**目标由路径首段 `/collect-pay-server/` 经动态路由解析**，而 `application.properties:17` 的 `service.route.mapping.default` **是空值**、整个文件**没有任何 `service.*.url`**（属 AGENTS.md §8「键缺失」那类形态，**判断线上真实目标 MUST 查 Deployment env**）。
  - **与 face-pay 的关系是本轮新增的待裁决项**：TVM / BOM / APP 当面付主实现已于 2026-09-16 17:31 迁到 `face-pay-server`（ADR-D117），而本模块的支付出向仍打 `collect-pay` 的 `/ci/app/requestPay`。collect-pay 仍在跑、这条链路当前**不会断**，但「取票支付到底该落哪个应用」**MUST 现查 + 向用户确认**，见 §六-1。
  - `payQuery` / `requestRefund` **在本模块零调用方**，而 `Ticket_Collect_Info` 的四个退款列（`REFUND_TMS` / `REFUND_AMT` / `REFUND_RESULT` / `REFUND_REASON`）齐全、Controller 又**没有任何退款端点** —— 判断「取票单退款走谁」**MUST 现查 collect-pay / face-pay 侧，NEVER 据本模块有 `requestRefund` 方法就认为链路在这里**。
  - `CollectPayClient` 四个方法**全部 `catch (Exception) { return null; }`**（`:32` / `:52` / `:65` / `:79`），属 AGENTS.md §5.2「返回 boolean / null 的 RPC 包装方法」同一族：调用点 **MUST 显式判空**。现状：`requestPaymentInfo:149~153` 把 `null` 映射成 `8008 服务提供商不可用`（**接住了**）；`notifyPayResultToPayServer:488` 的返回值**被完全丢弃**（**没接住**，见 §四-6）。**新写 RPC 包装 MUST 返回 `RpcOutcome`、NEVER 再返回 null**。
- **支付结果回调是双向的、且两个方向复用同一个 DTO**：入向是支付中心 → 本模块 `ticketCollectPayNotify`（DTO 类注释 `model/request/TicketCollectPayNotifyReqDTO.java:5` 自带反混淆判据「**注意：这里不是 IF8A-11 请求支付报文**」），出向是本模块 → collect-pay 的**同名端点**（`notifyPayResultToPayServer:479~492`）。**改这个 DTO 的字段会同时动两个方向的契约。**
- **验签是本模块自建的 `PaySignUtils`**（`util/PaySignUtils.java`）：`SIGN_ALGORITHM = "SHA256WithRSA"`（`:33`），签名串规则写在注释里（`:78~81`「Sort by parameter name alphabetically, concatenate as key=value&key=value」，实现是 `TreeMap` 升序拼接、`:82~92`）；密钥取 `pay.center.paycenter-public-key` / `pay.center.merchant-private-key`（`:27~31`，**两个键在 properties 里都不存在**、靠 env 注入）。改这里属 AGENTS.md §5.2「安全红线」，**MUST 提示人工复核**。它也是对「NEVER 新建工具类」的一处既存破例，**NEVER 再新增同类**。
- **错误码是本模块自己的一套**（`constant/CollectTicketErrorCodeEnum.java`）：`0000` 成功 / `9999` 失败 / `9001` 系统内部错误 / `8001` 无效参数 / `8002` 订单不存在 / `8003` 订单状态异常 / `8004` 不允许取票 / `8005` 已取票完成 / `8006` 二维码已过期 / `8007` 设备不存在 / `8008` 服务提供商不可用 / `8009` 签名验证失败。**`8003` 在本模块是「订单状态异常」，与卡池的 `8003 无卡资源` 不是一套编码 —— 跨模块比对 retCode MUST 带模块名。** 另注意 **`8006 二维码已过期` 与 `8007 设备不存在` 在实现里零使用**（超时那支走的是响应里的 `errorCode=2101`，不是 `8006`）。
- **两张主表 1:1**（`entity/TicketCollectInfo.java:5~8` 与 `entity/TicketCollectLogs.java:5~8` 对称写着「订单号与 … 一一对应」）；`Ticket_Collect_Log_Detail` 是 1:N，但**关系没有写进注释**（`entity/TicketCollectLogDetail.java:5~7` 只说明表名）。

### 四、代码里的风险现状（**来源是代码、不是注释** —— 本轮复核的主要增量）

以下 8 条都读自 `service/impl/TicketCollectServiceImpl.java`（**该文件 699 行、0 条注释**）与 `util/PaySignUtils.java`，**注释里一个字都没有**。它们是本模块真正的陷阱，**改这个模块前 MUST 先读本小节**。

1. **IF8A-10 计算票价是占位实现**：`resolveTicketPrice:682~687` 同站进出返 0、否则返回 `collect.ticket.default-ticket-price`（**默认 0**），**不查票价矩阵、不调 para-server**。连带后果：`requestPaymentInfo` 的 `amount` 也可能是 0。改造前 **MUST** 与用户确认票价来源（para-server 的票价矩阵 / 上游 APP 传入 / 还是保持配置兜底）。
2. **`@Transactional` 方法内发起 RPC，两处**：`requestPaymentInfo`（`:111` 注解 + `:148` 调 collect-pay）与 `ticketCollectPayNotify`（`:396` 注解 + `:440` 出网通知）。这直接违反 AGENTS.md §5.2「`@Transactional` 方法内 NEVER 发起任何 RPC / 网络调用」与「NEVER 在事务内提交异步通知」两条 —— 后者在 pay-sign 域已发生过 8 分钟锁等待 + 证据全丢的生产事故。**修法**：把出网移出事务（`afterCommit` 或摘事务注解），**NEVER 只是把超时调小**。
3. **二维码超时的 `orderStatus=99` 不落库**：`:241` 只改内存对象、后续**没有任何 `updateByOrderNo`**，因此库里那行仍是 `0`（未支付）。表现为「查一次说超时、再查一次还能继续支付」，且 `99` 这个值**只出现在响应里**。本文件上文「状态取值」小节写的「取消置 `99`」**也与代码不符** —— 代码里取消置的是 `0`（见下条），`99` 只在这一处内存赋值。**NEVER 据文档或响应值以为库里有 99。**
4. **支付失败分支把 `ORDER_STATUS` 写回 `0`（未支付）**（`:429~430`）：而实体注释声明 `1~99` 是「支付失败或异常」—— **代码从不写 1~99**。于是「从未支付」与「支付失败」在库里无法区分，对账与客诉都取不到失败证据。
5. **取消订单没有独立终态**：`cancelTicketCollectOrder:378` 把 `ORDER_STATUS` 置回 `0`，与「未支付」同值；`Ticket_Collect_Info` 也没有取消时间 / 取消原因列（`CancelTicketCollectOrderReqDTO.cancelReason` **收到即丢弃**）。因此「用户主动取消」在库里**不可见**。
6. **`notifyPayResultToPayServer` 的失败被彻底吞掉**（`:479~492`）：整段 `try/catch` 只 `log.error`，且 `collectPayClient.notifyPayResult` 本身也 `catch → return null`。于是「collect-pay 没收到支付结果」既不重试、也不落任何待补偿状态 —— 本模块**没有任何 `@Scheduled`、没有 outbox 列**。属 AGENTS.md §5.2「静默不一致」那一族。
7. **`PaySignUtils.verify` 在公钥未配置时直接返回 `true`**（`:65~68`，`log.warn("Pay center public key not configured, skip verification")`）。而 `pay.center.paycenter-public-key` 在 `application.properties` 里**根本不存在**、只靠 env 注入 —— **env 一旦漏注，支付回调验签等于全放过**，任何网络可达方都能伪造「支付成功」把订单改成已支付。这是本模块**最高优先级的安全现状**，改造 **MUST 提示人工复核**（fail-closed 还是保留 fail-open 属安全裁决，见 §六-2）。
8. **`@Transactional(rollbackFor = Exception.class)` + 方法内 `catch (Exception)` ⇒ 回滚永不触发**：五个写方法（`requestPaymentInfo` / `createTicketCollectOrder` / `ticketCollectNotify` / `cancelTicketCollectOrder` / `ticketCollectPayNotify`）都把全部异常在方法内接住并返 `9001`，异常**不会传播出代理**，`rollbackFor` 形同虚设。后果：`ticketCollectNotify` 里「主表已更新、`TicketCollectLogs` 或明细 insert 抛异常」时**主表那次更新会被提交**。
   - 连带两条更小的：**没有唯一索引依据**（本模块**无 `sql/` 目录、无 DDL**，`ORDER_NO` 是否唯一约束在仓库里**无法确认**，属 AGENTS.md 那条「代码有 mapper、库里表结构未知」的形态）；**`updateByOrderNo` 是全列覆盖式 UPDATE**（`resources/mapper/TicketCollectInfoMapper.xml:94` 起 23 个 `set` 列 + `where ORDER_NO`，**没有 `<if>` 收窄、没有状态 CAS 谓词**），并发两笔回调各自「查 → 改内存 → 全列写回」时**后写者会把前写者的列一起覆盖**。
   - 另记：`queryTicketCollectOrder` 按 `userId` 兜底取列表**第一条且无排序**（`findCollectInfo:598~601`，SQL 侧 `selectByUserId` 也没有 `ORDER BY`），同一用户多单时返回哪一单**不确定**。

### 五、状态取值与端口（注释是唯一成文处）

- 状态取值在本模块**只有注释这一处成文**（实现里是 `private static final int`、entity 里是裸 `Integer`，**没有枚举**）；阶段一已逐条列出四组取值与三处不一致（`COLLECT_STATUS` 的「1-已计次」只在 `Ticket_Collect_Info` 一侧、`110-重复取票成功` 三处都有、`CHANNEL_TYPE` 的「2-ETC 已弃用 / tradeType03 与 tradeType06 映射」只在 entity 一侧），**本轮复核确认那些注释未变、结论继续有效**，此处不重复抄录，改动状态值 **MUST 全局 grep 所有比较点**。
- **本轮补一条**：代码里实际写过的取值只有 `0` / `1` / `100`（外加内存里那个不落库的 `99`）；注释里的 `1~99`、`110`、`1-已计次` **实现从不写入**，`collectStatus` 直接取上游值、**无白名单** —— 「注释里的取值清单」与「代码会写的取值集合」**是两回事**，做对账或前台字典 **MUST 分清**。
- 端口 **9098 与 account-server 冲突**（`application.properties:1`，`spring.application.name=collect-ticket`），同机部署 **MUST 先确认**；这条在 AGENTS.md §2.2.1 端口冲突清单里是第一组。启动类 `CollectTicketServer` **只有 `@SpringBootApplication` + `@EnableConfigurationProperties`**，没有 `@EnableDefaultMybatisAutoConfig`（recon-server 有）—— mapper 靠 `@Mapper` + `mybatis-adaptor` 自动配置装配，**改持久层装配前 MUST 现验**。
- `mvn package` **就会推镜像**（`collect-ticket-server/pom.xml` 的 `build-image-after-package` / `<phase>package</phase>`，`build` + `push` 未注释、`deploy` 在注释里），只想拿 jar **MUST 加 `-Djkube.skip=true`**。

### 六、矛盾与待裁决

1. **取票支付的目标应用**：本模块出向仍打 `collect-pay` 的 `/collect-pay-server/ci/app/requestPay`，而 TVM / BOM / APP 当面付主实现已迁 `face-pay-server`（ADR-D117）。collect-pay 仍在跑、链路当前不断，但**「取票支付是否也要切到 face-pay」未裁决**。**MUST 现查** `fep-app` env 与 `fep-app-vr` 后向用户确认，**NEVER 自行改 `URLDynamicRouter` 的路径首段**（属改服务间路由）。
2. **`PaySignUtils.verify` 的 fail-open**（§四-7）：公钥未配置即放过。待裁决：改成 fail-closed（未配置即拒绝，**推荐**）还是保留现状 + 在部署清单里列为强制 env。属安全红线，**MUST 由人裁决**。
3. **`ORDER_STATUS` / `COLLECT_STATUS` 的失败态**（§四-4、§四-5）：代码从不写 `1~99`，「支付失败」「用户取消」在库里都退化成 `0`。待裁决：补失败态取值 + 取消态（需要 DDL）还是维持现状。**在裁决前 NEVER 拿 `ORDER_STATUS=0` 断言「用户没付过」。**
4. **三张表无仓库 DDL**：`Ticket_Collect_Info` / `Ticket_Collect_Logs` / `Ticket_Collect_Log_Detail` 在 `collect-ticket-server` 下**没有 `sql/` 目录**，列长、非空、`ORDER_NO` 是否唯一索引**仓库内不可知**。待裁决：补 `*-schema.sql` + `*-migration.sql`（照 AGENTS.md §8 那条），还是从 `AFCITPDB` 反查后回填文档。**在补齐前 NEVER 断言「靠唯一索引兜幂等」。**
5. **`singleTicketType` 无白名单**（§二-3）：甲方只定义 `0`，实现放过任意值。待裁决：补白名单校验（属改行为、会拒掉现有上游报文）还是先向甲方澄清取值范围。
6. **IF2A-04 出向通知未接线**（§一末条）：DTO 在、端点与调用都没有。待裁决：是否本期实现。

### 七、墓碑清单

严格意义的墓碑注释本模块**共 3 条，全部集中在 `CreateTicketCollectOrderReqDTO`**（阶段一记「0 条」已过期，NEVER 回退成 0 条）：

| # | 位置 | 断言内容 | 能否断言化 |
|---|---|---|---|
| M1 | `CreateTicketCollectOrderReqDTO.java:12~13` | 「当前 DTO 尚未完全覆盖接口文档字段」**方向正好相反、是错的**（实为全覆盖 + 多一个自加字段），NEVER 回退 | **能（弱）**：断言该 DTO 含那 7 个规格字段 + `channelType` 共 8 个 |
| M2 | 同文件 `:43~44`（`ticketPrice`） | 「当前服务实现尚未使用」**是错的**；NEVER 因为旧注释说没用就删字段 | **能**：纯单测 —— `ticketPrice=null` 时 `createTicketCollectOrder` 返 `8001` + 「ticketPrice不能为空」；`calculateOrderAmount` 随 `ticketPrice` 变化 |
| M3 | 同文件 `:53`（`singleTicketType`） | 「固定按 0 处理」**是错的**（传了就用、没传补 0），NEVER 回退 | **能**：纯单测 —— 传 `2` 时落库值为 `2`、不传时为 `0`（同一条测试同时把 §六-5 那个偏离点固定下来） |

另有 3 条「防误用」注释（非墓碑，阶段一已列表，本轮确认未变）：`TicketCollectPayNotifyReqDTO:5`「注意：这里不是 IF8A-11 请求支付报文」、`TicketCollectInfo:96`「2-ETC 端（已弃用）」、`pom.xml:106` 被注释掉的 `<goal>deploy</goal>`。

### 八、删除后代码里保留的护栏（collect-ticket 侧 1 条）

三个 mapper XML 各保留一条「**SQL 正文禁写注释（Druid WallFilter），说明写在 XML 注释里；本注释内 NEVER 出现两个连续减号**」的一行式提醒（即全局第 ⑦ 条护栏）。**本模块此前 mapper XML 与 properties 一条注释都没有，这三行是本轮新增的唯一注释**；Java 侧只保留标准 Javadoc，其余全部迁入本节。

### 九、覆盖率自评

- **按文件**：34 个文件里有注释的 22 个（全在 Java 侧），本节 + 阶段一逐条覆盖 **22/22**；无注释的 12 个（`TicketCollectServiceImpl` / `TicketCollectService` / 3 个 mapper XML / `application.properties` / 6 个纯 Lombok DTO）已按「无注释可迁」说明，其中 `TicketCollectServiceImpl` 的知识改由 §一 + §四 **从代码读出后落档**。
- **按注释行**：581 行中，**判为「有知识、已迁入本节或阶段一」的约 110 行**（3 条墓碑 + 8 条端点编号 + 四组状态取值 + 1:1 关系 + 签名规则 + 错误码 + 反混淆判据）；**判为「标准 Javadoc、随代码保留」的约 400 行**（一句话字段说明 + `@param` / `@return`）；**判为「纯样板、丢弃」的约 70 行**（`/**` `*/` 单独成行）。
- **按四类**：契约与判据（8 条端点编号 / 四组状态取值 / 金额单位 / 时间格式 / 错误码 / 1:1 关系）**全覆盖**；决策理由（本模块注释里**几乎没有** —— 唯一接近的是「2-ETC 已弃用」与签名串规则）；陷阱 **注释侧 1 条**（`singleTicketType` 无白名单）+ **代码侧 8 条**（§四，本轮新增）；墓碑 **3 条**（§七）。
- **未覆盖 / 无法从注释得到的**：① 三张表的真实 DDL（仓库内没有，见 §六-4）；② `collect.ticket.pay.callback-url` 的线上真实值（properties 里没有该键，MUST 查 Deployment env）；③ 甲方 IF2A-02/03/05 与 IF8A-09/10/11/20 的逐字段定义（只在 `.docx` 里）；④ 「取票支付 / 退款到底落哪个应用」（跨模块现状，见 §六-1）。





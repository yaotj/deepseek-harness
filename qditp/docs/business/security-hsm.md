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

## 附：acc-security-server / acc-secure-server 源码注释知识抽取（2026-09-16，阶段一）

> **有并发写入者，引用行号前 MUST 先 grep 现查。** 本节所有 `路径:行号` 都是 2026-09-16 抽取时刻的快照。
>
> 抽取范围：两模块 `src/main/java/**/*.java` 与 `src/main/resources/application.properties` 的全部注释（两模块**都没有** `src/main/resources/mapper/*.xml`）。四类归类中本节只归档①契约与判据、②决策理由、③陷阱；④墓碑注释单列文末「墓碑注释清单（建议转为断言测试）」，不进正文。已丢弃复述方法名/参数名的普通 Javadoc、`@author`/`@date`/`@param`/`@return`/`@version`、空 Javadoc 与分节横线。
>
> **安全口径**：涉及密钥的条目**只写键名、位置与语义，不回显任何值**。注释或配置里原文包含疑似真值的，一律写「值见源码该行，本处不回显」。

### 一、HSM 长连与报文

- **报文帧结构由 `Constant.DataPackage` 的字段注释逐项定义**（acc-security-server，`Constant.DataPackage`，`acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/config/Constant.java:13~47`）：`START`「包体开始标识」、`END`「包体结束标识」、`DATA_TYPE_QUERY`「查询包数据类型」、`DATA_TYPE_EXIST`「含有数据的消息包数据类型」、`DATA_TYPE_NONE`「没有数据消息报数据类型」、`DATA_HEADER_LENGTH`「消息头长度」。三条组包顺序注释是本域唯一的报文骨架说明：`DATA_EXIST_HEADER`「含有数据包包头 开始标识+数据类型(0x03)+序列号」（:37）、`DATA_QUERY_PACKAGE`「查询包结构 开始标识+数据类型(0x01) +序列号+数据长度(两位)+结尾」（:41）、`DATA_NONE_PACKAGE`「没有数据消息包 开始标识+数据类型(0x02)+序列号」（:45）。
- **Netty 拆包参数的语义**（acc-security-server，`Constant.NETTY_LENGTH`，`.../config/Constant.java:52~74`）：`MAX_MESSAGE_LENGTH`「最大长度」、`MIN_MESSAGE_LENGTH`「最小长度」、`LENGTH_FIELD_OFFSET`「长度偏移」、`LENGTH_FIELD_LENGTH`「长度字段所占的字节数」、`LENGTH_ADJUSTMENT`「消息尾，结束标识长度」、`INITIAL_BYTES_TO_STRIP`「忽略字节」。这六个值构成拆包契约。
- **应答值长度按业务分三档**（acc-security-server，`Constant.RESOPNSE_LENGTH`，`.../config/Constant.java:129~143`）：类注释「应答消息值长度(最后字段)」，`CENTER_TAC`「中心校验码应答值、TAC应答值」、`PASSCODE_KEY`「passcodekey应答值」、`PUBLICKEY_SIGN`「公钥值、签名值」。
- **加密机操作码按「返回字节数」分类**（acc-security-server，`Constant.ORDER_TYPE`，`.../config/Constant.java:147~161`）：类注释「加密机不同操作」，三个常量注释分别为「返回 0 字节」「返回 8 字节」「返回 10 字节」。
- **超时与轮询是「轮询等应答」模型，不是回调**（acc-security-server，`Constant.OverTime`，`.../config/Constant.java:85~95`）：类注释「超时时间」，`POLL_TIME`「轮询时间 毫秒」、`TIME`「超时时间 毫秒」。
- **短连接客户端刻意为「等待链路关闭」单开线程**（acc-security-server，`ShortConnectClient`，`.../server/shortconnect/ShortConnectClient.java:61`）：原文「等待客户端链路关闭，就是由于这里会将线程阻塞，导致无法发送信息，所以我这里开了线程」；同文件 :51「判断是否连接成功」、:58「发送数据」、:64「接收服务端返回的数据」。
- **长连接客户端有同款说明**（acc-security-server，`SocketClient`，`.../server/socket/SocketClient.java:53、57`）：「判断是否连接成功」「等待客户端链路关闭，就是由于这里会将线程阻塞，导致无法发送信息，这里开了线程」。
- **短连接处理器手动释放缓冲并主动关通道**（acc-security-server，`ShortConnectClientHandler`，`.../server/shortconnect/ShortConnectClientHandler.java:13、37、42`）：类注释「处理服务端返回的数据」，:37「手动释放」，:42「把客户端的通道关闭」。
- **重连与心跳自成一套外来构件**（acc-security-server，`reconnect` 包）：`ReConnectManager`（`.../server/reconnect/handle/ReConnectManager.java:26、36、44`）「Trigger reconnect job」「Close reconnect job if reconnect success.」「build an thread executor」；`ClientHeartBeatHandlerImpl`（`.../server/reconnect/thread/ClientHeartBeatHandlerImpl.java:39`）只有一行「重连」；`HeartBeatHandler`（`.../server/reconnect/thread/HeartBeatHandler.java:15`）「处理心跳」；`ContextHolder`（`.../server/reconnect/thread/ContextHolder.java:4`）「Function: Something about of client runtime sign.」；`BeanConfig`（`.../server/reconnect/BeanConfig.java:14`）「Function:bean 配置」。**这五个类的注释头署名 `crossoverJie`、日期 2018~2020，与本项目其余代码不同源。**
- **Netty 服务端 / 客户端的 pipeline 顺序与 backlog 说明**（acc-security-server，`NettyServer` / `NettyClient` / `SimpleServerHandler`）：`NettyServer`（`.../server/socketServer/NettyServer.java:26~28`）「第1步定义两个线程组，用来处理客户端通道的accept和读写事件」「parentGroup用来处理accept事件，childgroup用来处理通道的读写事件」「parentGroup获取客户端连接，连接接收到之后再将连接转发给childgroup去处理」，:31~33「用于构造服务端套接字ServerSocket对象，标识当服务器请求处理线程全满时，用于临时存放已完成三次握手的请求的队列的最大长度。」「用来初始化服务端可连接队列」「服务端处理客户端连接请求是按顺序处理的，所以同一时间只能处理一个客户端连接，多个客户端来的时候，服务端将不能处理的客户端连接请求放在队列中等待处理，backlog参数指定了队列的大小。」，:44~46「maxFrameLength表示这一贞最大的大小」「delimiter表示分隔符，我们需要先将分割符写入到ByteBuf中，然后当做参数传入；」「需要注意的是，netty并没有提供一个DelimiterBasedFrameDecoder对应的编码器实现(笔者没有找到)，因此在发送端需要自行编码添加分隔符，如 \r \n分隔符」；`SimpleServerHandler`（`.../server/socketServer/SimpleServerHandler.java:22、23、30`）「可以在这里面写一套类似SpringMVC的框架」「让SimpleServerHandler不跟任何业务有关，可以封装一套框架」「返回给客户端的数据，告诉我已经读到你的数据了」。**pipeline 顺序那两条属墓碑，见文末清单。**
- **线程池配置与拒绝策略的四种取值说明**（acc-security-server，`ExecutorConfig`，`.../server/config/ExecutorConfig.java:16、25~49、52~56`）：类注释「线程池配置类」，字段注释「线程核心数」「最大线程数」「队列大小」「线程名称前缀」，:41「使用VisiableThreadPoolTaskExecutor」，:52~56 逐条列出 `AbortPolicy`「丢弃任务并抛出RejectedExecutionException异常。」/ `DiscardPolicy`「也是丢弃任务，但是不抛出异常。」/ `DiscardOldestPolicy`「丢弃队列最前面的任务，然后重新尝试执行任务（重复此过程）」/ `CallerRunsPolicy`「由调用线程处理该任务,如果执行器已关闭,则丢弃.」。配套 `VisiableThreadPoolTaskExecutor`（`.../server/config/VisiableThreadPoolTaskExecutor.java:13`）「显示线程执行情况信息」。
- **应答暂存用的是自研内存缓存，不是 Redis**（acc-security-server，`LocalCache`，`.../server/config/LocalCache.java:16、21、25、91、114~129`）：「缓存默认失效时间(毫秒)」「缓存清除动作执行间隔(秒)」「缓存存储的map」「定时器线程-用于检查缓存过期」，内部类 `ValueEntity` 注释「存储单元」「值」「过期时间(毫秒)」「创建时的时间戳」。**:31 有一条空 `// TODO: 2020/6/20`（无内容）；:41~51 是被整段注释掉的双检锁 `getInstance()`。**
- **参数校验被设为快速失败**（acc-security-server，`ValidateConfig`，`.../server/config/ValidateConfig.java:18`）：原文「设置validator模式为快速失败返回」。
- **取客户端真实 IP 的代理头顺序**（acc-security-server，`MessageLogAop`，`.../server/aop/MessageLogAop.java:32、39~61`）：「获取远程ip」，随后逐条注明 `X-Forwarded-For`「Squid 服务代理」、`Proxy-Client-IP`「Apache 服务代理」、`WL-Proxy-Client-IP`「WebLogic 服务代理」、`HTTP_CLIENT_IP`「有些服务代理」、`X-Real-IP`「nginx 服务代理」，:57「有些网络通过多层代理,会获取到多个IP,通常以(,)分割开来,并且第一个IP为客户端真是IP」，:61「如果还获取不到,最后再通过request.getRemoteAddr()获取」；:82~91「记录下请求内容」「默认只传入一个参数」「请求处理」。**「默认只传入一个参数」是入参日志的隐含前提，多参方法下这行日志的含义会变。**
- **全局异常处理器只覆盖 Controller 层**（acc-security-server，`GlobalExceptionHandler`，`.../server/component/GlobalExceptionHandler.java:23`）：「处理Controller层所有异常」；:25~50 有一段被整段注释掉的 `@ExceptionHandler` + `BAD_REQUEST` 字段级错误收集实现（未启用）。

### 二、MAC 与 TAC

- **充值 MAC1 的完整出向报文口径写在注释里**（acc-security-server，`CommonMacServiceImpl`，`.../server/service/impl/CommonMacServiceImpl.java:34~52`）：逐字段列「命令类型：B0」「命令：81」「用户保留字：0000000000000000」「MAC类型：00」「次主秘钥索引：00BA」「分散次数：01」「分散数据：卡号」「临时秘钥计算算法：00」「SESSIONKEY数据：随机数4字节+2字节票卡计数器+0x8000」「MAC初始数据：0000000000000000」，MAC 数据体则是「大端」+「交易前余额（4字节）+ 交易金额（4字节）+ 交易类型（1字节 02-电子钱包圈存）+ 城市代码(2字节)」+「充值设备节点（4字节）」。**注意 :39 写「次主秘钥索引：00BA」而 :55 的行内注释写「MAC1 分散秘钥00B6」——同一方法内两处索引值不一致，见文末「缺口与矛盾」。**
- **充值 MAC2 的出向报文口径**（acc-security-server，`CommonMacServiceImpl`，`.../server/service/impl/CommonMacServiceImpl.java:138~155`）：「命令类型：B0」「命令：80」「次主秘钥索引：00B6」「分散次数：01」「分散数据：卡号」「SESSIONKEY数据：随机数4字节+2字节票卡计数器+0x8000」，数据体「大端」+「交易金额（4字节）+ 交易类型（1字节-固定02-电子钱包圈存）+」「设备节点标识码（4字节）+ 中心日期时间（7字节）」。配套行内注释 :158「MAC1 分散秘钥00B6」、:161「分散数据 卡号」、:165「sessionKey 随机数4字节+2字节票卡计数器+0x8000」、:173「mac数据拼接」、:181「mac数据长度」、:183「mac数据」。
- **发售 / 退款 key 的出向报文口径**（acc-security-server，`CommonSaleAndRefundServiceImpl`，`.../server/service/impl/CommonSaleAndRefundServiceImpl.java:32~38`）：「命令类型：B0」「命令：91」「用户保留字：0000000000000000」「分散次数：02」「分散数据：4500000000000000 + 卡号」「数据长度：0008」「数据：8字节」；:46 行内注释「MAC1 分散秘钥00B0」与上面块注释里的「命令：91」并存，**注意分散密钥索引在块注释里没写、只出现在行内注释**。
- **UL 卡公共 TAC 有两套口径，同一个类里并存**（acc-security-server，`CommonTacServiceImpl`，`.../server/service/impl/CommonTacServiceImpl.java`）：
  - 第一套 :34~44「命令类型：B0」「命令：84」「一次TAC：00」「次主秘钥索引：00C3」「分散次数：01」「分散数据： 16位逻辑卡号」「TAC初始数据：0000000000000000」「TAC: 8字节」「TAC数据长度： 2字节(大端) 001A」「TAC数据（小端) = 交易类型(1字节) + 终端编码（4字节）+ 操作员代号（4字节BCD）+ 交易时间（7字节BCD）+ 设备交易序号(4字节) + 交易金额（4字节）+　卡交易序号（2字节)」；行内 :46「ul卡 分散秘钥00C3」、:51「ul 分散数据 = 逻辑卡号」。
  - 第二套 :106~116 换成「次主秘钥索引：00C1」「分散次数：02」「分散数据： 4500FF0000000000 + 16位逻辑卡号」「TAC数据长度： 2字节(大端)  0016」「TAC数据（大端) = 交易金额（4字节）+交易类型（1字节）+ sam终端编号（6字节）+终端交易序号（4字节）+交易日期（4字节）+交易时间（3字节）」；行内 :118「ul卡 分散秘钥00C1」、:123「分散数据= 0532FF0000000000 + 逻辑卡号   青岛 0532」。**块注释写 `4500FF...` 而行内注释写 `0532FF...`（并标注「青岛 0532」），两者前两字节不同 —— 这是两套 TAC 里最容易改错的一处，见文末「缺口与矛盾」。**
  - **两套 TAC 的字节序相反**（第一套「TAC数据（小端)」、第二套「TAC数据（大端)」），改任一套 MUST 先确认改的是哪一套。
- **加密机入参对象的字段语义**（acc-security-server，`param` 包）：`MacBean`（`.../server/param/MacBean.java:10~70`）「命令类型」「命令」「用户保留字」「MAC类型」「次主密钥索引」「分散次数」「分散数据」「临时秘钥计算算法」「SESSIONKEY数据」「MAC初始数据」「MAC」「MAC数据长度」「MAC数据」；`TacBean`（`.../server/param/TacBean.java:12~62`）同形，把 MAC 换成「TAC类型」「TAC初始数据」「TAC」「TAC数据长度」「TAC数据」；`SaleAndRefundBean`（`.../server/param/SaleAndRefundBean.java:10~45`）少了 MAC 段，只到「数据长度」「数据」。**这三个 Bean 的字段顺序就是拼包顺序。**
- **上行 DTO 侧的字段语义**（acc-security-server，`feign/domain`）：`InvestMac1Param`（`.../feign/domain/commonmac/InvestMac1Param.java:12~46`）「交易前金额」「交易金额」「随机数」「票卡计数器」「卡号」「mac」；`InvestMac2Param`（`.../feign/domain/commonmac/InvestMac2Param.java:12~41`）「交易金额」「随机数」「票卡计数器」「卡号」「中心日期时间」；`SaleAndRefundParam`（`.../feign/domain/commonmac/SaleAndRefundParam.java:12、19`）「物理卡号」「mac」；`CpuTacParam` 与 `SingleTicketTacParam`（`.../feign/domain/commontac/CpuTacParam.java:12、19`、`SingleTicketTacParam.java:12、19`）**注释完全相同**，都是「16位逻辑卡号」「公共TAC」；`CenterMacParm`（`.../feign/domain/centercode/CenterMacParm.java:6、15`）「中心校验码参数」「中心票号」；`PassCodeKeyParm`（`.../feign/domain/passcode/PassCodeKeyParm.java:13`）「通行码 N32」；`QrcodeSignParm`（`.../feign/domain/qrcodesign/QrcodeSignParm.java:12、19`）「私钥索引号」「签名数据块字段」；`AsymmetricParm`（`.../feign/domain/asymmetric/AsymmetricParm.java:12`）「公钥索引号」；`BaseCheckResult`（`.../feign/domain/base/BaseCheckResult.java:8、16、20`）「加密服务通用响应体」「校验结果」「中心处理流水」。
- **按 `@Order` 注解排序拼报文是本模块的通用拼包机制**（acc-security-server，`OperationMacUtil.getMacString`，`.../server/util/OperationMacUtil.java:16、28`）：「通过类中属性的Order的值进行排序，并将所有属性值拼接成字符串返回」「对属性数组通过order注解进行排序」；注解定义在 `Order`（`.../feign/annotation/Order.java:6、17`）「校验规则.顺序 注解」「校验域中属性拼接顺序, 默认值为0」。**因此改动任何 `*Param` / `*Bean` 的字段 `@Order` 值等于改出向报文字节序；:46 有一行被注释掉的错误日志「通过Order注解进行报文拼接出错：{}」，说明该路径的异常曾被记日志、现已不记。**
- **Controller 层的业务命名与实现不完全对应**（acc-security-server，`controller` 包）：`CommonMacController`（`.../server/controller/CommonMacController.java:16、34`）类注释写的是「公共TAC计算Controller」、方法注释才是「充值mac2计算」；`CommonTacController`（`.../server/controller/CommonTacController.java:16`）类注释同样是「公共TAC计算Controller」。**两个不同 Controller 的类注释逐字相同，按类注释无法区分职责，MUST 看 URL。**
- **日志前缀常量固定了六类操作的中文名**（acc-security-server，`Constant.Log`，`.../config/Constant.java:99~125`）：「发卡机构公钥证书获取」「指定索引公钥查询」「二维码私钥签名计算」（**字段注释写「计算」、常量值写「验算」，不一致**）「中心校验码计算」「二维码码体加解密秘钥计算」「公共TAC计算」。按日志前缀检索时以常量值为准。
- **十六进制与 BCD 转换口径**（acc-security-server，`TransformUtils`，`.../server/util/TransformUtils.java:9、40、44、58、66、82、100、183、232、252`）：`hexToString` 注释给了一组具体的输入输出示例（:10）；`bytesToHex` 说明「字节数组转化为十六进制字符串 并按照len的倍数进行补"0"」，参数说明「部位原则  不够len倍数补"0"」，:58「长度不满32的整数倍 在后边添加 "0"`」；另有「将字符串str 按照长度len 进行分组」「将字符串数组进行异或运算」「异或运算」「将十进制数字字符串转换为BCD码」「BCD转String」，:252 英文注释说明单字节转换示例。**补 `0` 的位置（后补）与 32 的倍数是 MAC/TAC 数据拼装的隐含约定。**
- **`Util` 里有一条性能改造留痕**（acc-security-server，`Util`，`.../server/util/Util.java:134`）：「复用已验证的 encodeHex，避免循环字符串拼接的 O(n²) 拷贝」；同文件 :17「十六进制字符到4位二进制字符串的查表映射，下标即 "0123456789ABCDEF".indexOf(c)」、:369「非法字符与原 switch 行为一致：跳过不追加」。**后两条是行为等价性声明，属墓碑，见文末清单。**
- **雪花 ID 的位宽分配**（acc-security-server，`IdWorker`，`.../server/util/IdWorker.java:5~48、63~110`）：「下面两个每个5位，加起来就是10位的工作机器id」「12位的序列号」「初始时间戳」「长度为5位」「最大值」「序列号id长度」「序列号最大值」「工作id需要左移的位数，12位」「数据id需要左移位数 12+5=17位」「时间戳需要左移位数 12+5+5=22位」「上次时间戳，初始值为负数」；:67「获取当前时间戳如果小于上次时间戳，则表示时间戳获取出现异常」、:74「获取当前时间戳如果等于上次时间戳（同一毫秒内），则在序列号加一；否则序列号赋值为0，从0开始。」，:87~94 整段解释按位或拼装 ID 的原理。**:19~20 与 :69 各有一行被注释掉的 `System.out.printf` / `System.err.printf`（时钟回拨告警），即时钟回拨现在既不打日志也不抛异常。**

### 三、证书

- **`CertificateBean` 的字段就是证书类请求的拼包顺序**（acc-security-server，`CertificateBean`，`.../server/param/CertificateBean.java:12~37`）：「命令类型」「命令」「算法标识」「秘钥标识」「秘钥索引」「秘钥口令」。
- **卡证书报文的字段语义**（acc-security-server，`feign/domain/cardCertificate`）：`CardCertificate`（`.../feign/domain/cardCertificate/CardCertificate.java:12~69`）「证书格式」「发卡机构标识」「证书失效日期」「证书序列号」「发卡机构公钥签名算法标识」「发卡机构公钥加密算法标识」「公钥参数标识」「发卡机构公钥模长」「发卡机构公钥」；`CardCertificateParam`（`.../feign/domain/cardCertificate/CardCertificateParam.java:12、19`）「记录头」「服务标识」；`CardCertificateResult`（`.../feign/domain/cardCertificate/CardCertificateResult.java:9~25`）「记录头」「服务标识」「ACC密管中心公钥索引」「证书数字签名」。
- **证书签名的组装顺序与固定用户 ID 写在 `service/Main.java` 的注释里**（acc-security-server，`Main`，`.../server/service/Main.java:11、20~63`）：类注释「生成公钥证书->设备」，:20「第10字段公钥」、:23「证书1-10字段」、:26「证书1、3-10字段」、:34「公钥证书-测试」、:43「截取私钥」、:45「截取公钥」、:48「用户ID，使用固定值1234567812345678。」、:52「原文」、:55「计算哈希值」、:63「签名」。**:24 / :32 / :35 三行注释里包含疑似证书 / 公钥 / 签名真值（Base64 与 Hex 串），值见源码该行，本处不回显。**
- **`CertificateServiceImpl` 里同一套签名步骤再出现一次**（acc-security-server，`CertificateServiceImpl`，`.../server/service/impl/CertificateServiceImpl.java:71~91`）：「截取私钥」「截取公钥」「用户ID，使用固定值1234567812345678。」「原文」「计算哈希值」「签名」；:82 有一行被注释掉的 `str.getBytes()` 取原文写法。**「固定用户 ID」在本模块共出现 5 处（`service/Main.java:48`、`CertificateServiceImpl.java:76`、`util/sm2/Main.java:28、65、98`、`util/sm2/SM2.java:162、216、258`），它是 SM2 `getZ` 的输入，改一处等于改签名结果。**
- **整个「从加密机取证书」的实现是一个被逐行注释掉的类**（acc-security-server，`CertificateFromMachineServiceImpl`，`.../server/service/impl/CertificateFromMachineServiceImpl.java:1~153`）：**全文 153 行都在注释里，包括 `@Service` 声明**。其中保留了完整的报文口径注释（:47~52「命令类型： D3」「命令：02」「算法标识：07」「秘钥长度：0100」「秘钥索引：0001」「秘钥口令：0000000000000000」）、超时处理（:80「加密机调用超时，用户保留域{}」）、成功判据（:88 `"41".equals(resultData.substring(0, 2))`）、拼接九个证书字段后取签名（:102~113）、`0x24` 与 `Begin_Identifier("36")`（:115~116）、以及 :127「私钥密文」/ :128「公钥明文X+Y」在应答串中的截取区间。**这是「加密机版证书」唯一的现存设计记录；同时它内部有 `log.info("私钥:" + ...)` 一类打印密钥的写法（:140、:141），启用前 MUST 先删除那几行 —— 属墓碑，见文末清单。**
- **SM2 工具层的对外能力**（acc-security-server，`SM2SMUtil`，`.../server/util/sm2/SM2SMUtil.java:32、60~202`）：:32「Base64解码、加码用于判断是否为base64格式」，其余为「生成公私钥对」「根据用户ID签名 Str」「根据用户ID签名 都为byte」「SM2私钥签名，带用户ID。」「验签Str」「验签Byte」「加密Str」「加密byte」「解密」。
- **`SM2` 类里有三条实现约束型注释**（acc-security-server，`SM2`，`.../server/util/sm2/SM2.java`）：:376「某版本ios的sm2算法库有问题，服务端过滤掉这种他们不能处理的密钥」、:386「运行20次还不能生成一个有效的密钥对的话，就报错」、:639「目前只有羊城通是没有hash的，这里先不支持压缩公钥」；另有 :29~38 曲线参数说明（`y^3 = x^3+ax+b;`、`(gx,gy)为基点；`、`p,0`…`gy,5`）、:89~136 `getZ` 的拼装顺序（「userId length」「userId」「a,b」「gx,gy」「x,y」「Z」）、:321「从私钥推导出公钥来」、:348「非压缩公钥转成压缩公钥」、:355 与 :476 / :508「0x04未压缩」、:533 与 :574「压缩33字节，未压缩64字节」、:604「计算哈希值 -- 用指定的userid来做」。**:197~199 与 :245~247 是两段被注释掉的联调用例，注释原文标注「博思给的Base64编码的原文、Base64编码的SM2签名值、Base64编码的SM2公钥」，其中包含疑似公钥 / 签名 / 原文真值，值见源码该行，本处不回显。**
- **`util/sm2` 下另有一个 `Main`，同样是联调脚本**（acc-security-server，`Main`，`.../server/util/sm2/Main.java:17~114`）：「生成SM2密钥对，前32字节是私钥，后64字节是公钥。」「截取私钥」「截取公钥」「用户ID，使用固定值1234567812345678。」「原文」「计算哈希值」「签名」「验签」，:114 是被注释掉的 `log.info("签名HexString...")`。
- **`sm2` 包下的 `Utils` 带 GPL v3 头**（acc-security-server，`Utils`，`.../server/util/sm2/Utils.java:1~16、28、51`）：注释含 `$Id: Utils.java,v 1.1.1.1 2007/10/06 13:47:03 benmoez Exp $`、`Author : Moez Ben MBarka Moez`、「This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License … version 3」。**这是外来 GPL 代码进本仓库的唯一物证，涉合规，MUST 提示人工复核。** 同包 `GeneralDigest` / `SM3Digest` / `Cipher` 的注释均为算法内部步骤（「fill the current word」「process whole words.」「load in the remainder.」「add the pad bytes.」「Reset」），无本项目语义。

### 四、IF7B 出向代理

**A. acc-security-server 侧（`/ci/itp/**`，即被 acc-secure-server 代理的那一端）**

- **`ItpRemainingController` 的定位是「secret-web 的其余接口」**（acc-security-server，`ItpRemainingController`，`.../server/controller/ItpRemainingController.java:19、20`）：类注释「ITP剩余接口控制器。」+「这里承接 secret-web 中除签名公钥、签名行业数据以外的其他 ITP 接口。」**这句是本域的接口划分判据：签名公钥与签名行业数据在 `ItpSignPubkeyController`，其余在这里。**
- **五个方法的语义**（同文件 :35、:45、:57、:71、:83~87）：「请求生成地铁 CA 密钥。」「请求生成用户 SM2 密钥对。」「按 ITP 与 ACC 约定的 KEK 导出用户私钥。」「导出应用子密钥 DPK。  请求HCE卡片消费密钥」「请求发售 HCE 卡数据。」+「调用方传入 NFC 票卡类型 `ticketCard` 及物理卡号 `iptUserId`。`iptUserId` 的格式为 `000000` 加四字节 ITP 用户编码的十六进制字符串；成功响应 data 中的 `logicNum` 为生成的 HCE 逻辑卡号。」**`iptUserId` 那条格式约定是本域唯一的物理卡号构造规则（注意注释里的参数名拼写就是 `iptUserId`，不是 `itpUserId`）。**
- **同一条格式约定在 DTO 侧重复一次**（acc-security-server，`RequestHceCardDataParam`，`.../feign/domain/param/RequestHceCardDataParam.java:4、12、17`）：类注释「`/ci/itp/requestHceCardData` 的 HCE 卡数据发售参数。」，字段注释「票卡类型，开户场景固定为 `02`，表示后付费。」「物理卡号，格式为 `000000` + 四字节 ITP 用户编码的十六进制字符串。」**「开户场景固定为 02」是取值约束，属墓碑，见文末清单。**
- **`ItpRemainingService` 的注释说明了它与原项目的关系与每步的加密机指令**（acc-security-server，`ItpRemainingService`，`.../server/itp/service/ItpRemainingService.java`）：:28~30「ITP剩余接口业务处理。」+「这里保留了 secret-web 中对应接口的报文组装和响应解析逻辑，controller 只负责验签、解析 bizData 和转发调用。」；逐方法标注对应的原项目方法名 —— :56~57「请求生成地铁 CA 密钥。」/「对应原项目 HSMService.getCaKey。」、:91~92「请求生成用户 SM2 密钥对。」/「对应原项目 HSMService.requestUserSm2Key。」、:122~123「按 ITP 与 ACC 约定的 KEK 导出用户私钥。」/「对应原项目 HSMService.requestExportPriKey。」；:166~167「导出应用子密钥 DPK。」+「先通过 B061 获取分散密钥，再通过 72 指令按 KEK 加密返回。」；:209~210「请求发售 HCE 卡数据。」+「包含 MAC1、分散密钥、MAC2、MAC3 计算以及逻辑卡号生成。」；:277「组装 HCE 卡基础票卡数据，不包含后续 MAC1、MAC2、MAC3 填充结果。」、:299「组装 MAC1 请求报文。」、:312「组装分散密钥请求报文。」、:325「按大端格式将 int 转为 4 字节数组。」、:337「将原始字段按十六进制字符串输出。」、:344「加密机返回非 A 应答时，按原项目逻辑透传 errcode。」
  - **`B061` 与 `72` 两个指令号只出现在这一行注释里**，是 DPK 链路的唯一记录。
  - **:344「非 A 应答透传 errcode」是错误码口径**：本模块不把加密机错误码翻译成自有错误码，直接透传。
  - **`ItpRemainingService` 与 `ItpRequestService` 是零引用的死类**（本文档上方「死代码警告」已记，controller 只注入 `ItpService`）—— **因此上面这些注释是「唯一的设计记录、但不是在跑的代码」，改逻辑 MUST 改 `ItpServiceImpl`（`.../server/itp/service/impl/ItpServiceImpl.java:30` 只有一行类注释「ITP接口统一实现。」，无其他注释）。**
- **`ItpSignPubkeyController` 里的入向验签被整段注释掉**（acc-security-server，`ItpSignPubkeyController`，`.../server/controller/ItpSignPubkeyController.java:32、38~40、54、59~63、72~75`）：两个方法注释是「请求签名公钥」「请求签名行业数据」；:38~40 与 :61~63 是**被注释掉的 `itpRequestSignVerifier.checkSign(request)` 校验与「签名校验未通过」错误返回**，:59 起整个 `requestSignInsData(HttpServletRequest)` 方法体在注释里，:72~75 是被注释掉的 `parseBizData`（`JSON.parseObject(request.getParameter("bizData"), Map.class)`）。**结论：这两个端点当前没有入向验签，与 §5.2「新增状态变更型接口 MUST 有鉴权」冲突；`ItpRequestSignVerifier` 类本身一行注释都没有。**

**B. acc-secure-server 侧（出向代理，当前未接线）**

- **八条 IF7B 编号与业务名的对应，在 Controller、Service 接口、Service 实现、配置四处各写一遍**（acc-secure-server）：
  - `AccSecureController`（`acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/controller/AccSecureController.java:27、40~105`）类注释「ACC 安全接口代理服务。」，八个方法注释依次为「IF7B-01 请求逻辑卡号。」「IF7B-02 请求生成地铁CA密钥。」「IF7B-03 请求生成用户SM2密钥。」「IF7B-04 请求签名用户公钥。」「IF7B-05 请求导出用户私钥。」「IF7B-06 请求签名行业数据。」「IF7B-07 请求HCE卡片消费密钥。」「IF7B-08 请求发售HCE单程票。」
  - `AccSecureService`（`.../accsecure/service/AccSecureService.java:21~58`）的八条注释措辞不同、带「调用文档」字样：「调用文档 IF7B-01，请求 ACC 生成或下发逻辑卡号批次。」「…IF7B-02，请求 ACC 生成地铁 CA 密钥。」「…IF7B-03，请求 ACC 生成用户 SM2 密钥对。」「…IF7B-04，请求 ACC 对用户公钥进行签名。」「…IF7B-05，请求 ACC 按约定 KEK 导出用户私钥。」「…IF7B-06，请求 ACC 对行业数据进行签名。」「…IF7B-07，请求 ACC 返回 HCE 卡片消费子密钥。」「…IF7B-08，请求 ACC 发售 HCE 单程票数据。」
  - `AccSecureProperties.Paths`（`.../accsecure/config/AccSecureProperties.java:111~148`）八条路径键的注释与 Controller 逐字一致。
  - **IF7B-08 在两个模块里的业务名不一致**：acc-secure-server 一律写「请求发售HCE单程票」，acc-security-server 的 `ItpRemainingController:83` 写「请求发售 HCE 卡数据」。见文末「缺口与矛盾」。
- **通用 `post()` 的四段职责由注释划定**（acc-secure-server，`AccSecureServiceImpl`，`.../accsecure/service/impl/AccSecureServiceImpl.java:47、123~124、150、174、188、205、229`）：「初始化 HTTP 客户端和 ACC 配置。」「封装 ACC 公共报文后，通过 HTTP POST 调用指定 ACC 接口，并解析返回的业务报文。」「按文档要求组装 ACC 请求公共参数和签名值。」「把配置中的 ACC 根地址和接口路径拼成最终调用 URL。」「解析 ACC 返回报文，兼容"外层直接返回业务字段"和"bizData 包裹业务字段"两种结构。」「兜底补齐业务响应中的 retCode 和 retMsg，避免字段位于不同层级时丢失。」「在调用失败或解析异常时，构造统一的错误响应对象返回给上层。」
  - **「兼容两种响应结构」+「兜底补齐 retCode/retMsg」是本模块最重要的两条契约判据**：ACC 侧返回结构不固定，`retCode` 可能在外层也可能在 `bizData` 内；解析器两种都吃。
  - **「调用失败或解析异常时构造统一错误响应返回给上层」= 不抛异常**。这与 §5.2「返回 boolean 的 RPC 包装方法」同型：**调用方 MUST 显式检查 `retCode`，NEVER 假定「没抛异常就是成功」**（此判据由 AGENTS.md 提供，注释本身只说了「构造统一的错误响应对象返回给上层」）。
- **响应字段的密钥语义**（acc-secure-server，`accsecure/model/response`）：`RequestCaKeyRespDTO`（`.../model/response/RequestCaKeyRespDTO.java:9~21`）「LMK加密的私钥d。」「公钥XY。」「SM2密钥密文对。」；`RequestUserSm2KeyRespDTO`（`.../model/response/RequestUserSm2KeyRespDTO.java:9~21`）三字段注释与上者**逐字相同**；`RequestExportUserPriKeyRespDTO`（`:9`）「KEK保护的用户私钥d。」；`RequestDpkRespDTO`（`:9`）「KEK保护的用户消费子密钥。」；`RequestSignPubkeyRespDTO`（`:9`）「用户公钥签名数据。」；`RequestSignInsDataRespDTO`（`:9`）「行业数据签名值。」；`RequestHecCardDateRespDTO`（`:9、14`）「单程票数据发行发售内容。」「用户逻辑卡号。」。**「LMK 加密」与「KEK 保护」是两套不同的密钥保护语义：CA / 用户 SM2 密钥出向时是 LMK 加密，导出私钥与 DPK 是 KEK 保护 —— 混用会导致解密失败。**
- **请求字段的语义**（acc-secure-server，`accsecure/model/request`）：`RequestCaKeyReqDTO`（`:7`）「密钥索引。」；`RequestUserSm2KeyReqDTO`（`:7`）「用户逻辑卡号。」；`RequestSignPubkeyReqDTO`（`:7~34`）「用户公钥X。」「用户账户标识。」「公钥有效期。」「LMK加密的CA私钥d。」「CA公钥XY。」「SM2密钥密文对。」；`RequestExportUserPriKeyReqDTO`（`:7、12`）「LMK加密的私钥d。」「KEK密钥索引。」；`RequestSignInsDataReqDTO`（`:7、12`）「行业数据。」「用户逻辑卡号。」；`RequestDpkReqDTO`（`:7`）「用户逻辑卡号。」；`RequestHecCardDateReqDTO`（`:7`）「票种，文档示例：00-计时票，02-后付费。」；`RequestQrLogicNumListReqDTO`（`:7`）「请求数量，文档默认 10 万。」
  - **IF7B-08 的票种取值 `00` / `02` 与 acc-security-server 侧 `RequestHceCardDataParam:12` 的「开户场景固定为 02」并不矛盾但口径更宽**（前者是文档示例、后者是场景约束）。
  - **IF7B-01 的「文档默认 10 万」是批量规模上限的唯一记录**；该接口已迁至 card-pool-server 直连（见本文档上方），**这行注释属于「已迁走的接口的规格残留」**。
- **acc-security-server 侧对应的上行 DTO 注释略有差异**（acc-security-server，`feign/domain/param`）：`RequestExportUserPriKeyParam`（`.../feign/domain/param/RequestExportUserPriKeyParam.java:10、16、21`）写的是「LMK加密的私钥**k**」（acc-secure 侧写「私钥 d」）、「公钥XY」、「kek密钥索引」；`RequestSignPubkeyParam`（`.../feign/domain/param/RequestSignPubkeyParam.java:10~35`）六字段与 acc-secure 侧逐字一致；`RequestCaKeyParam`（`:10`）「密钥索引」。**「私钥 k」vs「私钥 d」是同一字段在两个模块的两种写法，见文末「缺口与矛盾」。** `RequestDPKParam` / `RequestSignInsDataParam` / `RequestUserSm2KeyParam` **只有作者与日期头，零业务注释**。
- **响应模型侧零注释**（acc-security-server，`itp/model`）：`ItpCaKeyResponse` / `ItpDpkResponse` / `ItpExportPriKeyResponse` / `ItpHceCardResponse` / `ItpResponseModel` / `ItpSignInsDataResponse` / `ItpSignPubkeyResponse` / `ItpUserSm2KeyResponse` **八个类一条注释都没有**；`AccSecureErrorCodeEnum`（acc-secure-server，`.../accsecure/constant/AccSecureErrorCodeEnum.java:3`）只有类注释「ACC 安全服务错误码定义。」，**枚举项逐条无注释**，因此**错误码含义在注释里没有记录**。
- **`AccSecureSignUtils`（acc-secure-server，`.../accsecure/util/AccSecureSignUtils.java:16`）只有一行类注释「ACC 签名工具。」**，签名算法、参与签名的字段与排序规则**注释里完全没有**。改动前 MUST 读代码。

### 五、配置与密钥注入

- **`acc-security-server/src/main/resources/application.properties` 里一条注释都没有**（全文 33 行，纯键值）。因此这一小节的「注释知识」为空，以下只按任务要求点出**键名与位置**，供后续补注释与整改用，**不回显任何值**：
  - HSM 连接类：`security.firstIp`（:18）、`security.firstPort`（:19）、`security.secondPort`（:20）、`security.connectOutTime`（:21）、`security.readOutTime`（:22）、`security.index`（:23）。**注释里没有任何一条说明「firstPort / secondPort 分别是什么」「connectOutTime 与 readOutTime 的单位」，与 §一 `Constant.OverTime` 那两个常量的关系也没有记录 —— 属缺口。**
  - **密钥类（含疑似明文真值，值见源码该行，本处不回显）**：`security.publicKey`（:24）、**`security.privateKey`（:25）**、**`itp.signKey`（:31）**、`itp.tacKeyIdex`（:32，注意键名拼写是 `Idex` 而非 `Index`）。**这四行既无注释、也未写成 `${ENV_VAR:}` 形态，与 AGENTS.md §5.2「敏感配置 MUST 写成 `${ENV_VAR:}`（空默认值）并由 K8s Secret 注入，NEVER 写默认真值」直接冲突。**
  - 线程池类：`thread.corePoolSize`（:27）、`thread.maxPoolSize`（:28）、`thread.queueCapacity`（:29）、`thread.socketNamePrefix`（:30）—— 对应 §一 `ExecutorConfig` 那四条字段注释。
  - `SecurityConfig`（acc-security-server，`.../server/config/SecurityConfig.java:7`）**只有一行类注释「加密服务配置」**，没有逐字段说明。
- **`acc-secure-server/src/main/resources/application.properties` 只有一条「注释」，而且是误报**（acc-secure-server，`:19`）：抽取工具把 `acc.secure.base-url=http://127.0.0.1:8080` 里的 `//127.0.0.1:8080` 当成行注释命中。**该文件真实注释数为 0。** 该文件的 `acc.secure.sign-key`（:25）是**空值**（符合 `${ENV:}` 精神但没写成 `${ENV_VAR:}` 形态），`acc.secure.base-url`（:19）是 `127.0.0.1` 占位（本文档上方「已知问题」已记）。
- **`AccSecureProperties` 的字段注释是这批配置键的唯一语义来源**（acc-secure-server，`AccSecureProperties`，`.../accsecure/config/AccSecureProperties.java:5、10~46`）：类注释「ACC 安全接口调用配置。」，字段注释依次为「ACC 服务根地址。」「商户编码。」「字符集。」「数据格式。」「设备编码。」「签名类型。」「**MD5 签名 key。**」「ACC 接口路径配置。」
  - **「MD5 签名 key」（:41，对应键 `acc.secure.sign-key`）是本域出向签名算法的唯一记录 —— 注释说是 MD5，与 AGENTS.md §4「SHA256WithRSA 仅用于渠道对接方向、入向验签分散在三处」的描述属另一条链路，MUST NOT 与其它链路的签名逻辑合并。**
  - 「签名类型。」（:36，键 `acc.secure.sign-type`，配置值 `00`）与 `AccountRequestVerifier` 的 `signType=00` 免签语义**同名不同链路**，注释里没说这里的 `00` 是否也代表免签 —— 属缺口。
- **`ItpConstants`（acc-security-server，`.../server/itp/util/ItpConstants.java`）一条注释都没有**，`itp.signKey` / `itp.tacKeyIdex` 两个键在代码侧的用法无注释可依。

### 六、持久层

**两个模块都没有持久层，注释侧也没有任何 SQL / mapper 相关记录。**

- `acc-security-server`：无 `mapper` 目录、无 `*Mapper.java`、无 `src/main/resources/mapper/*.xml`、无数据源配置（`application.properties` 里没有 `spring.datasource.*` 或 `other.sql.*`）。**唯一带「持久」语义的是 `LocalCache`（进程内 Map + 定时清理，见 §一），以及 `ItpLogicNumberService` 的内存自增发号（该类零注释，本文档上方「死代码警告」段落已记其重启不持久化的限制 —— 那条限制是文档结论，注释里没有）。**
- `acc-secure-server`：同样无 mapper、无数据源。
- 因此 AGENTS.md §5.1 那两条 Druid WallFilter 陷阱（SQL 正文注释、`where 1 = 1`）与 mapper XML 连续减号陷阱，**在这两个模块内都不适用**，本节不复制。

### 七、缺口与矛盾（照实记录，注释里没有的不代笔）

**A. 注释缺失的关键位置（有代码、无注释）**

1. **HSM 长连的核心实现类全部零注释**：`ClientDecoder` / `ClientHandler` / `ClientSendMsg` / `RawRequestContext` / `RawResponseFieldSpec` / `RawResponseSpec` / `RawSocketResponse` / `RawSocketResponseFuture`（`acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/socket/` 下除 `ClientEncoder`（仅有作者头）与 `SocketClient`（2 行）之外的全部类），外加 `ChannelCache`（`.../server/config/ChannelCache.java`）与 `SpringBeanFactory`（`.../server/reconnect/util/SpringBeanFactory.java`）。**「长连断线后 in-flight 请求怎么处理」「`RawSocketResponseFuture` 的超时与取消语义」「`ChannelCache` 的并发复用规则」——这三件事注释里一个字都没有**，而它们正是本域最容易出运行时问题的地方。
2. **`HsmUnavailableException`（`.../server/exception/HsmUnavailableException.java`）零注释**：抛出条件、上游该如何处置、是否可重试，均无记录。
3. **入向验签零注释**：`ItpRequestSignVerifier`（`.../server/itp/service/ItpRequestSignVerifier.java`）无任何注释，且其唯一调用点在 `ItpSignPubkeyController` 里**已被注释掉**（见 §四 A）。
4. **`ItpCardUtils` / `ItpDesUtils` / `ItpHexUtils` / `ItpPboc3DesMacUtils`（`.../server/itp/util/`）四个类零注释**：PBOC 3DES MAC 的填充方式、初始向量、分组口径**全无注释**，而 §二 那些 MAC 报文口径注释描述的是「拼什么」、不是「怎么算」。
5. **`SecurityServerApplication`（`.../server/SecurityServerApplication.java`）与 `AccSecureServer`（acc-secure-server 启动类）零注释**：开关、`@Enable*` 的取舍理由无记录。
6. **`acc-security-server/application.properties` 全文零注释**（见 §五）。
7. **`AccSecureErrorCodeEnum` 枚举项零注释**（见 §四 B）：IF7B 各接口的错误码含义在注释里查不到。

**B. 注释与注释 / 注释与代码互相矛盾**

1. **`CommonMacServiceImpl` 内充值 MAC1 的次主密钥索引两处不一致**：块注释 `:39`「次主秘钥索引：00BA」vs 行内注释 `:55`「MAC1 分散秘钥00B6」。以哪个为准注释里没说。
2. **`CommonTacServiceImpl` 第二套 TAC 的分散数据前缀两处不一致**：块注释 `:112`「分散数据： 4500FF0000000000 + 16位逻辑卡号」vs 行内注释 `:123`「分散数据= 0532FF0000000000 + 逻辑卡号   青岛 0532」。**行内那条带「青岛 0532」的解释，但块注释没改过来。**
3. **`Constant.Log.GET_SIGN` 的字段注释与常量值不一致**（`.../config/Constant.java:111` 注释「二维码私钥签名计算」，:113 常量值「【二维码私钥签名验算】」）。
4. **同一字段在两模块两种叫法**：`RequestExportUserPriKeyParam:10`「LMK加密的私钥**k**」（acc-security-server）vs `RequestExportUserPriKeyReqDTO:7`「LMK加密的私钥**d**」（acc-secure-server）。
5. **IF7B-08 业务名两模块不一致**：acc-secure-server 一律「请求发售HCE单程票」，acc-security-server `ItpRemainingController:83`「请求发售 HCE 卡数据」。
6. **`CommonMacController` 与 `CommonTacController` 的类注释逐字相同**（都是「公共TAC计算Controller」），但前者的方法是「充值mac2计算」。
7. **`CpuTacParam` 与 `SingleTicketTacParam` 的字段注释逐字相同**（「16位逻辑卡号」「公共TAC」），两个 DTO 从注释上无法区分。
8. **`RequestCaKeyRespDTO` 与 `RequestUserSm2KeyRespDTO` 三个字段注释逐字相同**（IF7B-02 与 IF7B-03 的响应从注释上无法区分）。
9. **注释与本文档上方正文的关系**：`ItpRemainingService`/`ItpRequestService` 的注释写得像「现行实现」（「这里保留了 secret-web 中对应接口的报文组装和响应解析逻辑」），但本文档上方「死代码警告」已实证它们**零引用**。**以文档正文为准，注释未标注废弃。**

**C. 与其它文档的口径差异**

1. **`ItpLogicNumberService` 内存自增、重启不持久化**这一条只写在本文档正文里，**源码注释里没有**（该类零注释）。
2. **acc-secure-server「配置路径拼写错误」（`requestQrLoigcNumList` / `requestHecCardDate`）**只写在本文档正文与配置值里，**注释里没有任何提示** —— `AccSecureProperties:112`「IF7B-01 请求逻辑卡号。」旁边就是那个拼错的键，注释没提。
3. **acc-secure-server「日志路径写死为个人目录 `/Users/zhoucong/logs`」这条在本文档正文里，但 2026-09-16 现查 `acc-secure-server/src/main/resources/application.properties:14~15` 是 `server.tomcat.basedir=./logs` / `logging.file.path=./logs`**（相对路径，不是个人目录）。**该条正文记载与当前配置不符，本次只报告、不修改正文。**

### 墓碑注释清单（建议转为断言测试）

> 「墓碑注释」= 明确禁止某种改法、或声明某个不变量必须保持的注释。**本节不进正文，逐条给 `文件:行号` + 禁止的事 + 能否断言化。**

| 序号 | 文件:行号 | 注释禁止 / 约束的事 | 能否断言化 |
|---|---|---|---|
| 1 | `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/socketServer/NettyServer.java:43` | 「解码器，接收的数据进行解码，**一定要加在 SimpleServerHandler 的上面**」——禁止调整 pipeline 中解码器与业务 handler 的先后顺序 | **能**：断言 `channel.pipeline().names()` 中解码器下标小于 `SimpleServerHandler` 下标（用 `EmbeddedChannel` 即可，不需要真 HSM） |
| 2 | `acc-security-server/.../server/socketServer/NettyClient.java:37` | 「字符串编码器，**一定要加在 SimpleClientHandler 的上面**」——同上，客户端侧 | **能**：同 1 的做法 |
| 3 | `acc-security-server/.../server/socketServer/NettyServer.java:46` | 「netty 并没有提供 DelimiterBasedFrameDecoder 对应的编码器实现，**因此在发送端需要自行编码添加分隔符**」——禁止假定框架会自动补分隔符 | **能**：对编码器输出断言末尾含分隔符字节 |
| 4 | `acc-security-server/.../server/util/Util.java:134` | 「复用已验证的 `encodeHex`，**避免循环字符串拼接的 O(n²) 拷贝**」——禁止回退成字符串累加实现 | **部分能**：性能不宜断言，但可断言该方法输出与 `encodeHex` 逐字节一致（等价性回归） |
| 5 | `acc-security-server/.../server/util/Util.java:369` | 「非法字符与原 `switch` 行为一致：**跳过不追加**」——禁止把非法字符改成抛异常或填充占位 | **能**：喂入含非法字符的串，断言输出长度与内容等于「跳过」语义 |
| 6 | `acc-security-server/.../server/util/TransformUtils.java:58` | 「长度不满 32 的整数倍 **在后边添加 "0"**」——禁止改成前补零或改基数 | **能**：断言 `bytesToHex(x, 32)` 的输出长度是 32 的倍数且补位在尾部 |
| 7 | `acc-security-server/.../server/itp/service/ItpRemainingService.java:344` | 「加密机返回**非 A 应答时，按原项目逻辑透传 errcode**」——禁止把加密机错误码翻译成自有错误码 | **能**：给一个非 `A` 打头的模拟应答，断言返回体里的错误码等于原始 errcode（需先解决该类是死类的问题，见正文 §四 A） |
| 8 | `acc-security-server/.../server/itp/service/ItpRemainingService.java:29~30` | 「**保留了** secret-web 中对应接口的报文组装和响应解析逻辑，controller 只负责验签、解析 bizData 和转发调用」——禁止把业务逻辑上移到 controller | **否**（分层约束，靠评审；可退化为「controller 里不出现报文拼装字样」的静态检查） |
| 9 | `acc-security-server/.../feign/domain/param/RequestHceCardDataParam.java:12` | 「票卡类型，**开户场景固定为 `02`**，表示后付费」——禁止在开户场景传其它票种 | **能**：断言开户链路组装出的 `ticketCard` 恒为 `02` |
| 10 | `acc-security-server/.../feign/domain/param/RequestHceCardDataParam.java:17`（与 `ItpRemainingController.java:86` 重复一次） | 「物理卡号，格式为 `000000` + 四字节 ITP 用户编码的十六进制字符串」——禁止改变前缀长度或编码宽度 | **能**：断言生成结果匹配 `^000000[0-9A-Fa-f]{8}$` |
| 11 | `acc-security-server/.../server/util/sm2/SM2.java:376` | 「某版本 ios 的 sm2 算法库有问题，**服务端过滤掉这种他们不能处理的密钥**」——禁止移除该过滤 | **能**：构造一个应被过滤的密钥对形态，断言生成结果不含它 |
| 12 | `acc-security-server/.../server/util/sm2/SM2.java:386` | 「**运行 20 次**还不能生成一个有效的密钥对的话，就报错」——禁止改成无限重试 | **能**：注入必定失败的随机源，断言在有限次后抛异常而非挂死 |
| 13 | `acc-security-server/.../server/util/sm2/SM2.java:639` | 「目前只有羊城通是没有 hash 的，**这里先不支持压缩公钥**」——禁止把压缩公钥喂进该分支 | **部分能**：可断言传入 33 字节压缩公钥时该方法明确拒绝，但「羊城通」这一业务前提无法断言 |
| 14 | `acc-security-server/.../server/service/impl/CertificateFromMachineServiceImpl.java:140~141`（整类在注释内，见正文 §三） | 该被注释类内含 `log.info("公钥:"...)` / `log.info("私钥:"...)`——**启用该类前必须先删掉打印密钥的两行** | **能**：加一条静态检查断言 `src/main` 内不存在打印私钥的日志语句（不依赖该类是否启用） |
| 15 | `acc-security-server/.../server/aop/MessageLogAop.java:57` | 「多层代理会获取到多个 IP，通常以 `,` 分割，**第一个 IP 为客户端真实 IP**」——禁止取最后一个或整串 | **能**：喂 `X-Forwarded-For: a,b,c`，断言取到 `a` |
| 16 | `acc-security-server/.../server/aop/MessageLogAop.java:84` | 「**默认只传入一个参数**」——该切面的入参日志建立在单参前提上 | **部分能**：可断言多参方法下不抛异常；但「日志语义是否仍正确」需人判 |
| 17 | `acc-security-server/.../server/shortconnect/ShortConnectClient.java:61` 与 `.../server/socket/SocketClient.java:57` | 「等待客户端链路关闭会将线程阻塞、导致无法发送信息，**所以开了线程**」——禁止把 `closeFuture` 等待挪回调用线程 | **否**（并发时序，断言不稳定；建议改为代码评审项 + 在该行保留告示） |
| 18 | `acc-security-server/.../server/util/sm2/Utils.java:6~12` | GPL v3 许可头——**禁止在未做合规确认的情况下随代码分发** | **否**（合规事项，MUST 人工复核，建议纳入构建期许可扫描） |

## 附：acc-security-server / acc-secure-server 注释知识迁移（2026-09-16，阶段二·完整）

> **本节与上面「阶段一」的区别**：阶段一只做**抽取归档**、代码里原样保留；本节是**迁移**（抽取 + 删除，闭环）。
> 凡标注 **【已删除】** 的条目，其原文短语**在工作副本里已经 grep 不到了**，只能在 SVN 历史（本轮基线 `r941`）里查：
> `svn cat -r 941 <路径> | grep -n '<短语>'`。标注 **【保留】** 的仍可在当前代码里 grep。
>
> 路径缩写（下文一律用缩写，展开后即真实路径）：
> - `SEC/` = `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/`
> - `FEIGN/` = `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/feign/`
> - `SECURE/` = `acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/`
>
> **行号是 2026-09-16 迁移完成后的快照**（删注释会让后面的行号上移），引用前 MUST 先 grep 现查。
> **安全口径**：密钥只写键名与位置，**任何值都不回显**（AGENTS.md §5.2「敏感配置」）。

### 一、HSM TCP 长连接与连接池

**1. 连接建立与「一条连接占一个线程」**（`SEC/socket/SocketClient.java:34~72`，【保留】grep `客户端连接成功`）
`start()` 带 `@Async("socketAsyncExecutor")`，内部 `Bootstrap` + `NioSocketChannel` + `TCP_NODELAY=true`，pipeline 顺序**固定为 `ClientDecoder` → `ClientEncoder` → `clientHandler`**（`clientHandler` 是 `@ChannelHandler.Sharable` 的单例 Bean，另两个每连接新建）。连到 `securityConfig.getFirstIp()` / `getFirstPort()`（配置 `security.firstIp` / `security.firstPort`），成功即 `ChannelCache.set(future.channel())`，随后**在该线程上 `closeFuture().sync()` 阻塞到连接断开** —— 因此**连接数上限等于 `socketAsyncExecutor` 的线程数**（`thread.corePoolSize` / `thread.maxPoolSize`，当前都是 10）。`finally` 里 `group.shutdownGracefully()` + `ChannelCache.clear(...)`。**【已删除】** 该行原有一行注释「等待客户端链路关闭，就是由于这里会将线程阻塞，导致无法发送信息，这里开了线程」—— 判据已写在本条，**NEVER 把 `closeFuture` 等待挪回调用线程**。

**2. 连接池就是 `ChannelCache` 的静态字段，没有第三方池**（`SEC/config/ChannelCache.java:12~70`，零注释，【无原文】）
`CopyOnWriteArrayList<Channel> cacheList` + `Set<String> exclusiveChannelIds`（`HashSet`，靠方法级 `synchronized` 保护）+ `int index` 轮询游标 + `int size` 独立计数器。全部方法 `static synchronized`。
- `get()`：从 `index` 起最多扫一圈，跳过 `!channel.isActive()` 与已在 `exclusiveChannelIds` 里的通道；池空抛 `HsmUnavailableException("no available channel")`，一圈没命中抛 `("no idle channel")`。
- `acquireExclusive()` / `releaseExclusive(channel)`：raw 报文链路（见第 4 条）的**独占租借**，租借期间该通道对 `get()` 不可见。
- `clear(channel)`：移除独占标记 → `channel.close()` → `cacheList.remove` 成功才 `size--`。
- **坑：`size` 与 `cacheList.size()` 是两个数**。`set()` 无条件 `size++`，`clear()` 只在 remove 命中时 `size--`；而重连判据用的是 `getSize()`（下条），一旦两者漂移，**重连会永久停摆或永久重连**，且没有任何日志能直接看出来。

**3. 断线重连的链路与两个执行器**（`SEC/socket/ClientHandler.java:55~68`、`SEC/reconnect/**`）
`channelInactive` → 打「socket disconnected」→ `ChannelCache.clear(channel)` → 首次进入时用 `SpringBeanFactory.getBean("scheduledTask", ScheduledExecutorService.class)` 与 `getBean(ReConnectManager.class)` 补齐字段 → `reConnectManager.reConnect(ctx)`。
`ReConnectManager.reConnect`（`SEC/reconnect/handle/ReConnectManager.java:30~33`，【保留】grep `Trigger reconnect job`）→ `scheduleAtFixedRate(new ReConnectJob(ctx), 2, 10, TimeUnit.SECONDS)`（**首次延迟 2 秒、之后每 10 秒一次，写死在代码里、没有配置项**）。
`ReConnectJob.run` → `ClientHeartBeatHandlerImpl.process`（`SEC/reconnect/thread/ClientHeartBeatHandlerImpl.java:35~47`，【保留】grep `当前连接数达到最大值`）：`ChannelCache.getSize() < corePoolSize` 就 `ContextHolder.setReconnect(true)` + `client.start()`；否则 `reConnectManager.reConnectSuccess()`（即 `scheduledExecutorService.shutdown()`）。
- **两个执行器并存、其中一个是死的**：`BeanConfig`（`SEC/reconnect/BeanConfig.java:26~34`）注册的 `"scheduledTask"` Bean 与 `ReConnectManager.buildExecutor()` 自建的执行器**线程名前缀都是 `reConnect-job-%d`**，但真正跑重连的是后者；`ClientHandler` 取到的 `scheduledTask` 赋给字段后**再没被使用过**。排查「重连线程是哪个」MUST 看 `ReConnectManager`，NEVER 按 Bean 名找。
- **`exceptionCaught` 只 `clear` 不重连**（`ClientHandler.java:70~78`）：异常关闭的连接要等下一次 `channelInactive` 或已在跑的重连任务捞回来。
- **`ContextHolder`（`SEC/reconnect/thread/ContextHolder.java`）是 `ThreadLocal<Boolean>`，`setReconnect(true)` 与 `client.start()` 的 `@Async` 不在同一线程** —— 该标记在被读之前就已经跨线程失效，全仓也没有任何 `getReconnect()` 调用点。属**无效状态位**，改重连逻辑时 NEVER 依赖它。

**4. 应答回收有两条并存的路径，超时口径不同**
- **路径 A「用户保留域 + 忙轮询」（旧）**：`ClientSendMsg.sendMsg(...)`（`SEC/socket/ClientSendMsg.java:24~34`）只 `writeAndFlush` 后**立刻返回 Channel、不等应答**；`ClientHandler.channelRead` 把 `str.substring(2, 18)` 当成 userRetain，以 **`channel.id() + userRetain.toUpperCase()`** 为键写进 `LocalCache`；业务侧在 `getXxxResultVO` 里 `while (resultData == null)` + `Thread.sleep(Constant.OverTime.POLL_TIME)`（**1 毫秒**）忙等到 `Constant.OverTime.TIME`（**2000 毫秒，硬编码在 `SEC/config/Constant.java`**）判超时，返「加密机调用超时」。`LocalCache` 的默认过期是 **3 秒**（`SEC/config/LocalCache.java:18`，【保留】grep `缓存默认失效时间`），比 2 秒超时**只多 1 秒**：迟到的应答仍会落进缓存、但没人再来取，靠 1 秒一轮的清理线程回收。
- **路径 B「独占通道 + Future」（新，raw 报文用）**：`ClientSendMsg.sendRawMsg(byte[], RawResponseSpec)`（同文件 `:48~71`）→ `ChannelCache.acquireExclusive()` → 把 `RawRequestContext(responseSpec, future)` 挂到通道属性 `ClientDecoder.RAW_REQUEST_CONTEXT` → `writeAndFlush(msg).sync()` → `future.get(securityConfig.getReadOutTime(), MILLISECONDS)`（**配置 `security.readOutTime`，当前 3000**）；`finally` 清属性 + `releaseExclusive`。
- **因此同一台加密机有两个超时值（2000 硬编码 / 3000 可配）**，排查「谁先超时」MUST 先确认走的是哪条路径。判据：调用方拿到的是 `Channel` 就是路径 A，拿到 `RawSocketResponse` 就是路径 B。

**5. 解码器的魔数与分支**（`SEC/socket/ClientDecoder.java`，零注释，【无原文】）
`decode` 先看通道属性里有没有 `RawRequestContext`：有就走 `decodeRaw`、**直接 return（旧格式一概不解）**；没有才走旧解码。旧解码：`in.readableBytes() < HEAD_LENGTH`（**8**）就等；读 1 字节 `type`，**`type == 65` 即 ASCII `'A'` = 成功**、**`type == 69` 即 `'E'` = 失败**；成功分支先读 8 字节 body，再按 `body[0]` 与 `Constant.ORDER_TYPE.BYTE8`（`"01"`）/ `BYTE10`（`"10"`）比对决定**追读 8 或 10 字节**，拼成 `"41" + hex`；失败分支读 9 字节拼 `"45" + hex`。**业务侧一律用 `"41".equals(resultData.substring(0, 2))` 判成功**（`CommonMacServiceImpl` / `CommonTacServiceImpl` / `CommonSaleAndRefundServiceImpl` / `ShortConnectClient` 四处各写一遍）。
`decodeRaw`：读 1 字节 ansCode → `responseSpec.reserved()` 为真再读 **8 字节**保留域 → `ansCode == 'A'` 时按 `RawResponseFieldSpec` 逐字段读（`fixed(n)` 定长；`dynamic(i)` 表示**长度取第 i 个已读字段的值**，`byteArrayToInt` 按**大端**折算），任何一段不够就 `resetReaderIndex()` 等下一帧；非 `'A'` 读 1 字节 errCode。
- **`ClientHandler.channelRead` 的第二个分支是 `if (msg instanceof Object)`（恒真）**：raw 上下文还在、但收到的不是 `RawSocketResponse` 时，把 future 置异常并清属性，**随后仍继续执行 `String str = (String) msg;`** —— 该分支只有在解码器吐出非 String 对象时才会 `ClassCastException`。属现状记录，改这段 MUST 连 `ClientDecoder` 一起看。

**6. 另有一套短连接实现，与长连接完全并行**（`SEC/shortconnect/**`）
`ShortConnectClient.getTacResult(byte[], String)`（`:34~85`，【保留】grep `加密机验证结果`）每次调用**新建 `NioEventLoopGroup` + 连接 + 发送 + `closeFuture().sync()` + `shutdownGracefully()`**，应答靠 `AttributeKey.valueOf(userRetain.toUpperCase())` 存在通道属性上取回，判成功同样是 `"41".equals(...)`。pipeline 只有 `ShortConnectClientEncoder` + `ShortConnectClientHandler`（**没有解码器**，handler 自己从 `ByteBuf` 读）。`ShortConnectClientHandler` 读完即 `ctx.close()`、并在 `finally` 里 `ReferenceCountUtil.release(buff)`（【保留】grep `手动释放`）。
**全仓零调用方**（2026-09-16 grep：`ShortConnectClient` 只被自己的 handler/encoder 引用），属未接线的备用通路。

### 二、MAC 计算

拼包统一走 `ByteConvertUtil.byteMergerAll(...)`，字段顺序**就是那串实参顺序**（改顺序等于改报文，NEVER 重排）。默认值来自 `SEC/param/MacBean.java`：`orderType={0xB0}`、`macType={0x00}`、`disperseNum={0x01}`、`temporaryAlgorithm={0x00}`、`initial=8×0x00`（8 字节全零 MAC 初始向量）。长度字段一律 `NumberUtil.unsignedShortToByte2(len)`（**大端 2 字节**）。

**1. 充值 MAC1 校验**（`SEC/service/impl/CommonMacServiceImpl.java` 的 `verifyMac1`，【保留】grep `MAC1 分散秘钥00B6`）
- 命令 `0x81`；**次主密钥索引代码实际取值 `{0x00, 0xB6}` 即 `00B6`**（见「矛盾与待裁决」第 1 条：本轮**已删除**的块注释里写的是 `00BA`）。
- 分散数据 = 卡号前 **8 字节**（`param.getCardNo()` 转字节后 `arraycopy(...,0,8)`，多余部分丢弃）。
- `sessionKey` = 随机数 4 字节 + 票卡计数器 2 字节（`unsignedShortToByte2`，**大端**）+ `0x80` + `0x00`。
- MAC 数据 **15 字节**：交易前余额 4（`intToByte4`，**大端**）+ 交易金额 4（同）+ `0x02`（交易类型，电子钱包圈存）+ **城市代码 `"4500"` 2 字节** + 设备节点 4。
- 用户保留域 = `Constant.ORDER_TYPE.BYTE0`（`"00"`，期待应答**不带**附加数据）+ `LocalCache.getSequence()`（14 位左补零的进程内自增，`AtomicLong`，**重启归零**）。
- 返回只有 true/false（`"41"` 前缀即 true）。
- **【已删除】** 方法首部原有 19 行块注释，逐字段列「命令类型：B0 / 命令：81 / 用户保留字：0000000000000000 / MAC类型：00 / 次主秘钥索引：00BA / 分散次数：01 / 分散数据：卡号 / 临时秘钥计算算法：00 / SESSIONKEY数据：随机数4字节+2字节票卡计数器+0x8000 / MAC初始数据：0000000000000000 / MAC / MAC数据长度 / MAC数据」+「大端 / 交易前余额（4字节）+ 交易金额（4字节）+ 交易类型（1字节 02-电子钱包圈存）+ 城市代码(2字节) / 充值设备节点（4字节）」。**内容已在上面逐条落地，唯一没落地的是那个与代码冲突的 `00BA`（已记进矛盾清单）。**

**2. 充值 MAC2 计算**（同文件 `getMac2`，【保留】grep `MAC1 分散秘钥00B6`（该行内注释在 MAC2 里被原样复制，**注释名写的是 MAC1**））
- 命令 `0x80`；索引 `{0x00, 0xB6}`（与已删除块注释的 `00B6` 一致，**MAC2 侧两处本来就不冲突**）。
- 分散数据、`sessionKey` 与 MAC1 完全相同。
- MAC 数据 **18 字节**：交易金额 4（大端）+ `0x02` + **城市代码 `"4500"` 2 字节** + 设备节点 4 + 中心日期时间 **7 字节**（调用方传 hex，本模块不校验 BCD 合法性）。
- 拼包实参里**没有 `getMac()`**（MAC1 有）—— MAC2 是「算」不是「验」。
- 用户保留域前缀 `Constant.ORDER_TYPE.BYTE8`（`"01"`，期待应答**追加 8 字节**）；取值口径 **`resultData.substring(len - 16, len - 8)`**，即倒数第二个 8 字节段。**这一句和解码器的「按 `body[0]` 追读 8 字节」是一对，改一处 MUST 改另一处。**
- **【已删除】** 方法首部原有 18 行块注释（同型，索引写 `00B6`、字段清单为「交易金额（4字节）+ 交易类型（1字节-固定02-电子钱包圈存）+ 设备节点标识码（4字节）+ 中心日期时间（7字节）」）。

**3. 发售 / 退款 key 计算**（`SEC/service/impl/CommonSaleAndRefundServiceImpl.java`，【保留】grep `MAC1 分散秘钥00B0`）
- 命令 `0x91`；**索引代码实际取值 `{0x00, 0xB0}` 即 `00B0`**。
- **分散数据 16 字节 = 代码实际取值 `"4500FF0000000000"` + 卡号 8 字节**（分散次数取 `SaleAndRefundBean` 默认值）。
- 数据段 = 随机数 **8 字节**；长度字段取 `SaleAndRefundBean.bytesLength` 的默认值（**方法里没有重新赋值**，与 MAC1/MAC2 那两处显式 `setBytesLength` 不同 —— 改这里 MUST 先确认默认值是否仍匹配 8 字节）。
- 用户保留域前缀 `Constant.ORDER_TYPE.BYTE10`（`"10"`，期待应答**追加 10 字节**）。
- **【已删除】** 方法首部 7 行块注释「命令类型：B0 / 命令：91 / 用户保留字：0000000000000000 / 分散次数：02 / 分散数据：4500000000000000 + 卡号 / 数据长度：0008 / 数据：8字节」。**注意它写的是 `4500` + 6 字节零，而代码是 `4500FF` + 5 字节零** —— 见矛盾清单第 3 条。

**4. HCE 链路里另有一套 MAC，索引来自配置而非硬编码**（`SEC/itp/service/impl/ItpServiceImpl.java`，零注释）
`requestHceCardData` 的三段 MAC：MAC1 走加密机指令 `B011`（`buildMac1Request`，索引取 `@Value("${itp.mac1KeyIdex:192}")`）；分散密钥走 `B091`（`buildDisRequest`，索引取 `${itp.mac2KeyIdex:193}`）；MAC2 / MAC3 走**本地** `ItpPboc3DesMacUtils.calculatePboc3desMAC(data, disMac, ZERO_IVC)`。分散密钥后 8 字节由前 8 字节**逐字节取反**得到（`disMac[i + 8] = (byte) ~disMac[i]`），其中 `disMac[6] = 0x05; disMac[7] = 0x32;`（**即青岛 `0532`，与下面 TAC 一节同源**）。MAC2 结果再做 `mac2Tmp[0]^mac2Tmp[2]`、`mac2Tmp[1]^mac2Tmp[3]` 压成 2 字节。
`ItpPboc3DesMacUtils`（`SEC/itp/util/ItpPboc3DesMacUtils.java`，零注释）：要求 key **16 字节**，取前 8 字节做 DES-CBC 链式异或，末块补 `0x80`（**`blockCount = len/8 + 1`，所以 8 的整数倍也会多出一整块**），最后一块用完整 16 字节 key 做 3DES-CBC，ICV 固定 `ZERO_IVC`。**这套填充口径在代码里没有任何注释，本条是唯一记录。**

### 三、TAC 计算

两个方法在同一个类里（`SEC/service/impl/CommonTacServiceImpl.java`），共用 `TacBean` 默认值：`orderType={0xB0}`、`order={0x84}`、`tacType={0x00}`、`initial=8×0x00`。**两者都不自己拼 TAC 数据体** —— `bytes` 直接取调用方给的 `param.getCommonTacStr()`，**本模块既不校验长度也不校验字节序**，字节序责任全在调用方（`ticket-server` / `fep-dev-server` 侧）。

**1. 单程票（UL 卡）TAC 校验 `ulTacVerify`**（【保留】grep `ul卡 分散秘钥00C3`、`ul 分散数据 = 逻辑卡号`）
- **索引代码实际取值 `{0x00, 0xC3}` 即 `00C3`**；分散次数显式置 `{(byte) 01}`（十进制 1，与默认值同）。
- 分散数据 = **逻辑卡号原样**（16 位 hex → 8 字节，无前缀）。
- 长度字段 = `unsignedShortToByte2(bytes.length)`（大端）；用户保留域前缀 `BYTE0`（`"00"`）。
- **【已删除】** 方法首部 11 行块注释：「命令类型：B0 / 命令：84 / 用户保留字：0000000000000000 / 一次TAC：00 / 次主秘钥索引：00C3 / 分散次数：01 / 分散数据： 16位逻辑卡号 / TAC初始数据：0000000000000000 / TAC: 8字节 / TAC数据长度： 2字节(大端) 001A / **TAC数据（小端)** = 交易类型(1字节) + 终端编码（4字节）+ 操作员代号（4字节BCD）+ 交易时间（7字节BCD）+ 设备交易序号(4字节) + 交易金额（4字节）+ 卡交易序号（2字节)」。**「小端」与长度 `001A`（26 字节）是这条链路唯一的数据体口径记录，除本节外无处可查。**

**2. CPU 卡 TAC 校验 `cpuTacVerify`**（【保留】grep `分散数据= 0532FF0000000000 + 逻辑卡号   青岛 0532`）
- **索引代码实际取值 `{0x00, 0xC1}` 即 `00C1`**；分散次数显式置 `{(byte) 02}`（**八进制字面量 `02`，值仍是 2，但写法容易误读**）。
- **分散数据 = 代码实际取值 `"0532FF0000000000"` + 逻辑卡号**，共 16 字节。行内注释注明 `青岛 0532`。
- **【已删除】** 方法首部 11 行块注释，其中「分散数据： **4500FF0000000000** + 16位逻辑卡号」与代码矛盾（见矛盾清单第 2 条），其余为「次主秘钥索引：00C1 / 分散次数：02 / TAC数据长度： 2字节(大端)  0016 / **TAC数据（大端)** = 交易金额（4字节）+交易类型（1字节）+ sam终端编号（6字节）+终端交易序号（4字节）+交易日期（4字节）+交易时间（3字节）」。
- **两套 TAC 的数据体字节序相反**（UL 小端 26 字节 / CPU 大端 22 字节）。改任一套 **MUST 先确认改的是哪一套**，这是本节最容易踩的一条。

**3. `0532` 与 `4500` 在代码里的分布（实测，用于判断哪个才是现行口径）**
- 代码里出现 **`0532`** 的三处：`cpuTacVerify` 的分散数据前缀 `"0532FF0000000000"`；`ItpServiceImpl.requestDPK` 的 `B061` 请求里 `0x02, 0x05, 0x32, (byte) 0xFF`（分散次数 `02` + `0532FF`）；`ItpServiceImpl.requestHceCardData` 的 `disMac[6] = 0x05; disMac[7] = 0x32;`。
- 代码里出现 **`4500`** 的三处：MAC1 / MAC2 的 **MAC 数据体内的城市代码字段**（`"4500"`，2 字节），以及 `getSaleAndRefundKey` 的**分散数据前缀** `"4500FF0000000000"`。
- **即：`0532` 只出现在「密钥分散数据」里、`4500` 既做过城市代码字段又做过一次分散数据前缀。** 已删除的三处块注释里，凡涉及分散数据前缀的都写 `4500`，而在跑的 TAC / DPK / HCE 代码都用 `0532`。**这不是笔误就是历史口径变更，MUST 拿甲方规格或加密机侧密钥配置定案（见矛盾清单第 2、3 条），NEVER 凭本节推断改代码。**

**4. TAC 密钥索引有两个来源，同一把密钥两种表达**
`ulTacVerify` 硬编码 `00C3`；ITP 侧 `ItpServiceImpl` 与两个死类都用 `@Value("${itp.tacKeyIdex:195}")`，**十进制 195 = 0xC3**，配置键 `itp.tacKeyIdex` 见第七节。**改索引 MUST 两处一起改**，只改一处会出现「同一把 TAC 密钥、两条链路指向不同索引」。注意键名拼写是 `Idex`（不是 `Index`），改名会静默回落到默认值 195。

### 四、证书与密钥

**1. 在跑的证书签名走「本地 SM2」，不走加密机**（`SEC/service/impl/CertificateServiceImpl.java`，【保留】grep `截取私钥`、`用户ID，使用固定值1234567812345678`）
- `getCertificate` 把 9 段按 `StringJoiner("")` 顺序拼成签名原文：`cert_format` → `org_id` → `cert_expire_time` → `cert_seq` → `sign_algorithm` → `encrypt_algorithm` → `parameter_id` → `publickey_length` → 公钥 hex（公钥入参是 Base64，先 `Base64.getDecoder().decode` 再转 hex）。**这个顺序就是契约，NEVER 重排。**
- `cert_index` **硬编码字符串 `"01"`**，没有读 `security.index`（配置里 `security.index` 恰好也是 `01`，**因此改配置不会生效** —— 属现状，改前 MUST 确认下游期望）。
- `getSign` 用 `securityConfig.getPrivateKey()` / `getPublicKey()`（配置 `security.privateKey` / `security.publicKey`）做 SM2 签名；固定用户 ID 字符串 `"1234567812345678"`；公钥按前 32 / 后 32 字节切成 X、Y；签名原文按 **`HexStringToByteArr(str)`**（当 hex 解析）转字节。
- **日志会打签名原文与签名结果 hex**（`log.info("签名数据：" + str + ...)`）；公钥也会 `log.info("公钥为" + publickey)`。属现状记录，**上生产前 MUST 评估是否降级为 debug**。

**2.「从加密机取证书」从未实现**（`SEC/service/impl/CertificateFromMachineServiceImpl.java`）
该文件原本 **153 行全部是 `//` 注释掉的整类代码**（含 `package` / `import` / `@Service` / 方法体），本轮**已整体删除**、文件只留一行迁移标记。删除前的关键事实，全部记在此处：
- 报文口径注释：「命令类型：D3 / 命令：02 / 算法标识：07 / 秘钥长度：0100 / 秘钥索引：0001 / 秘钥口令：0000000000000000」；秘钥标识由 `securityConfig.getIndex() + "00"` 拼出，秘钥口令直接用 `LocalCache.getSequence()`。
- 拼包顺序：`orderType` → `order` → `algorithmFlag` → `secretFlag` → `secretIndex` → `secretPassword`（与 `SEC/param/CertificateBean.java` 的字段注释「命令类型 / 命令 / 算法标识 / 秘钥标识 / 秘钥索引 / 秘钥口令」一一对应，**该 Bean 仍在、注释仍在**）。
- 应答解析：`resultData.substring(6, 223)` 当**私钥密文**（217 个 hex 字符），并注明「公钥明文X+Y」。
- **关键：核心方法 `getSM2SignFromMachine` 的方法体只有两行日志 + `return "";`** —— 即便当年解注释也拿不到签名。**因此「加密机侧证书签名」这条链路在本仓库从未存在过实现**，需要它 MUST 当新功能做（AGENTS.md §2.2.2 同型约束）。
- 另一半 `getSM2SignFromLocal` 依赖 `SM2EncDecUtils` / `SM2KeyVO` / `SM2SignVO` / `SM2SignVerUtils` 四个类，**这四个类在当前 `SEC/util/sm2/` 下都不存在** —— 解注释会直接编译失败。这是「为什么它被注释掉」的最可能原因，但**注释里没有任何说明，属推断、MUST 找原作者确认**。

**3. 联调脚本里有两处硬编码密钥对（只记位置，不回显值）**
- `SEC/service/Main.java`：`main` 方法里的公钥字面量、`getCertificateSign` 里的公钥 + **私钥**字面量；本轮**已删除**其中 4 行样例注释（两串证书报文样例、一串签名结果样例、标题「公钥证书-测试」），**代码里的密钥字面量一行未动**（AGENTS.md §5.2 安全红线：NEVER 擅自改加密/签名代码）。
- `SEC/util/sm2/Main.java`：`test1` / `test2` / `getSign` 三个方法各自带公钥 + 私钥字面量与签名样例。
- **这两个文件都是 `main` 方法脚本、零调用方**，但会被打进 jar。**MUST 处置**：连同下面第七节那 4 个配置项一起，把密钥搬到 K8s Secret，并对这两个脚本文件的存留做裁决（删除或移到 `src/test`）。**MUST 视这些字面量为已泄露、纳入轮换范围。**

**4. SM2 实现层的三条硬约束（都在跑，MUST 保留）**（`SEC/util/sm2/SM2.java`，【保留】）
- grep `某版本ios的sm2算法库有问题`：服务端**主动过滤**某版本 iOS SM2 库产出的畸形密钥。删掉这行等于让后人把过滤当成多余代码。
- grep `运行20次还不能生成一个有效的密钥对`：密钥对生成有 **20 次重试上限**，超限即失败而非死循环。
- grep `GNU General Public License` / `$Id: Utils.java`（`SEC/util/sm2/Utils.java:1~13`）：**GPL v3 许可头**（原文措辞是「GNU General Public License ... version 3」，**grep `GPL` 三个字母搜不到**），随代码分发前 MUST 做合规确认（阶段一墓碑清单第 18 条，本轮**未删**）。
- 本轮在该文件里删掉的是**注释掉的死代码**：两组 Base64 测试向量（公钥 / 签名值 / 原文）、一个注释掉的 `getHash(byte[], byte[])` 旧签名、两个注释掉的 `signBase64` 重载、一个注释掉的 `verifyBase64`，以及若干注释掉的中间变量行。**测试向量的值不在本节回显**（含密钥材料），需要复现 MUST 取 `svn cat -r 941`。

### 五、`ItpSignPubkeyController`（入向验签当前处于关闭状态）

文件 `SEC/controller/ItpSignPubkeyController.java`，类级 `@RequestMapping("/ci/itp")`，两个端点：
- `POST /ci/itp/requestSignPubkey` → 把 `publicKeyX` / `userId` / `publicKeyEffectiveDate` / `caPrivateKey` / `caPublicKey` / `caSm2KeyPair` 六个字段塞进 `Map<String,String>` 交给 `itpService.requestSignPubkey(map)`。
- `POST /ci/itp/requestSignInsData` → 传 `industryData` / `logicNum` 两个字段。

**1. 验签调用已被注释掉——这是事实，不是推断**（【保留】grep `if (!itpRequestSignVerifier.checkSign(request))`）
两个方法体开头各有一段被 `//` 注释掉的三行：`if (!itpRequestSignVerifier.checkSign(request)) { return ResultMapper.error("签名校验未通过"); }`。**这两段注释本轮刻意未删**（AGENTS.md §5.2 安全红线：它们本身就是被停用的签名逻辑，删掉等于抹掉「这里本该有验签」的唯一现场证据）。同时删除的是同文件那个注释掉的 `parseBizData(HttpServletRequest)` 私有方法（4 行，原文 `JSON.parseObject(request.getParameter("bizData"), Map.class)`）。
连带事实：`itpRequestSignVerifier` 仍然**通过构造器注入并持有**，只是没人调用 —— 编译器不会报警，IDE 也只会提示「字段未使用」。

**2. 恢复验签 NEVER 只是「解注释」**（本条是本节最重要的结论）
`ItpRequestSignVerifier.checkSign(HttpServletRequest)`（`SEC/itp/service/ItpRequestSignVerifier.java`，零注释）**全部取参走 `request.getParameter(...)`**：先取 `signType`，blank 直接 `return false`；`"00"` 免签直接 `return true`；`"01"` 走 `DigestUtils.sha1Hex`、`"02"` 走 `md5Hex`；签名原文 = 除 `sign` 外全部参数名升序 `k=v&` 拼接 + `key=<signKey>`（键 `itp.signKey`）。
而这两个端点**现在是 `@RequestBody` JSON 入参**（同文件还保留着一行注释掉的旧签名 `public ResultVO requestSignInsData(HttpServletRequest request)`，【保留】—— 它正是「入参形态被改过」的物证）。JSON 请求体下 `getParameter` 取不到任何值 ⇒ `signType` 为 blank ⇒ **`checkSign` 恒返 false ⇒ 解注释即所有请求都被判「签名校验未通过」**。恢复验签 MUST 同时决定：入参回退成 form-data + `bizData`，还是给 `checkSign` 另写一个读 JSON 体的重载（**后者属新增签名逻辑，MUST 人工复核 + 与调用方对齐**）。

**3. 当前实际暴露面**
- 该 Controller 两个端点 + `SEC/controller/ItpRemainingController.java` 五个端点（`requestCaKey` / `requestUserSm2Key` / `requestExportUserPriKey` / `requestDPK` / `requestHceCardData`）**共用同一个类级前缀 `/ci/itp`**，且后者**同样注入了 `ItpRequestSignVerifier` 却一次都没调用**（连注释掉的调用都没有）。
- 即：`/ci/itp/**` 七个端点**全部无鉴权**，其中包含「导出用户私钥」「导出 DPK」「生成 CA 密钥」。与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」直接冲突。**这是当前网络可达性兜底的（仅集群内 `service.security.url` 调用），不是设计上安全，上线前 MUST 关闭或补齐。**
- 阶段一已记「`itp.signKey` 在 `ItpRequestSignVerifier` 的 `@Value` 里带一个非空默认值」—— 即**即使 properties 不配也能算出签名**，见第七节。

### 六、IF7B 出向代理（acc-secure-server，当前未接线）

**1. 八条接口的形态**（`SECURE/controller/AccSecureController.java`，类级 `@RequestMapping("/ci/acc/secure")`，全部 Javadoc 【保留】）
`requestQrLogicNumList`（IF7B-01）/ `requestCaKey`（02）/ `requestUserSm2Key`（03）/ `requestSignPubkey`（04）/ `requestExportUserPriKey`（05）/ `requestSignInsData`（06）/ `requestDpk`（07）/ `requestHecCardDate`（08），一律 `@RequestBody` → 直接委托 `AccSecureService` 同名方法。**本模块 Javadoc 全是标准单行注释，本轮一行未删。**

**2. 出向调用的四段职责**（`SECURE/service/impl/AccSecureServiceImpl.java`，Javadoc 【保留】）
`post(path, bizData, responseClass)`：OkHttp（`JSON_MEDIA_TYPE = application/json; charset=utf-8`）→ `buildAccRequest` 组装 `ItpCommonRequest`（公共参数取 `acc.secure.provider-id` / `charset` / `format` / `device-id` / `sign-type`，签名走 `AccSecureSignUtils.buildSign`）→ `buildUrl` = `acc.secure.base-url` + `acc.secure.paths.*` → `parseResponse` **兼容「retCode 在外层」与「retCode 在 bizData 内」两种结构** → `normalizeBaseFields` 兜底补 `retCode` / `retMsg` → 出错走 `buildErrorResponse`。
**判据（阶段一已记，此处重申因为它决定调用方写法）：`post` 任何失败都不抛异常、只返回带错误码的响应对象。调用方 MUST 显式检查 `retCode`，NEVER 假定「没抛异常就是成功」**（同 AGENTS.md §5.2 那条 boolean 陷阱）。

**3. 接线前必然踩的两个 404（本轮新增实测结论）**
`acc.secure.paths.*` 里的两条路径与 acc-security-server 侧真实端点**对不上**：
- `acc.secure.paths.request-qr-logic-num-list=/ci/itp/requestQrLoigcNumList`（**`Loigc` 拼写颠倒**）—— 而 acc-security-server 的 `/ci/itp/**` 下**根本没有任何逻辑卡号端点**（IF7B-01 已迁至 `card-pool-server` 直连，见本文档上方）。
- `acc.secure.paths.request-hec-card-date=/ci/itp/requestHecCardDate` —— acc-security-server 侧实际是 **`requestHceCardData`**（`Hce` 不是 `Hec`、`Data` 不是 `Date`）。
其余六条（`requestCaKey` / `requestUserSm2Key` / `requestSignPubkey` / `requestExportUserPriKey` / `requestSignInsData` / `requestDPK`）与实际端点逐字一致。**注意 `requestDPK` 是全大写 DPK，acc-secure 侧方法名却是 `requestDpk`** —— 方法名与 URL 不同源，改任一处 MUST 对齐配置。
**因此「接通 acc-secure」不是改一个 `base-url` 就行**：至少要修这两条路径、并确认 IF7B-01 是否还需要（已迁走）。这两条在代码注释里**没有任何提示**，是本节的新增记录。

**4. 未接线的物证**（`acc-secure-server/src/main/resources/application.properties`）
`acc.secure.base-url=http://127.0.0.1:8080`（占位，K8s 内等于打到自己）；`acc.secure.sign-key=`（**空值**，签名算不出有效值）；`service.token.url=` 与 `service.route.mapping.default=` 也是空。`knife4j.production=true`（与 acc-security 的 `false` 相反）。

### 七、config 与 properties

**1. `acc-security-server/src/main/resources/application.properties`（32 行，全文零注释）**
- 服务：`server.port=9012`、`server.undertow.io-threads=10`、`server.undertow.worker-threads=10`、`spring.application.name=acc-security`。
- 日志：`logging.level.root=info`、`logging.level.com.chinasofti=debug`、`logging.file.path=logs/acc-security`。**注意 `com.chinasofti` 是 debug**，而第四节那几处日志会打签名原文 / 公钥。
- 文档：`knife4j.production=false`（**测试期口径，生产 MUST 改 true**；acc-secure 侧已是 true）。
- HSM 连接：`security.firstIp` / `security.firstPort` / `security.secondPort` / `security.connectOutTime` / `security.readOutTime` / `security.index`。**`firstIp` / `firstPort` 是裸硬编码的加密机内网地址与端口、没有 `${ENV:}` 包装**，判断线上真实值 MUST 查 Deployment env（AGENTS.md §8）。`security.secondPort` 与 `security.connectOutTime` **在代码里有 getter 但没有任何读取方**（`SecurityConfig` 只被 `SocketClient` / `ShortConnectClient` / `ClientSendMsg` / `CertificateServiceImpl` 用到，分别只读 `firstIp` / `firstPort` / `readOutTime` / `privateKey` / `publicKey`）—— 属预留双机字段，**NEVER 以为配了 `secondPort` 就有备机**。
- 线程池：`thread.corePoolSize=10` / `thread.maxPoolSize=10` / `thread.queueCapacity=99999` / `thread.socketNamePrefix=socket-connect`。`corePoolSize` **同时兼任「目标 HSM 连接数」**（第一节第 3 条），改它会连带改重连判据。
- **明文密钥项 4 个（只写键名与位置，值一律不回显）**：
  | 键名 | 位置 | 用途 | 现状 |
  |---|---|---|---|
  | `security.publicKey` | `acc-security-server/src/main/resources/application.properties:24` | 证书签名用 SM2 公钥（X\|\|Y） | 明文真值，无 `${ENV:}` |
  | `security.privateKey` | 同文件 `:25` | 证书签名用 SM2 **私钥** | 明文真值，无 `${ENV:}` |
  | `itp.signKey` | 同文件 `:31`；**另有一份非空默认值内联在 `SEC/itp/service/ItpRequestSignVerifier.java` 的 `@Value("${itp.signKey:...}")` 里** | `/ci/itp/**` 入向验签的共享密钥 | 明文真值，且**删掉配置也不会失效**（有代码内默认值兜底） |
  | `itp.tacKeyIdex` | 同文件 `:32`；默认值 `195` 内联在 `ItpServiceImpl` / `ItpRemainingService` / `ItpRequestService` 三处 `@Value` | TAC 次主密钥索引 | 明文；键名拼写 `Idex`，改名会静默回落默认值 |
  **MUST（AGENTS.md §5.2「敏感配置」）**：这 4 项一律改成 `${ENV_VAR:}`（**空默认值**）+ K8s Secret 注入，`ItpRequestSignVerifier` 里那个内联默认值 MUST 一并去掉（**留着它等于 Secret 没注入也照样能算签名，故障被掩盖**）。因为真值已随代码库长期存在（并且第四节第 3 条那两个脚本里还有另一对硬编码密钥对），**MUST 视为已泄露并安排轮换**，NEVER 只是改成环境变量了事。
- **`ItpConstants`（`SEC/itp/util/ItpConstants.java`）零注释**，`RET_SUCCESS="0000"` / `CHECK_FAILED_CODE="0001"` / `ERR_GETSOCKET_TIMEOUT="0101"` / `ERR_HEX_FORMAT="0202"` / `ERR_HEX_ODD="0203"` / `ERR_LENGTH="0204"` / `ERR_BYTE_IO="0205"` / `ERR_UNKNOW="9999"`，以及 `SIGN`/`SIGN_TYPE`/`NO_SIGN="00"`/`SHA1_SIGN="01"`/`MD5_SIGN="02"`。**这 8 个错误码常量当前没有任何读取方**（在跑的实现走 `ResultMapper.error("...")` 直接给文案），**NEVER 以为错误码已经按这套编号对外了**。

**2. 三个 config 类的现状**
- `SEC/config/ExecutorConfig.java`：`socketAsyncExecutor` 用 `VisiableThreadPoolTaskExecutor`（core/max 取 `thread.*`，队列 99999，前缀 `socket-connect`），拒绝策略 **`AbortPolicy`**（队列满即抛 `RejectedExecutionException`，**不是丢弃、也不是调用方执行**）；另有 `taskScheduler`（`poolSize=max(1, corePoolSize)`，前缀 `task-scheduler-`，`waitForTasksToCompleteOnShutdown=true`、`awaitTerminationSeconds=30`）。**【已删除】** 原有 7 行块注释，逐条解释 `AbortPolicy` / `DiscardPolicy` / `DiscardOldestPolicy` / `CallerRunsPolicy` 四种策略的语义 —— 那是 JDK 通识，**当前选的是 `AbortPolicy` 这一条事实已落在本行**。四个字段的行尾注释（「线程核心数」等）【保留】。
- `SEC/config/ValidateConfig.java`：【保留】grep `设置validator模式为快速失败返回` —— 参数校验**快速失败**，只报第一个错。
- `SEC/config/LocalCache.java`：**【已删除】** 那段 11 行注释掉的 `getInstance()` 双检锁（当前是 `private static volatile LocalCache instance = new LocalCache();` 直接初始化，构造器里 `new Thread(new TimeoutTimer()).start()`，**即类加载即起一条 while(true) 的清理线程、非守护、无法停**）。`// TODO: 2020/6/20` 与四条字段注释【保留】。
- `SEC/component/GlobalExceptionHandler.java`：当前只注册两个 handler —— `MethodArgumentNotValidException` 与 `HsmUnavailableException`（后者返 `ResultMapper.hsmUnavailable()`）。**【已删除】** `handle(ConstraintViolationException)` 上方那两行注释掉的 `@ExceptionHandler` / `@ResponseStatus(BAD_REQUEST)`，以及方法内三行注释掉的 `Map<String,String> errors` 旧写法。**因此该方法虽然存在却永不被调用**：`@Validated` 抛出的 `ConstraintViolationException` **当前没有任何处理器**，会落到框架默认 500。这是本轮删除注释时确认的行为事实，**要修 MUST 加回注解（属行为变更，先确认对上游影响）**。

**3. `acc-secure-server/src/main/resources/application.properties`（33 行）**
`server.port=9099`、`spring.application.name=acc-secure`、`knife4j.production=true`、`server.tomcat.basedir=./logs` + `logging.file.path=./logs`（**均为相对路径；阶段一提到的个人绝对目录已不在该文件里，NEVER 回退成「日志写死在个人目录」**）。`acc.secure.*` 共 14 键（6 个公共参数 + `sign-key` + 8 条路径 —— 其中 `provider-id=06`、`sign-type=00`、`device-id=ITP-ACC-SECURE`）。字段语义**唯一来源**是 `SECURE/config/AccSecureProperties.java` 的字段 Javadoc（【保留】，含「MD5 签名 key。」这条 —— **本域出向签名算法的唯一记录**）。

### 矛盾与待裁决

**四项 MUST 等外部证据（原作者 / 甲方规格 / 加密机侧密钥配置），本节只记事实，NEVER 代笔推断成结论。**

| # | 事项 | 代码里的实际取值（权威） | 与之冲突的说法 | 影响面 | 定案需要什么证据 |
|---|---|---|---|---|---|
| 1 | 充值 MAC1 的次主密钥索引 | **`00B6`**（`CommonMacServiceImpl.verifyMac1` 里 `byte[] bytes1 = {0x00, (byte) 0xB6};`，行内注释也写 `00B6`） | 已删除的块注释写 **`00BA`** | 索引错 ⇒ 加密机用错密钥 ⇒ **MAC1 校验恒不通过**、充值全失败；且 MAC2 用的确实是 `00B6`，若 MAC1 本该 `00BA` 则现状是「两步用同一把密钥」 | 加密机侧的次主密钥索引表（`00B6` / `00BA` 各是什么密钥），或甲方 MAC 计算规格 |
| 2 | CPU 卡 TAC 的分散数据前缀 | **`0532FF`**（`cpuTacVerify` 里 `"0532FF0000000000" + cardNo`，行内注释注明「青岛 0532」；`requestDPK` 与 `requestHceCardData` 也用 `0532`） | 已删除的块注释写 **`4500FF`** | 前缀参与密钥分散 ⇒ 错了就**分散出另一把密钥、TAC 恒校验失败**；三处在跑的代码一致用 `0532`，说明 `4500FF` 更可能是历史/他城口径 | 甲方城市代码约定（`0532` 电话区号 vs `4500` 是什么编码体系）+ ACC 侧分散规则 |
| 3 | 发售 / 退款 key 的分散数据前缀 | **`4500FF0000000000`**（`getSaleAndRefundKey`） | 同方法已删除的块注释写 **`4500000000000000`**（无 `FF`）；而同模块 TAC / DPK 侧用 `0532FF` | 同上；**并且这一处与第 2 条方向相反**（这里代码用 `4500`、那里代码用 `0532`），两条链路是否本该一致**无法从代码判断** | 同第 2 条；MUST 一次性把三处前缀的归属问清 |
| 4 | `/ci/itp/**` 入向验签 | **验签调用被注释掉、七个端点全裸**（`ItpSignPubkeyController` 两处 `checkSign` 在注释里，`ItpRemainingController` 连注释都没有） | 代码结构（注入了 verifier）暗示「本该验签」；`ItpRequestSignVerifier` 与 `itp.signKey` 都还在 | 「导出用户私钥 / 导出 DPK / 生成 CA 密钥」可被任意网络可达方调用；且**解注释即全部请求失败**（第五节第 2 条） | 原作者说明「为什么关」（联调期临时？调用方不支持？）+ 调用方（account-server / key-server / industry-data-server / fep-dev-server）当前是否具备签名能力 |

**另外 4 条本轮确认的矛盾（不属上面四项，但同样只记事实）**
5. `getMac2` 里那行行内注释写的是「MAC1 分散秘钥00B6」（**从 MAC1 复制过来、方法名没改**）—— 索引值本身对，措辞误导。
6. `TacBean.toString()` 输出的类名是 **`ShortConnectTacBean{`**（复制残留），按日志字符串定位类会找错文件。
7. `ItpRemainingService` 与 `ItpRequestService` 两个 `@Service` **零引用**（2026-09-16 全仓 grep 实测），与 `ItpServiceImpl` 是三份近似重复实现；它们的 Javadoc（「这里保留了 secret-web 中对应接口的报文组装和响应解析逻辑」「对应原项目 HSMService.getCaKey」等）写得像现行实现。**在跑的是 `ItpServiceImpl`（两个 Controller 都只注入 `ItpService`）**，改逻辑 MUST 改 `ItpServiceImpl`。本轮**未删**这两个类的注释（它们是 DPK 链路 `B061` / `72` 指令号的唯一记录）。
8. `CertificateServiceImpl.getSign` 的签名原文按 hex 解析（`HexStringToByteArr(str)`），而 `SEC/service/Main.java` 那份同型实现按 `str.getBytes()` 解析（该行现为注释掉的备选写法之一）。**同一套证书签名有两种原文口径**，脚本产出的样例签名与线上不一定可比。
9. **`acc-secure-server` 不在根 `pom.xml` 的 `<module>` 列表里**（2026-09-16 实测：根 pom 26 个 module 有 `acc-security-server`、**没有** `acc-secure-server`；`mvn -pl acc-secure-server` 直接报 `Could not find the selected project in the reactor: acc-secure-server`）。**本文档上方「但已在根 pom.xml 参与构建」这句是错的，NEVER 回退**。构建该模块 **MUST 进它自己的目录**：`cd acc-secure-server && mise exec -- mvn -o clean package -DskipTests`（或 `-f acc-secure-server/pom.xml`）。连带含义：**全仓 `mvn clean package` 根本不会编译它**，它的编译错误在聚合构建里发现不了。

### 墓碑清单

**A. 本轮删除的墓碑（原文已不在工作副本，MUST 用 `svn cat -r 941` 取）**

| # | 位置 | 墓碑内容 | 迁移落点 |
|---|---|---|---|
| 1 | `SEC/service/impl/CertificateFromMachineServiceImpl.java` | **整类 153 行被 `//` 注释掉**（含 `package`/`import`/`@Service`/两个方法体），且核心方法 `getSM2SignFromMachine` 只有 `return "";` | 本节第四节第 2 条（含报文口径、拼包顺序、`substring(6, 223)`、四个缺失类） |
| 2 | `SEC/service/impl/CommonMacServiceImpl.java` | MAC1 / MAC2 两段共 37 行报文口径块注释（含冲突的 `00BA`） | 第二节第 1、2 条 + 矛盾清单 1 |
| 3 | `SEC/service/impl/CommonTacServiceImpl.java` | UL / CPU 两段共 22 行 TAC 口径块注释（含 `001A`/`0016`、小端/大端、冲突的 `4500FF`） | 第三节第 1、2 条 + 矛盾清单 2 |
| 4 | `SEC/service/impl/CommonSaleAndRefundServiceImpl.java` | 7 行发售/退款报文口径块注释（含冲突的 `4500000000000000`） | 第二节第 3 条 + 矛盾清单 3 |
| 5 | `SEC/config/LocalCache.java` | 11 行注释掉的 `getInstance()` 双检锁 | 第七节第 2 条 |
| 6 | `SEC/util/TransformUtils.java` | 6 行注释掉的 hex 拼接旧实现（与 `Util:134` 那条「避免 O(n²) 拷贝」的改造留痕同源，**那条【保留】**） | 第七节（无独立条目，判据在 `Util:134` 的保留注释里） |
| 7 | `SEC/config/ExecutorConfig.java` | 7 行线程池拒绝策略科普块注释 | 第七节第 2 条（只留「当前选 `AbortPolicy`」这一事实） |
| 8 | `SEC/util/IdWorker.java` | 8 行雪花 ID 位运算科普块注释 + 2 行注释掉的 `System.out.printf` + 1 行注释掉的 `System.err.printf` + `//---------------测试---------------` | 位宽分配已在阶段一归档；本轮不重复（该类**零引用**） |
| 9 | `SEC/socketServer/NettyServer.java`、`NettyClient.java`、`SimpleServerHandler.java` | 「第1步/第2步/第3步/第4步」教学式叙述、`SO_BACKLOG` 科普、「可以在这里面写一套类似SpringMVC的框架」等 | **不迁**：三个类都是 `main` 方法 demo、零引用、与 HSM 链路无关（阶段一已归档其 pipeline 说明） |
| 10 | `SEC/component/GlobalExceptionHandler.java` | 2 行注释掉的 `@ExceptionHandler`/`@ResponseStatus` + 3 行注释掉的旧 `Map` 写法 | 第七节第 2 条（**含「`ConstraintViolationException` 当前无处理器」这个行为结论**） |
| 11 | `SEC/controller/ItpSignPubkeyController.java` | 4 行注释掉的 `parseBizData(HttpServletRequest)` | 第五节第 1 条 |
| 12 | `SEC/service/Main.java` | 4 行样例注释（两串证书报文样例、一串签名结果样例、「公钥证书-测试」标题） | 第四节第 3 条（**只记位置、不回显值**） |
| 13 | `SEC/util/sm2/SM2.java` | 两组 Base64 测试向量、注释掉的 `getHash(byte[],byte[])` 旧签名、两个注释掉的 `signBase64`、一个注释掉的 `verifyBase64`、若干注释掉的中间变量 | 第四节第 4 条（**测试向量含密钥材料、不回显**） |
| 14 | `SEC/util/sm2/Main.java`、`SEC/util/Util.java` | 1 行注释掉的 `genSM2KeyPair()` 调用；1 行注释掉的 `return hs.toUpperCase();` | 第四节第 3 条 |

**B. 保留的一行式护栏（紧贴加密/签名/MAC/TAC 计算、说明字节序或索引取值；AGENTS.md §5.2 安全红线，NEVER 删）**

| # | 位置（grep 短语） | 为什么必须留 |
|---|---|---|
| 1 | `CommonMacServiceImpl` — `MAC1 分散秘钥00B6`（**两处**：MAC1 与 MAC2 各一） | 索引取值；删了就只能靠猜 `0xB6` 是什么 |
| 2 | `CommonMacServiceImpl` — `分散数据 卡号`（两处） | 分散数据的语义来源 |
| 3 | `CommonMacServiceImpl` — `sessionKey 随机数4字节+2字节票卡计数器+0x8000`（两处） | 会话密钥的字节布局 |
| 4 | `CommonMacServiceImpl` — `mac数据拼接` / `mac数据长度` / `mac数据`（各两处） | 标出「哪几行在拼 MAC 数据体」，是大端假设的落脚点 |
| 5 | `CommonTacServiceImpl` — `ul卡 分散秘钥00C3` | 索引取值 |
| 6 | `CommonTacServiceImpl` — `ul 分散数据 = 逻辑卡号` | UL 分散数据**无前缀**这一事实 |
| 7 | `CommonTacServiceImpl` — `ul卡 分散秘钥00C1`（**注释写「ul卡」但方法是 CPU 卡**，措辞待修、值正确） | 索引取值 |
| 8 | `CommonTacServiceImpl` — `分散数据= 0532FF0000000000 + 逻辑卡号   青岛 0532` | **矛盾清单第 2 条的关键物证**，删了就无法证明代码选的是 `0532` |
| 9 | `CommonSaleAndRefundServiceImpl` — `MAC1 分散秘钥00B0` / `分散数据 卡号` | 索引取值 + 分散数据语义 |
| 10 | `CertificateServiceImpl` — `截取私钥` / `截取公钥` / `用户ID，使用固定值1234567812345678。` / `原文` / `计算哈希值` / `签名` | 公钥按 32+32 切分、固定用户 ID —— SM2 签名的三个硬约束 |
| 11 | `SM2.java` — `某版本ios的sm2算法库有问题` / `运行20次还不能生成一个有效的密钥对` | 密钥过滤与重试上限，属实现约束 |
| 12 | `util/sm2/Utils.java` — GPL v3 许可头 | 合规 |
| 13 | `Util.java:134` — `复用已验证的 encodeHex，避免循环字符串拼接的 O(n²) 拷贝` | 改造留痕，防止回退 |
| 14 | `ItpSignPubkeyController` — 两处 `if (!itpRequestSignVerifier.checkSign(request))` 与一处注释掉的 `HttpServletRequest` 方法签名 | **被停用的签名逻辑本身**；删了就抹掉「这里本该验签 + 入参形态被改过」的现场证据（矛盾清单第 4 条） |
| 15 | `MacBean` / `TacBean` / `SaleAndRefundBean` / `CertificateBean` 的全部字段 Javadoc | 逐字段就是报文字段表 |
| 16 | `Constant.java` 全部字段 Javadoc（帧结构 / 拆包参数 / 应答长度 / 操作码 / 超时） | 协议定义 |

### 覆盖率自评

- **抽取范围**：两模块 `src/main/java/**/*.java`（`acc-security` 121 个文件、`acc-secure` 25 个）+ 两个 `application.properties`。两模块**都没有** mapper XML、没有持久层、没有 `src/test`。
- **本轮抽取条数**：正文 7 节共 **31 条**（一 6 / 二 4 / 三 4 / 四 4 / 五 3 / 六 4 / 七 3 加二级要点若干）+ 矛盾 **9 条** + 墓碑删除 **14 组** + 保留护栏 **16 组**。四项待外部证据的事项**全部覆盖并写明了代码实际取值**。
- **实删量（按 `svn cat -r BASE` 逐文件比对）**：**17 个文件、385 条注释行**（物理行 386，多出 1 行是 `SM2.java` 里夹在注释掉的代码块中间的空行）。单文件最大是 `CertificateFromMachineServiceImpl.java`（152 条：153 条整类注释删除 + 1 行迁移标记）与 `SM2.java`（85 条）。`acc-security-server` 的注释总量由 **1891 → 1506**；`acc-secure-server` **318 条一条未动**。
- **「HSM 长连接核心类几乎零注释」的复核结论：判断成立，且比阶段一的措辞更严格。** 2026-09-16 逐文件实测：`ChannelCache`（70 行）、`ClientDecoder`（114 行）、`ClientSendMsg`（72 行）、`ClientHandler`（79 行）、`RawRequestContext` / `RawResponseSpec` / `RawResponseFieldSpec` / `RawSocketResponse` / `RawSocketResponseFuture`、`HsmUnavailableException` —— **注释数为 0**；`ClientEncoder` 与 `SocketClient` 只有作者 Javadoc（`@author houkepan` / `@date`）加两行一句话说明。因此：
  - **上面第一节第 2、4、5 条（连接池语义、两条应答回收路径、解码器魔数 `65`/`69`/`HEAD_LENGTH=8`、`dynamic` 长度取值规则、`size` 与 `cacheList.size()` 漂移）都是本轮读代码得出的、代码里从来没有过对应注释。** 它们是「代码事实」，不是「注释迁移」。
  - **这部分知识在代码里从来不存在，docs 也无法凭空补齐**：具体缺的是 —— ①为什么成功码是 `'A'`(65) / 失败码是 `'E'`(69)，错误码 `errCode` 那一字节的取值表；②`RawResponseSpec.reserved()` 何时该为 true（哪些指令带 8 字节保留域）；③两条应答路径为什么并存、旧路径是否计划下线；④`security.secondPort` 预留的双机方案；⑤`ContextHolder` 这个失效状态位当年想解决什么问题。**这些 MUST 找原作者（`@author houkepan`）或用加密机联调实测补齐，NEVER 由文档代笔编造。**
- **未覆盖 / 有意不迁**：`SEC/util/sm2/**` 的算法内部注释（SM2/SM3 标准实现，属通识 + GPL 来源，只迁了三条实现约束）；`Util` / `TransformUtils` / `NumberUtil` 的 `@param`/`@return` 标准 Javadoc（原地保留，未迁）；`feign/domain/**` 与 `itp/model/**` 的字段注释（阶段一已逐条归档，本轮不重复）；三个 `main` 方法 demo 类（`NettyServer` / `NettyClient` / `SimpleServerHandler`）的教学式注释**只删不迁**。
- **本轮不变量自证**：对每个改动文件用 Python 剥离全部注释（含字符串字面量保护）后与 `svn cat -r BASE` 的同样处理结果逐字节比对，**17 个文件全部一致** ⇒ 只删注释、**未动任何一行代码**（含全部加密/签名/MAC/TAC 逻辑）。改动集中在 `acc-security-server`（17 个 `.java`）；**`acc-secure-server` 一行未删**（它的注释全是标准单行 Javadoc）。
- **构建校验**：`mise exec -- mvn -o clean package -pl acc-security-server,acc-secure-server -am -DskipTests -Djkube.skip=true` **不可用** —— 报 `Could not find the selected project in the reactor: acc-secure-server`（成因见矛盾清单第 9 条）。实际拆成两条、**均 `BUILD SUCCESS`**：①`mise exec -- mvn -o clean package -pl acc-security-server -am -DskipTests -Djkube.skip=true`（产物 `acc-security-server/target/acc-security-server-2.0.14.jar`，输出里 `Pushed` / `digest:` 命中 **0** 条，确认 `-Djkube.skip=true` 挡住了该模块 `package` 阶段的推镜像）；②`cd acc-secure-server && mise exec -- mvn -o clean package -DskipTests -Djkube.skip=true`（产物 `acc-secure-server-2.0.6.jar`）。**判成败看 `BUILD SUCCESS` / `[ERROR]`，NEVER 看退出码** —— 第一次那条失败命令经管道后 shell 退出码仍是 `0`（AGENTS.md §7 那条陷阱本轮再次复现）。













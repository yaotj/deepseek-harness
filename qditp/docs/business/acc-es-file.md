---
业务域: ACC 编码设备（ES）管理与文件传输
模块: acc-es-server
---

# 提示词：ACC 编码设备与文件传输

## 何时读本文件
编码设备（ES）节点管理、任务计划/发行/预分配/注销/回收/个性化任务下发、报告回收与解析、Excel 导入、以及 **任何"文件传输"相关需求**。

## 模块定位
`acc-es-server`，`spring.application.name=acc-es`
- HTTP 端口 **9011**
- Netty TCP 端口 **5000**（`netty.port=5000`）
- 启动类 `acc-es-server/.../EsServerApplication.java`，`CommandLineRunner` 中 `nettyServer.start()`

**这是全仓唯一实现文件传输和二进制设备报文的模块。**

## HTTP 接口
| Controller | 前缀 | 接口 |
|---|---|---|
| `EsInfoController` | `/es` | `/all` `/save` `/update` `/page` `/delete` |
| `EsTaskController` | `/task` | `/1/save` `/1/update` `/1/delete` `/1/page`、`/2/save` `/2/update` `/2/delete` `/2/page`、`/custom/page` `/custom/save` |
| `TaskPlanController` | `/plan` | `/save` `/page` `/update` `/delete` `/approve` `/available` `/stat` |
| `EsAssignController` | `/assign` | `/save`、`GET /{taskNo}/{esCode}`、`PUT /{taskNo}/{esCode}` |
| `EsProcController` | `/proc` | `/page` |
| `EsReportController` | `/report` | `/page` |
| `EsAccountController` | `/account` | `/page` `/save` `/update` `/delete` |
| `FileController` | `/file` | `POST /upload`（Excel 导入）、`GET /download` |

## Netty 设备报文（真实报文码）
`netty/service/NettyServerHandler.java` 按 `txnType` 分发：

| 报文码 | 含义 | 处理 |
|---|---|---|
| `7000` | 设备签到 | `businessHandler.deviceSignIn` |
| `7002` | 任务申请 | `businessHandler.taskApply` |
| `7003` | 设备签退 | `businessHandler.deviceSignOut` |
| `7004` | 设备工作任务报告 | `businessHandler.taskStatReport` |
| `7005` | 设备状态报告 | `businessHandler.deviceStat` |

报文常量、MAC 应答码、长度定义：`netty/model/Constant.java`
任务分类分支（`PUBLISH` / `PRE_ASSIGN` / `HAND_CANCEL` / `CANCEL` / `SORT` / `RECODE` / `CUSTOM`）：`netty/service/BusinessHandler.java`

⚠️ **认知纠正**：技术规范第 4 部分的 `0x2001~0x9004` 系统级消息码在本仓**没有任何实现**。
只有上表 `7000~7005` 是真实存在的。接到"系统级报文"需求 **MUST** 先与用户确认是新建功能。

## 文件传输实现（重要事实）
- 协议：**明文 FTP**（Apache Commons Net `FTPClient`），**不是 SFTP**。工具类 `acc-es-server/.../util/FTP.java`（`getFTPClient` / `downLoadFTP` / `uploadFile` / `copyFile` / `moveFile` / `deleteByFolder` / `readFileByFolder`），BINARY 模式；另有 hutool `Ftp` 封装。
- 配置（`acc-es-server/src/main/resources/application.properties`）：`ftp.ip/port/username/password`、`report-target-dir=upload/`、`custom-target-dir=download/`、`report-local-dir`、`custom-local-dir`、`excel-dir`。端口固定 21，口令走环境变量。
- 下行（下载报告并解析入库）：`service/impl/EsReportServiceImpl.java` 的 `analysisFile` / `analysisCustomFile` —— `ftp.download(...)` 后按行读取，`substring(0,2)` 取文件标识 / 记录标识，逐条落库并写 `TBL_TKT_ES_FILE_PROC_LOG`
- 上行（个性化任务文件上传）：`service/impl/EsTaskServiceImpl.java` → `ftp.upload(customTargetDir, file)`
- 文件格式：**定长 / 前缀标识的纯文本行文件**。文件名前缀常量在 `Constant.FileMessage`：`FILE_FOLDER="/UPLOAD"`、`9050` 任务文件、`9060` 个性化任务文件、`9061` 个性化任务报告。`9050` 同时作为 `TBL_TKT_ES_PROC.actionCode` 落库。
- **没有定时任务**：文件处理由 Netty 报文（7002/7004）与 HTTP 接口触发，非定时扫描。全模块只有 `@EnableAsync`，无 `@Scheduled`、无 MQ。

## ⚠️ 对账与逻辑卡号均不在本模块
本模块的 FTP 只做**编码设备任务与报告文件**，与 ACC 对账和逻辑卡号文件**彻底无关**。
`docs/接口规范文档/ACC与ITP之间的文件.docx` 描述的两类文件接口已由其他模块落地：

- **ITP → ACC 对账文件（EXP / PAY / BUS / DETAIL）** → `recon-server` + 三个源服务（gate-txn-pay / collect-pay / daily-ticket），详见 `docs/business/recon.md`
- **ACC → ITP 逻辑卡号文件** → `card-pool-server`，IF7B-01 直连 ACC 申请批次 + FTP 下载卡号文件 + 入库卡池，详见 `docs/business/card-pool.md`

接到这两类需求 **MUST** 跳到对应模块的提示词文档，**NEVER** 在本模块找对账或逻辑卡号代码。

另有一条与 ACC 参数下发相关的**本地文件导入**通路（非 FTP），在 para-server：
`para-server/.../service/ParaFileImportService.java`（22 字节文件头，`paraType` 0001 路网 / 0002 日历 / 0003 票卡 / 0004 费率），入口 `/para/import/directory`、`/para/import/file`。详见 `common-services.md`。

## 编码约束
- 新增文件解析 **MUST** 沿用 `substring` 定长切分 + `TBL_TKT_ES_FILE_PROC_LOG` 记录处理日志的模式
- FTP 明文传输属安全弱点，改动传输逻辑时 **SHOULD** 向用户提出改 SFTP 的建议，但 **NEVER** 擅自替换协议
- Netty 报文长度与 MAC 定义 **MUST** 只改 `Constant.java`，**NEVER** 在 handler 内散落魔法数

## 参考原始文档
- `docs/接口规范文档/ACC与ITP之间的文件.docx`
- 技术规范第 4 部分（`0x2001~0x9004` 报文码）：**源文档不在仓库内**，需向甲方索取《城市轨道交通自动售检票系统技术规范-第4部分-系统接口规范》。仓库仅有第 9 部分。

## 附：acc-es-server 源码注释知识抽取（2026-09-16，阶段一）

> **本节所有行号有并发写入者，引用行号前 MUST 先 grep 现查**（抽取时点 2026-09-16）。
> 本节只收录**源码注释里真实写着的内容**。注释里没有的判据一律标注「本模块注释无此记载」，**NEVER** 据本节推断代码行为。
> 抽取范围：`acc-es-server/src/main/java/**/*.java`（222 个文件）+ `src/main/resources/mapper/*.xml`（13 个）+ `src/main/resources/application.properties` + `src/main/resources/sql/*.sql`（2 个）。

### 一、Netty 报文与编解码（7000~7005）

**契约与判据**

- 包头字段与字节长度（逐条来自字段注释）：`包长度 4` / `消息类型 4` / `发起/接收方标识码 8` / `会话流水号 9` / `文件交易  0：无文件；1：有文件` / 请求应答标识 / `1  md5  0：不加密；1：MD5加密` / `应答码默认两个空格`。定位：`acc-es-server` `Messagehead`（字段注释）`acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Messagehead.java:14,19,24,29,34,39,44,49`。
- 长度常量的注释口径：`包长度字节` 4、`包头长度字节` 26、`7000设备签到报文长度（不包含长度字段，包头 + 包体 + mac检验码）` 76、`7000设备签到报文体长度` 50、`7000设备签到报文MAC报文体长度` 8。定位：`acc-es-server` `Constant.DataLength` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:96,100,104,108,112`。
- `DATA_BODY_7002_TASK_BYTES = 104`（7002 单条任务体）**没有任何注释**，语义只能从常量名读。定位：`acc-es-server` `Constant.DataLength` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:116`。
- 报文码注释只写到「交易报文」四个字：`7000交易报文` / `7002交易报文` / `7003交易报文` / `7004交易报文` / `7005交易报文`，另有 `包头请求应答标识-应答`。**注释里没有写每个码对应哪笔业务**；业务含义只在 `switch` 分支代码上，且只有一个分支带注释「设备工作任务报告」（7004）。定位：`acc-es-server` `Constant.DataPackage` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:23,27,31,35,39,43`；`acc-es-server` `NettyServerHandler.channelRead` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/NettyServerHandler.java:46`。
- mack 应答码语义（注释原文）：`正常-00` / `报文格式错误`（常量名 `MD5_ERROR`）/ `无效的消息分类/类型码` / `无效的数值范围`（常量名 `WORK_ERROR`）/ `无效的节点标识码` / `无效的操作员` / `操作员密码错误`。定位：`acc-es-server` `Constant.MackStatus` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:53,58,61,66,70,75,81`。
- `INVALID_FILE_NAME=10` / `FILE_NOT_EXIT=11` / `FILE_GET_ERROR=12` / `OTHER=FF` 四个应答码**无注释**。定位：`acc-es-server` `Constant.MackStatus` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:84,86,88,90`。
- 拆帧参数注释：`最大长度`（1024*1024）/ `最小长度`（4）/ `长度偏移`（0）/ `长度字段所占的字节数`（4）/ `消息尾，结束标识长度`（`LENGTH_ADJUSTMENT`，值 0）/ `忽略字节`（`INITIAL_BYTES_TO_STRIP`，值 0）。定位：`acc-es-server` `Constant.NETTY_LENGTH` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:122,126,130,134,138,142`。
- `LengthFieldBasedFrameDecoder` 六参语义与算式写在构造器注释里，含 `@param byteOrder 大小端  ByteOrder.LITTLE_ENDIAN`、`lengthAdjustment  = 数据包长度 - lengthFieldOffset - lengthFieldLength  - 长度域的值(满足发送条件)`、`failFast 默认为true，当frame长度超过maxFrameLength时立即报TooLongFrameException异常，为false，读取完整个帧再报异`。定位：`acc-es-server` `ServerDecoder`（构造器注释）`acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/coder/ServerDecoder.java:18,22-23,25`。
- 7000 签到请求体字段：`节点标识码` / `操作员编码5位后补空格` / `操作员密码`；方向注释 `7000 string转byte`、`7000 byte转string`、`报文头`。定位：`acc-es-server` `DeviceSignIn.toDeviceSignInStr` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/DeviceSignIn.java:18,22,26,32,45,51`。
- 7000 签到应答体：`单位(秒),数值为0时不需要状态报告，长度为6`（设备状态报告间隔）。定位：`acc-es-server` `DeviceSignInMack.toBytesArray` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/DeviceSignInMack.java:18`。
- 7005 设备状态报告体切分（注释逐段给长度）：编码分拣机标识 / 操作员编号 / 动作码 / 任务标识号 / `设备工作状态 0 正常 1暂停 3故障` / 任务数量 / 完成数量 / `任务起始时间 14` / `任务结束时间 14` / `保留 8`。定位：`acc-es-server` `DeviceStatReport.message2DeviceStatReport` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/DeviceStatReport.java:57,61,65,69,73,77,81,85,89,93`。
- 设备状态取值另有一处写法 `0:正常 1，暂停 3，故障`。定位：`acc-es-server` `EsStatReport`（字段注释）`acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/EsStatReport.java:23`。
- 7004 任务报告体切分（注释逐段给长度，七个动作码分支各写一遍）：`任务标识号 8` / `任务执行结果 1` / `编码分拣机 8` / `操作员编号 10` / `车票类型 3` / `版本号 6` / `批次标识 10` / `起始序列号 10` / `任务数量 10` / `完成数量 10` / `废票数量 10`（部分分支写 `失败数量 10`）/ `任务起始时间 14` / `任务结束时间 14` / `任务报告文件名 20` / `保留 9`；分支头部另有裸数字注释 `143` / `9` / `134`（无文字说明）。定位：`acc-es-server` `EsTaskReport.message2TaskReport` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/EsTaskReport.java:36,37,38,42,46,47,51,55,59,63,67,74,78,82,86,90,94,98`（108~471 为其余分支的同型重复）。
- 7002 任务下发应答体：`工作任务状态 1` / `任务数 2` / `后续任务 1` / `工作任务`；七类任务体逐段注释，**保留段长度各不相同** —— 预赋值 `保留 25`、缴销 `保留 67`、注销 `保留41`、分拣 `保留 6`、个性化 `保留 41`，个性化另有 `个性化任务文件名`。定位：`acc-es-server` `TaskApplyMack.toBytesArray` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/TaskApplyMack.java:18,22,26,30,51,182,212,254,316,435,440`。

**决策理由**

- 不复用父类拆帧结果、整帧自行解析：注释原文 `在这里调用父类的方法,实现指得到想要的部分,在这里全部都要,也可以只要body部分`，紧随其后的 `super.decode(ctx, in)` 被注释掉。定位：`acc-es-server` `ServerDecoder.decode` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/coder/ServerDecoder.java:34-35`。
- Netty 服务端为单例、只装配一次：注释原文 `服务端基本配置，通过一个静态单例类，保证启动时候只被加载一次`；两个 EventLoopGroup 的分工注释 `用于处理服务器端接收客户端连接` / `进行网络通信（读写）`，`ServerBootstrap` 注释 `辅助工具类，用于服务器通道的一系列配置`，另有 `关闭服务器方法`（`@PreDestroy`）。定位：`acc-es-server` `NettyServer` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/NettyServer.java:19,35,40,44,96`。
- 大小端两套转换并存（int / short 各有大端、小端版本），注释逐个标注：`int转换为小端byte[]（高位放在高地址中）`、`int转换为大端byte[]（低放在高地址中）`、`short转换为小端byte[]（高位放在高地址中）`、`short转换为大端byte[]（低位放在高地址中）`。定位：`acc-es-server` `AlgorithmUtils` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/AlgorithmUtils.java:61,80,225,240`。
- 7004 报告的处理目的写在方法体内块注释里：`打印机执行任务后，给出报告，根据报告去修改相应的信息`。定位：`acc-es-server` `BusinessHandler.taskStatReport` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/BusinessHandler.java:130-132`。

**陷阱（只在运行时暴露）**

- `AlgorithmUtils` 的两处 `@note` 与方法语义相反：`bytes2IntBig` 的注释写 `数组长度至少为4，按小端方式转换，即传入的bytes是大端的，按这个规律组织成int`（前半句「小端」与方法名及后半句「大端」自相矛盾）；`bytes2CharBig` 同型，注释写 `数组长度至少为2，按小端方式转换`。**照注释判断字节序会取反**。定位：`acc-es-server` `AlgorithmUtils.bytes2IntBig` / `AlgorithmUtils.bytes2CharBig` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/AlgorithmUtils.java:119,151`。
- 请求 / 应答标识的注释两个取值都写成 `1`：原文 `1：请求消息；1：应答消息`。同类信息在 `Constant.DataPackage.RESULT_TYPE` 只标了「应答」一个值，**请求侧取值在注释里查不到**。定位：`acc-es-server` `Messagehead.requestType` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Messagehead.java:39`；`acc-es-server` `Constant.DataPackage` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:43`。
- 7000 报文长度常量的两处注释互相矛盾：Javadoc 写 `不包含长度字段，包头 + 包体 + mac检验码`，同一常量行尾却写 `//不包括mac108`。定位：`acc-es-server` `Constant.DataLength.DATA_7000_SIGN_BYTES` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:104,106`。
- 包体长度算式里 `- Constant.DataLength.DATA_MD5_BYTES` 被注释掉，现算式只减包头 26。**改回扣 MD5 长度会整段错位**，且注释没写为什么去掉。定位：`acc-es-server` `ServerDecoder.decode` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/coder/ServerDecoder.java:75`。
- 异常处理刻意不上抛父类：`super.exceptionCaught(ctx, cause);` 被注释掉，现为记两行 ERROR 日志 + `ctx.close()`。定位：`acc-es-server` `NettyServerHandler.exceptionCaught` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/NettyServerHandler.java:82`。
- **本模块注释无此记载**：`netty.port=5000` 是监听端口、`7000~7005` 是报文码这条判据，源码与配置文件的注释里**一个字都没有**；权威出处在 `AGENTS.md` §4 与本文件正文 §Netty 设备报文。

### 二、编码设备任务

**契约与判据**

- 任务类型码值↔中文名，唯一带注释的一份在 `TaskType`：`票卡发行`=1 / `票卡预赋值`=2 / `缴销`=3 / `注销`=4 / `分拣`=5 / `重编码`=6 / `个性化`=7。定位：`acc-es-server` `TaskType` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/TaskType.java:10,14,18,22,26,30,34`。
- 同一套码值在 `TaskClassification` 里**没有 Javadoc**，但文件尾部有整段被注释掉的枚举定义，写着 `PUBLISH("1","发行")` / `ASSIGN("2","赋值")` / `HARD_CANCEL("3","缴销")` / `CANCEL("4","注销")` / `SORT("5","分拣")` / `RECODE("6","重编码")` / `CUSTOM("7","车票个性化")`。注意该注释里的 `HARD_CANCEL` 与在用常量名 `HAND_CANCEL` 不一致、`ASSIGN` 与在用常量名 `PRE_ASSIGN` 不一致。定位：`acc-es-server` `TaskClassification` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/TaskClassification.java:21-27`。
- 接口注释把任务接口切成两组，**MUST 按这条分辨方法归属**：`保存任务 (发行，预赋值)` / `根据taskNO查询任务 (发行，预赋值)` / `更新任务 (发行，预赋值)` / `通过taskNo删除任务 (发行，预赋值)` / `分页查询任务 (发行，预赋值)` 与 `保存任务 （用于缴销/重编码/注销）` / `删除任务 （用于缴销/重编码/注销）`。定位：`acc-es-server` `IEsTaskService` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/IEsTaskService.java:13,21,29,37,46,56,64`。
- 报告处理链路的语义写在接口注释：`打印机执行任务后发送任务报告，根据任务报告，修改任务的信息，并保存卡信息到数据库中`；另有 `通过es节点编号和日期获取任务`、`根据任务No获取当前任务的类型`、`获取个性化任务`、`保存个性化任务`、`产生对应的任务文件并上传到ftp服务器`。定位：`acc-es-server` `IEsTaskService` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/IEsTaskService.java:88,97,105,112,121,129`。
- 计划审批分两条口径：`审批（发行和预赋值）` 与 `同意拒绝（缴销，重编码，注销）`，另有 `拆分任务`。定位：`acc-es-server` `ITaskPlanService` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/ITaskPlanService.java:43,51,59`。
- 可分配计划的筛选口径：`查询可以分配任务的计划（已经审核或者审批中）`。定位：`acc-es-server` `TaskPlanController` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/TaskPlanController.java:77`。
- 操作员级别取值：`根据账号密码获取账户的类型  1，操作员   2，管理员`（该值经 7000 应答的 `operatorRank` 回给设备）；实体侧另有一份同义注释 `操作员：1；管理员2`。定位：`acc-es-server` `IEsAccountService.getEsUserType` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/IEsAccountService.java:12`；`acc-es-server` `TblTktEsAccount.userType` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsAccount.java:27`。
- 拆分任务的三步与校验点写在实现里：`校验拆分任务数量不能超过计划数量` / `更新计划已分配数量和计划状态` / `保存拆分任务`；删除分支两处 `删除任务对应的分配记录`；报告分支多处 `更新任务执行结果` 与 `保存任务报告和文件处理记录`。定位：`acc-es-server` `EsTaskServiceImpl` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsTaskServiceImpl.java:77,89,92,139,180,223,225,236,247,258`。
- 报告文件记录布局（第一组，注释逐段给长度）：`记录标识 2` / `票卡逻辑号 16` / `原票卡逻辑号 16` / `CSN 16` / `票面号 16` / `初始金额 9` / `初始奖励金额 9` / `有效天数 6` / `有效期开始日期 8` / `押金 9` / `充值次数 9` / `消费次数 9` / `发行序号 10`。定位：`acc-es-server` `EsReportServiceImpl` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsReportServiceImpl.java:136,138,140,142,144,146,148,150,152,154,156,158,160`。
- 报告文件记录布局（第二组，**同名字段但注释里不带长度**，且字段集与第一组不同：多 `序号`、`物理卡号`，无 `原票卡逻辑号` / `CSN`）：`记录标识` / `序号` / `票卡逻辑号` / `物理卡号` / `票面号` / `初始金额` / `初始奖励金额` / `有效天数` / `有效期开始日期` / `押金` / `充值次数` / `消费次数` / `发行序号`。定位：`acc-es-server` `EsReportServiceImpl` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsReportServiceImpl.java:278,280,282,284,286,288,290,292,294,296,298,300,302`。
- 个性化任务导入的 Excel 列语义：`物理卡号 16 前补0` / `0:普通乘客` `1：地铁员工` / `姓名` / `证据按类型`（原文错别字，实为「证件类型」）/ `32位 后补零`（证件代码）/ `工作单位`。定位：`acc-es-server` `CustomTask` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/CustomTask.java:13,19-20,26,32,38,47`。
- 持卡人类型取值另有一份带注释的枚举：`普通乘客`=0 / `地铁员工`=1，字段注释 `类型值` / `类型名称`，方法注释 `根据名称获取typeValue的值`。定位：`acc-es-server` `PersonTypeEnum` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/PersonTypeEnum.java:12,18,21,25,30`。
- **状态机取值不在注释里**：`TaskExecuteStat`（0/1/2/3/9）、`TaskAssignStat`（0/1）、`TaskApplyStat`（0/1/2）、`PlanStat`（01~05 与 5/6 两套并存）、`EsStat`（0/1/3）、`LoginStat`（1/0）、`TicketType`（01/02）都靠**枚举字面量**表达，注释只有类级一句（`Description:设备状态`、`签到`）。**排查状态取值 MUST 读枚举常量，NEVER 指望注释**。定位：`acc-es-server` `EsStat` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/EsStat.java:4`；`acc-es-server` `LoginStat` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/LoginStat.java:4`。
- `MyDecoderState` 两个状态带注释 `未读头部` / `未读内容`（枚举名为 `READ_LENGTH` / `READ_CONTENT`，**注释语义与枚举名相反**）。定位：`acc-es-server` `MyDecoderState` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/MyDecoderState.java:5,10`。

**决策理由**

- 分拣任务分支刻意不落报告：`taskApply` 的 `SORT` 分支只有一行注释 `//不做`；对应的 `esTaskMapper.reportSortTaskReport(sortTaskReport)` 在报告处理里被整段注释掉。定位：`acc-es-server` `BusinessHandler.taskApply` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/BusinessHandler.java:283`；`acc-es-server` `EsTaskServiceImpl.report` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsTaskServiceImpl.java:267-268`。
- 报告处理不回写票卡 / 账户主表：`TblStlAcctInfoMapper.updateByPrimaryKey` 与 `TblStlTicketInfoMapper.updateByPrimaryKey` 两段批量更新被整段注释掉，注释里没写原因。定位：`acc-es-server` `EsReportServiceImpl` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsReportServiceImpl.java:336-343`。
- 文件处理结果批量落库的意图：`批量记录文件处理结果` / `保存文件处理日志`。定位：`acc-es-server` `EsReportServiceImpl` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsReportServiceImpl.java:383,397`。
- 签到时先更新登录状态：`更新设备登录状态`。定位：`acc-es-server` `EsInfoServerImpl.esSignIn` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsInfoServerImpl.java:41`。

**陷阱（只在运行时暴露）**

- `CustomTask.id` 注释写 `32位 后补零`，而生成任务文件时该字段走的是**后补空格**（`addSpaceRight(person.getId(), 32)`）；本模块的 `addZeroForNum` 是**左补 0**（其内部注释 `//左补0`）。**照注释填充会与设备解析错位**。定位：`acc-es-server` `CustomTask.id` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/CustomTask.java:38`；`acc-es-server` `ByteConvertUtil.addZeroForNum` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/ByteConvertUtil.java:101`。
- MD5 工具的用法反直觉：`加密解密算法 执行一次加密，两次解密`。定位：`acc-es-server` `Md5Util` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/Md5Util.java:54`。
- 类名 `DeviceSignInMack`（在用，7000 应答体）与 `DeviceSignlnMac`（`toByteByArray()` 直接 `return null`）**两个类并存**，注释分别是 `消息签到应答` 与 `设备签到应答`，`@program` 一个写 `cloud-acc-server`、一个写 `spring-cloud-acc`。**按注释无法分辨哪个在用**。定位：`acc-es-server` `DeviceSignInMack` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/DeviceSignInMack.java:10`；`acc-es-server` `DeviceSignlnMac` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/DeviceSignlnMac.java:8`。

### 三、FTP 文件传输

**契约与判据**

- 报文里携带的文件名字段：`个性化任务文件名`（7002 下发的个性化任务体）、`任务报告文件名 20`（7004 报告体）。定位：`acc-es-server` `TicketCustomTask.customFileName` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/TicketCustomTask.java:46`；`acc-es-server` `TaskApplyMack.toBytesArray` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/TaskApplyMack.java:435`；`acc-es-server` `EsTaskReport.message2TaskReport` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/data/EsTaskReport.java:94`。
- 出向文件的产生与投递语义：`产生对应的任务文件并上传到ftp服务器`（接口注释）、`上传并解析个性化任务文件`（实现注释）。定位：`acc-es-server` `IEsTaskService.customFile` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/IEsTaskService.java:129`；`acc-es-server` `EsTaskServiceImpl.saveCustom` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsTaskServiceImpl.java:308`。
- FTP 工具方法的职责边界（类内 Javadoc）：`获取FTPClient对象` / `关闭FTP方法` / `下载FTP下指定文件` / `FTP文件上传工具类` / `FPT上文件的复制` / `实现文件的移动，这里做的是一个文件夹下的所有内容移动到新的文件，如果要做指定文件移动，加个判断判断文件名` / `如果不需要移动，只是需要文件重命名，可以使用ftp.rename(oleName,newName)` / `删除FTP上指定文件夹下文件及其子文件方法，添加了对中文目录的支持` / `遍历解析文件夹下所有文件`。定位：`acc-es-server` `FTP` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:19,59,86,133,180,233-235,274,315`。
- 解析结果的形态：`这里就把一个txt文件完整解析成了个字符串，就可以调用实际需要操作的方法`；筛选口径 `判断为txt文件则解析`、`判断为文件夹，递归`。定位：`acc-es-server` `FTP` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:332,351,355`。
- **本模块注释无此记载**：`Constant.FileMessage` 的 `FILE_FOLDER = "/UPLOAD"`、`FILE_NAME_TASK_PREFIX = "9050"`、`FILE_NAME_CUSTOM_PREFIX = "9060"`、`FILE_NAME_CUSTOM_REPORT_PREFIX = "9061"` 四个常量**一条注释都没有**；`FtpComponent` 的 9 个配置字段（`ip` / `port` / `username` / `password` / `reportTargetDir` / `reportLocalDir` / `excelDir` / `customLocalDir` / `customTargetDir`）**也全部无注释**，类上只有 `@author`。因此**「FTP 目录与文件命名约定」在本模块注释里不存在**，只能读常量与拼接代码。定位：`acc-es-server` `Constant.FileMessage` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Constant.java:8-17`；`acc-es-server` `FtpComponent` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/config/FtpComponent.java:13-32`。

**决策理由**

- 下载后是否删源文件留给项目决定：注释 `下载成功删除文件,看项目需求`，紧随其后的 `ftp.deleteFile(...)` 被注释掉。定位：`acc-es-server` `FTP.downloadFile` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:113-114`。
- 连接参数的取值理由：`设置连接超时时间,5000毫秒`、`设置PassiveMode传输`。定位：`acc-es-server` `FTP` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:37,144`。
- 目标目录不存在即创建，是有意设计：`判断FPT目标文件夹时候存在不存在则创建`、`新文件夹不存在则创建`、`创建新目录`、`回到原有工作目录`。定位：`acc-es-server` `FTP` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:148,208,250,254`。

**陷阱（只在运行时暴露）**

- 传输模式：`设置二进制传输，使用BINARY_FILE_TYPE，ASC容易造成文件损坏`。**用 ASCII 模式不会报错、只会得到损坏文件**。定位：`acc-es-server` `FTP.uploadFile` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:146`。
- 编码集：`设置中文编码集，防止中文乱码`；下载侧 `绑定输出流下载文件,需要设置编码集，不然可能出现文件为空的情况`。**漏设编码集的症状是「文件为空」而非报错**。定位：`acc-es-server` `FTP.getFTPClient` / `FTP.downloadFile` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:39,111`。
- 复制链路必须显式设连接模式：`设置连接模式，不设置会获取为空`。定位：`acc-es-server` `FTP.copyFile` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:194`。
- 流未释放会 NPE：`ftp.retrieveFileStream使用了流，需要释放一下，不然会返回空指针`。定位：`acc-es-server` `FTP.parseFile` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:349`。
- 递归删除的边界只在注释里：`判断为文件则删除` / `判断是文件夹` / `递归删除子文件夹` / `循环完成后删除文件夹`。定位：`acc-es-server` `FTP.deleteFile` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:287,291,294,298`。

### 四、表结构与字段长度

**契约与判据**

- 建表脚本的适用范围与执行环境（脚本头注释原文）：`acc-es-server 建表脚本（AFCITPDB / QDITP）`、`执行环境：Oracle 19c，schema QDITP`。定位：`acc-es-server` `acc-es-server-schema.sql` `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:2,20`。
- 13 张表与中文名（逐表分节注释）：`1. TBL_TKT_TASK_PLAN — 制票任务计划` / `2. TBL_TKT_ES_TASK — 编码设备制票任务` / `3. TBL_TKT_ES_ASSIGN — 任务分配到编码设备` / `4. TBL_TKT_ES_INFO — 编码设备台账` / `5. TBL_TKT_ES_ACCOUNT — 编码设备操作员账号` / `6. TBL_TKT_ES_REPORT — ES 任务报告（日志中报错表之一）` / `7. TBL_TKT_ES_PROC — ES 报告文件处理结果（日志中报错表之一）` / `8. TBL_TKT_ES_FILE_PROC_LOG — 文件逐条处理明细` / `9. TBL_TKT_PRE_PERSON — 记名卡预登记人员` / `10. TBL_STL_TICKET_SET — 票种设置` / `11. TBL_STL_TICKET_INFO — 票卡信息` / `12. TBL_STL_ACCT_INFO — 储值票卡账户信息` / `13. TBL_STL_PERSON_INFO — 记名卡持卡人信息（含照片 BLOB）`。定位：`acc-es-server` `acc-es-server-schema.sql` `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:24,47,103,119,139,154,174,191,210,226,247,280,324`。
- **「13 张表全缺 / 按 mapper 反推建表 / 长度待复核」这三条在本模块注释里确有原文**（不必外求）：`背景：2026-08-25 核实 acc-es-server 13 个 mapper 对应的 13 张表在生产库全部不存在，/report/page 报 ORA-00942。仓库中原本没有任何 DDL。`、`⚠️ 本脚本由 mapper resultMap 的 jdbcType 与实体类字段类型**反推**得出，甲方原始 DDL 未获得。以下为已做的取舍，拿到原始 DDL 后 MUST 逐列比对：`。定位：`acc-es-server` `acc-es-server-schema.sql` `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:4-5,7-8`。
- 索引存在的唯一理由写在注释里：`TblStlAcctInfoMapper 有 WHERE TICKET_CSN = '00000000' || #{csn} 的查询`（对应 `IDX_STL_ACCT_INFO_CSN`）。定位：`acc-es-server` `acc-es-server-schema.sql` `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:320`。

**决策理由**

- 五条取舍逐条带理由（注释原文）：
  1. `所有字符串列统一用 VARCHAR2，**不用 CHAR**。原因：CHAR 长度猜错会因尾部空格补齐导致等值比较失效；VARCHAR2 无此问题。`
  2. `所有 DECIMAL / NUMERIC 列用不带精度的 NUMBER。原因：精度未知，不带精度可容纳任意数值，避免 ORA-01438。`
  3. `字符串长度按用途给宽：编码/ID 类 64，名称/文件名 256，长文本 1024。原因：宁可偏大也不能偏小，偏小会 ORA-12899 或静默截断。`
  4. `仅主键列加 NOT NULL，其余全部可空（mapper 的 insertSelective 允许缺列）。`
  5. `TASK_NO / PLAN_NO 统一 NUMBER。TblTktEsTaskMapper / TblTktEsAssignMapper 把 TASK_NO 标为 jdbcType=VARCHAR，与 TblTktEsReport/Proc 的 DECIMAL 冲突，此处按「任务号本质是数字」统一为 NUMBER，靠 Oracle 隐式转换兼容。`
  定位：`acc-es-server` `acc-es-server-schema.sql` `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:9-10,11-12,13-14,15,16-18`。

**陷阱（只在运行时暴露）**

- Oracle 保留字：`注意：PASSWORD 是 Oracle 保留字，mapper 中已用双引号引用，此处保持一致。`（漏引号只在执行期报错）。定位：`acc-es-server` `acc-es-server-schema.sql` `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:140`。
- 反向澄清（防止误加引号）：`注意：ID 是列名（mapper 中即为 ID），非保留字，可直接使用。`定位：`acc-es-server` `acc-es-server-schema.sql` `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:211`。
- 取舍 3 与取舍 5 都靠 Oracle 隐式行为兜底（`静默截断`、`靠 Oracle 隐式转换兼容`），**编译与单测都发现不了**；判据在上面「决策理由」两条原文里。

### 五、配置与部署

- `application.properties` **只有一处中文注释、且已乱码**：`#?????? ???IP`（出现两次，第 5 行与第 7 行），夹着一行被注释掉的 `#other.sql.host=172.20.222.3:1521`。文件内其余 40 余个键（含 `server.port=9011`、`netty.port=5000`、`ftp.*` 九个键、`mybatis.*`、`pagehelper.*`、`excel-path`）**一条注释都没有**。定位：`acc-es-server` `application.properties` `acc-es-server/src/main/resources/application.properties:5,6,7`。
- **本模块注释无此记载**：「jkube goals 绑 `package` 阶段、`mvn package` 就会推镜像」这条**不在本次抽取范围内**（`pom.xml` 未纳入范围），且 java / properties / sql 注释里没有任何相关文字；权威出处是 `AGENTS.md` §7。

### 六、持久层与 mapper

- mapper XML 的注释共 25 处，其中 **24 处是 MyBatis Generator 的 `<!--@mbg.generated-->` 标记**（分布在 `TblStlPersonInfoMapper.xml`、`TblTktEsAccountMapper.xml`、`TblTktPrePersonMapper.xml` 三个文件），**唯一一条人写的业务注释**是 `<!-- 根据票卡类型查询 -->`。定位：`acc-es-server` `TblStlTicketSetMapper.xml` `acc-es-server/src/main/resources/mapper/TblStlTicketSetMapper.xml:28`。
- mapper 接口上带语义的注释只有五条：`批量把任务修改为执行中` / `查询个性化任务` / `设置任务的状态为失败` / `根据个性化任务更新`（`TblTktEsTaskMapper`）与 `编码机签退`（`TblTktEsInfoMapper`）。定位：`acc-es-server` `TblTktEsTaskMapper` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsTaskMapper.java:60,67,74,81`；`acc-es-server` `TblTktEsInfoMapper` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsInfoMapper.java:23`。
- 实体类注释给出表↔中文名映射：`ES任务拆分`（`TblTktEsTask`）/ `ES任务报告`（`TblTktEsReport`）/ `ES任务计划`（`TblTktTaskPlan`）/ `ES任务分配`（`TblTktEsAssign`）/ `ES报告结果处理`（`TblTktEsProc`）/ `ES报告文件处理日志`（`TblTktEsFileProcLog`）/ `ES设备信息`（`TblTktEsInfo`）/ `票卡生命周期历史表`（`TblTktCycleHistroy`，**类名拼写为 `Histroy`**）。定位：`acc-es-server` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsTask.java:12`、`.../TblTktEsReport.java:13`、`.../TblTktTaskPlan.java:12`、`.../TblTktEsAssign.java:10`、`.../TblTktEsProc.java:10`、`.../TblTktEsFileProcLog.java:11`、`.../TblTktEsInfo.java:9`、`.../TblTktCycleHistroy.java:8`。
- `TblTktEsAccount` 的字段注释是本模块唯一带业务取值的实体注释：`用户名` / `密码` / `操作员：1；管理员2` / `账户添加时间` / `最后账户修改时间` / `账户添加或者修改账户`。定位：`acc-es-server` `TblTktEsAccount` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsAccount.java:13,20,27,41,46,51`。
- `TblStlPersonInfo` / `TblTktPrePerson` 的字段注释是生成器产物（`@TableName ...` + `This field was generated by MyBatis Generator.` + `This field corresponds to the database table ...`），字段说明仅为中文列名（`主键，票卡id，逻辑id` / `卡类型` / `照片` 等），**不含长度与取值范围**。定位：`acc-es-server` `TblStlPersonInfo` `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblStlPersonInfo.java:11,18,39,123,130-131`。
- `sql/TBL_STL_ACCT_INFO(1).sql` 是**空文件（0 行）**，无任何注释与语句。定位：`acc-es-server` `acc-es-server/src/main/resources/sql/TBL_STL_ACCT_INFO(1).sql`。

### 墓碑注释清单（建议转为断言测试）

不进正文，逐条给「`文件:行号` + 禁止的事 + 能否断言化」。

| # | 文件:行号 | 禁止的事（注释原文要点） | 能否断言化 |
|---|---|---|---|
| 1 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:9-10` | `所有字符串列统一用 VARCHAR2，不用 CHAR` —— 禁止把字符串列建成 `CHAR`（尾部空格补齐会让等值比较失效） | 可。查 `USER_TAB_COLS.DATA_TYPE`，13 张表内不得出现 `CHAR` |
| 2 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:11-12` | `所有 DECIMAL / NUMERIC 列用不带精度的 NUMBER` —— 禁止给数值列加精度（`ORA-01438`） | 可。断言 `DATA_PRECISION IS NULL AND DATA_SCALE IS NULL` |
| 3 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:13-14` | `宁可偏大也不能偏小，偏小会 ORA-12899 或静默截断` —— 禁止收窄字符串长度（编码/ID 64、名称/文件名 256、长文本 1024） | 部分可。按三档下限断言 `CHAR_LENGTH >= 64/256/1024` |
| 4 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:15` | `仅主键列加 NOT NULL，其余全部可空（mapper 的 insertSelective 允许缺列）` —— 禁止给非主键列加 `NOT NULL` | 可。断言非主键列 `NULLABLE='Y'` |
| 5 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:16-18` | `TASK_NO / PLAN_NO 统一 NUMBER` —— 禁止按 `TblTktEsTaskMapper` 的 `jdbcType=VARCHAR` 把这两列建成 `VARCHAR2` | 可。断言两列 `DATA_TYPE='NUMBER'` |
| 6 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:7-8` | `甲方原始 DDL 未获得 … 拿到原始 DDL 后 MUST 逐列比对` —— 禁止把本脚本当权威列定义使用 | 不可。属流程约束，无运行时判据 |
| 7 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:140` | `PASSWORD 是 Oracle 保留字，mapper 中已用双引号引用，此处保持一致` —— 禁止在 SQL / mapper 中不加双引号引用该列 | 可。grep `TblTktEsAccountMapper.xml` 中 `PASSWORD` 必带双引号 |
| 8 | `acc-es-server/src/main/resources/sql/acc-es-server-schema.sql:211` | `ID 是列名 … 非保留字，可直接使用` —— 反向澄清，不构成禁止 | 不可 |
| 9 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/coder/ServerDecoder.java:34-35` | `在这里全部都要,也可以只要body部分` + 被注释掉的 `super.decode(ctx, in)` —— 禁止改回「只取 body」 | 可。构造整帧字节喂 `decode`，断言返回的 `MessageBean` 包头四段齐全 |
| 10 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/coder/ServerDecoder.java:75` | 行尾被注释掉的 `- Constant.DataLength.DATA_MD5_BYTES` —— 禁止把包体长度改回「再扣 MD5 长度」 | 可。断言 `dataBody.length == Integer.parseInt(dataLength) - 26` |
| 11 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/NettyServerHandler.java:82` | 被注释掉的 `super.exceptionCaught(ctx, cause)` —— 禁止把异常交回父类（现为记日志 + `ctx.close()`） | 部分可。用 embedded channel 触发异常，断言 channel 被关闭且未再向上传播 |
| 12 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/BusinessHandler.java:283` | `//不做`（`SORT` 分支）—— 记录分拣任务不做实际处理 | 不可。仅意图记录，无可观测后果（与第 13 条合并断言） |
| 13 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsTaskServiceImpl.java:267-268` | 被注释掉的 `esTaskMapper.reportSortTaskReport(sortTaskReport)` —— 禁止让分拣报告落 `TBL_TKT_ES_REPORT` | 可。喂一条 `SORT` 报告，断言 `TBL_TKT_ES_REPORT` 无新增行 |
| 14 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsReportServiceImpl.java:336-343` | 被整段注释掉的 `TblStlAcctInfoMapper.updateByPrimaryKey` / `TblStlTicketInfoMapper.updateByPrimaryKey` —— 禁止在报告处理时回写票卡 / 账户主表 | 可。断言处理报告后 `TBL_STL_ACCT_INFO` / `TBL_STL_TICKET_INFO` 无 UPDATE |
| 15 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsReportServiceImpl.java:204`、`:361`、`:376` | 三处被注释掉的 `return true;` —— 无禁止语义 | 不可 |
| 16 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/TaskClassification.java:21-27` | 被整段注释掉的枚举定义（`PUBLISH("1","发行")` … `CUSTOM("7","车票个性化")`）—— 记录码值↔中文名，同时是「不改回枚举」的痕迹 | 可（仅码值部分）。断言 7 个常量值为 `1`~`7`；「不改回枚举」这层意图不可断言 |
| 17 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:113-114` | `下载成功删除文件,看项目需求` + 被注释掉的 `ftp.deleteFile(...)` —— 禁止下载后自动删除远端源文件 | 可。下载后断言远端文件仍存在 |
| 18 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/Md5Util.java:3` | 被注释掉的 `import org.apache.commons.codec.binary.Hex;` —— 禁止依赖 commons-codec 做十六进制转换 | 可。断言 classpath / 依赖树中该类未被本模块引用 |
| 19 | `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java:172` | `// TODO Auto-generated catch block` —— 无禁止语义（生成器残留） | 不可 |

## 附：acc-es-server 源码注释知识迁移（2026-09-16，阶段二·完整）

> **本节是「抽取 + 删除」闭环的产物**：源码里的多行叙述型 / MUST-NEVER / 事故史 / 墓碑注释**已被删除**，
> 知识只在本节存在。**二进制报文的字节偏移与长度类一行注释按例外保留在代码里**（`netty/data/**`，
> 删掉会直接诱发字节错位），因此那一类在代码与本节**双份并存**，两边不一致时 **MUST 以代码为准**。
> **行号有并发写入者，引用前 MUST 先 grep 现查**（抽取与删除时点 2026-09-16）。
>
> **抽取范围与实测规模**（`svn` 工作副本，`acc-es-server/src/main/**`）：
> 127 个文件（222 个 `.java` 中 **111 个在 `target/docker/.../build/` 下、是 jkube 构建副本，不在范围内**；
> 任务书里的「238 个文件 / 4826 行注释」是把那份构建副本一起数了，**NEVER 拿它当源码口径**）。
> 词法剥离实测：**99 个文件带注释、共 2262 个注释行**（Java 词法 + XML `<!-- -->` + properties 行首 `#` + SQL `--` / `/* */`）。
>
> 每条形如「结论 + `路径:行号` + 可 grep 原文短语」。**注释里没有的判据一律标注「本模块注释无此记载」，NEVER 据本节推断代码行为。**

### 一、Netty 报文层（`txnType` 7000~7005）

**1.1 包头骨架（两份并存，MUST 先确认改的是哪一份）**

- 在用的一份是 `Messagehead`，8 个字段逐个带长度：`包长度 4` / `消息类型 4` / `发起/接收方标识码 8` / `会话流水号 9` / `文件交易  0：无文件；1：有文件` / `1：请求消息；1：应答消息` / `1  md5  0：不加密；1：MD5加密` / `应答码默认两个空格`。定位 `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/model/Messagehead.java:14,19,24,29,34,39,44,49`。
- 另一份 `DataHead` 是**同形副本**：有 `消息报文版本`、没有 `包长度`，其余七段同名且**都不带长度数字**。定位 `.../netty/data/DataHead.java:22,27,33,37,42,47,52`。两份都在编译产物里，**NEVER 假定只有一个包头类**。
- 包头总长由常量固定为 **26**（`DATA_HEAD_BYTES`），`Messagehead` 各段之和 4+4+8+9+1+1+1+2 = 30 ≠ 26，**MUST 按 `ServerDecoder` 的实际读取顺序核对，NEVER 按注释相加**（`ServerDecoder` 里 `包长度` 4 字节是长度域、不计入 26）。

**1.2 长度常量（`Constant.DataLength`，`.../netty/model/Constant.java`）**

| 常量 | 值 | 行 | 注释原文 |
|---|---|---|---|
| `DATA_LENGTH_BYTES` | 4 | `:98` | `包长度字节` |
| `DATA_HEAD_BYTES` | 26 | `:102` | `包头长度字节` |
| `DATA_7000_SIGN_BYTES` | 76 | `:106` | Javadoc `7000设备签到报文长度（不包含长度字段，包头 + 包体 + mac检验码）` + 行尾 `//不包括mac108` |
| `DATA_BODY_7000_SIGN_BYTES` | 50 | `:110` | `7000设备签到报文体长度` |
| `DATA_BODY_7000_SIGN_BYTES_MACK` | 8 | `:114` | `7000设备签到报文MAC报文体长度` |
| `DATA_BODY_7002_TASK_BYTES` | 104 | `:116` | **无注释**，语义只能从常量名读 |

- **`DATA_7000_SIGN_BYTES` 不含 MAC，这一点已由算术定死**：26（包头）+ 50（包体）= 76，而 MAC 是另外 8 字节。因此那句 Javadoc 里的「+ mac检验码」是错的、行尾那句 `不包括mac` 才对；两者相加 76+8 = 84，与行尾数字 `108` 也对不上（`108` 无出处）。**NEVER 按 Javadoc 把 76 当「含 MAC 的总长」用**。

**1.3 报文码与应答码**

- 报文码常量 `Constant.DataPackage`：`TXN_TYPE_7000` `:25` / `7002` `:29` / `7003` `:33` / `7004` `:37` / `7005` `:41`，注释只写到「交易报文」四个字；`RESULT_TYPE = (byte)'1'` `:45`（`包头请求应答标识-应答`）。**业务含义不在常量上**，只在 `NettyServerHandler.channelRead` 的 `switch` 分支代码里，且原本只有 7004 那一支带注释（`设备工作任务报告`，`.../netty/service/NettyServerHandler.java:46`，该行注释已删、语义见本文件正文 §Netty 设备报文那张表）。
- `Constant.MackStatus` 全表（前 7 个带注释、后 4 个**从来没有注释**）：`00` `NORMAL` 正常（`:55`）/ `01` `MD5_ERROR` **报文格式错误**（`:59`，**常量名与语义不一致**）/ `02` `MESSAGE_ERROR` 无效的消息分类/类型码（`:63`）/ `03` `WORK_ERROR` **无效的数值范围**（`:67`，同样名不符义）/ `04` `ES_NODE_ERROR` 无效的节点标识码（`:72`）/ `05` `OPERATOR_ERROR` 无效的操作员（`:77`）/ `06` `PASSWORD_ERROR` 操作员密码错误（`:82`）/ `10` `INVALID_FILE_NAME`（`:84`）/ `11` `FILE_NOT_EXIT`（`:86`，**拼写是 `EXIT` 不是 `EXIST`**）/ `12` `FILE_GET_ERROR`（`:88`）/ `FF` `OTHER`（`:90`）。

**1.4 拆帧参数（`Constant.NETTY_LENGTH` + `ServerDecoder`）**

- `MAX_MESSAGE_LENGTH = 1024 * 1024`（`最大长度`，`:124`）/ `MIN_MESSAGE_LENGTH = 4`（`最小长度`，`:128`）/ `LENGTH_FIELD_OFFSET = 0`（`长度偏移`，`:132`）/ `LENGTH_FIELD_LENGTH = 4`（`长度字段所占的字节数`，`:136`）/ `LENGTH_ADJUSTMENT = 0`（注释写的是 `消息尾，结束标识长度`，**与 Netty 语义不同名**，`:140`）/ `INITIAL_BYTES_TO_STRIP = 0`（`忽略字节`，`:144`）。
- `LengthFieldBasedFrameDecoder` 六参语义与算式原本写在构造器 Javadoc 里（`.../netty/coder/ServerDecoder.java:18~25`），关键三句：`@param byteOrder 大小端  ByteOrder.LITTLE_ENDIAN`、`lengthAdjustment  = 数据包长度 - lengthFieldOffset - lengthFieldLength  - 长度域的值(满足发送条件)`、`failFast 默认为true，当frame长度超过maxFrameLength时立即报TooLongFrameException异常，为false，读取完整个帧再报异`（原文末尾即缺字）。

**1.5 报文体逐段偏移（7000 / 7003 / 7005）**

> 下面三组的一行式长度注释**仍保留在代码里**（护栏例外），本节是等价副本。

- **7000 签到请求体**（`.../netty/data/DeviceSignIn.java`）：`节点标识码`（`:18`）/ `操作员编码5位后补空格`（`:22`，**是右补空格、不是左补 0**）/ `操作员密码`（`:26`）；两个方向的转换入口原本标着 `7000 string转byte`（`:32`）与 `7000 byte转string`（`:44`），`报文头` 在 `:51`。
- **7000 签到应答体**（`.../netty/data/DeviceSignInMack.java:18`）：设备状态报告间隔 `单位(秒),数值为0时不需要状态报告，长度为6`；`operatorRank` 走 `IEsAccountService.getEsUserType` 的 `1，操作员 / 2，管理员`（见 §三）。
- **7000 应答体还有第二个类 `DeviceSignlnMac`**（注意是小写 L 拼成的 `ln`，`.../netty/data/DeviceSignlnMac.java`）：字段注释 `设备状态报告间隔`（`:16`）/ `操作员级别`（`:20`），但 `toByteByArray()` **直接 `return null`**。两个类**并存**、类注释分别是 `消息签到应答` 与 `设备签到应答`、`@program` 一个写 `cloud-acc-server` 一个写 `spring-cloud-acc`，**按注释无法分辨哪个在用；在用的是 `DeviceSignInMack`（`Mack` 结尾那个）**。
- **7003 签退体**（`.../netty/data/DeviceSignOut.java:12,14,16`）：`编码分拣机 8` / `操作员编号 10` / `操作员密码 32`，`报文头` 在 `:21`。三段之和 50，与 7000 包体同长。
- **7005 设备状态报告体**（`.../netty/data/DeviceStatReport.java`，解析在 `message2DeviceStatReport`）：`编码分拣机标识`（`:57`）/ `操作员编号`（`:61`）/ `动作码`（`:65`）/ `任务标识号`（`:69`）/ `设备工作状态 0 正常 1暂停 3故障`（`:73`）/ `任务数量`（`:77`）/ `完成数量`（`:81`）/ `任务起始时间 14`（`:85`）/ `任务结束时间 14`（`:89`）/ `保留 8`（`:93`）；同名字段在实体侧另有一份**写法不同的取值注释** `0:正常 1，暂停 3，故障`（`.../netty/data/EsStatReport.java:23`）。**前七段的长度只在代码的 `substring` 下标里，注释没给**，改这段 **MUST 数下标、NEVER 只看注释**。

### 二、7004 任务报告体与 7002 任务下发应答体

**2.1 7004 报告体（`.../netty/data/EsTaskReport.java`，`message2TaskReport`）**

- **七个动作码分支各自重复一遍同型切分**，首支在 `:36~98`、其余在 `:108~471`（`108` / `166` / `224` / `286` / `342` / `412` 各起一支），末尾 `报文头` 在 `:484`。
- 字段序列（带长度的原文）：`任务标识号 8` / `任务执行结果 1` / `编码分拣机 8` / `操作员编号 10` / `车票类型 3` / `版本号 6`（**只有发行 / 注销 / 重编码 / 个性化四支有**）/ `批次标识 10` + `起始序列号 10`（**只有发行、重编码、个性化三支有**）/ `任务数量 10` / `完成数量 10` / `废票数量 10`（**缴销 / 注销 / 分拣三支写的是 `失败数量 10`，同一偏移不同语义**）/ `任务起始时间 14` / `任务结束时间 14` / `任务报告文件名 20` / `保留 9`。
- **分支头部还有三个裸数字注释、无任何文字说明**：`143`（`:36`、`:224`、`:342`、`:412`）/ `9`（`:37` 等每支都有）/ `134`（`:46`、`:117`、`:175` 等）。按字段和推断：`9` 疑似「任务标识号 8 + 任务执行结果 1」，`134` 疑似「其后到保留段结束」，`143` = 9 + 134。**这是推断、注释里没有依据，MUST 用真实报文实测后再写进代码**。
- 七个报告 DTO 各自一句类注释：`注销任务报告`（`CancelTaskReport.java:9`）/ `车票个性化`（`CustomTaskReport.java:9`）/ `缴销任务报告`（`HandCancelTaskReport.java:9`）/ `预赋值报告`（`PreAssignTaskReport.java:9`）/ `车票分拣任务报告`（`SortTaskReport.java:9`）；`PublishTaskReport` 与 `RecodeTaskReport` **一条注释都没有**。

**2.2 7002 应答体（`.../netty/data/TaskApplyMack.java`，`toBytesArray`）**

- 头四段：`工作任务状态 1`（`:18`）/ `任务数 2`（`:22`）/ `后续任务 1`（`:26`）/ `工作任务`（`:30`）。
- 七类任务体逐段注释，**保留段长度七支各不相同，这是本文件最容易踩的一处**：发行支到 `有效期开始日期` 为止（`:123`）后**没有保留段注释**；预赋值 `保留 25`（`:182`）/ 缴销 `保留 67`（`:212`）/ 注销 `保留41`（`:254`，无空格）/ 分拣 `保留 6`（`:315`）/ 重编码 `保留 10`（`:401`）/ 个性化 `保留 41`（`:439`）。个性化支另有 `个性化任务文件名`（`:435`）。
- 发行支字段序列（`:54~123`）：`动作码 1` / `任务标识 8` / `任务变更标识 1` / `车票类型 3` / `车票主类型` / `版本号 2`（**注意这里写 2，而 7004 报告侧写 6**）/ `批次标识` / `任务数量` / `最小序列号` / `最大序列号` / `测试票标志` / `记名票标志` / `纪念票标志` / `钱包单位` / `初始票值` / `初始奖励值 5` / `压金 5` / `有效天` / `有效期开始日期`。
- 七类下发 DTO 的字段清单（全是 Javadoc、已保留）：`TicketPublishTask`（20 段，含票值 / 奖励 / 压金 / 有效期 / 票卡状态）/ `TicketPreassignTask`（13 段，含 `赋值日期` `赋值数量`）/ `TicketHandCancelTask`（6 段）/ `TicketCancelTask`（9 段，含 `使用次数`）/ `TicketSortTask`（13 段，**独有 `票箱1金额` / `票箱2金额` / `票箱3金额`**）/ `TicketRecodeTask`（19 段）/ `TicketCustomTask`（8 段，独有 `个性化任务文件名` `:46`）。

### 三、编解码与 Netty 服务端的决策与陷阱

**决策理由（原文已删，理由只在此）**

- **不复用父类拆帧结果、整帧自行解析**：原注释 `在这里调用父类的方法,实现指得到想要的部分,在这里全部都要,也可以只要body部分`，紧随其后的 `super.decode(ctx, in)` 被注释掉。定位 `.../netty/coder/ServerDecoder.java:34~35`。**NEVER 改回「只取 body」** —— 现实现依赖自己读长度域、包头四段与包体。
- **包体长度算式只减包头 26**：`.../netty/coder/ServerDecoder.java:75` 行尾原有被注释掉的 `- Constant.DataLength.DATA_MD5_BYTES`。**改回「再扣 MD5 长度」会让整段包体错位**，而注释里从来没写为什么去掉。
- 解码器逐段读取的顺序（原注释已删）：`读取长度`（`:45`）→ `读取消息类型码`（`:49`）→ `接收或发起方类型码`（`:54`）→ `读取流水号`（`:58`）→ `文件交易`（`:62`）→ `请求应答标志`（`:65`）→ `加密算法`（`:68`）→ `消息体`（`:74`）。**改 `ServerDecoder` MUST 按这个顺序对齐 `Messagehead` 的字段序**。
- **Netty 服务端是静态单例、只装配一次**：`服务端基本配置，通过一个静态单例类，保证启动时候只被加载一次`（`.../netty/service/NettyServer.java:19`）；两个 `EventLoopGroup` 分工 `用于处理服务器端接收客户端连接`（`:35`）/ `进行网络通信（读写）`（`:41`）；`ServerBootstrap` 是 `辅助工具类，用于服务器通道的一系列配置`（`:45`）；`@PreDestroy` 那个方法原注释 `关闭服务器方法`（`:96`）。启动入口是 `EsServerApplication` 的 `CommandLineRunner` 调 `nettyServer.start()`。
- **异常刻意不上抛父类**：`super.exceptionCaught(ctx, cause);` 被注释掉，现为两行 ERROR 日志 + `ctx.close()`。定位 `.../netty/service/NettyServerHandler.java:82`。
- **大小端两套转换并存**，`AlgorithmUtils` 里 int / short 各有大端小端版本：`int转换为小端byte[]（高位放在高地址中）`（`.../util/AlgorithmUtils.java:61`）/ `int转换为大端byte[]（低放在高地址中）`（`:80`）/ `short转换为小端byte[]（高位放在高地址中）`（`:225`）/ `short转换为大端byte[]（低位放在高地址中）`（`:240`）。**改任何一个 MUST 全模块 grep 调用方**。

**陷阱（只在运行时暴露）**

- **`AlgorithmUtils.Bytes2Int_BE` 的移位后掩码疑点 —— 只记录，NEVER 改代码。** 实现是（`.../util/AlgorithmUtils.java:121~127`）：
  ```java
  int iRst = (bytes[0] << 24) & 0xFF;
  iRst |= (bytes[1] << 16) & 0xFF;
  iRst |= (bytes[2] << 8) & 0xFF;
  iRst |= bytes[3] & 0xFF;
  ```
  掩码 `& 0xFF` **加在移位之后**，因此前三项恒为 `0`（左移已把有效位挪出低 8 位），整个方法**等价于只返回 `bytes[3] & 0xFF`**。对照小端版 `Bytes2Int_LE`（`:104~110`）写的是 `(bytes[1] & 0xFF) << 8`——**先掩码再移位**，那个是对的。`Bytes2Char_BE`（`:156~157`）同型同病：`(bytes[0] << 8) & 0xFF`。
  **本条只登记疑点、不动代码**：调用方目前的行为已建立在这个返回值上（若某处「长度只取最低字节」恰好能跑通，改成正确大端会立刻改变解析结果），**MUST 先用真实报文验证每个调用点再定改法**，NEVER 顺手「修 bug」。
- **`Bytes2Int_BE` / `Bytes2Char_BE` 的 `@note` 与方法名相反**：两处都写 `按小端方式转换`（`:102` 那条写小端且方法本身是小端、无问题；`:119` 与 `:151` 是大端方法却写「按小端方式转换」）。**照注释判断字节序会取反**。
- **`MyDecoderState` 是死枚举**：2026-09-16 全模块 grep 实测，`MyDecoderState` 在 `src/main` 下**只有声明处一处命中、零引用**（`.../enumns/MyDecoderState.java:3`），拆帧实际走 `LengthFieldBasedFrameDecoder` + `ServerDecoder`，没有手写状态机。且它两个常量的注释与名字**语义相反**：`READ_LENGTH` 注释写 `未读头部`、`READ_CONTENT` 注释写 `未读内容`。**NEVER 据它推断存在一台解码状态机；要删它属独立清理项、不在本次范围**。
- **MAC1 密钥索引与 TAC 分散数据前缀不在本模块**：2026-09-16 全模块 grep 实测，`acc-es-server/src/main` 下 `00B6` / `00BA` / `0532FF` / `4500FF` / `MAC1` / `TAC` **一个字面量都没有**（0 命中）。本模块与 MAC 有关的只有 `DATA_BODY_7000_SIGN_BYTES_MACK = 8`（MAC 长 8 字节）与 `Constant.MackStatus` 应答码。实际取值全在 **`acc-security-server`**：
  - MAC1 分散密钥索引 **`00B6`** —— `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/impl/CommonMacServiceImpl.java:55` 与 `:158`（`MAC1 分散秘钥00B6`），同文件 `:142` 又写 `次主秘钥索引：00B6`；
  - 次主密钥索引 **`00BA`** —— 同文件 `:38`（`次主秘钥索引：00BA`）；
  - MAC1 索引还有第三个值 **`00B0`** —— `CommonSaleAndRefundServiceImpl.java:46`（`MAC1 分散秘钥00B0`，代码 `byte[] bytes1 = {0x00, (byte) 0xB0}`）；
  - TAC 分散数据前缀 **`4500FF0000000000` + 16 位逻辑卡号** —— `CommonTacServiceImpl.java:110`，以及 `CommonSaleAndRefundServiceImpl.java:51` 的 `System.arraycopy(TransformUtils.HexStringToByteArr("4500FF0000000000"), 0, disperseData, 0, 8)`；
  - TAC 分散数据前缀 **`0532FF0000000000` + 逻辑卡号** —— `CommonTacServiceImpl.java:123~124`，注释注明 `青岛 0532`；同方法 `:119` 的次主密钥索引是 `00C1`（`ul卡 分散秘钥00C1`）。
  - **待甲方确认（本次抽取给出的标记，源码里没有任何「待确认」字样）**：① MAC1 索引在同一批实现里并存 `00B6` / `00B0` 两个值、次主索引并存 `00BA` / `00B6` / `00C1` 三个值，**哪个用于哪笔交易没有任何文档依据**；② 两个 TAC 前缀 `4500FF` 与 `0532FF` 并存，只有后者带「青岛」出处。**MUST 向甲方 / 加密机侧取一次真实应答做证据，NEVER 在 acc-es-server 里找这些值、也 NEVER 按「看起来更像青岛」自行统一。** 该域的提示词是 `docs/business/security-hsm.md`。

### 四、FTP 传输（`util/FTP.java` + `config/FtpComponent.java`）

**契约与判据**

- 工具方法职责边界（原 Javadoc，已删）：`获取FTPClient对象`（`:19`）/ `关闭FTP方法`（`:59`）/ `下载FTP下指定文件`（`:86`）/ `FTP文件上传工具类`（`:133`）/ `FPT上文件的复制`（`:180`，原文错字 `FPT`）/ `实现文件的移动，这里做的是一个文件夹下的所有内容移动到新的文件，如果要做指定文件移动，加个判断判断文件名` + `如果不需要移动，只是需要文件重命名，可以使用ftp.rename(oleName,newName)`（`:233~235`）/ `删除FTP上指定文件夹下文件及其子文件方法，添加了对中文目录的支持`（`:274`）/ `遍历解析文件夹下所有文件`（`:315`）。全部定位在 `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/FTP.java`。
- 下载链路的分步（原 `//` 注释，已删）：`默认失败`（`:96`）→ `跳转到文件目录`（`:100`）→ `获取目录下文件集合`（`:102`）→ `取得指定文件并下载`（`:106`）→ `绑定输出流下载文件,需要设置编码集，不然可能出现文件为空的情况`（`:111`）。
- 上传链路的分步：`设置PassiveMode传输`（`:144`）→ `设置二进制传输，使用BINARY_FILE_TYPE，ASC容易造成文件损坏`（`:146`）→ `判断FPT目标文件夹时候存在不存在则创建`（`:148`）→ `跳转目标目录`（`:152`）→ `上传文件`（`:155`）。
- 复制链路：`跳转到文件目录`（`:192`）→ `设置连接模式，不设置会获取为空`（`:194`）→ `获取目录下文件集合`（`:196`）→ `取得指定文件并下载`（`:201`）→ `读取文件，使用下载文件的方法把文件写入内存,绑定到out流上`（`:204`）→ `创建新目录`（`:208`）→ `文件复制，先读，再写`（`:210`）+ `二进制`（`:211`）。
- 移动链路：`获取文件数组`（`:248`）/ `新文件夹不存在则创建`（`:250`）/ `回到原有工作目录`（`:254`）/ `转存目录`（`:258`）。
- 递归删除的边界：`判断为文件则删除`（`:287`）/ `判断是文件夹`（`:291`）/ `递归删除子文件夹`（`:294`）/ `循环完成后删除文件夹`（`:298`）。
- 遍历解析：`设置FTP连接模式`（`:325`）/ `获取指定目录下文件文件对象集合`（`:327`）/ `判断为txt文件则解析`（`:332`）/ `ftp.retrieveFileStream使用了流，需要释放一下，不然会返回空指针`（`:349`）/ `这里就把一个txt文件完整解析成了个字符串，就可以调用实际需要操作的方法`（`:351`）/ `判断为文件夹，递归`（`:355`）。
- 连接参数：`连接FPT服务器,设置IP及端口`（`:33`）/ `设置用户名和密码`（`:35`）/ `设置连接超时时间,5000毫秒`（`:37`）/ `设置中文编码集，防止中文乱码`（`:39`）。

**三条只在运行时暴露的坑（原注释已删，判据只在此）**

1. **传输模式**：MUST `BINARY_FILE_TYPE`，`ASC容易造成文件损坏` —— 用 ASCII **不报错、只得到损坏文件**（`:146` 那句）。
2. **编码集**：上传侧漏设 → 中文乱码；下载侧漏设 → **文件为空而非报错**（`:39` 与 `:111` 两句）。
3. **流未释放会 NPE**：`retrieveFileStream` 用完必须 `completePendingCommand` / 关流，`不然会返回空指针`（`:349`）。复制链路还 MUST 显式设连接模式，`不设置会获取为空`（`:194`）。

**决策理由**

- **下载后不删远端源文件**：`下载成功删除文件,看项目需求` + 紧随其后被注释掉的 `ftp.deleteFile(...)`（`:113~114`）。**NEVER 打开它** —— ACC 侧是否还要取件不由我方决定。
- **目标目录不存在即创建，是有意设计**（`:148`、`:250`、`:208`、`:254` 四处）。
- `:172` 有一句生成器残留 `// TODO Auto-generated catch block`，无禁止语义。

**本模块注释无此记载（只能读常量与拼接代码）**

- `Constant.FileMessage` 四个常量**一条注释都没有**：`FILE_FOLDER = "/UPLOAD"`（`.../netty/model/Constant.java:10`）/ `FILE_NAME_TASK_PREFIX = "9050"`（`:12`）/ `FILE_NAME_CUSTOM_PREFIX = "9060"`（`:14`）/ `FILE_NAME_CUSTOM_REPORT_PREFIX = "9061"`（`:16`）。`9050` 同时作为 `TBL_TKT_ES_PROC.actionCode` 落库。
- `FtpComponent` 的 9 个配置字段（`ip` / `port` / `username` / `password` / `reportTargetDir` / `reportLocalDir` / `excelDir` / `customLocalDir` / `customTargetDir`）**全部无注释**，类上只有 `@author`（`.../config/FtpComponent.java:8`）。因此**「FTP 目录与文件命名约定」在源码注释里根本不存在**，MUST 读常量 + `EsReportServiceImpl` / `EsTaskServiceImpl` 的拼接代码。
- 报文里携带的文件名字段：`个性化任务文件名`（`TicketCustomTask.java:46`、`TaskApplyMack.java:435`）与 `任务报告文件名 20`（`EsTaskReport.java:94` 等七支）。
### 五、编码设备任务与报告

**任务分类码值（两份并存、常量名不一致）**

- 唯一带 Javadoc 的一份在 `enumns/TaskType.java`：`票卡发行`=1（`:10`）/ `票卡预赋值`=2（`:14`）/ `缴销`=3（`:18`）/ `注销`=4（`:22`）/ `分拣`=5（`:26`）/ `重编码`=6（`:30`）/ `个性化`=7（`:34`）。
- 在用的一份 `enumns/TaskClassification.java` **没有 Javadoc**，文件尾部原本有整段被注释掉的枚举定义（`:21~27`，已删）：`PUBLISH("1","发行")` / `ASSIGN("2","赋值")` / `HARD_CANCEL("3","缴销")` / `CANCEL("4","注销")` / `SORT("5","分拣")` / `RECODE("6","重编码")` / `CUSTOM("7","车票个性化")`。**该墓碑里的常量名与在用常量名不一致**：`HARD_CANCEL` vs 在用 `HAND_CANCEL`、`ASSIGN` vs 在用 `PRE_ASSIGN`。**NEVER 据那段墓碑改常量名**。
- 分类分支的落点是 `.../netty/service/BusinessHandler.java` 的 `taskApply`（`PUBLISH` / `PRE_ASSIGN` / `HAND_CANCEL` / `CANCEL` / `SORT` / `RECODE` / `CUSTOM` 七支）。

**接口语义（`service/IEsTaskService.java`，原 Javadoc 已删）**

- 任务接口**按票种切成两组，MUST 按这条分辨方法归属**：`保存任务 (发行，预赋值)`（`:13`）/ `根据taskNO查询任务 (发行，预赋值)`（`:21`）/ `更新任务 (发行，预赋值)`（`:29`）/ `通过taskNo删除任务 (发行，预赋值)`（`:37`）/ `分页查询任务 (发行，预赋值)`（`:46`）—— 与 —— `保存任务 （用于缴销/重编码/注销）`（`:56`）/ `删除任务 （用于缴销/重编码/注销）`（`:64`）。Controller 侧同样分两组，且 `/custom/save` 那条的注释是 `任务的保存 （用于缴销/重编码/注销/个性化）`（`controller/EsTaskController.java:67`，**比 service 侧多「个性化」**）。
- 报告链路语义：`打印机执行任务后发送任务报告，根据任务报告，修改任务的信息，并保存卡信息到数据库中`（`:105`）；另有 `通过es节点编号和日期获取任务`（`:88`）/ `根据任务No获取当前任务的类型`（`:97`）/ `获取个性化任务`（`:112`）/ `保存个性化任务`（`:121`）/ `产生对应的任务文件并上传到ftp服务器`（`:129`）。
- 计划审批**分两条口径**：`审批（发行和预赋值）`（`service/ITaskPlanService.java:43`）与 `同意拒绝（缴销，重编码，注销）`（`:51`），另有 `拆分任务`（`:59`）。可分配计划的筛选口径是 `查询可以分配任务的计划（已经审核或者审批中）`（`controller/TaskPlanController.java:77`）。
- 操作员级别取值：`根据账号密码获取账户的类型  1，操作员   2，管理员`（`service/IEsAccountService.java:12`），实体侧另一份同义注释 `操作员：1；管理员2`（`model/TblTktEsAccount.java:27`）。该值经 7000 应答的 `operatorRank` 回给设备。
- 7004 报告的处理目的（原方法体内块注释，已删）：`打印机执行任务后，给出报告，根据报告去修改相应的信息`（`.../netty/service/BusinessHandler.java:130~132`）。签到分支的分步原注释：`设备签到`（`:66`）/ `账号密码错误`（`:72`）/ `设置应答消息`（`:85`）/ `发送节点标识`（`:89`）；任务申请分支 `获取任务，最多50个`（`:181`，**这条是唯一记载「单次最多 50 条任务」的地方**）。签退分支 `设置应答消息`（`:159`）。签到时先 `更新设备登录状态`（`service/impl/EsInfoServerImpl.java:41`）。

**拆分任务与报告落库（`service/impl/EsTaskServiceImpl.java`，原 `//` 注释已删）**

- 拆分三步与校验点：`校验拆分任务数量不能超过计划数量`（`:77`）/ `更新计划已分配数量和计划状态`（`:89`）/ `保存拆分任务`（`:92`）。
- 删除分支两处 `删除任务对应的分配记录`（`:139`、`:180`）；报告分支四处 `更新任务执行结果`（`:223`、`:236`、`:247`、`:258`）+ `保存任务报告和文件处理记录`（`:225`）；个性化 `上传并解析个性化任务文件`（`:308`）。
- **分拣任务刻意不落报告**：`taskApply` 的 `SORT` 分支原本只有一行注释 `//不做`（`.../netty/service/BusinessHandler.java:283`），对应的 `esTaskMapper.reportSortTaskReport(sortTaskReport)` 在 `EsTaskServiceImpl.report` 里被整段注释掉（`:267~268`）。**NEVER 打开它** —— 打开等于让分拣报告开始写 `TBL_TKT_ES_REPORT`。

**报告文件的两组行布局（`service/impl/EsReportServiceImpl.java`）**

- 第一组（`analysisFile`，注释逐段给长度）：`记录标识 2`（`:136`）/ `票卡逻辑号 16`（`:138`）/ `原票卡逻辑号 16`（`:140`）/ `CSN 16`（`:142`）/ `票面号 16`（`:144`）/ `初始金额 9`（`:146`）/ `初始奖励金额 9`（`:148`）/ `有效天数 6`（`:150`）/ `有效期开始日期 8`（`:152`）/ `押金 9`（`:154`）/ `充值次数 9`（`:156`）/ `消费次数 9`（`:158`）/ `发行序号 10`（`:160`），随后 `卡类型`（`:177`）。
- 第二组（`analysisCustomFile`，**同名字段但注释不带长度，且字段集不同**：多 `序号`、`物理卡号`，无 `原票卡逻辑号` / `CSN`）：`记录标识`（`:278`）/ `序号`（`:280`）/ `票卡逻辑号`（`:282`）/ `物理卡号`（`:284`）/ `票面号`（`:286`）/ `初始金额`（`:288`）/ `初始奖励金额`（`:290`）/ `有效天数`（`:292`）/ `有效期开始日期`（`:294`）/ `押金`（`:296`）/ `充值次数`（`:298`）/ `消费次数`（`:300`）/ `发行序号`（`:302`），随后 `卡类型`（`:318`）。**两组切分下标不同，MUST 按方法分别核对、NEVER 互相套用。**
- **报告处理不回写票卡 / 账户主表**：`TblStlAcctInfoMapper.updateByPrimaryKey` 与 `TblStlTicketInfoMapper.updateByPrimaryKey` 两段批量更新被整段注释掉（`:336~343`），注释里没写原因。**NEVER 打开** —— 打开会让报告解析直接改 `TBL_STL_ACCT_INFO` / `TBL_STL_TICKET_INFO`。
- 批量落库意图：`批量记录文件处理结果`（`:382`）/ `保存文件处理日志`（`:397`）。另有三处被注释掉的 `return true;`（`:204`、`:361`、`:376`），无禁止语义。

**Excel 导入与个性化任务人员（`model/CustomTask.java`）**

- 列语义：`物理卡号 16 前补0`（`:13`）/ 持卡人类型 `0:普通乘客` `1：地铁员工`（`:19~20`）/ `姓名`（`:26`）/ `证据按类型`（`:32`，**原文错别字，实为「证件类型」**）/ `32位 后补零`（`:38`，证件代码）/ `工作单位`（`:47`）。
- **`CustomTask.id` 注释写「后补零」，实际走的是后补空格**：生成任务文件时用 `addSpaceRight(person.getId(), 32)`，而本模块的 `addZeroForNum` 是**左补 0**（`util/ByteConvertUtil.java:101` 原注释 `//左补0`，另有两处 `//右补0` 在 `:118`、`:135`）。**照注释填充会与设备解析错位。**
- 持卡人类型另有带注释的枚举 `enumns/PersonTypeEnum.java`：`普通乘客`=0（`:13`）/ `地铁员工`=1（`:17`），字段 `类型值`（`:22`）/ `类型名称`（`:26`），方法 `根据名称获取typeValue的值`（`:31`）。证件类型枚举 `enumns/PidCdEnum.java` 有 12 项（`身份证` / `学生证` / `护照` / `军官证` / `士兵证` / `警官证` / `港澳通行证` / `台胞证` / `驾驶证` / `居住证` / `船名证` / `员工证`，`:15~61`）。
- **状态机取值不在注释里**：`TaskExecuteStat`（0/1/2/3/9）、`TaskAssignStat`（0/1）、`TaskApplyStat`（0/1/2）、`PlanStat`（01~05 与 5/6 两套并存）、`EsStat`（0/1/3，类注释只有 `Description:设备状态`）、`LoginStat`（1/0，类注释只有 `签到`）、`TicketType`（01/02）**全靠枚举字面量表达**。**排查状态取值 MUST 读枚举常量，NEVER 指望注释。**
- MD5 工具用法反直觉：`加密解密算法 执行一次加密，两次解密`（`util/Md5Util.java:54`）；`:67` 还有一个 `测试主函数`。
### 六、mapper 与 13 张表

**mapper XML（`src/main/resources/mapper/*.xml`，13 个文件）**

- 注释共 **25 处，其中 24 处是 MyBatis Generator 的 `<!--@mbg.generated-->` 标记**，分布在三个文件：`TblStlPersonInfoMapper.xml`（11 处）/ `TblTktEsAccountMapper.xml`（8 处）/ `TblTktPrePersonMapper.xml`（5 处）。**唯一一条人写的业务注释**是 `<!-- 根据票卡类型查询 -->`（`TblStlTicketSetMapper.xml:28`）。这 25 处**已全部删除**（`@mbg.generated` 只服务 MBG 重新生成、本项目构建链里没有 MBG；业务那条的语义即 statement 名本身）。
- 其余 10 个 mapper XML（`TblTktTaskPlanMapper` / `TblTktEsTaskMapper` / `TblTktEsReportMapper` / `TblTktEsProcMapper` / `TblTktEsInfoMapper` / `TblTktEsFileProcLogMapper` / `TblTktEsAssignMapper` / `TblStlTicketInfoMapper` / `TblStlAcctInfoMapper` / `TblStlTicketSetMapper` 的其余部分）**本来就 0 条注释**。
- **注意 `TblStlAcctInfoMapper` 有 `WHERE TICKET_CSN = '00000000' || #{csn}` 这种拼接式查询**（原本记在 schema 脚本的索引注释里），对应索引 `IDX_STL_ACCT_INFO_CSN`。**改那条 SQL MUST 同步看索引是否还能命中。**

**mapper 接口（`mapper/*.java`）**

- 带语义的注释只有五条：`批量把任务修改为执行中`（`mapper/TblTktEsTaskMapper.java:60`）/ `查询个性化任务`（`:67`）/ `设置任务的状态为失败`（`:74`）/ `根据个性化任务更新`（`:81`）与 `编码机签退`（`mapper/TblTktEsInfoMapper.java:23`）。其余全是 `Mapper 接口` 这类生成器套话。

**实体类 ↔ 表 ↔ 中文名**

| 实体类 | 中文名（原类注释） | 定位 |
|---|---|---|
| `TblTktEsTask` | `ES任务拆分` | `model/TblTktEsTask.java:12` |
| `TblTktEsReport` | `ES任务报告` | `model/TblTktEsReport.java:13` |
| `TblTktTaskPlan` | `ES任务计划` | `model/TblTktTaskPlan.java:12` |
| `TblTktEsAssign` | `ES任务分配` | `model/TblTktEsAssign.java:10` |
| `TblTktEsProc` | `ES报告结果处理` | `model/TblTktEsProc.java:10` |
| `TblTktEsFileProcLog` | `ES报告文件处理日志` | `model/TblTktEsFileProcLog.java:11` |
| `TblTktEsInfo` | `ES设备信息` | `model/TblTktEsInfo.java:9` |
| `TblTktCycleHistroy` | `票卡生命周期历史表`（**类名拼写是 `Histroy`**） | `model/TblTktCycleHistroy.java:8` |

- `TblTktEsAccount` 是本模块**唯一带业务取值的实体注释**：`用户名`（`:13`）/ `密码`（`:20`）/ `操作员：1；管理员2`（`:27`）/ `账户添加时间`（`:41`）/ `最后账户修改时间`（`:46`）/ `账户添加或者修改账户`（`:51`）。
- `TblStlPersonInfo` / `TblTktPrePerson` 的字段注释是生成器产物（`@TableName ...` + `This field was generated by MyBatis Generator.` + `This field corresponds to the database table ...`），字段说明仅为中文列名（`主键，票卡id，逻辑id` / `卡类型` / `照片` 等），**不含长度与取值范围**。定位 `model/TblStlPersonInfo.java:11,18,39,123,130~131`、`model/TblTktPrePerson.java:12,19,25,32,39,46,53,60`（`任务编号` / `物理卡号` / `用户类型` / `用户名称` / `卡类型` / `卡id` / `员工编号` / `工作单位`）。

**13 张表与建表脚本（`src/main/resources/sql/acc-es-server-schema.sql`）**

- 脚本头部原有一整段叙述（已删）：`acc-es-server 建表脚本（AFCITPDB / QDITP）`（`:2`）、`执行环境：Oracle 19c，schema QDITP`（`:20`）、`背景：2026-08-25 核实 acc-es-server 13 个 mapper 对应的 13 张表在生产库全部不存在，/report/page 报 ORA-00942。仓库中原本没有任何 DDL。`（`:4~5`）、`⚠️ 本脚本由 mapper resultMap 的 jdbcType 与实体类字段类型**反推**得出，甲方原始 DDL 未获得。以下为已做的取舍，拿到原始 DDL 后 MUST 逐列比对：`（`:7~8`）。
- 13 张表与中文名（原逐表分节注释，已删）：`1. TBL_TKT_TASK_PLAN — 制票任务计划`（`:24`）/ `2. TBL_TKT_ES_TASK — 编码设备制票任务`（`:47`）/ `3. TBL_TKT_ES_ASSIGN — 任务分配到编码设备`（`:103`）/ `4. TBL_TKT_ES_INFO — 编码设备台账`（`:119`）/ `5. TBL_TKT_ES_ACCOUNT — 编码设备操作员账号`（`:139`）/ `6. TBL_TKT_ES_REPORT — ES 任务报告（日志中报错表之一）`（`:154`）/ `7. TBL_TKT_ES_PROC — ES 报告文件处理结果（日志中报错表之一）`（`:174`）/ `8. TBL_TKT_ES_FILE_PROC_LOG — 文件逐条处理明细`（`:191`）/ `9. TBL_TKT_PRE_PERSON — 记名卡预登记人员`（`:210`）/ `10. TBL_STL_TICKET_SET — 票种设置`（`:226`）/ `11. TBL_STL_TICKET_INFO — 票卡信息`（`:247`）/ `12. TBL_STL_ACCT_INFO — 储值票卡账户信息`（`:280`）/ `13. TBL_STL_PERSON_INFO — 记名卡持卡人信息（含照片 BLOB）`（`:324`）。
- **五条反推取舍（原文，逐条带理由，已删；这五条同时是墓碑清单第 1~5 条的判据）**：
  1. `所有字符串列统一用 VARCHAR2，**不用 CHAR**。原因：CHAR 长度猜错会因尾部空格补齐导致等值比较失效；VARCHAR2 无此问题。`（`:9~10`）
  2. `所有 DECIMAL / NUMERIC 列用不带精度的 NUMBER。原因：精度未知，不带精度可容纳任意数值，避免 ORA-01438。`（`:11~12`）
  3. `字符串长度按用途给宽：编码/ID 类 64，名称/文件名 256，长文本 1024。原因：宁可偏大也不能偏小，偏小会 ORA-12899 或静默截断。`（`:13~14`）
  4. `仅主键列加 NOT NULL，其余全部可空（mapper 的 insertSelective 允许缺列）。`（`:15`）
  5. `TASK_NO / PLAN_NO 统一 NUMBER。TblTktEsTaskMapper / TblTktEsAssignMapper 把 TASK_NO 标为 jdbcType=VARCHAR，与 TblTktEsReport/Proc 的 DECIMAL 冲突，此处按「任务号本质是数字」统一为 NUMBER，靠 Oracle 隐式转换兼容。`（`:16~18`）
- 两条列名注意（原文已删）：`注意：PASSWORD 是 Oracle 保留字，mapper 中已用双引号引用，此处保持一致。`（`:140`）—— **漏引号只在执行期报错**；反向澄清 `注意：ID 是列名（mapper 中即为 ID），非保留字，可直接使用。`（`:211`）。
- 取舍 3 与取舍 5 **都靠 Oracle 隐式行为兜底**（`静默截断`、`靠 Oracle 隐式转换兼容`），**编译、单测、`xmllint` 全都发现不了**。
- `sql/TBL_STL_ACCT_INFO(1).sql` 是**空文件（0 行）**，无任何语句与注释；文件名带括号，**引用时 MUST 加引号**。
### 七、config 与运行期配置

- `config/FtpComponent.java` 是唯一的 config 类，`@Component` + `@ConfigurationProperties` 形态承载 9 个 `ftp.*` 键，**9 个字段全部无注释**，类上只有 `@author rxwnc`（`:8`）。
- `component/GlobalExceptionHandler.java` **0 条注释**（全模块唯一的全局异常处理器）。
- 启动类 `EsServerApplication.java` 只有一段模板类注释（`:13~18`，`@program: spring-cloud-acc` / `@description:` 为空 / `@author: fc` / `@create: 2020-09-10 16:16`），**没有任何一句说明 `CommandLineRunner` 里要起 Netty**。
- `src/main/resources/application.properties` **只有 3 个注释行、且中文已乱码**：`#?????? ???IP`（`:5`）与同一句重复一次（`:7`），中间夹着一行**被注释掉的数据库地址** `#other.sql.host=172.20.222.3:1521`（`:6`）。文件内其余 40 余个键（含 `server.port=9011`、`netty.port=5000`、`ftp.*` 九个键、`mybatis.*`、`pagehelper.*`、`excel-path`）**一条注释都没有**。三行已删（乱码注释无信息量、被注释掉的地址属墓碑；该地址与 `mcp_database_qd` 指向的库一致，见 `AGENTS.md` §8）。
- **本模块注释无此记载**：①「`netty.port=5000` 是监听端口、`7000~7005` 是报文码」这条判据，源码与配置的注释里**一个字都没有**，权威出处是 `AGENTS.md` §4 与本文件正文 §Netty 设备报文；②「jkube goals 绑 `package` 阶段、`mvn package` 就会推镜像」这条**不在 `src/main` 范围内**（在 `pom.xml`），权威出处是 `AGENTS.md` §7 —— 因此本模块**构建时 MUST 加 `-Djkube.skip=true` 才只出 jar**；③全模块**没有 `@Scheduled`、没有 MQ、只有 `@EnableAsync`**，文件处理由 Netty 报文（7002/7004）与 HTTP 接口触发，这条也没有注释依据、判据是 grep 结果。
### 矛盾与待裁决

阶段一记了 8 处，阶段二逐文件复核后合并为下表 **16 处**。表里「谁能裁决」是判据出处，**NEVER 自行按「看起来更合理」那一侧改代码**。

| # | 矛盾 | 两侧原文 / 事实 | 定位 | 谁能裁决 |
|---|---|---|---|---|
| 1 | `DATA_7000_SIGN_BYTES` 到底含不含 MAC | Javadoc `包头 + 包体 + mac检验码` vs 行尾 `不包括mac108`；算术 26+50=76 站在「不含」一侧，且 `108` 无出处 | `netty/model/Constant.java:104,106` | 已可定论：**不含 MAC**；`108` 待甲方确认 |
| 2 | 请求 / 应答标识两个取值都写成 `1` | `1：请求消息；1：应答消息`；`Constant.DataPackage.RESULT_TYPE` 只定义了应答 `'1'`，**请求侧取值在整个模块里查不到** | `netty/model/Messagehead.java:39`、`Constant.java:45` | 甲方（设备侧实际发什么） |
| 3 | 版本号段长度 7002 写 2、7004 写 6 | `//版本号 2`（下发） vs `//版本号 6`（报告） | `netty/data/TaskApplyMack.java:72`、`netty/data/EsTaskReport.java:59` | 甲方报文规格 |
| 4 | 7004 同一偏移在不同分支叫「废票数量」/「失败数量」 | 发行 / 预赋值 / 重编码 / 个性化写 `废票数量 10`，缴销 / 注销 / 分拣写 `失败数量 10` | `netty/data/EsTaskReport.java:82` vs `:138`/`:198`/`:316` | 甲方（是否同一字段） |
| 5 | 7002 七支保留段长度各不相同、发行支缺注释 | 25 / 67 / 41 / 6 / 10 / 41，发行支无保留注释 | `netty/data/TaskApplyMack.java:182,212,254,315,401,439` | 甲方报文规格 |
| 6 | 分支头裸数字 `143` / `9` / `134` 无任何说明 | 只有数字、没有文字；按字段和**推断** 143 = 9 + 134 | `netty/data/EsTaskReport.java:36,37,46` | 甲方 / 真实报文实测 |
| 7 | 包头两份并存（`Messagehead` / `DataHead`），字段集不同 | `DataHead` 多 `消息报文版本`、少 `包长度` | `netty/model/Messagehead.java`、`netty/data/DataHead.java` | 项目内部（应删其中一份） |
| 8 | 7000 应答体两个类并存 | `DeviceSignInMack`（在用）vs `DeviceSignlnMac`（`toByteByArray()` 返回 `null`）；`@program` 一个 `cloud-acc-server`、一个 `spring-cloud-acc` | `netty/data/DeviceSignInMack.java:10`、`netty/data/DeviceSignlnMac.java:8` | 项目内部（应删死类） |
| 9 | `Bytes2Int_BE` / `Bytes2Char_BE` 移位后掩码 | 前三段恒 0，方法等价于只取最低字节；小端版写法相反且正确 | `util/AlgorithmUtils.java:124~127,156~157` | **只登记，改前 MUST 逐调用点用真实报文验证** |
| 10 | 两个 `@note` 写「按小端」却是大端方法 | `数组长度至少为4，按小端方式转换，即传入的bytes是大端的` 自相矛盾 | `util/AlgorithmUtils.java:119,151` | 项目内部（注释已删，本表即结论） |
| 11 | `MackStatus` 常量名与语义不一致 | `MD5_ERROR` = `报文格式错误`、`WORK_ERROR` = `无效的数值范围`；`FILE_NOT_EXIT` 拼写少 `S` | `netty/model/Constant.java:59,67,86` | 项目内部（改名要动设备应答链路，慎） |
| 12 | 任务分类常量名与墓碑枚举不一致 | 墓碑 `HARD_CANCEL` / `ASSIGN` vs 在用 `HAND_CANCEL` / `PRE_ASSIGN` | `enumns/TaskClassification.java`（墓碑原在 `:21~27`） | 项目内部（**NEVER 按墓碑改**） |
| 13 | `MyDecoderState` 死枚举 + 注释与枚举名相反 | 零引用；`READ_LENGTH` 注释 `未读头部`、`READ_CONTENT` 注释 `未读内容` | `enumns/MyDecoderState.java:3` | 项目内部（删除属独立清理项） |
| 14 | `CustomTask.id` 注释「后补零」vs 代码后补空格 | `32位 后补零` vs `addSpaceRight(..., 32)`；`addZeroForNum` 是**左补 0** | `model/CustomTask.java:38`、`util/ByteConvertUtil.java:101` | 甲方（设备按哪种解析） |
| 15 | 个性化任务保存的归属口径不一致 | service 注释 `（用于缴销/重编码/注销）` vs controller 注释多写「个性化」 | `service/IEsTaskService.java:56`、`controller/EsTaskController.java:67` | 项目内部 |
| 16 | 13 张表列类型 / 长度全是反推 | `甲方原始 DDL 未获得` + 取舍 3 / 5 靠 Oracle 隐式行为兜底 | `sql/acc-es-server-schema.sql:7~18` | 甲方（拿到原始 DDL 后逐列比对） |

### 墓碑清单

阶段二**实际删除**的墓碑（被注释掉的代码 / 已失效的告示）逐条登记如下。阶段一那张 19 行表里 1~8 与 16~19 条对应下表，**两张表 NEVER 互删**：上面那张记「禁止的事」，下表记「删了什么、能不能断言化」。

| # | 原位置 | 删掉的墓碑内容 | 语义 | 能否断言化 |
|---|---|---|---|---|
| 1 | `netty/coder/ServerDecoder.java:34~35` | `在这里全部都要,也可以只要body部分` + 注释掉的 `super.decode(ctx, in)` | 禁止改回「只取 body」 | 可。喂整帧，断言 `MessageBean` 包头四段齐全 |
| 2 | `netty/coder/ServerDecoder.java:75` | 行尾注释掉的 `- Constant.DataLength.DATA_MD5_BYTES` | 禁止包体长度再扣 MD5 | 可。断言 `dataBody.length == dataLength - 26` |
| 3 | `netty/service/NettyServerHandler.java:82` | 注释掉的 `super.exceptionCaught(ctx, cause)` | 禁止把异常交回父类 | 部分可。embedded channel 断言被关闭且未上传 |
| 4 | `netty/service/BusinessHandler.java:283` | `//不做`（`SORT` 分支） | 分拣申请不做实际处理 | 不可（与 #5 合并断言） |
| 5 | `service/impl/EsTaskServiceImpl.java:267~268` | 注释掉的 `esTaskMapper.reportSortTaskReport(sortTaskReport)` | 禁止分拣报告落 `TBL_TKT_ES_REPORT` | 可。喂 `SORT` 报告，断言该表无新增 |
| 6 | `service/impl/EsReportServiceImpl.java:336~343` | 注释掉的 `TblStlAcctInfoMapper.updateByPrimaryKey` / `TblStlTicketInfoMapper.updateByPrimaryKey` 两段批量更新 | 禁止报告处理回写票卡 / 账户主表 | 可。断言处理后两表无 UPDATE |
| 7 | `service/impl/EsReportServiceImpl.java:204`、`:361`、`:376` | 三处注释掉的 `return true;` | 无禁止语义 | 不可 |
| 8 | `enumns/TaskClassification.java:21~27` | 整段注释掉的枚举定义（`PUBLISH("1","发行")` … `CUSTOM("7","车票个性化")`） | 记码值↔中文名；同时是「不改回枚举」的痕迹 | 可（仅码值）。断言 7 个常量值为 `1`~`7` |
| 9 | `util/FTP.java:113~114` | `下载成功删除文件,看项目需求` + 注释掉的 `ftp.deleteFile(...)` | 禁止下载后删远端源文件 | 可。下载后断言远端文件仍存在 |
| 10 | `util/Md5Util.java:3` | 注释掉的 `import org.apache.commons.codec.binary.Hex;` | 禁止依赖 commons-codec 做十六进制转换 | 可。断言依赖树里本模块未引用该类 |
| 11 | `netty/model/Constant.java:106` | 行尾 `//不包括mac108` | 与 Javadoc 冲突的口径，已收进「矛盾 #1」 | 可。断言 `76 == 26 + 50` |
| 12 | `src/main/resources/application.properties:6` | 注释掉的 `#other.sql.host=172.20.222.3:1521` | 旧数据库地址；线上取值以 Deployment env 为准 | 不可（属配置口径） |
| 13 | `util/FTP.java:172` | `// TODO Auto-generated catch block` | 生成器残留 | 不可 |
| 14 | `util/Md5Util.java:67` | `// 测试主函数` | 标注 `main` 是测试入口 | 部分可。断言生产代码不调它 |

### 覆盖率自评

- **量化口径**：`src/main` 共 127 个在管文件、99 个带注释、2262 个注释行（词法剥离实测，非 grep 估算）。
- **已抽取（进本节或阶段一节）**：全部**承载判据的注释**，即报文字段与长度、应答码、状态取值、接口分组口径、FTP 三坑、五条建表取舍、13 张表中文名、墓碑 14 条、矛盾 16 条。按注释行折算约 **860 行**（其中 7004 / 7002 的同型重复按「一支 + 差异」折叠，未逐支复述）。
- **未逐条抄进文档的**（**有意为之**）：①`@param` / `@return` / `@author` / `@date` / `@program` 这类模板行约 **1100 行**，零判据；②`Mapper 接口` / `This field was generated by MyBatis Generator.` / `<!--@mbg.generated-->` 生成器套话约 **150 行**；③`//设置应答消息` / `//获取applicationContext` / `//通过name获取 Bean.` 这类逐行复述代码的注释约 **150 行**（只挑了含判据的，如 `获取任务，最多50个`）。
- **护栏例外造成的双份**：`netty/data/**` 的一行式字节偏移 / 长度注释**仍在代码里**，本节是等价副本，**两边不一致时以代码为准**。
- **自评结论**：判据类覆盖 **100%**（逐文件过了 99 个带注释文件）；**行级覆盖约 38%**，缺口全部落在上面三类无判据注释上。**残余风险两处**：①7004 / 7002 的七支重复只折叠记录，改某一支时 **MUST 回代码逐支核对**；②裸数字 `143` / `9` / `134` 的语义是推断、无原文依据。






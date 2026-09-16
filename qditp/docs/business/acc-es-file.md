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

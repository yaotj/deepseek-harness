# acc-es-server 微服务文档

> **模块路径**: `acc-es-server/`
> **端口**: 9011
> **职责**: ACC 事件源服务，处理 ACC 系统的事件溯源、任务分配、报表统计和文件处理
> **源码阅读范围**: `acc-es-server/src/main/java/`、`acc-es-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

acc-es-server 是 ACC（自动售检票系统）事件源服务，承担以下核心职责：

1. **事件溯源** - 记录票务业务事件流（TicketCustomTask、TicketPublishTask、TicketPreassignTask、TicketSortTask、TicketRecodeTask、TicketCancelTask、TicketHandCancelTask）
2. **任务分配** - 任务分配与执行状态管理（EsAssignController）
3. **报表统计** - 票务报表查询与统计（EsReportController）
4. **文件处理** - ACC 文件上传、解析、入库（FileController）
5. **任务计划** - 任务计划管理（TaskPlanController）
6. **账户处理** - ACC 账户相关处理（EsAccountController）
7. **流程处理** - 业务流程处理（EsProcController）
8. **信息查询** - ACC 信息查询（EsInfoController）

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |
| Netty | 网络通信（ACC 设备接入） |
| FTP | 文件传输 |

### 1.3 端口配置

```properties
server.port=9011
```

### 1.4 核心数据表

| 表名 | 说明 |
|------|------|
| `TBL_TKT_ES_INFO` | ACC 事件信息表 |
| `TBL_TKT_ES_ACCOUNT` | ACC 账户表 |
| `TBL_TKT_ES_ASSIGN` | ACC 任务分配表 |
| `TBL_TKT_ES_PROC` | ACC 流程表 |
| `TBL_TKT_ES_REPORT` | ACC 报表表 |
| `TBL_TKT_ES_TASK` | ACC 任务表 |
| `TBL_TKT_TASK_PLAN` | 任务计划表 |
| `TBL_TKT_PRE_PERSON` | 预分配人员表 |
| `TBL_STL_ACCT_INFO` | 结算账户信息表 |
| `TBL_STL_PERSON_INFO` | 结算人员信息表 |
| `TBL_STL_TICKET_INFO` | 结算票卡信息表 |
| `TBL_STL_TICKET_SET` | 结算票卡组表 |

---

## 二、接口清单

### 2.1 ACC 事件接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| - | 账户处理 | `/es/account` | POST | ACC 账户处理 |
| - | 任务分配 | `/es/assign` | POST | ACC 任务分配 |
| - | 信息查询 | `/es/info` | POST | ACC 信息查询 |
| - | 流程处理 | `/es/proc` | POST | 业务流程处理 |
| - | 报表统计 | `/es/report` | POST | ACC 报表统计 |
| - | 任务处理 | `/es/task` | POST | ACC 任务处理 |
| - | 文件上传 | `/file/upload` | POST | ACC 文件上传 |
| - | 任务计划 | `/task/plan` | POST | 任务计划管理 |

### 2.2 Netty 设备通信

| 接口类型 | 说明 |
|---------|------|
| 设备注册 | 设备签到/签退 |
| 事件上报 | ACC 事件数据上报 |
| 任务下发 | 任务下发到 ACC 设备 |

---

## 三、核心业务流程

### 3.1 任务发布流程

1. **任务创建** - 创建 TicketPublishTask 任务
2. **任务分配** - 分配任务到具体人员/设备
3. **任务执行** - ACC 设备执行任务
4. **结果上报** - 设备上报执行结果
5. **状态更新** - 更新任务执行状态

### 3.2 文件处理流程

1. **文件上传** - ACC 设备或系统上传文件
2. **文件解析** - 解析 ACC 文件格式
3. **数据入库** - 解析后数据写入数据库
4. **结果通知** - 通知处理结果

### 3.3 报表统计流程

1. **参数查询** - 查询统计参数
2. **数据聚合** - 聚合票务数据
3. **报表生成** - 生成统计报表
4. **结果返回** - 返回报表数据

---

## 四、枚举与常量

### 4.1 任务类型（TaskType）

| 枚举值 | 含义 |
|--------|------|
| CUSTOM | 自定义任务 |
| PUBLISH | 发布任务 |
| PREASSIGN | 预分配任务 |
| SORT | 排序任务 |
| RECODE | 记录任务 |
| CANCEL | 取消任务 |
| HAND_CANCEL | 手动取消任务 |

### 4.2 任务状态（TaskApplyStat、TaskAssignStat、TaskExecuteStat、PlanStat）

| 枚举值 | 含义 |
|--------|------|
| PENDING | 待处理 |
| PROCESSING | 处理中 |
| SUCCESS | 成功 |
| FAILED | 失败 |

### 4.3 人员类型（PersonTypeEnum）

| 枚举值 | 含义 |
|--------|------|
| ... | ... |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsAccountController.java` | 账户处理 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsAssignController.java` | 任务分配 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsInfoController.java` | 信息查询 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsProcController.java` | 流程处理 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsReportController.java` | 报表统计 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsTaskController.java` | 任务处理 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/FileController.java` | 文件上传 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/TaskPlanController.java` | 任务计划 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/IEsAccountService.java` | 账户服务接口 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsAccountServiceImpl.java` | 账户服务实现 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/IEsAssignService.java` | 任务分配服务接口 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsAssignServiceImpl.java` | 任务分配服务实现 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/IEsTaskService.java` | 任务服务接口 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/service/impl/EsTaskServiceImpl.java` | 任务服务实现 |

### 5.3 Netty 通信层

| 文件路径 | 说明 |
|---------|------|
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/NettyServer.java` | Netty 服务器 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/service/BusinessHandler.java` | 业务处理器 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/coder/ServerDecoder.java` | 服务端解码器 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/netty/coder/ServerEncoder.java` | 服务端编码器 |

### 5.4 数据访问层

| 文件路径 | 说明 |
|---------|------|
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsAccountMapper.java` | 账户 Mapper |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsAssignMapper.java` | 任务分配 Mapper |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsInfoMapper.java` | 事件信息 Mapper |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsProcMapper.java` | 流程 Mapper |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsReportMapper.java` | 报表 Mapper |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/mapper/TblTktEsTaskMapper.java` | 任务 Mapper |

### 5.5 实体类

| 文件路径 | 说明 |
|---------|------|
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsAccount.java` | 账户实体 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsAssign.java` | 任务分配实体 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsInfo.java` | 事件信息实体 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsProc.java` | 流程实体 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsReport.java` | 报表实体 |
| `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/model/TblTktEsTask.java` | 任务实体 |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| acc-es-server | ticket-server | 票务数据查询 | 查询票卡状态、交易记录 |
| acc-es-server | para-server | 参数查询 | 查询线路、车站、票价参数 |

---

## 七、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 八、源码文件索引

### 8.1 Java 源文件（共 85+ 个）

**控制器层 (8 个)**:
1. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsAccountController.java`
2. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsAssignController.java`
3. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsInfoController.java`
4. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsProcController.java`
5. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsReportController.java`
6. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/EsTaskController.java`
7. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/FileController.java`
8. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/controller/TaskPlanController.java`

**服务层 (12+ 个)**:
9-20. 见 5.2 文件清单

**Netty 通信层 (8 个)**:
21-28. 见 5.3 文件清单

**数据访问层 (12 个)**:
29-40. 见 5.4 文件清单

**实体类 (12 个)**:
41-52. 见 5.5 文件清单

**枚举类 (10 个)**:
53-62. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/enumns/*.java`

**工具类 (5 个)**:
63-67. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/util/*.java`

**启动类 (1 个)**:
68. `acc-es-server/src/main/java/com/chinasofti/huateng/acc/es/server/EsServerApplication.java`

### 8.2 资源文件

1. `acc-es-server/src/main/resources/application.properties`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/acc-es-server` 模块源码。

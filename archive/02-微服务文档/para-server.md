# para-server 微服务文档

> **模块路径**: `para-server/`
> **端口**: 9107
> **职责**: 参数管理服务，提供线路网络、票价、日历、票种等基础参数查询和导入
> **源码阅读范围**: `para-server/src/main/java/`、`para-server/src/main/resources/`
> **整理时间**: 2026-07-20

---

## 一、模块概述

### 1.1 核心职责

para-server 是 ITP 平台的参数管理服务，承担以下核心职责：

1. **线路网络参数** - 线路、车站、区间、区域、换乘信息查询
2. **票价参数** - 基础票价、票价矩阵、附加费、日票票价查询
3. **日历参数** - 特殊日期、时间段、票价日历查询
4. **票种参数** - 票种、芯片类型查询
5. **参数导入** - 支持线路网络、票价、日历、票种等参数批量导入
6. **车站名称查询** - 根据车站代码查询车站名称

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
server.port=9107
```

### 1.4 数据表

| 表名 | 说明 |
|------|------|
| `LINE_INFO` | 线路信息表 |
| `STATION_INFO` | 车站信息表 |
| `SECT_INFO` | 区间信息表 |
| `ZONE_INFO` | 区域信息表 |
| `ZONE_DTL` | 区域明细表 |
| `TSF_INFO` | 换乘信息表 |
| `BASE_FARE` | 基础票价表 |
| `FARE_MATRIX` | 票价矩阵表 |
| `ADD_PARA` | 附加费参数表 |
| `TICKET_FARE` | 日票票价表 |
| `FARE_TIME` | 票价日历表 |
| `SPECIAL_DATE` | 特殊日期表 |
| `TIME_INTERVAL` | 时间段表 |
| `TICKET_TYPE` | 票种表 |
| `CHIP_TYPE` | 芯片类型表 |
| `TOTAL_SALE_PART` | 总售分区表 |

---

## 二、接口清单

### 2.1 APP 参数接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO |
|---------|---------|---------|---------|---------|---------|
| - | 查询车站名称 | `/ci/app/requestStationName` | POST | `RequestStationNameReqDTO` | `RequestStationNameResult` |
| - | 查询参数版本 | `/ci/app/queryParaVersion` | POST | `QueryParaVersionReqDTO` | `QueryParaVersionResult` |

### 2.2 管理导入接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO |
|---------|---------|---------|---------|---------|---------|
| - | 导入线路网络 | `/manage/import/rowNetwork` | POST | `RowNetworkImportReqDTO` | `ParaImportResult` |
| - | 导入票价 | `/manage/import/rate` | POST | `RateImportReqDTO` | `ParaImportResult` |
| - | 导入日历 | `/manage/import/calendar` | POST | `CalendarImportReqDTO` | `ParaImportResult` |
| - | 导入票种 | `/manage/import/ticket` | POST | `TicketImportReqDTO` | `ParaImportResult` |

---

## 三、核心业务流程

### 3.1 车站名称查询流程

**业务逻辑**:

1. **参数校验** - 校验 `stationCode` 是否为空
2. **查询车站信息** - 根据 `stationCode` 查询 `STATION_INFO`
3. **返回结果** - 返回 `stationName`、`lineCode` 等

**调用方**: `ticket-server`、`online-server` 在需要转换车站代码为名称时调用

### 3.2 参数导入流程

**线路网络导入**:
```
管理平台
  → para-server (/manage/import/rowNetwork)
    → ParaFileImportService.importRowNetwork()
      → 1. 解析 Excel/CSV 文件
      → 2. 逐行解析线路、车站、区间、区域、换乘信息
      → 3. 批量写入数据库
      → 4. 返回导入结果
```

**票价导入**:
```
管理平台
  → para-server (/manage/import/rate)
    → ParaFileImportService.importRate()
      → 1. 解析 Excel/CSV 文件
      → 2. 逐行解析基础票价、票价矩阵、附加费
      → 3. 批量写入数据库
      → 4. 返回导入结果
```

**日历导入**:
```
管理平台
  → para-server (/manage/import/calendar)
    → ParaFileImportService.importCalendar()
      → 1. 解析 Excel/CSV 文件
      → 2. 逐行解析特殊日期、时间段
      → 3. 批量写入数据库
      → 4. 返回导入结果
```

**票种导入**:
```
管理平台
  → para-server (/manage/import/ticket)
    → ParaFileImportService.importTicket()
      → 1. 解析 Excel/CSV 文件
      → 2. 逐行解析票种、芯片类型
      → 3. 批量写入数据库
      → 4. 返回导入结果
```

---

## 四、数据模型

### 4.1 线路网络模型

| 表名 | 关键字段 | 说明 |
|------|---------|------|
| `LINE_INFO` | LINE_CODE, LINE_NAME | 线路信息 |
| `STATION_INFO` | STATION_CODE, LINE_CODE, STATION_NAME | 车站信息 |
| `SECT_INFO` | SECT_CODE, LINE_CODE, START_STATION, END_STATION | 区间信息 |
| `ZONE_INFO` | ZONE_CODE, ZONE_NAME | 区域信息 |
| `ZONE_DTL` | ZONE_CODE, STATION_CODE | 区域明细 |
| `TSF_INFO` | FROM_LINE, FROM_STATION, TO_LINE, TO_STATION | 换乘信息 |

### 4.2 票价模型

| 表名 | 关键字段 | 说明 |
|------|---------|------|
| `BASE_FARE` | SECT_CODE, FARE | 基础票价 |
| `FARE_MATRIX` | START_STATION, END_STATION, FARE | 票价矩阵 |
| `ADD_PARA` | SECT_CODE, ADD_FARE | 附加费 |
| `TICKET_FARE` | TICKET_TYPE, FARE | 日票票价 |

### 4.3 日历模型

| 表名 | 关键字段 | 说明 |
|------|---------|------|
| `FARE_TIME` | DATE, TIME_INTERVAL, FARE_TYPE | 票价日历 |
| `SPECIAL_DATE` | DATE, SPECIAL_TYPE | 特殊日期 |
| `TIME_INTERVAL` | TIME_INTERVAL, START_TIME, END_TIME | 时间段 |

### 4.4 票种模型

| 表名 | 关键字段 | 说明 |
|------|---------|------|
| `TICKET_TYPE` | TICKET_TYPE, TICKET_NAME | 票种信息 |
| `CHIP_TYPE` | CHIP_TYPE, CHIP_NAME | 芯片类型 |
| `TOTAL_SALE_PART` | SALE_PART_CODE, SALE_PART_NAME | 总售分区 |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `para-server/src/main/java/com/chinasofti/huateng/para/controller/AppParaController.java` | APP 参数接口入口 |
| `para-server/src/main/java/com/chinasofti/huateng/para/controller/ParaImportController.java` | 参数导入接口入口 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `para-server/src/main/java/com/chinasofti/huateng/para/service/AppParaService.java` | APP 参数服务接口 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/AppParaServiceImpl.java` | APP 参数服务实现 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/ParaFileImportService.java` | 参数文件导入服务接口 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/RateImportService.java` | 票价导入服务 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/CalendarImportService.java` | 日历导入服务 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/RowNetworkImportService.java` | 线路网络导入服务 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/TicketImportService.java` | 票种导入服务 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/RateParser.java` | 票价解析器 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/CalendarParser.java` | 日历解析器 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/RowNetworkParser.java` | 线路网络解析器 |
| `para-server/src/main/java/com/chinasofti/huateng/para/service/TicketParser.java` | 票种解析器 |

### 5.3 实体类

| 文件路径 | 说明 |
|---------|------|
| `para-server/src/main/java/com/chinasofti/huateng/para/entity/fare/*.java` | 票价相关实体 |
| `para-server/src/main/java/com/chinasofti/huateng/para/entity/network/*.java` | 线路网络相关实体 |
| `para-server/src/main/java/com/chinasofti/huateng/para/entity/calendar/*.java` | 日历相关实体 |
| `para-server/src/main/java/com/chinasofti/huateng/para/entity/ticket/*.java` | 票种相关实体 |

### 5.4 数据访问层

| 文件路径 | 说明 |
|---------|------|
| `para-server/src/main/java/com/chinasofti/huateng/para/mapper/fare/*.java` | 票价 Mapper |
| `para-server/src/main/java/com/chinasofti/huateng/para/mapper/network/*.java` | 线路网络 Mapper |
| `para-server/src/main/java/com/chinasofti/huateng/para/mapper/calendar/*.java` | 日历 Mapper |
| `para-server/src/main/java/com/chinasofti/huateng/para/mapper/ticket/*.java` | 票种 Mapper |

### 5.5 模型类

| 文件路径 | 说明 |
|---------|------|
| `para-server/src/main/java/com/chinasofti/huateng/para/model/*.java` | 导入结果模型 |

### 5.6 资源配置

| 文件路径 | 说明 |
|---------|------|
| `para-server/src/main/resources/application.properties` | 应用配置 |
| `para-server/src/main/resources/mapper/*.xml` | MyBatis Mapper XML |
| `para-server/src/main/resources/sql/*.sql` | 建表脚本 |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 调用时机 | 调用方法 |
|--------|---------|---------|
| ticket-server | 查询车站名称 | `paraClient.requestStationName()` |
| online-server | 查询车站名称 | `paraClient.requestStationName()` |
| fep-app-server | 需要参数时 | 通过 RPC 调用参数服务 |

---

## 七、待办事项

### 7.1 后续优化

1. 参数版本管理（PARA_VERSION）
2. 参数缓存优化
3. 导入文件格式校验增强

---

## 八、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 九、源码文件索引

### 9.1 Java 源文件（共 50+ 个）

**控制器层 (2 个)**:
1. `para-server/src/main/java/com/chinasofti/huateng/para/controller/AppParaController.java`
2. `para-server/src/main/java/com/chinasofti/huateng/para/controller/ParaImportController.java`

**服务层 (10+ 个)**:
- `para-server/src/main/java/com/chinasofti/huateng/para/service/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/service/*Parser.java`

**实体类 (15+ 个)**:
- `para-server/src/main/java/com/chinasofti/huateng/para/entity/fare/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/entity/network/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/entity/calendar/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/entity/ticket/*.java`

**数据访问层 (15+ 个)**:
- `para-server/src/main/java/com/chinasofti/huateng/para/mapper/fare/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/mapper/network/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/mapper/calendar/*.java`
- `para-server/src/main/java/com/chinasofti/huateng/para/mapper/ticket/*.java`

**启动类 (1 个)**:
- `para-server/src/main/java/com/chinasofti/huateng/ParaServer.java`

### 9.2 资源文件（共 20+ 个）

1. `para-server/src/main/resources/application.properties`
2. `para-server/src/main/resources/mapper/*.xml`
3. `para-server/src/main/resources/sql/*.sql`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/para-server` 模块源码。

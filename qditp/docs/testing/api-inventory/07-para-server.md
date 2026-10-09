# para-server 接口契约清单

> **模块**：`para-server`（线路车站 / 票价 / 风控 / 限购 / 参数文件导入）
> **容器端口**：9107（`server.port=9107`，`spring.application.name=para-server`，无 context-path）
> **网关前缀**：`/para-server/`（rewrite `/`，目标 `para-server-pufrl-svc:30026`）
> **NodePort**：30026
> **数据来源**：逐个 Controller 源文件 + model 模块 DTO + mapper XML，2026-09-22 核实

---

## 安全与测试关键结论

**结论一：`/para-server/` 前缀让全部 28 个端点对公网可达，包括 `/page/**` 管理后台的 15 个端点。这 15 个端点中有 PUT / POST / DELETE 写操作，且 para-server 没有 spring-security、没有全局拦截器兜底。** 压测和安全测试都要覆盖这批管理后台端点。写操作类端点（风险组增删改、风险规则增删改、退款周期增删改、购票限额更新、FTP 导入、文件上传）**绝不能在生产环境压测**。

**结论二：(a) 类 8 个 APP 参数查询端点有两条可达路径，报文形态完全不同：**
1. **裸 JSON 直连 para-server**：`POST http://172.20.211.23:30026/ci/app/<name>`，Content-Type `application/json`，body 直接是 DTO 的 JSON。
2. **form 报文经 fep-app**：`POST http://58.56.166.170:48000/fep-app/ci/app/<name>`，Content-Type `application/x-www-form-urlencoded`，参数含 `sign`/`charset`/`bizData` 等公共字段，业务参数在 `bizData` JSON 字符串里。fep-app 的 `AppParaController`（行33~61）用 `parseBizData` 解出 DTO 后通过 `ParaClient` RPC 转发到 para-server。

用例必须标明走哪条路径。**公网直连** para-server 等价于绕过接入层，该链路无验签。

---

## 数据依赖与压测陷阱：当前参数版本号

**已核实：(a) 的全部 8 个查询以及 (c) 的基础票价查询都走 `WHERE PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = ...)` 模式。** `PARA_TYPE='0001'` 对应路网拓扑（线路、车站），`PARA_TYPE='0004'` 对应费率（票价矩阵、基础票价）。

这意味着：
- **`TBL_PARA_VERSION.CURRENT_VER_NO` 指向的版本在明细表里必须真实存在，否则所有查询命中 0 行。**
- 改 `CURRENT_VER_NO` 做参数重导验证时，**绝不能用「当前版本减 1」**。明细表只保留少数几个历史版本（2026-09-08 实测 `TBL_LINE_INFO` 只有 28/41 两个版本号、`TBL_FARE_MATRIX` 只有 33/44 两个版本号），指向不存在的版本号等于把参数清空。改之前**必须**先 `SELECT PARA_VER_NO, COUNT(*) FROM <明细表> GROUP BY PARA_VER_NO` 查出真实存在的版本再回退。
- 压测环境启动前**必须**验证 `TBL_PARA_VERSION` 的 `CURRENT_VER_NO` 对应的明细行存在。

---

## 本文件端点总览

| 序号 | 分类 | 中文名 | HTTP + 对外 URL | 写操作 |
|---|---|---|---|---|
| 1 | (a) APP 参数 | 获取单程票最大购买张数 (IF8A-09) | POST `/para-server/ci/app/requestBuySinlgeTicketMaxNum` | 否 |
| 2 | (a) APP 参数 | 获取线路代码 (IF8A-07) | POST `/para-server/ci/app/requestLineCodeList` | 否 |
| 3 | (a) APP 参数 | 获取车站代码 (IF8A-08) | POST `/para-server/ci/app/requestStationCodeList` | 否 |
| 4 | (a) APP 参数 | 计算票价 (IF8A-10) | POST `/para-server/ci/app/requestTicketPriceByStation` | 否 |
| 5 | (a) APP 参数 | 获取线路站点代码版本 (IF8A-17) | POST `/para-server/ci/app/requestLineStationCodeVersion` | 否 |
| 6 | (a) APP 参数 | 查询车站名称 | POST `/para-server/ci/app/requestStationName` | 否 |
| 7 | (a) APP 参数 | 查询车站线路信息 | POST `/para-server/ci/app/requestStationLineInfo` | 否 |
| 8 | (a) APP 参数 | 批量查询车站名称 | POST `/para-server/ci/app/requestStationNameBatch` | 否 |
| 9 | (b) 参数导入 | FTP 参数扫描导入 | POST `/para-server/para/import/ftp` | 是 |
| 10 | (b) 参数导入 | FTP 参数扫描导入 (Quartz) | POST `/para-server/para/import/ftp/quartz` | 是 |
| 11 | (b) 参数导入 | 目录导入 (POST) | POST `/para-server/para/import/directory` | 是 |
| 12 | (b) 参数导入 | 目录导入 (GET) | GET `/para-server/para/import/directory` | 是 |
| 13 | (b) 参数导入 | 文件上传导入 | POST `/para-server/para/import/file` | 是 |
| 14 | (c) 管理后台 | 线路信息分页 | GET `/para-server/page/line-info` | 否 |
| 15 | (c) 管理后台 | 车站信息分页 | GET `/para-server/page/station-info` | 否 |
| 16 | (c) 管理后台 | 参数版本分页 | GET `/para-server/page/line-station-version` | 否 |
| 17 | (c) 管理后台 | 基础票价分页 | GET `/para-server/page/base-fare` | 否 |
| 18 | (c) 管理后台 | 基础票价车站选项 | GET `/para-server/page/base-fare/stations` | 否 |
| 19 | (c) 管理后台 | 基础票价线路选项 | GET `/para-server/page/base-fare/lines` | 否 |
| 20 | (c) 管理后台 | 获取购票限额 | GET `/para-server/page/single-ticket-purchase-limit` | 否 |
| 21 | (c) 管理后台 | 更新购票限额 | PUT `/para-server/page/single-ticket-purchase-limit` | 是 |
| 22 | (c) 管理后台 | 退款周期分页 | GET `/para-server/page/order-refund-cycle` | 否 |
| 23 | (c) 管理后台 | 新增退款周期 | POST `/para-server/page/order-refund-cycle` | 是 |
| 24 | (c) 管理后台 | 更新退款周期 | PUT `/para-server/page/order-refund-cycle/{ticketType}` | 是 |
| 25 | (c) 管理后台 | 删除退款周期 | DELETE `/para-server/page/order-refund-cycle/{ticketType}` | 是 |
| 26 | (c) 管理后台 | 风险组分页 | GET `/para-server/page/risk/groups` | 否 |
| 27 | (c) 管理后台 | 风险组全量选项 | GET `/para-server/page/risk/groups/options` | 否 |
| 28 | (c) 管理后台 | 新增风险组 | POST `/para-server/page/risk/groups` | 是 |
| 29 | (c) 管理后台 | 修改风险组 | PUT `/para-server/page/risk/groups/{groupId}` | 是 |
| 30 | (c) 管理后台 | 删除风险组 | DELETE `/para-server/page/risk/groups/{groupId}` | 是 |
| 31 | (c) 管理后台 | 风险规则分页 | GET `/para-server/page/risk/rules` | 否 |
| 32 | (c) 管理后台 | 新增风险规则 | POST `/para-server/page/risk/rules` | 是 |
| 33 | (c) 管理后台 | 修改风险规则 | PUT `/para-server/page/risk/rules/{ruleId}` | 是 |
| 34 | (c) 管理后台 | 删除风险规则 | DELETE `/para-server/page/risk/rules/{ruleId}` | 是 |
| 35 | (c) 管理后台 | 风控命中记录分页 | GET `/para-server/page/risk/control-logs` | 否 |

---

## (a) APP 参数查询 -- AppParaController

类级 `@RequestMapping("/ci/app")`，8 个 POST 端点。全部是 `@RequestBody` JSON，不带公共报文骨架（没有 sign/charset/bizData）。fep-app-server 经 `ParaClient` RPC 直连调用时已将公共报文剥掉。所有 DTO 均无 Bean Validation 注解（整个 para-server 的 `src/main` 下搜索 `jakarta.validation`/`@Valid`/`@NotNull`/`@NotBlank` 命中 0 条）。

### 1. 获取单程票最大购买张数 (IF8A-09)

- **对外 URL**：`POST /para-server/ci/app/requestBuySinlgeTicketMaxNum`
- **容器内路径**：`/ci/app/requestBuySinlgeTicketMaxNum`
- **另一条链路**：`POST /fep-app/ci/app/requestBuySinlgeTicketMaxNum`（form 报文，另有别名 `/fep-app/app/requestBuySinlgeTicketMaxNum` 和 `/fep-app/ci/app/requestBuySingleTicketMaxNum`，`fep-app-server/...AppParaController.java:33`）
- **Controller**：`AppParaController#requestBuySinlgeTicketMaxNum`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/para-server/src/main/java/com/chinasofti/huateng/para/controller/AppParaController.java:32`）
- **Content-Type**：`application/json`
- **入参形态**：无请求参数（方法签名无参）
- **公共报文骨架**：无（裸 JSON，不带 sign/bizData）

**请求字段** -- 无

**响应字段** -- `RequestBuySinlgeTicketMaxNumResult extends CommonResult`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/app/RequestBuySinlgeTicketMaxNumResult.java`）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码，0000 成功 |
| retMsg | String | 返回消息 |
| buySinlgeTicketMaxNum | String | 单次可购买的单程票最大张数 |

**压测备注**：只读查询，读 `SINGLE_TICKET_PURCHASE_LIMIT` 表。响应体量极小（单个数字）。无数据依赖（不走参数版本号过滤）。

### 2. 获取线路代码 (IF8A-07)

- **对外 URL**：`POST /para-server/ci/app/requestLineCodeList`
- **容器内路径**：`/ci/app/requestLineCodeList`
- **另一条链路**：`POST /fep-app/ci/app/requestLineCodeList`（form 报文）
- **Controller**：`AppParaController#requestLineCodeList`（`AppParaController.java:43`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody(required=false) RequestLineCodeListReqDTO`
- **公共报文骨架**：无

**请求字段** -- DTO `com.chinasofti.huateng.model.app.RequestLineCodeListReqDTO`（`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/model/src/main/java/com/chinasofti/huateng/model/app/RequestLineCodeListReqDTO.java`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| （无字段） | -- | -- | DTO 为空壳，接口规范未定义业务字段，允许传 `{}` 或 null |

**响应字段** -- `RequestLineCodeListResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| lineCodeRecord | `List<LineCodeRecordDTO>` | 线路代码记录列表 |

`LineCodeRecordDTO` 字段展开：

| 字段 | 类型 | 说明 |
|---|---|---|
| lineCode | String | 线路代码 |
| lineNameZH | String | 线路中文名称 |
| lineNameEN | String | 线路英文名称 |
| orderIndex | String | 展示排序序号（SQL 里用 LINE_CODE 填充） |

**压测备注**：只读查询，返回当前路网版本全部线路。响应体量取决于线路条数（青岛地铁当前约 6~8 条线路，体量很小）。**数据依赖**：`TBL_PARA_VERSION.CURRENT_VER_NO`（`PARA_TYPE='0001'`）必须指向 `TBL_LINE_INFO` 中存在的版本号。

### 3. 获取车站代码 (IF8A-08)

- **对外 URL**：`POST /para-server/ci/app/requestStationCodeList`
- **容器内路径**：`/ci/app/requestStationCodeList`
- **另一条链路**：`POST /fep-app/ci/app/requestStationCodeList`（form 报文）
- **Controller**：`AppParaController#requestStationCodeList`（`AppParaController.java:55`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody RequestStationCodeListReqDTO`
- **公共报文骨架**：无

**请求字段** -- DTO `com.chinasofti.huateng.model.app.RequestStationCodeListReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| lineCode | String | 无（代码无 Bean Validation，空值不会 400） | 线路代码；为空时返回全部线路车站 |

**响应字段** -- `RequestStationCodeListResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| stationCodeRecord | `List<StationCodeRecordDTO>` | 车站代码记录列表 |

`StationCodeRecordDTO` 字段展开：

| 字段 | 类型 | 说明 |
|---|---|---|
| lineCode | String | 所属线路代码 |
| stationCode | String | 车站代码 |
| stationNameZH | String | 车站中文名称 |
| stationNameEN | String | 车站英文名称 |
| transferYn | String | 是否换乘站，Y 是 / N 否（通过 `TBL_TSF_INFO` 子查询判断） |

**压测备注**：只读查询。**响应体量较大** -- lineCode 为空时返回全部车站（青岛地铁约 150+ 车站），需关注响应大小。每条记录还含一个换乘站子查询（EXISTS）。**数据依赖**：同上，`CURRENT_VER_NO`（`PARA_TYPE='0001'`）。

### 4. 计算票价 (IF8A-10)

- **对外 URL**：`POST /para-server/ci/app/requestTicketPriceByStation`
- **容器内路径**：`/ci/app/requestTicketPriceByStation`
- **另一条链路**：`POST /fep-app/ci/app/requestTicketPriceByStation`（form 报文）
- **Controller**：`AppParaController#requestTicketPriceByStation`（`AppParaController.java:67`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody RequestTicketPriceByStationReqDTO`
- **公共报文骨架**：无

**请求字段** -- DTO `com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| entryStationCode | String | 无（代码无 Bean Validation，空值不会 400） | 进站车站代码 |
| exitStationCode | String | 无（代码无 Bean Validation，空值不会 400） | 出站车站代码 |

**响应字段** -- `RequestTicketPriceByStationResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| ticketPrice | String | 票价 |

**压测备注**：只读查询，两步：先按进出站查 `TBL_FARE_MATRIX` 得费率等级，再按费率等级查 `TBL_BASE_FARE` 得票价。**数据依赖**：`CURRENT_VER_NO`（`PARA_TYPE='0004'`）。响应体量极小。

### 5. 获取线路站点代码版本 (IF8A-17)

- **对外 URL**：`POST /para-server/ci/app/requestLineStationCodeVersion`
- **容器内路径**：`/ci/app/requestLineStationCodeVersion`
- **另一条链路**：`POST /fep-app/ci/app/requestLineStationCodeVersion`（form 报文）
- **Controller**：`AppParaController#requestLineStationCodeVersion`（`AppParaController.java:79`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody(required=false) RequestLineStationCodeVersionReqDTO`
- **公共报文骨架**：无

**请求字段** -- DTO 为空壳（无字段）

**响应字段** -- `RequestLineStationCodeVersionResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| lineCodeIdx | String | 线路代码版本号 |
| stationCodeIdx | String | 车站代码版本号 |
| changeDate | String | 版本变更日期 |

**压测备注**：只读查询，读 `TBL_PARA_VERSION`。响应体量极小。

### 6. 查询车站名称

- **对外 URL**：`POST /para-server/ci/app/requestStationName`
- **容器内路径**：`/ci/app/requestStationName`
- **Controller**：`AppParaController#requestStationName`（`AppParaController.java:93`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody RequestStationNameReqDTO`
- **公共报文骨架**：无

**请求字段** -- DTO `com.chinasofti.huateng.model.app.RequestStationNameReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| stationCode | String | 无（代码无 Bean Validation，空值不会 400） | 车站代码 |

**响应字段** -- `RequestStationNameResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| stationCode | String | 车站代码 |
| stationName | String | 车站中文名称 |

**压测备注**：只读查询，内部服务用，ticket-server 查上一笔行程时补站名。**数据依赖**：`CURRENT_VER_NO`（`PARA_TYPE='0001'`）。

### 7. 查询车站线路信息

- **对外 URL**：`POST /para-server/ci/app/requestStationLineInfo`
- **容器内路径**：`/ci/app/requestStationLineInfo`
- **Controller**：`AppParaController#requestStationLineInfo`（`AppParaController.java:105`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody RequestStationLineInfoReqDTO`
- **公共报文骨架**：无

**请求字段** -- DTO `com.chinasofti.huateng.model.app.RequestStationLineInfoReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| stationCode | String | 无（代码无 Bean Validation，空值不会 400） | 车站代码 |

**响应字段** -- `RequestStationLineInfoResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| stationCode | String | 车站代码 |
| stationName | String | 车站中文名称 |
| lineCode | String | 所属线路代码 |
| lineName | String | 线路中文名称 |

**压测备注**：只读查询，LEFT JOIN `TBL_LINE_INFO`。**数据依赖**：`CURRENT_VER_NO`（`PARA_TYPE='0001'`）。

### 8. 批量查询车站名称

- **对外 URL**：`POST /para-server/ci/app/requestStationNameBatch`
- **容器内路径**：`/ci/app/requestStationNameBatch`
- **Controller**：`AppParaController#requestStationNameBatch`（`AppParaController.java:116`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody RequestStationNameBatchReqDTO`
- **公共报文骨架**：无

**请求字段** -- DTO `com.chinasofti.huateng.model.app.RequestStationNameBatchReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| stationCodes | `List<String>` | 无（代码无 Bean Validation，空值不会 400） | 车站代码列表 |

**响应字段** -- `RequestStationNameBatchResult`（注意：不继承 CommonResult，自带 retCode/retMsg）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| stationNameList | `List<RequestStationNameResult>` | 车站名称列表（每条含 stationCode + stationName） |

**压测备注**：只读查询，SQL 用 `IN` 展开。**响应体量**取决于传入的 stationCodes 数量；ticket-server 行程查询时会批量传入进出站代码。**数据依赖**：`CURRENT_VER_NO`（`PARA_TYPE='0001'`）。

---

## (b) 参数文件导入 -- ParaImportController

类级 `@RequestMapping("/para/import")`，5 个端点。这些端点均为写操作（触发 FTP 下载或本地文件解析并入库），绝不能在生产环境压测。

### 9. FTP 参数扫描导入

- **对外 URL**：`POST /para-server/para/import/ftp`
- **Controller**：`ParaImportController#importFromFtp`（`ParaImportController.java:52`）
- **Content-Type**：无请求体
- **入参形态**：无参数
- **响应**：`ParaFtpScanService.FtpScanResponse`（含 remoteDir / total / downloaded / imported / skipped / failed）
- **压测备注**：写操作，扫描 FTP 目录下载参数文件并入库。依赖 FTP 连接（`para.ftp.*` 配置）。

### 10. FTP 参数扫描导入 (Quartz 入口)

- **对外 URL**：`POST /para-server/para/import/ftp/quartz`
- **Controller**：`ParaImportController#importFromFtpForQuartz`（`ParaImportController.java:62`）
- **Content-Type**：无请求体
- **入参形态**：无参数
- **响应**：`CommonResult`（retCode `0000` 全部成功 / `9999` 存在失败文件，retMsg 含统计摘要）
- **压测备注**：写操作，web-admin Quartz 定时任务调用入口。

### 11. 目录导入 (POST)

- **对外 URL**：`POST /para-server/para/import/directory`
- **Controller**：`ParaImportController#importDirectory(DirectoryImportRequest)`（`ParaImportController.java:78`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody DirectoryImportRequest`（内部类，字段 `directory: String`）
- **响应**：`DirectoryImportResponse`（directory / total / success / failure / files 列表）
- **压测备注**：写操作，读取服务器本地目录下 `PRM.*` 文件并入库。

### 12. 目录导入 (GET)

- **对外 URL**：`GET /para-server/para/import/directory?directory=<path>`
- **Controller**：`ParaImportController#importDirectory(String)`（`ParaImportController.java:83`）
- **入参形态**：`@RequestParam("directory") String`
- **响应**：同上
- **压测备注**：写操作，功能同 #11 的 GET 形式。

### 13. 文件上传导入

- **对外 URL**：`POST /para-server/para/import/file`
- **Controller**：`ParaImportController#importFile`（`ParaImportController.java:88`）
- **Content-Type**：`multipart/form-data`
- **入参形态**：`@RequestParam("file") MultipartFile`，文件名必须以 `PRM.` 开头
- **响应**：`DirectoryImportResponse`
- **压测备注**：写操作，上传单个参数文件并入库。文件名校验：不以 `PRM.` 开头时返 `IllegalArgumentException`。

---

## (c) 管理后台 `/page/**`

### ParaPageController（类级 `@RequestMapping("/page")`，3 个端点）

### 14. 线路信息分页

- **对外 URL**：`GET /para-server/page/line-info?pageNum=1&pageSize=10`
- **Controller**：`ParaPageController#pageLineInfo`（`ParaPageController.java:25`）
- **参数**：`pageNum`（默认 1）、`pageSize`（默认 10）
- **响应**：`ResultVO<PageInfo<LineInfo>>`
- **压测备注**：只读，PageHelper 分页查 `TBL_LINE_INFO`。

`LineInfo` 字段：paraVerNo(Long) / lineCode(String) / lineNm(String) / lineENm(String)

### 15. 车站信息分页

- **对外 URL**：`GET /para-server/page/station-info?pageNum=1&pageSize=10`
- **Controller**：`ParaPageController#pageStationInfo`（`ParaPageController.java:32`）
- **参数**：`pageNum`（默认 1）、`pageSize`（默认 10）
- **响应**：`ResultVO<PageInfo<StationInfo>>`
- **压测备注**：只读。**响应体量较大** -- 不加筛选时返回全路网全版本车站。

`StationInfo` 字段：paraVerNo(Long) / stationCode(String) / ownerLineId(String) / ownerIncomeId(String) / stationType(String) / stationNm(String) / stationENm(String) / transferYn(String)

### 16. 参数版本分页

- **对外 URL**：`GET /para-server/page/line-station-version?pageNum=1&pageSize=10`
- **Controller**：`ParaPageController#pageLineStationVersion`（`ParaPageController.java:39`）
- **参数**：`pageNum`（默认 1）、`pageSize`（默认 10）
- **响应**：`ResultVO<PageInfo<LineStationVersion>>`

`LineStationVersion` 字段：paraTypeName(String) / versionNo(Long) / fileName(String) / updateTime(LocalDateTime) / effectiveTime(String)

### BaseFarePageController（类级 `@RequestMapping("/page/base-fare")`，3 个端点）

### 17. 基础票价分页

- **对外 URL**：`GET /para-server/page/base-fare?pageNum=1&pageSize=10&entryStationCode=&exitStationCode=&entryLineCode=&exitLineCode=`
- **Controller**：`BaseFarePageController#page`（`BaseFarePageController.java:28`）
- **参数**：`entryStationCode`(可选) / `exitStationCode`(可选) / `entryLineCode`(可选) / `exitLineCode`(可选) / `pageNum`(默认1) / `pageSize`(默认10)
- **响应**：`ResultVO<PageInfo<BaseFarePageView>>`
- **压测备注**：只读。查当前费率版本的票价矩阵 JOIN 车站名与基础票价。**数据依赖**：`CURRENT_VER_NO`（`PARA_TYPE='0001'` + `'0004'`）。**响应体量大** -- 全部进出站组合的票价行，不加筛选时可达数千行。

`BaseFarePageView` 字段：entryStationCode(String) / entryStationName(String) / exitStationCode(String) / exitStationName(String) / fareTier(Integer) / ticketPrice(Integer)

### 18. 基础票价车站选项

- **对外 URL**：`GET /para-server/page/base-fare/stations?lineCode=`
- **Controller**：`BaseFarePageController#listStations`（`BaseFarePageController.java:39`）
- **参数**：`lineCode`(可选，按线路过滤)
- **响应**：`ResultVO<List<BaseFareStationOption>>`
- **压测备注**：只读。**数据依赖**：`CURRENT_VER_NO`（`PARA_TYPE='0001'`）。

`BaseFareStationOption` 字段：stationCode(String) / stationName(String)

### 19. 基础票价线路选项

- **对外 URL**：`GET /para-server/page/base-fare/lines`
- **Controller**：`BaseFarePageController#listLines`（`BaseFarePageController.java:45`）
- **参数**：无
- **响应**：`ResultVO<List<BaseFareLineOption>>`

`BaseFareLineOption` 字段：lineCode(String) / lineName(String)

### SingleTicketPurchaseLimitController（类级 `@RequestMapping("/page/single-ticket-purchase-limit")`，2 个端点）

### 20. 获取当前购票限额

- **对外 URL**：`GET /para-server/page/single-ticket-purchase-limit`
- **Controller**：`SingleTicketPurchaseLimitController#getCurrent`（`SingleTicketPurchaseLimitController.java:23`）
- **参数**：无
- **响应**：`ResultVO<SingleTicketPurchaseLimit>`

`SingleTicketPurchaseLimit` 字段：configKey(String) / maxPurchaseQuantity(Integer) / createTime(LocalDateTime) / updateTime(LocalDateTime) / version(Integer)

### 21. 更新购票限额

- **对外 URL**：`PUT /para-server/page/single-ticket-purchase-limit`
- **Controller**：`SingleTicketPurchaseLimitController#update`（`SingleTicketPurchaseLimitController.java:28`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody SingleTicketPurchaseLimit`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**，改 `SINGLE_TICKET_PURCHASE_LIMIT` 表，影响 IF8A-09 的返回值。

### OrderRefundCycleController（类级 `@RequestMapping("/page/order-refund-cycle")`，4 个端点）

### 22. 退款周期分页

- **对外 URL**：`GET /para-server/page/order-refund-cycle?ticketType=&pageNum=1&pageSize=10`
- **Controller**：`OrderRefundCycleController#page`（`OrderRefundCycleController.java:29`）
- **参数**：`ticketType`(可选) / `pageNum`(默认1) / `pageSize`(默认10)
- **响应**：`ResultVO<PageInfo<OrderRefundCycle>>`

`OrderRefundCycle` 字段：ticketType(String, 业务主键) / autoRefundPeriod(Integer, 天) / createTime(LocalDateTime) / updateTime(LocalDateTime) / remark(String)

### 23. 新增退款周期

- **对外 URL**：`POST /para-server/page/order-refund-cycle`
- **Controller**：`OrderRefundCycleController#create`（`OrderRefundCycleController.java:37`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody OrderRefundCycle`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。

### 24. 更新退款周期

- **对外 URL**：`PUT /para-server/page/order-refund-cycle/{ticketType}`
- **Controller**：`OrderRefundCycleController#update`（`OrderRefundCycleController.java:43`）
- **Content-Type**：`application/json`
- **入参形态**：`@PathVariable String ticketType` + `@RequestBody OrderRefundCycle`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。路径中的 ticketType 不可修改。

### 25. 删除退款周期

- **对外 URL**：`DELETE /para-server/page/order-refund-cycle/{ticketType}`
- **Controller**：`OrderRefundCycleController#delete`（`OrderRefundCycleController.java:49`）
- **入参形态**：`@PathVariable String ticketType`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。

### RiskManagementController（类级 `@RequestMapping("/page/risk")`，10 个端点）

### 26. 风险组分页

- **对外 URL**：`GET /para-server/page/risk/groups?groupName=&pageNum=1&pageSize=10`
- **Controller**：`RiskManagementController#pageGroups`（`RiskManagementController.java:36`）
- **参数**：`groupName`(可选) / `pageNum`(默认1) / `pageSize`(默认10)
- **响应**：`ResultVO<PageInfo<RiskGroup>>`

`RiskGroup` 字段：groupId(Long) / groupName(String) / groupDesc(String) / createTime(LocalDateTime) / updateTime(LocalDateTime)

### 27. 风险组全量选项

- **对外 URL**：`GET /para-server/page/risk/groups/options`
- **Controller**：`RiskManagementController#groupOptions`（`RiskManagementController.java:44`）
- **参数**：无
- **响应**：`ResultVO<List<RiskGroup>>`

### 28. 新增风险组

- **对外 URL**：`POST /para-server/page/risk/groups`
- **Controller**：`RiskManagementController#createGroup`（`RiskManagementController.java:48`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody RiskGroup`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。

### 29. 修改风险组

- **对外 URL**：`PUT /para-server/page/risk/groups/{groupId}`
- **Controller**：`RiskManagementController#updateGroup`（`RiskManagementController.java:52`）
- **入参形态**：`@PathVariable Long groupId` + `@RequestBody RiskGroup`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。

### 30. 删除风险组

- **对外 URL**：`DELETE /para-server/page/risk/groups/{groupId}`
- **Controller**：`RiskManagementController#deleteGroup`（`RiskManagementController.java:57`）
- **入参形态**：`@PathVariable Long groupId`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。删除前校验无关联规则。

### 31. 风险规则分页

- **对外 URL**：`GET /para-server/page/risk/rules?ruleId=&ruleName=&groupId=&pageNum=1&pageSize=10`
- **Controller**：`RiskManagementController#pageRules`（`RiskManagementController.java:62`）
- **参数**：`ruleId`(可选) / `ruleName`(可选) / `groupId`(可选) / `pageNum`(默认1) / `pageSize`(默认10)
- **响应**：`ResultVO<PageInfo<RiskRule>>`

`RiskRule` 字段：ruleId(String) / groupId(Long) / groupName(String, 关联查询) / ruleName(String) / riskLevel(Integer, 1~5) / riskLimitValue(BigDecimal) / managerCode(String) / remark(String) / createTime(LocalDateTime) / updateTime(LocalDateTime)

### 32. 新增风险规则

- **对外 URL**：`POST /para-server/page/risk/rules`
- **Controller**：`RiskManagementController#createRule`（`RiskManagementController.java:72`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody RiskRule`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。规则编号创建后不可变更。

### 33. 修改风险规则

- **对外 URL**：`PUT /para-server/page/risk/rules/{ruleId}`
- **Controller**：`RiskManagementController#updateRule`（`RiskManagementController.java:76`）
- **入参形态**：`@PathVariable String ruleId` + `@RequestBody RiskRule`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。

### 34. 删除风险规则

- **对外 URL**：`DELETE /para-server/page/risk/rules/{ruleId}`
- **Controller**：`RiskManagementController#deleteRule`（`RiskManagementController.java:81`）
- **入参形态**：`@PathVariable String ruleId`
- **响应**：`ResultVO<Void>`
- **压测备注**：**写操作**。历史命中日志仍保留规则编号。

### 35. 风控命中记录分页

- **对外 URL**：`GET /para-server/page/risk/control-logs?cardId=&userOrderNo=&ruleId=&riskHitTimeBegin=&riskHitTimeEnd=&pageNum=1&pageSize=10`
- **Controller**：`RiskManagementController#pageControlLogs`（`RiskManagementController.java:86`）
- **参数**：`cardId`(可选) / `userOrderNo`(可选) / `ruleId`(可选) / `riskHitTimeBegin`(可选) / `riskHitTimeEnd`(可选) / `pageNum`(默认1) / `pageSize`(默认10)
- **响应**：`ResultVO<PageInfo<RiskControlLog>>`

`RiskControlLog` 字段：logId(Long) / cardId(String) / userOrderNo(String) / ruleId(String) / ruleName(String, 左关联) / riskHitTime(LocalDateTime) / createTime(LocalDateTime)

**压测备注**：只读，审计数据仅允许查询。

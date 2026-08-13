# findTravelList 乘车记录查询功能实现计划

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 完成并验证 `/channel/findTravelList` 支付宝乘车记录查询链路，覆盖请求入口、支付流水分页查询、进出站详情补全、响应组装与测试。

**Architecture:** 在现有 fep-alipay-server 入口基础上，串联 alipay-pay-sign-server 的支付流水分页查询与 ticket-server 的进出站详情查询；以现有 `AlipayTripFindTravelListReqDTO/RespDTO`、`AlipayTripTravelRecordDTO` 为核心数据结构，保持模块边界不变。

**Tech Stack:** Java 21、Spring Boot 3.2.6、Fastjson2、RPC JSON 调用、模块化多服务工程。

---

## 当前上下文

- 入口已存在：`fep-alipay-server/.../FepAlipayTripController.findTravelList`
- 核心编排已存在：`fep-alipay-server/.../AlipayTripServiceImpl.findTravelList`、`buildTravelRecord`
- 下游接口已存在：
  - `alipay-pay-sign-server` 支付流水分页查询
  - `ticket-server` 进出站详情查询
- DTO 已存在：请求、响应、乘车记录、支付流水、详情请求/响应
- 测试现状：`fep-app-server` 下有相关测试；`fep-alipay-server` 下未看到对应 service 单测
- 已完成：`ALIPAY_PAY_LOG.invoice` 字段已添加到实体、Mapper XML、查询条件及 `selectByOrderNo` 透传

---

## 实施计划

### Task 1: 梳理现有链路并补齐接口文档

**Objective:** 确认 `/channel/findTravelList` 现有实现与文档一致，识别缺口。

**Files:**
- Read: `docs/03-接口文档/接口规范正文/支付宝出行接口/findTravelList-调用链路图.md`
- Read: `docs/03-接口文档/接口规范正文/支付宝出行接口/ALIPAY-071-支付宝出行-查询乘车记录.md`
- Read: `docs/03-接口文档/接口规范正文/支付宝出行接口/ALIPAY-005-支付宝出行-查询乘车记录.md`

**Step 1: 读取现有链路图与规范**

确认字段映射：
- 请求：`thirdUserId`、`page`、`size`、`startDate`、`endDate`
- 支付流水：`orderNo`、`channelOrderNo`、`payStatus`、`entryId`、`exitId`、`cardId`
- 详情：`entryStationName`、`entryDate`、`exitStationName`、`exitDate`、`payAmount`、`totalAmount`

**Step 2: 核对代码映射**

核对 `fep-alipay-server/.../AlipayTripServiceImpl.java:312-385` 与 `:390-443` 的字段填充是否符合规范。

**Step 3: 记录缺口**

将缺失项写入 `.hermes/plans/findTravelList-gap.md`，例如：
- 是否有 `debitRequestResult`、`invoice` 筛选未落地
- ticket-server 失败时是否要返回部分成功
- 异常码是否与规范一致

---

### Task 2: 补齐请求/响应 DTO 与字段一致性

**Objective:** 确保 DTO 字段与链路图、R6 规范一致。

**Files:**
- Read: `model/src/main/java/com/chinasofti/huateng/model/alipaytrip/AlipayTripFindTravelListReqDTO.java`
- Modify: `.../model/alipaytrip/AlipayTripFindTravelListRespDTO.java`
- Modify: `.../model/alipaytrip/AlipayTripTravelRecordDTO.java`
- Modify: `.../model/alipaytrip/AlipayTripFindTravelDetailRespDTO.java`

**Request 字段（已存在）：**
- `thirdUserId`：第三方用户ID
- `page`：页码，从0开始
- `size`：每页大小
- `debitRequestResult`：扣款请求结果筛选（可选）
- `invoice`：发票状态筛选（可选）
- `startDate`：开始日期（可选）
- `endDate`：结束日期（可选）

**Step 1: 读取现有 DTO**

确认字段名、类型、注释。`AlipayTripFindTravelListReqDTO` 已包含全部请求字段。

**Step 2: 补全缺失字段**

如规范中存在但 DTO 缺失的字段，补齐 getter/setter。

**Step 3: 运行编译检查**

```bash
cd /Users/tuanjie/workspace/company/chinasofti/qd/qditp
mvn -pl fep-alipay-server -am compile -q
```

Expected: 编译成功。

---

### Task 2.5: 将 `invoice` 从支付流水透传到 `findTravelList` 响应

**Objective:** 把 `ALIPAY_PAY_LOG.invoice` 经 RPC 传到 `/channel/findTravelList` 响应体。

**Files:**
- Modify: `model/src/main/java/com/chinasofti/huateng/model/alipaytrip/AlipayPayLogDTO.java`
- Modify: `fep-alipay-server/.../AlipayTripServiceImpl.java` 的 `buildTravelRecord`
- Modify: `fep-alipay-server/.../model/alipaytrip/AlipayTripTravelRecordDTO.java`

**Step 1: 给 model DTO 加字段**

在 `AlipayPayLogDTO` 增加 `private String invoice;` 及 getter/setter。

**Step 2: 给乘车记录 DTO 加字段**

在 `AlipayTripTravelRecordDTO` 增加 `private String invoice;` 及 getter/setter。

**Step 3: 在 `buildTravelRecord` 中赋值**

```java
record.setInvoice(payLog.get("invoice") != null ? (String) payLog.get("invoice") : null);
```

**Step 4: 编译检查**

```bash
cd /Users/tuanjie/workspace/company/chinasofti/qd/qditp
mvn -pl fep-alipay-server -am compile -q
```

Expected: 编译成功。

---

### Task 2.6: 将请求筛选字段下发到 alipay-pay-sign-server

**Objective:** 让 `debitRequestResult` 和 `invoice` 从 `/channel/findTravelList` 请求传递到支付流水分页查询。

**Files:**
- Modify: `fep-alipay-server/.../AlipayTripServiceImpl.java` 的 `findTravelList`
- Modify: `rpc/src/main/java/com/chinasofti/huateng/rpc/alipay/paysign/AlipayPaySignClient.java`
- Modify: `alipay-pay-sign-server/.../service/AlipayPaySignService.java`
- Modify: `alipay-pay-sign-server/.../service/impl/AlipayPaySignServiceImpl.java`
- Modify: `alipay-pay-sign-server/.../mapper/AlipayPayLogMapper.java`
- Modify: `alipay-pay-sign-server/.../controller/AlipayPayLogController.java`

**Step 1: 在 RPC 客户端增加筛选参数**

修改 `AlipayPaySignClient.selectAlipayPayLogListForTravel` 签名，增加 `debitRequestResult`、`invoice` 参数，并在 JSON 请求中传递。

**Step 2: 在 Service 接口增加筛选参数**

修改 `AlipayPaySignService.selectAlipayPayLogListForTravel` 和 `countAlipayPayLogListForTravel` 默认方法，透传新参数。

**Step 3: 在 Service 实现传递到 Mapper**

修改 `AlipayPaySignServiceImpl` 中调用 `selectAlipayPayLogList` 和 `countAlipayPayLogList` 的地方，传入 `debitRequestResult`、`invoice`。

**Step 4: 在 Mapper 接口增加参数**

修改 `AlipayPayLogMapper.selectAlipayPayLogList` 和 `countAlipayPayLogList`，增加 `@Param("debitRequestResult")` 和 `@Param("invoice")`。

**Step 5: 在 Controller 提取参数**

修改 `AlipayPayLogController.travelListPost`，从 JSON 中提取 `debitRequestResult` 和 `invoice`，传给 `buildTravelListResult`。

**Step 6: 在 fep-alipay-server 组装参数**

修改 `AlipayTripServiceImpl.findTravelList`，将 `request.getDebitRequestResult()` 和 `request.getInvoice()` 传给 RPC 调用。

**Step 7: 编译检查**

```bash
cd /Users/tuanjie/workspace/company/chinasofti/qd/qditp
mvn -pl fep-alipay-server -am compile -q
```

Expected: 编译成功。

---

### Task 3: 完善 `buildDetailRequest` 的 entryId/exitId 拆段逻辑

**Objective:** 保证 `entryId/exitId` 按 `thirdUserId + HANDLE_DATE_TIME(14位) + TRX_TYPE(2位)` 正确拆分。

**Files:**
- Modify: `fep-alipay-server/.../AlipayTripServiceImpl.java:534-544`

**Step 1: 读取现有拆段代码**

当前实现依赖 `transactionId.length() > thirdUserId.length() + 2`。

**Step 2: 增加边界校验**

- 当长度不足时，记录 warn 并返回空 detail 请求
- 当 `remain` 长度不足 16 时，同样 warn

**Step 3: 增加单测**

Create: `fep-alipay-server/src/test/java/.../AlipayTripServiceImplFindTravelListTest.java`

```java
@Test
void buildDetailRequest_normal() {
    AlipayTripFindTravelDetailReqDTO req = service.buildDetailRequest("user123", "user12320240101120000AB", "card1");
    assertEquals("20240101120000", req.getHandleDateTime());
    assertEquals("AB", req.getTrxType());
    assertEquals("card1", req.getCardId());
}
```

**Step 4: 运行测试**

```bash
mvn -pl fep-alipay-server test -Dtest=AlipayTripServiceImplFindTravelListTest -q
```

Expected: PASS。

---

### Task 4: 补全 `findTravelList` 的异常处理与日志

**Objective:** 让异常分支与规范一致，且可观测。

**Files:**
- Modify: `fep-alipay-server/.../AlipayTripServiceImpl.java:312-385`

**Step 1: 明确异常码映射**

- `thirdUserId` 为空 → `8001`
- `alipay-paySign-server` 返回空 → `9999`
- ticket-server 异常 → 记录 warn，该条跳过，不中断整体

**Step 2: 增加 ticket-server 失败容错**

当前 `buildTravelRecord` 若 ticket 调用异常会抛到外层 catch，导致整条记录跳过。改为在 `buildTravelRecord` 内部 catch，仅日志 warn。

**Step 3: 增加日志字段**

记录 `pageNum/pageSize/total`，便于排查分页问题。

**Step 4: 编译并运行相关测试**

```bash
mvn -pl fep-alipay-server test -Dtest=AlipayTripServiceImplFindTravelListTest -q
```

---

### Task 5: 为 `findTravelList` 增加 service 层单测

**Objective:** 覆盖成功路径、空列表、参数非法、ticket-server 异常等场景。

**Files:**
- Create: `fep-alipay-server/src/test/java/.../AlipayTripServiceImplFindTravelListTest.java`

**Step 1: 写失败测试**

```java
@Test
void findTravelList_invalidParam_returns8001() {
    AlipayTripFindTravelListReqDTO req = new AlipayTripFindTravelListReqDTO();
    req.setThirdUserId("");
    AlipayTripFindTravelListRespDTO resp = service.findTravelList(req);
    assertEquals("8001", resp.getRetCode());
}
```

**Step 2: 运行验证失败**

```bash
mvn -pl fep-alipay-server test -Dtest=AlipayTripServiceImplFindTravelListTest#findTravelList_invalidParam_returns8001 -q
```

Expected: FAIL（若现有实现已返回 8001，则直接进入下一步）。

**Step 3: Mock 下游写成功场景**

Mock `alipayPaySignClient.selectAlipayPayLogListForTravel` 返回支付流水，Mock `ticketClient.alipayTripFindTravelDetail` 返回进出站详情，断言响应分页与记录字段。

**Step 4: 增加 ticket-server 异常场景**

Mock ticket 抛异常，断言整条记录不加入列表，其他记录正常。

**Step 5: 运行全量测试**

```bash
mvn -pl fep-alipay-server test -Dtest=AlipayTripServiceImplFindTravelListTest -q
```

Expected: 全部 PASS。

---

### Task 6: Controller 层参数校验与单测

**Objective:** 确认入口参数解析、日志、异常兜底正确。

**Files:**
- Read: `fep-alipay-server/.../FepAlipayTripController.java:113-124`
- Modify（如需要）：增加统一异常处理或参数非空校验

**Step 1: 读取 controller 代码**

确认 `@ModelAttribute CommonFormRequest` 解析 `bizData` 为 `AlipayTripFindTravelListReqDTO`。

**Step 2: 增加 controller 单测**

Create: `fep-alipay-server/src/test/java/.../FepAlipayTripControllerTest.java`

```java
@Test
void findTravelList_mapsBizData() throws Exception {
    mockMvc.perform(post("/channel/findTravelList")
            .param("bizData", "{\"thirdUserId\":\"u1\",\"page\":\"0\",\"size\":\"10\"}"))
            .andExpect(status().isOk());
}
```

**Step 3: 运行测试**

```bash
mvn -pl fep-alipay-server test -Dtest=FepAlipayTripControllerTest -q
```

Expected: PASS。

---

### Task 7: 对齐 alipay-pay-sign-server 与 ticket-server 接口契约

**Objective:** 确认下游接口字段与当前调用一致。

**Files:**
- Read: `alipay-pay-sign-server/.../AlipayPaySignService.java` 中 `selectAlipayPayLogListForTravel`
- Read: `alipay-pay-sign-server/.../AlipayPayLogController.java` 中 `/api/payment/payLog/travelList`
- Read: `ticket-server/.../TicketTransServiceImpl.java` 中 `alipayTripFindTravelDetail`
- Read: `ticket-server/.../AlipayTripController.java` 中 `/ci/channel/findTravelDetail`

**Step 1: 核对字段名**

- 支付流水返回：`list`、`total`、`pageNum`、`pageSize`、`totalPage`
- ticket 详情返回：`entryStationName`、`entryDate`、`exitStationName`、`exitDate`、`payAmount`、`totalAmount`

**Step 2: 记录不一致项**

如有不一致，在 `.hermes/plans/findTravelList-gap.md` 记录。

**Step 3: 如有一致性问题，提交修改建议**

不直接修改下游，除非当前实现已明显错误。

---

### Task 8: 集成验证与回归测试

**Objective:** 端到端验证链路可用。

**Files:**
- Run: 相关模块测试
- Run: `fep-alipay-server` 启动健康检查（如可能）

**Step 1: 运行模块测试**

```bash
mvn -pl fep-alipay-server test -q
```

**Step 2: 运行 ticket-server 与 alipay-pay-sign-server 相关测试**

```bash
mvn -pl alipay-pay-sign-server test -q
mvn -pl ticket-server test -q
```

**Step 3: 检查接口规范对照表**

Read: `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md`

确认 `/channel/findTravelList` 状态为“完整实现”。

---

## 文件变更清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `fep-alipay-server/.../AlipayTripServiceImpl.java` | 修改 | 补全异常处理、日志、容错 |
| `fep-alipay-server/.../model/alipaytrip/*.java` | 修改 | 字段一致性 |
| `model/src/main/java/com/chinasofti/huateng/model/alipaytrip/AlipayPayLogDTO.java` | 修改 | 增加 `invoice` 字段 |
| `fep-alipay-server/.../model/alipaytrip/AlipayTripTravelRecordDTO.java` | 修改 | 增加 `invoice` 字段 |
| `alipay-pay-sign-server/.../entity/AlipayPayLog.java` | 修改 | 增加 `invoice` 字段 |
| `alipay-pay-sign-server/.../service/impl/AlipayPaySignServiceImpl.java` | 修改 | `selectByOrderNo` 透传 `invoice` |
| `alipay-pay-sign-server/.../mapper/AlipayPayLogMapper.xml` | 修改 | `invoice` 列映射与查询条件 |
| `fep-alipay-server/src/test/.../AlipayTripServiceImplFindTravelListTest.java` | 新增 | service 单测 |
| `fep-alipay-server/src/test/.../FepAlipayTripControllerTest.java` | 新增 | controller 单测 |
| `.hermes/plans/findTravelList-gap.md` | 新增 | 缺口记录 |

## 验证清单

- [ ] `mvn -pl fep-alipay-server test` 全部通过
- [ ] `mvn -pl alipay-pay-sign-server test` 全部通过
- [ ] `mvn -pl ticket-server test` 全部通过
- [ ] 接口规范对照表中 `/channel/findTravelList` 标记为“完整实现”
- [ ] `buildDetailRequest` 边界情况单测通过
- [ ] `ALIPAY_PAY_LOG.invoice` 已映射到查询结果、DTO、并透传到 `/channel/findTravelList` 响应

## 风险与开放问题

- ticket-server 若返回异常码但非抛异常，当前 `buildTravelRecord` 已按 `retCode` 处理；若未来改为抛异常，需补充容错。
- `debitRequestResult`、`invoice` 筛选当前仅在日志中记录；`invoice` 字段已实现透传，如业务后续需要可按条件过滤。
- entryId/exitId 格式若发生变化，`buildDetailRequest` 需同步调整。

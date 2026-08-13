# fep-alipay-server 支付/黑名单/解约接口重新设计方案

> **For Hermes:** 本文件为计划草案，仅作设计说明，不直接改代码。

**Goal:** 在不改变 `fep-alipay-server` 对外 URL 的前提下，重新梳理 `/addBlackList`、`/api/payment/executeTermination`、`/api/payment/requestRefund`、`/api/payment/payQuery`、`/api/payment/requestPay` 五条接口的参数规范、返回语义、异常映射和日志链路，使其可追溯、可排障、与下游 RPC 责任边界清晰。

**Architecture:** 以 `AlipayPaymentService` / `AlipayQueryService` 为边界，控制器只做参数校验、解码、请求/响应日志；Service 层负责业务校验、RPC 映射、异常兜底；返回结构按领域语义化，避免复用黑名单结果类型承载解约执行结果。

**Tech Stack:** Java 21, Spring Boot 3.2.6, fastjson2, Hutool JSON, Maven 3.9.10

---

## 当前状态

- `/admin/payment/addBlackList`：位于 `FepAlipayTripPaymentController`，路径 `/admin/payment/addBlackList`，接收 `AddBlackListReqDTO`，委托 `AlipayPaymentService.addBlackListForAlipay`；当前实现已有基本日志，但 RPC 返回与响应映射较粗。
- `/admin/payment/executeTermination`：位于 `FepAlipayTripPaymentController`，路径 `/admin/payment/executeTermination`，接收 `agreementCode`，委托 `AlipayPaymentService.executeTermination`；当前返回 `BlackListOperateResult`，语义错误。
- `/admin/payment/requestRefund`：位于 `FepAlipayTripPaymentController`，路径 `/admin/payment/requestRefund`，接收 `CommonFormRequest`，解码为 `AlipayTripRequestRefundReqDTO`，委托 `AlipayPaymentService.requestRefund`；当前已有日志，但异常信息粒度不够。
- `/admin/payment/payQuery`：位于 `FepAlipayTripPaymentController`，路径 `/admin/payment/payQuery`，接收 `CommonFormRequest`，解码为 `AlipayTripPayQueryReqDTO`，委托 `AlipayQueryService.payQuery`；当前返回空校验较细，但缺少下游 RPC 字段逐项映射。
- `/api/payment/requestPay`：实际位于 `alipay-pay-sign-server` 的 `AlipayTripPaymentController`，路径 `/api/payment/requestPay`，不在 `fep-alipay-server`；如需改造需单独说明。

> **已完成 Phase 0：** 以上 4 个 `fep-alipay-server` 接口已完成前缀迁移，统一为 `/admin/payment/*`，与支付宝交通小程序接口（`/channel/*`）明确区分。`maven-compiler-plugin` 已升级到 `3.13.0`。

---

## 计划

### Phase 0：URL 前缀统一（Web 管理控制台隔离）
#### Task 0：控制器前缀迁移
- Modify: `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripPaymentController.java`
- 类级 `@RequestMapping` 从 `/api/payment` 改为 `/admin/payment`
- 接口路径同步调整：
  - `/api/payment/payQuery` → `/admin/payment/payQuery`
  - `/api/payment/requestRefund` → `/admin/payment/requestRefund`
  - `/api/payment/addBlackList` → `/admin/payment/addBlackList`
  - `/api/payment/executeTermination` → `/admin/payment/executeTermination`
- 注意：`/api/payment` 仍由 `alipay-pay-sign-server` 使用，保持其 URL 不变，仅修改 `fep-alipay-server` 前缀

### Phase 1：返回结构语义化
#### Task 1：新增解约执行结果 DTO
- Create: `model/src/main/java/com/chinasofti/huateng/model/alipaytrip/TerminationExecuteResult.java`
- 字段：`agreementCode`、`retCode`、`retMsg`、`status`
- status 枚举：`PENDING` / `COMPLETED` / `FAIL` / `TERMINATED`

#### Task 2：调整 Service 接口返回类型
- Modify: `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayPaymentService.java`
- `executeTermination(String agreementCode)` 返回类型从 `BlackListOperateResult` 改为 `TerminationExecuteResult`
- 同步修改 `AlipayTripService` 门面接口与实现

### Phase 2：控制器层请求/响应日志补齐
#### Task 3：补齐 4 个控制器的响应日志
- Modify: `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripPaymentController.java`
- 在 `/admin/payment/payQuery`、`/admin/payment/requestRefund`、`/admin/payment/executeTermination` 补充响应日志
- `/admin/payment/addBlackList` 已有响应日志，保留

#### Task 4：补充请求参数校验与解码日志
- Modify: `FepAlipayTripPaymentController.java`
- `/admin/payment/payQuery`、`/admin/payment/requestRefund` 在 `JSON.parseObject` 后补充解码后业务参数日志

### Phase 3：Service 层业务校验与 RPC 映射增强
#### Task 5：重构退款申请
- Modify: `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayPaymentServiceImpl.java`
- `requestRefund`：
  - 校验 `orderNo` 和 `refundAmount` 非空
  - RPC 调用 `alipayPaySignClient.alipayTripRequestRefund(request)`
  - 空响应时返回 `FAIL` 并记录错误日志
  - 异常时返回 `FAIL`，消息携带异常信息
  - 返回前记录 RPC 响应和最终响应

#### Task 6：重构黑名单添加
- Modify: `AlipayPaymentServiceImpl.java`
- `addBlackListForAlipay`：
  - 校验 `cardId` 非空
  - RPC 调用 `blacklistClient.addBlackList(request)`
  - 空响应/异常统一返回 `FAIL`
  - 返回前记录 RPC 响应和最终响应

#### Task 7：重构解约执行
- Modify: `AlipayPaymentServiceImpl.java`
- `executeTermination`：
  - 校验 `agreementCode` 非空
  - RPC 调用 `alipayPaySignClient.executeTermination(agreementCode)`
  - 空响应时返回 `FAIL`
  - 成功时 `status = COMPLETED`，失败时 `status = FAIL`
  - 记录 RPC 响应和最终响应

#### Task 8：重构支付结果查询
- Modify: `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayQueryServiceImpl.java`
- `payQuery`：
  - 校验 `orderNo` 非空
  - RPC 调用 `alipayPaySignClient.alipayTripPayQuery(request)`
  - 空响应时返回 `FAIL`
  - 逐字段映射 RPC 响应到响应 DTO：`retCode`、`retMsg`、`outTradeNo`、`paymentTime`、`tradeStatus`、`totalAmount`、`tradeNo`、`tradeDesc`
  - 记录 RPC 响应和最终响应

### Phase 4：门面类同步
#### Task 9：更新 AlipayTripServiceImpl 委托方法
- Modify: `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayTripServiceImpl.java`
- `executeTermination` 委托方法返回类型改为 `TerminationExecuteResult`

### Phase 5：编译与验证
#### Task 10：编译 model 模块
```bash
cd /Users/tuanjie/workspace/company/chinasofti/qd/qditp/model
env JAVA_HOME=/Users/tuanjie/.local/share/mise/installs/java/oracle-21.0.11 \
  PATH=/Users/tuanjie/.local/share/mise/installs/java/oracle-21.0.11/bin:/Users/tuanjie/.local/share/mise/installs/maven/3.9.10/apache-maven-3.9.10/bin:/usr/bin:/bin \
  mvn clean install -DskipTests
```

#### Task 11：编译 fep-alipay-server
```bash
cd /Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-alipay-server
env JAVA_HOME=/Users/tuanjie/.local/share/mise/installs/java/oracle-21.0.11 \
  PATH=/Users/tuanjie/.local/share/mise/installs/java/oracle-21.0.11/bin:/Users/tuanjie/.local/share/mise/installs/maven/3.9.10/apache-maven-3.9.10/bin:/usr/bin:/bin \
  mvn clean compile -DskipTests
```

#### Task 12：运行 ad-hoc 验证脚本
- 检查项：
  - `TerminationExecuteResult` DTO 存在且字段完整
  - `FepAlipayTripPaymentController` 4 个接口路径为 `/admin/payment/*` 且有响应日志
  - `AlipayPaymentService.executeTermination` 返回 `TerminationExecuteResult`
  - `AlipayPaymentServiceImpl.requestRefund/addBlackListForAlipay/executeTermination` 校验/RPC 映射/日志增强
  - `AlipayQueryServiceImpl.payQuery` 校验/RPC 字段映射/日志增强
  - `AlipayTripServiceImpl.executeTermination` 委托新返回类型

---

## 边界说明

- `/api/payment/requestPay` 当前实现在 `alipay-pay-sign-server`，不在 `fep-alipay-server`。若需同步重新设计，需单独制定计划。
- 本次计划不包含单元测试和集成测试，仅保证编译通过和静态结构正确。

---

## 预计文件变更

| 文件 | 变更类型 |
|------|----------|
| `model/.../alipaytrip/TerminationExecuteResult.java` | 新增 |
| `fep-alipay-server/.../service/AlipayPaymentService.java` | 修改返回类型 |
| `fep-alipay-server/.../service/impl/AlipayPaymentServiceImpl.java` | 重写 3 个方法 |
| `fep-alipay-server/.../service/impl/AlipayQueryServiceImpl.java` | 重写 `payQuery` |
| `fep-alipay-server/.../service/impl/AlipayTripServiceImpl.java` | 同步委托方法 |
| `fep-alipay-server/.../controller/FepAlipayTripPaymentController.java` | 补齐响应日志 |

---

## 风险与权衡

1. **返回类型变更风险**：`executeTermination` 从 `BlackListOperateResult` 改为 `TerminationExecuteResult`，若前端/调用方按原结构解析 `retCode` 会兼容失败；建议评估下游调用方。
2. **异常消息携带堆栈信息**：`response.setRetMsg("系统内部错误：" + e.getMessage())` 便于排障，但需确认是否暴露敏感信息。
3. **编译环境**：`fep-alipay-server` 当前 `pom.xml` 仍使用 `maven-compiler-plugin 3.11.0`，若后续升级 JDK 可能再次出现 `TypeTag :: UNKNOWN` 问题；建议统一升级到 3.13.0。

---

## 开放问题

1. `/api/payment/requestPay` 是否纳入本次计划？当前位于 `alipay-pay-sign-server`。
2. `executeTermination` 的 `status` 是否需要在响应中返回更详细的状态描述？
3. 是否需要为 5 个接口补充统一的请求 ID / 链路追踪？

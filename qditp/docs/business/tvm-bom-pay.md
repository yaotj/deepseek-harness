---
业务域: TVM / BOM 非现金购票、充值、退款
模块: collect-pay-server
---

# 提示词：TVM / BOM 非现金支付

## 何时读本文件
TVM 单程票下单出票、TVM 充值、BOM 非现金收款、当面付订单与退款、单程票退款、支付中心（bestonepay）对接、APP 取票下单通知重试相关改动。

## 模块定位
`collect-pay-server`，端口 **58101**，`spring.application.name=itpagm`（配置在 `application.yml`，注意本模块用 yml 而非 properties）

⚠️ **安全**：`collect-pay-server/src/main/resources/application.yml` 中明文内置支付中心商户私钥。触碰该文件 **MUST** 提示人工复核安全合规，**NEVER** 把私钥值输出到对话或日志。

## 接口清单

**TVM** `controller/ci/tvm/TvmOrderController.java`（前缀 `/itptvm/ci/tvm`）
- `notiDeviceHeard`、IF2A-01 `requestGenSjtOrder`、IF2A-11 `requestPayment`、IF2A-03 `requestPayResult`
- IF2A-04 `notiTakeTicketResult`、IF2A-05 `notiTakeTicketFailResult`、`requestRefund`
- IF8A-15 `requestActiveTicket`、IF2A-08 `requestTakeTicketAuth`
- IF2A-09 `requestTopup`、IF2A-06 `topupCardResultNoti`、IF2A-07 `topupCardFailNoti`
- `requestPayOrderDetail`、`payNotice`

**BOM** `controller/ci/bom/BomOrderController.java`（前缀 `/itpbom/ci/bom`）
- `notiDeviceHeard`、IF8A-04 `requestGenNoCashOrder`、IF8A-05 `requestPayment`、IF8A-06 `requestGetPayResult`
- IF2A-08 `notiBusResult`、IF2A-09 `notiTopupResult`
- IF5A-01 `requestCardDataAnalyse`、IF5A-03 `requestUpdateCardData`、IF5A-09 `notiUpdateHceData`
- `requestOrderResult`、`requestTicketRefund`

⚠️ BOM 的 IF8A-04/05/06 与 APP 域的 IF8A-04（自助补站）/IF8A-05（交易记录）/IF8A-06（解约）**编号冲突**，且 IF2A-08/09 在 TVM 与 BOM 下含义不同。定位接口 **MUST** 以「模块 + URL」为准，**NEVER** 只凭编号。

**APP 取票侧** `controller/ci/app/TvmAppOrderController.java`（前缀 `/ci/app`）
- IF8A-20 `requestOrder`、IF8A-11 `requestPaymentInfo`、IF8A-18 `requestPayResult`
- `requestPreActiveOrderList`、`requestRefundTicket`、`requestRefundTicketResult`、`receiveRefundResult`

**运营端** `controller/page/FacePayOrderPageController.java`（`/page/face-pay/orders`）：分页、`{orderNo}/refund` 全额退款

**已废弃**：`controller/ci/app/CollectPayController.java` 整类被注释（IF8A-09/10/12/13 未生效）。**NEVER** 在此类中新增代码，如需恢复 **MUST** 先与用户确认。

## 核心 service
`TvmOrderServiceImpl`、`TvmTopupServiceImpl`、`TvmTakeTicketServiceImpl`、`TvmOrderPreServiceImpl`、`BomOrderServiceImpl`、`AppOrderServiceImpl`、`PayCenterServiceImpl`、`TvmCommonServiceImpl`、`CollectPayServiceImpl`

TVM 主链路（见 `service/TvmOrderService.java` 方法注释）：
IF2A-01 下单 → IF2A-11 扫码支付 → IF2A-03 查支付结果 → IF2A-04/05 出票结果/故障通知 → `requestRefund` → `payNotice` → `sendNoticeAppTakeTicketRecord` / `sendNoticeAppTakeTicketFailureRecord`
异步补偿：`doTime.noticeTakeTicketTask`、`doTime.noticeRefundTask` 定时扫描，`app.retryTimes=5`

## 数据表
`collect-pay-server/sql.txt` 只有 **20 张 `CREATE TABLE`**。下列表名在 mapper XML 中被引用但 **sql.txt 里没有 DDL**，改动前 **MUST** 确认库中实际结构：`TBL_TVM_Topup_Notiy`（`TvmTopupOrderMapper.xml`）、`TBL_BOM_TOPUP_RESULT`（`BomTopupResultMapper.xml`）、`TBL_TICKET_REFUND_RECORD`（`BomNoCashOrderMapper.xml`）。
- TVM：`TBL_TVM_ORDER_PAY`、`TBL_TVM_ORDER_PAY_PRE`、`TBL_TVM_ORDER_TOPUP`、`TBL_TVM_Topup_Notiy`、`TBL_TVM_ORDER_REFUND`、`TBL_TVM_TAKE_TICKET_ORDER`、`TBL_TVM_MAIN_TICKET`、`TBL_TVM_SUB_TICKET`
- APP：`TBL_TVM_APP_ORDER`、`TBL_APP_ORDER_REFUND`、`APP_PAY_LOGS`
- BOM：`TBL_BOM_ORDER_PAY`、`TBL_BOM_ORDER_REFUND`、`TBL_BOM_SALE_INFO`、`TBL_BOM_MAIN_TICKET`、`TBL_BOM_SUB_TICKET`、`TBL_BOM_BUS_RESULT`、`TBL_BOM_TOPUP_RESULT`、`TBL_BOM_TICKET_REFUND`
- 通用：`TBL_TICKET_REFUND_RECORD`
- 通知重试表：`tbl_notice_app_taketicket_record`、`tbl_notice_app_refund_record`、`TBL_NOTICE_APP_FAILURE_RECORD`（后者建表脚本 `scripts/create_tbl_notice_app_failure_record.sql`）
- 订单号来源 `OrderSeqMapper`（Oracle `ORDER_NO_SEQ.NEXTVAL`），**MUST** 沿用序列，**NEVER** 自造订单号规则

## 状态定义（无枚举类，散落常量 —— 本域最大技术债）
- `CollectPayServiceImpl`：`PAY_TYPE_PAY=0`、`PAY_TYPE_REFUND=1`、`PAY_RESULT_SUCCESS=100`、`PAY_RESULT_FAIL=1`、`DEFAULT_ORDER_TIMEOUT=60`
- 业务操作结果初始状态 `"0"`（已通知待处理），见 `BomOrderServiceImpl`
- 返回码：`model/response/app/AppOrderResult.java`（0000/9999）、`model/response/bom/BomOrderResult.java`（0000/8999）

改状态值 **MUST** 全局 grep 字面量；新增状态 **SHOULD** 就近抽枚举，但不得改变已落库的取值。

## 幂等
- 主键约束 `ORDER_NO` / `ID`
- 状态终态短路：支付/退款/通知重入均"先查库再决定"（`TvmOrderServiceImpl`、`TvmTopupServiceImpl`、`BomOrderServiceImpl`、`AppOrderServiceImpl` 多处）
- 通知重试靠上述三张防重表
- 无 Redis 锁。新增支付/退款写入 **MUST** 补状态短路判断

## 参考原始文档
- `docs/业务需求文档/非现金购票单程票退款接口清单.md`、`非现金购票单程票退款功能3.docx`
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`（IF2A 域）
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（IF8A-11/15/18/20）

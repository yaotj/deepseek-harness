# 旧 ITP 交易管理 6 页截图包（2026-11-18）

> 对应分析：[旧 ITP 交易管理功能分析](../旧ITP交易管理功能分析-2026-11-18.md)。
> 来源：旧 ITP `http://58.56.166.170:9080/qd-omp`（登录账号 `FuTianTest002`），2026-11-18 15:17~15:22 用内置浏览器逐页实扫。
> **全程只读**：仅进入列表页、展开查询区、打开详情页；未点「成功 / 失败」处置按钮、未提交任何表单、未导出数据。
> 截图为 594×737 视口 PNG，原文件在 [screenshots/](screenshots/)；每张独立一张，文件名按「序号-功能-页面」命名。

## 一、截图清单（15 张）

| # | 文件 | 功能 | 页面 | 旧 URL（`a/` 后段） | 画面要点 |
|---|---|---|---|---|---|
| 01 | `01-bom-nocash-list.png` | BOM 非现金管理 | 列表 | `owner/ownerOrderNoCash/list` | 标题「BOM非现金业务管理」；10 列表头；**共 0 条** |
| 02 | `02-bom-nocash-query.png` | BOM 非现金管理 | 查询条件展开 | 同上 | 商户/交易/渠道订单号、订单状态、交易类型、支付状态、管理员、逻辑卡号、支付通道、设备编号、订单时间 |
| 03 | `03-sjt-order-list.png` | 单程票购票订单 | 列表 | `owner/ownerOrderSjt/list` | 列头含下单渠道/支付渠道/用户标识/票卡种类；**共 0 条** |
| 04 | `04-sjt-order-query.png` | 单程票购票订单 | 查询条件展开 | 同上 | 交易/商户订单号、**用户标识**、支付渠道订单号、下单渠道/支付通道/票卡类型/订单状态、订单时间 |
| 05 | `05-tvm-topup-list.png` | TVM 充值订单管理 | 列表 | `owner/ownerOrderTvmTopup/list` | 工具栏「查询 + 导出数据」；11 列；**共 0 条** |
| 06 | `06-tvm-topup-query.png` | TVM 充值订单管理 | 查询条件展开 | 同上 | 商户/交易/渠道订单号、设备编码、订单状态、逻辑卡号、订单时间、支付渠道、线路名称 |
| 07 | `07-tvm-suspicious-list.png` | TVM 充值存疑订单处理 | 列表 | `owner/ownerOrderTvmTopup/tvmMng?orderStatus=2` | **共 2 条**存疑老单；操作列可见铅笔（进详情）与更多动作入口 |
| 08 | `08-tvm-detail-order-tab.png` | TVM 充值存疑订单处理 | 详情 · 订单信息页签 | `owner/ownerOrderTvmTopup/form?idNo=1946` | 订单号 `08202404131654594872`；总价 50.00、订单状态已付款、**充值状态存疑**、充值金额/余额 0；逻辑卡号+物理卡号+写卡时间+线路站点 |
| 09 | `09-tvm-detail-payment-tab.png` | TVM 充值存疑订单处理 | 详情 · 支付信息页签 | 同上 | 支付状态/用户支付账号/商户订单号/支付订单号/支付渠道名称/支付时间；底部「关闭」；内嵌**TVM充值订单操作历史管理 3 条**（创建/支付/写卡） |
| 10 | `10-oneside-list.png` | 单边查询 | 列表 | `metro/metroMemberDebitRecord/orderExpTypeList` | 页面标题实为「扣款请求记录管理」；**共 3791 条 / 190 页**；工具栏「数据导出」；异常类型列首行「未知」 |
| 11 | `11-oneside-query.png` | 单边查询 | 查询条件展开 | 同上 | 逻辑卡号、票卡交易序列号（`tikcetTransSeq` 原样拼错）、交易/渠道/商户订单号、订单异常类型、订单日期 |
| 12 | `12-oneside-exp-type-options.png` | 单边查询 | 异常类型下拉展开 | 同上 | 字典实读：空 / 单边账(入站) / 单边账(出站) / 单边入站(人工处理单) / 单边出站(人工处理单) |
| 13 | `13-oneside-detail.png` | 单边查询 | 详情页 | `metro/metroMemberDebitRecord/form?idNo=188574` | 渠道编码/逻辑卡号/票卡交易序列号/订单号/商户订单号/支付订单号/订单日期/订单金额/实际扣款金额/异常类型/进出站线路站点与设备/进出站时间 |
| 14 | `14-supplement-order-list.png` | 在线补款订单管理 | 列表 | `owner/ownerOrderExcessFare/list` | 标题显示未翻译 key `owner_order_excess_fare查询`；列头含金额/支付渠道/补款订单号/操作；**共 0 条** |
| 15 | `15-supplement-order-query.png` | 在线补款订单管理 | 查询条件展开 | 同上 | 交易订单号、支付渠道订单号、商户订单号、补款订单号 |
| 16 | `16-oneside-detail-refund-action.png` | 单边查询 | 详情页底部 | `metro/metroMemberDebitRecord/form?idNo=188574` | **「申请退款」写操作按钮**（此前漏记）；进出站设备/时间、请求扣款结果、实际扣款时间、入库日期 |
| 17 | `17-bom-detail-form.png` | BOM 非现金管理 | 详情/新增表单 | `owner/ownerOrderNoCash/form` | 订单信息页签 16 字段；订单状态默认「已下单」、行政交易类型下拉截断；底部关闭/刷新 |
| 18 | `18-sjt-detail-form-top.png` | 单程票购票订单 | 详情/新增表单上半 | `owner/ownerOrderSjt/form` | 下单渠道默认「地铁APP」、票卡类型默认「有起始站，终点站单程票」；订单/票价/取票字段 |
| 19 | `19-sjt-detail-form-bottom.png` | 单程票购票订单 | 详情/新增表单下半 | 同上 | 「订单操作历史」+「TVM出票记录」子表；**共 425,048 条 / 21,253 页**；含 `undefined` 事件 |
| 20 | `20-supplement-form-top.png` | 在线补款订单管理 | 详情/新增表单上半 | `owner/ownerOrderExcessFare/form` | 框架裸表单，全英文字段名；`id` 必填 |
| 21 | `21-supplement-form-bottom.png` | 在线补款订单管理 | 详情/新增表单下半 | 同上 | `refund_amt_date`/`cancle_order_date`/`remark`/`reserve`/`panchan_trade_no`/`reg_date`/`order_array`；底部「关闭」 |

> **字段级全文记录**：[旧ITP交易管理-详情字段全集](旧ITP交易管理-详情字段全集.md) —— 6 页的查询条件、列表列、详情页全部字段/取值/下拉字典/按钮/子表逐项抄录。

## 二、覆盖口径与边界

- **6 个功能全覆盖**：每个功能至少 1 张列表 + 1 张查询条件，共 12 张。
- **详情页**：只有 TVM 充值存疑（2 条）与单边查询（3791 条）有数据可下钻，分别截了 2 张（订单/支付两个页签）与 1 张，共 3 张。
- **0 条页面**（BOM 非现金、单程票购票、TVM 充值订单、在线补款）无可点开的详情行，故无详情页截图；不能据「没有截图」推断旧系统没有详情入口——需等旧侧有数据时再补。
- 未截「成功 / 失败」处置动作执行后的画面：属写操作，本轮授权范围只含只读实扫。
- 截图含真实卡号、订单号、支付账号（部分已打码由旧系统自身渲染），仅限测试/对标使用，**NEVER 外传**。

## 三、复核方法

重新登录旧 ITP 后直接访问上表 URL；展开查询点右上角「查询/隐藏」切换；详情页用 `form?idNo=<上表 idNo>`。旧系统仅外网可达，与新综管台网络互斥。

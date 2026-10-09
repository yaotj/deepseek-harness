# 支付测试 · INDEX

原始资料：同事维护的测试案例总表 `docs/testing/ITP测试案例 - 可验证20260904-问题.xlsx` 的 **「ITP-支付」sheet**（18 条带编号 + 3 条无编号行，共 21 个三级用例）。导出的管道分隔快照在 `/tmp/itpcases/ITP-支付.txt`（单元格内换行已替换为 ` / `）。

**xlsx 只读、NEVER 修改**；本目录只写我方结论。处理方式同 [用户卡管理](../user-card/INDEX.md) 与 [当面付](../face-pay/INDEX.md)：保留原表编号，预期结果改写成**当前代码可验证的断言**，冲突处标 ⚠️ 并收敛到 `05`。

最后更新：2026-09-22 ｜ 依据代码：`main` 工作树（SVN 工作副本，**行号绑定本次会话的工作树，代码变更后需重新核对**）

## 页面

- [00-链路事实与配置字典](00-链路事实与配置字典.md) — 签约 / 解约 / 扣费 / 退款的真实 URL、`pay.sign.*-url` 七条完整地址、核心类、`APP_PAY_SIGN_INFO` / `APP_TERMINATION_REQUEST` / `GATE_TXN_PAY` / `PAY_TXN_DETAIL` / `PAY_REFUND_DETAIL` / `F2F_*` 表与状态取值
- [01-签约与解约](01-签约与解约.md) — Payment_001~004（支付宝签约 / 微信签约 / 解约 / 默认支付方式）
- [02-扣费与失败重试](02-扣费与失败重试.md) — Payment_005（实时扣费）/ Payment_005a（离线码扣费，**原表无编号、本库自拟**）/ Payment_006（扣费失败处理）
- [03-退款](03-退款.md) — Payment_007（异步退款至付款账号）/ Payment_008（单程票全额原路退）/ Payment_009（单程票部分原路退）
- [04-当面付补充](04-当面付补充.md) — Payment_010~018 + 两条无编号行里 **face-pay 页未覆盖的部分**；已覆盖的只留指向
- [05-阻塞项与缺陷候选](05-阻塞项与缺陷候选.md) — 原表 6 条「问题」备注的代码侧定位 + 本轮新发现
- [06-执行记录-2026-09-22](06-执行记录-2026-09-22.md) — **一次性实跑记录（不是可复跑用例）**：批次二在测试环境逐笔发过的请求 / 原样响应 / 库前后 / 日志原文 / 还原 SQL / 真实资金动作清单与残留
- [07-执行记录-2026-09-22-批次三](07-执行记录-2026-09-22-批次三.md) — 批次三：解约端到端实跑（`00522966`/`07`，PASS + `queryResult` 真的 `UNSIGNED`）/ 对账现状核对（`RECON20260920` 已 `SUCCESS`、4 源 `COMPLETED`）/ B2 与 B15 处置建议

## 三条无编号行的处置

原表这 3 行「序号」列为空，本库补临时编号（**编号为本库自拟，不是原表编号，回填结果时 MUST 同时写出三级用例名**）：

| 原表位置 | 三级用例 | 本库自拟编号 | 落在哪一页 |
|---|---|---|---|
| 005 之后 | 支付-离线码扣费 | `ITP_Payment_005a` | [02](02-扣费与失败重试.md) |
| 012 之后 | BOM发售单程票 | `ITP_Payment_012a` | [04](04-当面付补充.md) |
| 015 之后 | STT发售单程票 | `ITP_Payment_015a` | [04](04-当面付补充.md) |

## 去重指向：哪些条目已由别的页覆盖

`docs/testing/face-pay/` 已按**另一份案例清单**（9 例，其 INDEX 自述为 `ITP_Payment_012~020`）写过 TVM / BOM / STT 的购票充值取票补票。

> ⚠️ **编号错位（本轮新发现，见 `05` A1）**：face-pay 页的编号与本期 sheet **整体差 2**。face-pay INDEX 的 `012` 是「TVM 扫码购票」，而本期 sheet 的 `012` 是「TVM 扫码取票」。**跨页引用 MUST 按「三级用例名」对齐，NEVER 按编号对齐。**

| 本期编号 | 三级用例 | 状态 | 指向 |
|---|---|---|---|
| Payment_010 | TVM扫码购票 | 已覆盖 | [face-pay/01-TVM.md](../face-pay/01-TVM.md)（该页编号 012）；本页只补「部分发售无法退款」阻塞项 |
| Payment_011 | TVM扫码充值 | 已覆盖 | [face-pay/01-TVM.md](../face-pay/01-TVM.md)（该页编号 013）；本页只补「前台查不到订单」阻塞项 |
| Payment_012 | TVM扫码取票 | 已覆盖 | [face-pay/01-TVM.md](../face-pay/01-TVM.md)（该页编号 014）；本页只补「取票订单查不到」阻塞项 |
| Payment_012a | BOM发售单程票 | **未覆盖** | 本页 [04](04-当面付补充.md) 新建（face-pay 页无「BOM 发售单程票」小节） |
| Payment_013 | BOM非现金收款 | 已覆盖 | [face-pay/02-BOM.md](../face-pay/02-BOM.md)（该页编号 015） |
| Payment_014 | BOM扫码充值 | 已覆盖 | [face-pay/02-BOM.md](../face-pay/02-BOM.md)（该页编号 016）；本页只补「支付平台无响应」阻塞项 |
| Payment_015 | BOM补票-超程扣费 | 已覆盖 | [face-pay/02-BOM.md](../face-pay/02-BOM.md)（该页编号 017）；本页只补 `TBL_BOM_ORDER_PAY` 新旧表现状 |
| Payment_015a | STT发售单程票 | **未覆盖** | 本页 [04](04-当面付补充.md) 新建 |
| Payment_016 | STT非现金收款 | 已覆盖 | [face-pay/03-STT.md](../face-pay/03-STT.md)（该页编号 018） |
| Payment_017 | STT扫码充值 | 已覆盖 | [face-pay/03-STT.md](../face-pay/03-STT.md)（该页编号 019） |
| Payment_018 | STT补票-超程 | 已覆盖 | [face-pay/03-STT.md](../face-pay/03-STT.md)（该页编号 020） |
| Payment_001~004 | 签约 / 解约 / 默认支付方式 | 部分有实测记录 | 实测链路见 [pay-sign/e2e-2026-09-17.md](../pay-sign/e2e-2026-09-17.md) 与 [alipay-channel/](../alipay-channel/)；**本目录 `01` 才是用例形态**（那两处是一次性实测记录，不是可复跑用例） |
| Payment_007~009 | 退款 | 部分有实测记录 | [alipay-channel/04-requestRefund-e2e-2026-09-18.md](../alipay-channel/04-requestRefund-e2e-2026-09-18.md) 只覆盖**支付宝渠道**一次退款；本目录 `03` 覆盖 APP 侧 `requestRefund` 与单程票退款 |

**NEVER 重写 face-pay / pay-sign / alipay-channel 的页面**，本目录只补它们没有的部分。

## 用例可执行性一览

可 curl（内部端点或 APP 报文可自行构造）：

- Payment_003 支付渠道解约：可 curl `POST /ci/app/requestTermination`
- Payment_004 设置默认支付方式：可 curl `POST /ci/app/requestSetDefaultPayChannel`（fep-app 别名）／**直连 account-server 是根级 `POST /requestSetDefaultPayChannel`，NEVER 带 `/ci/app` 前缀**（2026-09-22 实测，见 `06` §二.1）
- Payment_005 实时扣费：可 curl `POST /ci/gateTxnPay/requestPay`（**跳过闸机、直接对 gate-txn-pay 建单扣费**）
- Payment_006 扣费失败处理：可 curl 三个内部端点（`/internal/gate-txn-pay/debit/retry/{default,alipay,recent}`）
- Payment_007 异步退款：可 curl `POST /ci/app/requestRefund` + `POST /internal/payment/compensateRefundQuery`
- Payment_008 单程票全额退：可 curl `POST /ci/app/requestRefundTicket`（face-pay）
- Payment_010~018 的**通知 / 上报段**：可 curl（`notiTakeTicketResult` / `notiTakeTicketFailResult` / `notiTopupResult` / `notiBusResult` 等）

已在测试环境实跑（2026-09-22 批次二，逐笔证据见 [[06-执行记录-2026-09-22]]）：

- Payment_004 设置默认支付方式：**PASS**（5 个分支，含 1 次真实改库 + 已还原）
- Payment_006 扣费失败处理：**PASS（补偿端点部分）**，B1 / B14 已证明
- Payment_007 异步退款：**阻塞** —— 发了 1 笔真实退款（3 分，被支付中心拒、无资金划转），我方链路按设计跑完但卡在支付中心口径矛盾（新增 B15 / B16）
- Payment_001 / 002 / 003 / 008 / 009 与对账批次：**未执行**，原因逐条写在 `06` §五

需人工（渠道侧扫码 / 真实闸机 / 真实设备）：

- Payment_001 / 002 签约：`requestContractAdvisory` 返回的签约页需在**渠道 App 内完成身份验证**，我方只能收 `receiveSignResult` 回调
- Payment_005 的「用户出站」段：真实闸机上报走 `fep-dev-server`，测试时用上面的 `requestPay` 直连替代
- Payment_005a 离线码进出站：需真实闸机 + 离线码
- Payment_009~018 的「扫码付款」段：需渠道侧真实扫码

无实现（本轮 grep 零命中，判据写在对应用例里）：

- Payment_006 的「计入扣费失败交易（表）」：**没有独立的扣费失败交易表**，失败只落 `GATE_TXN_PAY` 的列（见 `05` B1）
- Payment_007 的「退款结果通知」：pay-sign-server **无任何出向退款通知代码**（见 `05` B2）。**2026-09-22 实测加强**：`buildRequestRefundBizData`（`PaySignGatewayMessages.java:88-96`）不放 `notifyUrl`、`RequestRefundReqDTO` 连该字段都没有 ⇒ 「退款必须送 `notifyUrl`」在当前代码里**不可实现**，整轮实跑零退款回调
- Payment_010 的「部分发售后运营端退款」：运营端只有全额退款入口，且 `refundStatus='PARTIAL'` 后退款按钮被隐藏（见 `05` B3）
- Payment_011 / 012 的「前台查询界面」：`/page/app/orders` 在综管台前端**零引用**（见 `05` B4）
- Payment_015a~018 的 STT 设备入口：face-pay-server **无 STT 前缀路由**（见 `05` B6）

## 编写约束

- 只记录代码中确实存在的事实，一律带 `文件:行号`；**找不到就写「未找到」，NEVER 推断 URL / 表名 / 列名 / 状态取值 / 异常码**。
- 预期结果 MUST 能在「接口响应 / 库表 / 日志」三者之一里核对。
- 依赖真实渠道扫码的步骤标「需人工（渠道侧扫码）」，并给出**模拟回调**替代路径；模拟回调一律标注「**模拟回调 = 非真实渠道链路**，只验我方收单与状态机，不验渠道资金」。
- NodePort 一律写「待现查 `kubectl get svc -n itp`」，除 AGENTS.md §8 已实测记过的那几个。
- 矛盾显式标 ⚠️，不擅自裁决，收敛到 `05`。
- **不生成实现率 / 进度百分比**（`AGENTS.md` §5.3）。

## 更新记录

- 2026-09-22 首次建立。来源：xlsx「ITP-支付」sheet + 仓库代码实测（pay-sign-server / gate-txn-pay-server / face-pay-server / collect-pay-server / collect-ticket-server / account-server / fep-app-server / web）。
  主要发现：①face-pay 页与本期 sheet 编号整体差 2（A1）；②不存在「扣费失败交易表」（B1）；③pay-sign 无出向退款通知（B2）；④运营端当面付退款在 `PARTIAL` 后按钮消失且只有全额退（B3）；⑤`/page/app/orders` 前端零引用（B4）；⑥`updateDefaultPayChannelById` 与 `updateChannelDefaultContractById` 两条 SQL 逐字相同（B5）；⑦STT 无路由前缀（B6）；⑧`TBL_BOM_ORDER_PAY` 仍被 collect-pay 的 BOM mapper 与对账源读写、与 `F2F_ORDER` 并存（B7）。
- 2026-09-22 批次二：在测试环境实跑三组（Payment_004 / Payment_006 / Payment_007），新增页面 **`06-执行记录-2026-09-22.md`**（逐笔请求 / 原样响应 / 库前后 / 日志原文 / 还原 SQL / 真实资金动作清单）。同批改动：
  - **链路事实修正**：account-server 的 `requestSetDefaultPayChannel` 等八个端点是**根级**路径，`/ci/app/` 只是 fep-app 别名 —— `00` §3.8 与 `01` Payment_004 的旧写法**已作废、NEVER 回退**。
  - 回填 `01`~`03` 各用例「结果」行（`04` 本无结果行、未动）；`02` Payment_006 与 `03` Payment_007 写入实测结论。
  - `05` 新增「2026-09-22 批次二实跑：逐项被证明 / 被推翻 / 仍未验」一节：**B1 / B14 / B2 已证明**；**新增 B15（退款单永久空转、无最大回查次数）、B16（重复退款无我方幂等）、B17（历史 `RETRY` 行谁都不扫）、B18（签约与开户两表无约束）**；**5 条被推翻的判断**（converge 缺 `txnDate` / 补偿 `scanned=0` / 重试计数只加一次 / 「写入方产 `RETRY`」/ Payment_004 切换失败）已逐条标注。
- 2026-09-22 批次三（本轮，接批次二）：补跑 Payment_003 解约（`00522966`/`07`，PASS）+ 对账现状核对（`RECON20260920` SUCCESS，4 源 10 条 COMPLETED）。新增页面 **`07-执行记录-2026-09-22-批次三.md`**。同批改动：
  - 回填 `01` Payment_003 结果行。
  - `07` 新增 B2 / B15 处置建议（改哪里、为什么必须修，不动代码）。
  - 解约验证了三件事：①`APP_TERMINATION_REQUEST` `PENDING->SUCCESS` 全链 11s；②`queryResult` 真的 `UNSIGNED`（支付中心网关确认）；③`CHANNEL_SYNC_*` outbox 推进到 `SUCCESS`、三表同步清理。
  - 已知残留：`APP_TERMINATION_REQUEST` ID=62（`EVTTEST0001`，合成用户被支付中心拒 `code:9999`，回退 `PENDING`，每轮 process 跳过并计 skipped）。
  - `00522966` 的解约刻意不还原 -- 支付中心侧已真实 UNSIGNED，单方面恢复本地签约行会造成不一致。

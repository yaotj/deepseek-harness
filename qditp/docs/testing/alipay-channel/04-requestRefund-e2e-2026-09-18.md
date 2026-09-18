# requestRefund 迁移第 9 条端到端联调（2026-09-18）

镜像 `itp/alipay-pay-sign:1.1.34`（滚更前线上 **1.1.32**）+ `itp/fep-alipay:1.0.63`（同 tag 覆盖后 `rollout restart`）。
链路：`POST http://172.20.211.23:30020/admin/payment/requestRefund`（fep-alipay，form-urlencoded 的 `bizData`）
→ rpc `AlipayPaySignClient.alipayTripRequestRefund`
→ `POST http://172.20.211.23:30022/internal/alipay/payment/requestRefund`（pay-sign）
→ `service.impl.refund.AlipayPayRefundServiceImpl`（日志类名已实测确认，**新实现在跑**）。

## 一、部署前发现：退款链路当时是断的（比本次迁移更要紧的事实）

滚更前现查：**pay-sign 线上 1.1.32、而 fep-alipay 线上 1.0.63 已经在打新 URL**。
探活证据（滚更前，直连 30022）：

- `POST /internal/alipay/payment/payQuery` → `9999 支付明细不存在`（端点在）
- `GET /internal/alipay/termination/execute?agreementCode=...` → `8011 用户未签约`（端点在）
- `POST /internal/alipay/payment/requestRefund` → **`9001 系统内部错误`（端点不存在）**
- `POST /api/payment/payQuery` / `POST /api/payment/requestRefund` → `9001`（旧路径已按 hard_switch 删除）

即：迁移第 7 条（payQuery / executeTermination）已在 1.1.32 上线，**第 8 条（requestRefund 改 URL）从未上线**，而上游 fep-alipay 那侧已经切了 —— 于是**运维退款入口在那段时间内一打就是 `9001`**，两侧都不报 404、日志里只有一行 `9001`。这是 AGENTS.md §7「pom `<version>` 不等于线上版本」的又一次实证：**判断某条迁移上没上线 MUST 探活 + 查 Deployment tag，NEVER 看仓库 pom**。

连带一条本模块专属判据：**`9001 系统内部错误` 是本模块「路径无 handler」的伪装形态**（不是 UUID retCode）。但它**不唯一**——见 §三 E1，参数类异常也落这个码，因此 **看到 `9001` MUST 去服务端日志取原文**。

## 二、滚更

- `kubectl set image deploy/alipay-pay-sign-server alipay-pay-sign-server=...itp/alipay-pay-sign:1.1.34 -n itp`（回滚位：`1.1.32`）
- `fep-alipay` 的 `imagePullPolicy=Always`，1.0.63 是同 tag 覆盖，**`kubectl rollout restart deploy/fep-alipay -n itp` 即可拉到新镜像**，不必升版本号；`alipay-pay-sign-server` 是 `IfNotPresent`，靠换新 tag 生效。
- 探活：30022 / 30020 `/actuator/health` 均 `http=200`。
- 注意 ns 内有**两个** fep-alipay 相关 Deployment：`fep-alipay`（1.0.63，svc `fep-alipay-rec8g-svc:30020`，**`fep-app-vr` 的 `/fep-alipay/` 就路由到它**）与 `fep-alipay-server`（1.0.58，svc `:30023`，**不承接入向、且连 `service.alipay-pay-sign.url` 都没配**）。**改支付宝渠道接入层只需动 `fep-alipay`，NEVER 误动 `fep-alipay-server`。**

## 三、用例与结论（全部走 fep-alipay 对外面）

基线：`ALIPAY_REFUND_LOG` 33 行、`PROCESSING` 0 行。

- **E1 已全额退完的订单**（`GT20260814121142985542741`，`PAY_AMOUNT=200` / `REFUND_AMOUNT=200`）
  → 返 `9001 系统内部错误`；服务端日志原文是
  `java.lang.IllegalArgumentException: 退款金额必须大于0`（`RefundAmountCalculator:40` ← `AlipayPayRefundServiceImpl:96`）。
  **未落库**（33 未变）✓。
  **既有缺陷（非本次引入、旧实现同源）**：金额校验抛的是 `IllegalArgumentException`、不是 `BusinessException`，被全局处理器兜成 `9001 系统内部错误` —— **参数类错误伪装成系统错误，运维在响应里看不到真实原因**。改它会动响应契约，本批未改。
- **E2 造数一条 `PAY_STATUS='SUCCESS'` 未退订单**（`E2E20260918REFUND001`，1 分）
  → 返 `9001 退款结果未知，请稍后核对`。支付中心实答 `{"code":600,"msg":"原订单不存在或未支付成功或卡号错误"}`（造数只在我方库、对端没有这单），落 **`Rejected` 分支**。
  落库实测：明细 1 行 `REFUND_STATUS=PROCESSING` / `RESULT_CODE=INIT` / `RESULT_MSG=退款结果未知，待人工核对` / `RESPONSE_BODY` 存原文；`REFUND_ORDER_NO=R17897135780096255e20f`（`R+毫秒+8位UUID`）；`CHANNEL_AGREEMENT_NO=2088302232551032`（取自 `ALIPAY_SIGN_INFO`）；`CARD_ISSUE_CODE=0007`；`REFUND_AMOUNT=1`（空入参 → 按可退余额全额）。
  **`ALIPAY_PAY_LOG` 汇总未刷**（仍 `REFUND_STATUS=NONE` / `REFUND_AMOUNT=null`）✓ —— 非 `Accepted` 分支 NEVER 刷汇总。
  SQL 审计里的顺序与设计一致：查 `ALIPAY_PAY_LOG` → `countProcessing` → `INSERT PROCESSING`（先提交）→ 出网 → `UPDATE` 回写（`CREATE_TIME 14:39:38` → `UPDATE_TIME 14:39:39`）。
- **E3 同一订单立刻再打一次**
  → 返 `9999 该订单存在处理中的退款，请先确认上一笔结果`；日志 `processingCount=1`；**未新增明细、未出网** ✓（幂等短路在落库与出网之前）。
- **E4 回归两条已迁 URL**：`payQuery` → `9999 支付明细不存在`；`executeTermination?agreementCode=PROBE_NOT_EXIST_20260918` → `8011 用户未签约`。两条均通，hard_switch 没打断。
  **跑 `executeTermination` MUST 带一个不存在的 `agreementCode`** —— 不带参数会执行**全部**待处理解约，属真实副作用。

## 四、造数与清理

造数：`ALIPAY_PAY_LOG` 插 1 行（`PAY_SEQ=E2E20260918REFUNDSEQ001` / `ORDER_NO=E2E20260918REFUND001` / `CARD_ID=2607031119542741` / `THIRD_USER_ID=0700001448` / `PAY_AMOUNT=1` / `PAY_STATUS=SUCCESS`）。
清理（已执行，各 1 行）：

```sql
DELETE FROM ALIPAY_REFUND_LOG WHERE ORDER_NO = 'E2E20260918REFUND001';
DELETE FROM ALIPAY_PAY_LOG    WHERE ORDER_NO = 'E2E20260918REFUND001';
```

复跑本记录需要的现成素材：`CARD_ID=2607031119542741` / `THIRD_USER_ID=0700001448` 在 `ALIPAY_SIGN_INFO` 有 `SIGN_STATUS='SIGNED'` 的生效签约（`CHANNEL_AGREEMENT_CODE=2088302232551032`）。**存量 `PAY_STATUS='SUCCESS'` 的订单全部已全额退完**，所以想验「成功退款」这一支**必须造数**，直接拿存量订单只会走 E1 那条金额校验。

## 五、未闭合

- **没有真正验到 `Accepted` + `SUCCESS` 那一支**（明细置 `SUCCESS` → 紧接着刷 `ALIPAY_PAY_LOG` 汇总）：需要一笔支付中心侧真实存在且可退的订单，测试环境暂无。该分支目前只有单测覆盖（`AlipayPayRefundServiceImplTest` 的 `refundSuccessWritesDetailBeforeRefreshingSummary`，用 `InOrder` 钉住「先明细后汇总」）。
- E1 那条 `IllegalArgumentException → 9001` 的伪装，见 §三。
- 本端点仍**无鉴权、无归属校验**（既改状态又出网发起真实退款），与 AGENTS.md §5.2 冲突，属既有缺口。

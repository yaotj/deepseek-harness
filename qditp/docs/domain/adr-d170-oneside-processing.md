# ADR-D170：单边订单定时处理机制

**决策**：新增 `OneSideOrderProcessor` + `sys_job` 405，每日 3:00 扫表处理 `ORDER_EXP_TYPE` 为 `1`（单边入）或 `2`（单边出）的未终态订单。

**备选**：①不做自动处理，仅运营手动查询后人工处理 ②实时处理（出站时立即判断）

**理由**：
1. 单边订单是地铁 AFC 系统的常见问题：乘客进站后未正常出站（丢卡/码遮挡）、或出站时无进站记录
2. 当前系统仅有查询页（`/page/gate-txn-pay/one-side`），无自动处理机制
3. 实时处理不可行：单边订单需要等待乘客完成完整行程后才能确定最终费用
4. 人工处理效率低：运营人员需逐笔核对，容易遗漏

**采纳理由**：
- 定时处理可在低峰期（每日 3:00）执行，不影响在线交易
- 当前实现仅标记日志，后续可扩展为创建补款单/退款
- 与现有补偿任务（220/255/345/400）并行，不冲突

**换成①的代价**：运营人员需手动处理所有单边订单，工作量大且容易遗漏
**换成②的代价**：需要改造出站交易链路，影响面广，且无法处理已落库的历史单边订单

## 实现细节

### 业务规则
- **单边入（ORDER_EXP_TYPE='1'）**：进站有记录、出站缺失。需等乘客出站后补扣，或自动创建补款单
- **单边出（ORDER_EXP_TYPE='2'）**：出站有记录、进站缺失。视为无效行程，需退款或标记异常

### 当前实现
- 扫表条件：`ORDER_EXP_TYPE IN ('1','2')`、`DEBIT_STATUS IN ('INIT','RETRY','FAIL')`
- 排除条件：`DISCOUNT_CALC_STATUS != 'OFFLINE_FARE_PENDING'`（防离线码错额）
- 处理动作：仅记录日志，标记订单备注
- 后续扩展：创建补款单（调用 `face-pay-server` 的补款接口）或发起退款

### 配置文件
- `gate.oneside.batch-size`：每轮处理笔数，默认 100
- `gate.oneside.lookback-days`：回溯天数，默认 30

### sys_job 注册
- job_id: 405
- cron: `0 0 3 * * ?`（每日 3:00）
- invoke_target: `gateTxnPayQuartzTask.processOneSideOrders()`
- 触发端点: `POST /internal/gate-txn-pay/oneside/process`

## 状态

- **状态**：已实施
- **日期**：2026-11-04
- **相关文档**：
  - `docs/business/gate-txn-pay.md`
  - `docs/domain/decisions.md` ADR-D162
  - `docs/architecture/web-server.md` §7.0

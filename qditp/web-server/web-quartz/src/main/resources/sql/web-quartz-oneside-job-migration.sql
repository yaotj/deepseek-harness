-- 单边订单处理 sys_job 注册（动态取号，cron 每日 3:00 执行）
--
-- ⚠️ 取号 MUST 动态 MAX(job_id)+5 + NOT EXISTS，NEVER 写死号段：
--   测试库 405 已被「异步导出任务调度」占用（IT-056）。旧版脚本的
--   `DELETE FROM sys_job WHERE job_id = 405` 会把导出调度顶掉（IT-066，2026-11-09 修）。
--
-- 业务规则：
--   ORDER_EXP_TYPE='1'（单边入）：进站有记录、出站缺失 → 需补扣出站费用
--   ORDER_EXP_TYPE='2'（单边出）：出站有记录、进站缺失 → 视为无效行程，需人工确认后退
--
-- 当前实现：仅统计并记录日志，实际补款单创建/退款动作待业务确认后扩展。
--
-- 触发端点：POST /internal/gate-txn-pay/oneside/process
-- Quartz 调用目标：gateTxnPayQuartzTask.processOneSideOrders()
--
-- 执行说明：
--   1) 本脚本幂等：已存在同一 invoke_target 时跳过，不覆盖既有配置。
--   2) Quartz 是内存 JobStore：INSERT 后 MUST 重启 web-admin（或后台对该任务保存一次）才生效。
--   3) 执行前先现查：SELECT job_id, job_name, invoke_target FROM sys_job ORDER BY job_id;
--   4) 回滚：DELETE FROM sys_job WHERE invoke_target = 'gateTxnPayQuartzTask.processOneSideOrders()';
--      删除后同样须重启 web-admin 或后台保存一次才从调度器移除。

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT MAX(job_id) + 5 FROM sys_job), '单边订单处理', 'DEFAULT',
       'gateTxnPayQuartzTask.processOneSideOrders()', '0 0 3 * * ?', '3', '1', '0', 'admin', SYSDATE,
       '处理 ORDER_EXP_TYPE=1(单边入)/2(单边出) 的未终态订单。当日跑任务，仅标记日志，后续扩展为创建补款单/退款。与 sys_job 220/255/345 并行不替代。'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'gateTxnPayQuartzTask.processOneSideOrders()'
);

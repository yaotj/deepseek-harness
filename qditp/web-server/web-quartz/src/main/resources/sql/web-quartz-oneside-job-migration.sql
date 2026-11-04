-- 单边订单处理 sys_job 注册（job_id 405，cron 每日 3:00 执行）
--
-- 业务规则：
--   ORDER_EXP_TYPE='1'（单边入）：进站有记录、出站缺失 → 需补扣出站费用
--   ORDER_EXP_TYPE='2'（单边出）：出站有记录、进站缺失 → 视为无效行程，需人工确认后退
--
-- 当前实现：仅统计并记录日志，实际补款单创建/退款动作待业务确认后扩展。
--
-- 触发端点：POST /internal/gate-txn-pay/oneside/process
-- Quartz 调用目标：gateTxnPayQuartzTask.processOneSideOrders()

DELETE FROM sys_job WHERE job_id = 405;

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (405, '单边订单处理', 'DEFAULT', 'gateTxnPayQuartzTask.processOneSideOrders()', '0 0 3 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '处理 ORDER_EXP_TYPE=1(单边入)/2(单边出) 的未终态订单。当日跑任务，仅标记日志，后续扩展为创建补款单/退款。与 sys_job 220/255/345 并行不替代。');

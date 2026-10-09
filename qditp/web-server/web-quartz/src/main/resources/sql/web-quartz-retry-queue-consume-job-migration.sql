
DELETE FROM sys_job WHERE job_id = 400;

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (400, '用户主动重试扣费队列消费', 'DEFAULT', 'gateTxnPayQuartzTask.consumeRetryQueue()', '0 0/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        'ADR-D169 方案 A 队列解耦。POST /internal/gate-txn-pay/retry-queue/consume：扫描 GATE_RETRY_QUEUE 表，每轮最多消费 batchSize=10 笔，调 PaySignInitiator.retryAndConverge 发起扣款。失败最多重试 maxRetries=3 次，超时 24 小时自动恢复。与 sys_job 220/255/345 并行不替代，后者处理系统级批量补偿，本条只处理用户通过 APP 主动触发的重试请求。concurrent=1，web-admin MUST 单副本。详见 ADR-D169');

COMMIT;

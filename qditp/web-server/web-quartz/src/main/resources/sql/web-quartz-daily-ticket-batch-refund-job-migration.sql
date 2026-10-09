
DELETE FROM sys_job WHERE job_id IN (135, 136, 245, 250);

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (245, '多日票批量退款(当日)', 'DEFAULT', 'dailyTicketBatchRefundQuartzTask.refundDaily()', '0 0 20 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '甲方需求16，每天20点触发 POST /internal/daily-ticket/batch-refund/daily。候选：ORDER_STATUS与PAY_STATUS均PAID + 无票实例(未激活) + 过等待期3天(daily.batchRefund.waitDays)；回溯7天(daily.batchRefund.daily.lookbackDays)，单批200(daily.batchRefund.limit)。旅游票按主单整单退；独立日票候选带PARENT_ORDER_NO IS NULL，NEVER去掉。retCode=9998是限流不是失败。本条2026-09-21按用户要求由job_id 135改号为245，触发目标与cron未变，NEVER回退成135。详见ADR-D151');

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (250, '多日票批量退款(月度)', 'DEFAULT', 'dailyTicketBatchRefundQuartzTask.refundMonthly()', '0 0 20 L * ?', '3', '1', '0', 'admin', SYSDATE,
        '甲方需求17，每月最后一天20点(cron用L，NEVER改回30日否则2月整月不跑)触发 POST /internal/daily-ticket/batch-refund/monthly。口径同当日那条(job 245，原135)，只把回溯换成60天(daily.batchRefund.monthly.lookbackDays)兜漏网单；与245谓词重叠靠UK_DAILY_TICKET_REFUND_ORDER幂等。旅游票按主单整单退；NEVER去掉PARENT_ORDER_NO IS NULL。本条2026-09-21按用户要求由job_id 136改号为250，触发目标与cron未变，NEVER回退成136。详见ADR-D151');

COMMIT;

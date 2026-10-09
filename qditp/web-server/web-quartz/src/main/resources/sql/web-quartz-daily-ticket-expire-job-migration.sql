DELETE FROM sys_job WHERE job_id = 350;

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (350, '日票过期状态收敛', 'DEFAULT', 'dailyTicketQuartzTask.convergeExpiredTickets()', '0 5 * * * ?', '3', '1', '1', 'admin', SYSDATE,
        '每小时第5分调 daily-ticket-server POST /internal/daily-ticket/expire/converge，把 DAILY_TICKET_INSTANCE 中有效期已过、状态还停在 ACTIVATED 或 USED 的票逐条 CAS 推进成 EXPIRED。判据两支：COUNTING_END 非空按它比，为空回退 ACTIVATE_TIME 加 PERIOD 天（激活 IF8A-32 不写 COUNTING_END，从未乘车的票该列恒空）。REFUND_LOCKED / REFUNDED 由 CAS 的 expectStatus 排除。返 9998 属限流不是失败。编号按接在当前最大号 345 之后定为 350。初始 status=1 暂停：收敛成 EXPIRED 后该票不能再退款，属行为变更，须经业主确认后置 0。');

COMMIT;

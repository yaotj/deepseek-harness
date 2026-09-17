-- ============================================================
-- 用途: 新增两条 gate-txn-pay 补偿定时任务（各每 1 分钟一次）
-- ⚠️ 已于 2026-09-15 在 AFCITPDB 执行、job_id=120 / 121；重复执行会产生同名重复任务。
-- ⚠️ concurrent='1' 是【禁止并发】（'0' 才是允许，与直觉相反）：NEVER 改成 '0'，一改就是并发重复扣款。
-- ⚠️ 不执行这两条 INSERT，2.0.73 起这两条补偿链路完全不跑（离线码订单永远不扣款 / 换乘推送永远堆积）。详见 docs/ops/生产环境清单.md 附.二.2
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression,
                     misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES ('离线码金额补偿', 'DEFAULT', 'gateTxnPayQuartzTask.recoverOfflineFare()',
        '0 0/1 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每1分钟触发 gate-txn-pay-server POST /internal/gate-txn-pay/offline-fare/recover；重算离线码出站金额并补扣款，本轮笔数看 sys_job_log 的 job_message');

INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression,
                     misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES ('公交换乘推送', 'DEFAULT', 'gateTxnPayQuartzTask.pushMetroTransfer()',
        '0 0/1 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每1分钟触发 gate-txn-pay-server POST /internal/gate-txn-pay/metro-transfer/push；投递 METRO_TRANSFER_PUSH_TASK，原为模块内 10 秒轮询');

COMMIT;

-- 核对是否写入成功、重启 web-admin 后核对是否在跑、回滚：见 docs/ops/生产环境清单.md 附.二.2

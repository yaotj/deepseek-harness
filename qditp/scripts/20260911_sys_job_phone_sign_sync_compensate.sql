-- ============================================================
-- 用途: 新增「签约展示账号同步补偿」定时任务（每 5 分钟一次）
-- ⚠️ 已于 2026-09-11 在 AFCITPDB 执行、job_id=108；重复执行会产生同名重复任务。执行前提 / 核对 SQL / 回滚 见 docs/ops/生产环境清单.md 附.二.2
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression,
                     misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES ('签约展示账号同步补偿（手机号变更）', 'DEFAULT', 'accountQuartzTask.compensatePhoneSignSync()',
        '0 0/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每5分钟触发 account-server POST /phoneSignSyncCompensate，重推 USER_PHONE_CHANGE_LOG 中 SIGN_SYNC_STATUS 为 PENDING/FAILED 的行；单批上限 200 条、重试上限 10 次');

COMMIT;

-- 核对是否写入成功、补偿实际效果、回滚：见 docs/ops/生产环境清单.md 附.二.2

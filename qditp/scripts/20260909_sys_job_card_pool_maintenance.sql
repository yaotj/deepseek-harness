-- ============================================================
-- 用途: 新增「卡池维护」定时任务（每 5 分钟一次）
-- ⚠️ 已于 2026-09-09 在 AFCITPDB 执行、job_id=107；重复执行会产生同名重复任务。执行前提 / 核对 SQL / 回滚 见 docs/ops/生产环境清单.md 附.二.2
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression,
                     misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES ('卡池维护（回收预占/补货/推进批次）', 'DEFAULT', 'cardPoolQuartzTask.runMaintenance()',
        '0 0/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每5分钟触发 card-pool-server POST /internal/card-pools/maintenance；接口只受理，执行结果看 card-pool-server 日志与 /card-pools/summary');

COMMIT;

-- 核对是否写入成功、重启 web-admin 后核对是否在跑、回滚：见 docs/ops/生产环境清单.md 附.二.2

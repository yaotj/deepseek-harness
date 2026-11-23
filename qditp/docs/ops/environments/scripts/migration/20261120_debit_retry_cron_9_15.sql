-- 20261120 扣费重试对齐旧 ITP：每天上午 9 点、下午 15 点各一次（用户 2026-11-20 裁决）
-- 适用范围：测试环境 + 红岛生产（两环境 NEVER 混用，但本条 cron 口径一致）。
-- 三条 job_id 固定（220/255/345），NEVER 新建替代 job、NEVER 改 job_name / invoke_target / concurrent。
-- 执行后 MUST 重启 web-admin：Quartz 内存 JobStore，cron 不热加载。
-- 345 的 invoke_target 未变，但语义已收窄为只捞卡在 INIT 的单，MUST 同步发布 gate-txn-pay-server，
--   否则旧 war 仍按「近 10 分钟 INIT/RETRY/FAIL」扫，会和 220/255 抢同一批单双发扣款。
-- 详见 ADR-D176；真源迁移脚本 web-server/web-quartz/src/main/resources/sql/ 下两份 *retry-job-migration.sql。

UPDATE sys_job
   SET cron_expression = '0 0 9,15 * * ?',
       update_by       = 'admin',
       update_time     = SYSDATE,
       remark          = NVL(remark, '') || ' [2026-11-20 cron 每日 9/15 各一次，ADR-D176]'
 WHERE job_id IN (220, 255, 345)
   AND cron_expression <> '0 0 9,15 * * ?';

COMMIT;

-- 校验：必须返回 3 行，CRON_EXPRESSION 全为 0 0 9,15 * * ?，STATUS 保持原值。
SELECT job_id, job_name, cron_expression, status
  FROM sys_job
 WHERE job_id IN (220, 255, 345)
 ORDER BY job_id;

-- ============================================================
-- 30 · IT-056 异步导出调度启用 + cron 改分钟级
-- ============================================================
-- 问题：sys_job JOB_ID=405（asyncExportQuartzTask.execute()）处于暂停状态，
--       SYS_JOB_LOG 全库仅 8 次人工触发，从未有 02:00 的调度记录。
-- 后果：点「后台全量导出」后任务永远停在 PENDING，用户感知不到完成。
--
-- 处置（业主 2026-11-06 裁决：启用 + cron 改分钟级）：
--   1. STATUS '1'（暂停）→ '0'（正常）
--   2. CRON_EXPRESSION '0 0 2 * * ?'（每日 02:00）→ '0 */5 * * * ?'（每 5 分钟）
--      理由：下载中心「完成即提示」体验要求任务尽快被扫到，
--            02:00 单次调度下白天提交的导出要等次日，体验不成立。
--
-- ⚠️ 改库后 MUST 重启 web-admin：Quartz 用内存 JobStore，
--    运行期 INSERT/UPDATE sys_job 不生效（AGENTS.md §7 定时任务两条硬约束）。
-- ⚠️ 重启会打断测试环境所有在线会话（共享环境，已获业主授权）。
-- ============================================================

-- ---------- 变更前核对（应返回 1 行，STATUS='1' CRON='0 0 2 * * ?'） ----------
SELECT JOB_ID, JOB_NAME, JOB_GROUP, INVOKE_TARGET, CRON_EXPRESSION, STATUS, CONCURRENT, MISFIRE_POLICY
  FROM SYS_JOB WHERE JOB_ID = 405;

-- ---------- 执行变更 ----------
UPDATE SYS_JOB
   SET STATUS = '0',
       CRON_EXPRESSION = '0 */5 * * * ?',
       UPDATE_TIME = SYSTIMESTAMP
 WHERE JOB_ID = 405;
COMMIT;

-- ---------- 变更后核对（应返回 STATUS='0' CRON='0 */5 * * * ?'） ----------
SELECT JOB_ID, JOB_NAME, CRON_EXPRESSION, STATUS, UPDATE_TIME
  FROM SYS_JOB WHERE JOB_ID = 405;

-- ============================================================
-- 重启 web-admin（MUST，否则内存 JobStore 不加载新配置）
-- ============================================================
-- 方式一（云管台，推荐）：
--   itp-ops.sh --env test cloudos act web-admin restart --yes
-- 方式二（k8s）：
--   kubectl rollout restart deployment/web-admin -n itp
--
-- 重启后验证：
--   1) 综管台可登录（30029）
--   2) 等 5 分钟后查 SYS_JOB_LOG 是否有 JOB_ID=405 的新记录：
--      SELECT * FROM SYS_JOB_LOG WHERE JOB_ID=405 ORDER BY START_TIME DESC;
--   3) 点「后台全量导出」→ 任务应在 5 分钟内被扫到
-- ============================================================

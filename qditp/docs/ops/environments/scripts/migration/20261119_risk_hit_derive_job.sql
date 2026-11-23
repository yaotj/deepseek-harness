-- 20261119 风控命中批量派生调度（IT-086 方案 B，为 IT-083 自动加黑补命中输入）
-- job_id 动态取 MAX+5 防争用（IT-066 口径）；幂等：invoke_target 已存在即跳过。
-- 触发时点 :02/:12/...，早于加黑任务 425 的 :05/:15/...，保证同轮「先派生、后聚合加黑」。
-- 执行后须重启 web-admin（Quartz 内存 JobStore，新 job 不热加载）。
-- 注意：job_id 由 max(job_id)+5 动态生成；测试环境 2026-11-19 首次执行落为 430（文档写 426 作废，以实查为准）。
insert into sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select (select max(job_id) + 5 from sys_job), '风控命中批量派生', 'DEFAULT', 'riskHitDeriveQuartzTask.derive()',
       '0 2/10 * * * ?', '3', '1', '1', 'admin', sysdate, '每 10 分钟扫描交易表派生黑名单直拉规则组命中（000003 超时出站+滞留进站 / 000002 自然日重复出站），写 TBL_RISK_CONTROL_LOG 供 425 自动加黑聚合'
from dual
where not exists (select 1 from sys_job where invoke_target = 'riskHitDeriveQuartzTask.derive()');

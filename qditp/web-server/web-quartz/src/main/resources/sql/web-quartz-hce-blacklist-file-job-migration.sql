
-- 动态取号（MAX(job_id)+5）+ NOT EXISTS 幂等守卫：NEVER DELETE 写死 job_id，
-- 固定号 DELETE 会误删测试库占用同号的其它调度（IT-066，见 web-quartz-oneside-job-migration.sql）。
-- 默认 status='1' 暂停：功能尚未部署验收，MUST 由运维在前台手动启用，启用前确认
-- blacklist-server 的 ACC_FTP_HOST/ACC_FTP_USERNAME/ACC_FTP_PASSWORD/ACC_FTP_REMOTE_DIR 已注入。
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT NVL(MAX(job_id),0)+5 FROM sys_job), 'HCE黑名单文件生成上传', 'DEFAULT', 'hceBlacklistFileQuartzTask.generateAndUpload()', '0 0 2 * * ?', '3', '1', '1', 'admin', SYSDATE,
        '每天凌晨2点触发 blacklist-server POST /internal/blacklist/hce-file/generate：查 BLACKLIST 里 HCE 票种（CARD_TYPE=0442/0443）的生效行（RELEASING 仍算黑名单，与判黑同口径），分配只进不退的文件版本号，生成 PRM.0013.9700.<6位版本> 二进制全量黑名单文件并上传 ACC FTP。上传失败时文件留在服务端本地目录（blacklist.hce-file.local-dir，缺省 /tmp/hce-blacklist），响应 message 带路径，可人工补传；下一轮是新版本全量文件自然覆盖。本轮结果看 sys_job_log 与响应的 fileName/version/cardCount/uploaded'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'hceBlacklistFileQuartzTask.generateAndUpload()'
);

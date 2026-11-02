-- 异步导出任务 MVP 初始化 SQL（Oracle，幂等执行）
-- 包含：ASYNC_EXPORT_TASK 表、序列、sys_job 定时任务、菜单
--
-- 说明：
-- - 任务表放在 QDITP schema，与 web-admin 数据源一致。
-- - 文件生成目录约定：/home/javaapp/app/export/（复用现有 NFS PVC app-logs-v2）。
-- - Quartz 内存 JobStore：INSERT sys_job 后须重启 web-admin 才生效。
-- - 本 MVP 只接入 KEY_VERSION；其余 10 个 task_code 随后续批次补充。

-- ================== 1. 任务表 ==================
CREATE TABLE ASYNC_EXPORT_TASK (
    id              NUMBER(19)      NOT NULL,
    task_code       VARCHAR2(64)    NOT NULL,
    task_name       VARCHAR2(128)   NOT NULL,
    status          VARCHAR2(20)    NOT NULL,
    param_json      CLOB,
    file_path       VARCHAR2(512),
    file_size       NUMBER(19),
    row_count       NUMBER(19),
    error_msg       VARCHAR2(2000),
    execute_time    DATE,
    finish_time     DATE,
    retry_count     NUMBER(3)       DEFAULT 0,
    create_by       VARCHAR2(64),
    create_time     DATE            DEFAULT SYSDATE,
    update_by       VARCHAR2(64),
    update_time     DATE            DEFAULT SYSDATE,
    remark          VARCHAR2(500),
    CONSTRAINT pk_async_export_task PRIMARY KEY (id)
);

COMMENT ON TABLE ASYNC_EXPORT_TASK IS '异步导出任务表';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.id IS '任务ID';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.task_code IS '任务编码';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.task_name IS '任务中文名';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.status IS '任务状态';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.param_json IS '查询条件 JSON';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.file_path IS '生成文件路径';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.file_size IS '文件字节数';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.row_count IS '导出行数';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.error_msg IS '失败原因';
COMMENT ON COLUMN ASYNC_EXPORT_TASK.retry_count IS '重试次数';

ALTER TABLE ASYNC_EXPORT_TASK ADD (update_by VARCHAR2(64));

CREATE INDEX idx_async_export_status ON ASYNC_EXPORT_TASK(status, create_time);
CREATE INDEX idx_async_export_create_by ON ASYNC_EXPORT_TASK(create_by, create_time DESC);

-- 幂等由应用层控制：同一用户同一 task_code 同一 param_json 的 PENDING 任务只插入一条。
-- Oracle 不支持带 WHERE 的部分唯一索引，也不支持在 LOB 列上建索引；param_json 比对走 dbms_lob.compare。
DROP INDEX idx_async_export_pending_lookup;
CREATE INDEX idx_async_export_pending_lookup ON ASYNC_EXPORT_TASK(create_by, task_code, status);

-- 主键序列
CREATE SEQUENCE seq_async_export_task START WITH 1 INCREMENT BY 1 NOCACHE;

-- ================== 2. Quartz 定时任务 ==================
-- 每天 02:00 执行，错过不补；初始状态暂停（'1'），MVP 验证通过后手工切正常。
insert into sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select (select max(job_id) + 5 from sys_job), '异步导出任务调度', 'DEFAULT', 'asyncExportQuartzTask.execute()',
       '0 0 2 * * ?', '3', '1', '1', 'admin', sysdate, '每天凌晨2点扫描 ASYNC_EXPORT_TASK 的 PENDING 任务，按 task_code 路由到对应服务生成 xlsx'
from dual
where not exists (select 1 from sys_job where invoke_target = 'asyncExportQuartzTask.execute()');

-- ================== 3. 菜单 ==================
-- 挂在「系统监控」(menu_id=2) 下，与「卡池管理」等同级。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2090, '异步导出任务', 2,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2),
       'async-export', 'monitor/async-export/index', '', 'AsyncExportTask', 1, 0, 'C', '0', '0', 'monitor:async-export:query', 'download', 'admin', SYSDATE, '', NULL, '查看后台异步导出任务状态并下载生成的文件'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2090);

-- 导出提交按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2091, '提交异步导出', 2090, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'monitor:async-export:submit', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2091);

-- 下载按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2092, '下载导出文件', 2090, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'monitor:async-export:download', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2092);

-- 默认把菜单分配给超管角色（role_id=2）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, menu_id FROM (
    SELECT 2090 AS menu_id FROM dual
    UNION ALL SELECT 2091 FROM dual
    UNION ALL SELECT 2092 FROM dual
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu target WHERE target.role_id = 2 AND target.menu_id = source.menu_id);

COMMIT;

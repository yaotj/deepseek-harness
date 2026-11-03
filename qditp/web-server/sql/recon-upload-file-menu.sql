-- 对账文件上传功能初始化 SQL（Oracle，幂等执行）
-- 包含：RECON_UPLOAD_FILE 表、序列、菜单
--
-- 说明：
-- - 表放在 QDITP schema，与 web-admin 数据源一致。
-- - 文件落盘目录约定：/home/javaapp/app/export/recon-upload/（复用现有 NFS PVC app-logs-v2）。
-- - 菜单挂在「交易运营」(menu_id=2035) 下。

-- ================== 1. 对账文件上传表 ==================
-- Oracle（< 23ai）不支持 CREATE TABLE IF NOT EXISTS，用匿名块捕获 ORA-00955 幂等。
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE RECON_UPLOAD_FILE (
        id              NUMBER(19)      NOT NULL,
        file_name       VARCHAR2(255)   NOT NULL,
        file_path       VARCHAR2(512)   NOT NULL,
        file_size       NUMBER(19),
        file_type       VARCHAR2(32),
        recon_date      VARCHAR2(8),
        status          VARCHAR2(20)    DEFAULT ''UPLOADED'',
        create_by       VARCHAR2(64),
        create_time     DATE            DEFAULT SYSDATE,
        update_by       VARCHAR2(64),
        update_time     DATE            DEFAULT SYSDATE,
        remark          VARCHAR2(500),
        CONSTRAINT pk_recon_upload_file PRIMARY KEY (id)
    )';
EXCEPTION WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

COMMENT ON TABLE RECON_UPLOAD_FILE IS '对账文件上传记录表';
COMMENT ON COLUMN RECON_UPLOAD_FILE.id IS '文件ID';
COMMENT ON COLUMN RECON_UPLOAD_FILE.file_name IS '原始文件名';
COMMENT ON COLUMN RECON_UPLOAD_FILE.file_path IS '文件存储绝对路径';
COMMENT ON COLUMN RECON_UPLOAD_FILE.file_size IS '文件字节数';
COMMENT ON COLUMN RECON_UPLOAD_FILE.file_type IS '文件类型（扩展名）';
COMMENT ON COLUMN RECON_UPLOAD_FILE.recon_date IS '对账日期 yyyyMMdd';
COMMENT ON COLUMN RECON_UPLOAD_FILE.status IS '状态：UPLOADED 已上传';

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_recon_upload_file_create_time ON RECON_UPLOAD_FILE(create_time DESC)';
EXCEPTION WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_recon_upload_file_recon_date ON RECON_UPLOAD_FILE(recon_date)';
EXCEPTION WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

-- 主键序列
BEGIN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_recon_upload_file START WITH 1 INCREMENT BY 1 NOCACHE';
EXCEPTION WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

-- ================== 2. 菜单 ==================
-- 挂在「交易运营」(menu_id=2035) 下。
-- 注意：menu_id 取 max+1，避免号段冲突；执行前请先 select max(menu_id) from sys_menu 确认。

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT (select max(menu_id) + 1 from sys_menu), '对账文件上传', 2035,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2035),
       'recon-upload-file', 'trans/recon-upload-file/index', '', 'ReconUploadFile', 1, 0, 'C', '0', '0', 'recon:upload-file:query', 'upload', 'admin', SYSDATE, '', NULL, '上传对账文件并按倒序展示，支持下载与删除'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'recon:upload-file:query');

-- 上传按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT (select max(menu_id) + 1 from sys_menu), '对账文件上传按钮',
       (select menu_id from sys_menu where perms = 'recon:upload-file:query'),
       1, '', '', '', '', 1, 0, 'F', '0', '0', 'recon:upload-file:upload', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'recon:upload-file:upload');

-- 下载按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT (select max(menu_id) + 1 from sys_menu), '对账文件下载按钮',
       (select menu_id from sys_menu where perms = 'recon:upload-file:query'),
       2, '', '', '', '', 1, 0, 'F', '0', '0', 'recon:upload-file:download', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'recon:upload-file:download');

-- 删除按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT (select max(menu_id) + 1 from sys_menu), '对账文件删除按钮',
       (select menu_id from sys_menu where perms = 'recon:upload-file:query'),
       3, '', '', '', '', 1, 0, 'F', '0', '0', 'recon:upload-file:remove', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'recon:upload-file:remove');

-- 默认把菜单分配给超管角色（role_id=2）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, menu_id FROM (
    SELECT menu_id FROM sys_menu WHERE perms IN (
        'recon:upload-file:query',
        'recon:upload-file:upload',
        'recon:upload-file:download',
        'recon:upload-file:remove'
    )
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu target WHERE target.role_id = 2 AND target.menu_id = source.menu_id);

COMMIT;

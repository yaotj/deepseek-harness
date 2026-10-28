-- 批量导入记录表
-- 用途：追踪每次 Excel 导入操作的元数据，关联导入明细表
-- 设计原则：与 RuoYi BaseEntity 保持一致，主键 bigint 自增（Oracle 配合 SEQUENCE）

CREATE TABLE biz_import_record (
    -- ========== 主键 ==========
    import_id       NUMBER(20)      NOT NULL                   COMMENT '导入记录ID',

    -- ========== 基础信息 ==========
    biz_code        VARCHAR2(64)    NOT NULL                   COMMENT '业务标识（对应 BizRouter.register 的 key）',
    biz_name        VARCHAR2(100)   DEFAULT ''                 COMMENT '业务名称（冗余展示，便于查询）',
    file_name       VARCHAR2(255)   DEFAULT ''                 COMMENT '原始文件名',
    file_size       NUMBER(20)      DEFAULT 0                  COMMENT '文件大小（字节）',
    file_url        VARCHAR2(500)   DEFAULT ''                 COMMENT '文件存储路径（本地/OSS，供后续复查）',

    -- ========== 用户信息 ==========
    import_by       VARCHAR2(64)    DEFAULT ''                 COMMENT '操作人（与 sys_user.user_name 对应）',
    dept_id         NUMBER(20)      DEFAULT NULL               COMMENT '操作人所属部门ID',
    client_ip       VARCHAR2(128)   DEFAULT ''                 COMMENT '操作人IP',

    -- ========== 批次号（幂等关键） ==========
    batch_no        VARCHAR2(64)    NOT NULL                   COMMENT '导入批次号（UUID，作为幂等键，可跨环境复用）',

    -- ========== 统计信息 ==========
    total_rows      NUMBER(10)      DEFAULT 0                  COMMENT '总行数（不含表头）',
    success_rows    NUMBER(10)      DEFAULT 0                  COMMENT '成功条数',
    fail_rows       NUMBER(10)      DEFAULT 0                  COMMENT '失败条数',
    update_support  CHAR(1)         DEFAULT '0'                COMMENT '是否允许更新（0否 1是）',

    -- ========== 状态 ==========
    status          CHAR(1)         DEFAULT '0'                COMMENT '导入状态（0处理中 1成功 2失败）',
    finish_time     DATE                                   COMMENT '完成时间',
    error_msg       VARCHAR2(2000)  DEFAULT ''                 COMMENT '错误摘要（失败时记录）',

    -- ========== 审计字段（BaseEntity） ==========
    create_by       VARCHAR2(64)    DEFAULT ''                 COMMENT '创建者',
    create_time     DATE                                   COMMENT '创建时间',
    update_by       VARCHAR2(64)    DEFAULT ''                 COMMENT '更新者',
    update_time     DATE                                   COMMENT '更新时间',
    remark          VARCHAR2(500)   DEFAULT NULL               COMMENT '备注'
);

-- 注释
COMMENT ON TABLE biz_import_record IS '批量导入记录表';
COMMENT ON COLUMN biz_import_record.import_id IS '导入记录ID';
COMMENT ON COLUMN biz_import_record.biz_code IS '业务标识（对应 BizRouter 注册的 key）';
COMMENT ON COLUMN biz_import_record.file_name IS '原始文件名';
COMMENT ON COLUMN biz_import_record.batch_no IS '导入批次号（UUID，幂等键）';
COMMENT ON COLUMN biz_import_record.total_rows IS '总行数';
COMMENT ON COLUMN biz_import_record.success_rows IS '成功条数';
COMMENT ON COLUMN biz_import_record.fail_rows IS '失败条数';
COMMENT ON COLUMN biz_import_record.status IS '导入状态（0处理中 1成功 2失败）';

-- 索引
CREATE INDEX idx_import_biz_code ON biz_import_record(biz_code);
CREATE INDEX idx_import_batch_no ON biz_import_record(batch_no) GLOBAL;
CREATE INDEX idx_import_create_time ON biz_import_record(create_time);
CREATE INDEX idx_import_import_by ON biz_import_record(import_by);

-- 序列（Oracle）
CREATE SEQUENCE seq_biz_import_record START WITH 1 INCREMENT BY 1 NOCACHE;

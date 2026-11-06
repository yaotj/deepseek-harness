-- 对账文件上传：固定命名 + 「一日期一类别只有一份」唯一约束（2026-11-06）
--
-- 命名真源（不是本脚本发明的，改动前 MUST 先读这两处）：
--   - recon-server/src/main/java/com/chinasofti/huateng/recon/service/ReconFtpService.java:42
--       正则 ITP\.(EXP|PAY|BUS|DETAIL|TXNDIFF)\.\d{8}，不匹配直接抛「非法对账文件名」，且无扩展名。
--   - recon-server/src/main/java/com/chinasofti/huateng/recon/storage/ThirdPartyReconProperties.java:24
--       渠道日账单默认 glob reconciliation-bill-*.csv（可被 env RECON_THIRDPARTY_BILL_PATTERN 覆盖）。
--   类别中文含义真源：model/src/main/java/com/chinasofti/huateng/model/recon/ReconFileTypeEnum.java
--                    + docs/business/recon.md §「ITP.EXP / ITP.PAY / ITP.BUS / ITP.DETAIL」。
--
-- 业主裁决（2026-11-06）：两套命名都收；固定名之后允许扩展名；
--   重复上传是替换，旧磁盘文件改名 .replaced-<时间戳> 留存；
--   命名规则上线前已存在的记录（FILE_CATEGORY 为 NULL）豁免只读、不参与唯一约束。
--
-- 幂等：可重复执行。执行环境：测试 AFCITPDB / 红岛 hditpdb 各跑一次（两库同构不同实例）。
-- 执行方式：沿用 web-server/sql/apply_recon_upload_file.py 的连库跑法。

-- ============================================================
-- 1. 新增类别列
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM user_tab_cols
     WHERE table_name = 'RECON_UPLOAD_FILE' AND column_name = 'FILE_CATEGORY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE RECON_UPLOAD_FILE ADD (FILE_CATEGORY VARCHAR2(16))';
    END IF;
END;
/

COMMENT ON COLUMN RECON_UPLOAD_FILE.FILE_CATEGORY IS
'文件类别：EXP=单边交易明细(13段) / PAY=统计汇总(21段) / BUS=商业优惠汇总(4段) / DETAIL=虚拟电子多日计次票明细 / TXNDIFF=对账差异明细(21段) / BILL=渠道日账单(22字段)；NULL=命名规则上线前的历史行，豁免只读、不参与唯一约束';

-- ============================================================
-- 2. 唯一约束：同一 (类别, 对账日期) 只允许一份文件
--
--    MUST 用函数式索引而不是普通复合唯一索引：Oracle 只跳过「全部键列均为 NULL」的索引项，
--    普通索引下历史行 (NULL, '20261104') 仍会入索引并互相冲突，两行同日期历史数据会直接建索引失败。
--    这里把两个表达式都按 FILE_CATEGORY IS NULL 置 NULL ⇒ 历史行整条键为 NULL、自动豁免，
--    正是「旧数据不参与唯一约束」这条裁决的实现方式。
-- ============================================================
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM user_indexes
     WHERE index_name = 'UK_RUF_CATEGORY_DATE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE UNIQUE INDEX UK_RUF_CATEGORY_DATE ON RECON_UPLOAD_FILE (
                CASE WHEN FILE_CATEGORY IS NULL THEN NULL ELSE FILE_CATEGORY END,
                CASE WHEN FILE_CATEGORY IS NULL THEN NULL ELSE RECON_DATE    END
            )';
    END IF;
END;
/

-- 列表按类别筛选走这个普通索引（函数式唯一索引只在两列等值时才可被利用）
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM user_indexes
     WHERE index_name = 'IDX_RUF_CATEGORY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_RUF_CATEGORY ON RECON_UPLOAD_FILE (FILE_CATEGORY)';
    END IF;
END;
/

-- ============================================================
-- 3. 历史数据不回填
--
--    刻意 NOT 给现有行补 FILE_CATEGORY：那 2 行原始文件名是「对账.xls」，
--    本身不符合任何一套命名约定，硬归类会造出一个「看起来合规、实际名字对不上」的假数据。
--    保持 NULL ⇒ 列表仍可见、可下载、可删除，只是新规则不再产生同类行。
-- ============================================================

-- ============================================================
-- 回滚（需要时逐条手工执行）
-- ============================================================
-- DROP INDEX IDX_RUF_CATEGORY;
-- DROP INDEX UK_RUF_CATEGORY_DATE;
-- ALTER TABLE RECON_UPLOAD_FILE DROP COLUMN FILE_CATEGORY;

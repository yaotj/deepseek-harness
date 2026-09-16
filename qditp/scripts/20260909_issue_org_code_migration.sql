/* ============================================================
   USER_ITP_REG_INFO 发行渠道编码 / 发卡机构编码 拆分迁移
   日期: 2026-09-09
   背景: B14 —— APP 开户上送的 4 位「发卡机构编码」被原样写入 CARD_ISSUE_CODE，
         生码时 industry-data-server 的 normalizeHex(value, 2, "01") 取右侧 2 位，
         5412 -> 12、0008 -> 08，落进码体渠道位。而 IssueChannelCodeEnum 只有
         01(正常渠道) / 07(支付宝) 两个合法值。
   本次: CARD_ISSUE_CODE 收窄为发行渠道编码（仅 0001 / 0007），
         新增 ISSUE_ORG_CODE 保留 APP 原值供后续统计。
   注意: 本文件供人工在生产库按段执行，NEVER 整体一次跑完。
         QRCODE_TXN_DETAIL 里已落的 82 条 12 / 08 是历史交易的客观记录，
         本次不修改（用户 2026-09-09 决定）。受影响区间 2026-08-07 ~ 修复日。

   执行记录（2026-09-09 已在生产库 172.20.222.3/AFCITPDB 执行完毕，MCP 连接 qditp3）:
     迁移前分布 USER_ITP_REG_INFO.CARD_ISSUE_CODE 只有两种取值，无 0007、无 NULL:
       0008  11 行  REG_TMS 2026-08-12 15:42:35 ~ 2026-08-13 13:38:31
       5412  13 行  REG_TMS 2026-06-25 13:46:47 ~ 2026-09-09 18:53:30
     第 0 步备份表 24 行；第 3 步搬原值 24 行；第 4 步归一 24 行。
     迁移后 CARD_ISSUE_CODE 全为 0001，ISSUE_ORG_CODE 保留 0008(11) / 5412(13)。
     注: 0007 支付宝出行的用户不在本表，在 ALIPAY_USER_INFO（4 行，本次未动）。
   ============================================================ */

/* ---------- 第 0 步：备份（MUST 先执行，确认行数后再往下） ---------- */
CREATE TABLE USER_ITP_REG_INFO_BAK20260909 AS
SELECT ID, THIRD_USER_ID, CARD_ISSUE_CODE, CHANNEL, REG_TMS
FROM USER_ITP_REG_INFO
WHERE CARD_ISSUE_CODE IS NOT NULL
  AND CARD_ISSUE_CODE NOT IN ('0001', '0007');

SELECT COUNT(*) AS BAK_ROWS FROM USER_ITP_REG_INFO_BAK20260909;

/* ---------- 第 1 步：加列（低风险，可空、无默认值） ---------- */
ALTER TABLE USER_ITP_REG_INFO ADD (ISSUE_ORG_CODE VARCHAR2(16 CHAR));

COMMENT ON COLUMN USER_ITP_REG_INFO.ISSUE_ORG_CODE IS '发卡机构编码，APP开户上送原值，0004海上巴士/0007支付宝出行/0008成都地铁/0020青岛地铁早期/5412青岛地铁/5413畅行U惠小程序，仅留痕不参与码体拼装';
COMMENT ON COLUMN USER_ITP_REG_INFO.CARD_ISSUE_CODE IS '发行渠道编码，仅0001正常渠道/0007支付宝出行，参与码体拼装，取右2位落码体渠道位';

/* ---------- 第 2 步：迁移前核对（只读，记录原始分布） ---------- */
SELECT CARD_ISSUE_CODE, COUNT(*) AS CNT,
       MIN(REG_TMS) AS FIRST_REG, MAX(REG_TMS) AS LAST_REG
FROM USER_ITP_REG_INFO
GROUP BY CARD_ISSUE_CODE
ORDER BY CARD_ISSUE_CODE;

/* ---------- 第 3 步：原值搬到新列（全表，含已合法的 0007） ---------- */
UPDATE USER_ITP_REG_INFO
SET ISSUE_ORG_CODE = TRIM(CARD_ISSUE_CODE)
WHERE CARD_ISSUE_CODE IS NOT NULL
  AND ISSUE_ORG_CODE IS NULL;

/* ---------- 第 4 步：CARD_ISSUE_CODE 归一为发行渠道编码 ----------
   0007 支付宝出行 -> 0007 保持；其余机构码（0004 / 0008 / 0020 / 5412 / 5413）
   与任何未知值 -> 0001 正常渠道。
   与 CardIssueOrgEnum.toIssueChannelCode4 的口径必须一致，改一处 MUST 改两处。 */
UPDATE USER_ITP_REG_INFO
SET CARD_ISSUE_CODE = '0001'
WHERE CARD_ISSUE_CODE IS NOT NULL
  AND TRIM(CARD_ISSUE_CODE) <> '0007'
  AND TRIM(CARD_ISSUE_CODE) <> '0001';

UPDATE USER_ITP_REG_INFO
SET CARD_ISSUE_CODE = '0007'
WHERE TRIM(CARD_ISSUE_CODE) = '0007';

COMMIT;

/* ---------- 第 5 步：迁移后校验（期望只剩 0001 / 0007 / NULL） ---------- */
SELECT CARD_ISSUE_CODE, ISSUE_ORG_CODE, COUNT(*) AS CNT
FROM USER_ITP_REG_INFO
GROUP BY CARD_ISSUE_CODE, ISSUE_ORG_CODE
ORDER BY CARD_ISSUE_CODE, ISSUE_ORG_CODE;

SELECT COUNT(*) AS ILLEGAL_ROWS
FROM USER_ITP_REG_INFO
WHERE CARD_ISSUE_CODE IS NOT NULL
  AND CARD_ISSUE_CODE NOT IN ('0001', '0007');

/* ============================================================
   还原（仅在校验不通过时执行）
   ============================================================ */
/*
UPDATE USER_ITP_REG_INFO T
SET CARD_ISSUE_CODE = (SELECT B.CARD_ISSUE_CODE
                       FROM USER_ITP_REG_INFO_BAK20260909 B
                       WHERE B.ID = T.ID)
WHERE EXISTS (SELECT 1
              FROM USER_ITP_REG_INFO_BAK20260909 B
              WHERE B.ID = T.ID);

UPDATE USER_ITP_REG_INFO SET ISSUE_ORG_CODE = NULL;

COMMIT;

ALTER TABLE USER_ITP_REG_INFO DROP COLUMN ISSUE_ORG_CODE;
*/

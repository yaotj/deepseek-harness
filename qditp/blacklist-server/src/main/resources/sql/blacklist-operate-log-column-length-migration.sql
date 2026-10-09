-- BLACKLIST_OPERATE_LOG 的 CARD_ID 与 REASON 扩长，与 BLACKLIST / BLACKLIST_RELEASED 对齐
--
-- 背景：线上这张表不是按 blacklist-schema.sql 建的，两者逐列比对后有 4 处分叉
-- （实测 USER_TAB_COLS.CHAR_LENGTH，2026-09-18）：
--
--   列             线上实测   schema.sql   BLACKLIST   BLACKLIST_RELEASED
--   CARD_ID          16          32           32             32          <- 本次修
--   REASON          500        1000         1000           1000          <- 本次修
--   THIRD_USER_ID   128          16           16             16          <- 有意不动
--   OPERATE_TYPE     16          32            -              -          <- 有意不动
--
-- 为什么前两处 MUST 修：这两列写入的值与主表同源、同一事务。
--   CARD_ID：BlacklistServiceImpl.insertOperateLog 传的就是写进 BLACKLIST.CARD_ID 的那个
--            cardId，加黑与发起解除各写一行。卡号长度上限由主表定义为 32，一旦来了 17~32 位
--            的卡号，主表 INSERT 成功、操作日志 INSERT 报 ORA-12899，两条在同一个事务里，
--            于是整笔加黑 / 整笔发起解除直接失败并回滚。
--   REASON ：同理，ADD 记 request.getReason()（同时写进 BLACKLIST.REASON，那列 1000），
--            DELETE 记 releaseReason（主表 RELEASE_REASON 500、历史表同名列 500）。
--            因此 1000 是这一族里的上界，500 会在「原因写满 500~1000 字」时把整笔操作打挂。
--   两者的共同特征：不是「日志少记点信息」，而是**审计表把主业务事务拖失败**，而编译、单测、
--   xmllint 全都发现不了 —— 只在真实值超长那一次运行时炸。
--
-- 为什么后两处有意不动：
--   THIRD_USER_ID 线上 128 比同族的 16 更宽，只是分叉、没有截断风险（实测现存 43 行最长 10）。
--   收窄是有损 DDL、没有任何收益，NEVER 为了「看起来整齐」把它改成 16。
--   OPERATE_TYPE 线上 16，实际只写 ADD / DELETE 两个字面量（实测最长 6），16 够用；
--   这两处的分叉改记进 blacklist-schema.sql，让新建库与线上一致。
--
-- 扩长（VARCHAR2 加大）是安全操作：不重写行、不丢数据、不需要停机，CARD_ID 上的非唯一索引
-- IDX_BLACKLIST_LOG_CARD_ID 也不会失效。NEVER 反向收窄这两列。
--
-- 执行记录：2026-09-18 在 AFCITPDB（172.20.222.3:1521，用户 qditp）执行，
-- 用 USER_TAB_COLS.CHAR_LENGTH 回查通过（CARD_ID 32、REASON 1000），执行前表内 43 行、
-- CARD_ID 最长 16 / REASON 最长 26，无行需要回填。

ALTER TABLE BLACKLIST_OPERATE_LOG MODIFY (CARD_ID VARCHAR2(32 CHAR));

ALTER TABLE BLACKLIST_OPERATE_LOG MODIFY (REASON VARCHAR2(1000 CHAR));

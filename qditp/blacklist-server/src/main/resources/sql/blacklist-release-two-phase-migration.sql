-- blacklist-server 解除黑名单改为「先通知成功、再删主表行」的两阶段流程
--
-- 背景：原实现是「搬快照到 BLACKLIST_RELEASED + 删主表行 + 提交后异步推解除通知」。
-- 本地先解除、通知异步收敛，于是存在一个窗口：我方已放行、支付宝渠道侧仍按拉黑拦着，
-- 用户被告知已解除却仍过不了闸；若解除通知最终落 REJECTED 终态，这个不一致将永久存在
-- 而我方看起来一切正常。加黑方向的同类窗口后果是「欠费卡在支付宝渠道多过一次闸」，
-- 代价小、可后续补加，因此加黑保持乐观、只有解除改成两阶段（有意的不对称）。
--
-- 新流程：
--   阶段一（事务）：STATUS 从 ACTIVE CAS 成 RELEASING，CHANNEL_SYNC_STATUS 重置为 PENDING，
--                   记 RELEASE_REASON / RELEASE_BY。不搬历史、不删行。
--   提交后出网推解除通知。
--   阶段二（事务）：只有拿到成功应答才搬快照到 BLACKLIST_RELEASED 并删主表行。
--   不可达 -> 留 RELEASING + FAILED，等扫表补偿重入；业务拒绝 -> REJECTED 终态 + 人工。
--
-- 为什么只加一列、不给解除单独再建一套 outbox 四列：
-- 加黑通知推成功后 CHANNEL_SYNC_* 那四列就闲置了，解除时重置复用即可，靠 STATUS 区分方向：
--   STATUS='ACTIVE'    + CHANNEL_SYNC_STATUS='PENDING' => 加黑待推
--   STATUS='RELEASING' + CHANNEL_SYNC_STATUS='PENDING' => 解黑待推
-- NEVER 再给本表加第二套同义列 —— 两套列会让「这行到底在推哪个方向」无法单凭一行判断。
--
-- 判黑读路径不需要改：countByCardIds / selectByCardIds / queryBlackList 都不带 STATUS 过滤，
-- RELEASING 的行仍在表里、仍算黑名单，这正是要的语义（通知没成功前 MUST NOT 提前放行）。
-- NEVER 给这些查询加 STATUS='ACTIVE' 条件 —— 那等于把解除中的卡提前放行，本次改造就白做了。
-- 唯一要收窄的是 selectForInspect（可解除性盘点），MUST 排除 RELEASING，否则正在解除的卡
-- 会被反复报进待解除清单。
--
-- 不新建索引：黑名单表承载的是欠费用户，规模天然很小，现有
-- IDX_BLACKLIST_SYNC_SCAN (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_TIME) 足够把待推行筛出来，
-- STATUS 作为回表后的过滤条件即可。规模真的涨上来再评估。
--
-- 连带说明：BLACKLIST_RELEASED 上 2026-09-18 早先加的 CHANNEL_SYNC_* 四列与
-- IDX_BL_RELEASED_SYNC_SCAN 索引，自本次改造起**不再是解除通知的 outbox 载体**
-- （载体回到主表），降级为历史审计字段，保留不删。NEVER 再据那四列做补偿扫表。
--
-- 为什么主表也要有 RELEASE_REASON / RELEASE_BY：
-- 阶段一只标记 RELEASING、不搬历史，这两个值必须先在主表上暂存下来，阶段二才写进历史表。
-- 靠 afterCommit 的 lambda 捕获是不够的 —— 那是进程内的，JVM 一重启扫表补偿就拿不到，
-- 补偿成功后写进 BLACKLIST_RELEASED 的将是缺省值，**解除原因与操作者永久丢失**。
-- 这两列长度 MUST 与 BLACKLIST_RELEASED 上的同名列逐字一致（500 / 32），NEVER 让它们分叉 ——
-- 阶段二是把主表这两列原样搬过去，列长不一致会在截断处静默丢信息。
--
-- 执行记录：2026-09-18 在 AFCITPDB（172.20.222.3:1521，用户 qditp）执行并用
-- USER_TAB_COLS.CHAR_LENGTH + 逐值 COUNT 回查通过（STATUS 16 / DEFAULT 'ACTIVE'、
-- RELEASE_REASON 500、RELEASE_BY 32；执行时主表 0 行，无历史行需要回填）。

ALTER TABLE BLACKLIST ADD (STATUS VARCHAR2(16) DEFAULT 'ACTIVE');

ALTER TABLE BLACKLIST ADD (RELEASE_REASON VARCHAR2(500), RELEASE_BY VARCHAR2(32));

UPDATE BLACKLIST SET STATUS = 'ACTIVE' WHERE STATUS IS NULL;

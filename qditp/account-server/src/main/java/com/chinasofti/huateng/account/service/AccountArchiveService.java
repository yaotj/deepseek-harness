package com.chinasofti.huateng.account.service;

/**
 * 销户归档：把已注销且已无支付通道的开户记录迁到 {@code USER_ITP_REG_LOG}（{@code OPER_TYPE=3}）后物理删除。
 *
 * <p><b>归档是本域自己算出来的派生规则，不是被谁远程驱动的一次状态迁移</b> ——
 * 「最后一个通道解绑 ⇒ 销户归档」由 account-server 判定，pay-sign-server 只负责把「通道已解绑」这个事实送到。
 * 判据见 {@code docs/domain/README.md} §三第 4 条，<b>NEVER 改成由支付域调一个「请归档」的端点</b>。</p>
 *
 * <p><b>两个入口的异常语义刻意不同，NEVER 合并成一个方法</b>（这是 2026-09-09 两次线上修复留下的形状）：
 * <ul>
 *   <li>{@link #archiveIfLastChannelRemoved} —— 解绑侧（IF8A-75）用，<b>在调用方事务内执行、不吞异常</b>，
 *       归档失败 MUST 让整个删通道操作回滚（用户 2026-09-08 裁决：NEVER 留「通道已删、归档没做」的半成品）。</li>
 *   <li>{@link #tryArchiveAfterCancel} —— 销户侧（IF8A-42）用，<b>自开短事务、吞异常只记 warn</b>，
 *       归档失败 NEVER 把已成功的销户翻成失败。</li>
 * </ul>
 * 把两者统一成一种异常策略，必然破坏其中一边。</p>
 *
 * <p>归档本身幂等：三条件任一不满足即原样返回、不动数据，因此重复调用安全。</p>
 */
public interface AccountArchiveService {

    /**
     * 解绑掉最后一个签约渠道后的归档，<b>在调用方的事务内执行</b>。
     *
     * <p>触发条件三个同时满足，任一不满足即原样返回：①该用户在 {@code APP_USER_PAY_CHANNEL} 已无任何通道；
     * ②{@code USER_ITP_REG_INFO} 还有记录；③这些记录<b>全部</b>是注销态（{@code DEL_YN = 0}，IF8A-42 已执行）。</p>
     *
     * <p><b>不吞异常</b>：抛出即让调用方（{@code requestRemovePayChannel}）整单回滚。</p>
     */
    void archiveIfLastChannelRemoved(String thirdUserId);

    /**
     * 销户（IF8A-42）后补一次归档尝试，<b>自开独立短事务、失败只记 warn</b>。
     *
     * <p>为什么销户侧也要试：正常顺序 35 → 42 → 75 下此时通道还在，归档会自行跳过；
     * 但实测存在<b>反序场景</b>——用户先把通道全解绑、之后才销户（{@code 00522946}：通道 10:07/10:45 删完，
     * 14:24 才销户），此时 {@link #archiveIfLastChannelRemoved} 那条路径永远不会再被触发，
     * 归档三条件明明全满足却没有代码去检查，记录以 {@code DEL_YN=0} 永久残留。</p>
     *
     * <p><b>NEVER 让本方法抛异常</b>：归档幂等、下次调用会重试，翻掉已成功的销户会让 APP 陷入重试。</p>
     */
    void tryArchiveAfterCancel(String thirdUserId);
}

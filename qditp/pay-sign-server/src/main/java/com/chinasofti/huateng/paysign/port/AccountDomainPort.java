package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;

/**
 * 支付域看账户域的**窄接口**（防腐层），只暴露本域真正需要的三个**写**动作。
 *
 * <p><b>命名说明</b>：2026-09-12 的评估里我把它写成了 {@code PayDomainPort}，那是**方向写反了** ——
 * 端口在 pay-sign-server 里、指向的是账户域，叫 {@code PayDomainPort} 等于指向自己。<b>NEVER 改回那个名字</b>。</p>
 *
 * <p><b>为什么要这一层</b>（ADR-D46）：改造前 {@code accountClient.xxx(...)} 散在
 * {@code PaySignWorkflow} 与 {@code SignResultCommittedListener} 里，带来两个具体麻烦 ——
 * ①每个调用点各自装配 {@code RequestRemovePayChannelReqDTO} 并各写一遍 retCode 判定；
 * ②要给这些方法写单测就得 mock {@code AccountClient} 和一串 rpc DTO。收口后调用方只依赖本接口，
 * 单测 mock 一个接口即可，DTO 装配与 {@code retCode} → {@link RpcOutcome} 的翻译只在
 * {@code AccountDomainRpcAdapter} 一处。</p>
 *
 * <p><b>本接口的方法一律返回 {@link RpcOutcome}、NEVER 返回 boolean</b>（AGENTS.md §5.2 / ADR-D45）：
 * 账户域「答复了但拒绝」与「压根没打通」的正确处置相反，前者重推无用、后者才该重试。
 * 实现方 <b>NEVER 向外抛异常</b>，异常统一收成 {@link RpcOutcome.Unreachable}。</p>
 *
 * <p><b>读侧已于 2026-09-16 收进本接口（ADR-D94 续）</b>。此前这里写着「刻意只收写、不收读」，
 * 理由是「查询 DTO 字段很多、各调用点空值处置不同，搬进来等于把 rpc DTO 换个地方再暴露一次」——
 * <b>那个判断已被实证推翻，NEVER 回退</b>：
 * <ul>
 *   <li>「字段很多」不成立：三处调用点合起来只读 6 个字段，收窄成
 *       {@link AccountUserView}（3 个）与 {@link AccountPayChannelView}（3 个）即可，
 *       并没有把 rpc DTO 换个地方暴露；</li>
 *   <li>「各调用点处置不同」**恰恰是缺陷本身**而非保留理由：`queryWalletBindingResult` 要求
 *       {@code retCode=0000} 才采信，而 `resolvePaySignInfoFromAccount` **完全不看 retCode**，
 *       于是一条 {@code 8004} 应答里残留的 {@code channel} / {@code reqContractNo} 被送去
 *       **真实免密扣款**（ADR-D94）。散落的读调用让这种分叉没人看得见。</li>
 * </ul>
 * 收口的前置条件（原 Javadoc 要求的「先给这三处补单测」）已由
 * {@code AccountReadCharacterizationTest}（7 例）与
 * {@code TerminationInternalReadCharacterizationTest}（4 例）满足。
 *
 * <p><b>读方法返回 {@link AccountQuery}，写方法返回 {@link RpcOutcome}</b>：两者都是三分类型，
 * 区别只在读需要携带载荷。<b>NEVER 让读方法返回 {@code Optional}</b> ——
 * 会把「业务拒绝」和「不可达」压成同一个 {@code empty}，而签约结果查询正依赖这个区分
 * （拒绝 → {@code 0000/NOT_SIGNED}，不可达 → {@code 9001}）。
 */
public interface AccountDomainPort {

    /**
     * 按用户 + 票卡查账户域注册信息，供钱包绑定状态查询与免密扣款补参两处使用。
     *
     * <p>拿到 {@link AccountQuery.Found} 即代表账户域已答 {@code 0000}；
     * 调用点 <b>MUST NOT</b> 再自己判一次 retCode（视图里刻意没有那个字段）。
     */
    AccountQuery<AccountUserView> queryUser(String thirdUserId, String cardId, String cardType);

    /**
     * 按签约流水号反查账户域支付通道上的票卡信息（IF8A-75 直接解绑用）。
     *
     * <p>入参 {@code reqContractNo} 就是支付域的 {@code requestSignSeq}，两侧同一个值、不同列名。
     */
    AccountQuery<AccountPayChannelView> queryPayChannelByContract(String reqContractNo);

    /**
     * 解约成功后清理账户域的支付通道（IF8A-75 同语义的内部调用）。
     *
     * <p>账户域会先删支付渠道行；若该通道正好是注册信息里的默认通道，会同步清空
     * {@code thirdPayId} / {@code channel} / {@code reqContractNo}。</p>
     */
    RpcOutcome removeChannel(String thirdUserId, String paymentVendor, String cardId, String cardType);

    /**
     * 钱包（{@code paymentVendor=0B}）误走 {@code requestTermination} 时的兼容路径：只移除本地支付通道。
     *
     * <p>走的是账户域另一个端点 {@code /requestAgreeRelease}，与 {@link #removeChannel} <b>不是同一个 URL</b>，
     * NEVER 合并成一个方法。</p>
     */
    RpcOutcome agreeRelease(String thirdUserId, String paymentVendor, String cardId, String cardType);

    /**
     * 把签约结果里的支付账号回写到账户域（展示值）。
     *
     * <p><b>调用方允许失败且不补偿</b>：权威值一直在本域 {@code APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID}，
     * 丢一次只影响运营页面那一格。账户域未命中通道行时返 {@code 8004}，那是「签约先于加通道」的合法时序、
     * 属 {@link RpcOutcome.BizRejected}，MUST 只记 info。详见 {@code SignResultCommittedListener}。</p>
     */
    RpcOutcome syncPayAccountId(String reqContractNo, String payAccountId);
}

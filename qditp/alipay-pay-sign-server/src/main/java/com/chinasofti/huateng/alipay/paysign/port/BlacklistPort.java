package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;

/**
 * 扣款失败后把卡加入黑名单的出网口（ADR-D131）。
 *
 * <p>与 {@link DebitSyncPort} **刻意分成两个端口**：两者依赖的下游不同（blacklist-server 与
 * gate-txn-pay-server）、失败处置也不同，属不相交依赖簇。NEVER 为了「看起来整齐」合成一个
 * 「出网门面」—— 合了之后任一下游改签名都要动另一条链路的测试。</p>
 *
 * <p>入参刻意是四个标量而不是 {@code AddBlackListReqDTO}：DTO 装配属 rpc 细节，
 * 留在 adapter 里，调用方只表达业务意图。</p>
 */
public interface BlacklistPort {

    /**
     * @param reason 加黑原因，落在黑名单记录上供人工核对
     * @return {@code Ok} 已确认加黑；{@code BizRejected} 对端明确拒绝（重推无意义）；
     *         {@code Unreachable} 未获答复（可重试）
     */
    RpcOutcome addBlackList(String cardId, String thirdUserId, String cardType, String reason);
}

package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;

/**
 * 支付宝渠道看**账户域支付通道方向**的窄接口（防腐层，ADR-D131）。
 *
 * <p>存在的唯一理由：{@code AlipayAccountClient.updatePaymentChannel} 返回 {@code boolean}，
 * 而 {@code boolean} 分不清「对端答复了但拒绝」与「压根没答上」—— 前者重推一万次也不会成功、
 * MUST 一次即终态并开工单，后者才该进补偿队列（AGENTS.md §5.2）。</p>
 *
 * <p>能做这个区分的前提是一条实测事实：那个 client <b>内部并不 catch 异常</b>，
 * {@code getAndGetResponse} 抛出的 {@code RuntimeException} 会原样冒泡；它只在响应体为空时返
 * {@code false}。因此「抛异常 = 不可达」「返 false = 业务拒绝」这条映射成立，
 * <b>不需要改 {@code rpc} 模块</b>（那个 client 被 21 个模块引用、签名锁死）。</p>
 *
 * <p><b>NEVER 让 service 层直接注 {@code AlipayAccountClient}</b>：那等于把 try/catch 与
 * boolean 判读重新散回业务代码，本端口就白建了。</p>
 */
public interface AccountChannelPort {

    /**
     * 把签约成立后的支付通道信息同步给账户域。
     *
     * <p>本方法 <b>NEVER 向外抛异常</b>：异常一律收成 {@link RpcOutcome.Unreachable}，
     * 让调用点用穷尽 {@code switch} 决定 outbox 该记什么状态。</p>
     *
     * @param thirdUserId   支付宝用户标识
     * @param channelUserAccount 渠道侧账号（账户域的 {@code thirdPayId}）
     * @param agreementCode 我方协议号（账户域的 {@code reqContractNo}）
     */
    RpcOutcome updatePaymentChannel(String thirdUserId, String channelUserAccount, String agreementCode);
}

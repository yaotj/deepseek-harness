package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link AccountChannelPort} 的唯一实现：把 {@code boolean} + 异常翻译成三态（ADR-D131）。
 *
 * <p>映射规则（依据见端口 Javadoc 里那条实测事实）：</p>
 * <ul>
 *   <li>{@code true}  → {@link RpcOutcome.Ok}</li>
 *   <li>{@code false} → {@link RpcOutcome.BizRejected}：对端答复了（或答了个空体），重试无意义</li>
 *   <li>抛异常        → {@link RpcOutcome.Unreachable}：没拿到答复，可重试</li>
 * </ul>
 *
 * <p><b>NEVER 把 {@code false} 也归成 {@code Unreachable}</b> —— 那会让「账户域明确拒绝」
 * 这种永不自愈的情况进补偿队列反复重推，把一次性的人工介入拖成无限循环。</p>
 */
@Component
public class AccountChannelRpcAdapter implements AccountChannelPort {

    private static final Logger log = LoggerFactory.getLogger(AccountChannelRpcAdapter.class);

    /** 账户域返 false 时对端并没给业务码，这里给一个本地占位，仅用于日志与工单。 */
    private static final String REJECTED_CODE = "ACCOUNT_CHANNEL_REJECTED";

    private final AlipayAccountClient alipayAccountClient;

    public AccountChannelRpcAdapter(AlipayAccountClient alipayAccountClient) {
        this.alipayAccountClient = alipayAccountClient;
    }

    @Override
    public RpcOutcome updatePaymentChannel(String thirdUserId, String channelUserAccount, String agreementCode) {
        try {
            boolean accepted = alipayAccountClient.updatePaymentChannel(thirdUserId, channelUserAccount, agreementCode);
            if (accepted) {
                return new RpcOutcome.Ok();
            }
            log.error("账户域明确拒绝支付通道同步，重试无意义、MUST 人工核对, thirdUserId={}, agreementCode={}",
                    thirdUserId, agreementCode);
            return new RpcOutcome.BizRejected(REJECTED_CODE, "账户域返回 false 或空响应体");
        } catch (Exception e) {
            log.error("账户域支付通道同步未获答复（可重试）, thirdUserId={}, agreementCode={}",
                    thirdUserId, agreementCode, e);
            return new RpcOutcome.Unreachable(e);
        }
    }
}

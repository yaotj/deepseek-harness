package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class AccountChannelRpcAdapter implements AccountChannelPort {

    private static final Logger log = LoggerFactory.getLogger(AccountChannelRpcAdapter.class);

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

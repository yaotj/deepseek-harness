package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class BlacklistRpcAdapter implements BlacklistPort {

    private static final Logger log = LoggerFactory.getLogger(BlacklistRpcAdapter.class);

    private final BlacklistClient blacklistClient;

    public BlacklistRpcAdapter(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    @Override
    public RpcOutcome addBlackList(String cardId, String thirdUserId, String cardType, String reason) {
        AddBlackListReqDTO request = new AddBlackListReqDTO();
        request.setCardId(cardId);
        request.setThirdUserId(thirdUserId);
        request.setCardType(cardType);
        request.setReason(reason);
        request.setChannelCode("02");
        request.setBlackSource("01");
        request.setBlackCause("01");
        request.setCreateBy("alipay-pay-sign-server");
        try {
            BlackListOperateResult result = blacklistClient.addBlackList(request);
            if (result == null) {
                log.error("加黑名单无响应体（可重试）, cardId={}, thirdUserId={}", cardId, thirdUserId);
                return new RpcOutcome.Unreachable(new IllegalStateException("blacklist-server 响应为空"));
            }
            return RpcOutcome.ofRetCode(result.getRetCode(), result.getRetMsg());
        } catch (Exception e) {
            log.error("加黑名单未获答复（可重试）, cardId={}, thirdUserId={}", cardId, thirdUserId, e);
            return new RpcOutcome.Unreachable(e);
        }
    }
}

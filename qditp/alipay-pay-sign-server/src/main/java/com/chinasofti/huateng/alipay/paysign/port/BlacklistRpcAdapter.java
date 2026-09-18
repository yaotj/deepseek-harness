package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link BlacklistPort} 的唯一实现：DTO 装配 + {@code retCode} → {@link RpcOutcome} 翻译（ADR-D131）。
 *
 * <p>映射规则与 {@link DebitSyncRpcAdapter} 同构：{@code retCode == "0000"} → {@code Ok}；
 * 对端答了别的码 → {@code BizRejected}；响应为 {@code null} 或抛异常 → {@code Unreachable}。</p>
 */
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
        // 本模块的加黑一律是「支付宝渠道 + 系统自动 + 未付费欠费」，三个分类字段在此定死。
        // bizNo 暂不上送：端口签名是四个标量，补订单号要连带改三个调用点与其单测，列为待办。
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

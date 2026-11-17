package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
class SupplementUserLookup {

    private static final Logger log = LoggerFactory.getLogger(SupplementUserLookup.class);

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AlipayAccountClient alipayAccountClient;


    Result query(String cardId) {
        QueryUserInfoResult cardTypeResult;
        try {
            cardTypeResult = accountClient.queryCardTypeByCardId(cardId);
        } catch (Exception e) {
            return new Result(null, new RpcOutcome.Unreachable(e));
        }
        if (cardTypeResult == null
                || !SupplementCodec.RET_SUCCESS.equals(cardTypeResult.getRetCode())
                || !StringUtils.hasText(cardTypeResult.getThirdUserId())) {
            log.warn("IF5A-03 account 域未查到该卡，回落支付宝账户域, cardId={}, retCode={}, retMsg={}",
                    cardId,
                    cardTypeResult == null ? null : cardTypeResult.getRetCode(),
                    cardTypeResult == null ? null : cardTypeResult.getRetMsg());
            return fallbackToAlipay(cardId);
        }

        QueryUserInfoReqDTO userInfoReq = new QueryUserInfoReqDTO();
        userInfoReq.setThirdUserId(cardTypeResult.getThirdUserId());
        userInfoReq.setCardType(cardTypeResult.getCardType());
        userInfoReq.setCardId(cardId);
        QueryUserInfoResult userInfo;
        try {
            userInfo = accountClient.queryUserInfo(userInfoReq);
        } catch (Exception e) {
            return new Result(null, new RpcOutcome.Unreachable(e));
        }
        if (userInfo == null) {
            return new Result(null, new RpcOutcome.BizRejected(null, "查询用户详细信息无响应体"));
        }
        if (!SupplementCodec.RET_SUCCESS.equals(userInfo.getRetCode())
                || !StringUtils.hasText(userInfo.getThirdUserId())) {
            return new Result(null, new RpcOutcome.BizRejected(userInfo.getRetCode(), userInfo.getRetMsg()));
        }
        return new Result(userInfo, new RpcOutcome.Ok());
    }


    private Result fallbackToAlipay(String cardId) {
        AlipayUserInfoDTO alipayUser;
        try {
            alipayUser = alipayAccountClient.selectByCardId(cardId);
        } catch (Exception e) {
            log.warn("IF5A-03 支付宝账户域查询异常, cardId={}", cardId, e);
            return new Result(null, new RpcOutcome.Unreachable(e));
        }
        if (alipayUser == null || !StringUtils.hasText(alipayUser.getThirdUserId())) {
            log.warn("IF5A-03 支付宝账户域也未查到该卡, cardId={}", cardId);
            return new Result(null, new RpcOutcome.BizRejected(null, "未注册用户"));
        }

        QueryUserInfoResult userInfo = new QueryUserInfoResult();
        userInfo.setRetCode(SupplementCodec.RET_SUCCESS);
        userInfo.setThirdUserId(alipayUser.getThirdUserId());
        userInfo.setCardId(cardId);
        userInfo.setCardType(CardTypeMapping.toIssueCardType(alipayUser.getCardType()));
        userInfo.setChannel(SupplementCodec.defaultString(alipayUser.getChannel(), ""));

        log.info("IF5A-03 已按支付宝账户域补齐用户信息, cardId={}, thirdUserId={}, cardType={}",
                cardId, alipayUser.getThirdUserId(), userInfo.getCardType());
        return new Result(userInfo, new RpcOutcome.Ok());
    }

    record Result(QueryUserInfoResult userInfo, RpcOutcome outcome) {
    }
}

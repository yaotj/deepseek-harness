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

/**
 * IF5A-03 票卡更新所需的用户信息查询：account 域两跳 → 支付宝出行账户域回落。
 * 与 IF5A-01 的 {@code CardDataAnalyseHandler.queryUserInfo} 同一判据：
 * 看卡在哪个域里查得到，NEVER 按报文 {@code providerId} 分流。
 */
@Component
class SupplementUserLookup {

    private static final Logger log = LoggerFactory.getLogger(SupplementUserLookup.class);

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    /**
     * 查询用户信息。
     *
     * <p>第一跳 {@code queryCardTypeByCardId} 的失败判据 MUST 与 IF5A-01 逐字一致：
     * 无响应体、{@code retCode} 非 {@code 0000}、{@code thirdUserId} 为空**三者都只说明
     * 「这张卡不在 account 域」**，一律回落支付宝出行账户域。
     * NEVER 把 {@code retCode} 非 {@code 0000} 直接当 {@code BizRejected} 返回 ——
     * 支付宝出行卡在 account 域恒返 {@code 8004 没有账号卡片数据}，那样写等于永远走不到
     * {@link #fallbackToAlipay}，IF5A-01 明明给出了 {@code adviceOpt}，IF5A-03 却必返
     * {@code 8004 未注册用户}（2026-09-18 实测：卡 2607031119542741 走 018 补进站被拒）。
     * 只有真正的网络不可达（抛异常）才是 {@code Unreachable}。
     */
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

    /**
     * account 域查不到该卡时，回落支付宝出行账户域（{@code ALIPAY_USER_INFO}）按 cardId 补齐
     * {@code thirdUserId} / {@code cardType} / {@code channel}，与 IF1A-01 的
     * {@link com.chinasofti.huateng.ticket.gate.GateCardTypeEnricher} 同一判据。
     * NEVER 改回按报文 {@code providerId} 分流。
     */
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

    /** 用户信息查询结果，{@code userInfo} 仅在 {@code outcome} 为 {@code Ok} 时非空。 */
    record Result(QueryUserInfoResult userInfo, RpcOutcome outcome) {
    }
}

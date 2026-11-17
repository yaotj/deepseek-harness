package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class CardUnsettledQuery {

    private static final Logger log = LoggerFactory.getLogger(CardUnsettledQuery.class);

    private static final String DOWNSTREAM_SUCCESS = "0000";

    private final GateTxnPayClient gateTxnPayClient;
    private final AlipayPaySignClient alipayPaySignClient;

    public CardUnsettledQuery(GateTxnPayClient gateTxnPayClient,
                              AlipayPaySignClient alipayPaySignClient) {
        this.gateTxnPayClient = gateTxnPayClient;
        this.alipayPaySignClient = alipayPaySignClient;
    }


    public Boolean gateUnsettled(String cardId) {
        try {
            CardUnsettledQueryReqDTO request = new CardUnsettledQueryReqDTO();
            request.setCardId(cardId);
            CardUnsettledQueryRespDTO result = gateTxnPayClient.hasUnsettledOrderByCard(request);
            if (result == null || !DOWNSTREAM_SUCCESS.equals(result.getResultCode())) {
                log.warn("查询闸机扣费欠费未成功, cardId={}, response={}", cardId, result);
                return null;
            }
            return result.isHasUnsettled();
        } catch (Exception e) {
            log.error("查询闸机扣费欠费异常, cardId={}", cardId, e);
            return null;
        }
    }


    public Boolean alipayUnsettled(String cardId) {
        try {
            CardUnsettledQueryReqDTO request = new CardUnsettledQueryReqDTO();
            request.setCardId(cardId);
            CardUnsettledQueryRespDTO result = alipayPaySignClient.hasUnsettledOrderByCard(request);
            if (result == null || !DOWNSTREAM_SUCCESS.equals(result.getResultCode())) {
                log.warn("查询支付宝出行欠费未成功, cardId={}, response={}", cardId, result);
                return null;
            }
            return result.isHasUnsettled();
        } catch (Exception e) {
            log.error("查询支付宝出行欠费异常, cardId={}", cardId, e);
            return null;
        }
    }
}

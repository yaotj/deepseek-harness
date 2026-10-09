package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 按卡号查两个欠费源，是「这张卡还欠不欠钱」的唯一判定入口。
 *
 * <p>两个方法的返回值是<b>三态</b>：{@code TRUE} 仍有未结清订单、{@code FALSE} 已结清、
 * {@code null} <b>查询未成功执行（事实不明）</b>。<b>NEVER 把 {@code null} 当成 FALSE</b> ——
 * 那等于「查不通就放行」，欠费用户会被自动解黑。
 *
 * <p>抽成组件而不是在两个调用方各写一份私有方法：判定语义（尤其那条三态约定）只能有一处定义，
 * 抄两份时改一处必漏另一处。当前调用方是 {@link BlacklistReleaseInspectService}（只读盘点）
 * 与 {@link BlacklistAutoReleaseService}（自动解除），后者会据此真的改数据。
 *
 * <p>本类方法内有出网 HTTP，<b>NEVER 给它或调用方加 {@code @Transactional}</b>。
 */
@Component
public class CardUnsettledQuery {

    private static final Logger log = LoggerFactory.getLogger(CardUnsettledQuery.class);

    /** 下游成功码。 */
    private static final String DOWNSTREAM_SUCCESS = "0000";

    private final GateTxnPayClient gateTxnPayClient;
    private final AlipayPaySignClient alipayPaySignClient;

    public CardUnsettledQuery(GateTxnPayClient gateTxnPayClient,
                              AlipayPaySignClient alipayPaySignClient) {
        this.gateTxnPayClient = gateTxnPayClient;
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /**
     * 查闸机出站扣费欠费（地铁 APP 渠道的欠费源）。
     *
     * @param cardId 卡ID
     * @return TRUE 仍有欠费 / FALSE 已结清 / null 查询未成功执行
     */
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

    /**
     * 查支付宝出行欠费（支付宝渠道的欠费源）。
     *
     * @param cardId 卡ID
     * @return TRUE 仍有欠费 / FALSE 已结清 / null 查询未成功执行
     */
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

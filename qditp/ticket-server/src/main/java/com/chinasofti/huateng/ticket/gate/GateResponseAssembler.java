package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.utils.SignChannelUtils;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.merchant.MerchantParty;
import com.chinasofti.huateng.ticket.merchant.MerchantPartyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** IF1A-01 应答装配器 —— 把返回给 fep-dev-server / 闸机的全部字段填齐。 */
@Component
class GateResponseAssembler {

    private static final Logger log = LoggerFactory.getLogger(GateResponseAssembler.class);
    private static final String RET_SUCCESS = "0000";
    /** 订单异常类型：正常。 */
    private static final String ORDER_EXP_TYPE_NORMAL = "0";
    /** 订单异常类型：双段计费正常订单_行程超时。 */
    private static final String ORDER_EXP_TYPE_OVERTIME = "5";
    /** 离线码的签约渠道代码（0x17）。 */
    private static final String SIGN_CHANNEL_OFFLINE = "17";

    @Autowired
    private GateDailyTicketCoordinator dailyTicketCoordinator;

    @Autowired
    private MerchantPartyResolver merchantResolver;

    /**
     * 填齐成功应答。
     *
     * @param request 闸机检票请求（已被 {@code GateCardTypeEnricher} 富化过）
     * @param response 待填充的应答
     * @param effectiveStatus 已持久化的票卡状态，来自 {@link GateTicketWriter.WriteResult#status()}
     * @param resolvedCardType {@code CardTypeMapping.toIssueCardType(signChannelCode)} 的结果，
     */
    public void fillSuccessResponse(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response,
                                    QRCodeStatus effectiveStatus, String resolvedCardType) {
        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setTicketStatus(effectiveStatus.getCodeStatus());
        response.setOrderExpType(resolveOrderExpType(request.getTrxType()));
        response.setOfflineFlag(resolveOfflineFlag(request.getSignChannelCode()));
        response.setCompanionFlag(request.getCompanionFlag());
        response.setPayChannelCode(request.getPaymentVendor());
        response.setCountingFlag(dailyTicketCoordinator.resolveCountingFlag(resolvedCardType));
        response.setCountingTimes(dailyTicketCoordinator.resolveCountingTimes(resolvedCardType));
        if (CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            dailyTicketCoordinator.applyDailyTicketFields(request, response);
        }
        resolveMerchantParties(request, response);
        log.info("IF1A-01 返回 ticketResponse 字段, cardId={}, ticketStatus={}, orderExpType={}, offlineFlag={}, companionFlag={}, ticketCode={}, countingTimes={}, countingFlag={}, attributableParty={}, receivingParty={}, payChannelCode={}, signChannelCode={}",
                request.getCardId(), response.getTicketStatus(), response.getOrderExpType(),
                response.getOfflineFlag(), response.getCompanionFlag(), response.getTicketCode(),
                response.getCountingTimes(), response.getCountingFlag(),
                response.getAttributableParty(), response.getReceivingParty(),
                response.getPayChannelCode(), request.getSignChannelCode());
    }

    /** 商户号分账解析： */
    private void resolveMerchantParties(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        try {
            String txnDate = StringUtils.hasText(request.getHandleDateTime())
                    && request.getHandleDateTime().length() >= 8
                    ? request.getHandleDateTime().substring(0, 8) : null;
            boolean isSingleSideOrSupplement = !ORDER_EXP_TYPE_NORMAL.equals(response.getOrderExpType())
                    || TrxTypeCodeEnum.ABNORMAL.getCode().equals(request.getTrxType())
                    || TrxTypeCodeEnum.ENTRY_FAIL.getCode().equals(request.getTrxType());
            if (isSingleSideOrSupplement) {
                applyMerchantParty(response, merchantResolver.oldParty());
                log.info("IF1A-01 商户号解析-单边补站回退, cardId={}, txnDate={}, attributableParty={}, receivingParty={}",
                        request.getCardId(), txnDate, response.getAttributableParty(), response.getReceivingParty());
            } else {
                applyMerchantParty(response, merchantResolver.resolveFor(txnDate));
                log.info("IF1A-01 商户号解析完成, cardId={}, txnDate={}, orderExpType={}, isSingleSide={}, attributableParty={}, receivingParty={}",
                        request.getCardId(), txnDate, response.getOrderExpType(), isSingleSideOrSupplement,
                        response.getAttributableParty(), response.getReceivingParty());
            }
        } catch (Exception e) {
            log.error("IF1A-01 商户号解析异常，降级使用旧商户号, cardId={}", request.getCardId(), e);
            applyMerchantParty(response, merchantResolver.oldParty());
        }
    }

    /** 把一对商户号整体写进应答。 */
    private void applyMerchantParty(NotifyVerifyResultRespDTO response, MerchantParty party) {
        response.setAttributableParty(party.attributableParty());
        response.setReceivingParty(party.receivingParty());
    }

    /** 根据出站交易类型解析订单异常类型。 */
    private String resolveOrderExpType(String trxType) {
        if (TrxTypeCodeEnum.EXIT_OVERTIME.getCode().equals(trxType)) {
            return ORDER_EXP_TYPE_OVERTIME;
        }
        return ORDER_EXP_TYPE_NORMAL;
    }

    /** 根据签约渠道代码判断离线码标识。 */
    private String resolveOfflineFlag(String signChannelCode) {
        String resolved = SignChannelUtils.resolve(signChannelCode);
        return SIGN_CHANNEL_OFFLINE.equals(resolved) ? "Y" : null;
    }
}

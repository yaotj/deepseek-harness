package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Component
class GateFarePaymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GateFarePaymentOrchestrator.class);

    private static final Set<String> BOM_SUPPLEMENT_EXIT_ADVICE_OPTS = AdviceOptEnum.SUPPLEMENT_EXIT_CODES;

    private final GateTxnPayClient gateTxnPayClient;
    private final AlipayIndustryDetailAssembler industryDetailAssembler;
    private final GateTxnPayRequestAssembler payRequestAssembler;

    public GateFarePaymentOrchestrator(GateTxnPayClient gateTxnPayClient,
                                       AlipayIndustryDetailAssembler industryDetailAssembler,
                                       GateTxnPayRequestAssembler payRequestAssembler) {
        this.gateTxnPayClient = gateTxnPayClient;
        this.industryDetailAssembler = industryDetailAssembler;
        this.payRequestAssembler = payRequestAssembler;
    }

    public void settleIfNeeded(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse) {
        if (!shouldCreateOrder(request)) {
            return;
        }
        String adviceOpt = request.getAdviceOpt();
        if (StringUtils.hasText(adviceOpt)) {
            if (BOM_SUPPLEMENT_EXIT_ADVICE_OPTS.contains(adviceOpt)) {
                log.info("IF1A-01 BOM 补站交易落单但不触发扣款（现场已收款）, cardId={}, trxType={}, adviceOpt={}, trxAmount={}",
                        request.getCardId(), request.getTrxType(), adviceOpt, request.getTrxAmount());
            } else {
                log.warn("IF1A-01 出站报文带未知 adviceOpt，按正常出站落单并扣费, cardId={}, trxType={}, adviceOpt={}",
                        request.getCardId(), request.getTrxType(), adviceOpt);
            }
        }
        boolean alipayTransaction = isAlipayTransaction(request);
        log.info("IF1A-01 出站交易调用扣费交易服务, cardId={}, alipay={}", request.getCardId(), alipayTransaction);
        String industryDetail = industryDetailAssembler.assemble(request);
        sendGateTxnPay(request, ticketResponse, industryDetail);
    }

    private boolean isAlipayTransaction(NotifyVerifyResultReqDTO request) {
        return request != null && IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode());
    }

    private boolean shouldCreateOrder(NotifyVerifyResultReqDTO request) {
        return request != null && TrxTypeCodeEnum.isExitTxn(request.getTrxType());
    }
    private void sendGateTxnPay(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse,
                                String industryDetail) {
        try {
            GateTxnPayReqDTO payRequest = payRequestAssembler.assemble(request, ticketResponse, industryDetail);
            log.info("IF1A-01 出站交易调用扣费交易服务, cardId={}, trxType={}", request.getCardId(), request.getTrxType());
            GateTxnPayRespDTO payResponse = gateTxnPayClient.requestGateTxnPay(payRequest);
            log.info("IF1A-01 出站交易调用扣费交易服务, retCode={}", payResponse != null ? payResponse.getRetCode() : "null");
        } catch (Exception e) {
            log.error("IF1A-01 出站交易调用扣费交易服务异常, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
        }
    }
}

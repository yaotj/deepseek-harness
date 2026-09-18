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

/** IF1A-01 出站扣费编排：判断本笔该不该扣 → 按渠道组行业明细 → 调 gate-txn-pay-server 落单。 */
@Component
class GateFarePaymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GateFarePaymentOrchestrator.class);

    /** BOM 补站里属于「出站方向」的 adviceOpt 白名单：{@code 005} 免费更新、{@code 006} 补出站、{@code 020} 无时间窗免费更新。 */
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
    /**
     * 按需发起出站扣费。
     *
     * @param request 闸机检票报文（{@code cardType} / {@code itpUserId} 等已被前序步骤规范化）
     * @param ticketResponse 本笔检票的应答对象，字段已填齐
     */
    public void settleIfNeeded(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse) {
        if (!shouldPay(request)) {
            return;
        }
        boolean alipayTransaction = isAlipayTransaction(request);
        log.info("IF1A-01 出站交易调用扣费交易服务, cardId={}, alipay={}", request.getCardId(), alipayTransaction);
        String industryDetail = alipayTransaction ? industryDetailAssembler.assemble(request) : null;
        sendGateTxnPay(request, ticketResponse, industryDetail);
    }

    /** 判断是否为支付宝交易：issueChannelCode == "07" 表示支付宝渠道。 */
    private boolean isAlipayTransaction(NotifyVerifyResultReqDTO request) {
        return request != null && IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode());
    }
    /** 判断是否需要发起扣费：仅 trxType 为 "02"（正常出站）或 "03"（超时出站）时触发， 且 BOM 补站的 005 / 006 / 020 一律不扣。 */
    private boolean shouldPay(NotifyVerifyResultReqDTO request) {
        if (request == null || !TrxTypeCodeEnum.isExitTxn(request.getTrxType())) {
            return false;
        }
        String adviceOpt = request.getAdviceOpt();
        if (!StringUtils.hasText(adviceOpt)) {
            return true;
        }
        if (BOM_SUPPLEMENT_EXIT_ADVICE_OPTS.contains(adviceOpt)) {
            log.info("IF1A-01 BOM 补站交易不触发扣费, cardId={}, trxType={}, adviceOpt={}",
                    request.getCardId(), request.getTrxType(), adviceOpt);
            return false;
        }
        log.warn("IF1A-01 出站报文带未知 adviceOpt，按正常出站扣费, cardId={}, trxType={}, adviceOpt={}",
                request.getCardId(), request.getTrxType(), adviceOpt);
        return true;
    }
    /** 组装并调 gate-txn-pay-server 入库出站扣费交易记录。 */
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

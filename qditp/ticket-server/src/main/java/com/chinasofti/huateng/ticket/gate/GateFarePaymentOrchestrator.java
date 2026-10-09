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

/** IF1A-01 出站落单编排：判断本笔该不该落单 → 按渠道组行业明细 → 调 gate-txn-pay-server 落单。 */
@Component
class GateFarePaymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GateFarePaymentOrchestrator.class);

    /** BOM 补站里属于「出站方向」的 adviceOpt 白名单：{@code 005} 免费更新、{@code 006} 付费更新、{@code 020} 无时间窗免费更新。 */
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
     * 按需在 gate-txn-pay 落一条出站订单。
     *
     * <p><b>「落单」与「扣款」是两件事，NEVER 再合并成一个判断</b>（用户 2026-09-22 裁决）。
     * 出站方向一律落单 —— 乘车记录列表（IF8A-05）唯一数据源是 {@code GATE_TXN_PAY}，
     * 不落单等于这趟行程在 APP 里根本不存在（BOM 补站曾因此整笔不可见）。
     * 是否发起免密扣款改由 gate-txn-pay 侧按 {@code adviceOpt} 判定：
     * <ul>
     *   <li><b>BOM 补站</b>（{@code 005} / {@code 006} / {@code 020}）：钱是 BOM 现场收的，
     *       落单即 {@code SUCCESS}，**NEVER 由 ITP 再扣一次**（否则乘客重复付费）；</li>
     *   <li><b>闸机真实检票与 APP 自助补站</b>（{@code adviceOpt} 为空，后者走 IF8A-04
     *       {@code requestExcessFare}、由 {@code ExcessFareHandler} 组报文且从不设该字段）：走正常后付费扣款。</li>
     * </ul>
     *
     * @param request 闸机检票报文（{@code cardType} / {@code itpUserId} 等已被前序步骤规范化）
     * @param ticketResponse 本笔检票的应答对象，字段已填齐
     */
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
        String industryDetail = alipayTransaction ? industryDetailAssembler.assemble(request) : null;
        sendGateTxnPay(request, ticketResponse, industryDetail);
    }

    /** 判断是否为支付宝交易：issueChannelCode == "07" 表示支付宝渠道。 */
    private boolean isAlipayTransaction(NotifyVerifyResultReqDTO request) {
        return request != null && IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode());
    }
    /**
     * 判断是否需要落单：仅 trxType 为 {@code 02}（正常出站）或 {@code 03}（超时出站）时落单。
     *
     * <p><b>NEVER 在这里按 {@code adviceOpt} 拦掉 BOM 补站</b> —— 那会让补站行程在乘车记录里彻底消失
     * （2026-09-22 之前就是这样，用户按卡号在 APP 里查不到免费更新那一笔）。扣与不扣的判断已下移到
     * gate-txn-pay 侧，见 {@link #settleIfNeeded} 的说明。
     */
    private boolean shouldCreateOrder(NotifyVerifyResultReqDTO request) {
        return request != null && TrxTypeCodeEnum.isExitTxn(request.getTrxType());
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

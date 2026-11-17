package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class RefundCallbackSettler {

    private static final Logger log = LoggerFactory.getLogger(RefundCallbackSettler.class);

    private static final String CALLBACK_RESULT_SUCCESS = "SUCCESS";
    private static final String CALLBACK_RESULT_FAIL = "FAIL";

    private final RefundLogRepository refundLogRepository;

    RefundCallbackSettler(RefundLogRepository refundLogRepository) {
        this.refundLogRepository = refundLogRepository;
    }

    public enum Outcome {
        SETTLED_SUCCESS,
        SETTLED_FAIL,
        STILL_PROCESSING,
        NOT_MATCHED,
        UNKNOWN_RESULT
    }


    public Outcome settle(String orderNo, String refundOrderNo, String refundResult, String resultMsg) {
        if (CALLBACK_RESULT_SUCCESS.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, RefundLogRepository.REFUND_STATUS_SUCCESS, resultMsg,
                    Outcome.SETTLED_SUCCESS);
        }
        if (CALLBACK_RESULT_FAIL.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, RefundLogRepository.REFUND_STATUS_FAIL, resultMsg,
                    Outcome.SETTLED_FAIL);
        }
        if (RefundLogRepository.REFUND_STATUS_PROCESSING.equalsIgnoreCase(refundResult)) {
            log.info("退款回调仍为处理中，本次不回写明细, orderNo={}, refundOrderNo={}", orderNo, refundOrderNo);
            return Outcome.STILL_PROCESSING;
        }
        log.error("退款回调 refundResult 不在契约取值内，MUST 人工核对, orderNo={}, refundOrderNo={}, refundResult={}",
                orderNo, refundOrderNo, refundResult);
        return Outcome.UNKNOWN_RESULT;
    }

    private Outcome settleTerminal(String orderNo, String refundOrderNo, String refundStatus, String resultMsg,
                                   Outcome settled) {
        int affected = refundLogRepository.settleFromCallback(refundOrderNo, refundStatus, resultMsg);
        if (affected == 0) {
            log.warn("退款回调收口未命中处理中明细，按幂等处理, orderNo={}, refundOrderNo={}, refundStatus={}",
                    orderNo, refundOrderNo, refundStatus);
            return Outcome.NOT_MATCHED;
        }
        if (RefundLogRepository.REFUND_STATUS_SUCCESS.equals(refundStatus)) {
            refundLogRepository.refreshSummary(orderNo, refundOrderNo);
        }
        log.info("退款回调已收口明细, orderNo={}, refundOrderNo={}, refundStatus={}", orderNo, refundOrderNo, refundStatus);
        return settled;
    }
}

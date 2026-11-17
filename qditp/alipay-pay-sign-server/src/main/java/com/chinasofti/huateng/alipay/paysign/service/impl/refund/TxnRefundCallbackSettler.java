package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class TxnRefundCallbackSettler {

    private static final Logger log = LoggerFactory.getLogger(TxnRefundCallbackSettler.class);

    private static final String CALLBACK_RESULT_SUCCESS = "SUCCESS";
    private static final String CALLBACK_RESULT_FAIL = "FAIL";
    private static final String CALLBACK_RESULT_PROCESSING = "PROCESSING";

    private static final String REFUND_STATUS_SUCCESS = "SUCCESS";
    private static final String REFUND_STATUS_FAIL = "FAIL";

    private final AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper;
    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;

    TxnRefundCallbackSettler(AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper,
                             AlipayPayTxnDetailMapper alipayPayTxnDetailMapper) {
        this.alipayRefundTxnDetailMapper = alipayRefundTxnDetailMapper;
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
    }


    public RefundCallbackSettler.Outcome settle(String orderNo, String refundOrderNo, String refundResult,
                                               String resultMsg) {
        if (CALLBACK_RESULT_SUCCESS.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, REFUND_STATUS_SUCCESS, resultMsg,
                    RefundCallbackSettler.Outcome.SETTLED_SUCCESS);
        }
        if (CALLBACK_RESULT_FAIL.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, REFUND_STATUS_FAIL, resultMsg,
                    RefundCallbackSettler.Outcome.SETTLED_FAIL);
        }
        if (CALLBACK_RESULT_PROCESSING.equalsIgnoreCase(refundResult)) {
            log.info("退款回调仍为处理中，本次不回写新表明细, orderNo={}, refundOrderNo={}", orderNo, refundOrderNo);
            return RefundCallbackSettler.Outcome.STILL_PROCESSING;
        }
        log.error("退款回调 refundResult 不在契约取值内，MUST 人工核对, orderNo={}, refundOrderNo={}, refundResult={}",
                orderNo, refundOrderNo, refundResult);
        return RefundCallbackSettler.Outcome.UNKNOWN_RESULT;
    }


    private RefundCallbackSettler.Outcome settleTerminal(String orderNo, String refundOrderNo, String refundStatus,
                                                        String resultMsg, RefundCallbackSettler.Outcome settled) {
        int affected = alipayRefundTxnDetailMapper.settleFromCallback(refundOrderNo, refundStatus, resultMsg);
        if (affected == 0) {
            log.warn("退款回调未命中新表处理中明细，按幂等处理, orderNo={}, refundOrderNo={}, refundStatus={}",
                    orderNo, refundOrderNo, refundStatus);
            return RefundCallbackSettler.Outcome.NOT_MATCHED;
        }
        if (REFUND_STATUS_SUCCESS.equals(refundStatus)) {
            int summary = alipayPayTxnDetailMapper.updateRefundSummary(orderNo);
            if (summary == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 ALIPAY_PAY_TXN_DETAIL 与 ALIPAY_REFUND_TXN_DETAIL, orderNo={}, refundOrderNo={}",
                        orderNo, refundOrderNo);
            }
        }
        log.info("退款回调已收口新表明细, orderNo={}, refundOrderNo={}, refundStatus={}",
                orderNo, refundOrderNo, refundStatus);
        return settled;
    }
}

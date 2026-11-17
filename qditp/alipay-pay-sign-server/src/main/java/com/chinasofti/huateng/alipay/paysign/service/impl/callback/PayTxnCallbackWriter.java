package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
class PayTxnCallbackWriter {

    private static final Logger log = LoggerFactory.getLogger(PayTxnCallbackWriter.class);


    private static final int TRANS_TIME_LENGTH = 14;

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;

    PayTxnCallbackWriter(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
    }


    void applyCallback(String orderNo, String payStatus, String channelVoucherId, String transTime) {
        String normalizedTransTime = normalizeTransTime(transTime);
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setChannelOrderNo(channelVoucherId);
        update.setTransTime(normalizedTransTime);
        try {
            int affected = alipayPayTxnDetailMapper.updatePayCallback(update);
            if (affected == 0) {
                log.warn("支付回调回写支付明细影响 0 行（本单已是终态或本表无此明细行），不补建、不报错, orderNo={}, payStatus={}",
                        orderNo, payStatus);
                return;
            }
            log.info("支付回调已回写支付明细, orderNo={}, payStatus={}, channelOrderNo={}, transTime={}, rawTransTime={}",
                    orderNo, payStatus, channelVoucherId, normalizedTransTime, transTime);
        } catch (RuntimeException e) {
            log.error("支付回调回写支付明细异常，已忽略以免引来支付中心重推，MUST 人工核对本单支付状态, orderNo={}, payStatus={}",
                    orderNo, payStatus, e);
        }
    }


    private String normalizeTransTime(String transTime) {
        if (!StringUtils.hasText(transTime)) {
            return null;
        }
        String digits = transTime.replaceAll("\\D", "");
        if (digits.length() >= TRANS_TIME_LENGTH) {
            return digits.substring(0, TRANS_TIME_LENGTH);
        }
        log.warn("支付回调的 transTime 无法归一成 {} 位，原样入库, transTime={}", TRANS_TIME_LENGTH, transTime);
        return transTime;
    }
}

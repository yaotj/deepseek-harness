package com.chinasofti.huateng.alipay.paysign.service.impl.pay;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Component
class PayTxnRepository {

    private static final Logger log = LoggerFactory.getLogger(PayTxnRepository.class);

    private static final String STATUS_INIT = "INIT";
    static final String STATUS_PROCESSING = "PROCESSING";
    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAIL = "FAIL";

    private static final String REFUND_STATUS_NONE = "NONE";

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;

    PayTxnRepository(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
    }

    AlipayPayTxnDetail findByOrderNo(String orderNo) {
        return alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
    }


    static boolean isSettled(AlipayPayTxnDetail row) {
        return row != null && STATUS_SUCCESS.equals(row.getPayStatus());
    }


    void openAttempt(PayCommand command, AlipaySignInfo signInfo, String txnDate, AlipayPayTxnDetail existing) {
        if (existing == null) {
            insert(command, signInfo, txnDate);
        }
        alipayPayTxnDetailMapper.markRequesting(command.orderNo());
    }


    void writeRequestResult(String orderNo, String payStatus, String payCenterOrderNo, String channelOrderNo) {
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setPayCenterOrderNo(payCenterOrderNo);
        update.setChannelOrderNo(channelOrderNo);
        int affected = alipayPayTxnDetailMapper.updateRequestResult(update);
        if (affected == 0) {
            log.error("回写支付申请结果影响 0 行，明细行可能不存在、MUST 人工核对, orderNo={}, payStatus={}", orderNo, payStatus);
        }
    }


    private void insert(PayCommand command, AlipaySignInfo signInfo, String txnDate) {
        AlipayPayTxnDetail record = new AlipayPayTxnDetail();
        record.setOrderNo(command.orderNo());
        record.setTxnDate(txnDate);
        record.setPayStatus(STATUS_INIT);
        record.setAmount(command.amount());
        record.setRefundStatus(REFUND_STATUS_NONE);
        record.setRefundAmount(0);
        record.setRequestSignSeq(signInfo.getAgreementCode());
        record.setChannelAgreementNo(signInfo.getChannelAgreementCode());
        record.setRequestCount(0);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            alipayPayTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.info("支付明细已被并发请求落好，按幂等继续, orderNo={}", command.orderNo());
        }
    }

    private boolean isIntegrityViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("DuplicateKey") || name.contains("IntegrityConstraintViolation")) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}

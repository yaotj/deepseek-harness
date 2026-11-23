package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundLogMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;


@Component
class RefundLogRepository {

    private static final Logger log = LoggerFactory.getLogger(RefundLogRepository.class);

    static final String REFUND_STATUS_PROCESSING = "PROCESSING";
    static final String REFUND_STATUS_SUCCESS = "SUCCESS";
    static final String REFUND_STATUS_FAIL = "FAIL";
    static final String RESULT_CODE_INIT = "INIT";

    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";

    private final AlipayRefundLogMapper alipayRefundLogMapper;
    private final AlipayPayLogMapper alipayPayLogMapper;

    RefundLogRepository(AlipayRefundLogMapper alipayRefundLogMapper, AlipayPayLogMapper alipayPayLogMapper) {
        this.alipayRefundLogMapper = alipayRefundLogMapper;
        this.alipayPayLogMapper = alipayPayLogMapper;
    }

    int countProcessing(String orderNo) {
        return alipayRefundLogMapper.countByOrderNoAndStatus(orderNo, REFUND_STATUS_PROCESSING);
    }


    String openRefund(RefundCommand command, AlipayPayLog payLog, String channelAgreementNo,
                      String refundAmount, String refundOrderNo, String requestBody) {
        AlipayRefundLog refundLog = new AlipayRefundLog();
        refundLog.setRefundSeq(UUID.randomUUID().toString().replaceAll("-", ""));
        refundLog.setThirdUserId(payLog.getThirdUserId());
        refundLog.setCardId(payLog.getCardId());
        refundLog.setOrderNo(command.orderNo());
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundStatus(REFUND_STATUS_PROCESSING);
        refundLog.setCardIssueCode(CARD_ISSUE_CODE_ALIPAY);
        refundLog.setChannelAgreementNo(channelAgreementNo);
        refundLog.setRefundOrderNo(refundOrderNo);
        refundLog.setRequestBody(requestBody);
        refundLog.setResponseBody("");
        refundLog.setResultCode(RESULT_CODE_INIT);
        refundLog.setResultMsg("退款处理中");
        refundLog.setDeleteFlag("0");
        refundLog.setVersion("1");
        LocalDateTime now = LocalDateTime.now();
        refundLog.setCreateTime(now);
        refundLog.setUpdateTime(now);
        try {
            alipayRefundLogMapper.insert(refundLog);
        } catch (RuntimeException e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            // 唯一索引冲突 = 同 ORDER_NO 已有在途退款（UK_ARL_ACTIVE_ORDER），或同一随机单号重复。
            // 必须 fail-closed：绝不出网（出网即第二笔真实退款）。回查上一笔单号供人工对账。
            AlipayRefundLog active = alipayRefundLogMapper.selectProcessingByOrderNo(command.orderNo());
            String activeRefundOrderNo = active != null ? active.getRefundOrderNo() : "未查到在途单（需人工核对）";
            log.error("退款申请并发冲突：同订单已有在途退款，本次拒绝且未出网, orderNo={}, 本笔单号={}, 在途单号={}",
                    command.orderNo(), refundOrderNo, activeRefundOrderNo);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(),
                    "该订单已有处理中的退款，请先确认上一笔结果（退款单号：" + activeRefundOrderNo + "）");
        }
        return refundLog.getRefundSeq();
    }


    void writeResult(String refundSeq, String refundStatus, String resultCode, String resultMsg, String rawBody) {
        alipayRefundLogMapper.updateRefundStatus(refundSeq, refundStatus, resultCode, resultMsg,
                rawBody, LocalDateTime.now());
    }


    int settleFromCallback(String refundOrderNo, String refundStatus, String resultMsg) {
        return alipayRefundLogMapper.settleFromCallback(refundOrderNo, refundStatus, resultMsg, LocalDateTime.now());
    }


    java.util.List<AlipayRefundLog> scanCompensable(int scanDays, int staleMinutes, int limit) {
        return alipayRefundLogMapper.selectCompensableRefundQuery(scanDays, staleMinutes, limit);
    }


    void refreshSummary(String orderNo, String refundOrderNo) {
        int affected = alipayPayLogMapper.updateRefundSummary(orderNo);
        if (affected == 0) {
            log.error("退款汇总回写未命中原支付订单，MUST 人工核对 ALIPAY_PAY_LOG 与 ALIPAY_REFUND_LOG, orderNo={}, refundOrderNo={}",
                    orderNo, refundOrderNo);
        }
    }


    static boolean isIntegrityViolation(Throwable throwable) {
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

    AlipayPayLog findPayLog(String orderNo) {
        return alipayPayLogMapper.selectByOrderNo(orderNo);
    }
}

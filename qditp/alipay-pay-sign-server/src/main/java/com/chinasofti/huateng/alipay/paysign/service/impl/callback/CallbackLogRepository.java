package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;


@Component
class CallbackLogRepository {

    private static final Logger log = LoggerFactory.getLogger(CallbackLogRepository.class);

    static final String CALLBACK_TYPE_PAY = "PAY";
    private static final String CALLBACK_TYPE_REFUND = "REFUND";

    static final String HANDLE_STATUS_PROCESSING = "PROCESSING";
    static final String HANDLE_STATUS_SUCCESS = "SUCCESS";
    static final String HANDLE_STATUS_FAIL = "FAIL";
    static final String HANDLE_STATUS_MANUAL = "MANUAL";

    private static final int HANDLE_MSG_MAX = 500;

    private final AlipayPayCallbackLogMapper alipayPayCallbackLogMapper;

    CallbackLogRepository(AlipayPayCallbackLogMapper alipayPayCallbackLogMapper) {
        this.alipayPayCallbackLogMapper = alipayPayCallbackLogMapper;
    }


    String recordPayCallback(AlipayTripPayNotifyReqDTO request, String handleStatus, String handleMsg) {
        String callbackSeq = newCallbackSeq();
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(callbackSeq);
            callbackLog.setOrderNo(request.getOrderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_PAY);
            callbackLog.setTransStatus(request.getTransStatus());
            callbackLog.setChannelVoucherId(request.getChannelVoucherId());
            callbackLog.setTransAmount(request.getTransAmount());
            callbackLog.setTransTime(request.getTransTime());
            callbackLog.setRawBody(JSON.toJSONString(request));
            callbackLog.setHandleStatus(handleStatus);
            callbackLog.setHandleMsg(truncate(handleMsg));
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setUpdateTime(LocalDateTime.now());
            alipayPayCallbackLogMapper.insert(callbackLog);
            return callbackSeq;
        } catch (Exception e) {
            log.error("支付宝支付回调凭据落库失败，本次处理继续, orderNo={}", request.getOrderNo(), e);
            return null;
        }
    }


    String recordRefundCallback(AlipayTripRefundNotifyReqDTO request, RefundNotifyCommand.Accepted command,
                                String handleStatus, String handleMsg) {
        String callbackSeq = newCallbackSeq();
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(callbackSeq);
            callbackLog.setOrderNo(command.orderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_REFUND);
            callbackLog.setRefundOrderNo(command.refundOrderNo());
            callbackLog.setRefundAmount(command.refundAmount());
            callbackLog.setRefundStatus(command.refundResult());
            callbackLog.setRawBody(JSON.toJSONString(request));
            callbackLog.setHandleStatus(handleStatus);
            callbackLog.setHandleMsg(truncate(handleMsg));
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setUpdateTime(LocalDateTime.now());
            alipayPayCallbackLogMapper.insert(callbackLog);
            return callbackSeq;
        } catch (Exception e) {
            log.error("支付宝退款回调凭据落库失败，本次处理继续, orderNo={}", command.orderNo(), e);
            return null;
        }
    }



    int countPayPush(String orderNo) {
        try {
            return alipayPayCallbackLogMapper.countByOrderNo(orderNo, CALLBACK_TYPE_PAY);
        } catch (Exception e) {
            log.error("统计支付回调推送次数失败，本次不启用硬限次, orderNo={}", orderNo, e);
            return 0;
        }
    }

    void updateHandleResult(String callbackSeq, String handleStatus, String handleMsg) {
        if (callbackSeq == null) {
            return;
        }
        try {
            alipayPayCallbackLogMapper.updateHandleResult(callbackSeq, handleStatus, truncate(handleMsg));
        } catch (Exception e) {
            log.error("回写支付回调处理结果失败, callbackSeq={}, handleStatus={}", callbackSeq, handleStatus, e);
        }
    }

    void markPayManual(String orderNo, String reason) {
        try {
            alipayPayCallbackLogMapper.markManualByOrderNo(orderNo, CALLBACK_TYPE_PAY, truncate(reason));
        } catch (Exception e) {
            log.error("标记支付回调需人工处理失败, orderNo={}", orderNo, e);
        }
    }

    private String newCallbackSeq() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= HANDLE_MSG_MAX ? value : value.substring(0, HANDLE_MSG_MAX);
    }
}

package com.chinasofti.huateng.paysign.audit;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
public class PaySignAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(PaySignAuditLogger.class);


    private static final String OP_RECEIVE_SIGN_RESULT = "RECEIVE_SIGN_RESULT";

    private static final String OP_RECEIVE_TERMINATION_RESULT = "RECEIVE_TERMINATION_RESULT";


    private static final String NOTIFY_STATUS_PENDING = "PENDING";

    private static final int RESULT_CODE_MAX = 64;

    private static final int RESULT_MSG_MAX = 1024;

    private static final String[] CODE_KEYS = {"retCode", "resultCode", "code"};

    private static final String[] MSG_KEYS = {"retMsg", "resultMsg", "msg"};

    private final PaySignRequestMapper paySignRequestMapper;

    public PaySignAuditLogger(PaySignRequestMapper paySignRequestMapper) {
        this.paySignRequestMapper = paySignRequestMapper;
    }

    public void write(String operationType, String thirdUserId, String requestSignSeq, String paymentVendor,
                      String signChannel, Object request, Object response) {
        write(operationType, thirdUserId, requestSignSeq, paymentVendor, signChannel, request, response, null);
    }


    public void write(String operationType, String thirdUserId, String requestSignSeq, String paymentVendor,
                      String signChannel, Object request, Object response, String signStatus) {
        if (!StringUtils.hasText(requestSignSeq)) {
            return;
        }
        try {
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(requestSignSeq);
            logRecord.setThirdUserId(thirdUserId);
            logRecord.setPaymentVendor(paymentVendor);
            logRecord.setSignChannel(signChannel);
            logRecord.setDisplayAccount(extractDisplayAccount(request));
            logRecord.setOperationType(convertOperationType(operationType));
            String responseBody = response == null ? null : JSON.toJSONString(response);
            logRecord.setRequestBody(request == null ? null : JSON.toJSONString(request));
            logRecord.setResponseBody(responseBody);
            logRecord.setResultCode(pick(responseBody, CODE_KEYS, RESULT_CODE_MAX));
            logRecord.setResultMsg(pick(responseBody, MSG_KEYS, RESULT_MSG_MAX));
            logRecord.setSignStatus(signStatus);
            logRecord.setCreateTms(LocalDateTime.now());
            paySignRequestMapper.insert(logRecord);
        } catch (Exception e) {
            log.error("记录流水日志异常, requestSignSeq={}, 不影响主事务", requestSignSeq, e);
        }
    }


    public void writeSignResultNotifyPending(ReceiveSignResultReqDTO request, String signChannel, String signStatus) {
        PaySignRequest carrier = newNotifyPendingCarrier(OP_RECEIVE_SIGN_RESULT, request.getRequestSignSeq(),
                request.getThirdUserId(), request.getPaymentVendor(), signChannel, signStatus);
        carrier.setPayAccountId(request.getPayUserId());
        carrier.setPayAgreementNo(request.getPayAgreementNo());
        paySignRequestMapper.insert(carrier);
    }


    public void writeTerminationResultNotifyPending(ReceiveTerminationResultReqDTO request, String signChannel,
                                                    String signStatus) {
        PaySignRequest carrier = newNotifyPendingCarrier(OP_RECEIVE_TERMINATION_RESULT, request.getRequestSignSeq(),
                request.getThirdUserId(), request.getPaymentVendor(), signChannel, signStatus);
        carrier.setCardId(request.getCardId());
        carrier.setCardType(request.getCardType());
        carrier.setTerminationTime(request.getDismissalTime());
        paySignRequestMapper.insert(carrier);
    }

    private PaySignRequest newNotifyPendingCarrier(String operationType, String requestSignSeq, String thirdUserId,
                                                   String paymentVendor, String signChannel, String signStatus) {
        PaySignRequest carrier = new PaySignRequest();
        carrier.setRequestSignSeq(requestSignSeq);
        carrier.setThirdUserId(thirdUserId);
        carrier.setPaymentVendor(paymentVendor);
        carrier.setSignChannel(signChannel);
        carrier.setOperationType(operationType);
        carrier.setSignStatus(signStatus);
        carrier.setCreateTms(LocalDateTime.now());
        carrier.setNotifyStatus(NOTIFY_STATUS_PENDING);
        carrier.setNotifyRetryCount(0);
        return carrier;
    }

    private String pick(String responseBody, String[] keys, int maxLength) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        JSONObject json;
        try {
            json = JSON.parseObject(responseBody);
        } catch (RuntimeException e) {
            return null;
        }
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.hasText(value)) {
                return value.length() > maxLength ? value.substring(0, maxLength) : value;
            }
        }
        return null;
    }

    private String convertOperationType(String operationType) {
        if (!StringUtils.hasText(operationType)) {
            return "SIGN";
        }
        String upper = operationType.toUpperCase();
        if (upper.contains("TERMINATION") || upper.contains("UNSIGN")) {
            return "UNSIGN";
        }
        return "SIGN";
    }

    private String extractDisplayAccount(Object request) {
        if (request == null) {
            return null;
        }
        if (request instanceof RequestSignInfoReqDTO dto) {
            return dto.getDisplayAccount();
        }
        if (request instanceof ReceiveSignResultReqDTO dto) {
            return dto.getDisplayAccount();
        }
        return null;
    }
}

package com.chinasofti.huateng.paysign.audit;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 支付签约域接口流水的**唯一**写入点：每次调用往 {@code APP_PAY_SIGN_REQUEST} INSERT 一行快照。 */
@Component
public class PaySignAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(PaySignAuditLogger.class);

    /** RESULT_CODE 列宽（{@code pay-sign-schema.sql} 里是 {@code VARCHAR2(64 CHAR)}）。 */
    private static final int RESULT_CODE_MAX = 64;

    /** RESULT_MSG 列宽（{@code VARCHAR2(1024 CHAR)}）。 */
    private static final int RESULT_MSG_MAX = 1024;

    /** 结果码的候选键名，按优先级排列。 */
    private static final String[] CODE_KEYS = {"retCode", "resultCode", "code"};

    /** 结果文案的候选键名，与 {@link #CODE_KEYS} 一一对应、顺序 MUST 保持一致。 */
    private static final String[] MSG_KEYS = {"retMsg", "resultMsg", "msg"};

    private final PaySignRequestMapper paySignRequestMapper;

    public PaySignAuditLogger(PaySignRequestMapper paySignRequestMapper) {
        this.paySignRequestMapper = paySignRequestMapper;
    }

    /** 记录接口流水，不带签约状态。 */
    public void write(String operationType, String thirdUserId, String requestSignSeq, String paymentVendor,
                      String signChannel, Object request, Object response) {
        write(operationType, thirdUserId, requestSignSeq, paymentVendor, signChannel, request, response, null);
    }

    /**
     * 记录接口流水，并落 {@code SIGN_STATUS}。
     *
     * @param signStatus 取值来自 {@code SignStatus}，<b>NEVER 传解约流程的状态</b> ——
     */
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

    /** 从**已序列化的应答 JSON** 里按候选键名取第一个有值的字段，并按列宽截断。 */
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

    /** 将内部细分操作归并成流水表设计中的 SIGN / UNSIGN。 */
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

    /** 从请求对象中提取 {@code displayAccount}。 */
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

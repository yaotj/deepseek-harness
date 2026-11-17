package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;

import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public final class PayTxnRules {


    private PayTxnRules() {
    }

    public static boolean validatePaySignInfo(RequestPayReqDTO request, RequestPayResult response) {
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "paymentVendor不能为空");
            return false;
        }
        return switch (PaymentChannels.classify(request.getPaymentVendor())) {
            case PaymentChannel.Wallet ignored -> {
                if (!StringUtils.hasText(request.getPayUserId())) {
                    fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "钱包支付账户标识不能为空");
                    yield false;
                }
                yield true;
            }
            case PaymentChannel.Contracted ignored -> {
                if (!StringUtils.hasText(request.getRequestSignSeq())) {
                    fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "requestSignSeq不能为空");
                    yield false;
                }
                yield true;
            }
        };
    }

    public static String resolveDebitRequestResult(String payStatus) {
        if ("PROCESSING".equals(payStatus)) return "PROCESSING";
        if ("SUCCESS".equals(payStatus)) return "SUCCESS";
        return "FAIL";
    }

    public static PayCallbackLog buildPayCallbackLog(ReceivePayResultReqDTO request, String rawBody) {
        PayCallbackLog logRecord = new PayCallbackLog();
        logRecord.setOrderNo(request.getOrderNo());
        logRecord.setCallbackType("PAY");
        logRecord.setCallbackStatus(request.getStatus());
        logRecord.setMerchantOrderNo(request.getMerchantOrderNo());
        logRecord.setChannelOrderNo(request.getChannelOrderNo());
        logRecord.setPayTime(request.getPayTime());
        logRecord.setTotalAmount(request.getTotalAmount());
        logRecord.setCashAmount(request.getCashAmount());
        logRecord.setCouponAmount(request.getCouponAmount());
        logRecord.setDiscountInfo(request.getDiscountInfo());
        logRecord.setPayUserId(request.getPayUserId());
        logRecord.setPaymentVendor(request.getPaymentVendor());
        logRecord.setTxnDate(resolveTxnDate());
        logRecord.setRawBody(rawBody);
        logRecord.setHandleStatus("SUCCESS");
        logRecord.setCreateTime(LocalDateTime.now());
        return logRecord;
    }
}

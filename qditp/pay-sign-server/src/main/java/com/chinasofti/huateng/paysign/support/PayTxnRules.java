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

/** 免密扣款的**业务规则与本地实体装配**（2026-09-16 由 {@code PaymentDomainServiceImpl} 逐字搬出，ADR-D98）。 */
public final class PayTxnRules {

    /** 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108） */

    private PayTxnRules() {
    }

    /** 校验过闸扣费发起支付所需的签约信息是否已透传。 */
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

    /** 把落库的 {@code PAY_STATUS} 归一成回给闸机侧的扣费请求结果。 */
    public static String resolveDebitRequestResult(String payStatus) {
        if ("PROCESSING".equals(payStatus)) return "PROCESSING";
        if ("SUCCESS".equals(payStatus)) return "SUCCESS";
        return "FAIL";
    }

    /** 装配 {@code PAY_CALLBACK_LOG} 一行（回调留证据用，落库由调用方负责）。 */
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
        logRecord.setPayUserId(request.getPayUserId());
        logRecord.setPaymentVendor(request.getPaymentVendor());
        logRecord.setTxnDate(resolveTxnDate());
        logRecord.setRawBody(rawBody);
        logRecord.setHandleStatus("SUCCESS");
        logRecord.setCreateTime(LocalDateTime.now());
        return logRecord;
    }
}

package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;

import java.text.SimpleDateFormat;
import java.util.Date;


public final class DailyTicketRefundMessages {

    private DailyTicketRefundMessages() {
    }


    public static String buildRefundOrderNo(String orderNo) {
        String suffix = orderNo;
        if (suffix != null && suffix.length() > 8) {
            suffix = suffix.substring(suffix.length() - 8);
        }
        return "RF" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date())
                + (suffix == null ? "" : suffix);
    }


    public static DailyTicketRefundResult buildExistingRefundResult(DailyTicketRefundResult result,
                                                                    DailyTicketRefund refund) {
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundDate(refund.getRefundDate() == null ? null
                : new SimpleDateFormat("yyyyMMddHHmmss").format(refund.getRefundDate()));
        if ("REFUNDED".equals(refund.getRefundStatus())) {
            result.setRefundResult("SUCCESS");
            result.setRefundResultDesc("退款已完成");
        } else if ("FAILED".equals(refund.getRefundStatus())) {
            result.setRefundResult("FAILED");
            result.setRefundResultDesc("退款失败，可发起退款重试");
        } else {
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已申请，请勿重复提交");
        }
        return DailyTicketOrderSupport.success(result);
    }


    public static boolean isDuplicateRefund(Throwable e) {
        Throwable cursor = e;
        int depth = 0;
        while (cursor != null && depth < 16) {
            if (cursor instanceof org.springframework.dao.DuplicateKeyException
                    || cursor instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
            cursor = cursor.getCause();
            depth++;
        }
        return false;
    }


    public static boolean isGatewaySuccess(DailyTicketPayGatewayResponse response) {
        return response != null && (Boolean.TRUE.equals(response.getSuccess())
                || Integer.valueOf(0).equals(response.getCode())
                || Integer.valueOf(200).equals(response.getCode()));
    }


    public static boolean isGatewayExplicitFailure(DailyTicketPayGatewayResponse response) {
        if (response == null) {
            return false;
        }
        if (Boolean.FALSE.equals(response.getSuccess())) {
            return true;
        }
        return response.getCode() != null && !Integer.valueOf(0).equals(response.getCode())
                && !Integer.valueOf(200).equals(response.getCode());
    }
}

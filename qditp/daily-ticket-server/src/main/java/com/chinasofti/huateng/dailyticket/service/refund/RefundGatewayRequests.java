package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;


@Component
public class RefundGatewayRequests {

    private final DailyTicketPayProperties payProperties;

    public RefundGatewayRequests(DailyTicketPayProperties payProperties) {
        this.payProperties = payProperties;
    }

    public Map<String, Object> buildDailyTicketRefund(DailyTicketOrder order, DailyTicketRefund refund) {
        Map<String, Object> refundRequest = new LinkedHashMap<>();
        refundRequest.put("refundOrderNo", refund.getRefundOrderNo());
        refundRequest.put("merchantOrderNo", order.getOrderNo());
        refundRequest.put("orderNo", order.getPaymentOrderNo());
        refundRequest.put("refundAmount", refund.getRefundAmount());
        refundRequest.put("refundReason", "日票退款");
        DailyTicketOrderSupport.putIfText(refundRequest, "notifyUrl", payProperties.getRefundNotifyUrl());
        return refundRequest;
    }

    public Map<String, Object> buildTravelRefund(TravelTicketOrder parent, DailyTicketRefund refund) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("refundOrderNo", refund.getRefundOrderNo());
        request.put("merchantOrderNo", parent.getOrderNo());
        request.put("orderNo", parent.getPaymentOrderNo());
        request.put("refundAmount", refund.getRefundAmount());
        request.put("refundReason", refund.getRefundReason());
        DailyTicketOrderSupport.putIfText(request, "notifyUrl", payProperties.getRefundNotifyUrl());
        return request;
    }

    public Map<String, Object> buildTravelSubRefund(TravelTicketOrder parent, DailyTicketRefund refund) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("refundOrderNo", refund.getRefundOrderNo());
        request.put("merchantOrderNo", refund.getOrderNo());
        request.put("orderNo", parent.getPaymentOrderNo());
        request.put("refundAmount", refund.getRefundAmount());
        request.put("refundReason", refund.getRefundReason());
        DailyTicketOrderSupport.putIfText(request, "notifyUrl", payProperties.getRefundNotifyUrl());
        return request;
    }


    public Map<String, Object> buildRefundQuery(DailyTicketRefund refund) {
        Map<String, Object> queryRequest = new LinkedHashMap<>();
        queryRequest.put("refundOrderNo", refund.getPlatformRefundNo());
        queryRequest.put("merchantRefundNo", refund.getRefundOrderNo());
        return queryRequest;
    }
}

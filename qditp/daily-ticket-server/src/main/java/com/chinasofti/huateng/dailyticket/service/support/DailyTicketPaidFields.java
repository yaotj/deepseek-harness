package com.chinasofti.huateng.dailyticket.service.support;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;

import java.util.Date;


public final class DailyTicketPaidFields {

    private DailyTicketPaidFields() {
    }

    public static void applyPaid(DailyTicketOrder order, String tradeNo, String paymentOrderNo,
                                 Integer payAmount, Date payDate) {
        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setTradeNo(tradeNo);
        order.setPaymentOrderNo(paymentOrderNo);
        order.setPayAmount(payAmount == null ? order.getTicketPrice() : payAmount);
        order.setPayDate(payDate);
    }

    public static void applyPaid(TravelTicketOrder order, String tradeNo, String paymentOrderNo,
                                 Integer payAmount, Date payDate) {
        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setTradeNo(tradeNo);
        order.setPaymentOrderNo(paymentOrderNo);
        order.setPayAmount(payAmount == null ? order.getTotalAmount() : payAmount);
        order.setPayDate(payDate);
    }
}

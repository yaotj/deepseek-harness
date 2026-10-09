package com.chinasofti.huateng.dailyticket.service.support;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;

import java.util.Date;

/**
 * 「把一张单标成已付」需要写哪些列——列清单在此处唯一定义。
 *
 * <p>这组列有两个语义入口：<b>下单即已付</b>（海之巴士 / 免费票，订单出生就是已付，没有支付步骤）
 * 走 INSERT 初值；<b>支付回调 / 支付查询</b>走状态机 UPDATE（{@code updatePayResultIfPaying}）。
 * 两个入口互不调用，各写一份列清单就会各自漂移。
 *
 * <p><b>新增支付终态列 MUST 只改这里。</b>
 *
 * <p>本类只承载重叠子集：{@code PAY_CHANNEL_CODE} 由调用方自己写（下单即已付要写、
 * 支付回调不写），{@code CASH_AMOUNT} / {@code COUPON_AMOUNT} / {@code UPDATE_TIME}
 * 同理只属支付回调那一支，<b>NEVER 吞进来</b>。
 */
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

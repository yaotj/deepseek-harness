package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 送支付中心的**退款类出向报文**唯一组装处：日票退款 / 旅游票整单退款 / 旅游票子单退款 / 退款结果查询。
 *
 * <p>这四份报文原先散在 god class 与各退款服务里，而**每一份都被 2~3 个入口共用**
 * （发起 / 重试 / 重提交都送同一份退款报文），继续散着就会出现逐字副本 ——
 * 而支付中心的字段名是**实测得来的硬约束**（见 AGENTS.md §8 那条 `merchantRefundNo` 教训），
 * 副本之间一旦漂移，表现是「退款单永久空转、端点每轮返 0000」，既不报警也不自愈。
 *
 * <p>三份退款报文的差异**只在两个键**，改之前 MUST 想清楚：
 * <ul>
 *   <li>{@code merchantOrderNo} = 我方业务单号：日票取订单号、旅游票整单取主单号、旅游票子单取**子单号**；</li>
 *   <li>{@code orderNo} = 支付中心那笔**原支付单号**，旅游票两种都取主单的 {@code PAYMENT_ORDER_NO}
 *       （钱是按主单那一笔收的）。</li>
 * </ul>
 *
 * <p>退款查询**两个键都送**：{@code refundOrderNo} 送支付平台退款单号、{@code merchantRefundNo}
 * 送我方退款流水号。**NEVER 只送其中一个** —— 实测只送 `refundOrderNo` 时网关返
 * `9999「退款流水号或商户退款流水号必填」`。
 *
 * <p>{@code notifyUrl} 一律走 {@code putIfText}：配置为空时**不送该键**而不是送空串。
 */
@Component
public class RefundGatewayRequests {

    private final DailyTicketPayProperties payProperties;

    public RefundGatewayRequests(DailyTicketPayProperties payProperties) {
        this.payProperties = payProperties;
    }

    /** 日票（含多日计次票）退款报文，发起 / 重试 / 重提交三处共用。 */
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

    /** 旅游票**整单**退款报文（{@code REFUND_SCOPE=TRAVEL_FULL}），发起与重试共用。 */
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

    /** 旅游票**子单**退款报文（{@code REFUND_SCOPE=TRAVEL_SUB}），发起与重试共用。 */
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

    /**
     * 退款结果查询报文。**两个键都送、NEVER 删任一个**（见类注释那条实测教训）；
     * 调用方 MUST 先确认 {@code PLATFORM_REFUND_NO} 非空，拿不到就别发。
     */
    public Map<String, Object> buildRefundQuery(DailyTicketRefund refund) {
        Map<String, Object> queryRequest = new LinkedHashMap<>();
        queryRequest.put("refundOrderNo", refund.getPlatformRefundNo());
        queryRequest.put("merchantRefundNo", refund.getRefundOrderNo());
        return queryRequest;
    }
}

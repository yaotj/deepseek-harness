package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.defaultString;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 退款的**业务规则与本地实体装配**（2026-09-16 由 {@code RefundDomainServiceImpl} 逐字搬出，ADR-D98）。 */
public final class PayRefundRules {

    private PayRefundRules() {
    }

    /** 校验原支付订单是否允许退款。 */
    public static String validateRefundPayTxn(PayTxnDetail payTxn, Integer refundAmount) {
        if (payTxn == null) {
            return "原支付订单不存在";
        }
        if (!"SUCCESS".equals(payTxn.getPayStatus())) {
            return "原支付订单未支付成功";
        }
        if (resolvePaidAmount(payTxn) <= 0) {
            return "原支付订单实收金额为0，不可退款";
        }
        if (!StringUtils.hasText(payTxn.getPayCenterOrderNo())) {
            return "原支付订单缺少支付中心订单号，无法发起退款";
        }
        int paidAmount = resolvePaidAmount(payTxn);
        int refundedAmount = payTxn.getRefundAmount() == null ? 0 : payTxn.getRefundAmount();
        if (refundAmount > paidAmount - refundedAmount) {
            return "退款金额超出可退金额";
        }
        return null;
    }

    /** 已付金额：{@code TOTAL_AMOUNT} 优先，缺失或非正时退回 {@code AMOUNT}。 */
    public static int resolvePaidAmount(PayTxnDetail payTxn) {
        if (payTxn.getTotalAmount() != null && payTxn.getTotalAmount() > 0) {
            return payTxn.getTotalAmount();
        }
        return payTxn.getAmount() == null ? 0 : payTxn.getAmount();
    }

    /** 创建本地退款明细。 */
    public static PayRefundDetail buildPayRefundDetail(RequestRefundReqDTO request, PayTxnDetail payTxn) {
        PayRefundDetail record = new PayRefundDetail();
        record.setRefundOrderNo(buildRefundOrderNo(request.getOrderNo()));
        record.setOrderNo(payTxn.getOrderNo());
        record.setRefundStatus("INIT");
        record.setRefundAmount(request.getRefundAmount());
        record.setRefundReason(request.getRefundReason());
        record.setRequestCount(0);
        record.setTxnDate(resolveTxnDate());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    /** 回填退款应答里来自网关 {@code data} 的四个号码字段。 */
    public static void fillRefundResponseFields(RequestRefundResult response, PayRefundDetail refundDetail,
                                                PaySignGatewayResponse gatewayResponse) {
        response.setOrderNo(refundDetail.getOrderNo());
        response.setRefundOrderNo(refundDetail.getRefundOrderNo());
        if (gatewayResponse == null || gatewayResponse.getData() == null) {
            return;
        }
        response.setMerchantRefundNo(stringValue(gatewayResponse.getData().get("merchantRefundNo"), null));
        response.setRefundNo(stringValue(gatewayResponse.getData().get("refundNo"), null));
        response.setChannelRefundNo(stringValue(gatewayResponse.getData().get("channelRefundNo"), null));
        response.setRefundTime(stringValue(gatewayResponse.getData().get("refundTime"), null));
    }

    private static String buildRefundOrderNo(String orderNo) {
        String suffix = orderNo;
        if (suffix != null && suffix.length() > 8) {
            suffix = suffix.substring(suffix.length() - 8);
        }
        return "RF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + defaultString(suffix, "");
    }
}

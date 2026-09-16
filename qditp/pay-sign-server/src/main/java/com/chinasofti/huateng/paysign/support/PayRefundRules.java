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

/**
 * 退款的**业务规则与本地实体装配**（2026-09-16 由 {@code RefundDomainServiceImpl} 逐字搬出，ADR-D98）。
 *
 * <p><b>为什么不是搬进 {@link PaySignValidators}</b>：那个类的类注释里明文写着
 * 「刻意留在业务类里没搬的是 {@code validateRefundPayTxn}……属**退款业务规则**而非报文校验，
 * 搬进本类等于把业务判断塞进 support 包，NEVER 因为名字都叫 validate 就一起搬」。
 * 本次外提**没有推翻那条判断** —— 它说的是「不要混进报文校验类」，本类正是为此单独开的：
 * 报文校验（{@code PaySignValidators}）与退款业务规则（本类）在包里是两个类、两份职责。
 * <b>NEVER 把本类的方法并进 {@code PaySignValidators}。</b>
 *
 * <p><b>本类 MUST 保持纯函数、零状态、零依赖</b>（与 {@code PaySignValidators} /
 * {@code PaySignResponses} 同一条件式破例，见那两个类的注释与 ADR-D84）：
 * <b>NEVER 往本类注入任何 mapper / client / properties</b>。一旦需要它们，说明这段逻辑
 * 不属于本类，应留在领域服务里。
 */
public final class PayRefundRules {

    private PayRefundRules() {
    }

    /**
     * 校验原支付订单是否允许退款。
     *
     * <p>返回的字符串会原样进 APP 应答的 {@code retMsg}，<b>NEVER 改文案、NEVER 调整判断顺序</b>
     * —— 顺序决定「同时不满足两条时报哪一条」，联调方可能已按文案做断言。</p>
     */
    public static String validateRefundPayTxn(PayTxnDetail payTxn, Integer refundAmount) {
        if (payTxn == null) {
            return "原支付订单不存在";
        }
        if (!"SUCCESS".equals(payTxn.getPayStatus())) {
            return "原支付订单未支付成功";
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
        // ORDER_NO MUST 存我方商户订单号：退款汇总 updateRefundSummary 是按
        // PAY_REFUND_DETAIL.ORDER_NO = PAY_TXN_DETAIL.ORDER_NO 关联重算的，存别的号一律关联不上。
        // 生产已有 12 条 PAY_REFUND_DETAIL 因历史上存了支付中心订单号而关联不到原支付订单。
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

package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.*;

import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 护栏：可退金额上界、ORDER_NO 必须存我方商户订单号、以及校验的判断顺序。 */
class PayRefundRulesTest {

    private static final String MERCHANT_ORDER_NO = "GT20260916100000001586419";
    private static final String PAY_CENTER_ORDER_NO = "286275496309587968";

    @Test
    void nullPayTxnIsRejected() {
        assertEquals("原支付订单不存在", PayRefundRules.validateRefundPayTxn(null, 100));
    }

    @Test
    void unpaidOrderIsRejected() {
        assertEquals("原支付订单未支付成功",
                PayRefundRules.validateRefundPayTxn(payTxn("PROCESSING", 300, 0), 100));
    }

    /** 缺支付中心订单号无法发起退款：那是出向报文的必填键。 */
    @Test
    void missingPayCenterOrderNoIsRejected() {
        PayTxnDetail payTxn = payTxn("SUCCESS", 300, 0);
        payTxn.setPayCenterOrderNo(null);
        assertEquals("原支付订单缺少支付中心订单号，无法发起退款",
                PayRefundRules.validateRefundPayTxn(payTxn, 100));
    }

    /** 可退金额上界：已付 300、已退 100 时，退 201 拒、退 200 放行（边界 MUST 是可退）。 */
    @Test
    void refundAmountUpperBoundIsPaidMinusRefunded() {
        assertEquals("退款金额超出可退金额",
                PayRefundRules.validateRefundPayTxn(payTxn("SUCCESS", 300, 100), 201));
        assertNull(PayRefundRules.validateRefundPayTxn(payTxn("SUCCESS", 300, 100), 200));
    }

    /** REFUND_AMOUNT 为 null 视作已退 0，NEVER 因为 null 就拒掉整笔退款。 */
    @Test
    void nullRefundedAmountCountsAsZero() {
        PayTxnDetail payTxn = payTxn("SUCCESS", 300, null);
        assertNull(PayRefundRules.validateRefundPayTxn(payTxn, 300));
    }

    /** BOM 补站代收单（ITP 实收 0 元）MUST 拒退，NEVER 放宽成「只看有没有支付中心号」。 */
    @Test
    void zeroPaidAmountIsRejected() {
        PayTxnDetail payTxn = payTxn("SUCCESS", 0, 0);
        payTxn.setAmount(0);
        assertEquals("原支付订单实收金额为0，不可退款",
                PayRefundRules.validateRefundPayTxn(payTxn, 100));
    }

    /** 顺序不可调：未支付成功**且**缺支付中心号时，报的是前者。 */
    @Test
    void validationOrderIsPinned() {
        PayTxnDetail payTxn = payTxn("PROCESSING", 300, 0);
        payTxn.setPayCenterOrderNo(null);
        assertEquals("原支付订单未支付成功", PayRefundRules.validateRefundPayTxn(payTxn, 100));
    }

    /** 已付金额：TOTAL_AMOUNT 优先；缺失或非正时退回 AMOUNT；两者都空按 0。 */
    @Test
    void paidAmountPrefersTotalAmountThenFallsBack() {
        PayTxnDetail withTotal = payTxn("SUCCESS", 300, 0);
        withTotal.setAmount(999);
        assertEquals(300, PayRefundRules.resolvePaidAmount(withTotal));

        PayTxnDetail zeroTotal = payTxn("SUCCESS", 0, 0);
        zeroTotal.setAmount(250);
        assertEquals(250, PayRefundRules.resolvePaidAmount(zeroTotal));

        PayTxnDetail nullTotal = payTxn("SUCCESS", null, 0);
        nullTotal.setAmount(120);
        assertEquals(120, PayRefundRules.resolvePaidAmount(nullTotal));

        PayTxnDetail bothNull = payTxn("SUCCESS", null, 0);
        assertEquals(0, PayRefundRules.resolvePaidAmount(bothNull));
    }

    /** 本地退款明细：{@code ORDER_NO} MUST 是我方商户订单号。 */
    @Test
    void refundDetailKeepsMerchantOrderNoNotPayCenterOrderNo() {
        PayRefundDetail detail = PayRefundRules.buildPayRefundDetail(refundRequest(100), payTxn("SUCCESS", 300, 0));

        assertEquals(MERCHANT_ORDER_NO, detail.getOrderNo());
        assertNotEquals(PAY_CENTER_ORDER_NO, detail.getOrderNo());
        assertEquals("INIT", detail.getRefundStatus());
        assertEquals(0, detail.getRequestCount());
        assertEquals(100, detail.getRefundAmount());
        assertNotNull(detail.getTxnDate());
    }

    /** 退款单号形状：RF + 17 位时间戳 + 商户订单号后 8 位。 */
    @Test
    void refundOrderNoIsPrefixedAndSuffixedByOrderNoTail() {
        PayRefundDetail detail = PayRefundRules.buildPayRefundDetail(refundRequest(100), payTxn("SUCCESS", 300, 0));
        String refundOrderNo = detail.getRefundOrderNo();

        assertTrue(refundOrderNo.startsWith("RF"), refundOrderNo);
        assertTrue(refundOrderNo.endsWith(MERCHANT_ORDER_NO.substring(MERCHANT_ORDER_NO.length() - 8)), refundOrderNo);
        assertEquals(2 + 17 + 8, refundOrderNo.length(), refundOrderNo);
    }

    /** 网关无 data 时不抛，且两个本地号照样回填 —— 上游要靠它们对账。 */
    @Test
    void responseFieldsSurviveMissingGatewayData() {
        RequestRefundResult response = new RequestRefundResult();
        PayRefundDetail detail = PayRefundRules.buildPayRefundDetail(refundRequest(100), payTxn("SUCCESS", 300, 0));

        PayRefundRules.fillRefundResponseFields(response, detail, null);

        assertEquals(MERCHANT_ORDER_NO, response.getOrderNo());
        assertEquals(detail.getRefundOrderNo(), response.getRefundOrderNo());
        assertNull(response.getRefundNo());
    }

    @Test
    void responseFieldsCopyFourGatewayNumbers() {
        RequestRefundResult response = new RequestRefundResult();
        PayRefundDetail detail = PayRefundRules.buildPayRefundDetail(refundRequest(100), payTxn("SUCCESS", 300, 0));
        PaySignGatewayResponse gateway = new PaySignGatewayResponse();
        Map<String, Object> data = new HashMap<>();
        data.put("merchantRefundNo", detail.getRefundOrderNo());
        data.put("refundNo", "RN-0001");
        data.put("channelRefundNo", "CRN-0001");
        data.put("refundTime", "20260916104500");
        gateway.setData(data);

        PayRefundRules.fillRefundResponseFields(response, detail, gateway);

        assertEquals(detail.getRefundOrderNo(), response.getMerchantRefundNo());
        assertEquals("RN-0001", response.getRefundNo());
        assertEquals("CRN-0001", response.getChannelRefundNo());
        assertEquals("20260916104500", response.getRefundTime());
    }

    private RequestRefundReqDTO refundRequest(int refundAmount) {
        RequestRefundReqDTO request = new RequestRefundReqDTO();
        request.setOrderNo(MERCHANT_ORDER_NO);
        request.setRefundAmount(refundAmount);
        request.setRefundReason("测试退款");
        return request;
    }

    private PayTxnDetail payTxn(String payStatus, Integer totalAmount, Integer refundedAmount) {
        PayTxnDetail payTxn = new PayTxnDetail();
        payTxn.setOrderNo(MERCHANT_ORDER_NO);
        payTxn.setPayCenterOrderNo(PAY_CENTER_ORDER_NO);
        payTxn.setPayStatus(payStatus);
        payTxn.setTotalAmount(totalAmount);
        payTxn.setRefundAmount(refundedAmount);
        return payTxn;
    }
}

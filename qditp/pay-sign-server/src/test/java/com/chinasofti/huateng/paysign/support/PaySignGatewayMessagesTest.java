package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildContractBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildCreditQueryBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildDismissalBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildQueryResultBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildRequestPayBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildRequestRefundBizData;
import static org.assertj.core.api.Assertions.assertThat;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 护栏：出网报文的键集、键序与「空值不进报文」（键序参与加签）。 */
class PaySignGatewayMessagesTest {

    @Test
    @DisplayName("签约：必填四项恒在，扩展字段为空时整个键都不出现")
    void contractOmitsBlankOptionalFields() {
        RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
        request.setRequestSignSeq("SEQ-1");
        request.setThirdUserId("U-1");
        request.setDisplayAccount("138****8000");

        Map<String, Object> bizData = buildContractBizData(request, "03", null);

        assertThat(bizData).containsOnlyKeys("requestSignSeq", "paymentVendor", "thirdUserId", "displayAccount");
        assertThat(bizData).containsEntry("paymentVendor", "03");
        assertThat(bizData).doesNotContainKey("notifyUrl");
        assertThat(bizData).doesNotContainKey("bankCardNo");
    }

    @Test
    @DisplayName("签约：notifyUrl 取调用方传入值，与请求里的 returnUrl 互不影响")
    void contractTakesNotifyUrlFromCaller() {
        RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
        request.setRequestSignSeq("SEQ-1");
        request.setThirdUserId("U-1");
        request.setDisplayAccount("138****8000");
        request.setReturnUrl("https://app.example/return");

        Map<String, Object> bizData = buildContractBizData(request, "03", "https://itp.example/notify");

        assertThat(bizData).containsEntry("notifyUrl", "https://itp.example/notify");
        assertThat(bizData).containsEntry("returnUrl", "https://app.example/return");
    }

    @Test
    @DisplayName("签约：键序固定，加签串不能因字段增减而漂移")
    void contractKeyOrderIsStable() {
        RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
        request.setRequestSignSeq("SEQ-1");
        request.setThirdUserId("U-1");
        request.setDisplayAccount("138****8000");
        request.setOptions("{}");
        request.setBankCardNo("6222****1234");

        Map<String, Object> bizData = buildContractBizData(request, "03", "https://itp.example/notify");

        assertThat(bizData.keySet()).containsExactly("requestSignSeq", "paymentVendor", "thirdUserId",
                "displayAccount", "notifyUrl", "options", "bankCardNo");
    }

    @Test
    @DisplayName("授信查询：三键固定且有序")
    void creditQueryCarriesThreeKeysInOrder() {
        Map<String, Object> bizData = buildCreditQueryBizData("U-1", "SEQ-1", "03");
        assertThat(bizData.keySet()).containsExactly("thirdUserId", "requestSignSeq", "paymentVendor");
        assertThat(bizData).containsEntry("thirdUserId", "U-1");
    }

    @Test
    @DisplayName("签约结果查询与解约：键集相同但是两个独立方法，各自只带 requestSignSeq")
    void queryResultAndDismissalAreSeparateSingleKeyMessages() {
        assertThat(buildQueryResultBizData("SEQ-1")).containsExactlyEntriesOf(Map.of("requestSignSeq", "SEQ-1"));
        assertThat(buildDismissalBizData("SEQ-1")).containsExactlyEntriesOf(Map.of("requestSignSeq", "SEQ-1"));
    }

    @Test
    @DisplayName("免密扣款：amount 为 0 仍进报文，orderTimeOut 为 null 则不进")
    void requestPayKeepsZeroAmountAndOmitsNullTimeout() {
        RequestPayReqDTO request = new RequestPayReqDTO();
        request.setOrderNo("ORD-1");
        request.setScene("GATE");
        request.setPaymentVendor("03");
        request.setAmount(0);
        request.setIndustryType("METRO");
        request.setSubject("地铁乘车");
        request.setBody("地铁乘车扣费");

        Map<String, Object> bizData = buildRequestPayBizData(request, null);

        assertThat(bizData).containsEntry("amount", 0);
        assertThat(bizData).doesNotContainKey("orderTimeOut");
        assertThat(bizData).doesNotContainKey("notifyUrl");
        assertThat(bizData.keySet()).containsExactly("orderNo", "scene", "paymentVendor", "amount",
                "industryType", "subject", "body");
    }

    @Test
    @DisplayName("免密扣款：可选字段齐全时键序固定，notifyUrl 排在 authCode 之后")
    void requestPayKeyOrderIsStable() {
        RequestPayReqDTO request = new RequestPayReqDTO();
        request.setOrderNo("ORD-1");
        request.setScene("GATE");
        request.setPaymentVendor("03");
        request.setAmount(100);
        request.setIndustryType("METRO");
        request.setSubject("地铁乘车");
        request.setBody("地铁乘车扣费");
        request.setRequestSignSeq("SEQ-1");
        request.setThirdUserId("U-1");
        request.setOrderTimeOut(300L);
        request.setAuthCode("AUTH-1");
        request.setRemark("备注");

        Map<String, Object> bizData = buildRequestPayBizData(request, "https://itp.example/pay-notify");

        assertThat(bizData.keySet()).containsExactly("orderNo", "scene", "paymentVendor", "amount",
                "industryType", "subject", "body", "requestSignSeq", "thirdUserId",
                "orderTimeOut", "authCode", "notifyUrl", "remark");
    }

    @Test
    @DisplayName("退款：orderNo 取原支付的 PAY_CENTER_ORDER_NO，merchantOrderNo 取商户订单号")
    void requestRefundTakesOrderNoFromPayCenterOrderNo() {
        PayRefundDetail refundDetail = new PayRefundDetail();
        refundDetail.setRefundOrderNo("RF-1");
        refundDetail.setRefundAmount(50);
        refundDetail.setRefundReason("用户申请");
        refundDetail.setMerchantRefundNo(null);

        PayTxnDetail payTxn = new PayTxnDetail();
        payTxn.setOrderNo("ORD-1");
        payTxn.setPayCenterOrderNo("PC-1");

        Map<String, Object> bizData = buildRequestRefundBizData(refundDetail, payTxn, null);

        assertThat(bizData.keySet()).containsExactly("refundOrderNo", "merchantOrderNo", "orderNo",
                "refundAmount", "refundReason");
        assertThat(bizData).containsEntry("merchantOrderNo", "ORD-1");
        assertThat(bizData).containsEntry("orderNo", "PC-1");
        assertThat(bizData).containsEntry("refundAmount", 50);
    }

    @Test
    @DisplayName("退款：配了退款回调地址时 notifyUrl 必送（§3.1 必填键，缺它回调结构上到不了）")
    void requestRefundCarriesNotifyUrlWhenConfigured() {
        PayRefundDetail refundDetail = new PayRefundDetail();
        refundDetail.setRefundOrderNo("RF-2");
        refundDetail.setRefundAmount(50);

        PayTxnDetail payTxn = new PayTxnDetail();
        payTxn.setOrderNo("ORD-2");
        payTxn.setPayCenterOrderNo("PC-2");

        Map<String, Object> bizData = buildRequestRefundBizData(refundDetail, payTxn,
                "http://127.0.0.1:48000/fep-app/ci/app/paySign/payment/receiveRefundResult");

        assertThat(bizData).containsEntry("notifyUrl",
                "http://127.0.0.1:48000/fep-app/ci/app/paySign/payment/receiveRefundResult");
    }
}

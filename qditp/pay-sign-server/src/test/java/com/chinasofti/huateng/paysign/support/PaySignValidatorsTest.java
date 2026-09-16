package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateContractQuery;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveSignResult;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveTerminationResult;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestPay;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestRefund;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestSignInfo;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestTermination;
import static org.assertj.core.api.Assertions.assertThat;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PaySignValidators} 的行为固化测试（2026-09-14 拆分批次 2）。
 *
 * <p><b>断言的是文案与判断顺序，不是「有没有报错」</b>：这些字符串会原样进 APP 应答的
 * {@code retMsg}，联调方可能已按文案做断言。因此每个「缺字段」用例都只留一个字段为空、
 * 其余填满，逐个钉死映射关系；另有一组「同时缺两个」用例钉死**先报哪一个**。
 * 改这些断言等于改对外契约，<b>MUST 当成契约变更走确认，NEVER 顺手改成期望新文案</b>。
 */
class PaySignValidatorsTest {

    private static RequestSignInfoReqDTO fullSignInfo() {
        RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
        request.setThirdUserId("U-1");
        request.setDisplayAccount("138****8000");
        request.setPayChannelCode("ALIPAY");
        request.setRequestSignSeq("SEQ-1");
        return request;
    }

    private static RequestPayReqDTO fullPay() {
        RequestPayReqDTO request = new RequestPayReqDTO();
        request.setOrderNo("ORD-1");
        request.setScene("GATE");
        request.setThirdUserId("U-1");
        request.setAmount(100);
        request.setIndustryType("METRO");
        request.setSubject("地铁乘车");
        request.setBody("地铁乘车扣费");
        request.setCardId("CARD-1");
        request.setCardType("0441");
        return request;
    }

    @Test
    @DisplayName("签约请求：null 与四个必填字段逐一对应固定文案")
    void requestSignInfoReportsFieldSpecificMessages() {
        assertThat(validateRequestSignInfo(null)).isEqualTo("请求报文不能为空");
        assertThat(validateRequestSignInfo(fullSignInfo())).isNull();

        RequestSignInfoReqDTO noUser = fullSignInfo();
        noUser.setThirdUserId(" ");
        assertThat(validateRequestSignInfo(noUser)).isEqualTo("thirdUserId不能为空");

        RequestSignInfoReqDTO noAccount = fullSignInfo();
        noAccount.setDisplayAccount(null);
        assertThat(validateRequestSignInfo(noAccount)).isEqualTo("displayAccount不能为空");

        RequestSignInfoReqDTO noChannel = fullSignInfo();
        noChannel.setPayChannelCode(null);
        assertThat(validateRequestSignInfo(noChannel)).isEqualTo("payChannelCode不能为空");

        RequestSignInfoReqDTO noSeq = fullSignInfo();
        noSeq.setRequestSignSeq(null);
        assertThat(validateRequestSignInfo(noSeq)).isEqualTo("requestSignSeq不能为空");
    }

    @Test
    @DisplayName("签约请求：同时缺 thirdUserId 与 requestSignSeq 时先报 thirdUserId")
    void requestSignInfoReportsFirstMissingFieldInDeclaredOrder() {
        RequestSignInfoReqDTO request = fullSignInfo();
        request.setThirdUserId(null);
        request.setRequestSignSeq(null);
        assertThat(validateRequestSignInfo(request)).isEqualTo("thirdUserId不能为空");
    }

    @Test
    @DisplayName("签约查询：三个入参按 thirdUserId / requestSignSeq / paymentVendor 顺序校验")
    void contractQueryChecksThreeParametersInOrder() {
        assertThat(validateContractQuery("U-1", "SEQ-1", "03")).isNull();
        assertThat(validateContractQuery(null, null, null)).isEqualTo("thirdUserId不能为空");
        assertThat(validateContractQuery("U-1", "", "")).isEqualTo("requestSignSeq不能为空");
        assertThat(validateContractQuery("U-1", "SEQ-1", " ")).isEqualTo("paymentVendor不能为空");
    }

    @Test
    @DisplayName("解约请求：cardId / cardType / paymentVendor 缺失仍放行")
    void requestTerminationDoesNotRequireCardFields() {
        assertThat(validateRequestTermination(null)).isEqualTo("请求报文不能为空");

        RequestTerminationReqDTO request = new RequestTerminationReqDTO();
        request.setThirdUserId("U-1");
        request.setRequestSignSeq("SEQ-1");
        assertThat(validateRequestTermination(request)).isNull();

        request.setThirdUserId(null);
        assertThat(validateRequestTermination(request)).isEqualTo("thirdUserId不能为空");

        request.setThirdUserId("U-1");
        request.setRequestSignSeq(null);
        assertThat(validateRequestTermination(request)).isEqualTo("requestSignSeq不能为空");
    }

    @Test
    @DisplayName("免密扣款：amount 允许 0、只拒负数")
    void requestPayRejectsNegativeAmountButAcceptsZero() {
        assertThat(validateRequestPay(null)).isEqualTo("请求报文不能为空");
        assertThat(validateRequestPay(fullPay())).isNull();

        RequestPayReqDTO zeroAmount = fullPay();
        zeroAmount.setAmount(0);
        assertThat(validateRequestPay(zeroAmount)).isNull();

        RequestPayReqDTO nullAmount = fullPay();
        nullAmount.setAmount(null);
        assertThat(validateRequestPay(nullAmount)).isEqualTo("amount不能为空");

        RequestPayReqDTO negative = fullPay();
        negative.setAmount(-1);
        assertThat(validateRequestPay(negative)).isEqualTo("amount不能小于0");
    }

    @Test
    @DisplayName("免密扣款：其余七个必填字段逐一对应固定文案")
    void requestPayReportsFieldSpecificMessages() {
        RequestPayReqDTO noOrder = fullPay();
        noOrder.setOrderNo(null);
        assertThat(validateRequestPay(noOrder)).isEqualTo("orderNo不能为空");

        RequestPayReqDTO noScene = fullPay();
        noScene.setScene(null);
        assertThat(validateRequestPay(noScene)).isEqualTo("scene不能为空");

        RequestPayReqDTO noUser = fullPay();
        noUser.setThirdUserId(null);
        assertThat(validateRequestPay(noUser)).isEqualTo("thirdUserId不能为空");

        RequestPayReqDTO noIndustry = fullPay();
        noIndustry.setIndustryType(null);
        assertThat(validateRequestPay(noIndustry)).isEqualTo("industryType不能为空");

        RequestPayReqDTO noSubject = fullPay();
        noSubject.setSubject(null);
        assertThat(validateRequestPay(noSubject)).isEqualTo("subject不能为空");

        RequestPayReqDTO noBody = fullPay();
        noBody.setBody(null);
        assertThat(validateRequestPay(noBody)).isEqualTo("body不能为空");

        RequestPayReqDTO noCardId = fullPay();
        noCardId.setCardId(null);
        assertThat(validateRequestPay(noCardId)).isEqualTo("cardId不能为空");

        RequestPayReqDTO noCardType = fullPay();
        noCardType.setCardType(null);
        assertThat(validateRequestPay(noCardType)).isEqualTo("cardType不能为空");
    }

    @Test
    @DisplayName("请求退款：refundAmount 必须大于 0，且不校验可退金额上界")
    void requestRefundOnlyChecksLowerBound() {
        assertThat(validateRequestRefund(null)).isEqualTo("请求报文不能为空");

        RequestRefundReqDTO request = new RequestRefundReqDTO();
        request.setOrderNo("ORD-1");
        request.setRefundAmount(1);
        assertThat(validateRequestRefund(request)).isNull();

        request.setRefundAmount(Integer.MAX_VALUE);
        assertThat(validateRequestRefund(request)).isNull();

        request.setRefundAmount(0);
        assertThat(validateRequestRefund(request)).isEqualTo("refundAmount必须大于0");

        request.setRefundAmount(null);
        assertThat(validateRequestRefund(request)).isEqualTo("refundAmount不能为空");

        request.setOrderNo(null);
        request.setRefundAmount(1);
        assertThat(validateRequestRefund(request)).isEqualTo("orderNo不能为空");
    }

    @Test
    @DisplayName("签约结果回调：thirdUserId 缺失仍放行，另三项必填")
    void receiveSignResultDoesNotRequireThirdUserId() {
        assertThat(validateReceiveSignResult(null)).isEqualTo("请求报文不能为空");

        ReceiveSignResultReqDTO request = new ReceiveSignResultReqDTO();
        request.setRequestSignSeq("SEQ-1");
        request.setPaymentVendor("03");
        request.setStatus("SUCCESS");
        assertThat(validateReceiveSignResult(request)).isNull();

        request.setRequestSignSeq(null);
        assertThat(validateReceiveSignResult(request)).isEqualTo("requestSignSeq不能为空");

        request.setRequestSignSeq("SEQ-1");
        request.setPaymentVendor(null);
        assertThat(validateReceiveSignResult(request)).isEqualTo("paymentVendor不能为空");

        request.setPaymentVendor("03");
        request.setStatus(null);
        assertThat(validateReceiveSignResult(request)).isEqualTo("status不能为空");
    }

    @Test
    @DisplayName("解约结果回调：thirdUserId / cardId / cardType 缺失仍放行，另三项必填")
    void receiveTerminationResultDoesNotRequireCardFields() {
        assertThat(validateReceiveTerminationResult(null)).isEqualTo("请求报文不能为空");

        ReceiveTerminationResultReqDTO request = new ReceiveTerminationResultReqDTO();
        request.setRequestSignSeq("SEQ-1");
        request.setPaymentVendor("03");
        request.setStatus("UNSIGNED");
        assertThat(validateReceiveTerminationResult(request)).isNull();

        request.setRequestSignSeq(null);
        assertThat(validateReceiveTerminationResult(request)).isEqualTo("requestSignSeq不能为空");

        request.setRequestSignSeq("SEQ-1");
        request.setPaymentVendor(null);
        assertThat(validateReceiveTerminationResult(request)).isEqualTo("paymentVendor不能为空");

        request.setPaymentVendor("03");
        request.setStatus(null);
        assertThat(validateReceiveTerminationResult(request)).isEqualTo("status不能为空");
    }
}

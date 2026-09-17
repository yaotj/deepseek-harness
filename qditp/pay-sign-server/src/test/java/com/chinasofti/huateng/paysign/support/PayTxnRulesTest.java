package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.*;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import org.junit.jupiter.api.Test;

/** 护栏：钱包认 payUserId、传统渠道认 requestSignSeq，两套校验 NEVER 合并。 */
class PayTxnRulesTest {

    @Test
    void blankVendorIsRejectedBeforeChannelDispatch() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest(null, null, null), response));
        assertEquals(PaySignErrorCodeEnum.USER_NOT_SIGNED.getCode(), response.getRetCode());
        assertEquals("paymentVendor不能为空", response.getRetMsg());
    }

    /** 钱包：认 payUserId，缺它即视为未签约。 */
    @Test
    void walletRequiresPayUserId() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest("0B", null, null), response));
        assertEquals("钱包支付账户标识不能为空", response.getRetMsg());

        assertTrue(PayTxnRules.validatePaySignInfo(payRequest("0B", "2088-WALLET-0001", null), new RequestPayResult()));
    }

    /** 钱包 NEVER 因为缺 requestSignSeq 被拒 —— 那是传统渠道的键。 */
    @Test
    void walletDoesNotRequireRequestSignSeq() {
        assertTrue(PayTxnRules.validatePaySignInfo(payRequest("0B", "2088-WALLET-0001", null), new RequestPayResult()));
    }

    /** 传统渠道：认 requestSignSeq，缺它即未签约；有 payUserId 也不顶用。 */
    @Test
    void contractedRequiresRequestSignSeq() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest("03", "2088-XXX", null), response));
        assertEquals("requestSignSeq不能为空", response.getRetMsg());

        assertTrue(PayTxnRules.validatePaySignInfo(payRequest("03", null, "0052290701523990"), new RequestPayResult()));
    }

    /** 带空白的钱包渠道号也 MUST 认出来（归一化在 PaymentChannels 内部完成）。 */
    @Test
    void walletVendorWithWhitespaceStillTakesWalletBranch() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest("  0B  ", null, null), response));
        assertEquals("钱包支付账户标识不能为空", response.getRetMsg());
    }

    /** PAY_STATUS 到扣费请求结果的归一：只有 SUCCESS / PROCESSING 原样，其余一律 FAIL。 */
    @Test
    void debitRequestResultCollapsesEverythingElseToFail() {
        assertEquals("PROCESSING", PayTxnRules.resolveDebitRequestResult("PROCESSING"));
        assertEquals("SUCCESS", PayTxnRules.resolveDebitRequestResult("SUCCESS"));
        assertEquals("FAIL", PayTxnRules.resolveDebitRequestResult("FAIL"));
        assertEquals("FAIL", PayTxnRules.resolveDebitRequestResult("RETRY"));
        assertEquals("FAIL", PayTxnRules.resolveDebitRequestResult(null));
    }

    private RequestPayReqDTO payRequest(String vendor, String payUserId, String requestSignSeq) {
        RequestPayReqDTO request = new RequestPayReqDTO();
        request.setPaymentVendor(vendor);
        request.setPayUserId(payUserId);
        request.setRequestSignSeq(requestSignSeq);
        return request;
    }
}

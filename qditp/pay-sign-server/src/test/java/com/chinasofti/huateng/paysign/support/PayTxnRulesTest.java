package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.*;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import org.junit.jupiter.api.Test;

class PayTxnRulesTest {

    @Test
    void blankVendorIsRejectedBeforeChannelDispatch() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest(null, null, null), response));
        assertEquals(PaySignErrorCodeEnum.USER_NOT_SIGNED.getCode(), response.getRetCode());
        assertEquals("paymentVendor不能为空", response.getRetMsg());
    }

    @Test
    void walletRequiresPayUserId() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest("0B", null, null), response));
        assertEquals("钱包支付账户标识不能为空", response.getRetMsg());

        assertTrue(PayTxnRules.validatePaySignInfo(payRequest("0B", "2088-WALLET-0001", null), new RequestPayResult()));
    }

    @Test
    void walletDoesNotRequireRequestSignSeq() {
        assertTrue(PayTxnRules.validatePaySignInfo(payRequest("0B", "2088-WALLET-0001", null), new RequestPayResult()));
    }

    @Test
    void contractedRequiresRequestSignSeq() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest("03", "2088-XXX", null), response));
        assertEquals("requestSignSeq不能为空", response.getRetMsg());

        assertTrue(PayTxnRules.validatePaySignInfo(payRequest("03", null, "0052290701523990"), new RequestPayResult()));
    }

    @Test
    void walletVendorWithWhitespaceStillTakesWalletBranch() {
        RequestPayResult response = new RequestPayResult();
        assertFalse(PayTxnRules.validatePaySignInfo(payRequest("  0B  ", null, null), response));
        assertEquals("钱包支付账户标识不能为空", response.getRetMsg());
    }

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

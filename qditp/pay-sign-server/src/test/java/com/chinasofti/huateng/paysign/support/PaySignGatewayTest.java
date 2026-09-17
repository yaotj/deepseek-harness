package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import org.junit.jupiter.api.Test;

/** 护栏：isAlreadyPaidSuccess 的两个条件 MUST 同时成立，NEVER 放宽成只看 9999。 */
class PaySignGatewayTest {

    private final PaySignGateway gateway = new PaySignGateway(mock(PayGatewayClient.class));

    @Test
    void recognizesAlreadyPaidByMessageWording() {
        assertTrue(gateway.isAlreadyPaidSuccess(response(9999, "该订单已支付成功")));
        assertTrue(gateway.isAlreadyPaidSuccess(response(9999, "请勿重复支付")));
    }

    /** 9999 但措辞不是那两种 —— MUST 仍算失败。 */
    @Test
    void plainNineThousandNineHundredNinetyNineIsStillFailure() {
        assertFalse(gateway.isAlreadyPaidSuccess(response(9999, "系统繁忙")));
        assertFalse(gateway.isAlreadyPaidSuccess(response(9999, null)));
    }

    /** 措辞对但码不是 9999：也不算 —— 两个条件 MUST 同时成立。 */
    @Test
    void wordingAloneIsNotEnough() {
        assertFalse(gateway.isAlreadyPaidSuccess(response(600, "该订单已支付成功")));
    }

    @Test
    void nullResponseIsFalseWithoutThrowing() {
        assertFalse(gateway.isAlreadyPaidSuccess(null));
    }

    private PaySignGatewayResponse response(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
}

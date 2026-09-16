package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import org.junit.jupiter.api.Test;

/**
 * {@link PaySignGateway#isAlreadyPaidSuccess} 的护栏（2026-09-16）。
 *
 * <p><b>补的是 true 那一支</b>：既有用例的网关失败码一律是 {@code 600}，这一支此前零执行。
 * 类注释写明它散掉的后果是<b>把一笔已扣款成功的交易判成失败、进而走到拉黑分支</b>。
 *
 * <p>同时钉住反面：<b>NEVER 放宽成「code=9999 就算已支付」</b> —— 9999 是支付中心的通用失败码，
 * 放宽等于把所有失败都当成功。
 */
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

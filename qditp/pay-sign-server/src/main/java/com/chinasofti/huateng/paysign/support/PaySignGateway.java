package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付中心网关的**调用与应答判读**收口点：签约 / 支付 / 回调三条链路共用。
 *
 * <p>与 {@link PaySignValues} 的分工：那个类只收纯函数、可以全静态；本类要持有
 * {@link PayGatewayClient}，因此是 Spring Bean。两者都是为了让 `PaySignWorkflow` 拆成
 * 三个领域服务时**不必各留一份逐字副本**而抽的，理由同 ADR-D84 的 {@code F2fDuplicateKey}。
 *
 * <p><b>为什么不直接让三个领域服务各注 {@code PayGatewayClient}</b>：`isSuccess` / `errorMessage`
 * 本身就在 client 上，真正不能散的是 {@link #isAlreadyPaidSuccess} —— 它按
 * 「code=9999 且 msg 含特定中文」判「其实已支付成功」，是**对支付中心应答措辞的硬编码约定**，
 * 措辞一变就要同步改。散成三份副本时，改一处漏两处不会有任何编译错误，只会让一笔
 * 已扣款成功的交易被判成失败、进而走到拉黑分支。
 */
@Component
public class PaySignGateway {

    private final PayGatewayClient payGatewayClient;

    public PaySignGateway(PayGatewayClient payGatewayClient) {
        this.payGatewayClient = payGatewayClient;
    }

    /**
     * 调用支付平台通用网关。
     * 按公共报文格式组装 merchantNo/apiVersion/signType/charset/bizData/sign。
     */
    public PaySignGatewayResponse request(String url, Map<String, Object> bizData) {
        return payGatewayClient.request(url, bizData);
    }

    public boolean isSuccess(PaySignGatewayResponse response) {
        return payGatewayClient.isSuccess(response);
    }

    public String errorMessage(PaySignGatewayResponse response, String defaultMsg) {
        return payGatewayClient.errorMessage(response, defaultMsg);
    }

    /**
     * 判断是否为"订单已支付成功"幂等场景（网关返回 code=9999 但业务上已成功）
     *
     * <p>只按 msg 措辞识别，**NEVER 放宽成「code=9999 就算已支付」** —— 9999 是支付中心的通用
     * 失败码，放宽等于把所有失败都当成功。
     */
    public boolean isAlreadyPaidSuccess(PaySignGatewayResponse response) {
        if (response == null) {
            return false;
        }
        Integer code = response.getCode();
        String msg = response.getMsg();
        return Integer.valueOf(9999).equals(code)
                && (msg != null && (msg.contains("已支付成功") || msg.contains("请勿重复支付")));
    }
}

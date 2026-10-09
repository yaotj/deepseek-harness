package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** {@link RefundGatewayPort} 的唯一实现：退款方向出向 URL 与应答判读的收口点。 */
@Component
public class RefundGatewayAdapter implements RefundGatewayPort {

    private final PaySignProperties paySignProperties;
    private final PaySignGateway paySignGateway;

    public RefundGatewayAdapter(PaySignProperties paySignProperties, PaySignGateway paySignGateway) {
        this.paySignProperties = paySignProperties;
        this.paySignGateway = paySignGateway;
    }

    @Override
    public GatewayReply requestRefund(Map<String, Object> bizData) {
        return judge(paySignGateway.request(paySignProperties.getRequestRefundUrl(), bizData));
    }

    @Override
    public GatewayReply queryRefund(Map<String, Object> bizData) {
        return judge(paySignGateway.request(paySignProperties.getRefundQueryUrl(), bizData));
    }

    @Override
    public boolean refundQueryConfigured() {
        return StringUtils.hasText(paySignProperties.getRefundQueryUrl());
    }

    /**
     * §3.1 请求退款 bizData 的 {@code notifyUrl}（2026-09-22 新增，P1-3）。
     *
     * <p>**只读配置、不做兜底**：NEVER 退化成 {@code defaultNotifyUrl}（那是签约回调地址）
     * 或 {@code requestPayNotifyUrl}（那是支付回调地址）—— 三条回调的报文与处理分支完全不同，
     * 串了地址等于把退款结果送进支付回调解析链。
     */
    @Override
    public String refundNotifyUrl() {
        return paySignProperties.getRequestRefundNotifyUrl();
    }

    /** 成功码判定的唯一入口，NEVER 在本类重写一份（同 {@link ContractGatewayAdapter#judge}）。 */
    private GatewayReply judge(PaySignGatewayResponse response) {
        return paySignGateway.isSuccess(response)
                ? new GatewayReply.Accepted(response)
                : new GatewayReply.Rejected(response);
    }
}

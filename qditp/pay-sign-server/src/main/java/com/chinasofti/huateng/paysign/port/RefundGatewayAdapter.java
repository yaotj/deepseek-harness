package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@link RefundGatewayPort} 的唯一实现：退款方向出向 URL 与应答判读的收口点
 * （2026-09-16，ADR-D113 续；形态同 {@link ContractGatewayAdapter}）。
 *
 * <p><b>退款查询的字段名有实测结论，改动 MUST 先读 ADR-D92</b>：网关只认
 * {@code merchantRefundNo}，只送 {@code refundOrderNo} 时返 9999「退款流水号或商户退款流水号必填」，
 * 表现是退款单永久空转而端点每轮返 0000。那条约束落在调用点组装的 bizData 里，本类不改报文。
 */
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

    /** 成功码判定的唯一入口，NEVER 在本类重写一份（同 {@link ContractGatewayAdapter#judge}）。 */
    private GatewayReply judge(PaySignGatewayResponse response) {
        return paySignGateway.isSuccess(response)
                ? new GatewayReply.Accepted(response)
                : new GatewayReply.Rejected(response);
    }
}

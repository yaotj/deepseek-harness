package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.Map;
import org.springframework.util.StringUtils;

/** 免密扣款一次出向调用的判读结果（2026-09-16，ADR-D113 续）。 */
public sealed interface PaymentReply {

    /** 支付中心答成功。 */
    record Accepted(PaySignGatewayResponse raw) implements PaymentReply {
    }

    /** 幂等成功：{@code code=9999} + 措辞命中「已支付成功」/「请勿重复支付」。 */
    record AlreadyPaid(PaySignGatewayResponse raw) implements PaymentReply {
    }

    /** 真失败。 */
    record Rejected(PaySignGatewayResponse raw) implements PaymentReply {

        /** 同 {@code GatewayReply.Rejected#messageOr}：逐字复刻 {@code errorMessage} 语义。 */
        public String messageOr(String defaultMessage) {
            return raw == null || !StringUtils.hasText(raw.getMsg()) ? defaultMessage : raw.getMsg();
        }
    }

    PaySignGatewayResponse raw();

    /** {@code data} 节点，可能为 {@code null}。 */
    default Map<String, Object> data() {
        return raw() == null ? null : raw().getData();
    }

    /** 仅用于回填应答的 {@code success} 字段，NEVER 拿它做分支判断（分支 MUST 用模式匹配）。 */
    default boolean successful() {
        return this instanceof Accepted || this instanceof AlreadyPaid;
    }
}

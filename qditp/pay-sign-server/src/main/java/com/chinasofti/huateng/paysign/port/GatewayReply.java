package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.Map;
import org.springframework.util.StringUtils;

/** 支付中心一次出向调用的**应答判读结果**（2026-09-16，ADR-D112）。 */
public sealed interface GatewayReply {

    /** 支付中心答成功（{@code code=0}）。 */
    record Accepted(PaySignGatewayResponse raw) implements GatewayReply {

        /** {@code data} 节点，可能为 {@code null} —— 成功但无 data 是真实存在的形态。 */
        public Map<String, Object> data() {
            return raw == null ? null : raw.getData();
        }
    }

    /** 支付中心答了但不是成功码。 */
    record Rejected(PaySignGatewayResponse raw) implements GatewayReply {

        /** 对外文案：有对端 {@code msg} 就用它，否则用调用方给的语义化默认值。 */
        public String messageOr(String defaultMessage) {
            return raw == null || !StringUtils.hasText(raw.getMsg()) ? defaultMessage : raw.getMsg();
        }
    }

    /** 原始应答体；两个变体都可能为 {@code null}（对端完全没答时）。 */
    PaySignGatewayResponse raw();
}

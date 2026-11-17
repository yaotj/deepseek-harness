package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.Map;
import org.springframework.util.StringUtils;

public sealed interface GatewayReply {

    record Accepted(PaySignGatewayResponse raw) implements GatewayReply {

        public Map<String, Object> data() {
            return raw == null ? null : raw.getData();
        }
    }

    record Rejected(PaySignGatewayResponse raw) implements GatewayReply {

        public String messageOr(String defaultMessage) {
            return raw == null || !StringUtils.hasText(raw.getMsg()) ? defaultMessage : raw.getMsg();
        }
    }

    PaySignGatewayResponse raw();
}

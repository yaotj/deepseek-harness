package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.Map;
import org.springframework.util.StringUtils;

public sealed interface PaymentReply {

    record Accepted(PaySignGatewayResponse raw) implements PaymentReply {
    }

    record AlreadyPaid(PaySignGatewayResponse raw) implements PaymentReply {
    }

    record Rejected(PaySignGatewayResponse raw) implements PaymentReply {

        public String messageOr(String defaultMessage) {
            return raw == null || !StringUtils.hasText(raw.getMsg()) ? defaultMessage : raw.getMsg();
        }
    }

    PaySignGatewayResponse raw();

    default Map<String, Object> data() {
        return raw() == null ? null : raw().getData();
    }

    default boolean successful() {
        return this instanceof Accepted || this instanceof AlreadyPaid;
    }
}

package com.chinasofti.huateng.alipay.paysign.port;

import java.util.Map;


public sealed interface PayCenterReply {

    Integer code();

    Boolean success();

    String msg();

    String rawBody();


    record Accepted(Integer code, Boolean success, String msg, String rawBody,
                    String retCode, String retMsg, Map<String, Object> data) implements PayCenterReply {

        public String field(String key) {
            if (data == null) {
                return null;
            }
            Object value = data.get(key);
            return value == null ? null : value.toString();
        }
    }


    record Rejected(Integer code, Boolean success, String msg, String rawBody) implements PayCenterReply {
    }


    record NoAnswer(Integer code, Boolean success, String msg, String rawBody) implements PayCenterReply {

        public NoAnswer() {
            this(null, null, null, null);
        }
    }
}


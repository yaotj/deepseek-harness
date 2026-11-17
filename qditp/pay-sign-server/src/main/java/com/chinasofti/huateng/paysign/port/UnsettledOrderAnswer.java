package com.chinasofti.huateng.paysign.port;


public sealed interface UnsettledOrderAnswer {

    record Answered(boolean hasUnsettledOrder) implements UnsettledOrderAnswer {
    }

    record Rejected(String retCode, String retMsg) implements UnsettledOrderAnswer {
    }

    record Unknown(Throwable cause) implements UnsettledOrderAnswer {
    }
}

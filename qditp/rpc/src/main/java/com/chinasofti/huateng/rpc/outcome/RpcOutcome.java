package com.chinasofti.huateng.rpc.outcome;


public sealed interface RpcOutcome {

    String SUCCESS_CODE = "0000";

    record Ok() implements RpcOutcome {
    }


    record BizRejected(String retCode, String retMsg) implements RpcOutcome {
    }


    record Unreachable(Throwable cause) implements RpcOutcome {
    }

    default boolean isOk() {
        return this instanceof Ok;
    }


    static RpcOutcome ofRetCode(String retCode, String retMsg) {
        if (SUCCESS_CODE.equals(retCode)) {
            return new Ok();
        }
        return new BizRejected(retCode, retMsg);
    }
}

package com.chinasofti.huateng.rpc.outcome;

/**
 * 服务间调用的三态结果，替代本项目历来「返回 boolean 的 RPC 包装方法」。
 */
public sealed interface RpcOutcome {

    /** 本项目服务间调用统一的成功码。 */
    String SUCCESS_CODE = "0000";

    /** 对端已处理成功。 */
    record Ok() implements RpcOutcome {
    }

    /**
     * 对端答复了但业务拒绝，不可重试。
     * @param retCode 对端业务码，可能为 null（响应体缺该字段）
     * @param retMsg 对端文案，仅用于日志与工单。
     */
    record BizRejected(String retCode, String retMsg) implements RpcOutcome {
    }

    /**
     * 没拿到业务答复，可重试。
     * @param cause 原始异常。
     */
    record Unreachable(Throwable cause) implements RpcOutcome {
    }

    /** 是否成功。 */
    default boolean isOk() {
        return this instanceof Ok;
    }

    /**
     * 按对端返回的业务码归类。
     */
    static RpcOutcome ofRetCode(String retCode, String retMsg) {
        if (SUCCESS_CODE.equals(retCode)) {
            return new Ok();
        }
        return new BizRejected(retCode, retMsg);
    }
}

package com.chinasofti.huateng.paysign.port;

/**
 * 账户域**查询**调用的三分结果，是 {@code RpcOutcome} 在「读」方向上的对应物。
 *
 * @param <T> 本域真正需要的**窄视图**，NEVER 直接放 rpc 的查询 DTO ——
 */
public sealed interface AccountQuery<T> {

    /** 账户域答 {@code 0000} 且数据已收窄成本域视图。 */
    record Found<T>(T value) implements AccountQuery<T> {
    }

    /** 账户域**答了但不是成功**（如 {@code 8004} 该用户无此支付通道），**响应体为空也归这里**。 */
    record NotFound<T>(String retCode, String retMsg) implements AccountQuery<T> {
    }

    /** 压根没拿到业务答复：连不上、超时、HTTP 非 2xx。 */
    record Unreachable<T>(Throwable cause) implements AccountQuery<T> {
    }
}

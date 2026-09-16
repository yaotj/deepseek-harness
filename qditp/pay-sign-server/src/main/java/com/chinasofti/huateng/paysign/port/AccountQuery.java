package com.chinasofti.huateng.paysign.port;

/**
 * 账户域**查询**调用的三分结果，是 {@code RpcOutcome} 在「读」方向上的对应物。
 *
 * <p><b>为什么不直接复用 {@code RpcOutcome}</b>：那个类型只表达「成功 / 业务拒绝 / 不可达」，
 * 不携带查询结果。读调用必须把「答成功并且带回了数据」和「答了但拒绝」区分开，
 * 因此需要一个带载荷的三分类型。<b>NEVER 退化成 {@code Optional<T>}</b> ——
 * 那会把「业务拒绝」与「网络不可达」压成同一个 {@code empty}，而这两者的正确处置相反：
 * 前者重推一万次也不会变，后者才该重试。签约结果查询就依赖这个区分
 * （{@link NotFound} → {@code 0000/NOT_SIGNED}，{@link Unreachable} → {@code 9001}）。
 *
 * <p><b>调用点 MUST 用穷尽 switch 模式匹配</b>，少写一个分支直接编译失败 —— 这正是 ADR-D45
 * 引入 {@code RpcOutcome} 时想要的性质：不靠人记规则，靠编译器。
 *
 * <p>实现方（{@link AccountDomainRpcAdapter}）<b>NEVER 向外抛异常</b>，
 * 一切异常收成 {@link Unreachable} 并带上原始 cause。
 *
 * @param <T> 本域真正需要的**窄视图**，NEVER 直接放 rpc 的查询 DTO ——
 *            那等于把 rpc 契约换个地方再暴露一次，端口就白建了
 */
public sealed interface AccountQuery<T> {

    /** 账户域答 {@code 0000} 且数据已收窄成本域视图。 */
    record Found<T>(T value) implements AccountQuery<T> {
    }

    /**
     * 账户域**答了但不是成功**（如 {@code 8004} 该用户无此支付通道），**响应体为空也归这里**。
     *
     * <p>这是对端的正常业务答复、不是故障，<b>MUST NOT 重试</b>。
     * 保留 {@code retCode} / {@code retMsg} 供调用点记日志或原样透传。
     *
     * <p><b>为什么响应体为空算 NotFound 而不是 {@link Unreachable}</b>：本适配器的写方法
     * （{@code toOutcome}）一直把 {@code response == null} 收成 {@code BizRejected}，
     * 读侧沿用同一约定才不会出现「同一个类里空响应有两种含义」。
     * 语义上也说得通：HTTP 已 2xx、对端确实答了，只是答的内容我方无法采信。
     * <b>NEVER 改成 Unreachable</b> —— 钱包绑定状态查询会因此把「查不到」从
     * {@code 0000/NOT_SIGNED} 变成 {@code 9001}，属对外行为变更。
     */
    record NotFound<T>(String retCode, String retMsg) implements AccountQuery<T> {
    }

    /**
     * 压根没拿到业务答复：连不上、超时、HTTP 非 2xx。
     *
     * <p>调用点据此与 {@link NotFound} 区分处置 —— 签约结果查询把它映射成 {@code 9001}，
     * 而 {@link NotFound} 映射成 {@code 0000/NOT_SIGNED}。
     */
    record Unreachable<T>(Throwable cause) implements AccountQuery<T> {
    }
}

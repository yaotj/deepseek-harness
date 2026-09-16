package com.chinasofti.huateng.rpc.outcome;

/**
 * 服务间调用的三态结果，替代本项目历来「返回 boolean 的 RPC 包装方法」。
 *
 * <p><b>为什么不是 boolean</b>：AGENTS.md §5.2 为「内部 catch 全部异常后 return false、从不抛异常」
 * 的包装方法专门写了一条规则，而它被违反过两次并双双造成生产不一致
 * （见 {@code docs/domain/decisions.md} ADR-D13）。规则靠人记、boolean 靠人查；
 * sealed + record 让穷尽性由**编译器**检查——调用点少写一个分支就编译失败。</p>
 *
 * <p><b>真正的收益是「业务拒绝」与「网络不可达」终于可区分</b>，两者在 boolean 下都是 false：</p>
 * <ul>
 *   <li>{@link BizRejected} —— 对端答复了、但业务上拒绝（如「该用户在支付域没有签约记录」，
 *       表现为 UPDATE 影响 0 行后返 FAIL）。<b>重推一万次也不会成功</b>，MUST 直接转终态 / 工单，
 *       NEVER 放进补偿队列反复重试。</li>
 *   <li>{@link Unreachable} —— 请求没能拿到业务答复（连不上、超时、HTTP 4xx/5xx）。
 *       这才是补偿队列该收的那一类。</li>
 * </ul>
 *
 * <p><b>HTTP 4xx 归到 {@code Unreachable} 是有意的保守选择</b>：`ProxyWebClient.handleResponse`
 * （`resource/micro/web/.../client/ProxyWebClient.java:138`）把 `createError()` 产生的
 * `WebClientResponseException` 与连接失败一起包成 `RuntimeException`，包装方法拿不到可靠的区分依据。
 * 误判方向 MUST 是「把永久失败当成可重试」（代价是白重试几轮），
 * <b>NEVER 反过来</b>——把网络抖动当成业务拒绝会让一笔真实待投递的事实被直接判死。</p>
 *
 * <p><b>本类型 NEVER 参与序列化</b>：它是进程内的调用结果，不是报文 DTO，因此放在 `rpc` 而不是
 * `model`。跨服务的报文契约仍是各 `*Result` / `*RespDTO`。</p>
 *
 * <p><b>落地方式是「新增方法、老方法保留」</b>：`rpc` 版本号锁死在 2.0.1、被 21 个模块引用
 * （AGENTS.md §7），因此 MUST 只增不改签名，逐调用点迁移。</p>
 */
public sealed interface RpcOutcome {

    /** 本项目服务间调用统一的成功码。 */
    String SUCCESS_CODE = "0000";

    /** 对端已处理成功。 */
    record Ok() implements RpcOutcome {
    }

    /**
     * 对端答复了但业务拒绝，<b>不可重试</b>。
     *
     * @param retCode 对端业务码，可能为 null（响应体缺该字段）
     * @param retMsg  对端文案，仅用于日志与工单，NEVER 用它做分支判断
     */
    record BizRejected(String retCode, String retMsg) implements RpcOutcome {
    }

    /**
     * 没拿到业务答复，<b>可重试</b>。
     *
     * @param cause 原始异常，MUST 保留以便日志带栈；调用方 NEVER 只打 {@code getMessage()}
     */
    record Unreachable(Throwable cause) implements RpcOutcome {
    }

    /** 是否成功。<b>只用于日志与计数，分支判断 MUST 用 switch 模式匹配</b>，否则又退回 boolean。 */
    default boolean isOk() {
        return this instanceof Ok;
    }

    /**
     * 按对端返回的业务码归类。
     *
     * <p>响应体为空、或 {@code retCode} 缺失，都算 {@link BizRejected} 而不是 {@link Unreachable}
     * —— 那种情况下 HTTP 已经 2xx，是对端契约问题，重推同一报文不会变好。</p>
     */
    static RpcOutcome ofRetCode(String retCode, String retMsg) {
        if (SUCCESS_CODE.equals(retCode)) {
            return new Ok();
        }
        return new BizRejected(retCode, retMsg);
    }
}

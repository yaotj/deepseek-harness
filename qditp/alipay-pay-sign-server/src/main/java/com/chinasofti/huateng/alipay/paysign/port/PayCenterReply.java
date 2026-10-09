package com.chinasofti.huateng.alipay.paysign.port;

import java.util.Map;

/**
 * 支付中心（bestonepay）业务方向的应答，ADR-D131。
 *
 * <p>只服务**支付 / 退款 / 支付查询 / 退款查询**四个方向。这几处的传输层判据在改造前就是**逐字相同**的
 * （{@code code == 200 || success == TRUE}，随后解开 data 看业务 {@code retCode}、
 * {@code returnCode} 兜底），因此收口到一个类型**不构成任何判定语义的归一**。</p>
 *
 * <p><b>NEVER 把通知方向也塞进来</b>：{@code closeResultNotify} 只认 {@code code == 200}、
 * {@code blacklistNotify} 认 {@code success || retCode=="0000" || code==200} 三者任一 ——
 * 那两条判据互不相同、且与本类型不同，它们已收口在 {@code PaymentNotifyAdapter}。
 * 三套判据的差异是**既有现状**，上线前 MUST 向供方实测确认，NEVER 擅自归一。</p>
 *
 * <p>为什么不复用 {@code RpcOutcome}：调用点不只要「成没成」，还要带回 data 里的
 * {@code channelOrderNo} / {@code tradeNo} / {@code totalAmount} 等字段，
 * 且失败分支要把传输层的 {@code code} / {@code msg} / 原始响应体落库留证。
 * 按 AGENTS.md 的三档判据，这属于「要带回数据」那一档，MUST 自建 sealed interface。</p>
 */
public sealed interface PayCenterReply {

    /** 传输层 HTTP code，响应为空时是 {@code null}。 */
    Integer code();

    /** 传输层 success 标志，响应为空时是 {@code null}。 */
    Boolean success();

    /** 传输层 msg，业务失败时被多处当作文案兜底。 */
    String msg();

    /** 原始响应体 JSON，退款明细要把它整段落库留证，MUST 保留。 */
    String rawBody();

    /**
     * 传输层与网关层都通了，data 已解开一次。
     *
     * <p>{@code retCode} 已做过 {@code retCode -> returnCode} 兜底，{@code retMsg} 同理；
     * 业务成功与否由调用点自己判 {@code "SUCCESS".equals(retCode())}，
     * <b>本类型刻意不提供 {@code isSuccess()}</b> —— 支付、退款、查询三处对失败的处置完全不同
     * （加黑名单 / 落 FAIL / 回写 payStatus），把判定藏进类型只会掩盖差异。</p>
     */
    record Accepted(Integer code, Boolean success, String msg, String rawBody,
                    String retCode, String retMsg, Map<String, Object> data) implements PayCenterReply {

        /** 取 data 里的字符串字段；data 已在 adapter 内解开一次，这里不再重复 Base64 + JSON 解析。 */
        public String field(String key) {
            if (data == null) {
                return null;
            }
            Object value = data.get(key);
            return value == null ? null : value.toString();
        }
    }

    /**
     * 拿到响应了，但传输层判据不成立（{@code code != 200} 且 {@code success != TRUE}）。
     *
     * <p>与 {@link NoAnswer} 分开是因为 {@code requestPay} 对两者的处置**本来就不同**：
     * 本分支返 {@code FAIL} + 网关 msg，{@code NoAnswer} 返 {@code SYSTEM_ERROR} + 「调用支付中心失败」。
     * 退款与查询两处对两个分支的处置相同，但仍 MUST 各写一个 case ——
     * 合成一个分支就把「支付申请那处的差异」抹掉了。</p>
     *
     * <p>三处的共同现状：本分支 <b>NEVER 加黑名单</b>（拿不到业务应答不等于扣款被拒）。</p>
     */
    record Rejected(Integer code, Boolean success, String msg, String rawBody) implements PayCenterReply {
    }

    /**
     * 响应为 {@code null}：地址没配、{@code IOException}、非 2xx 都会落到这里。
     *
     * <p>{@code PayCenterClient.callPayCenter} 把这三种成因**全部吞成同一个 null**，
     * 因此本分支拿不到任何可区分的信息（四个字段全 {@code null}）。
     * 这是既有缺陷、列进批次 6 待办，<b>NEVER 在本类型上加字段假装能区分</b>。</p>
     */
    record NoAnswer(Integer code, Boolean success, String msg, String rawBody) implements PayCenterReply {

        public NoAnswer() {
            this(null, null, null, null);
        }
    }
}


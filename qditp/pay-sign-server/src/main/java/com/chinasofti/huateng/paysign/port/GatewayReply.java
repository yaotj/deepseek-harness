package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 支付中心一次出向调用的**应答判读结果**（2026-09-16，ADR-D112）。
 *
 * <p><b>与 {@link AccountQuery} / {@code RpcOutcome} 同一条路子</b>：把「成功 / 拒绝」做成类型，
 * 让调用点用模式匹配处置，而不是各自 {@code if (!gateway.isSuccess(x))} 一遍。
 * 收口前「取 URL + 组 bizData + 出网 + 判读」这套三件套在 <b>9 处</b>逐行重复
 * （Contract 5 / Payment 2 / Refund 2），而 {@code isSuccess} 更是散在 <b>5 个类</b>里。
 *
 * <p><b>刻意只有两个变体，没有 {@code Unreachable}</b>：{@code PayGatewayClient.request} 现在
 * 把网络异常兜成一个 {@code code!=0} 的应答（不抛），因此「不可达」在本层<b>不可观测</b>。
 * 硬造第三个变体会让调用点以为自己能区分，而实际区分不了 —— 那比没有更糟。
 * 要真正区分 MUST 先改 {@code PayGatewayClient}，那是另一件事。
 *
 * <p><b>{@link #raw()} 刻意保留</b>：`requestPayPlatformTermination` /
 * `queryPayPlatformContractStatus` 两个内部方法的签名是 {@code PaySignGatewayResponse}，
 * 调用方（`TerminationExecutor` / `TerminationProcessor`）自己判读。那两处属另一批次，
 * 本批次不动它们的契约，所以本类型 MUST 能原样交出应答体。
 */
public sealed interface GatewayReply {

    /** 支付中心答成功（{@code code=0}）。 */
    record Accepted(PaySignGatewayResponse raw) implements GatewayReply {

        /** {@code data} 节点，可能为 {@code null} —— 成功但无 data 是真实存在的形态。 */
        public Map<String, Object> data() {
            return raw == null ? null : raw.getData();
        }
    }

    /**
     * 支付中心答了但不是成功码。
     *
     * <p>包含「业务拒绝」与「网关自身异常被兜成失败码」两种，本层分不开（见类注释）。
     */
    record Rejected(PaySignGatewayResponse raw) implements GatewayReply {

        /**
         * 对外文案：有对端 {@code msg} 就用它，否则用调用方给的语义化默认值。
         *
         * <p><b>逐字复刻 {@code PayGatewayClient.errorMessage} 的语义</b>，因为默认文案按调用点不同
         * （「请求支付签约咨询接口失败」/「request pay contract result failed」…），
         * NEVER 收成一句统一文案 —— 联调方可能已按文案断言。
         */
        public String messageOr(String defaultMessage) {
            return raw == null || !StringUtils.hasText(raw.getMsg()) ? defaultMessage : raw.getMsg();
        }
    }

    /** 原始应答体；两个变体都可能为 {@code null}（对端完全没答时）。 */
    PaySignGatewayResponse raw();
}

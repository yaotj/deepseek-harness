package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 免密扣款一次出向调用的判读结果（2026-09-16，ADR-D113 续）。
 *
 * <p><b>为什么不复用 {@link GatewayReply}</b>：扣款方向有**第三种**结局 ——
 * {@link AlreadyPaid}（网关答 {@code code=9999} 但 msg 说「已支付成功 / 请勿重复支付」，
 * 业务上其实成功）。把它塞进 {@code GatewayReply} 的两分类，会让签约方向那 4 处
 * {@code instanceof Rejected} 的语义**悄悄变化**：一笔已扣款成功的交易会落进 Rejected。
 * <b>NEVER 合并这两个类型</b>；端口按用例设计，回复类型也按用例设计。
 */
public sealed interface PaymentReply {

    /** 支付中心答成功。 */
    record Accepted(PaySignGatewayResponse raw) implements PaymentReply {
    }

    /**
     * 幂等成功：{@code code=9999} + 措辞命中「已支付成功」/「请勿重复支付」。
     *
     * <p>判定口径由 {@code PaySignGateway.isAlreadyPaidSuccess} 独占（它是对支付中心
     * **应答措辞**的硬编码约定），<b>NEVER 在别处重写一份</b> —— 散开后措辞一变，
     * 改一处漏两处不会编译失败，只会让已扣款成功的交易被判成失败、进而走到拉黑分支。
     */
    record AlreadyPaid(PaySignGatewayResponse raw) implements PaymentReply {
    }

    /** 真失败。 */
    record Rejected(PaySignGatewayResponse raw) implements PaymentReply {

        /** 同 {@code GatewayReply.Rejected#messageOr}：逐字复刻 {@code errorMessage} 语义。 */
        public String messageOr(String defaultMessage) {
            return raw == null || !StringUtils.hasText(raw.getMsg()) ? defaultMessage : raw.getMsg();
        }
    }

    PaySignGatewayResponse raw();

    /** {@code data} 节点，可能为 {@code null}。 */
    default Map<String, Object> data() {
        return raw() == null ? null : raw().getData();
    }

    /** 仅用于回填应答的 {@code success} 字段，NEVER 拿它做分支判断（分支 MUST 用模式匹配）。 */
    default boolean successful() {
        return this instanceof Accepted || this instanceof AlreadyPaid;
    }
}

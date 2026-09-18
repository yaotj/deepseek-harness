package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import org.springframework.util.StringUtils;

/**
 * 一次支付结果回调的**解析结果**：值对象与校验结论合成一个 sealed 类型。
 *
 * <p>为什么不是「record + 校验失败抛异常」：本链路的两种非法形态**处置完全不同** ——
 * `orderNo` 空时连凭据都不落（没有业务键，落进去也无法关联），而 `transStatus` 非法时
 * <b>MUST 落一行 {@code MANUAL} 凭据</b>（报文本身是可举证的事实，人工核对契约要靠它）。
 * 抛同一个异常会把这个差异抹掉；返回 {@code null} 则迫使调用点再判一次 {@code transStatus}，
 * 判断逻辑就散成两处。做成穷尽 {@code switch} 的三支，<b>少写一支直接编译失败</b>。
 *
 * <p>{@code transStatus} 白名单只认契约里的 {@code 1} 成功 / {@code 2} 失败，<b>NEVER 改成
 * 「等于 1 就成功、否则失败」</b> —— 那样对端送任何脏值都会被当成扣款失败，而扣款失败是要加黑名单、
 * 要让 gate-txn-pay 把订单收敛成终态的，方向反了没人能发现。
 */
sealed interface PayNotifyCommand {

    /** 契约 §5.1 的 {@code transStatus}：成功。 */
    String TRANS_STATUS_SUCCESS = "1";
    /** 契约 §5.1 的 {@code transStatus}：失败。 */
    String TRANS_STATUS_FAIL = "2";

    /**
     * 报文合法，可以继续处理。
     *
     * @param rawTransStatus 对端原值，只用于留证与日志
     * @param payStatus      映射后的本地口径（{@code SUCCESS} / {@code FAIL}），出网同步时用它
     */
    record Accepted(String orderNo,
                    String rawTransStatus,
                    String payStatus,
                    String channelVoucherId,
                    String transAmount,
                    String transTime) implements PayNotifyCommand {
    }

    /** 报文为空或 {@code orderNo} 为空：**不落凭据**，直接拒。 */
    record MissingOrderNo() implements PayNotifyCommand {
    }

    /** {@code transStatus} 不在白名单内：**MUST 落一行 MANUAL 凭据**再拒。 */
    record IllegalTransStatus(String orderNo, String rawTransStatus) implements PayNotifyCommand {
    }

    static PayNotifyCommand from(AlipayTripPayNotifyReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return new MissingOrderNo();
        }
        String payStatus = mapTransStatus(request.getTransStatus());
        if (payStatus == null) {
            return new IllegalTransStatus(request.getOrderNo(), request.getTransStatus());
        }
        return new Accepted(request.getOrderNo(), request.getTransStatus(), payStatus,
                request.getChannelVoucherId(), request.getTransAmount(), request.getTransTime());
    }

    /** 白名单映射，其余一律 {@code null}（= 非法）。 */
    private static String mapTransStatus(String transStatus) {
        if (TRANS_STATUS_SUCCESS.equals(transStatus)) {
            return "SUCCESS";
        }
        if (TRANS_STATUS_FAIL.equals(transStatus)) {
            return "FAIL";
        }
        return null;
    }
}

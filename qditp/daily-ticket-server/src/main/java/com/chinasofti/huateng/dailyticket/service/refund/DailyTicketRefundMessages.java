package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 退款发起侧共用的无状态判定与应答组装。
 *
 * <p>退款六个入口（发起 / 回查 / 重试 / 重提交 / 旅游票整单 / 旅游票子单）都要用这三件事：
 * 生成退款流水号、把「已存在的退款单」翻译成对上游的应答、判断网关应答是否算成功。
 * 逐个入口搬家时若不先收口，这里就会出现 6 份逐字副本。
 *
 * <p><b>只准放无状态纯函数</b>：不注入 mapper、不碰库。
 */
public final class DailyTicketRefundMessages {

    private DailyTicketRefundMessages() {
    }

    /**
     * 退款流水号：{@code RF} + 毫秒时间戳 + 原订单号后 8 位。
     * 这是我方送给支付中心的 {@code refundOrderNo}（对方回调里叫 {@code outRefundNo}），
     * <b>改格式会让存量退款单与回调对不上，NEVER 改</b>。
     */
    public static String buildRefundOrderNo(String orderNo) {
        String suffix = orderNo;
        if (suffix != null && suffix.length() > 8) {
            suffix = suffix.substring(suffix.length() - 8);
        }
        return "RF" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date())
                + (suffix == null ? "" : suffix);
    }

    /**
     * 已存在退款单时的幂等应答：**一律返成功码**，把真实进度放在 {@code refundResult} 里。
     *
     * <p>NEVER 改成返失败 —— 运营重复点「退款」或 APP 重复提交都会走到这里，
     * 返失败会让前台以为可以再发起一笔，而退款单号已经占用。
     */
    public static DailyTicketRefundResult buildExistingRefundResult(DailyTicketRefundResult result,
                                                                    DailyTicketRefund refund) {
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundDate(refund.getRefundDate() == null ? null
                : new SimpleDateFormat("yyyyMMddHHmmss").format(refund.getRefundDate()));
        if ("REFUNDED".equals(refund.getRefundStatus())) {
            result.setRefundResult("SUCCESS");
            result.setRefundResultDesc("退款已完成");
        } else if ("FAILED".equals(refund.getRefundStatus())) {
            result.setRefundResult("FAILED");
            result.setRefundResultDesc("退款失败，可发起退款重试");
        } else {
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已申请，请勿重复提交");
        }
        return DailyTicketOrderSupport.success(result);
    }

    /**
     * 判定异常是否为 {@code UK_DAILY_TICKET_REFUND_ORDER} 唯一索引冲突（同一订单已有退款单）。
     *
     * <p><b>MUST 沿 {@code getCause()} 链逐层判定，NEVER 只看最外层类名</b>：本模块已打开
     * {@code management.tracing.enabled}，{@code resource/micro/web} 的观测切面会把异常重新包一层，
     * 只 {@code catch (DuplicateKeyException)} 的写法在本模块**会静默落空**，退化成对上游报 UUID retCode。
     * 同理也接住 {@code DataIntegrityViolationException} —— Oracle 驱动 + Spring 转译在不同路径下
     * 给出的类型并不固定。
     *
     * <p>收口在本类是因为退款发起与「取消单自动退款」两条链路都要用它，
     * 各写一份私有方法就是逐字副本。
     */
    public static boolean isDuplicateRefund(Throwable e) {
        Throwable cursor = e;
        int depth = 0;
        while (cursor != null && depth < 16) {
            if (cursor instanceof org.springframework.dao.DuplicateKeyException
                    || cursor instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
            cursor = cursor.getCause();
            depth++;
        }
        return false;
    }

    /**
     * 网关成功判据：{@code success=true} 或 {@code code} 为 {@code 0} / {@code 200} 三者任一。
     * 支付中心不同接口返的成功码不统一（实测既有 0 也有 200），**NEVER 收窄成只认一个**。
     */
    public static boolean isGatewaySuccess(DailyTicketPayGatewayResponse response) {
        return response != null && (Boolean.TRUE.equals(response.getSuccess())
                || Integer.valueOf(0).equals(response.getCode())
                || Integer.valueOf(200).equals(response.getCode()));
    }

    /**
     * 网关**明确失败**判据，与 {@link #isGatewaySuccess} 不是互为取反：
     * {@code response == null}（超时 / 网络异常）属「结果未知」，两个方法都返 {@code false}。
     * 这个三态区分是退款不敢乱回滚的前提，**NEVER 合并成一个方法**。
     */
    public static boolean isGatewayExplicitFailure(DailyTicketPayGatewayResponse response) {
        if (response == null) {
            return false;
        }
        if (Boolean.FALSE.equals(response.getSuccess())) {
            return true;
        }
        return response.getCode() != null && !Integer.valueOf(0).equals(response.getCode())
                && !Integer.valueOf(200).equals(response.getCode());
    }
}

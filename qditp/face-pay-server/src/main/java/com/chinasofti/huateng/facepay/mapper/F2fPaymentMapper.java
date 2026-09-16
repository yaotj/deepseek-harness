package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付交互流水 Mapper（表 F2F_PAYMENT），一行一次尝试。
 *
 * <p>本表三条约束决定了这里的方法形态，改这个接口前 MUST 先读：
 * <ul>
 *   <li><b>插入一次尝试只 INSERT，不先查后插。</b>同一订单内 ATTEMPT_NO 由
 *       {@code UK_F2F_PAY_ATTEMPT (ORDER_NO, ATTEMPT_NO)} 保证唯一，
 *       并发下重复序号抛 {@code DuplicateKeyException}，由 application 层重取序号或幂等返回。</li>
 *   <li><b>标记成功靠 UK_F2F_PAY_SUCCESS 兜底。</b>该索引是函数索引
 *       {@code CASE WHEN PAY_STATUS='SUCCESS' THEN ORDER_NO ELSE NULL END}，
 *       一笔订单最多只能有一条 SUCCESS 记录。因此
 *       {@link #markSuccess} 直接条件更新，NEVER 先查有没有成功记录再更新——并发下无效，
 *       第二条 SUCCESS 会被唯一索引挡住并抛 {@code DuplicateKeyException}，
 *       调用方 MUST 把该异常按「已有成功记录」处理。</li>
 *   <li><b>状态推进走白名单。</b>{@link #markSuccess} / {@link #markFinalStatus} 的
 *       前置状态必填，NEVER 写成「非终态即可更新」。</li>
 * </ul>
 *
 * <p><b>UNKNOWN 的含义：</b>PAY_STATUS=UNKNOWN 表示对端未明确应答，
 * 需靠查询接口收口，<b>不得直接判失败</b>。因此 UNKNOWN 在
 * {@link #markSuccess} / {@link #markFinalStatus} 的前置白名单里是允许状态，
 * 由收口任务据查询结果推进到 SUCCESS 或 FAILED。
 */
@Mapper
public interface F2fPaymentMapper {

    /**
     * 插入一次支付尝试。ATTEMPT_NO 由调用方基于 {@link #selectMaxAttemptNo} 加 1 生成，
     * 重复由 UK_F2F_PAY_ATTEMPT 抛 DuplicateKeyException，本方法不做判重。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fPayment payment);

    /**
     * 按订单号取最后一次尝试（ATTEMPT_NO 最大的一行），命中 IDX_F2F_PAY_ORDER。
     *
     * <p>SQL 带显式 ORDER BY ATTEMPT_NO DESC + FETCH FIRST 1 ROWS ONLY，
     * 只取一行，避免多笔命中抛 TooManyResultsException。
     *
     * @return 最后一次尝试；订单尚未发起过支付时为 null
     */
    F2fPayment selectLastAttempt(@Param("orderNo") String orderNo);

    /**
     * 按订单号取全部尝试，按 ATTEMPT_NO 升序，供排障与对账逐次回溯。
     *
     * @return 尝试列表；无记录时为空列表，不为 null
     */
    List<F2fPayment> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按支付中心订单号查询，供支付结果回调反查本次尝试，命中 IDX_F2F_PAY_CENTER_NO。
     *
     * @return 对应的尝试行；回调携带的订单号在本地无记录时为 null，
     *         调用方 MUST 据此拒绝未知回调而非新建记录
     */
    F2fPayment selectByPayCenterOrderNo(@Param("payCenterOrderNo") String payCenterOrderNo);

    /**
     * 把某次尝试标记为支付成功：PAY_STATUS 置 SUCCESS 并回填终态字段。
     *
     * <p>前置状态白名单为 INIT / PROCESSING / UNKNOWN，其中 UNKNOWN 是「对端未明确应答」，
     * 允许被查询接口收口成 SUCCESS。并发下的第二条 SUCCESS 由 UK_F2F_PAY_SUCCESS 挡住，
     * 抛 DuplicateKeyException，调用方 MUST 当作「该订单已有成功记录」幂等处理。
     *
     * @param payChannelCode 支付方式（渠道码，对端 {@code paymentVendor}）。
     *                       该值只在支付成功的回调或查询结果里才拿得到，因此在这里回填；
     *                       传 null 时 SQL 用 NVL 保留原值，NEVER 因为一次没带就把已有渠道码抹掉。
     *                       查询接口回吐的 {@code paymentChannelCode} 取自本列。
     * @return 影响行数；<b>0 表示该尝试当前状态不在白名单内</b>（已是 SUCCESS / FAILED 或行不存在），
     *         调用方 MUST 据此判断并停止后续业务推进，NEVER 忽略返回值
     */
    int markSuccess(@Param("orderNo") String orderNo,
                    @Param("attemptNo") Integer attemptNo,
                    @Param("payCenterOrderNo") String payCenterOrderNo,
                    @Param("channelOrderNo") String channelOrderNo,
                    @Param("payChannelCode") String payChannelCode,
                    @Param("retCode") String retCode,
                    @Param("retMsg") String retMsg,
                    @Param("finishTms") LocalDateTime finishTms);

    /**
     * 把某次尝试更新为终态（FAILED，或由 PROCESSING 转 UNKNOWN 等待收口）。
     *
     * <p>{@code fromStatuses} 必填白名单，NEVER 传空集合（空集合会生成 IN () 让 Oracle 直接报错，
     * 这比静默放行所有状态安全）。NEVER 把 UNKNOWN 直接改成 FAILED——
     * UNKNOWN 表示对端未明确应答，MUST 先经支付中心查询接口确认。
     *
     * @param fromStatuses 允许的前置状态白名单
     * @param toStatus     目标状态，取值受 CK_F2F_PAY_STATUS 约束
     * @return 影响行数；<b>0 表示前置状态不满足或该尝试不存在</b>，调用方 MUST 据此拒绝
     */
    int markFinalStatus(@Param("orderNo") String orderNo,
                        @Param("attemptNo") Integer attemptNo,
                        @Param("fromStatuses") List<String> fromStatuses,
                        @Param("toStatus") String toStatus,
                        @Param("retCode") String retCode,
                        @Param("retMsg") String retMsg,
                        @Param("costMs") Integer costMs,
                        @Param("finishTms") LocalDateTime finishTms);

    /**
     * 查某订单当前最大 ATTEMPT_NO，供生成下一个尝试序号，命中 IDX_F2F_PAY_ORDER。
     *
     * @return 当前最大序号；<b>该订单尚无任何尝试时返回 null</b>，
     *         调用方据此从 1 开始。序号仅作建议值，最终唯一性由 UK_F2F_PAY_ATTEMPT 保证
     */
    Integer selectMaxAttemptNo(@Param("orderNo") String orderNo);
}

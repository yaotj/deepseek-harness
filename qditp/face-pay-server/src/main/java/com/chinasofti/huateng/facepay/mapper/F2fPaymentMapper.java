package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 支付交互流水 Mapper（表 F2F_PAYMENT），一行一次尝试。 */
@Mapper
public interface F2fPaymentMapper {

    /**
     * 插入一次支付尝试。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fPayment payment);

    /**
     * 按订单号取最后一次尝试（ATTEMPT_NO 最大的一行），命中 IDX_F2F_PAY_ORDER。
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
     */
    F2fPayment selectByPayCenterOrderNo(@Param("payCenterOrderNo") String payCenterOrderNo);

    /**
     * 把某次尝试标记为支付成功：PAY_STATUS 置 SUCCESS 并回填终态字段。
     *
     * @param payChannelCode 支付方式（渠道码，对端 {@code paymentVendor}）。
     * @return 影响行数；0 表示该尝试当前状态不在白名单内（已是 SUCCESS / FAILED 或行不存在），
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
     * @param fromStatuses 允许的前置状态白名单
     * @param toStatus     目标状态，取值受 CK_F2F_PAY_STATUS 约束
     * @return 影响行数；0 表示前置状态不满足或该尝试不存在，调用方 MUST 据此拒绝
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
     * @return 当前最大序号；该订单尚无任何尝试时返回 null，
     */
    Integer selectMaxAttemptNo(@Param("orderNo") String orderNo);
}

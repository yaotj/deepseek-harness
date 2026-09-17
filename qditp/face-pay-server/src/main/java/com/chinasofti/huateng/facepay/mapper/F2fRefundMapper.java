package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 退款单 Mapper（表 F2F_REFUND）。 */
@Mapper
public interface F2fRefundMapper {

    /**
     * 插入退款单。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fRefund refund);

    /**
     * 按退款单号查询，命中 UK_F2F_REFUND_NO。
     *
     * @return 退款单；null 表示该退款单号不存在
     */
    F2fRefund selectByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 按原订单号查出该订单的全部退款单，命中 IDX_F2F_REFUND_ORIG。
     *
     * @return 退款单列表，按发起时间升序；无退款时为空列表
     */
    List<F2fRefund> selectByOrigOrderNo(@Param("origOrderNo") String origOrderNo);

    /**
     * 按支付中心退款单号查询，供退款回调反查本地单，命中 IDX_F2F_REFUND_CENTER。
     *
     * @return 退款单；null 表示回调带来的单号在本地不存在，调用方 MUST 拒绝并留痕
     */
    F2fRefund selectByPayCenterRefundNo(@Param("payCenterRefundNo") String payCenterRefundNo);

    /**
     * 退款状态推进。
     *
     * @param fromStatuses 允许的前置状态，NEVER 传空集合
     * @param payCenterRefundNo 支付中心退款单号，传 null 时不覆盖原值
     * @param finishTms 收口时间，进终态时传值，非终态可传 null
     * @return 影响行数；0 表示前置状态不满足（已被其他线程推进或状态非法），
     */
    int updateStatus(@Param("refundNo") String refundNo,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus,
                     @Param("payCenterRefundNo") String payCenterRefundNo,
                     @Param("finishTms") LocalDateTime finishTms);

    /**
     * 扫出到点该查的退款单，供 {@code @Scheduled} 补偿任务使用。
     *
     * @param statuses 需要收口的状态白名单，通常为 INIT / PROCESSING，NEVER 传空集合
     * @param now 当前时刻，只捞 NEXT_QUERY_TMS 不晚于此刻的单；退避未到点的单本轮不动
     * @param limit 单批条数上限
     * @return 待收口退款单列表；空列表表示本轮无到点的单
     */
    List<F2fRefund> selectRetryCandidates(@Param("statuses") List<String> statuses,
                                          @Param("now") LocalDateTime now,
                                          @Param("limit") int limit);

    /**
     * 累加 RETRY_TIMES、记录本次失败原因，并把下次查询时刻推到退避之后。
     *
     * @param failReason 本次失败原因，覆盖旧值
     * @param nextQueryTms 下次允许查询的时刻，由调用方按指数退避算出，NEVER 传 null
     * @return 影响行数；0 表示 refundNo 不存在
     */
    int increaseRetryTimes(@Param("refundNo") String refundNo,
                           @Param("failReason") String failReason,
                           @Param("nextQueryTms") LocalDateTime nextQueryTms);
}

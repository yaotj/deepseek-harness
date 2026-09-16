package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单 Mapper（表 F2F_REFUND）。
 *
 * <p><b>本表最关键的约束：退款防重靠唯一函数索引，NEVER 靠先查后插。</b>
 * {@code UK_F2F_REFUND_IDEM (ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'), REFUND_SOURCE)}
 * 表达「同一原订单 + 同一票 + 同一退款来源只能有一条退款单」；整单退时
 * {@code TICKET_LOGIC_NUM} 为空，索引用 {@code #WHOLE#} 占位，避免多笔整单退绕过约束。
 *
 * <p>因此 {@link #insert} MUST 直接 INSERT。并发下「先 SELECT 判断是否已退过再 INSERT」
 * 无效——两个线程都查不到就都插进去，等于重复退款，这是旧实现的最高优先级缺陷。
 * 重复插入由唯一索引抛 {@code DuplicateKeyException}，application 层捕获后按「已退过」
 * 处理（回查已有退款单幂等返回），NEVER 在 SQL 里做判重。
 *
 * <p>另两条：
 * <ul>
 *   <li><b>状态推进走白名单。</b>{@link #updateStatus} 的 {@code fromStatuses} 必填，
 *       NEVER 写成「非终态即可更新」。</li>
 *   <li><b>扫表重试命中 IDX_F2F_REFUND_SCAN (REFUND_STATUS, NEXT_QUERY_TMS)。</b>
 *       {@link #selectRetryCandidates} 的 WHERE 与 ORDER BY 保持这个列序，改动前先看索引。
 *       谓词里 <b>NEVER 再加 RETRY_TIMES 上限</b>——次数上限确实存在，但它在 application 层
 *       判定并显式置 MANUAL；写进 SQL 会让次数用尽的单直接从扫描结果消失、
 *       停在非终态且无人知晓。</li>
 * </ul>
 */
@Mapper
public interface F2fRefundMapper {

    /**
     * 插入退款单。
     *
     * <p>直接 INSERT，不做任何判重：重复的「原订单 + 票 + 来源」组合由
     * UK_F2F_REFUND_IDEM 抛 {@code DuplicateKeyException}，重复的 refundNo 由
     * UK_F2F_REFUND_NO 抛同一异常，均由 application 层捕获后按「已退过」处理。
     * NEVER 改成先 SELECT 再 INSERT。
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
     * 一个订单可能有多条（按票退 + 不同来源），调用方 MUST 自行按业务口径筛选。
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
     * 退款状态推进。仅当当前 REFUND_STATUS 在 fromStatuses 白名单内才更新；
     * 目标状态为 SUCCESS / FAILED 时同时回填 FINISH_TMS。
     *
     * @param fromStatuses 允许的前置状态，NEVER 传空集合
     * @param payCenterRefundNo 支付中心退款单号，传 null 时不覆盖原值
     * @param finishTms 收口时间，进终态时传值，非终态可传 null
     * @return 影响行数；0 表示前置状态不满足（已被其他线程推进或状态非法），
     *         调用方 MUST 据此判断并拒绝，NEVER 忽略返回值
     */
    int updateStatus(@Param("refundNo") String refundNo,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus,
                     @Param("payCenterRefundNo") String payCenterRefundNo,
                     @Param("finishTms") LocalDateTime finishTms);

    /**
     * 扫出到点该查的退款单，供 {@code @Scheduled} 补偿任务使用。
     * WHERE 用 REFUND_STATUS + NEXT_QUERY_TMS，命中 IDX_F2F_REFUND_SCAN；
     * 显式 ORDER BY NEXT_QUERY_TMS 是 FETCH FIRST 的前提，到点早的先查。
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
     * 三件事必须在同一条 UPDATE 里：分成两条会出现「次数加了但没排下次」的中间态，
     * 那笔单就会被下一轮立刻再查一次，退避形同虚设。
     *
     * <p>不改 REFUND_STATUS：状态推进只走 {@link #updateStatus}，两者职责分开。</p>
     *
     * @param failReason 本次失败原因，覆盖旧值
     * @param nextQueryTms 下次允许查询的时刻，由调用方按指数退避算出，NEVER 传 null
     * @return 影响行数；0 表示 refundNo 不存在
     */
    int increaseRetryTimes(@Param("refundNo") String refundNo,
                           @Param("failReason") String failReason,
                           @Param("nextQueryTms") LocalDateTime nextQueryTms);
}

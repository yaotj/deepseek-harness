package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.api.page.FacePayOrderPageVO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 收款单 Mapper（表 F2F_ORDER）。 */
@Mapper
public interface F2fOrderMapper {

    /**
     * 插入收款单。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fOrder order);

    /** 按 ITP 订单号查询。 */
    F2fOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /** 只取状态一列，供 CAS 返 0 行后的回查用 （{@code F2fOrderStatusTransition.classify} 的 {@code currentStatusLoader}）。 */
    String selectOrderStatus(@Param("orderNo") String orderNo);

    /** 按取票二维码三要素查询，对应 IF2A-08 扫码取票订单查询。 */
    F2fOrder selectByQrcode(@Param("deviceId") String deviceId,
                           @Param("qrcodeGenDate") String qrcodeGenDate,
                           @Param("randomFact") String randomFact);

    /** 查询某用户指定激活状态的订单，对应 APP requestPreActiveOrderList。 */
    List<F2fOrder> selectByUserAndActivateFlag(@Param("thirdUserId") String thirdUserId,
                                              @Param("activateFlag") String activateFlag);

    /** 按逻辑卡号取发售时间最近的一笔订单，对应《非现金购票单程票退款功能3》R4。 */
    F2fOrder selectLatestByCardId(@Param("cardId") String cardId,
                                  @Param("bizType") String bizType);

    /** 按设备操作流水查订单，用于 BOM {@code requestGenNoCashOrder} 撞 {@code UK_F2F_ORDER_DEV_SEQ} 后的幂等回查。 */
    F2fOrder selectByDeviceSeq(@Param("channel") String channel,
                               @Param("deviceId") String deviceId,
                               @Param("deviceSeq") String deviceSeq);

    /**
     * 状态推进。
     *
     * @param fromStatuses 允许的前置状态，NEVER 传空集合
     * @return 影响行数；0 表示前置状态不满足，调用方 MUST 据此判断并拒绝
     */
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus,
                     @Param("remark") String remark);

    /**
     * 记支付成功：推进状态到 PAID 并回填 PAID_TMS。
     *
     * @return 影响行数；0 表示订单已不在可支付状态
     */
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("paidTms") LocalDateTime paidTms);

    /**
     * 取票订单激活抢锁。
     *
     * @return 1 表示本设备抢到；0 表示订单不在 PAID 或已被其他设备激活，对外返回 2008
     */
    int activateForDevice(@Param("orderNo") String orderNo,
                          @Param("activateDeviceId") String activateDeviceId,
                          @Param("qrcodeGenDate") String qrcodeGenDate,
                          @Param("randomFact") String randomFact,
                          @Param("activateTms") LocalDateTime activateTms);

    /** 扫出二维码已超时但仍未支付的订单，供 EXPIRED 收口任务使用。 */
    List<F2fOrder> selectExpiredCandidates(@Param("earliestExpireTms") LocalDateTime earliestExpireTms,
                                           @Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    /** 统计「已过放弃窗口、仍停在 CREATED / PAYING」的订单数，供收口任务打告警。 */
    long countStaleExpired(@Param("earliestExpireTms") LocalDateTime earliestExpireTms);

    /** 扫出已支付但未完成业务的订单，供「每日批量退款未取票交易」使用。 */
    List<F2fOrder> selectPaidNotFulfilled(@Param("deadline") LocalDateTime deadline,
                                          @Param("limit") int limit);

    /**
     * 运营端分页查询。
     *
     * @param offset 从 0 开始
     */
    List<F2fOrder> selectPage(@Param("orderNo") String orderNo,
                              @Param("payCenterOrderNo") String payCenterOrderNo,
                              @Param("payCenterChannelOrderNo") String payCenterChannelOrderNo,
                              @Param("deviceId") String deviceId,
                              @Param("channel") String channel,
                              @Param("bizType") String bizType,
                              @Param("orderStatus") String orderStatus,
                              @Param("beginTime") LocalDateTime beginTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /** 与 {@link #selectPage} 同条件的总数。 */
    long countPage(@Param("orderNo") String orderNo,
                   @Param("payCenterOrderNo") String payCenterOrderNo,
                   @Param("payCenterChannelOrderNo") String payCenterChannelOrderNo,
                   @Param("deviceId") String deviceId,
                   @Param("channel") String channel,
                   @Param("bizType") String bizType,
                   @Param("orderStatus") String orderStatus,
                   @Param("beginTime") LocalDateTime beginTime,
                   @Param("endTime") LocalDateTime endTime);

    /**
     * 运营端强类型分页查询，直接返回 {@link FacePayOrderPageVO}。
     *
     * @param offset 从 0 开始
     */
    List<FacePayOrderPageVO> selectPageView(@Param("orderNo") String orderNo,
                                            @Param("payCenterOrderNo") String payCenterOrderNo,
                                            @Param("payCenterChannelOrderNo") String payCenterChannelOrderNo,
                                            @Param("deviceId") String deviceId,
                                            @Param("channel") String channel,
                                            @Param("bizType") String bizType,
                                            @Param("orderStatus") String orderStatus,
                                            @Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /**
     * 按 {@code F2F_REFUND} 重算本单的退款汇总三列（{@code REFUND_STATUS} / {@code REFUND_AMOUNT} / {@code LAST_REFUND_TMS}）。
     *
     * @return 影响行数；0 表示订单号不存在
     */
    int updateRefundSummary(@Param("orderNo") String orderNo);

    /** 本单尚未收口的退款单张数（{@code INIT} / {@code PROCESSING} / {@code MANUAL}）。 */
    int countUnsettledRefunds(@Param("orderNo") String orderNo);
}

package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.api.page.FacePayOrderPageVO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款单 Mapper（表 F2F_ORDER）。
 *
 * <p>三条本表特有的约束，改这个接口前 MUST 先读：
 * <ul>
 *   <li><b>下单只 INSERT，不先查后插。</b>并发下「先 SELECT 判存在再 INSERT」无效，
 *       且旧实现正是这个形态。重复下单靠 UK_F2F_ORDER_NO 抛
 *       {@code DuplicateKeyException}，由 application 层捕获后查已有订单幂等返回。</li>
 *   <li><b>状态推进走白名单。</b>{@link #updateStatus} 的 {@code fromStatuses} 必填，
 *       NEVER 写成「非终态即可更新」——中间态往往尚未通过前置校验。</li>
 *   <li><b>取票激活用条件更新抢锁。</b>{@link #activateForDevice} 的 WHERE 带
 *       {@code ACTIVATE_DEVICE_ID IS NULL}，靠返回行数判断是否抢到，
 *       返回 0 表示已被其他设备激活，对外返回 2008。</li>
 * </ul>
 */
@Mapper
public interface F2fOrderMapper {

    /**
     * 插入收款单。重复 orderNo 由唯一索引抛 DuplicateKeyException，不在此处判重。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fOrder order);

    /** 按 ITP 订单号查询。 */
    F2fOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 只取状态一列，供 CAS 返 0 行后的回查用
     * （{@code F2fOrderStatusTransition.classify} 的 {@code currentStatusLoader}）。
     *
     * <p><b>只在冲突分支调</b>：CAS 命中就不回查，这是三件套规范的一部分。
     * 订单不存在时返回 null，调用方按 CONFLICT 处理。</p>
     */
    String selectOrderStatus(@Param("orderNo") String orderNo);


    /**
     * 按取票二维码三要素查询，对应 IF2A-08 扫码取票订单查询。
     * 命中 IDX_F2F_ORDER_QRCODE。
     */
    F2fOrder selectByQrcode(@Param("deviceId") String deviceId,
                           @Param("qrcodeGenDate") String qrcodeGenDate,
                           @Param("randomFact") String randomFact);

    /**
     * 查询某用户指定激活状态的订单，对应 APP requestPreActiveOrderList。
     * 命中 IDX_F2F_ORDER_USER。
     */
    List<F2fOrder> selectByUserAndActivateFlag(@Param("thirdUserId") String thirdUserId,
                                              @Param("activateFlag") String activateFlag);

    /**
     * 按逻辑卡号取发售时间最近的一笔订单，对应《非现金购票单程票退款功能3》R4。
     *
     * <p>SQL 带显式 ORDER BY + FETCH FIRST。旧实现同类查询既无排序也无取一行，
     * 多笔命中会抛 TooManyResultsException，见 §14.6 第 2 条。
     */
    F2fOrder selectLatestByCardId(@Param("cardId") String cardId,
                                  @Param("bizType") String bizType);

    /**
     * 按设备操作流水查订单，用于 BOM {@code requestGenNoCashOrder} 撞
     * {@code UK_F2F_ORDER_DEV_SEQ} 后的幂等回查。
     *
     * <p>索引三列都参与匹配；{@code deviceSeq} 为 null 时索引不生效，此处不允许传 null。</p>
     */
    F2fOrder selectByDeviceSeq(@Param("channel") String channel,
                               @Param("deviceId") String deviceId,
                               @Param("deviceSeq") String deviceSeq);

    /**
     * 状态推进。仅当当前状态在 fromStatuses 白名单内才更新。
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
     * 取票订单激活抢锁。WHERE 带 ACTIVATE_DEVICE_ID IS NULL。
     *
     * <p>同时写入二维码三要素：{@link #selectByQrcode} 按
     * {@code ACTIVATE_DEVICE_ID + qrcodeGenDate + randomFact} 回查，
     * 这三列不在激活时落库，IF2A-08 取票鉴权就永远查不到订单。</p>
     *
     * @return 1 表示本设备抢到；0 表示订单不在 PAID 或已被其他设备激活，对外返回 2008
     */
    int activateForDevice(@Param("orderNo") String orderNo,
                          @Param("activateDeviceId") String activateDeviceId,
                          @Param("qrcodeGenDate") String qrcodeGenDate,
                          @Param("randomFact") String randomFact,
                          @Param("activateTms") LocalDateTime activateTms);

    /**
     * 扫出二维码已超时但仍未支付的订单，供 EXPIRED 收口任务使用。
     * 命中 IDX_F2F_ORDER_SCAN。
     *
     * <p><b>{@code earliestExpireTms} 是放弃窗口下界，MUST 传，NEVER 去掉。</b>
     * 没有下界时，一笔支付中心永远查不到的订单会被每轮扫到、每轮外呼一次且永不收敛——
     * 2026-09-10 实测：订单 {@code F200202609100914540082} 每 30 秒一次 {@code payQuery}、
     * 回 {@code 9999 未找到数据}，持续 40 分钟没有出口。超过下界的单不再被扫，
     * 改由 {@link #countStaleExpired} 计数告警 + 人工判定。</p>
     */
    List<F2fOrder> selectExpiredCandidates(@Param("earliestExpireTms") LocalDateTime earliestExpireTms,
                                           @Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    /**
     * 统计「已过放弃窗口、仍停在 CREATED / PAYING」的订单数，供收口任务打告警。
     *
     * <p>这些单已被 {@link #selectExpiredCandidates} 放弃，<b>不会再自动收口</b>；
     * 计数只为让问题可见——持续大于 0 说明有一批单需要人工判定。</p>
     */
    long countStaleExpired(@Param("earliestExpireTms") LocalDateTime earliestExpireTms);

    /**
     * 扫出已支付但未完成业务的订单，供「每日批量退款未取票交易」使用。
     * 对应 §7.5 规格要求，旧实现无此任务（缺口 E3）。
     */
    List<F2fOrder> selectPaidNotFulfilled(@Param("deadline") LocalDateTime deadline,
                                          @Param("limit") int limit);

    /**
     * 运营端分页查询。
     *
     * <p><b>调用方 MUST 先保证有检索范围</b>（订单号 / 支付中心订单号 / 完整时间范围之一），
     * 否则这条 SQL 会全表扫 {@code F2F_ORDER}。该表按月分区且是核心交易表，
     * 无条件全扫会拖垮设备链路，controller 层已挡在前面。</p>
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
     * <p>替代已废弃的 {@code selectLegacyPage}（返回裸 Map + 旧 ItpStatusEnum 状态映射）。
     * 本方法投影字段与取值对齐域模型：{@code orderStatus} 存真实枚举值
     * （CREATED / PAID / FULFILLED / REFUNDED 等），{@code orderAmount} / {@code ticketPrice} /
     * {@code refundAmount} 为 {@link Long} 单位分，退款状态由独立列承载与主状态正交。</p>
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
     * 按 {@code F2F_REFUND} 重算本单的退款汇总三列（{@code REFUND_STATUS} / {@code REFUND_AMOUNT}
     * / {@code LAST_REFUND_TMS}）。口径与 pay-sign 的 {@code PayTxnDetailMapper.updateRefundSummary}
     * 一致：<b>退款不改支付 / 履约主状态</b>，只动这三列（ADR-D88）。
     *
     * <p><b>重算、不累加</b>：结果只取决于明细表里 {@code REFUND_STATUS = 'SUCCESS'} 的行，
     * 执行 1 次和 N 次落库值相同。因此本方法<b>没有前置状态白名单、也不需要 CAS</b>；
     * 返回 1 只表示订单行存在，<b>NEVER 拿返回值判断「退款成功没成功」</b>——那要看 {@code F2F_REFUND}。</p>
     *
     * @return 影响行数；0 表示订单号不存在
     */
    int updateRefundSummary(@Param("orderNo") String orderNo);

    /**
     * 本单尚未收口的退款单张数（{@code INIT} / {@code PROCESSING} / {@code MANUAL}）。
     *
     * <p><b>跨来源重复退款的防线</b>：唯一函数索引 {@code UK_F2F_REFUND_IDEM} 只挡
     * 「同一订单 + 同一 {@code REFUND_SOURCE}」，挡不住「运营端退过、BOM 设备再退」。
     * 主状态不再走 {@code REFUNDING} 之后，这条查询接替了原先「状态是 REFUNDING 就拒绝」的作用，
     * 发起任何新退款前 <b>MUST</b> 先查它。</p>
     *
     * <p>{@code MANUAL} 也算未收口：它的语义是「本系统已放弃自动判定，钱退没退未知」，
     * 放行等于允许在一笔可能已成功的退款之上再退一次。</p>
     */
    int countUnsettledRefunds(@Param("orderNo") String orderNo);
}

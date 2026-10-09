package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketOrderMapper {
    int insert(DailyTicketOrder record);

    DailyTicketOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /** 按旅游票主单号查询其下全部日票子单。 */
    java.util.List<DailyTicketOrder> selectByParentOrderNo(@Param("parentOrderNo") String parentOrderNo);

    /**
     * 批量退款扫表：已支付且未激活（无票实例）、已过等待期的独立日票单。
     *
     * <p>候选判据带 {@code PARENT_ORDER_NO IS NULL}，旅游票子单一律排除 —— 旅游票按主单整单退。
     *
     * @param paidBefore 等待期上界，等于「当前时间 - waitDays」
     * @param paidAfter  回溯窗口下界，等于「当前时间 - lookbackDays」
     * @param limit      单批条数上限
     */
    java.util.List<DailyTicketOrder> selectPaidNotActivated(@Param("paidBefore") java.time.LocalDateTime paidBefore,
                                                           @Param("paidAfter") java.time.LocalDateTime paidAfter,
                                                           @Param("limit") int limit);

    /** 运营页面按下单时间及订单号查询日票订单。 */
    java.util.List<DailyTicketRefundOrderView> selectRefundOrders(DailyTicketRefundOrderQuery query);

    /** 运营页面查询旅游票主单下的子单明细。 */
    java.util.List<DailyTicketRefundOrderView> selectTravelSubRefundOrders(@Param("parentOrderNo") String parentOrderNo);

    int updatePayRequest(DailyTicketOrder record);

    /** 支付终态条件更新，仅允许支付中订单首次进入终态。 */
    int updatePayResultIfPaying(DailyTicketOrder record);

    /** 外部渠道同步支付成功，允许待支付或支付中订单进入已支付。 */
    int updatePaySuccessByExternalSync(DailyTicketOrder record);

    /** 回写支付平台原支付订单号，保留首次获取的有效值。 */
    int updatePaymentOrderNo(@Param("orderNo") String orderNo,
                             @Param("paymentOrderNo") String paymentOrderNo);

    int updateOrderStatus(@Param("orderNo") String orderNo, @Param("orderStatus") String orderStatus);

    /**
     * IF8A-65 取消未支付成功的订单，白名单只有 CREATED / PAYING / PAY_FAILED 三个状态。
     *
     * <p>用 CAS 而不是 {@link #updateOrderStatus}：取消与支付回调会赛跑，
     * 无条件 UPDATE 会把已经落 PAID 的订单改成 CANCELED 而支付事实仍在，NEVER 退回去。
     */
    int cancelIfPending(@Param("orderNo") String orderNo);

    /**
     * 取消**已支付但未激活**的订单，只放行 PAID + PAID。
     *
     * <p>取消成功后由 {@code CanceledOrderRefundService} 当场发起退款，订单随退款链路推进
     * REFUNDING / REFUNDED，因此本语句只负责把订单钉在 CANCELED 这一步。
     */
    int cancelIfPaid(@Param("orderNo") String orderNo);

    /** 旅游票主单取消时连带取消其下未激活子单，返回受影响子单数。 */
    int cancelSubOrdersByParent(@Param("parentOrderNo") String parentOrderNo);

    /**
     * 已取消订单收到支付成功通知：只补支付事实，{@code ORDER_STATUS} 保持 CANCELED。
     *
     * <p>后续退款要用 {@code PAYMENT_ORDER_NO}，因此这一步 MUST 落库；
     * 状态推进交给退款链路，**NEVER 在这里把订单改回 PAID**（那等于把已取消的票又放行了）。
     */
    int updatePayResultIfCanceled(DailyTicketOrder record);
}

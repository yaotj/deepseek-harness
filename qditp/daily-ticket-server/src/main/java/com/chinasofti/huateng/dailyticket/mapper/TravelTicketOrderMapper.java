package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TravelTicketOrderMapper {
    int insert(TravelTicketOrder record);

    TravelTicketOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 批量退款扫表：已支付、已过等待期、且名下全部子单都没有票实例的旅游票主单。
     *
     * <p>子单「是否已使用」的进一步校验由 {@code requestTravelRefund} 负责，本查询只保证
     * 「全部子单无票实例」这一条主单级候选判据。
     *
     * @param paidBefore 等待期上界，等于「当前时间 - waitDays」
     * @param paidAfter  回溯窗口下界，等于「当前时间 - lookbackDays」
     * @param limit      单批条数上限
     */
    java.util.List<TravelTicketOrder> selectPaidNotActivated(@Param("paidBefore") java.time.LocalDateTime paidBefore,
                                                            @Param("paidAfter") java.time.LocalDateTime paidAfter,
                                                            @Param("limit") int limit);

    int updatePayRequest(TravelTicketOrder record);

    int updatePayResultIfPaying(TravelTicketOrder record);

    /** 外部渠道同步支付成功，允许待支付或支付中主单进入已支付。 */
    int updatePaySuccessByExternalSync(TravelTicketOrder record);

    int updatePaymentOrderNo(@Param("orderNo") String orderNo,
                             @Param("paymentOrderNo") String paymentOrderNo);

    int updateOrderStatus(@Param("orderNo") String orderNo, @Param("orderStatus") String orderStatus);

    /** IF8A-65 取消未支付成功的旅游票主单，白名单只有 CREATED / PAYING / PAY_FAILED。 */
    int cancelIfPending(@Param("orderNo") String orderNo);

    /** 取消已支付但未激活的旅游票主单，只放行 PAID + PAID，取消后由自动退款链路整单退。 */
    int cancelIfPaid(@Param("orderNo") String orderNo);

    /** 已取消主单收到支付成功通知：只补支付事实，{@code ORDER_STATUS} 保持 CANCELED。 */
    int updatePayResultIfCanceled(TravelTicketOrder record);
}

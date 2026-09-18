package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface DailyTicketRefundMapper {
    int insert(DailyTicketRefund record);

    DailyTicketRefund selectByOrderNo(@Param("orderNo") String orderNo);

    DailyTicketRefund selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);

    /** 运营页面退款记录查询。 */
    java.util.List<DailyTicketRefundView> selectRefunds(DailyTicketRefundQuery query);

    int updateResult(DailyTicketRefund record);

    /**
     * 回写 IF8B-04 通知投递状态。
     *
     * @param orderNo      日票订单号
     * @param notifyStatus PENDING 待发 / SUCCESS 已受理 / GIVEUP 放弃重投
     * @param notifyTimes  已投递次数
     * @param notifyTime   本次投递时刻，置待发时传 null
     * @param notifyMsg    失败原因，成功或置待发时传 null
     */
    int updateNotifyStatus(@Param("orderNo") String orderNo,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("notifyTimes") Integer notifyTimes,
                           @Param("notifyTime") Date notifyTime,
                           @Param("notifyMsg") String notifyMsg);

    /**
     * 捞待投递的退款通知（扫表补偿入口）。
     *
     * @param maxTimes 投递次数上限，达到即不再捞出
     * @param limit    单批条数上限
     */
    List<DailyTicketRefund> selectPendingNotify(@Param("maxTimes") int maxTimes, @Param("limit") int limit);
}

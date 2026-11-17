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

    java.util.List<DailyTicketRefundView> selectRefunds(DailyTicketRefundQuery query);

    int updateResult(DailyTicketRefund record);


    int updateNotifyStatus(@Param("orderNo") String orderNo,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("notifyTimes") Integer notifyTimes,
                           @Param("notifyTime") Date notifyTime,
                           @Param("notifyMsg") String notifyMsg);


    List<DailyTicketRefund> selectPendingNotify(@Param("maxTimes") int maxTimes, @Param("limit") int limit);
}

package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketRefundDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DailyTicketRefundDetailMapper {
    int insert(DailyTicketRefundDetail record);

    List<DailyTicketRefundDetail> selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);

    List<DailyTicketRefundDetail> selectByParentOrderNo(@Param("parentOrderNo") String parentOrderNo);

    int updateStatusByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo,
                                    @Param("refundStatus") String refundStatus);
}

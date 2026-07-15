package com.chinasofti.huateng.collectticket.mapper;

import com.chinasofti.huateng.collectticket.entity.TicketCollectLogDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketCollectLogDetailMapper {
    /**
     * 根据订单号查询取票明细记录列表。
     */
    java.util.List<TicketCollectLogDetail> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 插入取票明细记录。
     */
    int insert(TicketCollectLogDetail record);

    /**
     * 批量插入取票明细记录。
     */
    int insertBatch(@Param("list") java.util.List<TicketCollectLogDetail> list);
}

package com.chinasofti.huateng.collectticket.mapper;

import com.chinasofti.huateng.collectticket.entity.TicketCollectLogs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketCollectLogsMapper {
    /**
     * 根据订单号查询取票记录。
     */
    TicketCollectLogs selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据用户ID查询取票记录列表。
     */
    java.util.List<TicketCollectLogs> selectByUserId(@Param("userId") String userId);

    /**
     * 插入取票记录。
     */
    int insert(TicketCollectLogs record);

    /**
     * 更新取票记录。
     */
    int updateByOrderNo(TicketCollectLogs record);
}

package com.chinasofti.huateng.collectticket.mapper;

import com.chinasofti.huateng.collectticket.entity.TicketCollectLogs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketCollectLogsMapper {

    TicketCollectLogs selectByOrderNo(@Param("orderNo") String orderNo);


    java.util.List<TicketCollectLogs> selectByUserId(@Param("userId") String userId);


    int insert(TicketCollectLogs record);


    int updateByOrderNo(TicketCollectLogs record);
}

package com.chinasofti.huateng.dailyticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReconExportMapper {


    List<Map<String, Object>> selectDetailPage(@Param("windowStart") Timestamp windowStart,
                                               @Param("windowEnd") Timestamp windowEnd,
                                               @Param("lastPayDate") Timestamp lastPayDate,
                                               @Param("lastOrderNo") String lastOrderNo,
                                               @Param("limit") int limit);


    List<Map<String, Object>> selectTravelTicketPaySummary(@Param("windowStart") Timestamp windowStart,
                                                           @Param("windowEnd") Timestamp windowEnd);
}

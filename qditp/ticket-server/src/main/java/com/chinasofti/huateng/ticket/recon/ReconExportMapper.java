package com.chinasofti.huateng.ticket.recon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReconExportMapper {


    List<Map<String, Object>> selectExpPage(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("windowStart") String windowStart,
                                            @Param("windowEnd") String windowEnd,
                                            @Param("lastHandleTime") String lastHandleTime,
                                            @Param("lastId") Long lastId,
                                            @Param("limit") int limit);


    List<Map<String, Object>> selectBusSummary(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("windowStart") String windowStart,
                                               @Param("windowEnd") String windowEnd);
}

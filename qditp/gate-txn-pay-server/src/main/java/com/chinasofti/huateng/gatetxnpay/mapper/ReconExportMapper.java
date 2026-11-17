package com.chinasofti.huateng.gatetxnpay.mapper;

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
                                            @Param("lastOutTime") String lastOutTime,
                                            @Param("lastId") Long lastId,
                                            @Param("limit") int limit);


    List<Map<String, Object>> selectPayGateSummary(@Param("startDate") String startDate,
                                                    @Param("endDate") String endDate,
                                                    @Param("windowStart") String windowStart,
                                                    @Param("windowEnd") String windowEnd);


    List<Map<String, Object>> selectPayExpSummary(@Param("startDate") String startDate,
                                                   @Param("endDate") String endDate,
                                                   @Param("windowStart") String windowStart,
                                                   @Param("windowEnd") String windowEnd);


    List<Map<String, Object>> selectBusSummary(@Param("startDate") String startDate,
                                                @Param("endDate") String endDate,
                                                @Param("windowStart") String windowStart,
                                                @Param("windowEnd") String windowEnd);


    List<Map<String, Object>> selectDetailPage(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("windowStart") String windowStart,
                                               @Param("windowEnd") String windowEnd,
                                               @Param("lastOutTime") String lastOutTime,
                                               @Param("lastId") Long lastId,
                                               @Param("limit") int limit);
}

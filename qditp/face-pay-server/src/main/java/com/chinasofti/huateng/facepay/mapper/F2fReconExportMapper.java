package com.chinasofti.huateng.facepay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface F2fReconExportMapper {

    List<Map<String, Object>> selectDeviceSalePaySummary(@Param("windowStart") Timestamp windowStart,
                                                        @Param("windowEnd") Timestamp windowEnd);

    List<Map<String, Object>> selectAppSalePaySummary(@Param("windowStart") Timestamp windowStart,
                                                     @Param("windowEnd") Timestamp windowEnd);

    List<Map<String, Object>> selectTopupPaySummary(@Param("windowStart") Timestamp windowStart,
                                                   @Param("windowEnd") Timestamp windowEnd);

    List<Map<String, Object>> selectBomAdminPaySummary(@Param("windowStart") Timestamp windowStart,
                                                      @Param("windowEnd") Timestamp windowEnd);

    List<Map<String, Object>> selectBomOtherPaySummary(@Param("windowStart") Timestamp windowStart,
                                                      @Param("windowEnd") Timestamp windowEnd);

    List<Map<String, Object>> selectBusSummary(@Param("windowStart") Timestamp windowStart,
                                               @Param("windowEnd") Timestamp windowEnd);

    Long countUncoveredPaidOrders(@Param("windowStart") Timestamp windowStart,
                                  @Param("windowEnd") Timestamp windowEnd);

    Long countPaidWithoutPaidTms(@Param("windowStart") Timestamp windowStart,
                                @Param("windowEnd") Timestamp windowEnd);

    List<Map<String, Object>> selectFacePayTxnDetails(@Param("windowStart") Timestamp windowStart,
                                                     @Param("windowEnd") Timestamp windowEnd);
}

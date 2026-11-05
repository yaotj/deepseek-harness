package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.api.page.F2fTopupSuspiciousPageVO;
import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface F2fResultReportMapper {

    int insert(F2fResultReport report);

    F2fResultReport selectByTypeAndOrderNo(@Param("reportType") String reportType,
                                           @Param("orderNo") String orderNo);

    List<F2fResultReport> selectAllByOrderNo(@Param("orderNo") String orderNo);

    List<F2fResultReport> selectByFaultSlipSeq(@Param("faultSlipSeq") String faultSlipSeq);

    List<F2fResultReport> selectPendingReports(@Param("limit") int limit,
                                               @Param("reportTypes") List<String> reportTypes,
                                               @Param("staleBefore") LocalDateTime staleBefore);

    int markProcessed(@Param("id") Long id);

    int updateOptResult(@Param("reportType") String reportType,
                       @Param("orderNo") String orderNo,
                       @Param("optResult") String optResult,
                       @Param("optResultDesc") String optResultDesc);

    List<F2fTopupSuspiciousPageVO> selectTopupSuspiciousPage(@Param("orderNo") String orderNo,
                                                             @Param("channel") String channel,
                                                             @Param("deviceId") String deviceId,
                                                             @Param("topupStatus") String topupStatus,
                                                             @Param("processed") String processed,
                                                             @Param("beginTms") LocalDateTime beginTms,
                                                             @Param("endTms") LocalDateTime endTms,
                                                             @Param("offset") int offset,
                                                             @Param("limit") int limit);

    long countTopupSuspiciousPage(@Param("orderNo") String orderNo,
                                  @Param("channel") String channel,
                                  @Param("deviceId") String deviceId,
                                  @Param("topupStatus") String topupStatus,
                                  @Param("processed") String processed,
                                  @Param("beginTms") LocalDateTime beginTms,
                                  @Param("endTms") LocalDateTime endTms);
}

package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 设备业务结果上报 Mapper（表 F2F_RESULT_REPORT）。 */
@Mapper
public interface F2fResultReportMapper {

    /**
     * 插入一条设备上报记录。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fResultReport report);

    /**
     * 按幂等键（上报类型 + 订单号）查唯一一条，命中 UK_F2F_REPORT_IDEM。
     *
     * @return 命中的上报记录；无则 null
     */
    F2fResultReport selectByTypeAndOrderNo(@Param("reportType") String reportType,
                                           @Param("orderNo") String orderNo);

    /**
     * 查某订单号下的全部上报记录（可能有多个 REPORT_TYPE），按接收时间正序。
     *
     * @return 该订单号的上报记录列表；无则空列表
     */
    List<F2fResultReport> selectAllByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按 TVM 打印的故障单号查询，供 BOM 侧凭故障单号追溯，命中 IDX_F2F_REPORT_SLIP。
     *
     * @return 该故障单号对应的上报记录列表；无则空列表
     */
    List<F2fResultReport> selectByFaultSlipSeq(@Param("faultSlipSeq") String faultSlipSeq);

    /**
     * 扫出尚未处理（{@code PROCESSED='0'}）且已过静默期的上报记录，按 RECEIVE_TMS 正序先到先处理， 命中 IDX_F2F_REPORT_PENDING。
     *
     * @param limit       单批最大条数，MUST 为正数
     * @param reportTypes 只捞这些 {@code REPORT_TYPE}，MUST 非空
     * @param staleBefore 只捞 {@code RECEIVE_TMS} 早于该时刻的行
     * @return 待补偿的上报记录列表；无则空列表
     */
    List<F2fResultReport> selectPendingReports(@Param("limit") int limit,
                                               @Param("reportTypes") List<String> reportTypes,
                                               @Param("staleBefore") LocalDateTime staleBefore);

    /**
     * 把某条上报标记为已处理。
     *
     * @return 影响行数；1 表示本线程抢到并完成标记，0 表示该条已被其他线程处理
     */
    int markProcessed(@Param("id") Long id);

    /**
     * 按幂等键回写上报的处理结论，供 IF5A-09 HCE 回写失败时把 {@code OPT_RESULT} 从「已受理」改成 {@code FAILED}。
     *
     * @return 影响行数；0 表示该上报不存在（正常不该发生）
     */
    int updateOptResult(@Param("reportType") String reportType,
                        @Param("orderNo") String orderNo,
                        @Param("optResult") String optResult,
                        @Param("optResultDesc") String optResultDesc);
}

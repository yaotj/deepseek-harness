package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备业务结果上报 Mapper（表 F2F_RESULT_REPORT）。
 *
 * <p>方法按用例定义，没有通用 CRUD。改这个接口前 MUST 先读以下三条本表约束：
 * <ul>
 *   <li><b>写入只 INSERT，NEVER 先查后插。</b>规格要求设备断网后重传，并发下
 *       「先 SELECT 判存在再 INSERT」无效。重复上报由 UK_F2F_REPORT_IDEM
 *       (REPORT_TYPE, ORDER_NO) 抛 {@code DuplicateKeyException}，
 *       application 层捕获后当作「已收到过」返回成功——这正是断网重传要的效果。</li>
 *   <li><b>接收与后续动作解耦。</b>退款、订单状态推进不在接收链路里做，
 *       由 {@link #selectPendingReports} 扫表驱动（命中 IDX_F2F_REPORT_PENDING）。</li>
 *   <li><b>标记已处理靠影响行数判并发。</b>{@link #markProcessed} 的 WHERE 带
 *       {@code PROCESSED = '0'}，返回 0 表示该条已被其他线程处理，调用方 MUST 据此跳过。</li>
 * </ul>
 */
@Mapper
public interface F2fResultReportMapper {

    /**
     * 插入一条设备上报记录。本语句不做任何判重，
     * 重复的 (REPORT_TYPE, ORDER_NO) 由唯一索引抛 DuplicateKeyException。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fResultReport report);

    /**
     * 按幂等键（上报类型 + 订单号）查唯一一条，命中 UK_F2F_REPORT_IDEM。
     * 捕获 DuplicateKeyException 后用它取回已存在的那条上报。
     *
     * @return 命中的上报记录；无则 null
     */
    F2fResultReport selectByTypeAndOrderNo(@Param("reportType") String reportType,
                                           @Param("orderNo") String orderNo);

    /**
     * 查某订单号下的全部上报记录（可能有多个 REPORT_TYPE），按接收时间正序。
     * 注意 orderNo 在 ERROR_CODE=2101 时是取票二维码的 randomFact。
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
     * 扫出尚未处理（{@code PROCESSED='0'}）且已过静默期的上报记录，按 RECEIVE_TMS 正序先到先处理，
     * 命中 IDX_F2F_REPORT_PENDING。供 {@code F2fReportRecovery} 的补偿任务使用。
     *
     * <p><b>静默期（{@code staleBefore}）不是可选的**：接收链路是「落票 → 落上报 → 推进订单 →
     * 差额退款 → 入队通知」，落上报之后那几步还在同一个请求里跑。若不排除刚落库的行，
     * 补偿任务会与首报<b>并发</b>做同一件事 —— 三步虽都幂等，但会白发一遍支付中心退款查询。
     *
     * <p><b>{@code reportTypes} 也不是可选的</b>：本表还承载 {@code BOM_BIZ_RESULT} /
     * {@code TOPUP_OK} 等类型，它们的后续动作在各自链路里内联完成、不该被本任务重放；
     * 不过滤会让那些行被反复扫到又什么都做不了。
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
     * 把某条上报标记为已处理。WHERE 带 PROCESSED = '0' 做并发保护。
     *
     * @return 影响行数；1 表示本线程抢到并完成标记，0 表示该条已被其他线程处理
     */
    int markProcessed(@Param("id") Long id);

    /**
     * 按幂等键回写上报的处理结论，供 IF5A-09 HCE 回写失败时把 {@code OPT_RESULT}
     * 从「已受理」改成 {@code FAILED}。
     *
     * <p>用幂等键而不是自增 id 定位：{@code insert} 不回填主键，而
     * (REPORT_TYPE, ORDER_NO) 本身就是 {@code UK_F2F_REPORT_IDEM}，唯一。</p>
     *
     * @return 影响行数；0 表示该上报不存在（正常不该发生）
     */
    int updateOptResult(@Param("reportType") String reportType,
                        @Param("orderNo") String orderNo,
                        @Param("optResult") String optResult,
                        @Param("optResultDesc") String optResultDesc);
}

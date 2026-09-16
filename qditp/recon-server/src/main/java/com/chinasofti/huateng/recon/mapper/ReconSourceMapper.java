package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.SourceProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code RECON_BATCH_SOURCE} 的数据访问，收齐判定的唯一依据表。
 */
@Mapper
public interface ReconSourceMapper {

    /**
     * 登记一条期望，按 {@code (BATCH_ID, SOURCE_NAME, FILE_TYPE)} 幂等。
     */
    int insertIfAbsent(@Param("batchId") String batchId,
                       @Param("source") String source,
                       @Param("fileType") String fileType,
                       @Param("status") String status);

    SourceProgress selectByKey(@Param("batchId") String batchId,
                               @Param("source") String source,
                               @Param("fileType") String fileType);

    List<SourceProgress> selectByBatch(@Param("batchId") String batchId);

    /**
     * 写入源声明的三项总账并置状态；同时把重试次数保留原值。
     */
    int updateDeclared(@Param("batchId") String batchId,
                       @Param("source") String source,
                       @Param("fileType") String fileType,
                       @Param("status") String status,
                       @Param("declaredParts") int declaredParts,
                       @Param("declaredRecords") long declaredRecords,
                       @Param("declaredAmount") long declaredAmount,
                       @Param("failReason") String failReason);

    /**
     * 只改状态与失败原因，用于下发指令成功 / 被回绝。
     */
    int updateStatus(@Param("batchId") String batchId,
                     @Param("source") String source,
                     @Param("fileType") String fileType,
                     @Param("status") String status,
                     @Param("failReason") String failReason);

    /**
     * 与 {@link #updateStatus} 同形，但**只在当前状态不是 COMPLETED / MISMATCH 时才改**，
     * 专供「置 EXPORTING」使用。
     *
     * <p>为什么必须条件更新：下发是「先发 HTTP、后写 EXPORTING」，而源侧抽取跑在自己的线程池里，
     * 空结果集（如 gate-txn-pay 的 EXP 0 行）**可能在 200ms 内就回调 complete 声明 COMPLETED**，
     * 此时编排线程才写 EXPORTING，就把 COMPLETED 覆盖回去 —— 该 {@code (来源, 文件类型)} 从此
     * 永远停在 EXPORTING、批次永不收齐。2026-09-11 实测复现（batchId=RECON20260909，
     * gate-txn-pay/EXP 已 declareComplete 却仍是 EXPORTING，整批 240s 超时停在 PARTIAL）。
     * <b>NEVER 把这里换回无条件的 {@link #updateStatus}。</b></p>
     */
    int updateStatusIfNotTerminal(@Param("batchId") String batchId,
                                  @Param("source") String source,
                                  @Param("fileType") String fileType,
                                  @Param("status") String status,
                                  @Param("failReason") String failReason);

    /**
     * 重试次数 +1，用于每次重新下发指令。
     */
    int increaseRetry(@Param("batchId") String batchId,
                      @Param("source") String source,
                      @Param("fileType") String fileType);
}

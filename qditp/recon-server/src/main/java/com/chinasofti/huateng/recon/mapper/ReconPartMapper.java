package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.PartReceipt;
import com.chinasofti.huateng.recon.model.PartTotals;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code RECON_BATCH_PART} 的数据访问。
 */
@Mapper
public interface ReconPartMapper {

    PartReceipt selectByKey(@Param("batchId") String batchId,
                            @Param("source") String source,
                            @Param("fileType") String fileType,
                            @Param("partNo") int partNo);

    int insert(@Param("batchId") String batchId,
               @Param("source") String source,
               @Param("fileType") String fileType,
               @Param("partNo") int partNo,
               @Param("byteCount") long byteCount,
               @Param("recordCount") long recordCount,
               @Param("amountTotal") long amountTotal,
               @Param("sha256") String sha256,
               @Param("path") String path,
               @Param("status") String status);

    int updateStatus(@Param("batchId") String batchId,
                     @Param("source") String source,
                     @Param("fileType") String fileType,
                     @Param("partNo") int partNo,
                     @Param("status") String status);

    List<PartReceipt> selectReceivedParts(@Param("batchId") String batchId,
                                          @Param("source") String source,
                                          @Param("fileType") String fileType);

    /**
     * 汇总已接收分片的片数、记录数与金额，用于与源声明的三项总账比对。
     *
     * <p>零行时返回 {@code (0,0,0)} 而不是 null（SQL 里用了 NVL + COUNT），调用方不必判空。</p>
     */
    PartTotals selectTotals(@Param("batchId") String batchId,
                            @Param("source") String source,
                            @Param("fileType") String fileType);
}

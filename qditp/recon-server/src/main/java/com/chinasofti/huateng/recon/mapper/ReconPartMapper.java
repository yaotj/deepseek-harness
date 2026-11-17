package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.PartReceipt;
import com.chinasofti.huateng.recon.model.PartTotals;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


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

    PartTotals selectTotals(@Param("batchId") String batchId,
                            @Param("source") String source,
                            @Param("fileType") String fileType);
}

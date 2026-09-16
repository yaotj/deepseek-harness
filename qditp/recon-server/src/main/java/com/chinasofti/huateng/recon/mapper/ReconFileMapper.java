package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.ReconFileView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconFileMapper {
    int upsert(@Param("batchId") String batchId,
               @Param("fileType") String fileType,
               @Param("fileName") String fileName,
               @Param("path") String path,
               @Param("byteCount") long byteCount,
               @Param("recordCount") long recordCount,
               @Param("amountTotal") long amountTotal,
               @Param("sha256") String sha256,
               @Param("status") String status);

    ReconFileView select(@Param("batchId") String batchId, @Param("fileType") String fileType);

    List<ReconFileView> selectByBatch(@Param("batchId") String batchId);

    int updateUpload(@Param("batchId") String batchId,
                     @Param("fileType") String fileType,
                     @Param("status") String status,
                     @Param("remotePath") String remotePath);
}

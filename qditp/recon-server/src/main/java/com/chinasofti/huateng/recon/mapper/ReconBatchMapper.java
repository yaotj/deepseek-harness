package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.BatchView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconBatchMapper {
    int insertIfAbsent(@Param("batchId") String batchId,
                       @Param("businessDate") String businessDate,
                       @Param("windowStart") String windowStart,
                       @Param("windowEnd") String windowEnd);

    BatchView selectById(@Param("batchId") String batchId);

    List<BatchView> selectUnfinished();

    int updateStatus(@Param("batchId") String batchId,
                     @Param("status") String status,
                     @Param("expectedStatus") String expectedStatus);
}

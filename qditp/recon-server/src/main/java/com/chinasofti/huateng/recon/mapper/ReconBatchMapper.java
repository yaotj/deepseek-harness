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

    /** 取尚未收口的批次，按创建时间升序、最多 20 条；FAILED 也在结果里（它是补偿入口）。 */
    List<BatchView> selectUnfinished();

    int updateStatus(@Param("batchId") String batchId,
                     @Param("status") String status,
                     @Param("expectedStatus") String expectedStatus);
}

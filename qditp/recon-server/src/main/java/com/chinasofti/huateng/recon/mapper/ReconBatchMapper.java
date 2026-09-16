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

    /**
     * 取尚未收口（{@code STATUS <> 'SUCCESS'}）的批次，按创建时间升序、最多 20 条，供编排任务逐个推进。
     *
     * <p>FAILED 也在结果里：它是补偿重试的入口，不是终态。</p>
     */
    List<BatchView> selectUnfinished();

    int updateStatus(@Param("batchId") String batchId,
                     @Param("status") String status,
                     @Param("expectedStatus") String expectedStatus);
}

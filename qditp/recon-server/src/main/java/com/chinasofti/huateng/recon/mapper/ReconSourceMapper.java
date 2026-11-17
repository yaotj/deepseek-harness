package com.chinasofti.huateng.recon.mapper;

import com.chinasofti.huateng.recon.model.SourceProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface ReconSourceMapper {


    int insertIfAbsent(@Param("batchId") String batchId,
                       @Param("source") String source,
                       @Param("fileType") String fileType,
                       @Param("status") String status);

    SourceProgress selectByKey(@Param("batchId") String batchId,
                               @Param("source") String source,
                               @Param("fileType") String fileType);

    List<SourceProgress> selectByBatch(@Param("batchId") String batchId);


    int updateDeclared(@Param("batchId") String batchId,
                       @Param("source") String source,
                       @Param("fileType") String fileType,
                       @Param("status") String status,
                       @Param("declaredParts") int declaredParts,
                       @Param("declaredRecords") long declaredRecords,
                       @Param("declaredAmount") long declaredAmount,
                       @Param("failReason") String failReason);


    int updateStatus(@Param("batchId") String batchId,
                     @Param("source") String source,
                     @Param("fileType") String fileType,
                     @Param("status") String status,
                     @Param("failReason") String failReason);


    int updateStatusIfNotTerminal(@Param("batchId") String batchId,
                                  @Param("source") String source,
                                  @Param("fileType") String fileType,
                                  @Param("status") String status,
                                  @Param("failReason") String failReason);


    int increaseRetry(@Param("batchId") String batchId,
                      @Param("source") String source,
                      @Param("fileType") String fileType);
}

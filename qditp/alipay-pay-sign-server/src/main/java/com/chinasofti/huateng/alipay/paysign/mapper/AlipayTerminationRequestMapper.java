package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlipayTerminationRequestMapper {

    AlipayTerminationRequest selectByAgreementCode(@Param("agreementCode") String agreementCode);

    int countByAgreementCode(@Param("agreementCode") String agreementCode);

    int insert(AlipayTerminationRequest request);

    int updateStatus(@Param("agreementCode") String agreementCode, @Param("status") String status,
                     @Param("updateTime") LocalDateTime updateTime);


    int updateStatusCas(@Param("terminationSeq") String terminationSeq,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("updateTime") LocalDateTime updateTime);

    List<AlipayTerminationRequest> selectByStatusLimit(@Param("status") String status,
                                                      @Param("limit") int limit);

    List<AlipayTerminationRequest> selectByStatusBefore(@Param("status") String status,
                                                       @Param("cutoff") LocalDateTime cutoff,
                                                       @Param("limit") int limit);
}

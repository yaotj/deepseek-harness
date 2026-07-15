package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayTerminationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayTerminationRequestMapper {
    AlipayTerminationRequest selectByAgreementCode(@Param("agreementCode") String agreementCode);
    int insert(AlipayTerminationRequest request);
    int updateStatus(@Param("agreementCode") String agreementCode, @Param("status") String status, @Param("updateTime") java.time.LocalDateTime updateTime);
}

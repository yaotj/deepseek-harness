package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipaySignInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipaySignInfoMapper {
    AlipaySignInfo selectByAgreementCode(@Param("agreementCode") String agreementCode);
    AlipaySignInfo selectByThirdUserIdAndChannel(@Param("thirdUserId") String thirdUserId, @Param("channel") String channel);
    int insert(AlipaySignInfo signInfo);
    int updateStatus(@Param("agreementCode") String agreementCode, @Param("signStatus") String signStatus, @Param("updateTime") java.time.LocalDateTime updateTime);
}

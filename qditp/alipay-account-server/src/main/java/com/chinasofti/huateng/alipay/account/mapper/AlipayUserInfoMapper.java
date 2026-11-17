package com.chinasofti.huateng.alipay.account.mapper;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface AlipayUserInfoMapper {
    AlipayUserInfo selectByThirdUserId(@Param("thirdUserId") String thirdUserId);

    AlipayUserInfo selectByCardId(@Param("cardId") String cardId);


    List<AlipayUserInfo> selectByFilters(@Param("msisdn") String msisdn,
                                         @Param("cardId") String cardId,
                                         @Param("thirdUserId") String thirdUserId,
                                         @Param("reqContractNo") String reqContractNo);

    int insert(AlipayUserInfo record);

    int updateByThirdUserId(AlipayUserInfo record);

    int updatePaymentChannel(AlipayUserInfo record);

    int updateMsisdnByThirdUserId(@Param("thirdUserId") String thirdUserId, @Param("msisdn") String msisdn);
}

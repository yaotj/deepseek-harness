package com.chinasofti.huateng.alipay.account.mapper;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

@Mapper
@Component
public interface AlipayUserInfoMapper {
    AlipayUserInfo selectByThirdUserId(@Param("thirdUserId") String thirdUserId);

    AlipayUserInfo selectByCardId(@Param("cardId") String cardId);

    int insert(AlipayUserInfo record);

    int updateByThirdUserId(AlipayUserInfo record);

    int updatePaymentChannel(AlipayUserInfo record);
}

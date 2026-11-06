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

    /**
     * 综管台并列输入框的 AND 组合查询（仅未逻辑删除记录）。
     *
     * <p>四个筛选参数之间是 AND，{@code null} 表示该条件未填、不参与谓词；{@code DELETE_FLAG} 恒参与谓词。
     * <b>MUST 由调用方保证至少一个筛选参数非空</b>，否则退化成整表扫描。
     */
    List<AlipayUserInfo> selectByFilters(@Param("msisdn") String msisdn,
                                         @Param("cardId") String cardId,
                                         @Param("thirdUserId") String thirdUserId,
                                         @Param("reqContractNo") String reqContractNo);

    int insert(AlipayUserInfo record);

    int updateByThirdUserId(AlipayUserInfo record);

    int updatePaymentChannel(AlipayUserInfo record);

    int updateMsisdnByThirdUserId(@Param("thirdUserId") String thirdUserId, @Param("msisdn") String msisdn);
}

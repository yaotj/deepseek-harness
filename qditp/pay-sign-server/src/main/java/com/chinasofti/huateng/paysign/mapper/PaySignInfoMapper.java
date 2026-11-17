package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface PaySignInfoMapper {
    PaySignInfo selectBySeq(
                            @Param("requestSignSeq") String requestSignSeq,
                            @Param("paymentVendor") String paymentVendor);

    PaySignInfo selectByUserAndVendor(
                            @Param("thirdUserId") String thirdUserId,
                            @Param("paymentVendor") String paymentVendor);

    int insert(PaySignInfo record);

    int updateBySeq(PaySignInfo record);

    int deleteByUserAndVendor(
                            @Param("thirdUserId") String thirdUserId,
                            @Param("paymentVendor") String paymentVendor);

    int updateDisplayAccountByThirdUserId(@Param("thirdUserId") String thirdUserId,
                                         @Param("displayAccount") String displayAccount);

    int markSigned(@Param("requestSignSeq") String requestSignSeq,
                   @Param("payAccountId") String payAccountId,
                   @Param("payAgreementNo") String payAgreementNo,
                   @Param("signTime") LocalDateTime signTime);

    int markSignFailed(@Param("requestSignSeq") String requestSignSeq);

    int markUnsigned(@Param("requestSignSeq") String requestSignSeq,
                     @Param("terminationTime") LocalDateTime terminationTime);

    int reactivateForResign(@Param("requestSignSeq") String requestSignSeq);

    String selectSignStatusBySeq(@Param("requestSignSeq") String requestSignSeq);

}

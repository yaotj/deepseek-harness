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

    /** SIGN_STATUS 状态机的 CAS UPDATE，前置状态写在 WHERE 里。 */
    int markSigned(@Param("requestSignSeq") String requestSignSeq,
                   @Param("payAccountId") String payAccountId,
                   @Param("payAgreementNo") String payAgreementNo,
                   @Param("signTime") LocalDateTime signTime);

    int markSignFailed(@Param("requestSignSeq") String requestSignSeq);

    int markUnsigned(@Param("requestSignSeq") String requestSignSeq,
                     @Param("terminationTime") LocalDateTime terminationTime);

    /** 复位重签：UNSIGNED / FAILED -> NOT_SIGNED，并清空上一轮的签约结果字段。 */
    int reactivateForResign(@Param("requestSignSeq") String requestSignSeq);

    /** CAS 返回 0 行后回查当前状态用，记录不存在时返回 null。 */
    String selectSignStatusBySeq(@Param("requestSignSeq") String requestSignSeq);

}

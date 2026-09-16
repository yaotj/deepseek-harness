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

    // 新增：根据用户ID和支付渠道查询
    PaySignInfo selectByUserAndVendor(
                            @Param("thirdUserId") String thirdUserId,
                            @Param("paymentVendor") String paymentVendor);

    // 新增：纯INSERT
    int insert(PaySignInfo record);

    // 新增：根据签约流水号更新
    int updateBySeq(PaySignInfo record);

    // 新增：根据用户ID和支付渠道删除
    int deleteByUserAndVendor(
                            @Param("thirdUserId") String thirdUserId,
                            @Param("paymentVendor") String paymentVendor);

    // 新增：根据用户ID批量更新签约展示账号
    int updateDisplayAccountByThirdUserId(@Param("thirdUserId") String thirdUserId,
                                         @Param("displayAccount") String displayAccount);

    /**
     * SIGN_STATUS 状态机的 CAS UPDATE，前置状态写在 WHERE 里。
     * <p>
     * 迁移白名单见 {@code docs/domain/state-machines.md} 与 mapper XML 注释。
     * 改 SIGN_STATUS **MUST** 走这 4 条，**NEVER** 再用 {@link #updateBySeq},
     * 后者 WHERE 只有 REQUEST_SIGN_SEQ，会让迟到的签约回调覆盖已解约状态。
     * 返回 0 行**不等于失败**：可能是幂等重放（当前已是目标态），也可能是真冲突，
     * 调用方 **MUST** 用 {@link #selectSignStatusBySeq} 回查后再决定。
     * </p>
     */
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

    // 废弃：原有的 upsert/MERGE
    // int upsert(PaySignInfo record);
}

package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserPayChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Component
public interface UserPayChannelMapper {
    UserPayChannel selectByThirdUserIdAndCardTypeAndChannel(@Param("thirdUserId") String thirdUserId,
                                                            @Param("cardType") String cardType,
                                                            @Param("channel") String channel);

    List<UserPayChannel> selectByThirdUserIdAndCardTypeAndCardId(@Param("thirdUserId") String thirdUserId,
                                                                 @Param("cardType") String cardType,
                                                                 @Param("cardId") String cardId);

    /**
     * 按签约流水号定位支付通道，供 pay-sign-server 在 IF8A-75 补建解约申请时取 CARD_ID / CARD_TYPE。
     */
    UserPayChannel selectByReqContractNo(@Param("reqContractNo") String reqContractNo);

    /**
     * 统计该用户名下剩余的支付通道条数，用于判断「刚删掉的是不是最后一个签约渠道」。
     */
    int countByThirdUserId(@Param("thirdUserId") String thirdUserId);

    int insert(UserPayChannel record);

    /**
     * 把支付域返回的 {@code PAY_ACCOUNT_ID} 回写到通道行（IF8A-77 唯一写入点，2.0.63 新增）。
     *
     * @param reqContractNo 签约流水号，即 {@code REQ_CONTRACT_NO}
     * @param payAccountId  支付中心侧付款账号标识
     * @param updateTms     回写时间
     * @return 实际更新行数
     */
    int updatePayAccountIdByReqContractNo(@Param("reqContractNo") String reqContractNo,
                                         @Param("payAccountId") String payAccountId,
                                         @Param("updateTms") LocalDateTime updateTms);

    int deleteByThirdUserIdAndCardTypeAndChannel(@Param("thirdUserId") String thirdUserId,
                                                 @Param("cardType") String cardType,
                                                 @Param("channel") String channel);
}

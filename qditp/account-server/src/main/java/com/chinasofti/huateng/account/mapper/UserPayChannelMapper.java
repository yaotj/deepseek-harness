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
     *
     * <p>REQ_CONTRACT_NO 上没有唯一约束，理论上可能重复，取最新一条即可——同一签约流水对应的
     * 票卡信息不会变。</p>
     */
    UserPayChannel selectByReqContractNo(@Param("reqContractNo") String reqContractNo);

    /**
     * 统计该用户名下剩余的支付通道条数，用于判断「刚删掉的是不是最后一个签约渠道」。
     *
     * <p>口径是 thirdUserId 全量，不带 cardType / cardId——销户是用户级动作，只要该用户还有任何
     * 一条支付通道就不算解绑干净。<b>MUST 在 delete 之后调用</b>，同一事务内读到的是删除后的结果。</p>
     */
    int countByThirdUserId(@Param("thirdUserId") String thirdUserId);

    int insert(UserPayChannel record);

    /**
     * 把支付域返回的 {@code PAY_ACCOUNT_ID} 回写到通道行（IF8A-77 唯一写入点，2.0.63 新增）。
     *
     * <p>加这一列的目的是让运营页面「支付账号」列**本地可读**，不必再按渠道逐条打
     * {@code paySignClient.querySignInfoBySeq}（见 ADR-D30）。</p>
     *
     * <p><b>返回 0 行不是失败</b>：该签约流水在本表可能压根没有对应通道行（例如只在
     * {@code USER_ITP_REG_INFO} 上换了默认支付方式）。调用方 MUST 只记日志、
     * <b>NEVER 因此让 IF8A-77 返回失败</b> —— 这一列是展示用的补充信息，不是业务结果。</p>
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

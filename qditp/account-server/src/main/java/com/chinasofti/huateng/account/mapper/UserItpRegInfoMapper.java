package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.page.RegStatView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface UserItpRegInfoMapper {
    UserItpRegInfo selectActiveByThirdUserId(@Param("thirdUserId") String thirdUserId);


    UserItpRegInfo selectActiveUniqueCard(@Param("thirdUserId") String thirdUserId,
                                        @Param("issueOrgCode") String issueOrgCode,
                                        @Param("cardType") String cardType);


    UserItpRegInfo selectActiveByThirdUserIdAndCardIdAndCardType(@Param("thirdUserId") String thirdUserId,
                                                                  @Param("cardId") String cardId,
                                                                  @Param("cardType") String cardType);


    UserItpRegInfo selectAnyByThirdUserIdAndCardIdAndCardType(@Param("thirdUserId") String thirdUserId,
                                                               @Param("cardId") String cardId,
                                                               @Param("cardType") String cardType);

    UserItpRegInfo selectActiveByCardId(@Param("cardId") String cardId);


    List<UserItpRegInfo> selectActiveListByThirdUserId(@Param("thirdUserId") String thirdUserId);


    List<UserItpRegInfo> selectActiveListByMsisdn(@Param("msisdn") String msisdn);


    List<UserItpRegInfo> selectListByThirdUserId(@Param("thirdUserId") String thirdUserId);


    List<UserItpRegInfo> selectListByMsisdn(@Param("msisdn") String msisdn);


    List<UserItpRegInfo> selectListByCardId(@Param("cardId") String cardId);


    List<UserItpRegInfo> selectListByFiltersAndDelYn(@Param("msisdn") String msisdn,
                                                     @Param("cardId") String cardId,
                                                     @Param("thirdUserId") String thirdUserId,
                                                     @Param("reqContractNo") String reqContractNo,
                                                     @Param("delYn") Integer delYn);


    List<UserItpRegInfo> selectListByCardIds(@Param("cardIds") List<String> cardIds);


    UserItpRegInfo selectByThirdUserIdAndCompanionFlag(@Param("thirdUserId") String thirdUserId,
                                                        @Param("companionFlag") String companionFlag);

    int updateChannelDefaultContractById(UserItpRegInfo record);

    int updateDefaultPayChannelById(UserItpRegInfo record);

    int clearDefaultPayChannelById(@Param("id") Integer id);


    int updateActiveHceDataByCardId(@Param("cardId") String cardId, @Param("hceData") String hceData);


    int updateMsisdnByThirdUserId(@Param("thirdUserId") String thirdUserId, @Param("msisdn") String msisdn);


    int updateCancelByThirdUserId(@Param("thirdUserId") String thirdUserId,
                                  @Param("unRegTms") java.time.LocalDateTime unRegTms);


    List<UserItpRegInfo> selectAnyListByThirdUserIdForUpdate(@Param("thirdUserId") String thirdUserId);


    int deleteCanceledByThirdUserId(@Param("thirdUserId") String thirdUserId);

    int insert(UserItpRegInfo record);



    List<RegStatView> countGroupByCardType(@Param("startDate") String startDate,
                                           @Param("endDate") String endDate);
}

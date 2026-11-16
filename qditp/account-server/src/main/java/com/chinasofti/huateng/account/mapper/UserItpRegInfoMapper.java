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

    /**
     * 按用户、所属方原值和映射后卡类型查询有效唯一卡（不含 ticketLimit=2 的同行卡）。
     */
    UserItpRegInfo selectActiveUniqueCard(@Param("thirdUserId") String thirdUserId,
                                        @Param("issueOrgCode") String issueOrgCode,
                                        @Param("cardType") String cardType);

    /**
     * 查询指定用户、逻辑卡号和票卡类型的有效开户记录。
     */
    UserItpRegInfo selectActiveByThirdUserIdAndCardIdAndCardType(@Param("thirdUserId") String thirdUserId,
                                                                  @Param("cardId") String cardId,
                                                                  @Param("cardType") String cardType);

    /**
     * 查询指定用户、逻辑卡号和票卡类型的最新一条开户记录，<b>不过滤 {@code DEL_YN}</b>。
     */
    UserItpRegInfo selectAnyByThirdUserIdAndCardIdAndCardType(@Param("thirdUserId") String thirdUserId,
                                                               @Param("cardId") String cardId,
                                                               @Param("cardType") String cardType);

    UserItpRegInfo selectActiveByCardId(@Param("cardId") String cardId);

    /**
     * 运营后台查询同一用户的全部有效票卡。
     */
    List<UserItpRegInfo> selectActiveListByThirdUserId(@Param("thirdUserId") String thirdUserId);

    /**
     * 运营后台按手机号查询全部有效票卡。
     */
    List<UserItpRegInfo> selectActiveListByMsisdn(@Param("msisdn") String msisdn);

    /**
     * 运营查询专用：按用户查全部开户记录（含已注销），<b>不带 {@code DEL_YN} 过滤、不加锁</b>。
     */
    List<UserItpRegInfo> selectListByThirdUserId(@Param("thirdUserId") String thirdUserId);

    /**
     * 运营查询专用：按手机号查全部开户记录（含已注销），不带 {@code DEL_YN} 过滤。
     */
    List<UserItpRegInfo> selectListByMsisdn(@Param("msisdn") String msisdn);

    /**
     * 运营查询专用：按逻辑卡号查全部开户记录（含已注销），不带 {@code DEL_YN} 过滤。
     */
    List<UserItpRegInfo> selectListByCardId(@Param("cardId") String cardId);

    /**
     * 运营查询专用：综管台并列输入框的 AND 组合查询。
     *
     * <p>四个筛选参数之间是 AND，{@code null} 表示该条件未填、不参与谓词；
     * {@code DEL_YN} 恒参与谓词，因此 {@code WHERE} 永不为空（Druid WallFilter 禁「恒真条件」）。
     * <b>MUST 由调用方保证至少一个筛选参数非空</b>，否则退化成整表扫描。
     */
    List<UserItpRegInfo> selectListByFiltersAndDelYn(@Param("msisdn") String msisdn,
                                                     @Param("cardId") String cardId,
                                                     @Param("thirdUserId") String thirdUserId,
                                                     @Param("reqContractNo") String reqContractNo,
                                                     @Param("delYn") Integer delYn);

    /**
     * 综管台批量导入查询：按卡号列表批量查，口径与 {@link #selectListByCardId} 完全一致
     * （不过滤 DEL_YN，含已注销）。
     */
    List<UserItpRegInfo> selectListByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * IF8A-77 定位第三方渠道用户（支持互联互通卡等未知发卡机构）。
     *
     * <p>只按 {@code THIRD_USER_ID} + {@code COMPANION_FLAG='C'} 定位，
     * 不对 {@code ISSUE_ORG_CODE} 做严格校验 —— 互联互通卡（如 {@code 0009}）可能不在
     * {@code CardIssueOrgEnum} 中，但仍需支持更换默认支付渠道。
     *
     * <p>取最新一条（{@code ORDER BY REG_TMS DESC FETCH FIRST 1 ROWS ONLY}），
     * 与 {@link #selectByThirdUserIdAndIssueOrgCodeAndCompanionFlag} 的区别是放弃机构码匹配。
     */
    UserItpRegInfo selectByThirdUserIdAndCompanionFlag(@Param("thirdUserId") String thirdUserId,
                                                        @Param("companionFlag") String companionFlag);

    int updateChannelDefaultContractById(UserItpRegInfo record);

    int updateDefaultPayChannelById(UserItpRegInfo record);

    int clearDefaultPayChannelById(@Param("id") Integer id);

    /**
     * 按有效逻辑卡号更新 HCE 卡数据。
     */
    int updateActiveHceDataByCardId(@Param("cardId") String cardId, @Param("hceData") String hceData);

    /**
     * 更新用户手机号。
     */
    int updateMsisdnByThirdUserId(@Param("thirdUserId") String thirdUserId, @Param("msisdn") String msisdn);

    /**
     * IF8A-42 销户：把该用户全部有效开户记录置为已注销（{@code DEL_YN} 由 1 改 0）。
     */
    int updateCancelByThirdUserId(@Param("thirdUserId") String thirdUserId,
                                  @Param("unRegTms") java.time.LocalDateTime unRegTms);

    /**
     * 查询该用户的全部开户记录并<b>加行锁（{@code for update}）</b>，不过滤 {@code DEL_YN}，
     * 供销户归档判断使用。
     */
    List<UserItpRegInfo> selectAnyListByThirdUserIdForUpdate(@Param("thirdUserId") String thirdUserId);

    /**
     * 销户归档：物理删除该用户全部<b>已注销</b>的开户记录。
     */
    int deleteCanceledByThirdUserId(@Param("thirdUserId") String thirdUserId);

    int insert(UserItpRegInfo record);

    /**
     * 注册量统计：按 {@code CARD_TYPE} 分组计数，可选 {@code REG_TMS} 日期窗（含边界）。
     */
    /**
     * MUST 用 <where>、NEVER 写 where 1 = 1 + 全可选 <if>（Druid 判 select alway true condition，2.0.73 修复）。
     */
    List<RegStatView> countGroupByCardType(@Param("startDate") String startDate,
                                           @Param("endDate") String endDate);
}

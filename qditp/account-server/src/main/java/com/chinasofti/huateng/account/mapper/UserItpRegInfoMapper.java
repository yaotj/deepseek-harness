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
     * 查询指定用户和票卡类型的有效开户记录。
     */
    UserItpRegInfo selectActiveByThirdUserIdAndCardType(@Param("thirdUserId") String thirdUserId,
                                                         @Param("cardType") String cardType);

    /** 查询指定用户、逻辑卡号和票卡类型的有效开户记录。 */
    UserItpRegInfo selectActiveByThirdUserIdAndCardIdAndCardType(@Param("thirdUserId") String thirdUserId,
                                                                  @Param("cardId") String cardId,
                                                                  @Param("cardType") String cardType);

    /**
     * 查询指定用户、逻辑卡号和票卡类型的最新一条开户记录，<b>不过滤 {@code DEL_YN}</b>。
     *
     * <p>专供「已销户后仍需解绑支付渠道」场景（IF8A-42 先置 {@code DEL_YN=0}、IF8A-75 随后逐渠道解绑）。
     * 常规链路 <b>MUST</b> 用 {@link #selectActiveByThirdUserIdAndCardIdAndCardType}，
     * <b>NEVER</b> 用本方法绕过有效性校验——调用方必须自己判断 {@code delYn} 并明确接受已注销记录。</p>
     */
    UserItpRegInfo selectAnyByThirdUserIdAndCardIdAndCardType(@Param("thirdUserId") String thirdUserId,
                                                               @Param("cardId") String cardId,
                                                               @Param("cardType") String cardType);

    UserItpRegInfo selectActiveByCardId(@Param("cardId") String cardId);

    /** 运营后台查询同一用户的全部有效票卡。 */
    List<UserItpRegInfo> selectActiveListByThirdUserId(@Param("thirdUserId") String thirdUserId);

    /** 运营后台按手机号查询全部有效票卡。 */
    List<UserItpRegInfo> selectActiveListByMsisdn(@Param("msisdn") String msisdn);

    /**
     * 运营查询专用：按用户查全部开户记录（含已注销），<b>不带 {@code DEL_YN} 过滤、不加锁</b>。
     *
     * <p>与 {@link #selectAnyListByThirdUserIdForUpdate} 的差别只在锁：本方法不持锁，
     * 供只读展示用；归档判断仍 MUST 用带锁版本。</p>
     */
    List<UserItpRegInfo> selectListByThirdUserId(@Param("thirdUserId") String thirdUserId);

    /** 运营查询专用：按手机号查全部开户记录（含已注销），不带 {@code DEL_YN} 过滤。 */
    List<UserItpRegInfo> selectListByMsisdn(@Param("msisdn") String msisdn);

    /**
     * 运营查询专用：按逻辑卡号查全部开户记录（含已注销），不带 {@code DEL_YN} 过滤。
     *
     * <p>注销未归档期间同一卡号可能与重新开户的有效记录并存，故返回 {@code List}。</p>
     */
    List<UserItpRegInfo> selectListByCardId(@Param("cardId") String cardId);

    /**
     * 综管台批量导入查询：按卡号列表批量查，口径与 {@link #selectListByCardId} 完全一致
     * （不过滤 DEL_YN，含已注销）。调用方 MUST 限制列表长度（上限 500）。
     */
    List<UserItpRegInfo> selectListByCardIds(@Param("cardIds") List<String> cardIds);

    UserItpRegInfo selectByThirdUserIdAndCardIssueCodeAndCompanionFlag(@Param("thirdUserId") String thirdUserId,
                                                                       @Param("cardIssueCode") String cardIssueCode,
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
     *
     * <p>{@code DEL_YN} 的语义是 <b>1 = 有效、0 = 已注销</b>（见 {@code account-server-schema.sql}
     * 的列注释），与直觉相反，改动前 <b>MUST</b> 先确认。</p>
     *
     * <p>一个 {@code thirdUserId} 可能有多条有效记录（多卡），本语句一次覆盖全部，
     * 因此返回值是被注销的票卡数。WHERE 带 {@code DEL_YN = 1} 使其天然幂等：
     * 重复调用返回 0 而不是报错，调用方 <b>MUST</b> 把 0 当成「已注销」而非失败。</p>
     */
    int updateCancelByThirdUserId(@Param("thirdUserId") String thirdUserId,
                                  @Param("unRegTms") java.time.LocalDateTime unRegTms);

    /**
     * 查询该用户的全部开户记录并<b>加行锁（{@code for update}）</b>，不过滤 {@code DEL_YN}，
     * 供销户归档判断使用。
     *
     * <p>与 {@link #selectActiveListByThirdUserId} 的区别有两点：不带 {@code DEL_YN = 1}
     * （归档发生在 IF8A-42 置注销态之后，用有效口径查必然是空集），以及带 {@code for update}。</p>
     *
     * <p><b>锁 MUST 在 {@code UserPayChannelMapper.countByThirdUserId} 之前获取。</b>
     * 同一用户多渠道并发解绑时，后到的事务会阻塞在本查询上，等前一个提交后再 count，才能读到
     * 「全部渠道都已删除」的最新结果。顺序颠倒会让两个事务各自看到对方未提交的渠道仍存在、
     * 双方都跳过归档，用户信息永久残留且没有补偿路径。</p>
     *
     * <p>本方法持锁到事务结束，<b>NEVER</b> 用于常规查询。</p>
     */
    List<UserItpRegInfo> selectAnyListByThirdUserIdForUpdate(@Param("thirdUserId") String thirdUserId);

    /**
     * 销户归档：物理删除该用户全部<b>已注销</b>的开户记录。
     *
     * <p>WHERE 固定带 {@code DEL_YN = 0}（0 = 已注销），因此不会误删并发期间新开户产生的有效记录。
     * 调用前 <b>MUST</b> 已把这些行的快照写入 {@code USER_ITP_REG_LOG}，且 <b>MUST</b> 已确认该用户
     * 在 {@code APP_USER_PAY_CHANNEL} 中不再有任何支付通道——原表行删掉之后，
     * {@code selectAnyByThirdUserIdAndCardIdAndCardType} 那条「忽略 DEL_YN 再查一次」的兜底就失效了，
     * 残留的支付通道将无法再被清理。</p>
     */
    int deleteCanceledByThirdUserId(@Param("thirdUserId") String thirdUserId);

    int insert(UserItpRegInfo record);

    /**
     * 注册量统计：按 {@code CARD_TYPE} 分组计数，可选 {@code REG_TMS} 日期窗（含边界）。
     *
     * <p>只读聚合，不带 {@code DEL_YN} 过滤（统计口径含有效与已注销）。
     * 日期参数为闭区间字符串（{@code yyyy-MM-dd} 或时间戳），由调用方保证格式。</p>
     */
    List<RegStatView> countGroupByCardType(@Param("startDate") String startDate,
                                           @Param("endDate") String endDate);
}

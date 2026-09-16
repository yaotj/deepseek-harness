package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface UserAccEmployeeCardMapper {
    UserAccEmployeeCard selectByCardNo(@Param("cardNo") String cardNo);

    /**
     * 按手机号查该号码下的「活跃」员工码。
     *
     * <p>「活跃」的口径是 {@code CARD_STATUS = 1}（正常），**不是「非注销」**：2 禁用、3 未激活、
     * 4 注销都不返回。换号链路靠它判断旧号名下有没有需要跟着迁移的员工码，
     * 改这个判据 MUST 同步 {@code UserAccEmployeeCardMapper.xml}。</p>
     *
     * <p><b>返回行的 {@code photoUrl} 恒为 {@code null}</b>：本查询走 XML 里的
     * {@code LeanColumnList}，刻意不取 CLOB 列 {@code PHOTO_URL}（单行可达 250KB，
     * 一次可能返回多张卡）。<b>调用方 NEVER 读这些行的 photoUrl</b>，要照片改走
     * {@link #selectByCardNo}。</p>
     */
    List<UserAccEmployeeCard> selectActiveByPhone(@Param("phone") String phone);

    int insert(UserAccEmployeeCard record);

    int update(UserAccEmployeeCard record);

    int updateEmployeeInfo(UserAccEmployeeCard record);

    /**
     * 把员工码挂到 ITP 用户上。<b>CAS 语义</b>：WHERE 内含 {@code CARD_STATUS = 1} 与
     * 「{@code THIRD_USER_ID} 为空或已等于目标值」，因此
     * <ul>
     *   <li>影响 1 行 = 绑定成功或本就是同一用户（幂等重放）；</li>
     *   <li>影响 0 行 = 卡不在正常态，或已被**别的**用户占用——调用方 MUST 当失败处理，
     *       NEVER 忽略返回值，否则会把别人的员工码静默算到当前用户名下。</li>
     * </ul>
     */
    int updateThirdUserId(@Param("cardNo") String cardNo, @Param("thirdUserId") String thirdUserId);

    /**
     * 换号时把该 ITP 用户名下所有员工码的 {@code PHONE} 跟着改成新号。
     *
     * <p>存在的理由：{@link #selectActiveByPhone} 是**按手机号**找员工码的唯一入口，若换号后这里
     * 不同步，旧号仍留在员工码行上，之后再按新号查就查不到本人的卡。</p>
     *
     * <p>按 {@code THIRD_USER_ID} 全量改（与 {@code UserItpRegInfoMapper.updateMsisdnByThirdUserId}
     * 同口径），**不带 `CARD_STATUS` 过滤**：已禁用 / 未激活的卡也要跟着迁，否则重新激活后手机号还是旧的。
     * 影响 0 行是正常情况（该用户名下没有员工码），调用方 **NEVER 当失败处理**。</p>
     */
    int updatePhoneByThirdUserId(@Param("thirdUserId") String thirdUserId, @Param("phone") String phone);
}

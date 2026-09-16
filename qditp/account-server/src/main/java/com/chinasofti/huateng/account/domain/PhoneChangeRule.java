package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import org.springframework.util.StringUtils;

/**
 * 换手机号的前置条件，<b>纯函数、无状态、不依赖任何 Bean</b>（形态照 {@link ArchiveDecision}）。
 *
 * <p>2026-09-11 从 {@code PhoneChangeServiceImpl.updatePhone} / {@code updatePhoneLocally} 抽出，
 * <b>判断顺序与判据逐条照搬、行为不变</b>。抽出的动机是这组前置条件原先夹在
 * 「查库 → 判断 → 改三张表 → 落日志表」的中间，要断言它就得连 mapper 一起 mock。</p>
 *
 * <p>本类<b>只回答「该不该改」，绝不回答「怎么改」</b>：真正的写入（{@code USER_ITP_REG_INFO.MSISDN}、
 * {@code USER_ACC_EMPLOYEE_CARD.PHONE}、{@code USER_PHONE_CHANGE_LOG} 三张表同事务）仍在服务层。</p>
 *
 * <p>三条 NEVER：</p>
 * <ul>
 *   <li><b>NEVER 在本类里查库或调 RPC</b>。调用方 MUST 自己把「有效开户记录」查好传进来。</li>
 *   <li><b>NEVER 给 {@link #decide} 补 {@code isActive()} 判断</b>。原实现只判
 *       {@code regInfo == null}，不判 {@code isActive()}——因为查询方法
 *       {@code selectActiveByThirdUserId} 已经在 SQL 侧带了 {@code DEL_YN = 1} 口径
 *       （见 {@code UserItpRegInfoMapper.xml} 的 {@code Del_Yn_Active_Filter}）。补一层
 *       Java 判断不是「更严」，而是<b>把权威从 SQL 挪到 Java、且两处口径会各自漂移</b>；
 *       真要收紧 MUST 先改 SQL 口径。注意 {@code requestAddPayChannel} 那条链路<b>确实</b>多判了
 *       {@code isActive()}，两处不一致是<b>已知的、有意保留的现状</b>。</li>
 *   <li>{@link Precondition#UNCHANGED} 与 {@link Precondition#NO_ACTIVE_USER} <b>MUST 保持可区分</b>：
 *       前者对 APP 返成功（换号是幂等的，重复提交同一号码不该报错），后者返失败。
 *       <b>NEVER 合并成一个 boolean</b>。</li>
 * </ul>
 */
public final class PhoneChangeRule {

    /** 前置条件的三种结论。 */
    public enum Precondition {
        /** 没有有效开户记录，换号失败。 */
        NO_ACTIVE_USER,
        /** 新旧号相同，不需要改库、也不需要向支付域投递，对 APP 仍返成功。 */
        UNCHANGED,
        /** 可以改库并在提交后向支付域投递。 */
        PROCEED
    }

    private PhoneChangeRule() {
    }

    /**
     * 入口级必填校验：{@code thirdUserId} 与新号都 MUST 非空白。
     *
     * <p>这一步<b>刻意与 {@link #decide} 分开</b>：它在事务之外、查库之前就要拦掉，
     * 合并进 {@code decide} 会让调用方为了校验两个字符串先去查一次库。</p>
     */
    public static boolean hasRequiredFields(String thirdUserId, String newMsisdn) {
        return StringUtils.hasText(thirdUserId) && StringUtils.hasText(newMsisdn);
    }

    /**
     * 判定是否需要执行换号。
     *
     * <p>「新旧号相同」的判据是 {@code oldMsisdn != null && oldMsisdn.equals(newMsisdn)}，
     * <b>NEVER 改成 {@code Objects.equals}</b>：库里 {@code MSISDN} 为 NULL 的历史行必须走
     * {@link Precondition#PROCEED}（把号补上），而 {@code newMsisdn} 已由
     * {@link #hasRequiredFields} 保证非空，用 {@code Objects.equals} 结论虽相同但把
     * 「NULL 要补号」这个意图藏起来了。</p>
     *
     * <p><b>多卡用户口径</b>：{@code regInfo} 是 {@code selectActiveByThirdUserId} 取的「最新一条」，
     * 而后续 UPDATE 是按 {@code THIRD_USER_ID} 全量改。若该用户名下各行原本手机号不一致（历史脏数据），
     * 这里比对的只是其中一张卡，<b>NEVER 拿本方法的 UNCHANGED 当「所有卡都已是新号」的判据</b>。</p>
     *
     * @param regInfo   有效开户记录，允许为 {@code null}（表示查不到）
     * @param newMsisdn 新手机号，调用方 MUST 已 trim 且非空
     */
    public static Precondition decide(UserItpRegInfo regInfo, String newMsisdn) {
        if (regInfo == null) {
            return Precondition.NO_ACTIVE_USER;
        }
        String oldMsisdn = regInfo.getMsisdn();
        if (oldMsisdn != null && oldMsisdn.equals(newMsisdn)) {
            return Precondition.UNCHANGED;
        }
        return Precondition.PROCEED;
    }
}

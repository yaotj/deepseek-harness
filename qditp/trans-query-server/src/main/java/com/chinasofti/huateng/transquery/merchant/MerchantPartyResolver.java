package com.chinasofti.huateng.transquery.merchant;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 商户号（归属方 / 收款方）解析器 —— 按乘车日期决定用城交旧商户还是新商户。
 *
 * <p><b>本类所在的 {@code merchant/} 包是「被多个业务包共享的下沉能力」，不是业务包。</b>
 * 与 {@code ticket-server} 的 {@code ticket/merchant/MerchantPartyResolver} 是**有意保留的同形副本**：
 * 两个模块之间没有共享代码的通道（只有 {@code model} / {@code rpc}，而这是配置读取器、不是 DTO 也不是 Client），
 * 因此过渡期两边各留一份。**代价是 {@code app.trans.*} 五个键 MUST 两边同值**，
 * 否则同一笔订单在新旧链路会返回不同商户号；**NEVER 只改一边**。
 *
 * <p>ticket-server 侧的 {@code query/TransMerchantResolver} 已于 2026-09-14 删除（r770），
 * 该模块现在也只剩 {@code merchant/} 这一份。**NEVER 再新建第三份。**
 *
 * <p><b>依赖方向是单向的，NEVER 反过来</b>：本包只准依赖 Spring 与 JDK，
 * **NEVER 依赖 {@code query} / {@code station} / {@code service} / {@code controller} 任何一个**。
 *
 * <p>本类**没有**照搬 {@code TransMerchantResolver} 里的空方法
 * {@code resolveMerchantParties(String, Object)} —— 那个方法体只有一行注释、什么都不做，
 * 全仓无调用点，已随旧类一起删除。**NEVER 加回来**：
 * 要按记录填商户号，调用方自己按下面四个 getter 加 {@link #shouldUseOldMerchant} 组合，
 * 语义留在调用方比藏在一个 {@code Object} 入参里清楚。
 *
 * <p>已知未闭合项：{@code app.trans.merchant-change-date=20260901} 仍待业务确认
 * （见 {@code docs/ops/生产环境清单.md}）。**未配置变更日期时一律用新商户**，这是有意的默认值，
 * NEVER 改成默认旧商户 —— 那会让全部历史与当期记录一起回落城交商户。
 */
@Component
public class MerchantPartyResolver {

    private static final Logger log = LoggerFactory.getLogger(MerchantPartyResolver.class);

    /**
     * 变更日期只接受 8 位 {@code yyyyMMdd}。{@link #shouldUseOldMerchant} 是拿**字符串 compareTo**
     * 比大小的，位数一旦不对（比如误配成 {@code 2026-09-01} 或 {@code 202691}），比较结果就是
     * 字典序垃圾、且**不会抛异常**——每一笔订单都会静默拿到错的商户号。因此宁可启动失败。
     */
    private static final Pattern CHANGE_DATE_PATTERN = Pattern.compile("\\d{8}");

    /** 商户号变更日期，格式 yyyyMMdd */
    @Value("${app.trans.merchant-change-date:}")
    private String merchantChangeDate;

    /** 城交商户号（变更日期前使用） */
    @Value("${app.trans.old-attributable-party:}")
    private String oldAttributableParty;

    @Value("${app.trans.old-receiving-party:}")
    private String oldReceivingParty;

    /** 新商户号（变更日期后使用） */
    @Value("${app.trans.new-attributable-party:}")
    private String newAttributableParty;

    @Value("${app.trans.new-receiving-party:}")
    private String newReceivingParty;

    /**
     * 启动期校验 + 把生效配置打成一行指纹。
     *
     * <p><b>这行日志是「两份副本有没有跑偏」的唯一检测手段</b>：本类与 ticket-server 的同形副本
     * 各读一份 {@code app.trans.*}，两边配置分叉时**代码不会报错、编译更不会**，只会让同一笔订单
     * 在新旧链路返回不同商户号。核对方法是两个服务各 grep 一次
     * {@code MERCHANT_RULE_FINGERPRINT}，字符串不相等即已分叉。
     * <b>NEVER 删这行日志、NEVER 改指纹的字段顺序</b>（改了就与另一份对不上，等于把检测手段废掉）。
     *
     * <p>校验故意做成**启动失败**而不是打个 WARN 继续跑：商户号错配属资金归属问题，
     * 事后从数据里看不出来（记录上的商户号看着是「一个合法的商户号」），只能靠启动期拦住。
     */
    @PostConstruct
    void validateAndLogEffectiveRule() {
        if (StringUtils.hasText(merchantChangeDate)
                && !CHANGE_DATE_PATTERN.matcher(merchantChangeDate).matches()) {
            throw new IllegalStateException(
                    "app.trans.merchant-change-date 必须是 8 位 yyyyMMdd（或留空表示一律用新商户），实际="
                            + merchantChangeDate
                            + "；shouldUseOldMerchant 用字符串 compareTo 比日期，位数不对会静默算错商户号");
        }
        if (!StringUtils.hasText(merchantChangeDate)) {
            log.warn("app.trans.merchant-change-date 未配置，全部记录一律用新商户（这是有意的默认值，非缺陷）");
        }
        if (!StringUtils.hasText(newAttributableParty) || !StringUtils.hasText(newReceivingParty)) {
            log.warn("app.trans.new-attributable-party / new-receiving-party 有空值，出参商户号会是空串");
        }
        log.info("商户号规则生效配置 MERCHANT_RULE_FINGERPRINT={}", merchantRuleFingerprint());
    }

    /**
     * 生效配置指纹，字段顺序固定为
     * {@code changeDate|oldAttributable|oldReceiving|newAttributable|newReceiving}。
     *
     * <p>只用于与另一份副本比对，**NEVER 拿它参与业务判断**。
     */
    public String merchantRuleFingerprint() {
        return String.join("|",
                blankIfNull(merchantChangeDate),
                blankIfNull(oldAttributableParty),
                blankIfNull(oldReceivingParty),
                blankIfNull(newAttributableParty),
                blankIfNull(newReceivingParty));
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * 按乘车日期选出该记录应使用的商户号一对。
     *
     * <p><b>这是本类唯一的日期驱动出口</b>：调用方拿到的是不可分割的 {@link MerchantParty}，
     * 因此「老归属方 + 新收单方」这种混搭在编译期就不可表达。
     * 2026-09-14 之前是「四个单字段 getter + 一个 {@code shouldUseOldMerchant} 布尔」交给调用方自己拼，
     * 拼错了在数据里事后完全看不出来（库里就是两个正常商户号），只有对账资金流向不上才暴露。</p>
     *
     * @param rideDateStr 乘车日期，{@code yyyyMMdd} 或 {@code yyyyMMddHHmmss}，可为空（按新商户处理）
     */
    public MerchantParty resolveFor(String rideDateStr) {
        return shouldUseOldMerchant(rideDateStr) ? oldParty() : newParty();
    }

    /**
     * 强制取变更日期之前的老商户号（城交）一对。
     *
     * <p>供**按业务规则、与日期无关**地判定为老商户的场景使用（如闸机侧「单边 / 补站回退城交」）。
     * <b>NEVER 在「本该按日期选」的地方调它</b> —— 那等于绕过 {@link #resolveFor}。</p>
     */
    public MerchantParty oldParty() {
        return new MerchantParty(oldAttributableParty, oldReceivingParty, true);
    }

    /** 强制取变更日期之后的新商户号一对，约束同 {@link #oldParty()}。 */
    public MerchantParty newParty() {
        return new MerchantParty(newAttributableParty, newReceivingParty, false);
    }

    /**
     * 判断是否应使用旧商户（城交）。
     *
     * <p>2026-09-14 由 public 降级为 private：它单独暴露出去就意味着调用方要自己配对商户号，
     * 而那正是混搭错配的来源。<b>NEVER 改回 public</b>，需要按日期取值一律走 {@link #resolveFor}。</p>
     *
     * @param rideDateStr 乘车日期，{@code yyyyMMdd} 或 {@code yyyyMMddHHmmss}，可为空
     * @return true=使用城交商户，false=使用新商户
     */
    private boolean shouldUseOldMerchant(String rideDateStr) {
        if (!StringUtils.hasText(merchantChangeDate)) {
            return false;
        }
        if (!StringUtils.hasText(rideDateStr)) {
            return false;
        }
        String dateOnly = rideDateStr.length() >= 8 ? rideDateStr.substring(0, 8) : rideDateStr;
        return dateOnly.compareTo(merchantChangeDate) < 0;
    }
}

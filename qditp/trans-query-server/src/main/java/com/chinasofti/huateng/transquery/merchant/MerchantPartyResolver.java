package com.chinasofti.huateng.transquery.merchant;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/** 商户号（归属方 / 收款方）解析器 —— 按乘车日期决定用城交旧商户还是新商户。 */
@Component
public class MerchantPartyResolver {

    private static final Logger log = LoggerFactory.getLogger(MerchantPartyResolver.class);

    /** 变更日期只接受 8 位 {@code yyyyMMdd}。 */
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

    /** 启动期校验 + 把生效配置打成一行指纹。 */
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

    /** 生效配置指纹，字段顺序固定为 {@code changeDate|oldAttributable|oldReceiving|newAttributable|newReceiving}。 */
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
     * @param rideDateStr 乘车日期，{@code yyyyMMdd} 或 {@code yyyyMMddHHmmss}，可为空（按新商户处理）
     */
    public MerchantParty resolveFor(String rideDateStr) {
        return shouldUseOldMerchant(rideDateStr) ? oldParty() : newParty();
    }

    /** 强制取变更日期之前的老商户号（城交）一对。 */
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

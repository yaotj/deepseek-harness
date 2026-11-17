package com.chinasofti.huateng.ticket.merchant;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class MerchantPartyResolver {

    private static final Logger log = LoggerFactory.getLogger(MerchantPartyResolver.class);

    private static final Pattern CHANGE_DATE_PATTERN = Pattern.compile("\\d{8}");

    @Value("${app.trans.merchant-change-date:}")
    private String merchantChangeDate;

    @Value("${app.trans.old-attributable-party:}")
    private String oldAttributableParty;

    @Value("${app.trans.old-receiving-party:}")
    private String oldReceivingParty;

    @Value("${app.trans.new-attributable-party:}")
    private String newAttributableParty;

    @Value("${app.trans.new-receiving-party:}")
    private String newReceivingParty;

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


    public MerchantParty resolveFor(String rideDateStr) {
        return shouldUseOldMerchant(rideDateStr) ? oldParty() : newParty();
    }

    public MerchantParty oldParty() {
        return new MerchantParty(oldAttributableParty, oldReceivingParty, true);
    }

    public MerchantParty newParty() {
        return new MerchantParty(newAttributableParty, newReceivingParty, false);
    }


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

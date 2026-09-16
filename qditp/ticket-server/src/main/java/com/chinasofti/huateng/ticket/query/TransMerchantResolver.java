package com.chinasofti.huateng.ticket.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 商户号解析器 - 根据变更日期决定使用城交商户还是新商户。
 */
@Component
public class TransMerchantResolver {

    private static final Logger log = LoggerFactory.getLogger(TransMerchantResolver.class);

    /** 商户号变更日期，格式yyyyMMdd */
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
     * 解析商户号并填充到记录中。
     *
     * @param rideDateStr 乘车日期字符串 (yyyyMMddHHmmss 或 yyyyMMdd)
     * @param record      交易记录（will be mutated）
     */
    public void resolveMerchantParties(String rideDateStr, Object record) {
        // 由于通用性，直接暴露配置值供调用方使用
    }

    /**
     * 判断是否应使用旧商户（城交）。
     *
     * @param rideDateStr 乘车日期 (yyyyMMdd)
     * @return true=使用城交商户，false=使用新商户
     */
    public boolean shouldUseOldMerchant(String rideDateStr) {
        if (!StringUtils.hasText(merchantChangeDate)) {
            // 未配置变更日期，默认使用新商户
            return false;
        }
        if (!StringUtils.hasText(rideDateStr)) {
            return false;
        }
        // 只取前8位比较
        String dateOnly = rideDateStr.length() >= 8 ? rideDateStr.substring(0, 8) : rideDateStr;
        return dateOnly.compareTo(merchantChangeDate) < 0;
    }

    public String getOldAttributableParty() { return oldAttributableParty; }
    public String getOldReceivingParty() { return oldReceivingParty; }
    public String getNewAttributableParty() { return newAttributableParty; }
    public String getNewReceivingParty() { return newReceivingParty; }
}

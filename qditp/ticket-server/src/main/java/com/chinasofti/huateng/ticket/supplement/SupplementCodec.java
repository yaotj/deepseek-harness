package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** 补站域（IF5A-01 / IF5A-03 / IF8A-04）包内共享的字面量与编解码。 */
final class SupplementCodec {

    private static final Logger log = LoggerFactory.getLogger(SupplementCodec.class);

    /** 闸机 / BOM 报文里表示「站点未知」的哨兵值，与 {@code ticket.default-last-txn-station} 默认值一致。 */
    static final String STATION_UNKNOWN = "FFFF";

    /** {@code updateType=01} 乘客在付费区（闸机内侧）。 */
    static final String UPDATE_TYPE_PAID_AREA = "01";

    /** {@code updateType=00} 乘客在非付费区（闸机外侧）。 */
    static final String UPDATE_TYPE_FREE_AREA = "00";

    /** {@code providerId} 缺省回填值。 */
    static final String PROVIDER_ID_DEFAULT = "99";

    /** {@code issueChannelCode} 缺省回填值。 */
    static final String ISSUE_CHANNEL_DEFAULT = "01";

    /** 闸机报文 {@code handleResultCode} 成功值。 */
    static final String HANDLE_RESULT_SUCCESS = "000";

    /** 远端 {@code retCode} 成功值。 */
    static final String RET_SUCCESS = "0000";

    /** 金额零值，报文里是字符串而不是数值。 */
    static final String AMOUNT_ZERO = "0";

    private SupplementCodec() {
    }

    static String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /** 判断站点码是否为「未知站」。 */
    static boolean isUnknownStation(String stationCode, String configuredUnknown) {
        if (!StringUtils.hasText(stationCode)) {
            return true;
        }
        return STATION_UNKNOWN.equalsIgnoreCase(stationCode)
                || (StringUtils.hasText(configuredUnknown) && configuredUnknown.equalsIgnoreCase(stationCode));
    }

    /** 把 account-server 给的十进制 thirdUserId 归一成检票链路里的 {@code itpUserId} 形态： 去掉前导零后按渠道左补零（支付宝 10 位、其余 8 位）。 */
    static String normalizeDeviceThirdUserId(String decimalThirdUserId, String issueChannelCode) {
        if (!StringUtils.hasText(decimalThirdUserId)) {
            return decimalThirdUserId;
        }
        String decimal;
        try {
            decimal = Long.toString(Long.parseLong(decimalThirdUserId.trim()));
        } catch (NumberFormatException e) {
            log.error("thirdUserId 非十进制数字，无法按渠道补位，本次置空: {}", decimalThirdUserId);
            return null;
        }
        return leftPad(decimal, IssueChannelCodeEnum.thirdUserIdLength(issueChannelCode));
    }

    /** 左侧补零至指定长度，已满足长度时原值返回。 */
    private static String leftPad(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}

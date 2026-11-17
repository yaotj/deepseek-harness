package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public final class SupplementCodec {

    private static final Logger log = LoggerFactory.getLogger(SupplementCodec.class);

    static final String STATION_UNKNOWN = "FFFF";

    static final String UPDATE_TYPE_PAID_AREA = "01";

    static final String UPDATE_TYPE_FREE_AREA = "00";

    static final String PROVIDER_ID_DEFAULT = "99";

    static final String ISSUE_CHANNEL_DEFAULT = "01";

    static final String HANDLE_RESULT_SUCCESS = "000";

    static final String RET_SUCCESS = "0000";

    static final String AMOUNT_ZERO = "0";

    private SupplementCodec() {
    }

    static String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }


    public static boolean isUnknownStation(String stationCode, String configuredUnknown) {
        if (!StringUtils.hasText(stationCode)) {
            return true;
        }
        return STATION_UNKNOWN.equalsIgnoreCase(stationCode)
                || (StringUtils.hasText(configuredUnknown) && configuredUnknown.equalsIgnoreCase(stationCode));
    }

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

    private static String leftPad(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}

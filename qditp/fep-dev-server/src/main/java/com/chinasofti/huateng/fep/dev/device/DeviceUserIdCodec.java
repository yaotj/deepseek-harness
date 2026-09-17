package com.chinasofti.huateng.fep.dev.device;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;

/** 设备上送 {@code itpUserId} 的编码转换：十六进制转十进制并按渠道补齐零位。 */
@Component
public class DeviceUserIdCodec {
    private static final Logger log = LoggerFactory.getLogger(DeviceUserIdCodec.class);

    /** 将十六进制 itpUserId 转换为十进制字符串，并补齐到该渠道要求的位数。 */
    public String normalize(String itpUserId, String issueChannelCode) {
        if (!StringUtils.hasText(itpUserId)) {
            return itpUserId;
        }
        String decimal;
        try {
            decimal = new BigInteger(itpUserId.trim(), 16).toString(10);
        } catch (Exception e) {
            log.warn("itpUserId十六进制转十进制失败, itpUserId={}", itpUserId);
            return itpUserId;
        }
        return leftPad(decimal, IssueChannelCodeEnum.thirdUserIdLength(issueChannelCode));
    }

    /** 左侧补零至指定长度，已满足长度或为空时原值返回。 */
    private String leftPad(String value, int length) {
        if (!StringUtils.hasText(value) || value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}

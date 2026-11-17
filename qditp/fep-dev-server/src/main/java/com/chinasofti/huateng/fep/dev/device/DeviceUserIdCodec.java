package com.chinasofti.huateng.fep.dev.device;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;

@Component
public class DeviceUserIdCodec {
    private static final Logger log = LoggerFactory.getLogger(DeviceUserIdCodec.class);

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

    private String leftPad(String value, int length) {
        if (!StringUtils.hasText(value) || value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}

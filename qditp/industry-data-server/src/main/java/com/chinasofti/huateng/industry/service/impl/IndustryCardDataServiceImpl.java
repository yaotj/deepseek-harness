package com.chinasofti.huateng.industry.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.industry.service.IndustryCardDataService;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.app.RequestSignInsDataReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInsDataRespDTO;
import com.chinasofti.huateng.rpc.security.SecurityClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 行业卡数据生成服务默认实现。
 */
@Service
public class IndustryCardDataServiceImpl implements IndustryCardDataService {
    private static final Logger log = LoggerFactory.getLogger(IndustryCardDataServiceImpl.class);
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    private static final String RET_SUCCESS = "0000";

    private final SecurityClient securityClient;

    @Value("${industry.issue-channel-code:01}")
    private String defaultIssueChannelCode;

    @Value("${industry.ticket-type:0441}")
    private String defaultTicketType;

    @Value("${industry.timestamp-expire-hours:4}")
    private int timestampExpireHours;

    public IndustryCardDataServiceImpl(SecurityClient securityClient) {
        this.securityClient = securityClient;
    }

    @Override
    public IndustryCardDataBuildRespDTO buildCardData(IndustryCardDataBuildReqDTO request) {
        IndustryCardDataBuildRespDTO response = new IndustryCardDataBuildRespDTO();
        String validMsg = validateRequest(request);
        if (validMsg != null) {
            response.setRetCode("8001");
            response.setRetMsg(validMsg);
            return response;
        }

        String unsignedIndustryData = buildUnsignedIndustryData(request);
        response.setUnsignedIndustryData(unsignedIndustryData);
        log.info("行业数据签名前报文, request={}, unsignedIndustryData={}", JSON.toJSONString(request), unsignedIndustryData);

        RequestSignInsDataReqDTO signReq = new RequestSignInsDataReqDTO();
        signReq.setLogicNum(request.getCardId());
        signReq.setIndustryData(unsignedIndustryData);
        RequestSignInsDataRespDTO signResp = securityClient.requestSignInsData(signReq);
        log.info("调用security-server签名行业数据结果, request={}, response={}", JSON.toJSONString(signReq), JSON.toJSONString(signResp));

        if (!isSecuritySuccess(signResp)) {
            response.setRetCode(signResp == null ? "9999" : signResp.getRetCode());
            response.setRetMsg(signResp == null ? "security-server签名失败" : signResp.getRetMsg());
            return response;
        }

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setCardData(unsignedIndustryData + normalizeHex(signResp.getIndustryDataSign(), 16, ""));
        return response;
    }

    private String buildUnsignedIndustryData(IndustryCardDataBuildReqDTO request) {
        String thirdUserId = toFourByteHex(request.getThirdUserId());
        String ticketStatus = normalizeHex(request.getTicketStatus(), 2, "03");
        String lastStationCode = normalizeHex(firstNonBlankExcludeZero(request.getLastTxnStation(), request.getGateInStation()), 4, "FFFF");
        String handleDate = toFourByteHexByBizTime(firstNonBlank(request.getLastTxnTime(), request.getGateInTime()));
        String timeStamp = toFourByteHexByDateTime(LocalDateTime.now().plusHours(timestampExpireHours));
        String ticketLogicNo = normalizeHex(request.getCardId(), 16, "");
        String transSeq = toFourByteHex(request.getTxnSeq());
        String issueChannelCode = firstNonBlank(request.getIssueChannelCode(), defaultIssueChannelCode);
        String signChannelCode = firstNonBlank(request.getSignChannelCode(), "01");

        return thirdUserId
                + ticketStatus
                + lastStationCode
                + handleDate
                + timeStamp
                + ticketLogicNo
                + resolveTicketType(request.getCardType())
                + transSeq
                + normalizeHex(issueChannelCode, 2, "01")
                + normalizeHex(signChannelCode, 2, "01")
                + "01";
    }

    private String resolveTicketType(String cardType) {
        String normalizedCardType = firstNonBlank(cardType, defaultTicketType);
        return normalizeHex(CardTypeMapping.toIssueCardType(normalizedCardType), 4, "0441");
    }

    private String validateRequest(IndustryCardDataBuildReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }

    private boolean isSecuritySuccess(RequestSignInsDataRespDTO response) {
        return response != null
                && (RET_SUCCESS.equals(response.getRetCode()) || "200".equals(response.getRetCode()))
                && StringUtils.hasText(response.getIndustryDataSign());
    }

    private String toFourByteHex(String value) {
        long numericValue = 0L;
        if (StringUtils.hasText(value)) {
            String normalized = value.trim();
            try {
                numericValue = Long.parseLong(normalized);
            } catch (NumberFormatException e) {
                numericValue = Integer.toUnsignedLong(normalized.hashCode());
            }
        }
        return String.format("%08X", numericValue & 0xFFFFFFFFL);
    }

    private String toFourByteHexByBizTime(String bizTime) {
        if (!StringUtils.hasText(bizTime)) {
            return toFourByteHexByDateTime(LocalDateTime.now());
        }
        String trimmed = bizTime.trim();
        if (trimmed.length() != 14 || isAllZeros(trimmed)) {
            return toFourByteHexByDateTime(LocalDateTime.now());
        }
        try {
            LocalDateTime dateTime = LocalDateTime.of(
                    Integer.parseInt(trimmed.substring(0, 4)),
                    Integer.parseInt(trimmed.substring(4, 6)),
                    Integer.parseInt(trimmed.substring(6, 8)),
                    Integer.parseInt(trimmed.substring(8, 10)),
                    Integer.parseInt(trimmed.substring(10, 12)),
                    Integer.parseInt(trimmed.substring(12, 14)));
            return toFourByteHexByDateTime(dateTime);
        } catch (Exception e) {
            log.warn("业务时间解析失败, bizTime={}, 使用当前时间", trimmed);
            return toFourByteHexByDateTime(LocalDateTime.now());
        }
    }

    private String toFourByteHexByDateTime(LocalDateTime dateTime) {
        long seconds = ChronoUnit.SECONDS.between(BASE_TIME, dateTime);
        return String.format("%08X", seconds & 0xFFFFFFFFL);
    }

    private String normalizeHex(String value, int length, String defaultValue) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : defaultValue;
        if (normalized == null) {
            normalized = "";
        }
        if (normalized.length() > length) {
            normalized = normalized.substring(normalized.length() - length);
        }
        if (normalized.length() < length) {
            normalized = "0".repeat(length - normalized.length()) + normalized;
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private String firstNonBlankExcludeZero(String first, String second) {
        if (StringUtils.hasText(first) && !isAllZeros(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second) && !isAllZeros(second)) {
            return second.trim();
        }
        return null;
    }

    private boolean isAllZeros(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        for (char c : value.toCharArray()) {
            if (c != '0') {
                return false;
            }
        }
        return true;
    }
}

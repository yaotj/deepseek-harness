package com.chinasofti.huateng.industry.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.industry.service.IndustryCardDataService;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
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
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class IndustryCardDataServiceImpl implements IndustryCardDataService {
    private static final Logger log = LoggerFactory.getLogger(IndustryCardDataServiceImpl.class);
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    private static final String RET_SUCCESS = "0000";

    private static final int BODY_LENGTH = 64;

    private static final Pattern HEX_BODY = Pattern.compile("[0-9A-F]{" + BODY_LENGTH + "}");


    private static final int SIGN_LENGTH = 16;

    private static final int CARD_DATA_LENGTH = BODY_LENGTH + SIGN_LENGTH;

    private static final Pattern HEX_CARD_DATA = Pattern.compile("[0-9A-F]{" + CARD_DATA_LENGTH + "}");

    private static final int CHANNEL_LENGTH = 2;

    private static final int TICKET_TYPE_LENGTH = 4;

    private static final int LOGIC_NO_LENGTH = 16;

    private static final int CARD_VERSION_LENGTH = 2;

    private static final int TICKET_STATUS_LENGTH = 2;

    private static final int STATION_LENGTH = 4;

    private static final int FOUR_BYTE_HEX_LENGTH = 8;

    private static final String FOUR_BYTE_HEX_FORMAT = "%08X";
    private static final long FOUR_BYTE_MASK = 0xFFFFFFFFL;

    private static final int BIZ_TIME_LENGTH = 14;

    private interface SegmentReader {
        String read(IndustryCardDataServiceImpl service, IndustryCardDataBuildReqDTO request);
    }

    private record BodySegment(String name, int length, SegmentReader reader) { }

    private static final List<BodySegment> BODY_LAYOUT = List.of(
            new BodySegment("thirdUserId", FOUR_BYTE_HEX_LENGTH, (s, r) -> s.toFourByteHex(r.getThirdUserId())),
            new BodySegment("ticketStatus", TICKET_STATUS_LENGTH,
                    (s, r) -> s.normalizeHex(r.getTicketStatus(), TICKET_STATUS_LENGTH, "03")),
            new BodySegment("lastStationCode", STATION_LENGTH, (s, r) -> s.normalizeHex(
                    s.firstNonBlankExcludeZero(r.getLastTxnStation(), r.getGateInStation()),
                    STATION_LENGTH, "FFFF")),
            new BodySegment("handleDate", FOUR_BYTE_HEX_LENGTH, (s, r) -> s.toFourByteHexByBizTime(
                    s.firstNonBlank(r.getLastTxnTime(), r.getGateInTime()))),
            new BodySegment("timeStamp", FOUR_BYTE_HEX_LENGTH, (s, r) -> s.toFourByteHexByDateTime(
                    LocalDateTime.now().plusHours(s.timestampExpireHours))),
            new BodySegment("ticketLogicNo", LOGIC_NO_LENGTH,
                    (s, r) -> s.normalizeHex(r.getCardId(), LOGIC_NO_LENGTH, "")),
            new BodySegment("ticketType", TICKET_TYPE_LENGTH, (s, r) -> s.resolveTicketType(r.getCardType())),
            new BodySegment("transSeq", FOUR_BYTE_HEX_LENGTH, (s, r) -> s.toFourByteHex(r.getTxnSeq())),
            new BodySegment("issueChannelCode", CHANNEL_LENGTH, (s, r) -> s.resolveChannelCode(
                    "issueChannelCode", r.getIssueChannelCode(), s.defaultIssueChannelCode, r.getCardId())),
            new BodySegment("signChannelCode", CHANNEL_LENGTH, (s, r) -> s.resolveChannelCode(
                    "signChannelCode", r.getSignChannelCode(), "01", r.getCardId())),
            new BodySegment("cardVersion", CARD_VERSION_LENGTH, (s, r) -> "01"));

    static {
        int declaredLength = BODY_LAYOUT.stream().mapToInt(BodySegment::length).sum();
        if (declaredLength != BODY_LENGTH) {
            throw new IllegalStateException("行业卡码体段长之和 MUST 等于 " + BODY_LENGTH + "，当前为 " + declaredLength);
        }
    }

    private record RequiredField(String name, Function<IndustryCardDataBuildReqDTO, String> reader) { }

    private static final List<RequiredField> REQUIRED_FIELDS = List.of(
            new RequiredField("thirdUserId", IndustryCardDataBuildReqDTO::getThirdUserId),
            new RequiredField("cardId", IndustryCardDataBuildReqDTO::getCardId),
            new RequiredField("cardType", IndustryCardDataBuildReqDTO::getCardType));

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

        if (!HEX_BODY.matcher(unsignedIndustryData).matches()) {
            log.error("行业数据签名前报文含非十六进制字符, request={}, unsignedIndustryData={}",
                    JSON.toJSONString(request), unsignedIndustryData);
            response.setRetCode("8001");
            response.setRetMsg("行业数据码体非法（非十六进制），请检查 ticketStatus / lastTxnStation / issueChannelCode / signChannelCode 取值");
            return response;
        }

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


        String cardData = unsignedIndustryData + normalizeHex(signResp.getIndustryDataSign(), SIGN_LENGTH, "");
        if (!HEX_CARD_DATA.matcher(cardData).matches()) {
            log.error("行业数据卡数据非法, 期望{}位十六进制, actualLength={}, cardData={}, industryDataSign={}",
                    CARD_DATA_LENGTH, cardData.length(), cardData, signResp.getIndustryDataSign());
            response.setRetCode("8001");
            response.setRetMsg("行业数据卡数据非法（期望 " + CARD_DATA_LENGTH + " 位十六进制），请检查签名段长度");
            return response;
        }

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setCardData(cardData);
        return response;
    }

    private String buildUnsignedIndustryData(IndustryCardDataBuildReqDTO request) {
        StringBuilder body = new StringBuilder(BODY_LENGTH);
        for (BodySegment segment : BODY_LAYOUT) {
            String value = segment.reader().read(this, request);
            if (value == null || value.length() != segment.length()) {
                log.error("行业卡码体段长不符, segment={}, expectedLength={}, value={}, cardId={}",
                        segment.name(), segment.length(), value, request.getCardId());
            }
            body.append(value);
        }
        return body.toString();
    }

    private String resolveChannelCode(String field, String rawValue, String defaultValue, String cardId) {
        String value = firstNonBlank(rawValue, defaultValue);
        warnIfTruncated(field, value, CHANNEL_LENGTH, cardId);
        return normalizeHex(value, CHANNEL_LENGTH, "01");
    }

    private String resolveTicketType(String cardType) {
        String normalizedCardType = CardTypeMapping.toIssueCardType(firstNonBlank(cardType, defaultTicketType));
        if (CardTypeCodeEnum.usesQrTicketType(normalizedCardType)) {
            return CardTypeCodeEnum.QR_POSTPAID.getCode();
        }
        CardTypeCodeEnum known = CardTypeCodeEnum.fromCode(normalizedCardType);
        return normalizeHex(known != null ? known.getCode() : normalizedCardType, TICKET_TYPE_LENGTH,
                CardTypeCodeEnum.QR_POSTPAID.getCode());
    }

    private String validateRequest(IndustryCardDataBuildReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        for (RequiredField field : REQUIRED_FIELDS) {
            if (!StringUtils.hasText(field.reader().apply(request))) {
                return field.name() + "不能为空";
            }
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
        return String.format(FOUR_BYTE_HEX_FORMAT, numericValue & FOUR_BYTE_MASK);
    }

    private String toFourByteHexByBizTime(String bizTime) {
        if (!StringUtils.hasText(bizTime)) {
            return toFourByteHexByDateTime(LocalDateTime.now());
        }
        String trimmed = bizTime.trim();
        if (trimmed.length() != BIZ_TIME_LENGTH || isAllZeros(trimmed)) {
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
        return String.format(FOUR_BYTE_HEX_FORMAT, seconds & FOUR_BYTE_MASK);
    }

    private void warnIfTruncated(String field, String value, int length, String cardId) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= length) {
            return;
        }
        String kept = trimmed.substring(trimmed.length() - length).toUpperCase();
        if (IssueChannelCodeEnum.fromCode(kept) != null) {
            return;
        }
        log.warn("码体渠道位被截断为非法值, field={}, rawValue={}, kept={}, cardId={}", field, trimmed, kept, cardId);
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

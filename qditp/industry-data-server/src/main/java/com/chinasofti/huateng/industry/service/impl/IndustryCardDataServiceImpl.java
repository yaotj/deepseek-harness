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

/** 行业卡数据生成服务默认实现。 */
@Service
public class IndustryCardDataServiceImpl implements IndustryCardDataService {
    private static final Logger log = LoggerFactory.getLogger(IndustryCardDataServiceImpl.class);
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    private static final String RET_SUCCESS = "0000";

    /** 码体是定长 64 位十六进制。 */
    private static final Pattern HEX_BODY = Pattern.compile("[0-9A-F]{64}");

    /** 码体定长。 */
    private static final int BODY_LENGTH = 64;

    /** 段取值。 */
    private interface SegmentReader {
        String read(IndustryCardDataServiceImpl service, IndustryCardDataBuildReqDTO request);
    }

    /** 码体的一段：名字只用于日志，长度是定长契约。 */
    private record BodySegment(String name, int length, SegmentReader reader) { }

    /** 码体段布局。 */
    private static final List<BodySegment> BODY_LAYOUT = List.of(
            new BodySegment("thirdUserId", 8, (s, r) -> s.toFourByteHex(r.getThirdUserId())),
            new BodySegment("ticketStatus", 2, (s, r) -> s.normalizeHex(r.getTicketStatus(), 2, "03")),
            new BodySegment("lastStationCode", 4, (s, r) -> s.normalizeHex(
                    s.firstNonBlankExcludeZero(r.getLastTxnStation(), r.getGateInStation()), 4, "FFFF")),
            new BodySegment("handleDate", 8, (s, r) -> s.toFourByteHexByBizTime(
                    s.firstNonBlank(r.getLastTxnTime(), r.getGateInTime()))),
            new BodySegment("timeStamp", 8, (s, r) -> s.toFourByteHexByDateTime(
                    LocalDateTime.now().plusHours(s.timestampExpireHours))),
            new BodySegment("ticketLogicNo", 16, (s, r) -> s.normalizeHex(r.getCardId(), 16, "")),
            new BodySegment("ticketType", 4, (s, r) -> s.resolveTicketType(r.getCardType())),
            new BodySegment("transSeq", 8, (s, r) -> s.toFourByteHex(r.getTxnSeq())),
            new BodySegment("issueChannelCode", 2, (s, r) -> s.resolveChannelCode(
                    "issueChannelCode", r.getIssueChannelCode(), s.defaultIssueChannelCode, r.getCardId())),
            new BodySegment("signChannelCode", 2, (s, r) -> s.resolveChannelCode(
                    "signChannelCode", r.getSignChannelCode(), "01", r.getCardId())),
            new BodySegment("cardVersion", 2, (s, r) -> "01"));

    static {
        int declaredLength = BODY_LAYOUT.stream().mapToInt(BodySegment::length).sum();
        if (declaredLength != BODY_LENGTH) {
            throw new IllegalStateException("行业卡码体段长之和 MUST 等于 " + BODY_LENGTH + "，当前为 " + declaredLength);
        }
    }

    /** 必填项。 */
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

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setCardData(unsignedIndustryData + normalizeHex(signResp.getIndustryDataSign(), 16, ""));
        return response;
    }

    /** 按 {@link #BODY_LAYOUT} 逐段拼装签名前码体。 */
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

    /** 渠道位取值：入参为空取默认值，超长留痕后取右 2 位。 */
    private String resolveChannelCode(String field, String rawValue, String defaultValue, String cardId) {
        String value = firstNonBlank(rawValue, defaultValue);
        warnIfTruncated(field, value, 2, cardId);
        return normalizeHex(value, 2, "01");
    }

    /** 票种段：员工票与日票族的账户卡种各不相同，但行业码体统一压成二维码票种 0441。 */
    private String resolveTicketType(String cardType) {
        String normalizedCardType = CardTypeMapping.toIssueCardType(firstNonBlank(cardType, defaultTicketType));
        if (CardTypeCodeEnum.usesQrTicketType(normalizedCardType)) {
            return CardTypeCodeEnum.QR_POSTPAID.getCode();
        }
        CardTypeCodeEnum known = CardTypeCodeEnum.fromCode(normalizedCardType);
        return normalizeHex(known != null ? known.getCode() : normalizedCardType, 4,
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

    /** 码体渠道位是定长 2 位，normalizeHex 对超长入参取右侧 2 位。 */
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

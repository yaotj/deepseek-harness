package com.chinasofti.huateng.ticket.gate;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entrytxn.EntryTxnQueryService;
import com.chinasofti.huateng.ticket.station.StationLineResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;


@Component
class AlipayIndustryDetailAssembler {
    private static final Logger log = LoggerFactory.getLogger(AlipayIndustryDetailAssembler.class);

    private static final String ENTRY_ID_SUFFIX = "01";
    private static final String EXIT_ID_SUFFIX = "02";
    private static final DateTimeFormatter ORDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int FORMATTED_DATE_LENGTH = 19;
    private static final int CARD_ISSUE_CODE_LENGTH = 4;

    private final EntryTxnQueryService entryTxnQueryService;
    private final StationLineResolver stationLineResolver;
    private final String alipayCardIssueCode;
    private final Executor industryDetailExecutor;

    public AlipayIndustryDetailAssembler(
            EntryTxnQueryService entryTxnQueryService,
            StationLineResolver stationLineResolver,
            @Value("${alipay.trip.card-issue-code:0007}") String alipayCardIssueCode,
            @Qualifier("industryDetailExecutor") Executor industryDetailExecutor) {
        this.entryTxnQueryService = entryTxnQueryService;
        this.stationLineResolver = stationLineResolver;
        this.alipayCardIssueCode = alipayCardIssueCode;
        this.industryDetailExecutor = industryDetailExecutor;
    }
    public String assemble(NotifyVerifyResultReqDTO request) {
        try {
            String entryStationCode = request.getLastHandleStationCode();
            String exitStationCode = request.getHandleStationCode();
            CompletableFuture<String> entryDeviceFuture =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return entryTxnQueryService.queryEntryDevice(request.getCardId());
                        } catch (RuntimeException e) {
                            log.warn("查询进站设备异常, cardId={}", request.getCardId(), e);
                            return null;
                        }
                    }, industryDetailExecutor);
            CompletableFuture<RequestStationLineInfoResult> entryStationFuture =
                    CompletableFuture.supplyAsync(() -> queryStationLineInfo(entryStationCode), industryDetailExecutor);
            CompletableFuture<RequestStationLineInfoResult> exitStationFuture =
                    CompletableFuture.supplyAsync(() -> queryStationLineInfo(exitStationCode), industryDetailExecutor);

            Map<String, Object> stationInfo = buildStationInfoMap(
                    request, entryStationFuture.join(), exitStationFuture.join());
            applyEntryDeviceCode(stationInfo, entryDeviceFuture.join(), request.getCardId());
            stationInfo.put("cardIssueCode", resolveCardIssueCode(request));

            String industryDetail = JSON.toJSONString(stationInfo);
            log.info("IF1A-01 免密行业明细已组装, cardId={}, issueChannelCode={}, industryDetail={}",
                    request.getCardId(), request.getIssueChannelCode(), industryDetail);
            return industryDetail;
        } catch (Exception e) {
            log.error("IF1A-01 免密行业明细组装异常，本笔仍落单但出网 industryDetail 会缺 V1.2 键, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
            return null;
        }
    }
    private Map<String, Object> buildStationInfoMap(NotifyVerifyResultReqDTO request,
                                                    RequestStationLineInfoResult entryStationInfo,
                                                    RequestStationLineInfoResult exitStationInfo) {
        String entryStationCode = request.getLastHandleStationCode();
        String exitStationCode = request.getHandleStationCode();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderDate", resolveOrderDate(request));
        detail.put("cardNum", blankIfNull(request.getCardId()));
        detail.put("cardType", blankIfNull(request.getCardType()));
        detail.put("phone", blankIfNull(request.getPhone()));
        detail.put("channelAgreementNo", "");
        detail.put("ticketTransSeq", blankIfNull(request.getTicketTransSeq()));
        detail.put("tikcetTransSeq", blankIfNull(request.getTicketTransSeq()));
        detail.put("ticketFlag", resolveTicketFlag(request));
        detail.put("cumulativeType", resolveCumulativeType(request));

        detail.put("entryLineCode", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineCode, entryStationCode)));
        detail.put("entryLineName", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineName, entryStationCode)));
        detail.put("entryStationCode", blankIfNull(entryStationCode));
        detail.put("entryStationName", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getStationName, entryStationCode)));
        detail.put("entryDeviceCode", "");
        detail.put("entryDate", blankIfNull(convertHandleDateTime(request.getLastHandleDateTime())));
        detail.put("entryId", blankIfNull(buildTripId(request.getItpUserId(), request.getLastHandleDateTime(), ENTRY_ID_SUFFIX)));

        detail.put("exitId", blankIfNull(buildTripId(request.getItpUserId(), request.getHandleDateTime(), EXIT_ID_SUFFIX)));
        detail.put("exitLineCode", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineCode, exitStationCode)));
        detail.put("exitLineName", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineName, exitStationCode)));
        detail.put("exitStationCode", blankIfNull(exitStationCode));
        detail.put("exitStationName", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getStationName, exitStationCode)));
        detail.put("exitDeviceCode", blankIfNull(request.getDeviceId()));
        detail.put("exitDate", blankIfNull(convertHandleDateTime(request.getHandleDateTime())));

        detail.put("orderExpType", "03".equals(request.getTrxType()) ? "5" : "0");
        putOverTimeAmount(detail, request);
        return detail;
    }


    private String resolveTicketFlag(NotifyVerifyResultReqDTO request) {
        return StringUtils.hasText(request.getCompanionFlag()) ? request.getCompanionFlag().trim() : "N";
    }


    private String resolveCardIssueCode(NotifyVerifyResultReqDTO request) {
        if (IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode())) {
            return alipayCardIssueCode;
        }
        String code = request.getIssueChannelCode();
        if (!StringUtils.hasText(code)) {
            return "";
        }
        String trimmed = code.trim();
        StringBuilder padded = new StringBuilder(trimmed);
        while (padded.length() < CARD_ISSUE_CODE_LENGTH) {
            padded.insert(0, '0');
        }
        return padded.toString();
    }


    private String resolveCumulativeType(NotifyVerifyResultReqDTO request) {
        if (StringUtils.hasText(request.getCompanionFlag())) {
            return "03";
        }
        return "03".equals(request.getTrxType()) ? "02" : "01";
    }


    private void putOverTimeAmount(Map<String, Object> detail, NotifyVerifyResultReqDTO request) {
        String amount = request.getOvertimeAmount();
        if (!StringUtils.hasText(amount)) {
            return;
        }
        try {
            detail.put("overTimeAmount", Integer.valueOf(amount.trim()));
        } catch (NumberFormatException e) {
            log.warn("IF1A-01 超时金额非数字，overTimeAmount 缺省, cardId={}, overtimeAmount={}",
                    request.getCardId(), amount);
        }
    }

    private String resolveOrderDate(NotifyVerifyResultReqDTO request) {
        String exitDate = convertHandleDateTime(request.getHandleDateTime());
        if (exitDate != null && exitDate.length() >= FORMATTED_DATE_LENGTH) {
            return exitDate;
        }
        log.warn("IF1A-01 出站时间缺失或格式异常，orderDate 回落当前时刻, cardId={}, handleDateTime={}",
                request.getCardId(), request.getHandleDateTime());
        return LocalDateTime.now().format(ORDER_DATE_FORMATTER);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private void applyEntryDeviceCode(Map<String, Object> stationInfo, String entryDeviceCode, String cardId) {
        if (StringUtils.hasText(entryDeviceCode)) {
            stationInfo.put("entryDeviceCode", entryDeviceCode.trim());
            return;
        }
        log.warn("IF1A-01 未取到进站设备号，industryDetail 的 entryDeviceCode 置空串, cardId={}", cardId);
    }

    private <T> String getOrFallback(T info, Function<T, String> getter, String fallback) {
        if (info == null) return fallback;
        String value = getter.apply(info);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private RequestStationLineInfoResult queryStationLineInfo(String stationCode) {
        return stationLineResolver.resolveLineInfo(stationCode);
    }
    private String convertHandleDateTime(String handleDateTime) {
        if (handleDateTime == null || handleDateTime.length() < 14) {
            return handleDateTime;
        }
        return handleDateTime.substring(0, 4) + "-" +
                handleDateTime.substring(4, 6) + "-" +
                handleDateTime.substring(6, 8) + " " +
                handleDateTime.substring(8, 10) + ":" +
                handleDateTime.substring(10, 12) + ":" +
                handleDateTime.substring(12, 14);
    }

    private String buildTripId(String itpUserId, String handleDateTime, String suffix) {
        String dateCompact = compactDate(convertHandleDateTime(handleDateTime));
        return itpUserId + (StringUtils.hasText(dateCompact) ? dateCompact : "") + suffix;
    }

    private String compactDate(String date) {
        if (!StringUtils.hasText(date)) {
            return "";
        }
        String compact = date.replaceAll("[^0-9]", "");
        return compact.length() > 14 ? compact.substring(0, 14) : compact;
    }
}

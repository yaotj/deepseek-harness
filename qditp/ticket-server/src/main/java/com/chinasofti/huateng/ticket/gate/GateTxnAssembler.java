package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** IF1A-01 落库对象组装器 —— 把闸机报文翻译成 {@link QRCodeTxnDetail}（交易明细）与 {@link QRCodeStatus}（下一票卡状态）两个待写实体。 */
@Component
class GateTxnAssembler {

    private static final Logger log = LoggerFactory.getLogger(GateTxnAssembler.class);
    private static final DateTimeFormatter TXN_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private GateCodeStatusResolver codeStatusResolver;

    /** 构建交易明细。 */
    public QRCodeTxnDetail buildTxnDetail(NotifyVerifyResultReqDTO request) {
        QRCodeTxnDetail detail = new QRCodeTxnDetail();
        detail.setDeviceId(request.getDeviceId());
        detail.setItpUserId(request.getItpUserId());
        detail.setTrxType(request.getTrxType());
        detail.setIssueChannelCode(request.getIssueChannelCode());
        detail.setSignChannelCode(request.getSignChannelCode());
        detail.setCardId(request.getCardId());
        detail.setCardType(request.getCardType());
        detail.setHandleDateTime(request.getHandleDateTime());
        detail.setTxnDate(resolveTxnDate(request.getHandleDateTime()));
        detail.setHandleStationCode(request.getHandleStationCode());
        detail.setTrxAmount(parseAmount(request.getTrxAmount()));
        detail.setOvertimeAmount(parseAmount(request.getOvertimeAmount()));
        detail.setLastTicketStatus(request.getLastTicketStatus());
        detail.setHandleResultCode(request.getHandleResultCode());
        detail.setLastHandleStationCode(request.getLastHandleStationCode());
        detail.setLastHandleDateTime(request.getLastHandleDateTime());
        detail.setTicketTransSeq(request.getTicketTransSeq());
        detail.setReserve1(request.getReserve1());
        detail.setReserve2(request.getReserve2());
        detail.setCreateTime(LocalDateTime.now());
        return detail;
    }

    /** 从 {@code handleDateTime}（yyyyMMddHHmmss）取交易日期。 */
    private String resolveTxnDate(String handleDateTime) {
        if (handleDateTime != null && handleDateTime.length() >= 8) {
            String candidate = handleDateTime.substring(0, 8);
            if (isAllDigits(candidate)) {
                return candidate;
            }
        }
        String fallback = LocalDateTime.now().format(TXN_DATE_FORMATTER);
        log.warn("IF1A-01 handleDateTime 格式异常，txnDate 兜底为当天, handleDateTime={}, txnDate={}",
                handleDateTime, fallback);
        return fallback;
    }

    private boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 解析金额（分）。 */
    private Long parseAmount(String amount) {
        if (!StringUtils.hasText(amount)) {
            return null;
        }
        try {
            return Long.valueOf(amount.trim());
        } catch (NumberFormatException e) {
            log.warn("IF1A-01 金额字段非数字，本字段置空继续处理, amount={}", amount);
            return null;
        }
    }

    /** 构建下一票卡状态。 */
    public QRCodeStatus buildNextStatus(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus) {
        QRCodeStatus nextStatus = new QRCodeStatus();
        nextStatus.setCardId(request.getCardId());
        nextStatus.setUseCount(currentStatus.getUseCount() == null ? 1 : currentStatus.getUseCount() + 1);
        nextStatus.setChannel(StringUtils.hasText(request.getIssueChannelCode())
                ? request.getIssueChannelCode() : currentStatus.getChannel());
        nextStatus.setCodeStatus(codeStatusResolver.resolveCodeStatus(
                request.getTrxType(), request.getExcessFareType(), request.getAdviceOpt()));
        nextStatus.setLastTxnTime(request.getHandleDateTime());
        nextStatus.setLastTxnStation(request.getHandleStationCode());
        nextStatus.setTxnSeq(incrementTxnSeq(currentStatus.getTxnSeq()));
        nextStatus.setCreateTime(currentStatus.getCreateTime());
        nextStatus.setUpdateTime(LocalDateTime.now());
        nextStatus.setGateStatus(request.getTrxType());

        if (TrxTypeCodeEnum.isEntryTxn(request.getTrxType())) {
            nextStatus.setGateInTime(request.getHandleDateTime());
            nextStatus.setGateInStation(request.getHandleStationCode());
        } else {
            nextStatus.setGateInTime(currentStatus.getGateInTime());
            nextStatus.setGateInStation(currentStatus.getGateInStation());
        }

        if (!TrxTypeCodeEnum.isEntryTxn(request.getTrxType())) {
            nextStatus.setTrxAmount(parseAmount(request.getTrxAmount()));
        } else {
            nextStatus.setTrxAmount(currentStatus.getTrxAmount());
        }

        log.info("IF1A-01 构建下一状态, cardId={}, trxType={}, currentStatus={}, nextStatus={}",
                request.getCardId(), request.getTrxType(), currentStatus, nextStatus);
        warnIfTransitionUnexpected(currentStatus, nextStatus, request);
        return nextStatus;
    }

    /** 迁移白名单观察日志：只告警、 */
    private void warnIfTransitionUnexpected(QRCodeStatus currentStatus, QRCodeStatus nextStatus,
                                            NotifyVerifyResultReqDTO request) {
        QRCodeStatusEnum from = QRCodeStatusEnum.parseOrNull(currentStatus.getCodeStatus());
        QRCodeStatusEnum to = QRCodeStatusEnum.parseOrNull(nextStatus.getCodeStatus());
        if (from == null || to == null) {
            log.warn("IF1A-01 状态迁移含未登记取值, cardId={}, from={}, to={}",
                    request.getCardId(), currentStatus.getCodeStatus(), nextStatus.getCodeStatus());
            return;
        }
        if (from == to && from.isClosedLoop()) {
            log.warn("IF1A-01 闭环状态重复流转（仅告警不拦截）, cardId={}, status={}({}), trxType={}, adviceOpt={},"
                            + " 本次站={}, 本次时间={}, 库内进站站={}, 库内进站时间={}, 库内末次站={}, 库内末次时间={}",
                    request.getCardId(), from.getCode(), from.getDesc(),
                    request.getTrxType(), request.getAdviceOpt(),
                    request.getHandleStationCode(), request.getHandleDateTime(),
                    currentStatus.getGateInStation(), currentStatus.getGateInTime(),
                    currentStatus.getLastTxnStation(), currentStatus.getLastTxnTime());
            return;
        }
        if (!from.canTransitTo(to)) {
            log.warn("IF1A-01 状态迁移不在白名单内（仅告警不拦截）, cardId={}, from={}({}), to={}({}), trxType={}, adviceOpt={}",
                    request.getCardId(), from.getCode(), from.getDesc(), to.getCode(), to.getDesc(),
                    request.getTrxType(), request.getAdviceOpt());
        }
    }

    /** 交易序号 +1。 */
    private String incrementTxnSeq(String txnSeq) {
        if (!StringUtils.hasText(txnSeq)) {
            return "1";
        }
        try {
            return String.valueOf(Long.parseLong(txnSeq.trim()) + 1);
        } catch (NumberFormatException e) {
            log.error("IF1A-01 库内 TXN_SEQ 非数字，无法递增，回退为 1 并需人工核对, txnSeq={}", txnSeq);
            return "1";
        }
    }
}

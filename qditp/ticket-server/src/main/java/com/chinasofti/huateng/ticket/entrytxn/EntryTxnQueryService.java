package com.chinasofti.huateng.ticket.entrytxn;

import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnReqDTO;
import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnResult;
import com.chinasofti.huateng.model.ticket.QueryLatestEntryTxnResult;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 进站交易只读查询（源表 {@code QRCODE_TXN_DETAIL}，只查 {@code TRX_TYPE='01'}）。 */
@Service
public class EntryTxnQueryService {

    private static final Logger log = LoggerFactory.getLogger(EntryTxnQueryService.class);

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    public EntryTxnQueryService(QRCodeTxnDetailMapper qrCodeTxnDetailMapper) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
    }

    /** 查询最近一次进站设备编号。 */
    public String queryEntryDevice(String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return null;
        }
        try {
            QRCodeTxnDetail entryDetail = qrCodeTxnDetailMapper.selectLatestEntryByCardId(cardId);
            return entryDetail != null ? entryDetail.getDeviceId() : null;
        } catch (Exception e) {
            log.error("查询进站设备异常, cardId={}", cardId, e);
            return null;
        }
    }

    /** 查询同序列号的首笔进站交易。 */
    public QueryFirstEntryTxnResult queryFirstEntryTxn(QueryFirstEntryTxnReqDTO request) {
        QueryFirstEntryTxnResult result = new QueryFirstEntryTxnResult();
        if (request == null || !StringUtils.hasText(request.getCardId())
                || !StringUtils.hasText(request.getTicketTransSeq())) {
            result.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("cardId和ticketTransSeq不能为空");
            return result;
        }
        QRCodeTxnDetail detail = qrCodeTxnDetailMapper.selectFirstEntryBySequence(
                request.getCardId().trim(), request.getTicketTransSeq().trim());
        if (detail == null) {
            result.setRetCode(TicketErrorCodeEnum.ENTRY_TXN_NOT_FOUND.getCode());
            result.setRetMsg(TicketErrorCodeEnum.ENTRY_TXN_NOT_FOUND.getMsg());
            return result;
        }
        result.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        result.setCardId(detail.getCardId());
        result.setTicketTransSeq(detail.getTicketTransSeq());
        result.setHandleDateTime(detail.getHandleDateTime());
        result.setHandleStationCode(detail.getHandleStationCode());
        return result;
    }

    /**
     * 查「同卡 + 进站 + 早于或等于本次出站时间的最近一笔」明细（离线码出站重算票价用）。
     *
     * <p>与上面 {@link #queryFirstEntryTxn} 并存、<b>互不替代</b>。三条口径 NEVER 改：
     * <p>1. <b>NEVER 退回按 {@code ticketTransSeq} 相等配对</b> —— 进站与出站是同一张卡的两笔不同交易，
     * 闸机上送的序列号天然不同（2026-09-22 实测进站 0 / 出站 1），相等配对恒命中 0 行、
     * 订单永久卡 {@code OFFLINE_FARE_PENDING}（C9 缺陷本体）。
     * <p>2. <b>NEVER 改用 {@code QRCODE_STATUS.GATE_IN_STATION} / {@code GATE_IN_TIME}</b> ——
     * 那是票卡当前状态快照、会被下一趟行程覆盖，而本查询服务的是**延迟执行**的补偿链路，
     * 延迟期间该卡再进站一次就会拿新行程的进站信息算上一笔的钱（算错钱比算不出更坏）。
     * <p>3. 查不到 MUST 明确返 {@code 8004} 让上游走异常分支，<b>NEVER 返回空对象冒充成功</b>
     * （与 {@code queryFirstEntryTxn} 同一条契约）。
     */
    public QueryLatestEntryTxnResult queryLatestEntryBeforeExit(String cardId, String exitHandleDateTime) {
        QueryLatestEntryTxnResult result = new QueryLatestEntryTxnResult();
        if (!StringUtils.hasText(cardId) || !StringUtils.hasText(exitHandleDateTime)) {
            result.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("cardId和exitHandleDateTime不能为空");
            return result;
        }
        QRCodeTxnDetail detail = qrCodeTxnDetailMapper.selectLatestEntryBeforeExit(
                cardId.trim(), exitHandleDateTime.trim());
        if (detail == null) {
            result.setRetCode(TicketErrorCodeEnum.ENTRY_TXN_NOT_FOUND.getCode());
            result.setRetMsg(TicketErrorCodeEnum.ENTRY_TXN_NOT_FOUND.getMsg());
            return result;
        }
        result.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        result.setCardId(detail.getCardId());
        result.setTicketTransSeq(detail.getTicketTransSeq());
        result.setHandleDateTime(detail.getHandleDateTime());
        result.setHandleStationCode(detail.getHandleStationCode());
        return result;
    }
}

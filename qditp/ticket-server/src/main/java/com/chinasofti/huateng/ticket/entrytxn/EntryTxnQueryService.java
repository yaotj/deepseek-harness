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

@Service
public class EntryTxnQueryService {

    private static final Logger log = LoggerFactory.getLogger(EntryTxnQueryService.class);

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    public EntryTxnQueryService(QRCodeTxnDetailMapper qrCodeTxnDetailMapper) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
    }

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

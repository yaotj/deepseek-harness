package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.springframework.stereotype.Component;

@Component
class SupplementGateRequestAssembler {

    NotifyVerifyResultReqDTO newBaseRequest(QRCodeStatus currentStatus, String cardId, String decimalItpUserId,
                                            String trxType, String handleDateTime, String handleStationCode,
                                            String cardType, String signChannelCode, String channelType) {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        String issueChannelCode = SupplementCodec.defaultString(
                currentStatus.getChannel(), SupplementCodec.ISSUE_CHANNEL_DEFAULT);
        request.setItpUserId(SupplementCodec.normalizeDeviceThirdUserId(
                decimalItpUserId, issueChannelCode));
        request.setTrxType(trxType);
        request.setIssueChannelCode(issueChannelCode);
        request.setSignChannelCode(signChannelCode);
        // R6 卡片行程通知要求 channelType：00 闸机 / 01 蓝牙(HCE，不推) / 02 BOM / 03 自助补站
        request.setChannelType(channelType);
        request.setCardId(cardId);
        request.setCardType(cardType);
        request.setHandleDateTime(handleDateTime);
        request.setHandleStationCode(handleStationCode);
        request.setOvertimeAmount(SupplementCodec.AMOUNT_ZERO);
        request.setLastTicketStatus(SupplementCodec.defaultString(
                currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode()));
        request.setHandleResultCode(SupplementCodec.HANDLE_RESULT_SUCCESS);
        request.setLastHandleStationCode(currentStatus.getLastTxnStation());
        request.setLastHandleDateTime(currentStatus.getLastTxnTime());
        request.setTicketTransSeq(currentStatus.getTxnSeq() == null
                ? SupplementCodec.AMOUNT_ZERO : currentStatus.getTxnSeq());
        request.setTrxAmount(SupplementCodec.AMOUNT_ZERO);
        request.setReserve1(null);
        request.setReserve2(null);
        return request;
    }
}

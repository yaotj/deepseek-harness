package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationTxnDetailQueryService {

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    public OperationTxnDetailQueryService(QRCodeTxnDetailMapper qrCodeTxnDetailMapper) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
    }


    public List<QRCodeTxnDetail> page(String cardId, String thirdUserId, String signChannelCode, String cardType,
                                      String startDate, String endDate, int offset, int pageSize) {
        return qrCodeTxnDetailMapper.selectOperationPage(cardId, thirdUserId, signChannelCode, cardType,
                startDate, endDate, offset, pageSize);
    }

    public int count(String cardId, String thirdUserId, String signChannelCode, String cardType,
                     String startDate, String endDate) {
        return qrCodeTxnDetailMapper.countOperationPage(cardId, thirdUserId, signChannelCode, cardType,
                startDate, endDate);
    }
}

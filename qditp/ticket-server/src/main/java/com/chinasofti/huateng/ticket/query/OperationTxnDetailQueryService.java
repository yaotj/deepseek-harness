package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 运营后台交易明细分页查询。 */
@Service
public class OperationTxnDetailQueryService {

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    public OperationTxnDetailQueryService(QRCodeTxnDetailMapper qrCodeTxnDetailMapper) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
    }

    /**
     * 按运营条件分页取交易明细。
     *
     * @param offset 起始偏移，调用方已按页码算好
     * @param pageSize 单页条数，调用方已钳制上限
     */
    public List<QRCodeTxnDetail> page(String cardId, String thirdUserId, String signChannelCode, String cardType,
                                      String startDate, String endDate, int offset, int pageSize) {
        return qrCodeTxnDetailMapper.selectOperationPage(cardId, thirdUserId, signChannelCode, cardType,
                startDate, endDate, offset, pageSize);
    }

    /** 与 {@link #page} 同条件的总条数。 */
    public int count(String cardId, String thirdUserId, String signChannelCode, String cardType,
                     String startDate, String endDate) {
        return qrCodeTxnDetailMapper.countOperationPage(cardId, thirdUserId, signChannelCode, cardType,
                startDate, endDate);
    }
}

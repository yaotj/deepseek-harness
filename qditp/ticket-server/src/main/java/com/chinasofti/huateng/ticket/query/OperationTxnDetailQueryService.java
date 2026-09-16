package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 运营后台交易明细分页查询。
 *
 * <p>2026-09-14 新增，用来消除 {@code controller/page/QRCodeTxnDetailPageController} 直连
 * {@code QRCodeTxnDetailMapper} 的分层违规（AGENTS.md §3.3：controller 只做参数校验与路由）。
 * 原实现里那个 controller 同时承担参数归一、分页边界钳制与两次 SQL 调用，
 * 于是「运营后台怎么查」这件事**没有任何一层可以被单测覆盖**。
 *
 * <p>本类只负责取数：入参已由调用方归一（{@code null} 表示该条件不生效），
 * 分页边界也由调用方钳制。**NEVER 在这里补默认值** —— 默认页大小属于展示策略，
 * 放在这里会让同一套 SQL 对不同调用方给出不同结果。
 */
@Service
public class OperationTxnDetailQueryService {

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    public OperationTxnDetailQueryService(QRCodeTxnDetailMapper qrCodeTxnDetailMapper) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
    }

    /**
     * 按运营条件分页取交易明细。
     *
     * @param offset   起始偏移，调用方已按页码算好
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

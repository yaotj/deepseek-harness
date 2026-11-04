package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.service.AlipayChannelPageService;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 综管台支付宝渠道只读查询实现：直接走两张表的运营分页语句，检索范围在控制器层强校验。 */
@Service
public class AlipayChannelPageServiceImpl implements AlipayChannelPageService {

    private final AlipaySignInfoMapper signInfoMapper;
    private final AlipayRefundTxnDetailMapper refundTxnDetailMapper;

    public AlipayChannelPageServiceImpl(AlipaySignInfoMapper signInfoMapper,
                                        AlipayRefundTxnDetailMapper refundTxnDetailMapper) {
        this.signInfoMapper = signInfoMapper;
        this.refundTxnDetailMapper = refundTxnDetailMapper;
    }

    @Override
    public List<AlipaySignInfo> selectSignedUsersPage(String thirdUserId, String cardId, String signStatus,
                                                       String signTimeBegin, String signTimeEnd, int offset, int limit) {
        return signInfoMapper.selectPageForOps(trim(thirdUserId), trim(cardId), trim(signStatus),
                trim(signTimeBegin), trim(signTimeEnd), offset, limit);
    }

    @Override
    public int countSignedUsersPage(String thirdUserId, String cardId, String signStatus,
                                    String signTimeBegin, String signTimeEnd) {
        return signInfoMapper.countPageForOps(trim(thirdUserId), trim(cardId), trim(signStatus),
                trim(signTimeBegin), trim(signTimeEnd));
    }

    @Override
    public List<AlipayRefundTxnDetail> selectRefundRecordsPage(String refundOrderNo, String orderNo, String refundStatus,
                                                               String txnDateBegin, String txnDateEnd, int offset, int limit) {
        return refundTxnDetailMapper.selectPageForOps(trim(refundOrderNo), trim(orderNo), trim(refundStatus),
                trim(txnDateBegin), trim(txnDateEnd), offset, limit);
    }

    @Override
    public int countRefundRecordsPage(String refundOrderNo, String orderNo, String refundStatus,
                                      String txnDateBegin, String txnDateEnd) {
        return refundTxnDetailMapper.countPageForOps(trim(refundOrderNo), trim(orderNo), trim(refundStatus),
                trim(txnDateBegin), trim(txnDateEnd));
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

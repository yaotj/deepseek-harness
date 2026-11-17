package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;

import java.util.List;

public interface AlipayChannelPageService {

    List<AlipaySignInfo> selectSignedUsersPage(String thirdUserId, String cardId, String signStatus,
                                                String signTimeBegin, String signTimeEnd, int offset, int limit);

    int countSignedUsersPage(String thirdUserId, String cardId, String signStatus,
                             String signTimeBegin, String signTimeEnd);

    List<AlipayRefundTxnDetail> selectRefundRecordsPage(String refundOrderNo, String orderNo, String refundStatus,
                                                         String txnDateBegin, String txnDateEnd, int offset, int limit);

    int countRefundRecordsPage(String refundOrderNo, String orderNo, String refundStatus,
                               String txnDateBegin, String txnDateEnd);
}

package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;

import java.util.List;

/** 综管台「支付宝渠道管理」只读查询：签约用户 / 渠道退款记录。 */
public interface AlipayChannelPageService {

    /** 签约用户分页（{@code ALIPAY_SIGN_INFO}，DELETE_FLAG='0' 在账行；检索范围由调用方保证）。 */
    List<AlipaySignInfo> selectSignedUsersPage(String thirdUserId, String cardId, String signStatus,
                                                String signTimeBegin, String signTimeEnd, int offset, int limit);

    /** 签约用户总数。 */
    int countSignedUsersPage(String thirdUserId, String cardId, String signStatus,
                             String signTimeBegin, String signTimeEnd);

    /** 渠道退款记录分页（{@code ALIPAY_REFUND_TXN_DETAIL}；检索范围由调用方保证）。 */
    List<AlipayRefundTxnDetail> selectRefundRecordsPage(String refundOrderNo, String orderNo, String refundStatus,
                                                         String txnDateBegin, String txnDateEnd, int offset, int limit);

    /** 渠道退款记录总数。 */
    int countRefundRecordsPage(String refundOrderNo, String orderNo, String refundStatus,
                               String txnDateBegin, String txnDateEnd);
}

package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.response.AlipayPayLogVO;
import com.chinasofti.huateng.alipay.paysign.model.response.PageResult;

import java.util.List;

public interface AlipayPayLogQueryService {
    PageResult<AlipayPayLogVO> selectAlipayPayLogList(String thirdUserId, String startTime, String endTime, int offset, int limit,
                                                      String debitRequestResult, String invoice);
    int countAlipayPayLogList(String thirdUserId, String startTime, String endTime,
                              String debitRequestResult, String invoice);
    AlipayPayLogVO selectByOrderNo(String orderNo);
    AlipayPayLogVO selectByEntryId(String entryId);
    AlipayPayLogVO selectByExitId(String exitId);
    AlipayPayLogVO selectByTravelRecord(String thirdUserId, String entryDate, String cardNum);
}

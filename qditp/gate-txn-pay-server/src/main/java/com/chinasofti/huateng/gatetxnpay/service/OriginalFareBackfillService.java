package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.model.page.OriginalFareBackfillRequest;

import java.util.Map;

/** 历史订单 {@code ORIGINAL_FARE}（地铁原价）运营补数。 */
public interface OriginalFareBackfillService {

    /** 按 {@code TXN_DATE} 区间扫出 {@code ORIGINAL_FARE} 为空的订单并回填。 */
    ResultVO<Map<String, Object>> backfillOriginalFare(OriginalFareBackfillRequest request);
}

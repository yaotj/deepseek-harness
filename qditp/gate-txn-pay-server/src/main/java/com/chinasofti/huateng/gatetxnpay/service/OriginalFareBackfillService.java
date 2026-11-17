package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.model.page.OriginalFareBackfillRequest;

import java.util.Map;

public interface OriginalFareBackfillService {

    ResultVO<Map<String, Object>> backfillOriginalFare(OriginalFareBackfillRequest request);
}

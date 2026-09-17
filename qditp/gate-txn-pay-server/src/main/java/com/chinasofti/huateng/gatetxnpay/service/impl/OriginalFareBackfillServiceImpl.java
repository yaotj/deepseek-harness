package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareCalculator;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.model.page.OriginalFareBackfillRequest;
import com.chinasofti.huateng.gatetxnpay.service.OriginalFareBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 运营补数实现。 */
@Service
public class OriginalFareBackfillServiceImpl implements OriginalFareBackfillService {
    private static final Logger log = LoggerFactory.getLogger(OriginalFareBackfillServiceImpl.class);

    private final GateTxnPayMapper gateTxnPayMapper;
    private final FareCalculator fareCalculator;

    public OriginalFareBackfillServiceImpl(GateTxnPayMapper gateTxnPayMapper, FareCalculator fareCalculator) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.fareCalculator = fareCalculator;
    }

    /** 历史订单 ORIGINAL_FARE 补数。 */
    @Override
    public ResultVO<Map<String, Object>> backfillOriginalFare(OriginalFareBackfillRequest request) {
        if (request == null) {
            return ResultMapper.illegalParams("请求体不能为空");
        }
        String startDate = trimToNull(request.getStartDate());
        String endDate = trimToNull(request.getEndDate());
        if (!isTxnDate(startDate) || !isTxnDate(endDate)) {
            return ResultMapper.illegalParams("startDate、endDate 必填且须为 yyyyMMdd");
        }
        if (startDate.compareTo(endDate) > 0) {
            return ResultMapper.illegalParams("startDate 不能大于 endDate");
        }
        int limit = request.getLimit() == null ? 500 : Math.min(Math.max(request.getLimit(), 1), 5000);
        boolean dryRun = request.getDryRun() == null || request.getDryRun();
        int suspectDiffCents = request.getSuspectDiffCents() == null ? 300 : Math.max(request.getSuspectDiffCents(), 0);
        boolean force = Boolean.TRUE.equals(request.getForce());

        List<GateTxnPay> rows = gateTxnPayMapper.selectMissingOriginalFare(startDate, endDate, limit);
        List<Map<String, Object>> updatedList = new ArrayList<>();
        List<Map<String, Object>> suspectList = new ArrayList<>();
        List<Map<String, Object>> failedList = new ArrayList<>();
        int noFareCount = 0;
        for (GateTxnPay row : rows) {
            Integer originalFare = fareCalculator.queryOriginalFare(row.getInStation(), row.getOutStation());
            if (originalFare == null) {
                noFareCount++;
                continue;
            }
            int trxAmount = row.getTrxAmount() == null ? 0 : row.getTrxAmount();
            if (!force && trxAmount > 0 && originalFare - trxAmount > suspectDiffCents) {
                suspectList.add(describeBackfillRow(row, originalFare));
                continue;
            }
            if (dryRun) {
                updatedList.add(describeBackfillRow(row, originalFare));
                continue;
            }
            try {
                int affected = gateTxnPayMapper.updateOriginalFareIfNull(row.getOrderNo(), row.getTxnDate(), originalFare);
                if (affected > 0) {
                    updatedList.add(describeBackfillRow(row, originalFare));
                } else {
                    log.info("ORIGINAL_FARE 已被其他调用回填，跳过, orderNo={}", row.getOrderNo());
                }
            } catch (Exception e) {
                log.warn("ORIGINAL_FARE 回填失败, orderNo={}, originalFare={}", row.getOrderNo(), originalFare, e);
                Map<String, Object> failed = describeBackfillRow(row, originalFare);
                failed.put("error", e.getMessage());
                failedList.add(failed);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("scannedCount", rows.size());
        result.put("updatedCount", updatedList.size());
        result.put("noFareCount", noFareCount);
        result.put("suspectCount", suspectList.size());
        result.put("failedCount", failedList.size());
        result.put("updatedList", updatedList);
        result.put("suspectList", suspectList);
        result.put("failedList", failedList);
        log.info("ORIGINAL_FARE 补数完成, dryRun={}, startDate={}, endDate={}, scanned={}, updated={}, noFare={}, suspect={}, failed={}",
                dryRun, startDate, endDate, rows.size(), updatedList.size(), noFareCount, suspectList.size(), failedList.size());
        return ResultMapper.ok(result);
    }

    /** 补数结果行：只回显定位与金额字段。 */
    private Map<String, Object> describeBackfillRow(GateTxnPay row, Integer originalFare) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderNo", row.getOrderNo());
        item.put("txnDate", row.getTxnDate());
        item.put("inStation", row.getInStation());
        item.put("outStation", row.getOutStation());
        item.put("trxAmount", row.getTrxAmount());
        item.put("originalFare", originalFare);
        return item;
    }

    /** TXN_DATE 是 VARCHAR2 存 yyyyMMdd，这里只做长度与数字校验。 */
    private boolean isTxnDate(String value) {
        if (value == null || value.length() != 8) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 与 {@code GateTxnPayServiceImpl} 各留一份的三行副本：项目规则禁止为此新建工具类。 */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

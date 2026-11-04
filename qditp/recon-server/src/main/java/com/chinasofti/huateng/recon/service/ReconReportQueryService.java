package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.recon.mapper.ReconReportMapper;
import com.chinasofti.huateng.recon.model.page.ReconClearingRowView;
import com.chinasofti.huateng.recon.model.page.ReconDailyStatView;
import com.chinasofti.huateng.recon.model.page.ReconThirdBillPageView;
import com.chinasofti.huateng.recon.model.page.ReconTxnDiffPageView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 综管台「统计分析」报表页的只读查询（对标旧 ITP 报表管理组）。
 *
 * <p>口径：对账差异 / 渠道账单 / 清分直接查 recon 落地表（{@code RECON_TXN_DIFF} / {@code RECON_THIRD_BILL}），
 * 日统计对 {@code PAY_TXN_DETAIL} 做纯查询聚合——不建聚合表、不建定时任务，数据量增长后再评估。
 */
@Service
public class ReconReportQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    private final ReconReportMapper reconReportMapper;

    public ReconReportQueryService(ReconReportMapper reconReportMapper) {
        this.reconReportMapper = reconReportMapper;
    }

    /** 对账差异明细分页查询：账单日窗必填。 */
    public ResultVO<Map<String, Object>> txnDiffPage(String billDateStart, String billDateEnd,
                                                      String payChannel, String diffSide, String orderNo,
                                                      Integer pageNum, Integer pageSize) {
        String start = normalizeDate(billDateStart);
        String end = normalizeDate(billDateEnd);
        if (!hasDateWindow(start, end)) {
            return ResultMapper.illegalParams("请同时填写完整的账单日期范围");
        }
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String channel = trimToNull(payChannel);
        String side = trimToNull(diffSide);
        String no = trimToNull(orderNo);
        List<ReconTxnDiffPageView> list = reconReportMapper.selectTxnDiffPage(
                start, end, channel, side, no, (currentPage - 1) * currentPageSize, currentPageSize);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", list);
        page.put("total", reconReportMapper.countTxnDiffPage(start, end, channel, side, no));
        return ResultMapper.ok(page);
    }

    /** 渠道账单行分页查询：账单日窗必填。 */
    public ResultVO<Map<String, Object>> thirdBillPage(String billDateStart, String billDateEnd,
                                                       String paymentChannel, String paymentVendor, String merchantOrderNo,
                                                       Integer pageNum, Integer pageSize) {
        String start = normalizeDate(billDateStart);
        String end = normalizeDate(billDateEnd);
        if (!hasDateWindow(start, end)) {
            return ResultMapper.illegalParams("请同时填写完整的账单日期范围");
        }
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String channel = trimToNull(paymentChannel);
        String vendor = trimToNull(paymentVendor);
        String no = trimToNull(merchantOrderNo);
        List<ReconThirdBillPageView> list = reconReportMapper.selectThirdBillPage(
                start, end, channel, vendor, no, (currentPage - 1) * currentPageSize, currentPageSize);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", list);
        page.put("total", reconReportMapper.countThirdBillPage(start, end, channel, vendor, no));
        return ResultMapper.ok(page);
    }

    /** 日统计：groupBy=VENDOR 按「支付账户 + 交易日期」，groupBy=HOUR 按当天小时（要求单日窗）。 */
    public ResultVO<Map<String, Object>> dailyStats(String groupBy, String dateStart, String dateEnd) {
        String start = normalizeDate(dateStart);
        String end = normalizeDate(dateEnd);
        if (!hasDateWindow(start, end)) {
            return ResultMapper.illegalParams("请同时填写完整的交易日期范围");
        }
        String mode = trimToNull(groupBy);
        if ("HOUR".equalsIgnoreCase(mode)) {
            if (!start.equals(end)) {
                return ResultMapper.illegalParams("按当天小时统计只支持单日日期范围");
            }
            List<ReconDailyStatView> rows = reconReportMapper.selectDailyStats("HOUR", start, end);
            return ResultMapper.ok(Map.of("list", rows));
        }
        List<ReconDailyStatView> rows = reconReportMapper.selectDailyStats("VENDOR", start, end);
        return ResultMapper.ok(Map.of("list", rows));
    }

    /** 业务清分明细：渠道账单按「账单日 + 支付商户」聚合，账单日窗必填。 */
    public ResultVO<Map<String, Object>> clearing(String billDateStart, String billDateEnd,
                                                   String paymentChannel, String paymentVendor) {
        String start = normalizeDate(billDateStart);
        String end = normalizeDate(billDateEnd);
        if (!hasDateWindow(start, end)) {
            return ResultMapper.illegalParams("请同时填写完整的账单日期范围");
        }
        List<ReconClearingRowView> rows = reconReportMapper.selectClearingRows(
                start, end, trimToNull(paymentChannel), trimToNull(paymentVendor));
        return ResultMapper.ok(Map.of("list", rows));
    }

    /** 账单日 / 交易日（yyyy-MM-dd 或 yyyyMMdd）统一为落地表的 yyyyMMdd 口径。 */
    private String normalizeDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replace("-", "");
        return digits.matches("\\d{8}") ? digits : null;
    }

    private boolean hasDateWindow(String start, String end) {
        return start != null && end != null && start.compareTo(end) <= 0;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

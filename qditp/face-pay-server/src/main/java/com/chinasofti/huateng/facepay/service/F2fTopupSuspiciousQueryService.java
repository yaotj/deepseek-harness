package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.page.F2fTopupSuspiciousPageVO;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/** 运营端 TVM 充值存疑回执（{@code F2F_RESULT_REPORT}）的只读查询。 */
@Service
public class F2fTopupSuspiciousQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    /** 前端传的时间形态，与当面付订单页一致。 */
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 充值回执状态字典：00 成功，01 失败，02 存疑，03 取消。 */
    private static final Set<String> TOPUP_STATUS_VALUES = Set.of("00", "01", "02", "03");

    /** 本页默认口径：存疑（TOPUP_STATUS='02'）。 */
    private static final String DEFAULT_SUSPICIOUS_STATUS = "02";

    private final F2fResultReportMapper resultReportMapper;

    public F2fTopupSuspiciousQueryService(F2fResultReportMapper resultReportMapper) {
        this.resultReportMapper = resultReportMapper;
    }

    /** TVM 充值存疑回执分页查询入参。 */
    public record TopupSuspiciousPageQuery(String orderNo,
                                           String channel,
                                           String deviceId,
                                           String topupStatus,
                                           String processed,
                                           String beginTime,
                                           String endTime,
                                           Integer pageNum,
                                           Integer pageSize) {
    }

    /** 查询判定结果，调用方 MUST 穷尽分支后各自组响应壳。 */
    public sealed interface PageOutcome {

        record Ok(List<F2fTopupSuspiciousPageVO> list, long total) implements PageOutcome {
        }

        record Rejected(String reason) implements PageOutcome {
        }
    }

    /**
     * 分页查询充值回执。业务流水表，无条件全表扫会伤库，故要求订单号或完整接收时间范围。
     */
    public PageOutcome page(TopupSuspiciousPageQuery query) {
        String channel = trimToNull(query.channel());
        if (channel != null && !F2fChannel.isSupported(channel)) {
            return new PageOutcome.Rejected("受理渠道只能是 01(APP)、02(TVM) 或 03(BOM)");
        }
        String processed = trimToNull(query.processed());
        if (processed != null && !processed.equals("0") && !processed.equals("1")) {
            return new PageOutcome.Rejected("处理状态只能是 1(已处理) 或 0(未处理)");
        }
        String topupStatus = trimToNull(query.topupStatus());
        if (topupStatus == null) {
            topupStatus = DEFAULT_SUSPICIOUS_STATUS;
        }
        if (!TOPUP_STATUS_VALUES.contains(topupStatus)) {
            return new PageOutcome.Rejected("充值回执状态只能是 00(成功)、01(失败)、02(存疑) 或 03(取消)");
        }

        LocalDateTime beginTms;
        LocalDateTime endTms;
        try {
            beginTms = parse(query.beginTime());
            endTms = parse(query.endTime());
        } catch (DateTimeParseException e) {
            return new PageOutcome.Rejected("时间格式必须是 yyyy-MM-dd HH:mm:ss");
        }
        String orderNo = trimToNull(query.orderNo());
        if (orderNo == null && (beginTms == null || endTms == null)) {
            return new PageOutcome.Rejected("请填写订单号，或完整的接收时间范围");
        }

        int currentPage = query.pageNum() == null || query.pageNum() < 1 ? 1 : query.pageNum();
        int currentPageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        int offset = (currentPage - 1) * currentPageSize;

        List<F2fTopupSuspiciousPageVO> list = resultReportMapper.selectTopupSuspiciousPage(
                orderNo, channel, trimToNull(query.deviceId()), topupStatus, processed,
                beginTms, endTms, offset, currentPageSize);
        long total = resultReportMapper.countTopupSuspiciousPage(
                orderNo, channel, trimToNull(query.deviceId()), topupStatus, processed, beginTms, endTms);
        return new PageOutcome.Ok(list, total);
    }

    private LocalDateTime parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDateTime.parse(value.trim(), INPUT_FORMATTER);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

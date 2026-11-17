package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrigRef;
import com.chinasofti.huateng.facepay.mapper.SupplementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SupplementOrderQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_ORIG_ORDER_NOS = 200;

    private final SupplementOrderMapper supplementOrderMapper;

    public SupplementOrderQueryService(SupplementOrderMapper supplementOrderMapper) {
        this.supplementOrderMapper = supplementOrderMapper;
    }

    public record SupplementOrderPageQuery(String orderNo,
                                           String cardId,
                                           String thirdUserId,
                                           String payStatus,
                                           String txnDateStart,
                                           String txnDateEnd,
                                           Integer pageNum,
                                           Integer pageSize) {
    }

    public sealed interface PageOutcome {

        record Ok(List<SupplementOrder> list, long total) implements PageOutcome {
        }

        record Rejected(String reason) implements PageOutcome {
        }
    }

    public PageOutcome page(SupplementOrderPageQuery query) {
        String orderNo = trimToNull(query.orderNo());
        String cardId = trimToNull(query.cardId());
        String thirdUserId = trimToNull(query.thirdUserId());
        String txnDateStart = normalizeTxnDate(query.txnDateStart());
        String txnDateEnd = normalizeTxnDate(query.txnDateEnd());
        if ((txnDateStart == null) != (txnDateEnd == null)) {
            return new PageOutcome.Rejected("交易日期范围需同时填写开始与结束日期");
        }
        if (txnDateStart != null && txnDateEnd != null && txnDateStart.compareTo(txnDateEnd) > 0) {
            return new PageOutcome.Rejected("交易开始日期不能晚于结束日期");
        }
        if (orderNo == null && cardId == null && thirdUserId == null
                && (txnDateStart == null || txnDateEnd == null)) {
            return new PageOutcome.Rejected("请填写补款单号、卡号、第三方用户ID，或完整的交易日期范围");
        }

        int currentPage = query.pageNum() == null || query.pageNum() < 1 ? 1 : query.pageNum();
        int currentPageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        int offset = (currentPage - 1) * currentPageSize;

        String payStatus = trimToNull(query.payStatus());
        List<SupplementOrder> list = supplementOrderMapper.selectSupplementOrderPage(
                orderNo, cardId, thirdUserId, payStatus, txnDateStart, txnDateEnd, offset, currentPageSize);
        long total = supplementOrderMapper.countSupplementOrderPage(
                orderNo, cardId, thirdUserId, payStatus, txnDateStart, txnDateEnd);
        return new PageOutcome.Ok(list, total);
    }


    public List<SupplementOrigRef> byOrigOrderNos(List<String> origOrderNos) {
        if (origOrderNos == null || origOrderNos.isEmpty()) {
            throw new IllegalArgumentException("原扣费订单号列表不能为空");
        }
        List<String> distinct = origOrderNos.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("原扣费订单号列表不能为空");
        }
        if (distinct.size() > MAX_ORIG_ORDER_NOS) {
            throw new IllegalArgumentException("单次最多查询 " + MAX_ORIG_ORDER_NOS + " 个原扣费订单号");
        }
        return supplementOrderMapper.selectRefsByOrigOrderNos(distinct);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeTxnDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replace("-", "");
        return digits.matches("\\d{8}") ? digits : null;
    }
}

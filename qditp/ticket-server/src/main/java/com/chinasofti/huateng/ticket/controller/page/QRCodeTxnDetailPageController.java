package com.chinasofti.huateng.ticket.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.query.OperationTxnDetailQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户运营端二维码票卡交易明细查询。 */
@RestController
@RequestMapping("/page/qrcode-txn-detail")
public class QRCodeTxnDetailPageController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final OperationTxnDetailQueryService txnDetailQueryService;

    public QRCodeTxnDetailPageController(OperationTxnDetailQueryService txnDetailQueryService) {
        this.txnDetailQueryService = txnDetailQueryService;
    }

    /** 分页查询二维码票卡交易；必须指定用户标识或完整日期范围以控制查询范围。 */
    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String cardId,
                                              @RequestParam(required = false) String thirdUserId,
                                              @RequestParam(required = false) String signChannelCode,
                                              @RequestParam(required = false) String cardType,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        String normalizedCardId = trimToNull(cardId);
        String normalizedThirdUserId = trimToNull(thirdUserId);
        String normalizedSignChannelCode = trimToNull(signChannelCode);
        String normalizedCardType = trimToNull(cardType);
        String normalizedStartDate = trimToNull(startDate);
        String normalizedEndDate = trimToNull(endDate);
        if (!hasSearchScope(normalizedCardId, normalizedThirdUserId, normalizedStartDate, normalizedEndDate)) {
            return ResultMapper.illegalParams("请填写逻辑卡号、第三方用户ID，或同时填写开始日期和结束日期");
        }
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (currentPage - 1) * currentPageSize;
        List<QRCodeTxnDetail> records = txnDetailQueryService.page(
                normalizedCardId, normalizedThirdUserId, normalizedSignChannelCode, normalizedCardType,
                normalizedStartDate, normalizedEndDate, offset, currentPageSize);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", records);
        page.put("total", txnDetailQueryService.count(
                normalizedCardId, normalizedThirdUserId, normalizedSignChannelCode, normalizedCardType,
                normalizedStartDate, normalizedEndDate));
        return ResultMapper.ok(page);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean hasSearchScope(String cardId, String thirdUserId, String startDate, String endDate) {
        return cardId != null || thirdUserId != null || (startDate != null && endDate != null);
    }
}

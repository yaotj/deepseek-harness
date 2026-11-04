package com.chinasofti.huateng.facepay.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.service.SupplementOrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运营端在线补款单查询（对标旧 ITP「在线补款订单管理」，只读）。
 *
 * <p>数据为 {@code SUPPLEMENT_ORDER}（face-pay-server owner），处置动作（支付/关单/强制销账）
 * 走既有内部链路与收敛任务，不在本端点开放写操作。
 */
@RestController
@RequestMapping("/page/face-pay/supplement-orders")
public class SupplementOrderPageController {

    private final SupplementOrderQueryService supplementOrderQueryService;

    public SupplementOrderPageController(SupplementOrderQueryService supplementOrderQueryService) {
        this.supplementOrderQueryService = supplementOrderQueryService;
    }

    /** 分页查询在线补款单；要求订单/卡号/用户标识或完整交易日期窗。 */
    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String cardId,
                                              @RequestParam(required = false) String thirdUserId,
                                              @RequestParam(required = false) String payStatus,
                                              @RequestParam(required = false) String txnDateStart,
                                              @RequestParam(required = false) String txnDateEnd,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        SupplementOrderQueryService.SupplementOrderPageQuery query =
                new SupplementOrderQueryService.SupplementOrderPageQuery(
                        orderNo, cardId, thirdUserId, payStatus, txnDateStart, txnDateEnd,
                        pageNum, pageSize);
        return switch (supplementOrderQueryService.page(query)) {
            case SupplementOrderQueryService.PageOutcome.Ok ok -> {
                Map<String, Object> page = new LinkedHashMap<>();
                page.put("list", ok.list());
                page.put("total", ok.total());
                yield ResultMapper.ok(page);
            }
            case SupplementOrderQueryService.PageOutcome.Rejected rejected ->
                    ResultMapper.illegalParams(rejected.reason());
        };
    }
}

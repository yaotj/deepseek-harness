package com.chinasofti.huateng.facepay.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrigRef;
import com.chinasofti.huateng.facepay.service.SupplementOrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/page/face-pay/supplement-orders")
public class SupplementOrderPageController {

    private final SupplementOrderQueryService supplementOrderQueryService;

    public SupplementOrderPageController(SupplementOrderQueryService supplementOrderQueryService) {
        this.supplementOrderQueryService = supplementOrderQueryService;
    }

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

    /**
     * 综管台扣费明细页回填用：按一批原扣费订单号反查补款单。
     *
     * <p>返回扁平列表（不是分页页）——调用方是「拿当页扣费订单号换补款信息」，
     * 分页在这里没有语义，硬造一个 total 反而误导。
     */
    @GetMapping("/by-orig")
    public ResultVO<List<SupplementOrigRef>> byOrig(@RequestParam(required = false) List<String> origOrderNos) {
        try {
            return ResultMapper.ok(supplementOrderQueryService.byOrigOrderNos(origOrderNos));
        } catch (IllegalArgumentException e) {
            return ResultMapper.illegalParams(e.getMessage());
        }
    }
}

package com.chinasofti.huateng.gatetxnpay.controller.page;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundOvertimeRequest;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundResult;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.gatetxnpay.model.page.OriginalFareBackfillRequest;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayQueryService;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.gatetxnpay.service.OriginalFareBackfillService;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/page/gate-txn-pay")
public class GateTxnPayPageController {
    private final GateTxnPayService gateTxnPayService;
    private final GateTxnPayQueryService gateTxnPayQueryService;
    private final OriginalFareBackfillService originalFareBackfillService;

    public GateTxnPayPageController(GateTxnPayService gateTxnPayService,
                                    GateTxnPayQueryService gateTxnPayQueryService,
                                    OriginalFareBackfillService originalFareBackfillService) {
        this.gateTxnPayService = gateTxnPayService;
        this.gateTxnPayQueryService = gateTxnPayQueryService;
        this.originalFareBackfillService = originalFareBackfillService;
    }

    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String cardId,
                                              @RequestParam(required = false) String thirdUserId,
                                              @RequestParam(required = false) String signChannelCode,
                                              @RequestParam(required = false) String cardType,
                                              @RequestParam(required = false) String debitStatus,
                                              @RequestParam(required = false) String issueChannelCode,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        return gateTxnPayQueryService.page(orderNo, cardId, thirdUserId, signChannelCode, cardType, debitStatus,
                issueChannelCode, startDate, endDate, pageNum, pageSize);
    }

    @GetMapping("/offline-stats")
    public ResultVO<Map<String, Object>> offlineStats(@RequestParam(required = false) String startDate,
                                                      @RequestParam(required = false) String endDate) {
        return gateTxnPayQueryService.offlineStats(startDate, endDate);
    }

    @GetMapping("/overtime-refundable")
    public ResultVO<Map<String, Object>> overtimeRefundable(@RequestParam(required = false) String stationCode,
                                                            @RequestParam(required = false) String startDate,
                                                            @RequestParam(required = false) String endDate,
                                                            @RequestParam(required = false) Integer pageNum,
                                                            @RequestParam(required = false) Integer pageSize) {
        return gateTxnPayQueryService.overtimeRefundablePage(stationCode, startDate, endDate, pageNum, pageSize);
    }

    @GetMapping("/one-side")
    public ResultVO<Map<String, Object>> oneSide(@RequestParam(required = false) String orderNo,
                                                 @RequestParam(required = false) String cardId,
                                                 @RequestParam(required = false) String thirdUserId,
                                                 @RequestParam(required = false) String orderExpType,
                                                 @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(required = false) Integer pageNum,
                                                 @RequestParam(required = false) Integer pageSize) {
        return gateTxnPayQueryService.oneSidePage(orderNo, cardId, thirdUserId, orderExpType,
                startDate, endDate, pageNum, pageSize);
    }

    @PostMapping("/batch-refund-overtime")
    public ResultVO<BatchRefundResult> batchRefundOvertime(@RequestBody BatchRefundOvertimeRequest request) {
        return gateTxnPayService.batchRefundOvertime(request);
    }

    @PostMapping("/{orderNo}/refund")
    public ResultVO<RequestRefundResult> requestRefund(@PathVariable String orderNo,
                                                        @RequestBody GateTxnPayRefundRequest request) {
        return gateTxnPayService.requestRefund(orderNo, request);
    }

    @PostMapping("/backfill-original-fare")
    public ResultVO<Map<String, Object>> backfillOriginalFare(@RequestBody OriginalFareBackfillRequest request) {
        return originalFareBackfillService.backfillOriginalFare(request);
    }
}

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

/**
 * 用户运营端过闸扣费信息与退款入口。
 */
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

    /** 分页查询过闸扣费订单；服务层要求订单标识或完整日期范围防止全表扫描。 */
    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String cardId,
                                              @RequestParam(required = false) String thirdUserId,
                                              @RequestParam(required = false) String signChannelCode,
                                              @RequestParam(required = false) String cardType,
                                              @RequestParam(required = false) String debitStatus,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        return gateTxnPayQueryService.page(orderNo, cardId, thirdUserId, signChannelCode, cardType, debitStatus,
                startDate, endDate, pageNum, pageSize);
    }

    /** 离线码交易统计：按车站分组笔数与独立卡数，日期窗必填（yyyy-MM-dd，闭区间）。 */
    @GetMapping("/offline-stats")
    public ResultVO<Map<String, Object>> offlineStats(@RequestParam(required = false) String startDate,
                                                      @RequestParam(required = false) String endDate) {
        return gateTxnPayQueryService.offlineStats(startDate, endDate);
    }

    /** 批量退超时罚金的圈单查询：日期窗必填（yyyy-MM-dd，闭区间），按车站分页圈出候选单。 */
    @GetMapping("/overtime-refundable")
    public ResultVO<Map<String, Object>> overtimeRefundable(@RequestParam(required = false) String stationCode,
                                                            @RequestParam(required = false) String startDate,
                                                            @RequestParam(required = false) String endDate,
                                                            @RequestParam(required = false) Integer pageNum,
                                                            @RequestParam(required = false) Integer pageSize) {
        return gateTxnPayQueryService.overtimeRefundablePage(stationCode, startDate, endDate, pageNum, pageSize);
    }

    /**
     * 批量发起超时罚金退款：逐单走单笔退款链路，金额为各自 OVERTIME_AMOUNT，单批 ≤ 200 笔。
     *
     * <p>⚠️ 与本模块其它 {@code /page/**} 写接口一样**当前没有鉴权**，且这是资金操作。
     * 上线前 MUST 由运维在入口侧限制该路径只对运营网段开放。</p>
     */
    @PostMapping("/batch-refund-overtime")
    public ResultVO<BatchRefundResult> batchRefundOvertime(@RequestBody BatchRefundOvertimeRequest request) {
        return gateTxnPayService.batchRefundOvertime(request);
    }

    /** 根据扣费订单发起支付退款申请，由 pay-sign 对接实际支付渠道。 */
    @PostMapping("/{orderNo}/refund")
    public ResultVO<RequestRefundResult> requestRefund(@PathVariable String orderNo,
                                                        @RequestBody GateTxnPayRefundRequest request) {
        return gateTxnPayService.requestRefund(orderNo, request);
    }

    /**
     * 历史订单地铁原价（{@code ORIGINAL_FARE}）补数，按进出站重查票价后回填空值行。
     *
     * <p>{@code dryRun} 默认 true 只试算；确认 {@code updatedList} 与 {@code suspectList}
     * 后再传 {@code dryRun=false} 落库。回填只写空值行，重复调用幂等。</p>
     *
     * <p>⚠️ 与本模块其它 {@code /page/**} 接口一样，**当前没有鉴权**：模块内无 spring-security、
     * 无全局拦截器，网络可达方即可调用，而这是**写接口**。上线前 MUST 由运维在入口侧限制
     * 该路径只对运营网段开放，或补齐与 {@code AccountRequestVerifier} 对齐的验签。</p>
     */
    @PostMapping("/backfill-original-fare")
    public ResultVO<Map<String, Object>> backfillOriginalFare(@RequestBody OriginalFareBackfillRequest request) {
        return originalFareBackfillService.backfillOriginalFare(request);
    }
}

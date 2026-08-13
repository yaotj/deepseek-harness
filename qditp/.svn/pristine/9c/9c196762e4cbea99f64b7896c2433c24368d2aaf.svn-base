package com.chinasofti.huateng.gatetxnpay.controller.page;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
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

    public GateTxnPayPageController(GateTxnPayService gateTxnPayService) {
        this.gateTxnPayService = gateTxnPayService;
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
        return gateTxnPayService.page(orderNo, cardId, thirdUserId, signChannelCode, cardType, debitStatus,
                startDate, endDate, pageNum, pageSize);
    }

    /** 根据扣费订单发起支付退款申请，由 pay-sign 对接实际支付渠道。 */
    @PostMapping("/{orderNo}/refund")
    public ResultVO<RequestRefundResult> requestRefund(@PathVariable String orderNo,
                                                        @RequestBody GateTxnPayRefundRequest request) {
        return gateTxnPayService.requestRefund(orderNo, request);
    }
}

package com.chinasofti.huateng.alipay.paysign.controller.internal;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRefundService;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.AlipayTxnPayQueryService;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.AlipayTxnRefundService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayTxnBriefDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/internal/alipay/payment")
public class AlipayPaymentInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentInternalController.class);

    private final AlipayTxnPayQueryService alipayTxnPayQueryService;
    private final AlipayPayRefundService alipayPayRefundService;
    private final AlipayTxnRefundService alipayTxnRefundService;

    public AlipayPaymentInternalController(AlipayTxnPayQueryService alipayTxnPayQueryService,
                                           AlipayPayRefundService alipayPayRefundService,
                                           AlipayTxnRefundService alipayTxnRefundService) {
        this.alipayTxnPayQueryService = alipayTxnPayQueryService;
        this.alipayPayRefundService = alipayPayRefundService;
        this.alipayTxnRefundService = alipayTxnRefundService;
    }

    @PostMapping("/payQuery")
    public AlipayTripPayQueryRespDTO payQuery(@RequestBody AlipayTripPayQueryReqDTO request) {
        log.info("收到支付查询（新链路）: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripPayQueryRespDTO response = alipayTxnPayQueryService.payQuery(request);
        log.info("支付查询响应结果（新链路）：retCode={}, tradeStatus={}",
                response != null ? response.getRetCode() : "null",
                response != null ? response.getTradeStatus() : "null");
        return response;
    }


    @PostMapping("/payTxnBrief")
    public List<AlipayTripPayTxnBriefDTO> payTxnBrief(@RequestBody List<String> orderNos) {
        log.info("收到批量补齐支付明细请求: 条数={}", orderNos != null ? orderNos.size() : 0);
        List<AlipayTripPayTxnBriefDTO> response = alipayTxnPayQueryService.queryPayTxnBrief(orderNos);
        log.info("批量补齐支付明细响应结果: 命中条数={}", response != null ? response.size() : 0);
        return response;
    }

    @PostMapping("/requestRefund")
    public AlipayTripRequestRefundRespDTO requestRefund(@RequestBody AlipayTripRequestRefundReqDTO request) {
        log.info("收到退款申请: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripRequestRefundRespDTO response = alipayPayRefundService.requestRefund(request);
        log.info("退款申请响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }


    @PostMapping("/requestTxnRefund")
    public AlipayTripTxnRefundRespDTO requestTxnRefund(@RequestBody AlipayTripTxnRefundReqDTO request) {
        log.info("收到退款申请（新表链路）: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripTxnRefundRespDTO response = alipayTxnRefundService.requestRefund(request);
        log.info("退款申请响应结果（新表链路）：retCode={}, retMsg={}, refundOrderNo={}",
                response != null ? response.getRetCode() : "null",
                response != null ? response.getRetMsg() : "null",
                response != null ? response.getRefundOrderNo() : "null");
        return response;
    }
}

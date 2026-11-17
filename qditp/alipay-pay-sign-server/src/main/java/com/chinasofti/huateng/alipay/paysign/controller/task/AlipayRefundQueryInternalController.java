package com.chinasofti.huateng.alipay.paysign.controller.task;

import com.chinasofti.huateng.alipay.paysign.service.impl.refund.RefundQueryCompensationService;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.TxnRefundQueryCompensationService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/alipay/refund")
public class AlipayRefundQueryInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayRefundQueryInternalController.class);

    private final RefundQueryCompensationService refundQueryCompensationService;
    private final TxnRefundQueryCompensationService txnRefundQueryCompensationService;

    public AlipayRefundQueryInternalController(RefundQueryCompensationService refundQueryCompensationService,
                                               TxnRefundQueryCompensationService txnRefundQueryCompensationService) {
        this.refundQueryCompensationService = refundQueryCompensationService;
        this.txnRefundQueryCompensationService = txnRefundQueryCompensationService;
    }


    @PostMapping("/compensateQuery")
    public AlipayCommonResponse compensateQuery() {
        log.info("收到退款回查补偿请求");
        OutboxScan.Result legacyScan = refundQueryCompensationService.compensate();
        OutboxScan.Result txnScan = txnRefundQueryCompensationService.compensate();
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("旧表扫描 " + legacyScan.scanned() + " 条、收口 " + legacyScan.success()
                + " 条、仍处理中 " + legacyScan.failed() + " 条；新表扫描 " + txnScan.scanned()
                + " 条、收口 " + txnScan.success() + " 条、仍处理中 " + txnScan.failed() + " 条");
        return response;
    }
}

package com.chinasofti.huateng.alipay.paysign.controller.internal;

import com.chinasofti.huateng.alipay.paysign.service.impl.query.AlipayArrearsQueryService;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/alipayPay")
public class AlipayArrearsInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayArrearsInternalController.class);

    private final AlipayArrearsQueryService alipayArrearsQueryService;

    public AlipayArrearsInternalController(AlipayArrearsQueryService alipayArrearsQueryService) {
        this.alipayArrearsQueryService = alipayArrearsQueryService;
    }


    @PostMapping("/hasUnsettledOrderByCard")
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        log.info("按卡查询支付宝出行未结清订单, cardId={}", request != null ? request.getCardId() : null);
        CardUnsettledQueryRespDTO response = alipayArrearsQueryService.hasUnsettledOrderByCard(
                request != null ? request.getCardId() : null);
        log.info("按卡查询支付宝出行未结清订单完成, 返回={}", response);
        return response;
    }
}

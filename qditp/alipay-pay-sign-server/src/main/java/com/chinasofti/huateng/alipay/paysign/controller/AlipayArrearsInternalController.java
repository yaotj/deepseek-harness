package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.service.AlipayArrearsQueryService;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行欠费内部只读接口。
 *
 * <p>只提供查询，不暴露任何状态变更能力，因此无需鉴权与归属校验。
 * 后续若在本前缀下新增写接口，MUST 先补验签（对齐现有实现，NEVER 自造签名逻辑）。</p>
 */
@RestController
@RequestMapping("/internal/alipayPay")
public class AlipayArrearsInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayArrearsInternalController.class);

    private final AlipayArrearsQueryService alipayArrearsQueryService;

    public AlipayArrearsInternalController(AlipayArrearsQueryService alipayArrearsQueryService) {
        this.alipayArrearsQueryService = alipayArrearsQueryService;
    }

    /**
     * 按卡号查询支付宝出行链路是否仍有未结清订单（供 blacklist-server 盘点调用）。
     */
    @PostMapping("/hasUnsettledOrderByCard")
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        log.info("按卡查询支付宝出行未结清订单, cardId={}", request != null ? request.getCardId() : null);
        CardUnsettledQueryRespDTO response = alipayArrearsQueryService.hasUnsettledOrderByCard(
                request != null ? request.getCardId() : null);
        log.info("按卡查询支付宝出行未结清订单完成, 返回={}", response);
        return response;
    }
}

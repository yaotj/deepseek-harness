package com.chinasofti.huateng.acc.security.server.controller;

import com.chinasofti.huateng.acc.security.feign.domain.commonmac.SaleAndRefundParam;
import com.chinasofti.huateng.acc.security.server.service.CommonSaleAndRefundService;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 公共发售、退款key计算Controller
 */
@Validated
@RestController
public class CommonSaleAndRefundController {

    @Autowired
    private CommonSaleAndRefundService commonSaleAndRefundService;

    /**
     * 公共发售、退款key计算
     */
    @PostMapping("/get/saleAndRefund/key")
    public ResultVO<String> getSaleKey(@RequestBody @Valid SaleAndRefundParam param) throws InterruptedException {
        return commonSaleAndRefundService.getSaleAndRefundKey(param);
    }

}


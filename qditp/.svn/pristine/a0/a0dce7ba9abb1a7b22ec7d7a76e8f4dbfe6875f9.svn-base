package com.chinasofti.huateng.acc.security.server.controller;

import com.chinasofti.huateng.acc.security.feign.domain.commonmac.InvestMac1Param;
import com.chinasofti.huateng.acc.security.feign.domain.commonmac.InvestMac2Param;
import com.chinasofti.huateng.acc.security.server.service.CommonMacService;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 公共TAC计算Controller
 */
@Validated
@RestController
public class CommonMacController {

    @Autowired
    private CommonMacService commonMacService;

    /**
     * @Description: (充值mac1验证方法)
     */
    @PostMapping("/verify/mac1")
    public ResultVO<Boolean> verifyMac1(@RequestBody @Valid InvestMac1Param param) throws InterruptedException {
        return commonMacService.verifyMac1(param);
    }

    /**
     * 充值mac2计算
     */
    @PostMapping("/get/mac2")
    public ResultVO<String> getMac2(@RequestBody @Valid InvestMac2Param param) throws InterruptedException {
        return commonMacService.getMac2(param);
    }

}


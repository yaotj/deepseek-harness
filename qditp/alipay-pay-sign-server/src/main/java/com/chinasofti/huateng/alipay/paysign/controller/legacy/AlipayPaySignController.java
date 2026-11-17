package com.chinasofti.huateng.alipay.paysign.controller.legacy;

import com.chinasofti.huateng.alipay.paysign.controller.sign.AlipayContractController;
import com.chinasofti.huateng.alipay.paysign.service.AlipayContractService;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/channel")
public class AlipayPaySignController {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaySignController.class);
    private final AlipayContractService alipayContractService;

    public AlipayPaySignController(AlipayContractService alipayContractService) {
        this.alipayContractService = alipayContractService;
    }


    @GetMapping("/selectSignInfo")
    public AlipaySignInfoDTO selectSignInfo(@RequestParam String thirdUserId) {
        log.info("查询签约信息: thirdUserId={}", thirdUserId);
        AlipaySignInfoDTO response = alipayContractService.selectSignInfo(thirdUserId);
        log.info("查询签约信息响应结果：{}", response != null ? response.getThirdUserId() : "null");
        return response;
    }
}

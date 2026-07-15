package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPaySignService;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝签约 Controller。
 */
@RestController
@RequestMapping("/channel")
public class AlipayPaySignController {

    private final AlipayPaySignService alipayPaySignService;

    public AlipayPaySignController(AlipayPaySignService alipayPaySignService) {
        this.alipayPaySignService = alipayPaySignService;
    }

    /**
     * 添加签约信息。
     */
    @PostMapping("/addContract")
    public AlipayTripAddContractRespDTO addContract(@RequestBody AlipayTripAddContractReqDTO request) {
        return alipayPaySignService.addContract(request);
    }

    /**
     * 解约登记。
     */
    @PostMapping("/terminateContract")
    public AlipayTripTerminateContractRespDTO terminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        return alipayPaySignService.terminateContract(request);
    }

    /**
     * 查询用户签约信息。
     */
    @GetMapping("/selectSignInfo")
    public AlipaySignInfoDTO selectSignInfo(@RequestParam String thirdUserId) {
        return alipayPaySignService.selectSignInfo(thirdUserId);
    }
}

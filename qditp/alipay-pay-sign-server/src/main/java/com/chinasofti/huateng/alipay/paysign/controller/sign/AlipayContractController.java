package com.chinasofti.huateng.alipay.paysign.controller.sign;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipaySignContractService;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/channel")
public class AlipayContractController {

    private static final Logger log = LoggerFactory.getLogger(AlipayContractController.class);

    private final AlipaySignContractService alipaySignContractService;
    private final AlipayTerminationService alipayTerminationService;

    public AlipayContractController(AlipaySignContractService alipaySignContractService,
                                    AlipayTerminationService alipayTerminationService) {
        this.alipaySignContractService = alipaySignContractService;
        this.alipayTerminationService = alipayTerminationService;
    }


    @PostMapping("/addContract")
    public AlipayTripAddContractRespDTO addContract(@RequestBody AlipayTripAddContractReqDTO request) {
        log.info("收到签约请求: thirdUserId={}", request != null ? request.getThirdUserId() : null);
        AlipayTripAddContractRespDTO response = alipaySignContractService.addContract(request);
        log.info("添加签约信息响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }


    @PostMapping("/terminateContract")
    public AlipayTripTerminateContractRespDTO terminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        log.info("收到解约登记请求: agreementCode={}", request != null ? request.getAgreementCode() : null);
        AlipayTripTerminateContractRespDTO response = alipayTerminationService.terminateContract(request);
        log.info("解约登记响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}

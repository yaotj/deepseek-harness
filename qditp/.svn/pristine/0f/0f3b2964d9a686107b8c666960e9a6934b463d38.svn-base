package com.chinasofti.huateng.wallet.controller.ci.app;

import com.chinasofti.huateng.wallet.model.contract.RequestContractResultReqDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestContractResultRespDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestTerminationReqDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestTerminationRespDTO;
import com.chinasofti.huateng.wallet.service.WalletContractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包签约相关接口。
 */
@RestController
@RequestMapping("/ci/app")
public class WalletContractController {
    private static final Logger log = LoggerFactory.getLogger(WalletContractController.class);
    @Autowired
    private WalletContractService walletContractService;

    /**
     * IF8A-06 请求解约。
     */
    @PostMapping("/requestTermination")
    public RequestTerminationRespDTO requestTermination(@RequestBody RequestTerminationReqDTO request) {
        log.info("接收到请求解约接口报文: {}", request);
        return walletContractService.requestTermination(request);
    }

    /**
     * IF8A-22 签约结果咨询。
     */
    @PostMapping("/requestContractResult")
    public RequestContractResultRespDTO requestContractResult(@RequestBody RequestContractResultReqDTO request) {
        log.info("接收到签约结果咨询接口报文: {}", request);
        return walletContractService.requestContractResult(request);
    }
}

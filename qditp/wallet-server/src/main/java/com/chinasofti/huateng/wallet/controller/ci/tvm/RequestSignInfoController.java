package com.chinasofti.huateng.wallet.controller.ci.tvm;

import com.chinasofti.huateng.wallet.model.sign.RequestSignInfoReqDTO;
import com.chinasofti.huateng.wallet.model.sign.RequestSignInfoRespDTO;
import com.chinasofti.huateng.wallet.service.WalletSignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IF8A-16 请求签约请求信息接口。
 */
@RestController
@RequestMapping("/ci/tvm")
public class RequestSignInfoController {
    private static final Logger log = LoggerFactory.getLogger(RequestSignInfoController.class);
    @Autowired
    private WalletSignService walletSignService;

    /**
     * IF8A-16 请求签约请求信息。
     */
    @PostMapping("/requestSignInfo")
    public RequestSignInfoRespDTO requestSignInfo(@RequestBody RequestSignInfoReqDTO request) {
        log.info("接收到请求签约请求信息接口报文: {}", request);
        return walletSignService.requestSignInfo(request);
    }
}

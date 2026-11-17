package com.chinasofti.huateng.paysign.controller.ci.app;

import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaySignController {
    private static final Logger log = LoggerFactory.getLogger(PaySignController.class);

    private final PaySignService paySignService;

    public PaySignController(PaySignService paySignService) {
        this.paySignService = paySignService;
    }

    @PostMapping("/requestSignInfo")
    public RequestSignInfoResult requestSignInfo(@RequestBody RequestSignInfoReqDTO request) {
        log.info("接收到请求签约信息报文: {}", request);
        return paySignService.requestSignInfo(request, SignChannelEnum.METRO_APP.getCode());
    }

    @GetMapping("/querySignInfoBySeq")
    public PaySignInfoDTO querySignInfoBySeq(@RequestParam String requestSignSeq) {
        log.info("接收到查询签约信息请求: requestSignSeq={}", requestSignSeq);
        return paySignService.querySignInfoBySeq(requestSignSeq);
    }
}

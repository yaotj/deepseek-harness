package com.chinasofti.huateng.paysign.controller.ticket;

import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ticket")
public class TerminationNotifyController {
    private static final Logger log = LoggerFactory.getLogger(TerminationNotifyController.class);

    @Autowired
    private PaySignService paySignService;

    @PostMapping("/receiveTerminationResultFromItp")
    public BaseRespDTO receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.METRO_APP.getCode());
    }
}

package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.fep.app.service.AppService;
import com.chinasofti.huateng.model.app.ReceiveBlackListFromItpReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IF8B-03 接收黑名单结果通知接口。
 */
@RestController
@RequestMapping("/app")
public class AppNotifyController {

    private static final Logger log = LoggerFactory.getLogger(AppNotifyController.class);

    @Autowired
    private AppService appService;

    /**
     * 黑名单状态变更通知。
     *
     * @param request 黑名单结果通知请求参数
     * @return 通用响应
     */
    @PostMapping("/receiveBlackListFromItp")
    public CommonResult receiveBlackListFromItp(@RequestBody ReceiveBlackListFromItpReqDTO request) {
        log.info("IF8B-03 接收黑名单结果通知, request={}", request);
        return appService.receiveBlackListFromItp(request);
    }
}

package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AppService;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 票务类接口入口。
 */
@RestController
@RequestMapping("/app/ticket")
public class FepAppTicketController {
    private static final Logger log = LoggerFactory.getLogger(FepAppTicketController.class);

    @Autowired
    private AppService appService;

    /**
     * IF8A-73 查询黑名单（FormData 格式）。
     *
     * @param request FormData 格式的公共请求报文
     * @return 查询黑名单结果
     */
    @PostMapping("/queryBlackList")
    public QueryBlackListResult queryBlackList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-73 查询黑名单,请求参数: {}", request);
        QueryBlackListReqDTO bizData = JSON.parseObject(request.getBizData(), QueryBlackListReqDTO.class);
        return appService.queryBlackList(bizData);
    }
}

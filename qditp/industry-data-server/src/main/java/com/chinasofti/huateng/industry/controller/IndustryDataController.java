package com.chinasofti.huateng.industry.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.industry.service.IndustryCardDataService;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行业数据接口。
 */
@RestController
@RequestMapping("/ci/industry")
public class IndustryDataController {
    private static final Logger log = LoggerFactory.getLogger(IndustryDataController.class);

    private final IndustryCardDataService industryCardDataService;

    public IndustryDataController(IndustryCardDataService industryCardDataService) {
        this.industryCardDataService = industryCardDataService;
    }

    /**
     * 生成完整行业卡数据。
     *
     * @param request 卡数据生成请求
     * @return 卡数据生成结果
     */
    @PostMapping("/buildCardData")
    public IndustryCardDataBuildRespDTO buildCardData(@RequestBody IndustryCardDataBuildReqDTO request) {
        log.info("生成行业卡数据, request={}", JSON.toJSONString(request));
        IndustryCardDataBuildRespDTO response = industryCardDataService.buildCardData(request);
        log.info("生成行业卡数据完成, response={}", JSON.toJSONString(response));
        return response;
    }
}

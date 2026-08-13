package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.IndustryDataService;
import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestIndustryDataResult;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 行业数据及离线码数据接口入口。
 *
 * <p>本层只承接 APP 请求；用户信息、乘车状态和行业卡数据生成由
 * {@link IndustryDataService} 协调 account-server、ticket-server 和 industry-data-server 完成。</p>
 */
@RestController
@RequestMapping("/ci/app")
public class AppIndustryDataController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppIndustryDataController.class);

    private final IndustryDataService industryDataService;

    public AppIndustryDataController(IndustryDataService industryDataService) {
        this.industryDataService = industryDataService;
    }

    /**
     * IF8A-03 请求行业数据。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestIndustryDataReqDTO} JSON
     * @return 行业卡数据结果
     */
    @PostMapping("/requestIndustryData")
    public RequestIndustryDataResult requestIndustryData(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-03 请求行业数据, 请求参数: {}", request);
        return industryDataService.requestIndustryData(parseBizData(request, RequestIndustryDataReqDTO.class));
    }

    /**
     * IF8D_03 获取离线码数据。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestNoSignalDataReqDTO} JSON
     * @return 离线码行业数据结果
     */
    @PostMapping("/requestNoSignalData")
    public RequestNoSignalDataResult requestNoSignalData(@ModelAttribute CommonFormRequest request) {
        log.info("IF8D_03 获取离线码数据, 请求参数: {}", request);
        return industryDataService.requestNoSignalData(parseBizData(request, RequestNoSignalDataReqDTO.class));
    }
}

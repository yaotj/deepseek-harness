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
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 行业数据接口入口。
 *
 * <p>涵盖 IF8A-03 请求行业数据、IF8D-03 请求离线码数据。</p>
 */
@RestController
public class IndustryDataController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(IndustryDataController.class);

    private final IndustryDataService industryDataService;

    public IndustryDataController(IndustryDataService industryDataService) {
        this.industryDataService = industryDataService;
    }

    @PostMapping({"/ci/app/requestIndustryData"})
    public RequestIndustryDataResult requestIndustryData(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-03 请求行业数据, 请求参数: {}", request);
        return industryDataService.requestIndustryData(parseBizData(request, RequestIndustryDataReqDTO.class));
    }

    @PostMapping({"/ci/app/requestNoSignalData"})
    public RequestNoSignalDataResult requestNoSignalData(@ModelAttribute CommonFormRequest request) {
        log.info("IF8D-03 请求离线码数据, 请求参数: {}", request);
        return industryDataService.requestNoSignalData(parseBizData(request, RequestNoSignalDataReqDTO.class));
    }
}

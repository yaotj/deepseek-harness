package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.ParaAppService;
import com.chinasofti.huateng.model.app.RequestBuySinlgeTicketMaxNumResult;
import com.chinasofti.huateng.model.app.RequestLineCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestLineCodeListResult;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionReqDTO;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionResult;
import com.chinasofti.huateng.model.app.RequestStationCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestStationCodeListResult;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 基础参数查询接口入口。
 *
 * <p>线路、车站、票价和站点版本均由 para-server 提供。
 * 同时支持 {@code /ci/app} 和 {@code /app} 两条路径。</p>
 */
@RestController
public class AppParaController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppParaController.class);

    private final ParaAppService paraAppService;

    public AppParaController(ParaAppService paraAppService) {
        this.paraAppService = paraAppService;
    }

    @PostMapping({"/ci/app/requestBuySinlgeTicketMaxNum", "/app/requestBuySinlgeTicketMaxNum",
                  "/ci/app/requestBuySingleTicketMaxNum", "/app/requestBuySingleTicketMaxNum"})
    public RequestBuySinlgeTicketMaxNumResult requestBuySingleTicketMaxNum(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-09 获取单次购买单程票最大张数, 请求参数: {}", request);
        return paraAppService.requestBuySinlgeTicketMaxNum();
    }

    @PostMapping({"/ci/app/requestLineCodeList", "/app/requestLineCodeList"})
    public RequestLineCodeListResult requestLineCodeList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-07 获取线路代码, 请求参数: {}", request);
        return paraAppService.requestLineCodeList(parseBizData(request, RequestLineCodeListReqDTO.class));
    }

    @PostMapping({"/ci/app/requestStationCodeList", "/app/requestStationCodeList"})
    public RequestStationCodeListResult requestStationCodeList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-08 获取车站代码, 请求参数: {}", request);
        return paraAppService.requestStationCodeList(parseBizData(request, RequestStationCodeListReqDTO.class));
    }

    @PostMapping({"/ci/app/requestTicketPriceByStation", "/app/requestTicketPriceByStation"})
    public RequestTicketPriceByStationResult requestTicketPriceByStation(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-10 计算票价, 请求参数: {}", request);
        return paraAppService.requestTicketPriceByStation(parseBizData(request, RequestTicketPriceByStationReqDTO.class));
    }

    @PostMapping({"/ci/app/requestLineStationCodeVersion", "/app/requestLineStationCodeVersion"})
    public RequestLineStationCodeVersionResult requestLineStationCodeVersion(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-17 获取线路站点代码版本, 请求参数: {}", request);
        return paraAppService.requestLineStationCodeVersion(parseBizData(request, RequestLineStationCodeVersionReqDTO.class));
    }
}

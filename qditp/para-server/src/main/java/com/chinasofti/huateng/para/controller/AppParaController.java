package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.para.service.AppParaService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/ci/app")
public class AppParaController {
    private final AppParaService appParaService;

    public AppParaController(AppParaService appParaService) {
        this.appParaService = appParaService;
    }


    @PostMapping("/requestBuySinlgeTicketMaxNum")
    public RequestBuySinlgeTicketMaxNumResult requestBuySinlgeTicketMaxNum() {
        return appParaService.requestBuySinlgeTicketMaxNum();
    }


    @PostMapping("/requestLineCodeList")
    public RequestLineCodeListResult requestLineCodeList(@RequestBody(required = false) RequestLineCodeListReqDTO request) {
        return appParaService.requestLineCodeList(request);
    }


    @PostMapping("/requestStationCodeList")
    public RequestStationCodeListResult requestStationCodeList(@RequestBody RequestStationCodeListReqDTO request) {
        return appParaService.requestStationCodeList(request);
    }


    @PostMapping("/requestTicketPriceByStation")
    public RequestTicketPriceByStationResult requestTicketPriceByStation(@RequestBody RequestTicketPriceByStationReqDTO request) {
        return appParaService.requestTicketPriceByStation(request);
    }


    @PostMapping("/requestLineStationCodeVersion")
    public RequestLineStationCodeVersionResult requestLineStationCodeVersion(@RequestBody(required = false) RequestLineStationCodeVersionReqDTO request) {
        return appParaService.requestLineStationCodeVersion(request);
    }


    @PostMapping("/requestStationName")
    public RequestStationNameResult requestStationName(@RequestBody RequestStationNameReqDTO request) {
        return appParaService.requestStationName(request);
    }


    @PostMapping("/requestStationLineInfo")
    public RequestStationLineInfoResult requestStationLineInfo(@RequestBody RequestStationLineInfoReqDTO request) {
        return appParaService.requestStationLineInfo(request);
    }


    @PostMapping("/requestStationNameBatch")
    public RequestStationNameBatchResult requestStationNameBatch(@RequestBody RequestStationNameBatchReqDTO request) {
        return appParaService.requestStationNameBatch(request);
    }
}

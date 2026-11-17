package com.chinasofti.huateng.ticket.controller.internal;

import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestIndustryDataResult;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataResult;
import com.chinasofti.huateng.ticket.industry.IndustryDataOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/ticket/industry")
@Tag(name = "行业数据编排（内部）")
public class IndustryDataInternalController {

    private final IndustryDataOrchestrator industryDataOrchestrator;

    public IndustryDataInternalController(IndustryDataOrchestrator industryDataOrchestrator) {
        this.industryDataOrchestrator = industryDataOrchestrator;
    }

    @PostMapping("/online")
    @Operation(summary = "IF8A-03 请求行业数据（在线码）")
    public RequestIndustryDataResult requestIndustryData(@RequestBody RequestIndustryDataReqDTO request) {
        return industryDataOrchestrator.requestIndustryData(request);
    }

    @PostMapping("/offline")
    @Operation(summary = "IF8D-03 获取离线码数据")
    public RequestNoSignalDataResult requestNoSignalData(@RequestBody RequestNoSignalDataReqDTO request) {
        return industryDataOrchestrator.requestNoSignalData(request);
    }
}

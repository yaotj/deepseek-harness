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

/**
 * 行业数据编排的内部入口（ADR-D142）。
 *
 * <p>只被 `fep-app-server` 转发调用（IF8A-03 / IF8D-03），**不对 APP 直接暴露**：
 * 对外 URL 仍是 `fep-app-server` 的 `/ci/app/requestIndustryData` 与 `/ci/app/requestNoSignalData`。
 *
 * <p>两个端点都是**只读**的（生码不落库、不改任何状态），因此不属 §5.2「状态变更型接口 MUST 有鉴权」
 * 的范围；鉴权现状与本项目其余 `/internal/**` 一致（当前无校验，上线前随那批一起恢复）。
 */
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

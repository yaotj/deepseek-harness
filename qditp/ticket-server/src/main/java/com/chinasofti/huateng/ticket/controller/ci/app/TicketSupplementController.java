package com.chinasofti.huateng.ticket.controller.ci.app;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.ticket.supplement.SupplementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 补站类接口控制器（APP 自助补站 + BOM 单边处理）。 */
@RestController
@RequestMapping("/ci/app")
public class TicketSupplementController {
    private static final Logger log = LoggerFactory.getLogger(TicketSupplementController.class);

    private final SupplementService supplementService;

    public TicketSupplementController(SupplementService supplementService) {
        this.supplementService = supplementService;
    }

    /** IF8A-04 请求自助补站。 */
    @PostMapping("/requestExcessFare")
    public RequestExcessFareResult requestExcessFare(@RequestBody RequestExcessFareReqDTO request) {
        log.info("IF8A-04 请求自助补站，请求参数：{}", JSON.toJSONString(request));
        return supplementService.requestExcessFare(request);
    }

    /** IF5A-01 请求票卡分析。 */
    @PostMapping("/requestCardDataAnalyse")
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(@RequestBody RequestCardDataAnalyseReqDTO request) {
        log.info("IF5A-01 请求票卡分析，请求参数：{}", JSON.toJSONString(request));
        return supplementService.requestCardDataAnalyse(request);
    }

    /** IF5A-03 请求票卡更新。 */
    @PostMapping("/requestUpdateCardData")
    public RequestCardDataUpdateRespDTO requestUpdateCardData(@RequestBody RequestCardDataUpdateReqDTO request) {
        log.info("IF5A-03 请求票卡更新，请求参数：{}", JSON.toJSONString(request));
        return supplementService.requestCardDataUpdate(request);
    }
}

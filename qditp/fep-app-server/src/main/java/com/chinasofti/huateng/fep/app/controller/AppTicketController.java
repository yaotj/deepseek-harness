package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.fep.app.service.TicketAppService;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 票务域接口入口。
 */
@RestController
public class AppTicketController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppTicketController.class);

    private final TicketAppService ticketAppService;

    public AppTicketController(TicketAppService ticketAppService) {
        this.ticketAppService = ticketAppService;
    }

    @PostMapping({"/ci/app/queryBlackList", "/app/queryBlackList", "/app/ticket/queryBlackList"})
    public QueryBlackListResult queryBlackList(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-73 查询黑名单, 请求参数: {}", request);
        return ticketAppService.queryBlackList(parseBizData(request, QueryBlackListReqDTO.class));
    }

    @PostMapping({"/ci/app/queryUserItinerary", "/app/queryUserItinerary", "/app/ticket/queryUserItinerary"})
    public QueryUserItineraryResult queryUserItinerary(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-29 查询用户上次行程, 请求参数: {}", request);
        return ticketAppService.queryUserItinerary(parseBizData(request, QueryUserItineraryReqDTO.class));
    }

    @PostMapping({"/ci/app/requestExcessFare", "/app/requestExcessFare", "/app/ticket/requestExcessFare"})
    public RequestExcessFareResult requestExcessFare(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-04 请求自助补站, 请求参数: {}", request);
        return ticketAppService.requestExcessFare(parseBizData(request, RequestExcessFareReqDTO.class));
    }

    @PostMapping({"/ci/app/requestTransList", "/app/requestTransList", "/app/ticket/requestTransList"})
    public RequestTransListResult requestTransList(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-05 请求查询交易记录, 请求参数: {}", request);
        return ticketAppService.requestTransList(parseBizData(request, RequestTransListReqDTO.class));
    }

    @PostMapping({"/ci/app/requestTransDetail", "/app/requestTransDetail", "/app/ticket/requestTransDetail"})
    public RequestTransDetailResult requestTransDetail(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-34 获取订单详情, 请求参数: {}", request);
        return ticketAppService.requestTransDetail(parseBizData(request, RequestTransDetailReqDTO.class));
    }

    @PostMapping({"/ci/app/requestTransStatistics", "/app/requestTransStatistics", "/app/ticket/requestTransStatistics"})
    public RequestTransStatisticsResult requestTransStatistics(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-41 查询账单统计, 请求参数: {}", request);
        return ticketAppService.requestTransStatistics(parseBizData(request, RequestTransStatisticsReqDTO.class));
    }

    /**
     * 实名查询 Mock 桩，契约待确认。
     */
    @PostMapping("/app/ticket/realName")
    public CommonResult realName(@ModelAttribute ItpCommonFormRequest request) {
        log.info("实名查询(Mock 桩, 契约待确认), 请求参数: {}", request);
        CommonResult result = new CommonResult();
        result.setRetCode("0000");
        result.setRetMsg("成功");
        return result;
    }
}

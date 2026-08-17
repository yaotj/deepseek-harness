package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 票务域接口入口（/ci/app）。
 *
 * <p>涵盖行程查询、黑名单、自助补站、交易记录、账单统计和订单详情等功能。
 * 黑名单由 blacklist-server 处理；行程、补站和交易记录由 ticket-server 处理。</p>
 */
@RestController
@RequestMapping("/ci/app")
public class AppTicketController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppTicketController.class);

    private final TicketAppService ticketAppService;

    public AppTicketController(TicketAppService ticketAppService) {
        this.ticketAppService = ticketAppService;
    }

    /**
     * IF8A-73 查询黑名单。
     */
    @PostMapping("/queryBlackList")
    public QueryBlackListResult queryBlackList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-73 查询黑名单, 请求参数: {}", request);
        return ticketAppService.queryBlackList(parseBizData(request, QueryBlackListReqDTO.class));
    }

    /**
     * IF8A-29 查询用户上次行程。
     */
    @PostMapping("/queryUserItinerary")
    public QueryUserItineraryResult queryUserItinerary(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-29 查询用户上次行程, 请求参数: {}", request);
        return ticketAppService.queryUserItinerary(parseBizData(request, QueryUserItineraryReqDTO.class));
    }

    /**
     * IF8A-04 请求自助补站。
     */
    @PostMapping("/requestExcessFare")
    public RequestExcessFareResult requestExcessFare(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-04 请求自助补站, 请求参数: {}", request);
        return ticketAppService.requestExcessFare(parseBizData(request, RequestExcessFareReqDTO.class));
    }

    /**
     * IF8A-05 请求查询交易记录。
     */
    @PostMapping("/requestTransList")
    public RequestTransListResult requestTransList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-05 请求查询交易记录, 请求参数: {}", request);
        return ticketAppService.requestTransList(parseBizData(request, RequestTransListReqDTO.class));
    }

    /**
     * IF8A-34 获取订单详情。
     */
    @PostMapping("/requestTransDetail")
    public RequestTransDetailResult requestTransDetail(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-34 获取订单详情, 请求参数: {}", request);
        return ticketAppService.requestTransDetail(parseBizData(request, RequestTransDetailReqDTO.class));
    }

    /**
     * IF8A-41 查询账单统计。
     */
    @PostMapping("/requestTransStatistics")
    public RequestTransStatisticsResult requestTransStatistics(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-41 查询账单统计, 请求参数: {}", request);
        return ticketAppService.requestTransStatistics(parseBizData(request, RequestTransStatisticsReqDTO.class));
    }
}

package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.TicketAppService;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
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
 * APP 单程票、行程及黑名单查询接口入口。
 *
 * <p>本层仅解析 APP FormData 报文并调用票务领域服务；黑名单由 blacklist-server 处理，
 * 行程、自助补站和交易记录由 ticket-server 处理。</p>
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
     *
     * <p>保留 {@code /ci/app/ticket/queryBlackList} 兼容入口。</p>
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link QueryBlackListReqDTO} JSON
     * @return 黑名单查询结果
     */
    @PostMapping("/ticket/queryBlackList")
    public QueryBlackListResult queryBlackList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-73 查询黑名单,请求参数: {}", request);
        return ticketAppService.queryBlackList(parseBizData(request, QueryBlackListReqDTO.class));
    }

    /**
     * IF8A-29 查询用户上次行程。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link QueryUserItineraryReqDTO} JSON
     * @return 用户当前或上次行程信息
     */
    @PostMapping("/queryUserItinerary")
    public QueryUserItineraryResult queryUserItinerary(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-29 查询用户上次行程,请求参数: {}", request);
        return ticketAppService.queryUserItinerary(parseBizData(request, QueryUserItineraryReqDTO.class));
    }

    /**
     * IF8A-04 请求自助补站。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestExcessFareReqDTO} JSON
     * @return 自助补站结果
     */
    @PostMapping("/requestExcessFare")
    public RequestExcessFareResult requestExcessFare(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-04 请求自助补站,请求参数: {}", request);
        return ticketAppService.requestExcessFare(parseBizData(request, RequestExcessFareReqDTO.class));
    }

    /**
     * IF8A-05 请求查询交易记录。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestTransListReqDTO} JSON
     * @return 交易记录列表
     */
    @PostMapping("/requestTransList")
    public RequestTransListResult requestTransList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-05 请求查询交易记录,请求参数: {}", request);
        return ticketAppService.requestTransList(parseBizData(request, RequestTransListReqDTO.class));
    }

    /**
     * IF8A-41 查询账单统计。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestTransStatisticsReqDTO} JSON
     * @return 账单统计结果
     */
    @PostMapping("/requestTransStatistics")
    public RequestTransStatisticsResult requestTransStatistics(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-41 查询账单统计,请求参数: {}", request);
        return ticketAppService.requestTransStatistics(parseBizData(request, RequestTransStatisticsReqDTO.class));
    }
}

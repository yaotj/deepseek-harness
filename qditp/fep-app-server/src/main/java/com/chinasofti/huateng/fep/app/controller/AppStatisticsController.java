package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.TicketAppService;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 账单统计接口入口。
 *
 * <p>对应规范 IF8A-41 查询账单统计，接口地址 /app/requestTransStatistics。</p>
 */
@RestController
@RequestMapping("/app")
public class AppStatisticsController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppStatisticsController.class);

    private final TicketAppService ticketAppService;

    public AppStatisticsController(TicketAppService ticketAppService) {
        this.ticketAppService = ticketAppService;
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

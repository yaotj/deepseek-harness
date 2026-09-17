package com.chinasofti.huateng.transquery.controller.ci.app;

import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.transquery.service.TransQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** APP 交易查询入口（由 fep-app-server 经 rpc 调用）。 */
@RestController
@RequestMapping("/ci/app")
public class TransQueryController {

    private static final Logger log = LoggerFactory.getLogger(TransQueryController.class);

    @Autowired
    private TransQueryService transQueryService;

    /** IF8A-05 请求查询交易记录。 */
    @PostMapping("/requestTransList")
    public RequestTransListResult requestTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("IF8A-05 请求查询交易记录,请求参数: {}", request);
        return transQueryService.requestTransList(request);
    }

    /** IF8A-41 查询账单统计。 */
    @PostMapping("/requestTransStatistics")
    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        log.info("IF8A-41 查询账单统计,请求参数: {}", request);
        return transQueryService.requestTransStatistics(request);
    }

    /** IF8A-34 获取订单详情。 */
    @PostMapping("/requestTransDetail")
    public RequestTransDetailResult requestTransDetail(@RequestBody RequestTransDetailReqDTO request) {
        log.info("IF8A-34 获取订单详情,请求参数: {}", request);
        return transQueryService.requestTransDetail(request);
    }
}

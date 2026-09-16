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

/**
 * APP 交易查询入口（由 fep-app-server 经 rpc 调用）。
 *
 * <p><b>三条 URL 与 ticket-server 上的原路径逐字一致</b>，只换实现所在的服务：
 * {@code /ci/app/requestTransList} / {@code requestTransStatistics} / {@code requestTransDetail}。
 * 这是**对外契约**（fep-app-server 的 `TicketClient` 按这三个路径发请求），
 * <b>NEVER 改路径、NEVER 改请求 / 应答 DTO 的字段</b> —— 改了就要连带改接入层并重建其镜像。
 *
 * <p>入向不验签：与 ticket-server 原实现保持一致（`sign` 由 fep-app-server 原样透传，
 * 见 AGENTS.md §2.2.1「入向验签并非统一 SHA256WithRSA」）。三个接口都是**只读查询**，
 * 但 IF8A-34 详情内含**归属校验**（订单的 `thirdUserId` 必须与请求一致，
 * 见 {@code TransDetailQueryHandler}），<b>NEVER 删那段校验</b>，否则任何网络可达方
 * 都能按订单号查任意用户的行程与金额。
 */
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

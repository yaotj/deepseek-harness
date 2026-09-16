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

/**
 * 补站类接口控制器（APP 自助补站 + BOM 单边处理）。
 *
 * <p>由 TicketRideStatusController 拆出，URL 前缀与路径保持不变，对外契约零变化。
 *
 * <p>本类只做「日志 + 路由」，业务与入参校验全在 {@link SupplementService}（AGENTS.md §3.3）。
 * **NEVER 让本类再直接注入 supplement 包里的 Handler，也 NEVER 注入 gate 包的服务**——
 * 那正是 2026-09-14 重构前的耦合形态。
 *
 * <p>两条补站链路互不相同，改动时 NEVER 混用字段名与白名单：
 * <ul>
 *   <li>APP 自助补站：IF8A-04 {@code requestExcessFare} → ExcessFareHandler，
 *       参数 {@code upgradeAreaType}，{@code deviceId} = 站点码 + "36" + "01"</li>
 *   <li>BOM 单边处理：IF5A-01 {@code requestCardDataAnalyse} + IF5A-03
 *       {@code requestUpdateCardData} → CardDataHandler，
 *       参数 {@code updateType} + {@code adviceOpt}，{@code deviceId} = {@code operaterId}</li>
 * </ul>
 *
 * <p>BOM 侧真实入口是 face-pay-server 的 {@code /itpbom/ci/bom/**}，命中本类的 {@code /ci/app/**}；
 * TicketAgmController 的 {@code /ci/agm/requestCardData*} 是同名旁路，实际链路不经过它。
 *
 * <p>IF5A-03 无验签、无归属校验，是状态变更 + 涉及资金的接口，上线前必须补（用户 2026-09-10 裁决本轮不加）。
 */
@RestController
@RequestMapping("/ci/app")
public class TicketSupplementController {
    private static final Logger log = LoggerFactory.getLogger(TicketSupplementController.class);

    private final SupplementService supplementService;

    public TicketSupplementController(SupplementService supplementService) {
        this.supplementService = supplementService;
    }

    /**
     * IF8A-04 请求自助补站。
     */
    @PostMapping("/requestExcessFare")
    public RequestExcessFareResult requestExcessFare(@RequestBody RequestExcessFareReqDTO request) {
        log.info("IF8A-04 请求自助补站，请求参数：{}", JSON.toJSONString(request));
        return supplementService.requestExcessFare(request);
    }

    /**
     * IF5A-01 请求票卡分析。
     */
    @PostMapping("/requestCardDataAnalyse")
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(@RequestBody RequestCardDataAnalyseReqDTO request) {
        log.info("IF5A-01 请求票卡分析，请求参数：{}", JSON.toJSONString(request));
        return supplementService.requestCardDataAnalyse(request);
    }

    /**
     * IF5A-03 请求票卡更新。
     */
    @PostMapping("/requestUpdateCardData")
    public RequestCardDataUpdateRespDTO requestUpdateCardData(@RequestBody RequestCardDataUpdateReqDTO request) {
        log.info("IF5A-03 请求票卡更新，请求参数：{}", JSON.toJSONString(request));
        return supplementService.requestCardDataUpdate(request);
    }
}

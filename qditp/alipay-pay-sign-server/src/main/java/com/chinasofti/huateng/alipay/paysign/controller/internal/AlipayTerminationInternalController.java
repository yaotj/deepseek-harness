package com.chinasofti.huateng.alipay.paysign.controller.internal;

import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationInternalService;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationService;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行解约域的**内部接口控制器**：批处理销卡与单笔执行解约都在本类。
 *
 * <p><b>2026-09-18 迁入 {@code executeTermination}</b>（原 {@code GET /channel/executeTermination}，
 * 宿主是 {@code controller/sign/AlipayContractController}）。迁移理由：该端点只有内部调用方
 * （{@code rpc/AlipayPaySignClient.executeTermination} ← fep-alipay {@code AlipayPaymentServiceImpl}，
 * 真实入口是管理台），与批处理 {@code /process} 走的是**同一套销卡语义**（同一个
 * {@code TerminationNotifier}，只是「单笔手工补执行 vs 批量排空」），因此并进本类而不另建 controller。
 *
 * <p><b>同日本类由 {@code controller} 根包迁入 {@code controller.internal} 子包</b>：
 * 只改 Java 包声明，**类名、Bean 名、类级 `@RequestMapping` 前缀与两条 URL 全都没动**，
 * 因此这一步**零契约影响、不需要同批滚更任何上游**。组件扫描根是启动类所在的
 * {@code ...alipay.paysign}，子包天然被扫到，**NEVER 因为迁了包就去加 `@ComponentScan`**。
 *
 * <p><b>URL 迁移那一步的旧路径已直接删除、没有留别名</b>（用户裁决 hard_switch）。因此当时
 * {@code alipay-pay-sign-server} 与 {@code fep-alipay} 两个镜像 <b>MUST 同批滚更</b> ——
 * 任一侧先上，另一侧打的就是已不存在的路径，而本项目的 404 会被伪装成
 * HTTP 200 + UUID retCode、静默不报（`closeResultForAlipay` 已踩过一次，见 ADR-D137）。
 *
 * <p><b>HTTP 形态刻意保持 GET + {@code @RequestParam} 不变</b>：改成 POST 会连带改 rpc 侧的调用形态，
 * 超出「迁 internal」这一步的范围。「GET 做状态变更」是既有现状，要改是单独一步。
 *
 * <p><b>迁到 internal 不带来任何鉴权收益</b>：本模块无拦截器、无验签，四个 {@code /internal/**}
 * controller 全部裸暴露。{@code executeTermination} 是状态变更型接口、按 AGENTS.md §5.2 应当有鉴权，
 * <b>迁移前后都不满足</b>，属已知缺口 —— 迁包与迁 URL 都只是结构收敛，不承担安全语义。
 */
@RestController
@RequestMapping("/internal/alipay/termination")
public class AlipayTerminationInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTerminationInternalController.class);

    private final AlipayTerminationInternalService alipayTerminationInternalService;
    private final AlipayTerminationService alipayTerminationService;

    public AlipayTerminationInternalController(AlipayTerminationInternalService alipayTerminationInternalService,
                                               AlipayTerminationService alipayTerminationService) {
        this.alipayTerminationInternalService = alipayTerminationInternalService;
        this.alipayTerminationService = alipayTerminationService;
    }

    /**
     * 支付宝出行销卡批处理：扫一批 PENDING 的销卡登记逐条执行。
     */
    @PostMapping("/process")
    public AlipayProcessTerminationRespDTO processTermination(
            @RequestBody(required = false) AlipayProcessTerminationReqDTO request) {
        log.info("收到支付宝出行销卡批处理请求, request={}", request);
        return alipayTerminationInternalService.processTermination(request);
    }

    /**
     * 支付宝出行-执行解约（单笔手工补执行）。URL 由 {@code /channel/executeTermination} 迁至此处，
     * 服务实现、入参与应答字段一字未改。
     */
    @GetMapping("/execute")
    public AlipayCommonResponse executeTermination(@RequestParam String agreementCode) {
        log.info("收到执行解约请求: agreementCode={}", agreementCode);
        AlipayCommonResponse response = alipayTerminationService.executeTermination(agreementCode);
        log.info("执行解约响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}

package com.chinasofti.huateng.alipay.paysign.controller.sign;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipaySignContractService;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝签约域 Controller：承载当前有真实调用方的两条 {@code /channel} 链路，**已全部切到新服务**。
 *
 * <p>两条端点的调用方（都经 {@code rpc/AlipayPaySignClient}）：
 * <ul>
 *   <li>{@code POST /channel/addContract} ← fep-alipay-server {@code AlipayContractServiceImpl.addContract}</li>
 *   <li>{@code POST /channel/terminateContract} ← fep-alipay-server {@code AlipayContractServiceImpl.terminateContract}</li>
 * </ul>
 *
 * <p><b>2026-09-18 按 addContract → terminateContract → executeTermination 的顺序逐条切换完毕</b>：
 * 本类不再注旧门面 {@code AlipayContractService}，改注两个按聚合拆开的新接口 ——
 * 签约走 {@link AlipaySignContractService}（实现 {@code service/impl/sign/}，新抽象 {@code SignCommand} /
 * sealed {@code SignOutcome} / {@code SignRepository} / {@code SignLogWriter}），
 * 解约走 {@link AlipayTerminationService}（实现 {@code service/impl/termination/TerminationCoordinator}）。
 * <b>URL、入向 DTO、应答字段一字未改</b>，切的只是 handler 背后的服务。
 *
 * <p><b>{@code executeTermination} 已于同日迁出本类</b>（迁移第 7 条）：它只有内部调用方，现宿主是
 * {@code controller/internal/AlipayTerminationInternalController}，URL 由 {@code GET /channel/executeTermination}
 * 改为 {@code GET /internal/alipay/termination/execute}（**GET + {@code @RequestParam} 形态刻意未变**），
 * 旧路径**已删除、无别名**。<b>NEVER 在本类把它加回来</b> ——
 * 与旧 URL 并存等于让「调用方打的是哪条」无法判断，而 hard_switch 的前提就是同批滚更 {@code fep-alipay}。
 *
 * <p>迁移期的两条口径 **NEVER 改**：
 * <ul>
 *   <li>旧的 {@code AlipayContractService} + {@code impl/contract/AlipayContractServiceImpl} **原样留着**，
 *       现在它的 {@code addContract} / {@code terminateContract} / {@code executeTermination} 已成零调用方，
 *       只剩 {@code selectSignInfo} 还被 {@code controller/legacy/AlipayPaySignController} 用着
 *       （该类 2026-09-20 迁入 {@code controller.legacy}，URL 逐字未变）——
 *       **回滚某一条只需把对应方法改回注旧接口**，这就是保留它的目的；
 *   <li>新旧两侧**互不调用**：NEVER 让旧实现转发到新服务、也 NEVER 反过来，否则「哪一侧在跑」无法判断。
 * </ul>
 *
 * <p>包位置是 {@code controller.sign}，组件扫描根是启动类所在的 {@code ...alipay.paysign}，
 * 子包天然被扫到，**NEVER 因为迁了包就去加 `@ComponentScan`**。
 */
@RestController
@RequestMapping("/channel")
public class AlipayContractController {

    private static final Logger log = LoggerFactory.getLogger(AlipayContractController.class);

    private final AlipaySignContractService alipaySignContractService;
    private final AlipayTerminationService alipayTerminationService;

    public AlipayContractController(AlipaySignContractService alipaySignContractService,
                                    AlipayTerminationService alipayTerminationService) {
        this.alipaySignContractService = alipaySignContractService;
        this.alipayTerminationService = alipayTerminationService;
    }

    /**
     * 添加签约信息 —— 迁移第 1 条，已切至 {@link AlipaySignContractService#addContract}。
     */
    @PostMapping("/addContract")
    public AlipayTripAddContractRespDTO addContract(@RequestBody AlipayTripAddContractReqDTO request) {
        log.info("收到签约请求: thirdUserId={}", request != null ? request.getThirdUserId() : null);
        AlipayTripAddContractRespDTO response = alipaySignContractService.addContract(request);
        log.info("添加签约信息响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }

    /**
     * 解约登记 —— 迁移第 2 条，已切至 {@link AlipayTerminationService#terminateContract}。
     */
    @PostMapping("/terminateContract")
    public AlipayTripTerminateContractRespDTO terminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        log.info("收到解约登记请求: agreementCode={}", request != null ? request.getAgreementCode() : null);
        AlipayTripTerminateContractRespDTO response = alipayTerminationService.terminateContract(request);
        log.info("解约登记响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}

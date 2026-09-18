package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.controller.sign.AlipayContractController;
import com.chinasofti.huateng.alipay.paysign.service.AlipayContractService;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝签约域的**存量端点留存类**：只放当前没有任何调用方、但按裁决暂不删除的签约端点。
 *
 * <p>在用的两条 {@code /channel} 签约链路（{@code addContract} / {@code terminateContract}）
 * 已迁至 {@link AlipayContractController}（**该类自 2026-09-18 起在 `controller.sign` 子包**），
 * **URL 逐字未变**（两个类共用 {@code /channel} 前缀）。
 * 第三条 {@code executeTermination} 同日迁到了 {@code /internal/alipay/termination/execute}
 * （宿主 {@code AlipayTerminationInternalController}），**旧 `/channel` 路径已删除、无别名**。
 *
 * <p>本类现存 {@code selectSignInfo} 的现状（2026-09-18 全仓溯源实测）：
 * {@code rpc/AlipayPaySignClient.selectSignInfo} 里**有**包装方法，但**该方法全仓零引用**，
 * 因此这条链路没有任何真实调用方，前端 {@code web/src} 也从未接线。
 * 按裁决**先保留不删**，删除时 **MUST 连 `rpc` 里那个包装方法一起删** ——
 * 只删端点会留下「Client 在打、服务端没 handler」的 404 陷阱，
 * 而本项目的 404 会被伪装成 HTTP 200 + UUID retCode、静默不报（`closeResultForAlipay` 已踩过一次，见 ADR-D137）。
 *
 * <p>**NEVER 往本类新增端点**：新增签约域端点一律进 {@link AlipayContractController}。
 */
@RestController
@RequestMapping("/channel")
public class AlipayPaySignController {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaySignController.class);
    private final AlipayContractService alipayContractService;

    public AlipayPaySignController(AlipayContractService alipayContractService) {
        this.alipayContractService = alipayContractService;
    }

    /**
     * 查询用户签约信息。**当前零调用方**（`AlipayPaySignClient.selectSignInfo` 存在但无人调用）。
     */
    @GetMapping("/selectSignInfo")
    public AlipaySignInfoDTO selectSignInfo(@RequestParam String thirdUserId) {
        log.info("查询签约信息: thirdUserId={}", thirdUserId);
        AlipaySignInfoDTO response = alipayContractService.selectSignInfo(thirdUserId);
        log.info("查询签约信息响应结果：{}", response != null ? response.getThirdUserId() : "null");
        return response;
    }
}

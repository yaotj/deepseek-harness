package com.chinasofti.huateng.alipay.paysign.controller.legacy;

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
 * <p><b>2026-09-20 迁入 {@code controller.legacy} 子包</b>（迁移第 10 条）：
 * 本类的定位是「等着被删的历史残留」，与 {@code controller.sign} / {@code controller.payment} /
 * {@code controller.internal} 那些在跑的端点放在同一层容易被当成同级能力。
 * <b>URL 逐字未变</b>（类级前缀仍是 {@code /channel}，方法仍是 {@code GET /selectSignInfo}）——
 * Spring 的映射只看注解、与包路径无关，所以 {@code rpc/AlipayPaySignClient} 里那个包装方法不需要改一行。
 * 组件扫描根是启动类所在的 {@code ...alipay.paysign}，子包天然被扫到，
 * <b>NEVER 因为迁了包就去加 {@code @ComponentScan}</b>。
 *
 * <p>在用的两条 {@code /channel} 签约链路（{@code addContract} / {@code terminateContract}）
 * 已迁至 {@link AlipayContractController}（**该类自 2026-09-18 起在 `controller.sign` 子包**），
 * **URL 逐字未变**（两个类共用 {@code /channel} 前缀）。
 * 第三条 {@code executeTermination} 同日迁到了 {@code /internal/alipay/termination/execute}
 * （宿主 {@code AlipayTerminationInternalController}），**旧 `/channel` 路径已删除、无别名**。
 *
 * <p>本类现存 {@code selectSignInfo} 的现状（2026-09-20 全仓溯源复核，与 2026-09-18 结论一致）：
 * {@code rpc/AlipayPaySignClient.selectSignInfo} 里**有**包装方法，但**该方法全仓零引用**，
 * 因此这条链路没有任何真实调用方，前端 {@code web/src} 也从未接线。
 * 按裁决**先保留不删**，删除时 **MUST 连 `rpc` 里那个包装方法一起删** ——
 * 只删端点会留下「Client 在打、服务端没 handler」的 404 陷阱，
 * 而本项目的 404 会被伪装成 HTTP 200 + UUID retCode、静默不报（`closeResultForAlipay` 已踩过一次，见 ADR-D137）。
 *
 * <p><b>NEVER 往本包新增端点</b>：新增签约域端点一律进 {@link AlipayContractController}。
 * 本包收的是**历史形态留存类**，现有四类、<b>判据各不相同、NEVER 混为一谈</b>：
 * ①本类 —— 已确认零调用方、**等待删除**（删时连 rpc 包装方法一起删）；
 * ②{@link AlipayNotifyController}（2026-09-20 迁入）—— <b>两条端点都有真实在跑的调用方</b>，
 * 只因「类级无前缀 + 方法级全路径」这个历史形态才迁入，<b>NEVER 当成死代码删掉</b>；
 * ③{@link AlipayPayLogController}（2026-09-20 迁入）—— 8 个 handler 零调用方，
 * <b>但不能删</b>：它是旧表 {@code ALIPAY_PAY_LOG}（已无写入方、计划废弃）存量数据的**唯一读出口**；
 * ④{@link AlipayTripPaymentController}（2026-09-20 迁入，迁移第 13 条）—— {@code findTravelDetail}
 * 零调用方（活链路在 trans-query-server）+ <b>三段回滚位注释块</b>（requestPay / payQuery / requestRefund），
 * <b>删它前 MUST 先确认那三段回滚位不再需要</b>，且那三段 MUST 保持注释状态。
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

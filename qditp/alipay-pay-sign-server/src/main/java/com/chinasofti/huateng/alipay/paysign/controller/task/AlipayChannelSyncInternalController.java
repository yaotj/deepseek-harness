package com.chinasofti.huateng.alipay.paysign.controller.task;

import com.chinasofti.huateng.alipay.paysign.service.impl.channelsync.ChannelSyncCompensationService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行「支付通道同步」补偿内部接口（ADR-D132）。
 *
 * <p><b>2026-09-20 迁入 {@code controller.task} 子包</b>（迁移第 14 条）：<b>URL、Bean 名、类名逐字未动</b>
 * （类级前缀仍是 {@code /internal/alipay/channelSync}，方法仍是 {@code POST /compensate}）——
 * Spring 的映射只看注解、与包路径无关，因此 {@code rpc/AlipayPaySignClient.compensateChannelSync}
 * 与 web-admin 的任务 Bean 都不需要改一行。组件扫描根是启动类所在的 {@code ...alipay.paysign}，
 * 子包天然被扫到，<b>NEVER 因为迁了包就去加 {@code @ComponentScan}</b>。
 *
 * <p><b>本包（{@code controller.task}）的唯一判据：端点的直接调用方只有 web-admin 的 Quartz，
 * 没有任何服务间调用方。</b>与其它两个子包的区别 MUST 分清、<b>NEVER 混为一谈</b>：
 * ①{@code controller.internal} 收的是**服务间**调用的 {@code /internal/**}
 * （如 {@code AlipayArrearsInternalController} ← blacklist-server、
 * {@code AlipayPaymentInternalController} ← fep-alipay）；
 * ②{@code AlipayTerminationInternalController} 是**混合**形态（{@code process} 由 Quartz 打、
 * {@code execute} 是管理台人工经 fep-alipay），因此**刻意留在 {@code controller.internal}**，
 * <b>NEVER 因为它也有 Quartz 调用方就搬进本包</b>；
 * ③{@code controller.legacy} 收历史形态留存类，判据另有四条（详见该包 {@code AlipayPaySignController} 注释）。
 *
 * <p>由 web-admin 的 Quartz 打进来，本模块自身没有 `@Scheduled`（AGENTS.md §2.2.1）。
 * <b>驱动源已落地</b>：`sys_job` **315「支付宝支付通道同步补偿」cron `0 0/5 * * * ?`**（2026-09-21 由 124 改号为 315）
 * （任务 Bean `AlipayChannelSyncQuartzTask.compensate()`，迁移脚本
 * `web-server/web-quartz/src/main/resources/sql/web-quartz-alipay-channel-sync-job-migration.sql`）。
 * 本注释此前写「上线前 MUST 在 web-admin 建对应 `sys_job`」已过期、**NEVER 回退**；
 * 但 <b>「代码写了」仍不等于「补偿在跑」——判据是 `SYS_JOB_LOG` 有没有记录，MUST 现查、NEVER 引用本行</b>。
 * 停用那条 job 等于签约首推不可达的行永久停在非 `SUCCESS`、账户域缺一条支付通道且对上游不可见。
 *
 * <p><b>本端点当前无鉴权</b>，与 §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」冲突。
 * 这与同模块另两个 `/internal/**` 端点是同一现状（`/internal/alipay/termination`、
 * `/internal/alipayPay`），属**测试期的既有降级**、不是本次新引入的口子；
 * 上线前 MUST 与那两个一并补上，**NEVER 在这里自造一套签名**。</p>
 */
@RestController
@RequestMapping("/internal/alipay/channelSync")
public class AlipayChannelSyncInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayChannelSyncInternalController.class);

    private final ChannelSyncCompensationService channelSyncCompensationService;

    public AlipayChannelSyncInternalController(ChannelSyncCompensationService channelSyncCompensationService) {
        this.channelSyncCompensationService = channelSyncCompensationService;
    }

    /**
     * 扫一批未收口的支付通道同步并逐条重推。
     *
     * <p>无入参：批量大小与重试上限都是配置项，**NEVER 改成让调用方传** ——
     * 那等于把「一次扫多少」的决定权交给 Quartz 配置页面，容易被误设成全表。</p>
     */
    @PostMapping("/compensate")
    public AlipayCommonResponse compensate() {
        log.info("收到支付通道同步补偿请求");
        int succeeded = channelSyncCompensationService.compensate();
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("本轮收口 " + succeeded + " 条");
        return response;
    }
}

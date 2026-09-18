package com.chinasofti.huateng.alipay.paysign.controller;

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
 * <p>由 web-admin 的 Quartz 打进来，本模块自身没有 `@Scheduled`（AGENTS.md §2.2.1）。
 * 上线前 MUST 在 web-admin 建对应 `sys_job`，否则这个端点没有任何驱动源、
 * 补偿等于没落地 —— <b>「代码写了」不等于「补偿在跑」，判据是 `SYS_JOB_LOG` 有没有记录。</b></p>
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

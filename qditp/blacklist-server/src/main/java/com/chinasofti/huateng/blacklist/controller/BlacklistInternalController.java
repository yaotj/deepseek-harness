package com.chinasofti.huateng.blacklist.controller;

import com.chinasofti.huateng.blacklist.service.BlacklistReleaseInspectService;
import com.chinasofti.huateng.blacklist.service.impl.BlacklistChannelSyncService;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 黑名单内部接口。只读盘点 + 渠道同步补偿，全部由 web-admin 的 Quartz 任务驱动。
 *
 * <p><b>本前缀下的接口目前都没有验签</b>，与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」冲突。
 * 这是沿用本模块与 recon 那批内部端点的现状（开发测试期的有意降级），<b>上生产前 MUST 补验签</b>。
 * 两个补偿端点只推进渠道同步状态、不改黑名单本体，误调的后果是向支付宝多推一次已成功的通知。
 */
@RestController
@RequestMapping("/internal/blacklist")
public class BlacklistInternalController {

    private static final Logger log = LoggerFactory.getLogger(BlacklistInternalController.class);

    /** 单轮扫描上限的缺省值，够覆盖积压又不至于让单次调用跑太久。 */
    private static final int DEFAULT_LIMIT = 200;

    private final BlacklistReleaseInspectService blacklistReleaseInspectService;
    private final BlacklistChannelSyncService blacklistChannelSyncService;

    public BlacklistInternalController(BlacklistReleaseInspectService blacklistReleaseInspectService,
                                       BlacklistChannelSyncService blacklistChannelSyncService) {
        this.blacklistReleaseInspectService = blacklistReleaseInspectService;
        this.blacklistChannelSyncService = blacklistChannelSyncService;
    }

    /**
     * 盘点黑名单记录的欠费结清情况，供 web-server 的 Quartz 任务调用。
     *
     * <p>只读，NEVER 删除任何黑名单记录。</p>
     */
    @PostMapping("/inspectReleasable")
    public BlacklistReleaseInspectRespDTO inspectReleasable() {
        log.info("接收到黑名单可解除性盘点请求");
        BlacklistReleaseInspectRespDTO response = blacklistReleaseInspectService.inspect();
        log.info("黑名单可解除性盘点响应, scanned={}, settled={}, unsettled={}, unknown={}",
                response.getScanned(), response.getSettled(), response.getUnsettled(), response.getUnknown());
        return response;
    }

    /**
     * 补推加黑方向的渠道同步（`BLACKLIST.CHANNEL_SYNC_*` 里 PENDING / FAILED 的行）。
     *
     * <p>由 web-admin 的 Quartz 任务定时调用，是加黑通知的<b>唯一兜底</b> ——
     * afterCommit 那条快速路径是进程内非持久的，JVM 崩溃即丢。
     * <b>NEVER 在 blacklist-server 内部加 `@Scheduled` 替代它。</b>
     */
    @PostMapping("/channel-sync/compensate-add")
    public OutboxScan.Result compensateAdd(@RequestParam(required = false) Integer limit) {
        int effective = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        log.info("接收到加黑通知补偿请求, limit={}", effective);
        return blacklistChannelSyncService.compensateAdd(effective);
    }

    /**
     * 补推解黑方向的渠道同步（`BLACKLIST_RELEASED.CHANNEL_SYNC_*` 里 PENDING / FAILED 的行）。
     *
     * <p>载体在解除历史表、不在主表：解黑那一刻主表行已被删除。其余同上。
     */
    @PostMapping("/channel-sync/compensate-release")
    public OutboxScan.Result compensateRelease(@RequestParam(required = false) Integer limit) {
        int effective = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        log.info("接收到解黑通知补偿请求, limit={}", effective);
        return blacklistChannelSyncService.compensateRelease(effective);
    }
}

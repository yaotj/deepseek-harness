package com.chinasofti.huateng.blacklist.controller;

import com.chinasofti.huateng.blacklist.service.BlacklistAutoReleaseService;
import com.chinasofti.huateng.blacklist.service.BlacklistReleaseInspectService;
import com.chinasofti.huateng.blacklist.service.HceBlacklistFileService;
import com.chinasofti.huateng.blacklist.service.impl.BlacklistChannelSyncService;
import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.model.app.HceBlacklistFileGenRespDTO;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/blacklist")
public class BlacklistInternalController {

    private static final Logger log = LoggerFactory.getLogger(BlacklistInternalController.class);

    private static final int DEFAULT_LIMIT = 200;

    private final BlacklistReleaseInspectService blacklistReleaseInspectService;
    private final BlacklistChannelSyncService blacklistChannelSyncService;
    private final BlacklistAutoReleaseService blacklistAutoReleaseService;
    private final HceBlacklistFileService hceBlacklistFileService;

    public BlacklistInternalController(BlacklistReleaseInspectService blacklistReleaseInspectService,
                                       BlacklistChannelSyncService blacklistChannelSyncService,
                                       BlacklistAutoReleaseService blacklistAutoReleaseService,
                                       HceBlacklistFileService hceBlacklistFileService) {
        this.blacklistReleaseInspectService = blacklistReleaseInspectService;
        this.blacklistChannelSyncService = blacklistChannelSyncService;
        this.blacklistAutoReleaseService = blacklistAutoReleaseService;
        this.hceBlacklistFileService = hceBlacklistFileService;
    }


    @PostMapping("/auto-release")
    public BlacklistAutoReleaseRespDTO autoRelease(@RequestParam(required = false) Integer limit) {
        log.info("接收到黑名单自动解除请求, limit={}", limit);
        BlacklistAutoReleaseRespDTO response = blacklistAutoReleaseService.autoRelease(limit);
        log.info("黑名单自动解除响应, scanned={}, released={}, unsettled={}, unknown={}, skipped={}, failed={}",
                response.getScanned(), response.getReleased(), response.getUnsettled(),
                response.getUnknown(), response.getSkipped(), response.getFailed());
        return response;
    }


    @PostMapping("/inspectReleasable")
    public BlacklistReleaseInspectRespDTO inspectReleasable() {
        log.info("接收到黑名单可解除性盘点请求");
        BlacklistReleaseInspectRespDTO response = blacklistReleaseInspectService.inspect();
        log.info("黑名单可解除性盘点响应, scanned={}, settled={}, unsettled={}, unknown={}",
                response.getScanned(), response.getSettled(), response.getUnsettled(), response.getUnknown());
        return response;
    }


    @PostMapping("/channel-sync/compensate-add")
    public OutboxScan.Result compensateAdd(@RequestParam(required = false) Integer limit) {
        int effective = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        log.info("接收到加黑通知补偿请求, limit={}", effective);
        return blacklistChannelSyncService.compensateAdd(effective);
    }


    @PostMapping("/channel-sync/compensate-release")
    public OutboxScan.Result compensateRelease(@RequestParam(required = false) Integer limit) {
        int effective = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        log.info("接收到解黑通知补偿请求, limit={}", effective);
        return blacklistChannelSyncService.compensateRelease(effective);
    }


    @PostMapping("/hce-file/generate")
    public HceBlacklistFileGenRespDTO generateHceFile() {
        log.info("接收到HCE黑名单文件生成上传请求");
        HceBlacklistFileGenRespDTO response = hceBlacklistFileService.generateAndUpload();
        log.info("HCE黑名单文件生成上传响应, resultCode={}, fileName={}, version={}, cardCount={}, uploaded={}, message={}",
                response.getResultCode(), response.getFileName(), response.getVersion(),
                response.getCardCount(), response.isUploaded(), response.getMessage());
        return response;
    }
}

package com.chinasofti.huateng.blacklist.controller;

import com.chinasofti.huateng.blacklist.service.BlacklistReleaseInspectService;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 黑名单内部接口。当前只有只读盘点接口，不改数据。
 *
 * <p>本前缀下新增写接口 MUST 先补验签。</p>
 */
@RestController
@RequestMapping("/internal/blacklist")
public class BlacklistInternalController {

    private static final Logger log = LoggerFactory.getLogger(BlacklistInternalController.class);

    private final BlacklistReleaseInspectService blacklistReleaseInspectService;

    public BlacklistInternalController(BlacklistReleaseInspectService blacklistReleaseInspectService) {
        this.blacklistReleaseInspectService = blacklistReleaseInspectService;
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
}

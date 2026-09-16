package com.chinasofti.huateng.blacklist.controller;

import com.chinasofti.huateng.blacklist.service.BlacklistReleaseInspectService;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 黑名单内部接口。
 *
 * <p>当前只有一个只读盘点接口，不改任何数据，因此无需鉴权与归属校验。
 * 后续若在本前缀下新增写接口（例如真正执行自动解除），MUST 先补验签
 * （对齐 {@code AccountRequestVerifier} / {@code ItpRequestSignVerifier}，NEVER 自造签名逻辑）。</p>
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
     * <p><b>只读，NEVER 删除任何黑名单记录。</b>无入参：批量范围由服务端的
     * {@code blacklist.inspect.batch-size} 控制，避免调用方能通过参数放大单次开销。</p>
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

package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.HceBlacklistFileGenRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component("hceBlacklistFileQuartzTask")
public class HceBlacklistFileQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(HceBlacklistFileQuartzTask.class);

    private final BlacklistClient blacklistClient;

    public HceBlacklistFileQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    public void generateAndUpload() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            HceBlacklistFileGenRespDTO result = blacklistClient.generateHceBlacklistFile(
                    QuartzTraceUtils.traceHeaders(traceId));
            if (result == null) {
                throw new IllegalStateException("HCE黑名单文件生成上传接口未返回响应");
            }
            if (!"0000".equals(result.getResultCode()) || !result.isUploaded()) {
                throw new IllegalStateException("HCE黑名单文件生成上传未成功, resultCode=" + result.getResultCode()
                        + ", fileName=" + result.getFileName() + ", version=" + result.getVersion()
                        + ", cardCount=" + result.getCardCount() + ", message=" + result.getMessage());
            }
            log.info("HCE黑名单文件生成上传完成, fileName={}, version={}, cardCount={}",
                    result.getFileName(), result.getVersion(), result.getCardCount());
        });
    }
}

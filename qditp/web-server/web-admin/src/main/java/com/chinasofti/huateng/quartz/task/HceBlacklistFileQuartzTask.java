package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.HceBlacklistFileGenRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HCE 卡黑名单文件生成上传任务。
 *
 * <p>触发 blacklist-server {@code POST /internal/blacklist/hce-file/generate}：
 * 查 HCE 票种（0442/0443）的黑名单生效行 → 分配版本号 → 生成 PRM.0013.9700.&lt;版本&gt;
 * 二进制文件 → 上传 ACC FTP。全量覆盖语义，版本号只进不退。
 *
 * <p>该模块<b>刻意没有 {@code @Scheduled}</b>（调度统一由 web-admin 的 {@code sys_job} 驱动，
 * 便于前台改频率、留 {@code SYS_JOB_LOG}、单副本可控）。
 */
@Component("hceBlacklistFileQuartzTask")
public class HceBlacklistFileQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(HceBlacklistFileQuartzTask.class);

    private final BlacklistClient blacklistClient;

    public HceBlacklistFileQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    /** 生成并上传 HCE 黑名单文件。前台调用目标：hceBlacklistFileQuartzTask.generateAndUpload()。 */
    public void generateAndUpload() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            HceBlacklistFileGenRespDTO result = blacklistClient.generateHceBlacklistFile(
                    QuartzTraceUtils.traceHeaders(traceId));
            // 响应为 null MUST 抛异常：静默返回会让调度日志记成成功，「任务根本没跑通」看起来一切正常。
            if (result == null) {
                throw new IllegalStateException("HCE黑名单文件生成上传接口未返回响应");
            }
            if (!"0000".equals(result.getResultCode()) || !result.isUploaded()) {
                // 生成失败 / FTP 未配置 / 上传失败：抛异常让 SYS_JOB_LOG 记成失败，值班才会看到。
                // 文件版本号已消耗、本地文件已落盘，人工核对后可补传或等下一轮全量覆盖。
                throw new IllegalStateException("HCE黑名单文件生成上传未成功, resultCode=" + result.getResultCode()
                        + ", fileName=" + result.getFileName() + ", version=" + result.getVersion()
                        + ", cardCount=" + result.getCardCount() + ", message=" + result.getMessage());
            }
            log.info("HCE黑名单文件生成上传完成, fileName={}, version={}, cardCount={}",
                    result.getFileName(), result.getVersion(), result.getCardCount());
        });
    }
}

package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方支付对账（TXNDIFF）配置。
 *
 * <p>本配置项仅控制 ITP 侧比对与文件生成的本地行为；外部契约（账单投递方式、渠道枚举映射值、
 * 扣款失败原因取值）仍见 {@code docs/business/recon-thirdparty.md} §五，未确认项一律走文档里的默认假设。</p>
 */
@ConfigurationProperties(prefix = "recon.thirdparty")
public class ThirdPartyReconProperties {

    /** 总开关；关闭则不注册定时任务、内部端点直接拒绝。 */
    private boolean enabled = false;

    /** 渠道账单 csv 的本地读取目录（账单投递方式=本地放置时的默认路径；FTP 拉取为待确认扩展点）。 */
    private String billLocalDir = "/home/javaapp/app/recon/thirdparty-bill";

    /** 账单文件名匹配模式（glob）。需求文件名为 reconciliation-bill-date.csv。 */
    private String billFilePattern = "reconciliation-bill-*.csv";

    /** TXNDIFF 生成文件的本地输出目录。 */
    private String outputDir = "/home/javaapp/app/recon/txndiff";

    /** 定时任务是否启用（与 web-admin sys_job 二选一，避免双跑）。 */
    private boolean schedulerEnabled = false;

    /** 定时任务 cron；默认每日 03:30，晚于日终批(sys_job 225)。 */
    private String schedulerCron = "0 30 3 * * ?";

    /** 向 face-pay 拉取 F2F_TXN 分片的等待超时（毫秒），默认 5 分钟。 */
    private long facePayFetchTimeoutMillis = 300000L;

    /** 渠道枚举归一化映射：key=账单 payment_channel|payment_vendor，value=ITP 侧支付通道编码；缺省按原值透传。 */
    private Map<String, String> channelMapping = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBillLocalDir() { return billLocalDir; }
    public void setBillLocalDir(String billLocalDir) { this.billLocalDir = billLocalDir; }
    public String getBillFilePattern() { return billFilePattern; }
    public void setBillFilePattern(String billFilePattern) { this.billFilePattern = billFilePattern; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }
    public String getSchedulerCron() { return schedulerCron; }
    public void setSchedulerCron(String schedulerCron) { this.schedulerCron = schedulerCron; }

    public long getFacePayFetchTimeoutMillis() { return facePayFetchTimeoutMillis; }
    public void setFacePayFetchTimeoutMillis(long facePayFetchTimeoutMillis) { this.facePayFetchTimeoutMillis = facePayFetchTimeoutMillis; }
    public Map<String, String> getChannelMapping() { return channelMapping; }
    public void setChannelMapping(Map<String, String> channelMapping) { this.channelMapping = channelMapping == null ? new HashMap<>() : channelMapping; }
}

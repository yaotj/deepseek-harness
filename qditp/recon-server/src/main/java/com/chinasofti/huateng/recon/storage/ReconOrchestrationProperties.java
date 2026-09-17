package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 日终对账编排配置：一次整批运行的节奏、账期窗口推算规则与各源的期望清单。
 */
@ConfigurationProperties(prefix = "recon.orchestration")
public class ReconOrchestrationProperties {

    /** 编排总开关。关掉后 {@code runDailyBatch} 与 {@code advance} 都直接返回，单批次人工接口仍可用。 */
    private boolean enabled = true;

    /** 一次整批运行内，两轮推进之间的等待毫秒数。 */
    private long advanceDelayMillis = 60000L;

    /**
     * 一次整批运行的总超时毫秒数，默认 4 分钟。
     *
     * <p>护栏：MUST 小于 {@code ReconClient} 的响应超时（5 分钟），否则会变成
     * 「客户端先超时报错、服务端还在跑」，两处要放宽 MUST 一起改。</p>
     */
    private long runTimeoutMillis = 240000L;

    /** 账期偏移天数：{@code businessDate = 今天 - windowOffsetDays}，默认 2 即 T-2 日。 */
    private int windowOffsetDays = 2;

    /** 窗口的日切时刻 {@code HHmmss}，默认 02:00:00，窗口为 [businessDate 日切, +1 天 日切)。 */
    private String windowStartTime = "020000";

    /** 同一 {@code (来源, 文件类型)} 允许的最大重下发次数。 */
    private int maxRetry = 3;

    /** 期望清单。为空时 dispatch 无事可做，属配置缺失，MUST 在部署清单里核对。 */
    private List<SourceExpectation> sources = new ArrayList<>();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getAdvanceDelayMillis() { return advanceDelayMillis; }

    public void setAdvanceDelayMillis(long advanceDelayMillis) { this.advanceDelayMillis = advanceDelayMillis; }

    public long getRunTimeoutMillis() { return runTimeoutMillis; }

    public void setRunTimeoutMillis(long runTimeoutMillis) { this.runTimeoutMillis = runTimeoutMillis; }

    public int getWindowOffsetDays() { return windowOffsetDays; }

    public void setWindowOffsetDays(int windowOffsetDays) { this.windowOffsetDays = windowOffsetDays; }

    public String getWindowStartTime() { return windowStartTime; }

    public void setWindowStartTime(String windowStartTime) { this.windowStartTime = windowStartTime; }

    public int getMaxRetry() { return maxRetry; }

    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }

    public List<SourceExpectation> getSources() { return sources; }

    public void setSources(List<SourceExpectation> sources) {
        this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
    }

    /**
     * 单个源服务的期望：名称、基础地址与需要它产出的文件类型清单。
     */
    public static class SourceExpectation {

        /** 来源标识，落库到 {@code RECON_BATCH_SOURCE.SOURCE_NAME}，MUST 匹配 {@code [A-Za-z0-9_-]{1,32}}。 */
        private String name;

        /** 源服务基础地址，形如 {@code http://gate-txn-pay-server-jomf4-svc.itp.svc:30019}（Service 端口）。 */
        private String url;

        /** 该源需要产出的文件类型名，如 {@code [DETAIL, PAY]}。 */
        private List<String> fileTypes = new ArrayList<>();

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }

        public void setUrl(String url) { this.url = url; }

        public List<String> getFileTypes() { return fileTypes; }

        public void setFileTypes(List<String> fileTypes) {
            this.fileTypes = fileTypes == null ? new ArrayList<>() : new ArrayList<>(fileTypes);
        }
    }
}

package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 日终对账编排配置：一次整批运行的节奏、账期窗口推算规则与各源的期望清单。
 *
 * <p>期望清单是收齐判定的源头：{@code sources[].fileTypes} 决定了
 * {@code RECON_BATCH_SOURCE} 会登记哪些 {@code (来源, 文件类型)} 行，
 * 进而决定「全部 COMPLETED」的判据与最终要产出哪几个文件。
 * 因此新增 / 下线一个源 MUST 改这里，NEVER 在代码里硬编码来源名。</p>
 *
 * <p><b>本类不再有 cron 配置</b>：触发时机由 web-admin 的 {@code sys_job} 决定
 * （见 {@link com.chinasofti.huateng.ReconServer} 的类注释）。原 {@code dispatch-cron}
 * 已删除，**NEVER 加回**，也 NEVER 在 K8s 注入 {@code RECON_DISPATCH_CRON}——
 * 那个键现在没有任何读取方，留着只会让运维误以为改它能改调度频率。</p>
 */
@ConfigurationProperties(prefix = "recon.orchestration")
public class ReconOrchestrationProperties {

    /** 编排总开关。关掉后 {@code runDailyBatch} 与 {@code advance} 都直接返回，单批次人工接口仍可用。 */
    private boolean enabled = true;

    /**
     * 一次整批运行内，两轮推进之间的等待毫秒数。
     *
     * <p>源服务的抽取是异步的（受理后才开始跑），所以 dispatch 之后必须轮询等收齐。
     * 沿用原 {@code advance-delay-millis} 键名与默认值，语义从「定时扫描间隔」变为
     * 「同一次运行内的轮询间隔」。</p>
     */
    private long advanceDelayMillis = 60000L;

    /**
     * 一次整批运行的总超时毫秒数，默认 4 分钟。
     *
     * <p>超时即抛异常，让 web-admin 的 {@code sys_job_log} 记失败并告警；批次自身留在
     * 非终态，下一次运行会从 FAILED / PARTIAL 重入（状态机白名单允许），**不会丢数据**。
     * 实测一整轮（4 个文件、6 行 PAY）约 60 秒。</p>
     *
     * <p><b>MUST 小于 {@code ReconClient.getResponseTimeout()} 的 5 分钟</b>：web-admin 是同步等
     * {@code POST /internal/recon/daily/run} 的响应，本值若超过客户端超时，会变成「客户端先超时报错、
     * 服务端还在跑」，`sys_job_log` 记的失败原因也就没有意义了。要放宽 MUST 两处一起改。</p>
     */
    private long runTimeoutMillis = 240000L;

    /**
     * 账期偏移天数：{@code businessDate = 今天 - windowOffsetDays}，默认 2 即 T-2 日。
     *
     * <p>甲方《ACC与ITP之间的文件》§一规定「T 日 2 点统计 T-2 日 2 点 ~ T-1 日 2 点」，
     * 文件名后缀取 T-2 日（例：8 月 20 号 2 点生成 ITP.EXP.20190818），因此默认 2，
     * <b>NEVER 改回 1</b>——改成 1 会让文件名后缀与统计区间同时前移一天，与甲方对不上。</p>
     */
    private int windowOffsetDays = 2;

    /**
     * 窗口的日切时刻 {@code HHmmss}，默认 02:00:00。
     *
     * <p>窗口为 {@code [businessDate 当天日切, businessDate + 1 天 日切)}，
     * 即默认 {@code [T-2 02:00, T-1 02:00)}。</p>
     */
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

        /** 源服务基础地址，形如 {@code http://ticket-server-svc.itp.svc:9103}。 */
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

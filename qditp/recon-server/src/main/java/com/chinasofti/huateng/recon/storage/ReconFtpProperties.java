package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recon.ftp")
public class ReconFtpProperties {
    private boolean enabled;
    private String host;
    private int port = 21;
    private String username;
    private String password;
    private String remoteRoot = "/itp/recon";
    /**
     * 每个文件投递完成后，回查远端确认文件真实存在且字节数一致（RNTO 之后立刻发 SIZE，失败再退回 LIST）。
     *
     * <p><b>NEVER 关掉</b>：`storeFile` 的 226 与 `rename` 的 250 只说明命令被接受，不构成
     * 「ACC 已拿到这个文件」的证据；回查是链路上唯一的正向证据，成功时会打
     * 「对账文件投递已回查通过 remotePath=..., bytes=...」，失败即抛 IOException 把该文件置 FAILED 等重试。</p>
     *
     * <p><b>它同时是「BUS 文件不见了」这类误判的唯一证伪手段。</b>2026-09-11 曾据「DB 记 UPLOADED
     * 但 FTP 上取回 550」判定投递静默失败，实际是 <b>ACC 侧会在约 30~45 秒内自行取走并删除
     * {@code ITP.BUS*} 文件</b>（对 {@code ITP.EXP/PAY/DETAIL} 与 {@code PROBE.BUS.*} 都不动，
     * 已用四个变体命名的探针文件在 172.20.215.3 上实测确认）。开着回查后日志里有
     * 「BUS bytes=19 回查通过」，才能把「我方没投上去」与「对端已消费」区分开。
     * <b>NEVER 再把「事后 LIST 看不到 ITP.BUS」当成我方缺陷</b>。</p>
     */
    private boolean verifyAfterUpload = true;
    /**
     * 相邻两个文件投递之间的间隔毫秒数，避免四个文件在几十毫秒内连打四次 FTP 会话。
     *
     * <p>置 0 表示不等待。<b>这不是修复项、只是保守限流</b>——引入时以为对端在极短间隔的连续会话上会丢文件，
     * 后经探针实测推翻（真实原因见 {@link #verifyAfterUpload}）。保留是因为日终一天只跑一次、
     * 多等 2.4 秒没有代价，且对端是甲方 vsFTPd + 取件进程，少并发更稳。要调小可以，但 MUST 先在
     * 测试环境连跑几轮，并以「回查通过」日志而不是事后 LIST 作为判据。</p>
     */
    private long uploadIntervalMillis = 800L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRemoteRoot() { return remoteRoot; }
    public void setRemoteRoot(String remoteRoot) { this.remoteRoot = remoteRoot; }
    public boolean isVerifyAfterUpload() { return verifyAfterUpload; }
    public void setVerifyAfterUpload(boolean verifyAfterUpload) { this.verifyAfterUpload = verifyAfterUpload; }
    public long getUploadIntervalMillis() { return uploadIntervalMillis; }
    public void setUploadIntervalMillis(long uploadIntervalMillis) { this.uploadIntervalMillis = uploadIntervalMillis; }
}

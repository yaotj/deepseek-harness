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
    /** 每个文件投递完成后，回查远端确认文件真实存在且字节数一致（RNTO 后发 SIZE，失败退回 LIST）。 */
    private boolean verifyAfterUpload = true;
    /** 相邻两个文件投递之间的间隔毫秒数，置 0 表示不等待。 */
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

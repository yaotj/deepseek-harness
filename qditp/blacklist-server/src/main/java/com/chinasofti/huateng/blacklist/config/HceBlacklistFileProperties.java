package com.chinasofti.huateng.blacklist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HCE 卡黑名单文件生成与上传 ACC FTP 的配置。
 *
 * <p>FTP 四要素（host/port/username/password）与远端目录全部走配置、MUST 支持环境变量覆盖，
 * 仓库里 NEVER 写真实口令。参考 para-server 的 {@code ParaFtpProperties} 口径。
 */
@Component
@ConfigurationProperties(prefix = "blacklist.hce-file")
public class HceBlacklistFileProperties {

    /** ACC FTP 主机。 */
    private String ftpHost;

    /** ACC FTP 端口。 */
    private int ftpPort = 21;

    /** ACC FTP 用户名。 */
    private String ftpUsername;

    /** ACC FTP 密码。 */
    private String ftpPassword;

    /** ACC 侧接收黑名单文件的远端目录。 */
    private String ftpRemoteDir = "/blacklist/";

    /** 本地暂存目录：文件生成后先落这里，上传成功后保留作为审计留痕。 */
    private String localDir = "/tmp/hce-blacklist";

    /** FTP 连接超时（毫秒）。 */
    private int connectTimeoutMillis = 10000;

    /** FTP 数据传输超时（毫秒）。 */
    private int dataTimeoutMillis = 60000;

    /** 文件名前缀：PRM. + 参数类型 + . + 节点编码 + .，版本号由状态表分配后拼接在尾部。 */
    private String filePrefix = "PRM.0013.9700.";

    /** 参数类型，同时是版本号状态表 HCE_BLACKLIST_FILE_STATE 的主键。 */
    private String paramType = "0013";

    public String getFtpHost() {
        return ftpHost;
    }

    public void setFtpHost(String ftpHost) {
        this.ftpHost = ftpHost;
    }

    public int getFtpPort() {
        return ftpPort;
    }

    public void setFtpPort(int ftpPort) {
        this.ftpPort = ftpPort;
    }

    public String getFtpUsername() {
        return ftpUsername;
    }

    public void setFtpUsername(String ftpUsername) {
        this.ftpUsername = ftpUsername;
    }

    public String getFtpPassword() {
        return ftpPassword;
    }

    public void setFtpPassword(String ftpPassword) {
        this.ftpPassword = ftpPassword;
    }

    public String getFtpRemoteDir() {
        return ftpRemoteDir;
    }

    public void setFtpRemoteDir(String ftpRemoteDir) {
        this.ftpRemoteDir = ftpRemoteDir;
    }

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getDataTimeoutMillis() {
        return dataTimeoutMillis;
    }

    public void setDataTimeoutMillis(int dataTimeoutMillis) {
        this.dataTimeoutMillis = dataTimeoutMillis;
    }

    public String getFilePrefix() {
        return filePrefix;
    }

    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }
}

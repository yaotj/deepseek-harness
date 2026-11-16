package com.chinasofti.huateng.blacklist.service.hce;

import com.chinasofti.huateng.blacklist.config.HceBlacklistFileProperties;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HCE 黑名单文件的 ACC FTP 上传器。
 *
 * <p>连接与超时口径对齐 para-server 的 {@code ParaFtpScanService}：被动模式、二进制传输、
 * 控制通道 UTF-8、文件名转 ISO-8859-1。
 */
@Component
public class HceBlacklistFtpUploader {

    private static final Logger log = LoggerFactory.getLogger(HceBlacklistFtpUploader.class);

    private final HceBlacklistFileProperties properties;

    public HceBlacklistFtpUploader(HceBlacklistFileProperties properties) {
        this.properties = properties;
    }

    /**
     * 把本地文件上传到 ACC FTP 的远端目录。
     *
     * @param localFile 本地文件
     * @param remoteFileName 远端文件名
     * @throws IllegalStateException 连接、登录或传输失败
     */
    public void upload(Path localFile, String remoteFileName) {
        FTPClient ftp = connect();
        try {
            if (!ftp.changeWorkingDirectory(properties.getFtpRemoteDir())) {
                throw new IllegalStateException("FTP目录不存在: " + properties.getFtpRemoteDir());
            }
            String ftpFileName = new String(remoteFileName.getBytes(StandardCharsets.UTF_8), "ISO-8859-1");
            try (InputStream in = Files.newInputStream(localFile)) {
                if (!ftp.storeFile(ftpFileName, in)) {
                    throw new IllegalStateException("FTP上传失败: " + ftp.getReplyString());
                }
            }
            log.info("HCE黑名单文件上传成功, {} -> {}:{}", remoteFileName,
                    properties.getFtpHost(), properties.getFtpRemoteDir());
        } catch (IOException e) {
            throw new IllegalStateException("FTP上传失败: " + e.getMessage(), e);
        } finally {
            disconnect(ftp);
        }
    }

    private FTPClient connect() {
        FTPClient ftp = new FTPClient();
        try {
            ftp.setConnectTimeout(properties.getConnectTimeoutMillis());
            ftp.setDefaultTimeout(properties.getConnectTimeoutMillis());
            ftp.setControlEncoding("UTF-8");
            ftp.connect(properties.getFtpHost(), properties.getFtpPort());
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new IllegalStateException("FTP连接被拒绝, replyCode=" + ftp.getReplyCode());
            }
            if (!ftp.login(properties.getFtpUsername(), properties.getFtpPassword())) {
                throw new IllegalStateException("FTP登录失败, 请检查用户名密码配置");
            }
            ftp.setSoTimeout(properties.getDataTimeoutMillis());
            ftp.setDataTimeout(java.time.Duration.ofMillis(properties.getDataTimeoutMillis()));
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
            return ftp;
        } catch (IOException e) {
            disconnect(ftp);
            throw new IllegalStateException("FTP连接失败: " + properties.getFtpHost() + ":" + properties.getFtpPort()
                    + ", " + e.getMessage(), e);
        }
    }

    private void disconnect(FTPClient ftp) {
        try {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
        } catch (IOException e) {
            log.warn("FTP关闭失败: {}", e.getMessage());
        }
    }
}

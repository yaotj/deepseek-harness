package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.recon.storage.ReconFtpProperties;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPCmd;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 对账文件的 FTP 投递。
 *
 * <p><b>投递成功的判据不是返回码，而是 RNTO 之后回查远端确实存在同字节数的文件。</b>
 * `storeFile` 的 226 与 `rename` 的 250 只说明命令被接受，回查（SIZE，失败退回 LIST）才是
 * 「文件真的躺在 /itp/recon 上」的证据，成功时打「对账文件投递已回查通过」，失败抛 IOException
 * 让该文件落 FAILED 等下一轮重试。</p>
 *
 * <p><b>NEVER 再把「事后 LIST 看不到 ITP.BUS.yyyyMMdd」当成投递失败。</b>2026-09-11 一度如此误判
 * （DB 记 UPLOADED、几分钟后 curl 取回 550，且四个文件原本在 70ms 内投完，看着像对端在极短间隔
 * 连续会话上丢文件）。用四个变体命名的探针文件在 172.20.215.3（vsFTPd 3.0.3）上实测推翻：
 * {@code ITP.BUS.20260910} / {@code ITP.BUS.20260909} / {@code ITP.BUSX.20260910} 都在 30~45 秒内
 * 被删除，{@code PROBE.BUS.20260910} 与 {@code ITP.EXP/PAY/DETAIL.*} 一直留着——<b>是 ACC 侧有个
 * 按 {@code ITP.BUS*} 取件的进程，取完即删</b>，我方投递本来就是成功的（同一轮日志里 BUS
 * bytes=19 回查通过）。核对投递结果 MUST 看回查日志，LIST 只能证明「还没被取走」。</p>
 */
@Service
public class ReconFtpService {
    private static final Logger log = LoggerFactory.getLogger(ReconFtpService.class);

    private final ReconFtpProperties properties;

    private final ReentrantLock uploadLock = new ReentrantLock();

    private long lastUploadFinishedNanos;

    public ReconFtpService(ReconFtpProperties properties) {
        this.properties = properties;
    }

    public String upload(Path localFile, String fileName) throws IOException {
        if (!properties.isEnabled()) throw new IllegalStateException("对账 FTP 未启用");
        if (properties.getHost() == null || properties.getHost().isBlank()
                || properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException("对账 FTP 配置不完整");
        }
        if (fileName == null || !fileName.matches("ITP\\.(EXP|PAY|BUS|DETAIL)\\.\\d{8}")) {
            throw new IllegalArgumentException("非法对账文件名");
        }
        Path path = localFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException("对账文件不存在: " + path);

        String remoteRoot = normalizeRemoteRoot(properties.getRemoteRoot());
        String remotePath = remoteRoot + "/" + fileName;
        String temporaryPath = remotePath + ".uploading";
        uploadLock.lock();
        try {
            awaitUploadInterval();
            return doUpload(path, fileName, remoteRoot, remotePath, temporaryPath);
        } finally {
            lastUploadFinishedNanos = System.nanoTime();
            uploadLock.unlock();
        }
    }

    /**
     * 串行化并拉开相邻投递的间隔。
     *
     * <p>用 {@link ReentrantLock} 而不是 {@code synchronized}：本方法后面紧跟着阻塞式的 FTP IO，
     * 而 JDK 21 的虚拟线程在 {@code synchronized} 内阻塞会 pin 住载体线程（见 AGENTS.md §5.2）。</p>
     */
    private void awaitUploadInterval() throws IOException {
        long interval = properties.getUploadIntervalMillis();
        if (interval <= 0 || lastUploadFinishedNanos == 0L) return;
        long elapsed = (System.nanoTime() - lastUploadFinishedNanos) / 1_000_000L;
        long remaining = interval - elapsed;
        if (remaining <= 0) return;
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("等待对账 FTP 投递间隔被中断", ex);
        }
    }

    private String doUpload(Path path, String fileName, String remoteRoot, String remotePath, String temporaryPath)
            throws IOException {
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(30_000);
        ftp.setDefaultTimeout(30_000);
        ftp.setDataTimeout(120_000);
        try {
            ftp.connect(properties.getHost(), properties.getPort());
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) throw new IOException("FTP 连接被拒绝");
            if (!ftp.login(properties.getUsername(), properties.getPassword())) throw new IOException("FTP 登录失败");
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
            ensureDirectory(ftp, remoteRoot);
            try (InputStream input = Files.newInputStream(path)) {
                if (!ftp.storeFile(temporaryPath, input)) throw new IOException("FTP 临时文件上传失败: " + ftp.getReplyString());
            }
            if (!ftp.rename(temporaryPath, remotePath)) throw new IOException("FTP 文件改名失败: " + ftp.getReplyString());
            if (properties.isVerifyAfterUpload()) {
                verifyRemote(ftp, remoteRoot, fileName, remotePath, Files.size(path));
            }
            return remotePath;
        } finally {
            if (ftp.isConnected()) {
                try { ftp.logout(); } finally { ftp.disconnect(); }
            }
        }
    }

    /**
     * 回查远端文件确实存在且字节数与本地一致，不一致即抛异常让批次转 FAILED 等重试。
     *
     * <p>先用 {@code SIZE}（BINARY 模式下回 {@code 213 <bytes>}），拿不到再退回 {@code LIST}
     * 解析目录项。两条路都问不出结果时**按失败处理**：这里的存在意义就是兜住「返回码说成功、
     * 文件其实没落地」，问不出来就不能声称投递成功。</p>
     */
    private void verifyRemote(FTPClient ftp, String remoteRoot, String fileName, String remotePath, long expectedBytes)
            throws IOException {
        Long actual = remoteSizeBySizeCommand(ftp, remotePath);
        if (actual == null) {
            actual = remoteSizeByListing(ftp, remoteRoot, fileName);
        }
        if (actual == null) {
            throw new IOException("对账文件投递后回查不到远端文件: " + remotePath
                    + "，最后应答: " + ftp.getReplyString());
        }
        if (actual != expectedBytes) {
            throw new IOException("对账文件投递后远端字节数不一致: " + remotePath
                    + "，本地=" + expectedBytes + "，远端=" + actual);
        }
        log.info("对账文件投递已回查通过 remotePath={}, bytes={}", remotePath, actual);
    }

    private Long remoteSizeBySizeCommand(FTPClient ftp, String remotePath) throws IOException {
        if (!FTPReply.isPositiveCompletion(ftp.sendCommand(FTPCmd.SIZE, remotePath))) return null;
        String reply = ftp.getReplyString();
        if (reply == null) return null;
        String digits = reply.replaceAll("^\\s*\\d{3}[\\s-]*", "").trim();
        int end = 0;
        while (end < digits.length() && Character.isDigit(digits.charAt(end))) end++;
        if (end == 0) return null;
        try {
            return Long.parseLong(digits.substring(0, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long remoteSizeByListing(FTPClient ftp, String remoteRoot, String fileName) throws IOException {
        for (FTPFile candidate : ftp.listFiles(remoteRoot)) {
            if (candidate != null && candidate.isFile() && fileName.equals(candidate.getName())) {
                return candidate.getSize();
            }
        }
        return null;
    }

    private void ensureDirectory(FTPClient ftp, String remoteRoot) throws IOException {
        String current = "";
        for (String part : remoteRoot.split("/")) {
            if (part.isBlank()) continue;
            current += "/" + part;
            if (!ftp.changeWorkingDirectory(current) && !ftp.makeDirectory(current) && !ftp.changeWorkingDirectory(current)) {
                throw new IOException("FTP 目录不可用: " + current);
            }
        }
    }

    private String normalizeRemoteRoot(String root) {
        if (root == null || root.isBlank()) throw new IllegalStateException("对账 FTP 远端目录未配置");
        String normalized = root.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        while (normalized.endsWith("/") && normalized.length() > 1) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.contains("..")) throw new IllegalArgumentException("非法 FTP 远端目录");
        return normalized;
    }
}

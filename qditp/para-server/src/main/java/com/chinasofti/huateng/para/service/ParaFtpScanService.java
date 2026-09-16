package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.config.ParaFtpProperties;
import com.chinasofti.huateng.para.entity.ParaVersion;
import com.chinasofti.huateng.para.mapper.ParaVersionMapper;
import com.chinasofti.huateng.para.model.ParaImportResult;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描FTP目录中的路网拓扑(0001)、费率(0004)参数文件，
 * 按「版本号 + MD5」判断有无变更，有变更时下载并解析入库。
 */
@Service
public class ParaFtpScanService {

    private static final Logger log = LoggerFactory.getLogger(ParaFtpScanService.class);

    /**
     * 参数文件名规则：PRM. + 参数类型(4位) + . + 节点编码(4位) + . + 版本号(6位) + [. + 尾段]
     * 2026-09-08 实测 ACC 真实文件名带第 5 段（如 PRM.0001.9900.000041.02000000），
     * 因此尾段做可选匹配；原先以 (\d{6})$ 收尾会一个文件都匹配不上。
     */
    private static final Pattern PARA_FILE_PATTERN = Pattern.compile("^PRM\\.(0001|0004)\\.(\\d{4})\\.(\\d{6})(?:\\.\\d+)?$");

    private final ParaFtpProperties ftpProperties;
    private final ParaVersionMapper paraVersionMapper;
    private final ParaFileImportService paraFileImportService;

    public ParaFtpScanService(ParaFtpProperties ftpProperties,
                              ParaVersionMapper paraVersionMapper,
                              ParaFileImportService paraFileImportService) {
        this.ftpProperties = ftpProperties;
        this.paraVersionMapper = paraVersionMapper;
        this.paraFileImportService = paraFileImportService;
    }

    public FtpScanResponse scanAndImport() {
        FtpScanResponse response = new FtpScanResponse();
        response.setRemoteDir(ftpProperties.getRemoteDir());
        response.setFiles(new ArrayList<>());

        FTPClient ftp = connect();
        Path tempDirectory = null;
        try {
            if (!ftp.changeWorkingDirectory(ftpProperties.getRemoteDir())) {
                throw new IllegalStateException("FTP目录不存在: " + ftpProperties.getRemoteDir());
            }
            List<FtpParaFile> candidates = findCandidates(ftp);
            response.setTotal(candidates.size());

            if (!candidates.isEmpty()) {
                tempDirectory = Files.createTempDirectory("para-ftp-");
                for (FtpParaFile candidate : candidates) {
                    FtpFileResult fileResult = processOne(ftp, candidate, tempDirectory);
                    response.getFiles().add(fileResult);
                    if (Boolean.TRUE.equals(fileResult.getDownloaded())) {
                        response.setDownloaded(response.getDownloaded() + 1);
                    }
                    if (Boolean.TRUE.equals(fileResult.getImported())) {
                        response.setImported(response.getImported() + 1);
                    } else if (Boolean.TRUE.equals(fileResult.getSuccess())) {
                        response.setSkipped(response.getSkipped() + 1);
                    } else {
                        response.setFailed(response.getFailed() + 1);
                    }
                }
            }
            log.info("FTP参数扫描完成, dir={}, 匹配={}, 下载={}, 入库={}, 跳过={}, 失败={}",
                    response.getRemoteDir(), response.getTotal(), response.getDownloaded(),
                    response.getImported(), response.getSkipped(), response.getFailed());
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("FTP扫描失败: " + e.getMessage(), e);
        } finally {
            deleteTempDirectory(tempDirectory);
            disconnect(ftp);
        }
    }

    private FTPClient connect() {
        FTPClient ftp = new FTPClient();
        try {
            ftp.setConnectTimeout(ftpProperties.getConnectTimeoutMillis());
            ftp.setDefaultTimeout(ftpProperties.getConnectTimeoutMillis());
            ftp.setControlEncoding("UTF-8");
            ftp.connect(ftpProperties.getIp(), ftpProperties.getPort());
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new IllegalStateException("FTP连接被拒绝, replyCode=" + ftp.getReplyCode());
            }
            if (!ftp.login(ftpProperties.getUsername(), ftpProperties.getPassword())) {
                throw new IllegalStateException("FTP登录失败, 请检查用户名密码配置");
            }
            ftp.setSoTimeout(ftpProperties.getDataTimeoutMillis());
            ftp.setDataTimeout(java.time.Duration.ofMillis(ftpProperties.getDataTimeoutMillis()));
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
            log.info("FTP连接成功, {}:{}", ftpProperties.getIp(), ftpProperties.getPort());
            return ftp;
        } catch (IOException e) {
            disconnect(ftp);
            throw new IllegalStateException("FTP连接失败: " + ftpProperties.getIp() + ":" + ftpProperties.getPort()
                    + ", " + e.getMessage(), e);
        }
    }

    /**
     * 列出FTP目录中符合命名规则、且需要进一步判断的文件，按参数类型分组、组内版本号升序返回。
     *
     * <p>2026-09-08 起预筛判据是「版本号 + MD5」。FTP LIST 拿不到 MD5，必须下载后才能算，
     * 因此本方法只能按版本号做**粗筛**，等版本的文件也要进候选下载：</p>
     * <ul>
     *   <li>文件版本号 &gt; 库中版本 → 候选（版本升高）</li>
     *   <li>文件版本号 = 库中版本 → 候选（需下载后比 MD5 才能确定有无变更）</li>
     *   <li>文件版本号 &lt; 库中版本 → 跳过（版本回退不处理）</li>
     * </ul>
     *
     * <p>最终「导入还是跳过」由 ParaFileImportService 单点裁决，本方法 NEVER 自己比 MD5——
     * 那会让 /para/import/ftp 与 /para/import/directory 两条路径的判据分叉。</p>
     */
    private List<FtpParaFile> findCandidates(FTPClient ftp) throws IOException {
        Map<String, Long> currentVersions = new HashMap<>();
        List<FtpParaFile> candidates = new ArrayList<>();
        for (FTPFile file : ftp.listFiles()) {
            if (!file.isFile()) {
                continue;
            }
            Matcher matcher = PARA_FILE_PATTERN.matcher(file.getName());
            if (!matcher.matches()) {
                continue;
            }
            String paraType = matcher.group(1);
            long fileVerNo = Long.parseLong(matcher.group(3));
            long currentVerNo = currentVersions.computeIfAbsent(paraType, this::queryCurrentVersion);
            if (fileVerNo < currentVerNo) {
                log.info("参数文件版本回退, 跳过: {}, 文件版本={}, 库中版本={}", file.getName(), fileVerNo, currentVerNo);
                continue;
            }
            if (fileVerNo == currentVerNo) {
                log.info("参数文件版本相同, 下载后比对MD5: {}, 版本={}", file.getName(), fileVerNo);
            }
            candidates.add(new FtpParaFile(file.getName(), paraType, fileVerNo, currentVerNo));
        }
        candidates.sort(Comparator.comparing(FtpParaFile::getParaType).thenComparingLong(FtpParaFile::getFileVerNo));
        return candidates;
    }

    private long queryCurrentVersion(String paraType) {
        ParaVersion current = paraVersionMapper.selectByParaType(paraType);
        return current != null && current.getCurrentVerNo() != null ? current.getCurrentVerNo() : 0L;
    }

    private FtpFileResult processOne(FTPClient ftp, FtpParaFile candidate, Path tempDirectory) {
        FtpFileResult result = new FtpFileResult();
        result.setFileName(candidate.getFileName());
        result.setParaType(candidate.getParaType());
        result.setFileVerNo(candidate.getFileVerNo());
        result.setCurrentVerNo(candidate.getCurrentVerNo());
        result.setSuccess(false);
        result.setDownloaded(false);
        result.setImported(false);

        Path localFile = tempDirectory.resolve(candidate.getFileName());
        try {
            // 下载FTP文件到本地临时目录
            try (OutputStream out = Files.newOutputStream(localFile)) {
                String ftpFileName = new String(candidate.getFileName().getBytes("UTF-8"), "ISO-8859-1");
                if (!ftp.retrieveFile(ftpFileName, out)) {
                    result.setMessage("FTP下载失败: " + ftp.getReplyString());
                    log.error("FTP下载失败: {}, {}", candidate.getFileName(), result.getMessage());
                    return result;
                }
            }
            result.setDownloaded(true);

            // 解析入库（内部按文件头版本号 + 全文MD5做权威复核，无变更会跳过）
            ParaImportResult importResult = paraFileImportService.importLocalFile(localFile.toString());
            result.setSuccess(true);
            result.setImported(importResult.getImported());
            result.setMessage(importResult.getMessage());
            log.info("参数文件处理完成: {}, imported={}, {}", candidate.getFileName(), importResult.getImported(), importResult.getMessage());
        } catch (Exception e) {
            result.setMessage(buildErrorMessage(e));
            log.error("参数文件处理失败: {}", candidate.getFileName(), e);
        } finally {
            deleteTempFile(localFile);
        }
        return result;
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

    private void deleteTempFile(Path file) {
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.warn("临时文件删除失败: {}, {}", file, e.getMessage());
        }
    }

    private void deleteTempDirectory(Path directory) {
        try {
            if (directory != null) {
                Files.deleteIfExists(directory);
            }
        } catch (IOException e) {
            log.warn("临时目录删除失败: {}, {}", directory, e.getMessage());
        }
    }

    private String buildErrorMessage(Throwable throwable) {
        StringBuilder message = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (!message.isEmpty()) {
                message.append("\nCaused by: ");
            }
            message.append(current.getClass().getName()).append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return message.toString();
    }

    /** FTP上匹配到的候选参数文件 */
    private static class FtpParaFile {
        private final String fileName;
        private final String paraType;
        private final long fileVerNo;
        private final long currentVerNo;

        FtpParaFile(String fileName, String paraType, long fileVerNo, long currentVerNo) {
            this.fileName = fileName;
            this.paraType = paraType;
            this.fileVerNo = fileVerNo;
            this.currentVerNo = currentVerNo;
        }

        String getFileName() { return fileName; }
        String getParaType() { return paraType; }
        long getFileVerNo() { return fileVerNo; }
        long getCurrentVerNo() { return currentVerNo; }
    }

    public static class FtpScanResponse {
        private String remoteDir;
        private int total;
        private int downloaded;
        private int imported;
        private int skipped;
        private int failed;
        private List<FtpFileResult> files;

        public String getRemoteDir() { return remoteDir; }
        public void setRemoteDir(String remoteDir) { this.remoteDir = remoteDir; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getDownloaded() { return downloaded; }
        public void setDownloaded(int downloaded) { this.downloaded = downloaded; }
        public int getImported() { return imported; }
        public void setImported(int imported) { this.imported = imported; }
        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public List<FtpFileResult> getFiles() { return files; }
        public void setFiles(List<FtpFileResult> files) { this.files = files; }
    }

    public static class FtpFileResult {
        private String fileName;
        private String paraType;
        private Long fileVerNo;
        private Long currentVerNo;
        private Boolean success;
        private Boolean downloaded;
        private Boolean imported;
        private String message;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getParaType() { return paraType; }
        public void setParaType(String paraType) { this.paraType = paraType; }
        public Long getFileVerNo() { return fileVerNo; }
        public void setFileVerNo(Long fileVerNo) { this.fileVerNo = fileVerNo; }
        public Long getCurrentVerNo() { return currentVerNo; }
        public void setCurrentVerNo(Long currentVerNo) { this.currentVerNo = currentVerNo; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Boolean getDownloaded() { return downloaded; }
        public void setDownloaded(Boolean downloaded) { this.downloaded = downloaded; }
        public Boolean getImported() { return imported; }
        public void setImported(Boolean imported) { this.imported = imported; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

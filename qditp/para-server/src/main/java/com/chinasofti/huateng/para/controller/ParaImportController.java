package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.para.model.CalendarParseResult;
import com.chinasofti.huateng.para.model.ParaImportResult;
import com.chinasofti.huateng.para.model.RateParseResult;
import com.chinasofti.huateng.para.model.RowNetworkParseResult;
import com.chinasofti.huateng.para.model.TicketParseResult;
import com.chinasofti.huateng.para.service.ParaFileImportService;
import com.chinasofti.huateng.para.service.ParaFtpScanService;
import com.chinasofti.huateng.common.response.CommonResult;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/para/import")
public class ParaImportController {

    private final ParaFileImportService paraFileImportService;
    private final ParaFtpScanService paraFtpScanService;

    public ParaImportController(ParaFileImportService paraFileImportService,
                                ParaFtpScanService paraFtpScanService) {
        this.paraFileImportService = paraFileImportService;
        this.paraFtpScanService = paraFtpScanService;
    }

    /**
     * 扫描FTP目录中的路网拓扑(0001)、费率(0004)参数文件，
     * 版本号高于库中版本时下载并解析入库。
     */
    @PostMapping("/ftp")
    public ParaFtpScanService.FtpScanResponse importFromFtp() {
        return paraFtpScanService.scanAndImport();
    }

    /**
     * 供 web-server Quartz 定时任务调用：扫描FTP参数文件并入库。
     * retCode=0000 表示本次扫描全部成功，存在失败文件时返回 9999。
     */
    @PostMapping("/ftp/quartz")
    public CommonResult importFromFtpForQuartz() {
        ParaFtpScanService.FtpScanResponse response = paraFtpScanService.scanAndImport();
        String summary = String.format("FTP参数扫描完成: 目录=%s, 匹配=%d, 下载=%d, 入库=%d, 跳过=%d, 失败=%d",
                response.getRemoteDir(), response.getTotal(), response.getDownloaded(),
                response.getImported(), response.getSkipped(), response.getFailed());
        CommonResult result = new CommonResult();
        if (response.getFailed() > 0) {
            result.setRetCode("9999");
        } else {
            result.setRetCode("0000");
        }
        result.setRetMsg(summary);
        return result;
    }

    @PostMapping("/directory")
    public DirectoryImportResponse importDirectory(@RequestBody DirectoryImportRequest request) {
        return importDirectoryPath(request.getDirectory());
    }

    @GetMapping("/directory")
    public DirectoryImportResponse importDirectory(@RequestParam("directory") String directory) {
        return importDirectoryPath(directory);
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DirectoryImportResponse importFile(@RequestParam("file") MultipartFile file) {
        String fileName = resolveUploadFileName(file);
        Path tempDirectory;
        try {
            tempDirectory = Files.createTempDirectory("para-import-");
        } catch (IOException e) {
            throw new IllegalStateException("创建参数上传临时目录失败", e);
        }

        Path tempFile = tempDirectory.resolve(fileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            List<FileImportResult> results = importFiles(List.of(tempFile));
            DirectoryImportResponse response = new DirectoryImportResponse();
            response.setDirectory(tempDirectory.toString());
            response.setTotal(results.size());
            response.setSuccess((int) results.stream().filter(FileImportResult::getSuccess).count());
            response.setFailure(response.getTotal() - response.getSuccess());
            response.setFiles(results);
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("保存上传参数文件失败: " + fileName, e);
        } finally {
            deleteTemporaryUpload(tempFile, tempDirectory);
        }
    }

    private DirectoryImportResponse importDirectoryPath(String directory) {
        Path dir = Path.of(directory);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在: " + directory);
        }

        List<Path> files;
        try (Stream<Path> paths = Files.list(dir)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("PRM."))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("读取参数目录失败: " + directory, e);
        }

        List<FileImportResult> results = importFiles(files);
        DirectoryImportResponse response = new DirectoryImportResponse();
        response.setDirectory(directory);
        response.setTotal(results.size());
        response.setSuccess((int) results.stream().filter(FileImportResult::getSuccess).count());
        response.setFailure(response.getTotal() - response.getSuccess());
        response.setFiles(results);
        return response;
    }

    private String resolveUploadFileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传参数文件");
        }
        String originalFilename = file.getOriginalFilename();
        String fileName = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        int pathSeparator = fileName.lastIndexOf('/');
        fileName = pathSeparator >= 0 ? fileName.substring(pathSeparator + 1) : fileName;
        if (!fileName.startsWith("PRM.")) {
            throw new IllegalArgumentException("参数文件名必须以 PRM. 开头");
        }
        return fileName;
    }

    private void deleteTemporaryUpload(Path tempFile, Path tempDirectory) {
        try {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempDirectory);
        } catch (IOException ignored) {
            // Temporary files are not part of the import result and can be cleaned up by the OS if needed.
        }
    }

    private List<FileImportResult> importFiles(List<Path> files) {
        List<FileImportResult> results = new ArrayList<>();
        for (Path file : files) {
            results.add(importOne(file));
        }
        return results;
    }

    private FileImportResult importOne(Path path) {
        FileImportResult result = new FileImportResult();
        result.setFileName(path.getFileName().toString());
        result.setFilePath(path.toString());
        try {
            ParaImportResult importResult = paraFileImportService.importLocalFile(path.toString());
            result.setSuccess(true);
            result.setMessage(importResult.getMessage());
            result.setImported(importResult.getImported());
            result.setParseSummary(buildSummary(importResult));
        } catch (Exception e) {
            result.setSuccess(false);
            result.setImported(false);
            result.setMessage(buildErrorMessage(e));
        }
        return result;
    }

    private Map<String, Object> buildSummary(ParaImportResult importResult) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("header", importResult.getHeader());
        summary.put("imported", importResult.getImported());
        Object parseResult = importResult.getParseResult();
        if (parseResult == null) {
            return summary;
        }
        if (parseResult instanceof RowNetworkParseResult result) {
            summary.put("md5Valid", result.getMd5Valid());
            summary.put("bodyReadBytes", result.getBodyReadBytes());
            summary.put("bodyTotalBytes", result.getBodyTotalBytes());
            summary.put("lineInfos", result.getLineInfos().size());
            summary.put("stationInfos", result.getStationInfos().size());
            summary.put("tsfInfos", result.getTsfInfos().size());
            summary.put("zoneInfos", result.getZoneInfos().size());
            summary.put("zoneDtls", result.getZoneDtls().size());
            summary.put("sectInfos", result.getSectInfos().size());
        } else if (parseResult instanceof CalendarParseResult result) {
            summary.put("md5Valid", result.getMd5Valid());
            summary.put("bodyReadBytes", result.getBodyReadBytes());
            summary.put("bodyTotalBytes", result.getBodyTotalBytes());
            summary.put("specialDates", result.getSpecialDates().size());
            summary.put("timeIntervals", result.getTimeIntervals().size());
            summary.put("fareTimes", result.getFareTimes().size());
        } else if (parseResult instanceof TicketParseResult result) {
            summary.put("md5Valid", result.getMd5Valid());
            summary.put("bodyReadBytes", result.getBodyReadBytes());
            summary.put("bodyTotalBytes", result.getBodyTotalBytes());
            summary.put("chipTypes", result.getChipTypes().size());
            summary.put("ticketTypes", result.getTicketTypes().size());
            summary.put("totalSaleParts", result.getTotalSaleParts().size());
        } else if (parseResult instanceof RateParseResult result) {
            summary.put("md5Valid", result.getMd5Valid());
            summary.put("bodyReadBytes", result.getBodyReadBytes());
            summary.put("bodyTotalBytes", result.getBodyTotalBytes());
            summary.put("fareMatrices", result.getFareMatrices().size());
            summary.put("ticketFares", result.getTicketFares().size());
            summary.put("fareGroups", result.getFareGroups().size());
            summary.put("baseFares", result.getBaseFares().size());
        }
        return summary;
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

    public static class DirectoryImportRequest {
        private String directory;

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
    }

    public static class DirectoryImportResponse {
        private String directory;
        private Integer total;
        private Integer success;
        private Integer failure;
        private List<FileImportResult> files;

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public Integer getTotal() { return total; }
        public void setTotal(Integer total) { this.total = total; }
        public Integer getSuccess() { return success; }
        public void setSuccess(Integer success) { this.success = success; }
        public Integer getFailure() { return failure; }
        public void setFailure(Integer failure) { this.failure = failure; }
        public List<FileImportResult> getFiles() { return files; }
        public void setFiles(List<FileImportResult> files) { this.files = files; }
    }

    public static class FileImportResult {
        private String fileName;
        private String filePath;
        private Boolean success;
        private Boolean imported;
        private String message;
        private Map<String, Object> parseSummary;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public Boolean getImported() { return imported; }
        public void setImported(Boolean imported) { this.imported = imported; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Map<String, Object> getParseSummary() { return parseSummary; }
        public void setParseSummary(Map<String, Object> parseSummary) { this.parseSummary = parseSummary; }
    }
}

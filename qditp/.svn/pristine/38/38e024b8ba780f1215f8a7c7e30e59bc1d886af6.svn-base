package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.para.model.CalendarParseResult;
import com.chinasofti.huateng.para.model.ParaImportResult;
import com.chinasofti.huateng.para.model.RateParseResult;
import com.chinasofti.huateng.para.model.RowNetworkParseResult;
import com.chinasofti.huateng.para.model.TicketParseResult;
import com.chinasofti.huateng.para.service.ParaFileImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public ParaImportController(ParaFileImportService paraFileImportService) {
        this.paraFileImportService = paraFileImportService;
    }

    @PostMapping("/directory")
    public DirectoryImportResponse importDirectory(@RequestBody DirectoryImportRequest request) {
        return importDirectoryPath(request.getDirectory());
    }

    @GetMapping("/directory")
    public DirectoryImportResponse importDirectory(@RequestParam("directory") String directory) {
        return importDirectoryPath(directory);
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

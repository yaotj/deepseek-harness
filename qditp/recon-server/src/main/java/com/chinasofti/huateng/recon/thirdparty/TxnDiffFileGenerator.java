package com.chinasofti.huateng.recon.thirdparty;

import com.chinasofti.huateng.model.recon.ReconRecord;
import com.chinasofti.huateng.recon.service.ReconFtpService;
import com.chinasofti.huateng.recon.storage.ThirdPartyReconProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;


@Service
public class TxnDiffFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(TxnDiffFileGenerator.class);

    private final ReconFtpService ftpService;
    private final String outputDir;

    public TxnDiffFileGenerator(ReconFtpService ftpService, ThirdPartyReconProperties properties) {
        this.ftpService = ftpService;
        this.outputDir = properties.getOutputDir();
    }

    public String generate(String billDate, List<TxnDiffRecord> diffs) throws IOException {
        String yyyyMMdd = billDate == null ? "" : billDate.replace("-", "");
        String fileName = "ITP.TXNDIFF." + yyyyMMdd;
        Path directory = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName).normalize();
        Path temporary = directory.resolve(fileName + ".generating");

        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            for (TxnDiffRecord r : diffs) {
                writer.write(line(r));
                writer.write('\n');
            }
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(temporary);
            throw ex;
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("TXNDIFF 文件已生成 file={} 记录数={}", target, diffs.size());

        try {
            String remote = ftpService.upload(target, fileName);
            log.info("TXNDIFF 文件已投递 remote={}", remote);
        } catch (IllegalStateException ex) {
            log.warn("TXNDIFF FTP 投递跳过（FTP 未启用或文件名校验失败）：{}", ex.getMessage());
        }
        return fileName;
    }

    private static String line(TxnDiffRecord r) {
        return ReconRecord.line(
                r.getBillDate(),
                r.getTxnType(),
                r.getLogicCardNo(),
                r.getTxnDateTime(),
                r.getOriginalFare(),
                r.getCurStationName(),
                r.getDeviceNo(),
                r.getTicketType(),
                r.getTicketSubType(),
                r.getTimes(),
                r.getPayChannel(),
                r.getActivateTime(),
                r.getQuantity(),
                r.getTicketTransSeq(),
                r.getOrderNo(),
                r.getOrderAmount(),
                r.getActualAmount(),
                r.getDiscountCumulativeAmount(),
                r.getOvertimeAmount(),
                r.getDiffSide(),
                r.getFailReason()
        );
    }
}

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

/**
 * 把差异明细生成为 ITP.TXNDIFF.YYYYMMDD（管道分隔、UTF-8、无表头），并复用 ReconFtpService 投递到 /itp/recon/。
 *
 * <p>文件名后缀取自然日（YYYYMMDD），与 DETAIL 的运营日口径不同，见 recon-thirdparty.md §五 项8。</p>
 */
@Service
public class TxnDiffFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(TxnDiffFileGenerator.class);

    private final ReconFtpService ftpService;
    private final String outputDir;

    public TxnDiffFileGenerator(ReconFtpService ftpService, ThirdPartyReconProperties properties) {
        this.ftpService = ftpService;
        this.outputDir = properties.getOutputDir();
    }

    /** @return 本地文件名（投递成功与否均返回文件名，便于审计） */
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
                r.getBillDate(),            // 1 运营日期
                r.getTxnType(),             // 2 交易类型
                r.getLogicCardNo(),         // 3 逻辑卡号
                r.getTxnDateTime(),         // 4 交易日期
                r.getOriginalFare(),        // 5 交易原价(分)
                r.getCurStationName(),      // 6 当前车站名称
                r.getDeviceNo(),            // 7 设备编码
                r.getTicketType(),          // 8 票卡类型
                r.getTicketSubType(),       // 9 票卡子类型
                r.getTimes(),               // 10 次数
                r.getPayChannel(),          // 11 支付渠道
                r.getActivateTime(),        // 12 激活时间
                r.getQuantity(),            // 13 数量
                r.getTicketTransSeq(),      // 14 交易流水号
                r.getOrderNo(),             // 15 交易订单号
                r.getOrderAmount(),         // 16 订单金额(分)
                r.getActualAmount(),        // 17 实际扣款金额(分)
                r.getDiscountCumulativeAmount(), // 18 优惠累计消费金额(分)
                r.getOvertimeAmount(),      // 19 超时金额(分)
                r.getDiffSide(),            // 20 差异方
                r.getFailReason()           // 21 扣款失败原因
        );
    }
}

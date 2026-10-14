package com.chinasofti.huateng.recon.thirdparty;

import com.chinasofti.huateng.recon.mapper.ReconThirdBillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 把渠道账单 csv 解析并幂等入库 RECON_THIRD_BILL。
 *
 * <p>账单投递方式（FTP 拉取 / 本地放置）属待确认项（recon-thirdparty.md §五 项1）；本服务只负责「拿到本地文件后」的解析与入库，
 * 文件来源由调度层决定。</p>
 */
@Service
public class ThirdPartyBillIngestService {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyBillIngestService.class);

    private final ThirdPartyBillCsvParser parser;
    private final ReconThirdBillMapper billMapper;

    public ThirdPartyBillIngestService(ThirdPartyBillCsvParser parser, ReconThirdBillMapper billMapper) {
        this.parser = parser;
        this.billMapper = billMapper;
    }

    /** 仅解析不入库，供调用方先确定账单日范围。 */
    public List<ThirdPartyBill> parseOnly(Path csvFile) throws IOException {
        if (!Files.isRegularFile(csvFile)) throw new IOException("渠道账单文件不存在: " + csvFile);
        return parser.parse(csvFile);
    }

    /** 入库已解析的账单列表。 */
    public int ingest(List<ThirdPartyBill> bills) {
        if (bills == null || bills.isEmpty()) return 0;
        String batchNo = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        for (ThirdPartyBill bill : bills) {
            bill.setBatchNo(batchNo);
        }
        int affected = billMapper.mergeBatch(bills, batchNo);
        log.info("渠道账单入库完成 解析行数={} 入库影响行数={} batchNo={}", bills.size(), affected, batchNo);
        return bills.size();
    }

    /** 解析并幂等入库（便捷入口）。 */
    public int ingest(Path csvFile) throws IOException {
        return ingest(parseOnly(csvFile));
    }
}

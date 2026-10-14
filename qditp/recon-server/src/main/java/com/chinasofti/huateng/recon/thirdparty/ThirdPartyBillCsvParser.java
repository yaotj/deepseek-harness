package com.chinasofti.huateng.recon.thirdparty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析渠道日账单 reconciliation-bill-date.csv（22 字段，逗号分隔，首行为表头）。
 *
 * <p>手写解析以支持带双引号的字段（渠道账单可能含逗号/换行），不引入额外依赖。
 * 字段顺序严格遵循需求文档，索引与 docs/business/recon-thirdparty.md §二 一致。</p>
 */
@Component
public class ThirdPartyBillCsvParser {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyBillCsvParser.class);

    private static final int EXPECTED_FIELDS = 22;

    public List<ThirdPartyBill> parse(Path csvFile) throws IOException {
        List<ThirdPartyBill> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                if (line.isBlank()) continue;
                List<String> fields = splitCsvLine(line);
                if (fields.size() != EXPECTED_FIELDS) {
                    log.warn("渠道账单行字段数异常，跳过 file={} 期望={} 实际={} 内容前64={}",
                            csvFile.getFileName(), EXPECTED_FIELDS, fields.size(), line.length() > 64 ? line.substring(0, 64) : line);
                    continue;
                }
                result.add(toBill(fields));
            }
        }
        return result;
    }

    /** 拆解一行 CSV，支持双引号包裹与转义（"" → "）。 */
    private List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private ThirdPartyBill toBill(List<String> f) {
        ThirdPartyBill bill = new ThirdPartyBill();
        bill.setBillDate(trim(f.get(0)));
        bill.setPaymentChannel(trim(f.get(1)));
        bill.setPaymentVendor(trim(f.get(2)));
        bill.setChannelName(trim(f.get(3)));
        bill.setChannelMerchantNo(trim(f.get(4)));
        bill.setTerminalNo(trim(f.get(5)));
        bill.setBusinessType(trim(f.get(6)));
        bill.setTradeStatus(trim(f.get(7)));
        bill.setTradeTime(trim(f.get(8)));
        bill.setClearingDate(trim(f.get(9)));
        bill.setMerchantOrderNo(trim(f.get(10)));
        bill.setChannelTradeNo(trim(f.get(11)));
        bill.setMerchantRefundNo(trim(f.get(12)));
        bill.setChannelRefundNo(trim(f.get(13)));
        bill.setCurrency(trim(f.get(14)));
        bill.setOrderAmount(toDecimal(f.get(15)));
        bill.setTransactionAmount(toDecimal(f.get(16)));
        bill.setFeeAmount(toDecimal(f.get(17)));
        bill.setSettlementAmount(toDecimal(f.get(18)));
        bill.setDiscountAmount(toDecimal(f.get(19)));
        bill.setProductName(trim(f.get(20)));
        bill.setRemark(trim(f.get(21)));
        return bill;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static BigDecimal toDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

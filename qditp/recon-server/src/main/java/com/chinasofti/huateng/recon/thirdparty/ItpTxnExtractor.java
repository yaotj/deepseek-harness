package com.chinasofti.huateng.recon.thirdparty;

import com.chinasofti.huateng.recon.mapper.PayTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class ItpTxnExtractor {

    private static final Logger log = LoggerFactory.getLogger(ItpTxnExtractor.class);

    private final PayTxnDetailMapper payTxnDetailMapper;
    private final FacePayTxnShardFetcher facePayFetcher;

    public ItpTxnExtractor(PayTxnDetailMapper payTxnDetailMapper, FacePayTxnShardFetcher facePayFetcher) {
        this.payTxnDetailMapper = payTxnDetailMapper;
        this.facePayFetcher = facePayFetcher;
    }

    public List<ItpTxnRecord> extract(String txnDate) {
        List<ItpTxnRecord> records = new ArrayList<>();
        List<ItpTxnRecord> paySign = payTxnDetailMapper.selectByTxnDate(txnDate);
        List<ItpTxnRecord> facePay = facePayFetcher.fetch(txnDate);
        records.addAll(paySign);
        records.addAll(facePay);
        log.info("ITP 侧交易抽取完成 txnDate={} paySign={} facePay={} 合计={}",
                txnDate, paySign.size(), facePay.size(), records.size());
        return records;
    }
}

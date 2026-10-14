package com.chinasofti.huateng.recon.thirdparty;

import com.chinasofti.huateng.recon.mapper.PayTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽取 ITP 侧当日交易（按自然日 TXN_DATE），作为第三方对账的 ITP 侧源。
 *
 * <p> Union 两流（recon-thirdparty.md §八）：
 * <ul>
 *   <li>pay-sign：PAY_TXN_DETAIL，覆盖 QR 后付费 / 日票（face-pay 不落此表）。recon-server 单数据源对
 *       PAY_TXN_DETAIL 有 SELECT 授权（用户决策 B：以支付台账为基），直接抽取。</li>
 *   <li>face-pay：本源数据来自 {@link FacePayTxnShardFetcher}——recon-server 单数据源对 F2F_ORDER /
 *       F2F_PAYMENT <b>无</b> SELECT 授权、且即将拆库（recon-thirdparty.md §八），故不能直连；改由
 *       face-pay-server 在对账抽取指令里把 F2F_ORDER JOIN F2F_PAYMENT(SUCCESS) 写成分片上送，
 *       fetcher 按账期拉回。关联键 merchantOrderNo=F2F_ORDER.ORDER_NO、channelTradeNo=F2F_PAYMENT.CHANNEL_ORDER_NO。</li>
 * </ul>
 * 两流各自按自然日取数，合并后由 TxnDiffReconService 与渠道账单做 FULL OUTER 比对。</p>
 */
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

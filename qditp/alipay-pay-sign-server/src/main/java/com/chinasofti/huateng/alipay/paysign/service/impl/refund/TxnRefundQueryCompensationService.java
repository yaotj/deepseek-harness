package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class TxnRefundQueryCompensationService {

    private static final Logger log = LoggerFactory.getLogger(TxnRefundQueryCompensationService.class);

    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private static final int SCAN_DAYS = 7;


    private static final int STALE_MINUTES = 5;

    private static final int BACKOFF_SECONDS = 300;

    private final AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper;
    private final TxnRefundCallbackSettler txnRefundCallbackSettler;
    private final PayCenterPort payCenterPort;

    @Value("${alipay.refund-query.batch-size:200}")
    private int batchSize;

    public TxnRefundQueryCompensationService(AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper,
                                            TxnRefundCallbackSettler txnRefundCallbackSettler,
                                            PayCenterPort payCenterPort) {
        this.alipayRefundTxnDetailMapper = alipayRefundTxnDetailMapper;
        this.txnRefundCallbackSettler = txnRefundCallbackSettler;
        this.payCenterPort = payCenterPort;
    }


    public OutboxScan.Result compensate() {
        List<AlipayRefundTxnDetail> pending =
                alipayRefundTxnDetailMapper.selectCompensableRefundQuery(SCAN_DAYS, STALE_MINUTES, batchSize);
        if (pending == null || pending.isEmpty()) {
            log.info("新表退款回查补偿：本轮无待处理行, scanDays={}, staleMinutes={}, batchSize={}",
                    SCAN_DAYS, STALE_MINUTES, batchSize);
            return new OutboxScan.Result(0, 0, 0);
        }
        OutboxScan.Result scan = OutboxScan.run(pending,
                this::settleByQuery,
                row -> log.warn("新表退款回查本轮未收口，已退避 {} 秒等下次重扫, orderNo={}, refundOrderNo={}",
                        BACKOFF_SECONDS, row.getOrderNo(), row.getRefundOrderNo()),
                (row, e) -> log.error("单条新表退款回查异常，NEVER 因此中断整批, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo(), e));
        log.info("新表退款回查补偿完成, scanned={}, settled={}, pendingAgain={}",
                scan.scanned(), scan.success(), scan.failed());
        return scan;
    }


    private boolean settleByQuery(AlipayRefundTxnDetail row) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", row.getRefundOrderNo());
        bizData.put("merchantRefundNo", row.getRefundOrderNo());

        PayCenterReply reply = payCenterPort.refundQuery(bizData);
        log.info("新表退款回查支付中心返回, orderNo={}, refundOrderNo={}, code={}, success={}, msg={}",
                row.getOrderNo(), row.getRefundOrderNo(), reply.code(), reply.success(), reply.msg());

        String settledStatus = resolveSettledStatus(row, reply);
        if (settledStatus == null) {
            delayNextQuery(row);
            return false;
        }
        return settle(row, settledStatus);
    }


    private String resolveSettledStatus(AlipayRefundTxnDetail row, PayCenterReply reply) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    log.error("新表退款回查业务应答非成功，本轮不收口，MUST 人工核对出网参数与网关码表, orderNo={}, refundOrderNo={}, retCode={}, retMsg={}",
                            row.getOrderNo(), row.getRefundOrderNo(), accepted.retCode(), accepted.retMsg());
                    return null;
                }
                String status = accepted.field("status");
                String normalized = PayCenterTradeStatus.normalize(status);
                if (normalized == null) {
                    log.info("新表退款回查未得终态，保持 PROCESSING, orderNo={}, refundOrderNo={}, status={}",
                            row.getOrderNo(), row.getRefundOrderNo(), status);
                }
                return normalized;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("新表退款回查未拿到业务应答（网关拒绝），保持 PROCESSING, orderNo={}, refundOrderNo={}, code={}, msg={}",
                        row.getOrderNo(), row.getRefundOrderNo(), rejected.code(), rejected.msg());
                return null;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("新表退款回查未拿到任何应答（地址未配置或网络不可达），保持 PROCESSING, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo());
                return null;
            }
        }
    }

    private boolean settle(AlipayRefundTxnDetail row, String settledStatus) {
        RefundCallbackSettler.Outcome outcome = txnRefundCallbackSettler.settle(row.getOrderNo(),
                row.getRefundOrderNo(), settledStatus, "退款回查收口为 " + settledStatus);
        return outcome == RefundCallbackSettler.Outcome.SETTLED_SUCCESS
                || outcome == RefundCallbackSettler.Outcome.SETTLED_FAIL;
    }


    private void delayNextQuery(AlipayRefundTxnDetail row) {
        int affected = alipayRefundTxnDetailMapper.delayNextRefundQuery(row.getRefundOrderNo(), BACKOFF_SECONDS);
        if (affected == 0) {
            log.info("新表退款回查退避未命中处理中明细，判定期间已被收口, orderNo={}, refundOrderNo={}",
                    row.getOrderNo(), row.getRefundOrderNo());
        }
    }
}

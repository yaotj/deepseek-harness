package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
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
public class RefundQueryCompensationService {

    private static final Logger log = LoggerFactory.getLogger(RefundQueryCompensationService.class);

    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private static final int SCAN_DAYS = 7;


    private static final int STALE_MINUTES = 5;

    private final RefundLogRepository refundLogRepository;
    private final PayCenterPort payCenterPort;

    @Value("${alipay.refund-query.batch-size:200}")
    private int batchSize;

    public RefundQueryCompensationService(RefundLogRepository refundLogRepository, PayCenterPort payCenterPort) {
        this.refundLogRepository = refundLogRepository;
        this.payCenterPort = payCenterPort;
    }


    public OutboxScan.Result compensate() {
        List<AlipayRefundLog> pending = refundLogRepository.scanCompensable(SCAN_DAYS, STALE_MINUTES, batchSize);
        if (pending == null || pending.isEmpty()) {
            log.info("退款回查补偿：本轮无待处理行, scanDays={}, staleMinutes={}, batchSize={}",
                    SCAN_DAYS, STALE_MINUTES, batchSize);
            return new OutboxScan.Result(0, 0, 0);
        }
        OutboxScan.Result scan = OutboxScan.run(pending,
                this::settleByQuery,
                row -> log.warn("退款回查本轮未收口，等下次重扫, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo()),
                (row, e) -> log.error("单条退款回查异常，NEVER 因此中断整批, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo(), e));
        log.info("退款回查补偿完成, scanned={}, settled={}, pendingAgain={}",
                scan.scanned(), scan.success(), scan.failed());
        return scan;
    }


    private boolean settleByQuery(AlipayRefundLog row) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", row.getRefundOrderNo());
        bizData.put("merchantRefundNo", row.getRefundOrderNo());

        PayCenterReply reply = payCenterPort.refundQuery(bizData);
        log.info("退款回查支付中心返回, orderNo={}, refundOrderNo={}, code={}, success={}, msg={}",
                row.getOrderNo(), row.getRefundOrderNo(), reply.code(), reply.success(), reply.msg());

        String settledStatus = resolveSettledStatus(row, reply);
        if (settledStatus == null) {
            return false;
        }
        return settle(row, settledStatus);
    }


    private String resolveSettledStatus(AlipayRefundLog row, PayCenterReply reply) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    log.error("退款回查业务应答非成功，本轮不收口，MUST 人工核对出网参数与网关码表, orderNo={}, refundOrderNo={}, retCode={}, retMsg={}",
                            row.getOrderNo(), row.getRefundOrderNo(), accepted.retCode(), accepted.retMsg());
                    return null;
                }
                String status = accepted.field("status");
                String normalized = PayCenterTradeStatus.normalize(status);
                if (normalized == null) {
                    log.info("退款回查未得终态，保持 PROCESSING, orderNo={}, refundOrderNo={}, status={}",
                            row.getOrderNo(), row.getRefundOrderNo(), status);
                }
                return normalized;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("退款回查未拿到业务应答（网关拒绝），保持 PROCESSING, orderNo={}, refundOrderNo={}, code={}, msg={}",
                        row.getOrderNo(), row.getRefundOrderNo(), rejected.code(), rejected.msg());
                return null;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("退款回查未拿到任何应答（地址未配置或网络不可达），保持 PROCESSING, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo());
                return null;
            }
        }
    }

    private boolean settle(AlipayRefundLog row, String settledStatus) {
        String resultMsg = "退款回查收口为 " + settledStatus;
        int affected = refundLogRepository.settleFromCallback(row.getRefundOrderNo(), settledStatus, resultMsg);
        if (affected == 0) {
            log.warn("退款回查收口 CAS 命中 0 行，已被回调或人工收口，本轮不重算汇总, orderNo={}, refundOrderNo={}, 回查状态={}",
                    row.getOrderNo(), row.getRefundOrderNo(), settledStatus);
            return false;
        }
        if (RefundLogRepository.REFUND_STATUS_SUCCESS.equals(settledStatus)) {
            refundLogRepository.refreshSummary(row.getOrderNo(), row.getRefundOrderNo());
        }
        log.info("退款回查已收口明细, orderNo={}, refundOrderNo={}, refundStatus={}",
                row.getOrderNo(), row.getRefundOrderNo(), settledStatus);
        return true;
    }
}

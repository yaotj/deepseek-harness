package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨域分叉核对（IT-081 P3）：扣费域已 SUCCESS 而支付域仍非终态时，说明两域口径已分叉。
 * 本类只「发现 + 告警」，NEVER 自动改支付域：补款收口场景下原单支付本就没有发生，
 * 擅自置 SUCCESS 会污染支付事实；直扣漏回调的场景由 pay-sign 的主动回查（P1）自行收口。
 */
@Service
public class CrossDomainDivergenceService {

    private static final Logger log = LoggerFactory.getLogger(CrossDomainDivergenceService.class);

    private static final int SCAN_DAYS = 2;

    private static final int RECENT_HOURS = 48;

    private static final int BATCH_SIZE = 100;

    private static final int QUERY_CHUNK = 50;

    private static final int REPORTED_CAPACITY = 5000;

    private static final String SUCCESS_CODE = "0000";

    private static final String SUPPLEMENT_MARK = "补款";

    private final GateTxnPayMapper gateTxnPayMapper;

    private final PaySignClient paySignClient;

    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public CrossDomainDivergenceService(GateTxnPayMapper gateTxnPayMapper, PaySignClient paySignClient) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.paySignClient = paySignClient;
    }


    public CompensateNotifyRespDTO check() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            String txnDateFrom = LocalDate.now().minusDays(SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<GateTxnPay> settled = gateTxnPayMapper.selectSettledForDivergenceCheck(
                    txnDateFrom, RECENT_HOURS, BATCH_SIZE);
            if (settled == null || settled.isEmpty()) {
                log.info("跨域分叉核对：近{}小时内无新收口的成功单", RECENT_HOURS);
                fillSuccess(response);
                return response;
            }

            Map<String, String> payStatusByOrderNo = queryPayStatus(settled);
            int aligned = 0;
            int diverged = 0;
            int unknown = 0;
            for (GateTxnPay row : settled) {
                String payStatus = payStatusByOrderNo.get(row.getOrderNo());
                if (!StringUtils.hasText(payStatus)) {
                    unknown++;
                    continue;
                }
                if ("SUCCESS".equals(payStatus)) {
                    aligned++;
                    continue;
                }
                diverged++;
                reportOnce(row, payStatus);
            }

            response.setScanned(settled.size());
            response.setSubmitted(aligned);
            response.setSkipped(diverged + unknown);
            log.info("跨域分叉核对完成, scanned={}, 一致={}, 分叉={}, 未取到支付域状态={}",
                    settled.size(), aligned, diverged, unknown);
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("跨域分叉核对异常", e);
            response.setResultCode("9999");
            response.setResultMsg("跨域分叉核对异常：" + e.getMessage());
            return response;
        }
    }


    private Map<String, String> queryPayStatus(List<GateTxnPay> rows) {
        Map<String, String> result = new HashMap<>();
        List<String> orderNos = new ArrayList<>(rows.size());
        for (GateTxnPay row : rows) {
            orderNos.add(row.getOrderNo());
        }
        for (int from = 0; from < orderNos.size(); from += QUERY_CHUNK) {
            List<String> chunk = orderNos.subList(from, Math.min(from + QUERY_CHUNK, orderNos.size()));
            QueryPayTxnBatchReqDTO request = new QueryPayTxnBatchReqDTO();
            request.setOrderNos(new ArrayList<>(chunk));
            RequestPayTxnBatchResult batch;
            try {
                batch = paySignClient.queryPayTxnBatch(request);
            } catch (Exception e) {
                log.error("跨域分叉核对：查询支付域明细异常，本批按未取到状态处理, size={}", chunk.size(), e);
                continue;
            }
            if (batch == null || !SUCCESS_CODE.equals(batch.getRetCode())) {
                log.error("跨域分叉核对：支付域批量查询未成功，本批按未取到状态处理, size={}, retCode={}, retMsg={}",
                        chunk.size(), batch == null ? null : batch.getRetCode(),
                        batch == null ? null : batch.getRetMsg());
                continue;
            }
            List<PayTxnDetailDTO> details = batch.getPayTxnDetailList() != null
                    ? batch.getPayTxnDetailList() : batch.getData();
            if (details == null) {
                continue;
            }
            for (PayTxnDetailDTO detail : details) {
                if (detail != null && StringUtils.hasText(detail.getOrderNo())) {
                    result.put(detail.getOrderNo(), detail.getPayStatus());
                }
            }
        }
        return result;
    }


    private void reportOnce(GateTxnPay row, String payStatus) {
        if (reported.size() > REPORTED_CAPACITY) {
            reported.clear();
        }
        if (!reported.add(row.getOrderNo())) {
            return;
        }
        String remark = row.getRemark() == null ? "" : row.getRemark();
        if (remark.contains(SUPPLEMENT_MARK)) {
            log.warn("跨域口径差异（补款收口）：扣费域 SUCCESS 而支付域={}，原单支付本未发生，"
                            + "对账以上口径为准, orderNo={}, txnDate={}, remark={}",
                    payStatus, row.getOrderNo(), row.getTxnDate(), remark);
            return;
        }
        log.error("跨域分叉：扣费域 SUCCESS 而支付域={}，MUST 人工核对, orderNo={}, txnDate={}, remark={}",
                payStatus, row.getOrderNo(), row.getTxnDate(), remark);
    }


    private void fillSuccess(CompensateNotifyRespDTO response) {
        response.setResultCode(SUCCESS_CODE);
        response.setResultMsg("成功");
    }
}

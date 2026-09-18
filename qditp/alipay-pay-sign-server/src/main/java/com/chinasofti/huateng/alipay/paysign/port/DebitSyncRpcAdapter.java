package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link DebitSyncPort} 的唯一实现：rpc DTO 装配 + {@code retCode} → {@link RpcOutcome} 翻译（ADR-D131）。
 *
 * <p>映射规则：{@code retCode == "0000"} → {@code Ok}；对端答了别的码 → {@code BizRejected}；
 * 响应为 {@code null} 或抛异常 → {@code Unreachable}。</p>
 */
@Component
public class DebitSyncRpcAdapter implements DebitSyncPort {

    private static final Logger log = LoggerFactory.getLogger(DebitSyncRpcAdapter.class);

    private final GateTxnPayClient gateTxnPayClient;

    public DebitSyncRpcAdapter(GateTxnPayClient gateTxnPayClient) {
        this.gateTxnPayClient = gateTxnPayClient;
    }

    @Override
    public RpcOutcome syncDebitStatus(String orderNo, String payStatus, String remark) {
        GateTxnPaySyncStatusReqDTO request = new GateTxnPaySyncStatusReqDTO();
        request.setOrderNo(orderNo);
        request.setPayStatus(payStatus);
        request.setRemark(remark);
        try {
            GateTxnPayRespDTO response = gateTxnPayClient.syncDebitStatus(request);
            if (response == null) {
                // 空响应体等于「没答上」，与业务拒绝不同：可重试。
                log.error("扣费订单状态同步无响应体（可重试）, orderNo={}, payStatus={}", orderNo, payStatus);
                return new RpcOutcome.Unreachable(new IllegalStateException("gate-txn-pay 响应为空"));
            }
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            log.error("扣费订单状态同步未获答复（可重试）, orderNo={}, payStatus={}", orderNo, payStatus, e);
            return new RpcOutcome.Unreachable(e);
        }
    }
}

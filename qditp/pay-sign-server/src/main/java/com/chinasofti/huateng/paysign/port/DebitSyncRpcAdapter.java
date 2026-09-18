package com.chinasofti.huateng.paysign.port;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@link DebitSyncPort} 的唯一实现：rpc DTO 装配 + {@code retCode} → {@link RpcOutcome} 翻译。 */
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
                log.error("扣费状态同步：闸机域响应为空, orderNo={}, payStatus={}", orderNo, payStatus);
                return new RpcOutcome.BizRejected(null, "闸机域响应为空");
            }
            log.info("扣费状态同步：闸机域已答复, orderNo={}, response={}", orderNo, JSON.toJSONString(response));
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            log.error("扣费状态同步未获业务答复（可重试）, orderNo={}, payStatus={}", orderNo, payStatus, e);
            return new RpcOutcome.Unreachable(e);
        }
    }
}

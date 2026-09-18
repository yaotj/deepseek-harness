package com.chinasofti.huateng.paysign.port;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link UnsettledOrderPort} 的唯一实现。
 *
 * <p>注意闸机域这个端点用的是**形状 C**（{@code resultCode} / {@code resultMsg}），
 * 不是本项目多数内部接口的 {@code retCode} —— 所以这里 NEVER 复用 {@code RpcOutcome.ofRetCode}。
 */
@Component
public class UnsettledOrderRpcAdapter implements UnsettledOrderPort {

    private static final Logger log = LoggerFactory.getLogger(UnsettledOrderRpcAdapter.class);

    /** 闸机域成功码，与本模块对外应答复用同一个枚举值，NEVER 写成裸字面量。 */
    private static final String SUCCESS_CODE = PaySignErrorCodeEnum.SUCCESS.getCode();

    private final GateTxnPayClient gateTxnPayClient;

    public UnsettledOrderRpcAdapter(GateTxnPayClient gateTxnPayClient) {
        this.gateTxnPayClient = gateTxnPayClient;
    }

    @Override
    public UnsettledOrderAnswer hasUnsettledOrder(String thirdUserId, String paymentVendor,
                                                 LocalDateTime requestTime) {
        GateTxnPayFailedOrderReqDTO request = new GateTxnPayFailedOrderReqDTO();
        request.setThirdUserId(thirdUserId);
        request.setPaymentVendor(paymentVendor);
        request.setRequestTime(requestTime);
        try {
            GateTxnPayFailedOrderRespDTO response = gateTxnPayClient.hasFailedOrder(request);
            if (response == null) {
                log.error("未结清扣费订单查询：闸机域响应为空, thirdUserId={}, paymentVendor={}",
                        thirdUserId, paymentVendor);
                return new UnsettledOrderAnswer.Rejected(null, "闸机域响应为空");
            }
            if (!SUCCESS_CODE.equals(response.getResultCode())) {
                log.error("未结清扣费订单查询未成功，MUST 按「问不出来」处置, thirdUserId={}, response={}",
                        thirdUserId, JSON.toJSONString(response));
                return new UnsettledOrderAnswer.Rejected(response.getResultCode(), response.getResultMsg());
            }
            return new UnsettledOrderAnswer.Answered(response.isHasFailedOrder());
        } catch (Exception e) {
            log.error("未结清扣费订单查询未获业务答复, thirdUserId={}, paymentVendor={}",
                    thirdUserId, paymentVendor, e);
            return new UnsettledOrderAnswer.Unknown(e);
        }
    }
}

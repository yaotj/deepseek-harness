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


@Component
public class UnsettledOrderRpcAdapter implements UnsettledOrderPort {

    private static final Logger log = LoggerFactory.getLogger(UnsettledOrderRpcAdapter.class);

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

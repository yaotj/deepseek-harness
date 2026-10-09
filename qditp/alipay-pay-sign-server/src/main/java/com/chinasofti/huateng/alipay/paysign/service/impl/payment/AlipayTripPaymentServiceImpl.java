package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTripPaymentService;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 支付宝出行支付的**跨聚合门面**：八个方法分别转发给支付申请 / 查询 / 退款 / 通知四类实现。
 *
 * <p><b>2026-09-21 起本类全仓零调用方</b>（拆门面第 1 步）：最后两个调用方
 * {@code controller/legacy/AlipayNotifyController} 与 {@code controller/legacy/AlipayTripPaymentController}
 * 已分别改为直注 {@link com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter}
 * 与 {@link PaymentQueryService}；此前另外几条（requestPay / payQuery / requestRefund / 两个 notify）
 * 早在 2026-09-18 的迁移里就已各自切到新实现。
 *
 * <p><b>按用户裁决保留、不删</b>：它与 {@link PaymentRequestService} / {@link PaymentRefundService}
 * 一起构成旧链路的回滚位（各 controller 的回滚位注释块都指向这里）。
 * <b>NEVER 在本类上新增能力</b>，也 NEVER 把任何 controller 改回注它。
 */
@Service
public class AlipayTripPaymentServiceImpl implements AlipayTripPaymentService {
    private static final Logger log = LoggerFactory.getLogger(AlipayTripPaymentServiceImpl.class);

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentRefundService paymentRefundService;

    @Autowired
    private PaymentQueryService paymentQueryService;

    @Autowired
    private PaymentNotifyAdapter paymentNotifyAdapter;

    @Override
    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        return paymentRequestService.requestPay(request);
    }

    @Override
    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        return paymentRefundService.requestRefund(request);
    }

    @Override
    public AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request) {
        return paymentQueryService.payQuery(request);
    }

    @Override
    public AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request) {
        return paymentQueryService.handlePayNotify(request);
    }

    @Override
    public AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request) {
        return paymentRefundService.handleRefundNotify(request);
    }

    @Override
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        return paymentQueryService.findTravelDetail(request);
    }

    @Override
    public AlipayCommonResponse notifyBlackListChange(AlipayBlackListNotifyReqDTO request) {
        return paymentNotifyAdapter.notifyBlackListChange(request);
    }

    @Override
    public AlipayCommonResponse notifyCloseResult(String agreementCode, boolean result) {
        return paymentNotifyAdapter.notifyCloseResult(agreementCode, result);
    }
}

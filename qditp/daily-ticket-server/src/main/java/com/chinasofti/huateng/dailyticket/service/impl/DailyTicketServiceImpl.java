package com.chinasofti.huateng.dailyticket.service.impl;

import com.chinasofti.huateng.dailyticket.page.TravelTicketSubRefundRequest;
import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
import com.chinasofti.huateng.dailyticket.service.lifecycle.DailyTicketInstanceLifecycleService;
import com.chinasofti.huateng.dailyticket.service.order.DailyTicketOrderCreationService;
import com.chinasofti.huateng.dailyticket.service.payment.DailyTicketPaymentService;
import com.chinasofti.huateng.dailyticket.service.refund.DailyTicketRefundCallbackService;
import com.chinasofti.huateng.dailyticket.service.refund.DailyTicketRefundInitiationService;
import com.chinasofti.huateng.dailyticket.service.refund.RefundProgressService;
import com.chinasofti.huateng.dailyticket.service.refund.TravelSubRefundService;
import com.chinasofti.huateng.dailyticket.service.sync.DailyTicketOrderSyncService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketSyncOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import org.springframework.stereotype.Service;

/** 日票业务服务默认实现。 */
@Service
public class DailyTicketServiceImpl implements DailyTicketService {

    private final DailyTicketRefundInitiationService refundInitiationService;
    private final DailyTicketRefundCallbackService refundCallbackService;
    private final TravelSubRefundService travelSubRefundService;
    private final RefundProgressService refundProgressService;
    private final DailyTicketInstanceLifecycleService lifecycleService;
    private final DailyTicketOrderSyncService orderSyncService;
    private final DailyTicketPaymentService paymentService;
    private final DailyTicketOrderCreationService orderCreationService;

    public DailyTicketServiceImpl(DailyTicketRefundInitiationService refundInitiationService,
                                  DailyTicketRefundCallbackService refundCallbackService,
                                  TravelSubRefundService travelSubRefundService,
                                  RefundProgressService refundProgressService,
                                  DailyTicketInstanceLifecycleService lifecycleService,
                                  DailyTicketOrderSyncService orderSyncService,
                                  DailyTicketPaymentService paymentService,
                                  DailyTicketOrderCreationService orderCreationService) {
        this.refundInitiationService = refundInitiationService;
        this.refundCallbackService = refundCallbackService;
        this.travelSubRefundService = travelSubRefundService;
        this.refundProgressService = refundProgressService;
        this.lifecycleService = lifecycleService;
        this.orderSyncService = orderSyncService;
        this.paymentService = paymentService;
        this.orderCreationService = orderCreationService;
    }

    /** IF8A-70 旅游票下单。 */
    @Override
    public TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request) {
        return orderCreationService.requestTravelOrder(request);
    }

    @Override
    public DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request) {
        return orderCreationService.requestCountingOrder(request);
    }

    /** IF8A-73 免费票请求下单。 */
    @Override
    public DailyTicketFreeOrderResult requestOrderFree(DailyTicketFreeOrderReqDTO request) {
        return orderCreationService.requestOrderFree(request);
    }

    /** IF8A-72 小程序票状态同步。 */
    @Override
    public DailyTicketBaseResult syncOrder(DailyTicketSyncOrderReqDTO request) {
        return orderSyncService.syncOrder(request);
    }

    @Override
    public DailyTicketPayResult requestPay(DailyTicketPayReqDTO request) {
        return paymentService.requestPay(request);
    }

    @Override
    public DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request) {
        return paymentService.requestPayResult(request);
    }

    @Override
    public DailyTicketPayQueryResult queryPayTicket(DailyTicketOrderNoReqDTO request) {
        return paymentService.queryPayTicket(request);
    }

    @Override
    public DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request) {
        return refundInitiationService.requestRefundTicket(request);
    }

    @Override
    public DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request) {
        return refundProgressService.queryRefundTicket(request);
    }

    @Override
    public DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request) {
        return refundProgressService.retryRefundTicket(request);
    }

    @Override
    public DailyTicketRefundResult resubmitRefundTicket(DailyTicketOrderNoReqDTO request) {
        return refundProgressService.resubmitRefundTicket(request);
    }

    @Override
    public DailyTicketRefundResult requestTravelSubRefund(TravelTicketSubRefundRequest request) {
        return travelSubRefundService.requestTravelSubRefund(request);
    }

    @Override
    public DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request) {
        return orderCreationService.cancelOrder(request);
    }

    /** 激活日票（IF8A-32）。 */
    @Override
    public DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request) {
        return lifecycleService.updateTicket(request);
    }

    /** APP 首次使用通知（IF8A-33）：写入有效期截止时间并置「已开始使用」。 */
    @Override
    public DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request) {
        return lifecycleService.updateAndNotice(request);
    }

    @Override
    public DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request) {
        return paymentService.receivePayResult(request);
    }

    /** 支付中心退款结果回调收口（网关文档 §5.2），实现已迁至 {@link DailyTicketRefundCallbackService}。 */
    @Override
    public DailyTicketBaseResult receiveRefundResult(DailyTicketRefundCallbackReqDTO request) {
        return refundCallbackService.receiveRefundResult(request);
    }

    @Override
    public QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request) {
        return lifecycleService.queryDailyTicketInfo(request);
    }

    /** 按票号查日票的购票支付信息，供 IF8A-34 / IF8A-05 交易详情填充三个支付字段。 */
    @Override
    public QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(QueryDailyTicketPayInfoReqDTO request) {
        return paymentService.queryDailyTicketPayInfo(request);
    }

    @Override
    public DailyTicketBaseResult validateEntryCheck(String cardNum) {
        return lifecycleService.validateEntryCheck(cardNum);
    }

    /**
     * 拉码（IF8A-03）前置的只读可用性判定。
     * 判据与 {@link #validateEntryCheck} 同源（有效期 / 次数 / 退款占用），但只读、不推进状态，
     * 且异常一律吞掉转成 retCode，因为调用方按「不可达即降级放行」处置。
     */
    @Override
    public DailyTicketBaseResult checkRideAvailability(String cardNum) {
        return lifecycleService.checkRideAvailability(cardNum);
    }

    /** 出站处理：计次票扣次、写入出站时间。 */
    @Override
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd) {
        return lifecycleService.markUsed(cardNum, countingEnd);
    }

    @Override
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd,
                                          String orderNo, String inStation, String outStation) {
        return lifecycleService.markUsed(cardNum, countingEnd, orderNo, inStation, outStation);
    }

    @Override
    public DailyTicketBaseResult queryUsageLog(String cardNum) {
        return lifecycleService.queryUsageLog(cardNum);
    }
}

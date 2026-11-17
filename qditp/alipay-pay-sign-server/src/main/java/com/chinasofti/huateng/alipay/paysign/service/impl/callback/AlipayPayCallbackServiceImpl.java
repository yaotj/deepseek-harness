package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayCallbackService;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.RefundCallbackSettler;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.TxnRefundCallbackSettler;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class AlipayPayCallbackServiceImpl implements AlipayPayCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayCallbackServiceImpl.class);

    private static final int MAX_PAY_CALLBACK_PUSH = 2;

    private final CallbackLogRepository callbackLogRepository;
    private final PayTxnCallbackWriter payTxnCallbackWriter;
    private final DebitSyncPort debitSyncPort;
    private final RefundCallbackSettler refundCallbackSettler;
    private final TxnRefundCallbackSettler txnRefundCallbackSettler;

    public AlipayPayCallbackServiceImpl(CallbackLogRepository callbackLogRepository,
                                       PayTxnCallbackWriter payTxnCallbackWriter,
                                       DebitSyncPort debitSyncPort,
                                       RefundCallbackSettler refundCallbackSettler,
                                       TxnRefundCallbackSettler txnRefundCallbackSettler) {
        this.callbackLogRepository = callbackLogRepository;
        this.payTxnCallbackWriter = payTxnCallbackWriter;
        this.debitSyncPort = debitSyncPort;
        this.refundCallbackSettler = refundCallbackSettler;
        this.txnRefundCallbackSettler = txnRefundCallbackSettler;
    }

    @Override
    public AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request) {
        log.info("接收到支付宝支付结果回调, 原始报文: {}", JSON.toJSONString(request));
        return switch (PayNotifyCommand.from(request)) {
            case PayNotifyCommand.MissingOrderNo ignored -> {
                log.warn("支付宝支付回调报文为空或订单号为空");
                yield response(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "参数异常：orderNo不能为空");
            }
            case PayNotifyCommand.IllegalTransStatus illegal -> {
                log.error("支付宝支付回调 transStatus 不在白名单内，拒绝处理、MUST 人工核对报文契约, orderNo={}, transStatus={}",
                        illegal.orderNo(), illegal.rawTransStatus());
                callbackLogRepository.recordPayCallback(request, CallbackLogRepository.HANDLE_STATUS_MANUAL,
                        "transStatus 非法: " + illegal.rawTransStatus());
                yield response(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "参数异常：transStatus 取值非法");
            }
            case PayNotifyCommand.Accepted accepted -> onAccepted(request, accepted);
        };
    }


    private AlipayCommonResponse onAccepted(AlipayTripPayNotifyReqDTO request, PayNotifyCommand.Accepted accepted) {
        String callbackSeq = callbackLogRepository.recordPayCallback(request,
                CallbackLogRepository.HANDLE_STATUS_PROCESSING, null);
        int pushCount = callbackLogRepository.countPayPush(accepted.orderNo());

        log.info("支付宝支付回调,准备同步扣费订单状态, orderNo={}, transStatus={}, payStatus={}, channelVoucherId={}, transTime={}, transAmount={}, pushCount={}",
                accepted.orderNo(), accepted.rawTransStatus(), accepted.payStatus(),
                accepted.channelVoucherId(), accepted.transTime(), accepted.transAmount(), pushCount);

        payTxnCallbackWriter.applyCallback(accepted.orderNo(), accepted.payStatus(), accepted.channelVoucherId(),
                accepted.transTime());

        if (!syncGateTxnPayStatus(accepted.orderNo(), accepted.payStatus())) {
            if (pushCount >= MAX_PAY_CALLBACK_PUSH) {
                log.error("支付宝支付回调已推送{}次仍未处理成功，达到上限{}，放弃重推并返回0000，MUST 人工处理, orderNo={}, 原因={}",
                        pushCount, MAX_PAY_CALLBACK_PUSH, accepted.orderNo(), "扣费订单状态同步失败");
                callbackLogRepository.markPayManual(accepted.orderNo(), "扣费订单状态同步失败");
                return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
            }
            callbackLogRepository.updateHandleResult(callbackSeq, CallbackLogRepository.HANDLE_STATUS_FAIL,
                    "扣费订单状态同步失败，待支付中心重推");
            return response(FepAppErrorCodeEnum.FAIL.getCode(), "扣费订单状态同步失败");
        }

        callbackLogRepository.updateHandleResult(callbackSeq, CallbackLogRepository.HANDLE_STATUS_SUCCESS, null);
        log.info("支付宝支付回调处理完成, orderNo={}, payStatus={}", accepted.orderNo(), accepted.payStatus());
        return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
    }

    @Override
    public AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request) {
        log.info("接收到支付宝退款结果回调, 原始报文: {}", JSON.toJSONString(request));
        return switch (RefundNotifyCommand.from(request)) {
            case RefundNotifyCommand.MissingOrderNo ignored -> {
                log.warn("支付宝退款回调报文为空或订单号为空");
                yield response(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "参数异常：orderNo不能为空");
            }
            case RefundNotifyCommand.Accepted accepted -> onRefundAccepted(request, accepted);
        };
    }


    private AlipayCommonResponse onRefundAccepted(AlipayTripRefundNotifyReqDTO request,
                                                  RefundNotifyCommand.Accepted accepted) {
        String callbackSeq = callbackLogRepository.recordRefundCallback(request, accepted,
                CallbackLogRepository.HANDLE_STATUS_PROCESSING, null);
        log.info("支付宝退款回调已留证据，准备收口退款明细, orderNo={}, refundNo={}, outRefundNo={}, refundResult={}, refundAmount={}",
                accepted.orderNo(), request.getRefundNo(), accepted.refundOrderNo(), accepted.refundResult(),
                accepted.refundAmount());

        RefundCallbackSettler.Outcome outcome = refundCallbackSettler.settle(accepted.orderNo(),
                accepted.refundOrderNo(), accepted.refundResult(), request.getRefundResultDesc());
        if (outcome == RefundCallbackSettler.Outcome.NOT_MATCHED) {
            outcome = txnRefundCallbackSettler.settle(accepted.orderNo(), accepted.refundOrderNo(),
                    accepted.refundResult(), request.getRefundResultDesc());
        }

        switch (outcome) {
            case SETTLED_SUCCESS -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_SUCCESS, "退款明细已收口为 SUCCESS，汇总已重算");
            case SETTLED_FAIL -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_SUCCESS, "退款明细已收口为 FAIL");
            case STILL_PROCESSING -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_PROCESSING, "退款仍处理中，等待终态回调");
            case NOT_MATCHED -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_SUCCESS, "未命中处理中明细，按幂等处理（重推或已收口）");
            case UNKNOWN_RESULT -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_MANUAL,
                    "refundResult 取值不在契约内: " + accepted.refundResult());
        }
        return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
    }



    private boolean syncGateTxnPayStatus(String orderNo, String payStatus) {
        RpcOutcome outcome = debitSyncPort.syncDebitStatus(orderNo, payStatus, "支付宝出行扣费结果回调");
        switch (outcome) {
            case RpcOutcome.Ok ok -> {
                log.info("扣费订单状态同步成功, orderNo={}, payStatus={}", orderNo, payStatus);
                return true;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("扣费订单状态同步被业务拒绝，待支付中心重推, orderNo={}, payStatus={}, retCode={}, retMsg={}",
                        orderNo, payStatus, rejected.retCode(), rejected.retMsg());
                return false;
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("扣费订单状态同步未获答复，待支付中心重推, orderNo={}, payStatus={}, cause={}",
                        orderNo, payStatus, unreachable.cause().getClass().getSimpleName());
                return false;
            }
        }
    }

    private AlipayCommonResponse response(String retCode, String retMsg) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}

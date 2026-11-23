package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.constant.DiscountCalcStatus;
import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayFieldCode;
import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareCalculator;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundOvertimeRequest;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundResult;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.gatetxnpay.station.StationNameBackfiller;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.Executor;

@Service
public class GateTxnPayServiceImpl implements GateTxnPayService {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayServiceImpl.class);
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final Set<String> BOM_SUPPLEMENT_ADVICE_OPTS = Set.of("005", "006", "020");

    private final GateTxnPayMapper gateTxnPayMapper;
    private final GateTxnPayWriter gateTxnPayWriter;

    private final FareCalculator fareCalculator;

    private final PaySignInitiator paySignInitiator;

    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    private final Executor paySignAsyncExecutor;
    private final GateTxnPayManualOpsService manualOpsService;

    private final StationNameBackfiller stationNameBackfiller;

    public GateTxnPayServiceImpl(
            GateTxnPayMapper gateTxnPayMapper,
            GateTxnPayWriter gateTxnPayWriter,
            FareCalculator fareCalculator,
            PaySignInitiator paySignInitiator,
            MetroTransferPushTaskProcessor metroTransferPushTaskProcessor,
            GateTxnPayManualOpsService manualOpsService,
            StationNameBackfiller stationNameBackfiller,
            @org.springframework.beans.factory.annotation.Qualifier("paySignAsyncExecutor") Executor paySignAsyncExecutor) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.gateTxnPayWriter = gateTxnPayWriter;
        this.fareCalculator = fareCalculator;
        this.paySignInitiator = paySignInitiator;
        this.metroTransferPushTaskProcessor = metroTransferPushTaskProcessor;
        this.manualOpsService = manualOpsService;
        this.stationNameBackfiller = stationNameBackfiller;
        this.paySignAsyncExecutor = paySignAsyncExecutor;
    }

    @Override
    public GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        String validMsg = validate(request);
        if (validMsg != null) {
            response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
            response.setRetMsg(validMsg);
            return response;
        }

        GateTxnPay order = buildOrder(request);
        try {
            if (isOfflineExit(request)) {
                fareCalculator.calculateOfflineFare(order, request);
            } else {
                fareCalculator.fillOriginalFare(order);
                fareCalculator.calculateWalletDiscount(order, request);
            }
        } catch (RuntimeException e) {
            if (!isOfflineExit(request)) {
                throw e;
            }
            String reason = StringUtils.hasText(e.getMessage()) ? e.getMessage() : "离线码金额计算失败";
            log.error("离线码出站金额计算失败，落单留痕待补偿，NEVER 按闸机金额扣款, cardId={}, seq={}",
                    request.getCardId(), request.getTicketTransSeq(), e);
            return saveOfflineFarePendingOrder(order, reason);
        }
        stationNameBackfiller.backfill(order);
        order.setTotalAmount((order.getTrxAmount() == null ? 0 : order.getTrxAmount())
                + (order.getOvertimeAmount() == null ? 0 : order.getOvertimeAmount()));
        MetroTransferPushTask pushTask = metroTransferPushTaskProcessor.buildMetroTransferPushTask(order);

        if (isDailyTicket(order) || order.getTotalAmount() <= 0 || isBomSupplement(order)) {
            String reason;
            if (isDailyTicket(order)) {
                reason = "日票交易默认支付成功";
            } else if (isBomSupplement(order)) {
                reason = "BOM补站现场已收款，不由ITP扣款";
            } else {
                reason = "免扣费交易默认支付成功";
            }
            order.setExpectedGateAmount(0);
            if (!StringUtils.hasText(order.getDiscountCalcStatus())) {
                order.setDiscountCalcStatus(DiscountCalcStatus.SKIPPED.code());
                order.setDiscountCalcMsg(reason + "，不参与钱包折扣计算");
            }
            if (isBomSupplement(order)) {
                String registerFailure = paySignInitiator.registerCompletedTxnForBomSupplement(order, request, reason);
                if (registerFailure != null) {
                    response.setRetCode(GateTxnPayRetCode.ORDER_PERSIST_FAILED);
                    response.setRetMsg(registerFailure);
                    log.error("BOM补站单未能在支付域登记支付流水，本笔不落单、返错给上游, orderNo={}, cardId={}, msg={}",
                            order.getOrderNo(), order.getCardId(), registerFailure);
                    return response;
                }
            }
            GateTxnPay savedOrder = gateTxnPayWriter.insertOrderAndUpdateStatusWithMetroTransferPushTask(
                    order, DebitStatus.SUCCESS.code(), reason, pushTask);
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("成功");
            response.setOrderNo(savedOrder.getOrderNo());
            response.setPayStatus(DebitStatus.SUCCESS.code());
            log.info("过闸交易已入库并默认支付成功，不调用pay-sign, orderNo={}, cardId={}, cardType={}, totalAmount={}, originalFare={}",
                    savedOrder.getOrderNo(), savedOrder.getCardId(), savedOrder.getCardType(),
                    savedOrder.getTotalAmount(), savedOrder.getOriginalFare());
            return response;
        }

        GateTxnPay savedOrder = gateTxnPayWriter.insertOrderWithMetroTransferPushTask(order, pushTask);
        if (savedOrder == null || savedOrder.getOrderNo() == null) {
            response.setRetCode(GateTxnPayRetCode.ORDER_PERSIST_FAILED);
            response.setRetMsg("订单入库失败");
            return response;
        }

        if (!savedOrder.getOrderNo().equals(order.getOrderNo())) {
            log.warn("并发重复请求，订单已存在，跳过重复触发pay-sign, orderNo={}, existingStatus={}",
                    savedOrder.getOrderNo(), savedOrder.getDebitStatus());
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("成功");
            response.setOrderNo(savedOrder.getOrderNo());
            response.setPayStatus(savedOrder.getDebitStatus());
            return response;
        }

        paySignAsyncExecutor.execute(() -> paySignInitiator.initiateAsync(savedOrder, request));

        response.setRetCode(GateTxnPayRetCode.SUCCESS);
        response.setRetMsg("成功");
        response.setOrderNo(savedOrder.getOrderNo());
        response.setPayStatus(DebitStatus.PROCESSING.code());
        return response;
    }

    private static final String OFFLINE_FARE_PENDING = DiscountCalcStatus.OFFLINE_FARE_PENDING.code();


    private GateTxnPayRespDTO saveOfflineFarePendingOrder(GateTxnPay order, String reason) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        response.setRetCode(GateTxnPayRetCode.ORDER_PERSIST_FAILED);
        response.setRetMsg(reason);
        order.setDebitStatus(DebitStatus.INIT.code());
        order.setTrxAmount(0);
        order.setOvertimeAmount(0);
        order.setTotalAmount(0);
        order.setDiscountCalcStatus(OFFLINE_FARE_PENDING);
        order.setDiscountCalcMsg(truncate("离线码金额待重算：" + reason, 500));
        try {
            GateTxnPay saved = gateTxnPayWriter.insertOrder(order);
            response.setOrderNo(saved.getOrderNo());
            response.setPayStatus(saved.getDebitStatus());
            log.warn("离线码订单已落待重算态，等待补偿重算金额与扣款, orderNo={}, cardId={}, seq={}",
                    saved.getOrderNo(), saved.getCardId(), saved.getTicketTransSeq());
        } catch (RuntimeException ex) {
            log.error("离线码待重算订单落库失败，本次出站在 GATE_TXN_PAY 无任何痕迹, cardId={}, seq={}",
                    order.getCardId(), order.getTicketTransSeq(), ex);
        }
        return response;
    }

    @Override
    public GateTxnPayRespDTO retryPay(String orderNo) {
        return manualOpsService.retryPay(orderNo);
    }

    @Override
    public ResultVO<RequestRefundResult> requestRefund(String orderNo, GateTxnPayRefundRequest request) {
        return manualOpsService.requestRefund(orderNo, request);
    }

    @Override
    public ResultVO<BatchRefundResult> batchRefundOvertime(BatchRefundOvertimeRequest request) {
        return manualOpsService.batchRefundOvertime(request);
    }

    private String validate(GateTxnPayReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!GateTxnPayFieldCode.isExitTrxType(request.getTrxType())) {
            return "非出站扣费交易";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getHandleDateTime()) || request.getHandleDateTime().length() < 8) {
            return "handleDateTime不能为空且长度不能小于8";
        }
        return null;
    }

    private GateTxnPay buildOrder(GateTxnPayReqDTO request) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo(buildOrderNo(request));
        order.setDebitStatus(DebitStatus.INIT.code());
        order.setThirdUserId(request.getItpUserId());
        order.setCardId(request.getCardId());
        order.setCardType(request.getCardType());
        order.setDeviceId(request.getDeviceId());
        order.setTrxType(request.getTrxType());
        order.setTicketTransSeq(request.getTicketTransSeq());
        order.setInStation(request.getLastHandleStationCode());
        order.setInTime(request.getLastHandleDateTime());
        order.setOutStation(request.getHandleStationCode());
        order.setOutTime(request.getHandleDateTime());
        order.setTxnDate(request.getHandleDateTime().substring(0, 8));
        order.setTrxAmount(parseAmount(request.getTrxAmount()));
        order.setOvertimeAmount(parseAmount(request.getOvertimeAmount()));
        order.setTotalAmount(order.getTrxAmount() + order.getOvertimeAmount());
        order.setIssueChannelCode(request.getIssueChannelCode());
        order.setSignChannelCode(request.getSignChannelCode());
        order.setPaymentVendor(trimToNull(request.getPaymentVendor()));
        order.setChannelType(trimToNull(request.getChannelType()));
        order.setPayUserId(trimToNull(request.getPayUserId()));
        order.setIndustryDetail(truncate(trimToNull(request.getIndustryDetail()), 4000));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setTicketStatus(request.getTicketStatus());
        order.setOrderExpType(request.getOrderExpType());
        order.setAdviceOpt(trimToNull(request.getAdviceOpt()));
        order.setEntryStationName(request.getEntryStationName());
        order.setExitStationName(request.getExitStationName());
        order.setCompanionFlag(request.getCompanionFlag());
        order.setOfflineFlag(request.getOfflineFlag());
        order.setTicketCode(request.getTicketCode());
        boolean dailyTicketOrder = CardTypeCodeEnum.isDailyTicket(order.getCardType());
        order.setCountingTimes(request.getCountingTimes() != null
                ? request.getCountingTimes()
                : (dailyTicketOrder ? 1 : 0));
        order.setCountingFlag(StringUtils.hasText(request.getCountingFlag())
                ? request.getCountingFlag()
                : (dailyTicketOrder ? "Y" : "N"));
        order.setAttributableParty(request.getAttributableParty());
        order.setReceivingParty(request.getReceivingParty());
        log.info("IF1A-01 构建 GateTxnPay 订单快照, cardId={}, orderNo={}, ticketStatus={}, orderExpType={}, adviceOpt={}, offlineFlag={}, companionFlag={}, ticketCode={}, countingTimes={}, countingFlag={}, attributableParty={}, receivingParty={}, entryStationName={}, exitStationName={}",
                request.getCardId(), order.getOrderNo(), order.getTicketStatus(),
                order.getOrderExpType(), order.getAdviceOpt(), order.getOfflineFlag(),
                order.getCompanionFlag(), order.getTicketCode(), order.getCountingTimes(),
                order.getCountingFlag(), order.getAttributableParty(), order.getReceivingParty(),
                order.getEntryStationName(), order.getExitStationName());
        
        // 监控日志：记录所有出站订单的 THIRD_USER_ID 与 CARD_ID 映射，便于排查数据不一致
        log.warn("IF1A-01-MONITOR 用户-卡号映射监控, thirdUserId={}, cardId={}, orderNo={}, deviceNo={}, inStation={}, outStation={}",
                order.getThirdUserId(), order.getCardId(), order.getOrderNo(), order.getDeviceId(),
                order.getInStation(), order.getOutStation());
        
        return order;
    }

    @Override
    public GateTxnPayRespDTO syncDebitStatus(GateTxnPaySyncStatusReqDTO request) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        String orderNo = request != null ? trimToNull(request.getOrderNo()) : null;
        String payStatus = request != null ? trimToNull(request.getPayStatus()) : null;
        if (orderNo == null || payStatus == null) {
            response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
            response.setRetMsg("orderNo与payStatus不能为空");
            log.warn("支付结果同步参数缺失, request={}", request);
            return response;
        }

        GateTxnPay order = gateTxnPayMapper.selectByOrderNo(orderNo);
        if (order == null) {
            response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
            response.setRetMsg("未找到对应的过闸扣费订单");
            log.error("支付结果同步未找到订单，MUST 人工核对支付中心与本地口径, orderNo={}, payStatus={}", orderNo, payStatus);
            return response;
        }

        String debitStatus = DebitStatus.SUCCESS.is(payStatus)
                ? DebitStatus.SUCCESS.code() : DebitStatus.FAIL.code();
        int updated = gateTxnPayWriter.convergeDebitStatus(
                orderNo, order.getTxnDate(), debitStatus, "支付结果回调收敛：" + payStatus);
        if (updated == 0) {
            if (debitStatus.equals(order.getDebitStatus())) {
                log.info("支付结果同步幂等跳过，订单已是同一终态, orderNo={}, debitStatus={}", orderNo, debitStatus);
                response.setRetCode(GateTxnPayRetCode.SUCCESS);
                response.setRetMsg("成功");
            } else {
                log.error("支付结果同步未命中，订单状态与回调不一致，MUST 人工核对, orderNo={}, 当前={}, 回调={}",
                        orderNo, order.getDebitStatus(), debitStatus);
                response.setRetCode(GateTxnPayRetCode.STATUS_REJECT);
                response.setRetMsg("订单状态不允许收敛：" + order.getDebitStatus());
            }
            response.setOrderNo(orderNo);
            response.setPayStatus(order.getDebitStatus());
            return response;
        }

        log.info("支付结果同步完成, orderNo={}, txnDate={}, {} -> {}",
                orderNo, order.getTxnDate(), order.getDebitStatus(), debitStatus);
        response.setRetCode(GateTxnPayRetCode.SUCCESS);
        response.setRetMsg("成功");
        response.setOrderNo(orderNo);
        response.setPayStatus(debitStatus);
        return response;
    }

    @Override
    public GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(GateTxnPayDebitConvergeReqDTO request) {
        GateTxnPayDebitConvergeRespDTO response = new GateTxnPayDebitConvergeRespDTO();
        String origOrderNo = request != null ? trimToNull(request.getOrigOrderNo()) : null;
        String txnDate = request != null ? trimToNull(request.getTxnDate()) : null;
        response.setOrigOrderNo(origOrderNo);
        if (origOrderNo == null) {
            response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
            response.setRetMsg("origOrderNo不能为空");
            response.setConverged(false);
            log.warn("补款收敛参数缺失, request={}", request);
            return response;
        }

        GateTxnPay order = gateTxnPayMapper.selectByOrderNo(origOrderNo);
        if (order == null) {
            response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
            response.setRetMsg("未找到对应的过闸扣费订单");
            response.setConverged(false);
            log.error("补款收敛未找到原订单，MUST 人工核对, origOrderNo={}", origOrderNo);
            return response;
        }

        String effectiveTxnDate = txnDate != null ? txnDate : order.getTxnDate();
        int updated = gateTxnPayWriter.convergeDebitStatusForSupplement(
                origOrderNo, effectiveTxnDate, trimToNull(request.getRemark()));
        if (updated > 0) {
            log.info("补款收敛原订单完成, origOrderNo={}, txnDate={}, {} -> SUCCESS",
                    origOrderNo, effectiveTxnDate, order.getDebitStatus());
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("成功");
            response.setConverged(true);
            response.setDebitStatus(DebitStatus.SUCCESS.code());
            return response;
        }

        response.setDebitStatus(order.getDebitStatus());
        response.setConverged(false);
        if (DebitStatus.SUCCESS.is(order.getDebitStatus())) {
            log.warn("补款收敛未命中，原订单已是SUCCESS（疑似已被先到的补款单结清）, origOrderNo={}, txnDate={}",
                    origOrderNo, effectiveTxnDate);
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("原订单已结清，本次未改动");
        } else {
            log.error("补款收敛未命中，原订单状态不允许收敛，MUST 人工核对, origOrderNo={}, txnDate={}, debitStatus={}",
                    origOrderNo, effectiveTxnDate, order.getDebitStatus());
            response.setRetCode(GateTxnPayRetCode.STATUS_REJECT);
            response.setRetMsg("订单状态不允许收敛：" + order.getDebitStatus());
        }
        return response;
    }

    private boolean isOfflineExit(GateTxnPayReqDTO request) {
        return request != null && "Y".equalsIgnoreCase(trimToNull(request.getOfflineFlag()))
                && GateTxnPayFieldCode.isExitTrxType(request.getTrxType());
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private boolean isDailyTicket(GateTxnPay order) {
        return CardTypeCodeEnum.isDailyTicket(order.getCardType());
    }


    private boolean isBomSupplement(GateTxnPay order) {
        if (order == null) {
            return false;
        }
        String adviceOpt = trimToNull(order.getAdviceOpt());
        return adviceOpt != null && BOM_SUPPLEMENT_ADVICE_OPTS.contains(adviceOpt);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildOrderNo(GateTxnPayReqDTO request) {
        String time = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String suffix = request.getCardId();
        if (suffix != null && suffix.length() > 6) {
            suffix = suffix.substring(suffix.length() - 6);
        }
        return "GT" + time + (suffix == null ? "" : suffix);
    }

    private int parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

}

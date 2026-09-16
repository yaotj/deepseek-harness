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
import java.util.concurrent.Executor;

@Service
public class GateTxnPayServiceImpl implements GateTxnPayService {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayServiceImpl.class);
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final GateTxnPayMapper gateTxnPayMapper;
    private final GateTxnPayWriter gateTxnPayWriter;
    /**
     * 算价整段已搬到 {@link FareCalculator}：本类不再直接持有票价 / 账户 / 钱包 / 折扣档位 / 换乘减免
     * 这五个协作者，也不再读 {@code offline.billing.*} 三个配置项，它们全部收口在算价类内部。
     */
    private final FareCalculator fareCalculator;
    /**
     * 「发起扣款」整段已搬到 {@link PaySignInitiator}：两个渠道的报文组装（各自一个工厂）、
     * {@code gate.pay.*} / {@code alipay.trip.*} 两组配置与「0000 才 PROCESSING」的收敛规则
     * 全部收口在那里。出账口 MUST 只有一处。
     */
    private final PaySignInitiator paySignInitiator;
    /**
     * 公交换乘推送任务的**判定与构建**已搬到 {@link MetroTransferPushTaskProcessor}：
     * 那五个条件（出站 / 钱包渠道 / 非蓝牙 / 非同行 / 非第三方）与该任务的推送、重试同处一类，
     * 出站首次落单与离线码补偿两条链路共用同一份，**NEVER 各自复制一份判定**。
     *
     * <p>该类只依赖任务表与推送客户端、不反向引用本 Service，因此不构成循环依赖。</p>
     */
    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    private final Executor paySignAsyncExecutor;
    /**
     * 运营人工干预（重试 / 退款）整段已搬到 {@link GateTxnPayManualOpsService}。
     *
     * <p>搬走的直接收益是**本类不再持有任何 rpc client**：退款是它此前持有
     * {@code PaySignClient} 的唯一理由，而那让本类同时握着「出账口」与「退款口」两个出向 RPC。
     * 现在出账口只在 {@link PaySignInitiator}、退款口只在 ManualOps。</p>
     *
     * <p>该类不反向引用本 Service，因此不构成循环依赖。</p>
     */
    private final GateTxnPayManualOpsService manualOpsService;

    /**
     * 中文站名回填：本模块是 {@code IN_STATION} 的最终写入方，因此站名的 owner 也 MUST 在这里。
     * 详细成因见 {@link StationNameBackfiller} 的类注释。
     */
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
                // 票价查询与钱包折扣解耦：ORIGINAL_FARE 是「本次行程的地铁原价」，与是否扣费、走哪个支付渠道无关，
                // APP 扣费详情要靠它算「已省金额」。原先它只在 calculateWalletDiscount 的
                // 「paymentVendor=0B 且参与钱包累计」分支里赋值，于是日票 / 员工票 / 非钱包渠道
                // 全都拿不到 ORIGINAL_FARE 与 EXPECTED_GATE_AMOUNT（2026-09-10 定位：日票扣费详情全 null）。
                // fillOriginalFare 自己吞异常，NEVER 因票价查不到而影响出站放行。
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
        // 站名回填 MUST 在算价之后：离线码路径的 FareCalculator 会按 cardId + ticketTransSeq
        // 重查首笔进站交易并覆盖 IN_STATION，放到算价之前等于拿旧编码（可能是占位 FFFF 或
        // 上一趟行程的站码）去查名，白做且会写进错的中文名。
        stationNameBackfiller.backfill(order);
        order.setTotalAmount((order.getTrxAmount() == null ? 0 : order.getTrxAmount())
                + (order.getOvertimeAmount() == null ? 0 : order.getOvertimeAmount()));
        MetroTransferPushTask pushTask = metroTransferPushTaskProcessor.buildMetroTransferPushTask(order);

        if (isDailyTicket(order) || order.getTotalAmount() <= 0) {
            String reason = isDailyTicket(order) ? "日票交易默认支付成功" : "免扣费交易默认支付成功";
            // 免扣费交易实扣为 0，显式写 EXPECTED_GATE_AMOUNT 供 APP 扣费详情展示「原价 X / 已省 X / 实付 0」，
            // NEVER 留 null——留 null 时 APP 拿不到任何金额，扣费详情只能显示 0（2026-09-10 修复）。
            order.setExpectedGateAmount(0);
            if (!StringUtils.hasText(order.getDiscountCalcStatus())) {
                order.setDiscountCalcStatus(DiscountCalcStatus.SKIPPED.code());
                order.setDiscountCalcMsg(reason + "，不参与钱包折扣计算");
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

        // 幂等保护：如果订单已存在且已触发过支付请求，不再重复触发异步调用
        // insertOrder 在唯一索引冲突时会返回已有订单，需判断是否已处理过
        if (!savedOrder.getOrderNo().equals(order.getOrderNo())) {
            log.warn("并发重复请求，订单已存在，跳过重复触发pay-sign, orderNo={}, existingStatus={}",
                    savedOrder.getOrderNo(), savedOrder.getDebitStatus());
            // 返回已有订单号，状态以实际为准
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("成功");
            response.setOrderNo(savedOrder.getOrderNo());
            response.setPayStatus(savedOrder.getDebitStatus());
            return response;
        }

        // 异步调用 pay-sign，成功后回调更新状态
        paySignAsyncExecutor.execute(() -> paySignInitiator.initiateAsync(savedOrder, request));

        response.setRetCode(GateTxnPayRetCode.SUCCESS);
        response.setRetMsg("成功");
        response.setOrderNo(savedOrder.getOrderNo());
        response.setPayStatus(DebitStatus.PROCESSING.code());
        return response;
    }


    /**
     * 离线码金额待重算的标记，落在 {@code DISCOUNT_CALC_STATUS} 上。
     *
     * <p>借这一列而不是新增列，是为了零 DDL——{@code GATE_TXN_PAY} 是月分区表，加列成本高。
     * 改动该字面量 MUST 同步改 {@code GateTxnPayMapper.xml} 里三处同名条件，否则补偿静默失效。</p>
     */
    private static final String OFFLINE_FARE_PENDING = DiscountCalcStatus.OFFLINE_FARE_PENDING.code();

    /**
     * 离线码算价失败时落单留痕，交由 {@link com.chinasofti.huateng.gatetxnpay.service.OfflineFareRecoveryService} 补偿。
     *
     * <p>金额清零并显式打上 {@link #OFFLINE_FARE_PENDING}：这行 {@code TOTAL_AMOUNT=0} 但
     * **不是免扣费交易**，因此 **NEVER** 让它走 {@code totalAmount <= 0} 的默认 SUCCESS 分支，
     * 也 **NEVER** 在此建换乘推送任务（金额与换乘减免都还没算出来）。
     * 对上游仍返回 8002，保持「闸机照常放行、ITP 侧不认账」的既有语义不变。</p>
     */
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


    /**
     * 只处理出站类交易扣费，进站交易只更新票卡状态，不进入扣款链路。
     */
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

    /**
     * 根据闸机交易报文生成本地过闸扣费订单。
     *
     * <p>itpUserId 入库前转换为十进制 thirdUserId；进出站信息保持简单字段，
     * 后续查询或退款都通过 orderNo 关联支付明细。</p>
     */
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
        // 支付宝出行行业明细：由 fep-dev-server 在出站时整块组好透传下来，本服务只存不算。
        // 其中 9 个键（进出站线路码/名称、进站设备号、entryId/exitId、cardNum、cardIssueCode）
        // 在 GATE_TXN_PAY 没有对应列，只有出站那一刻的三个并行 RPC 拿得到，
        // NEVER 在扣费或重试时按订单字段重算——重算出来的键名和值都与支付宝要的不一致。
        order.setIndustryDetail(truncate(trimToNull(request.getIndustryDetail()), 4000));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        // 新增字段：由 ticket-server 透传
        order.setTicketStatus(request.getTicketStatus());
        order.setOrderExpType(request.getOrderExpType());
        order.setEntryStationName(request.getEntryStationName());
        order.setExitStationName(request.getExitStationName());
        order.setCompanionFlag(request.getCompanionFlag());
        order.setOfflineFlag(request.getOfflineFlag());
        order.setTicketCode(request.getTicketCode());
        // COUNTING_TIMES / COUNTING_FLAG MUST 入库即有值、NEVER 留 NULL（用户 2026-09-10 裁定）：
        // COUNTING_TIMES 非日票恒 0、日票恒 1（本次行程消耗次数，NEVER 是剩余次数）；
        // COUNTING_FLAG   Y=日票（记期票+计次票）、N=非日票。
        // 正常链路由 ticket-server GateTicketHandler 透传下来；这里再按 CARD_TYPE 兜一次，
        // 覆盖上游未升级镜像、补录、离线补单等不带这两个字段的入口。
        // 兜底 MUST 放在入库前而不是查询出口——出口补值只能骗过 APP，库里仍是 NULL，报表与对账照样缺。
        boolean dailyTicketOrder = CardTypeCodeEnum.isDailyTicket(order.getCardType());
        order.setCountingTimes(request.getCountingTimes() != null
                ? request.getCountingTimes()
                : (dailyTicketOrder ? 1 : 0));
        order.setCountingFlag(StringUtils.hasText(request.getCountingFlag())
                ? request.getCountingFlag()
                : (dailyTicketOrder ? "Y" : "N"));
        order.setAttributableParty(request.getAttributableParty());
        order.setReceivingParty(request.getReceivingParty());
        // payChannelCode/discountFee/discountInfo 仅用于 pay-sign，不持久化到 GATE_TXN_PAY
        log.info("IF1A-01 构建 GateTxnPay 订单快照, cardId={}, orderNo={}, ticketStatus={}, orderExpType={}, offlineFlag={}, companionFlag={}, ticketCode={}, countingTimes={}, countingFlag={}, attributableParty={}, receivingParty={}, entryStationName={}, exitStationName={}",
                request.getCardId(), order.getOrderNo(), order.getTicketStatus(),
                order.getOrderExpType(), order.getOfflineFlag(),
                order.getCompanionFlag(), order.getTicketCode(), order.getCountingTimes(),
                order.getCountingFlag(), order.getAttributableParty(), order.getReceivingParty(),
                order.getEntryStationName(), order.getExitStationName());
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

        // 只认 SUCCESS 为成功，其余一切支付状态（FAIL / CLOSED / 未知值）都收敛为 FAIL。
        // NEVER 反过来写成「非 FAIL 即成功」——未知状态被当成扣款成功等于放弃这笔应收。
        String debitStatus = DebitStatus.SUCCESS.is(payStatus)
                ? DebitStatus.SUCCESS.code() : DebitStatus.FAIL.code();
        int updated = gateTxnPayWriter.convergeDebitStatus(
                orderNo, order.getTxnDate(), debitStatus, "支付结果回调收敛：" + payStatus);
        if (updated == 0) {
            // 0 行有两种含义：订单已是终态（重复回调，正常）或状态值意外。
            // 已是同一终态即视为幂等成功，其余情形回非 0000 让调用方留痕。
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

    /**
     * 补款专用收敛：白名单含 {@code FAIL}，且把「本次是否真改了行」与「订单当前权威状态」
     * 一起回给调用方，让它一次调用就能分出三支。
     *
     * <p>本方法**不带事务**、也**不调任何远端**：单条 UPDATE 自动提交即可，
     * 而 {@code selectByOrderNo} 只是为了拿 {@code TXN_DATE}（月分区表的 WHERE 组成）与状态。</p>
     */
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

        // 调用方送的 txnDate 优先（补款明细里记的是下单当时的账期），缺失时退回订单自身的值。
        // NEVER 用当天日期替代：GATE_TXN_PAY 按月分区，日期错了 UPDATE 恒 0 行且不报错。
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

        // 0 行：MUST 回填当前状态，调用方靠它区分「已被先到的补款单收敛」与「状态不在白名单」。
        // 前者是重复支付待退款、后者是需人工核对，两者都不该再重试。
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

    /**
     * 金额字段按分保存，空值按 0 处理。
     */
    private int parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }


}

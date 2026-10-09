package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.BizDataBuilder;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.IndustryDetailEnricher;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.BlacklistPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付宝出行（小程序）扣费申请 —— <b>落新表 {@code ALIPAY_PAY_TXN_DETAIL} 的实现</b>。
 *
 * <p><b>2026-09-18 起本类已是零调用方、只作回滚位</b>：{@code POST /api/payment/requestPay} 已切到
 * {@code service.AlipayPayRequestService}（实现 {@code service.impl.pay.AlipayPayRequestServiceImpl}）。
 * 本类行为一行未改，<b>NEVER 在这里新增能力</b> —— 新的扣款能力一律加在新落点；
 * 回滚只需把 {@code AlipayTxnPayController.requestPay} 改回注本类。
 * 新实现<b>唯一的行为差异</b>是把「已 SUCCESS 短路」挪到取 {@code txnDate} 的 RPC 之前
 * （本类的顺序会让一笔已成功的单子在 gate-txn-pay 不可达时被报成失败）。
 *
 * <p>与同包的旧 {@link PaymentRequestService} <b>并存</b>：旧类逐字保留、零改动，只是没有 HTTP 入口了。
 * <b>NEVER 在旧类上改任何东西</b>。
 *
 * <p><b>与旧实现的四处实质差异</b>（不是重构，是修缺陷）：
 * <ol>
 *   <li><b>落库</b>。旧实现全程不写任何表（单测 {@code AlipayPaymentCharacterizationTest} 有一条断言
 *       专门钉住这个缺陷），于是扣款申请没有任何本地痕迹、失败无法补偿、对账无源。本实现按
 *       「先落库 → 再出网 → 后回写」三步走。</li>
 *   <li><b>报文留痕</b>。每次出网的请求与应答原文进 {@code ALIPAY_PAY_CENTER_MSG_LOG}，一次调用一行，
 *       重试链路每一步都可举证。</li>
 *   <li><b>幂等</b>。旧实现对同一 {@code orderNo} 重推会无条件再打一次支付中心；本实现先查本地，
 *       已 {@code SUCCESS} 直接短路返成功，并靠 {@code UK_APTD_ORDER} 兜住并发。</li>
 *   <li><b>{@code thirdUserId} 必填校验</b>。旧实现的六项校验里没有它，null 时会一路走到查签约、
 *       被当成「用户未签约」返回，掩盖了参数问题。</li>
 * </ol>
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：本方法内有支付中心 HTTP 调用与一次 RPC，事务包住它们会
 * 让行锁持有时长等于对端响应时长，上游重推全部堆在同一行上串行等待 —— pay-sign 侧 2026-08-26 已因此
 * 出过循环重推 8 分钟、单请求 287 秒、连「留证据」的 INSERT 都被一起回滚的事故。每条 SQL 自动提交
 * 才是这里要的语义：**出网前那次落库必须已经提交**，否则出网后进程挂掉就什么都不剩。
 */
@Service
public class AlipayTxnPayService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnPayService.class);

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String API_REQUEST_PAY = "requestPay";

    private static final String PAY_STATUS_INIT = "INIT";
    private static final String PAY_STATUS_PROCESSING = "PROCESSING";
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String PAY_STATUS_FAIL = "FAIL";
    private static final String REFUND_STATUS_NONE = "NONE";

    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;
    private final AlipayPayCenterMsgLogWriter msgLogWriter;
    private final GateTxnPayClient gateTxnPayClient;
    private final IndustryDetailEnricher industryDetailEnricher;
    private final BizDataBuilder bizDataBuilder;
    private final PayCenterProperties payCenterProperties;
    private final PayCenterPort payCenterPort;
    private final BlacklistPort blacklistPort;

    public AlipayTxnPayService(AlipaySignInfoMapper alipaySignInfoMapper,
                              AlipayPayTxnDetailMapper alipayPayTxnDetailMapper,
                              AlipayPayCenterMsgLogWriter msgLogWriter,
                              GateTxnPayClient gateTxnPayClient,
                              IndustryDetailEnricher industryDetailEnricher,
                              BizDataBuilder bizDataBuilder,
                              PayCenterProperties payCenterProperties,
                              PayCenterPort payCenterPort,
                              BlacklistPort blacklistPort) {
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
        this.msgLogWriter = msgLogWriter;
        this.gateTxnPayClient = gateTxnPayClient;
        this.industryDetailEnricher = industryDetailEnricher;
        this.bizDataBuilder = bizDataBuilder;
        this.payCenterProperties = payCenterProperties;
        this.payCenterPort = payCenterPort;
        this.blacklistPort = blacklistPort;
    }

    /** 扣费申请。 */
    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        log.info("接收到支付宝支付申请报文（新链路）: {}", JSON.toJSONString(request));
        validate(request);

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (signInfo == null) {
            throw new BusinessException(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), "用户未签约");
        }
        request.setRequestSignSeq(signInfo.getAgreementCode());

        String orderNo = request.getOrderNo();
        String txnDate = resolveTxnDate(orderNo);

        AlipayPayTxnDetail existing = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        if (existing != null && PAY_STATUS_SUCCESS.equals(existing.getPayStatus())) {
            log.info("支付明细已是终态 SUCCESS，按重推短路返成功, orderNo={}", orderNo);
            return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "支付成功", orderNo);
        }
        if (existing == null) {
            insertDetail(request, signInfo, orderNo, txnDate);
        }

        alipayPayTxnDetailMapper.markRequesting(orderNo);

        request.setIndustryDetail(industryDetailEnricher.enrich(request.getIndustryDetail(), signInfo.getThirdUserId()));
        Map<String, Object> bizData = bizDataBuilder.build(request, signInfo, payCenterProperties);
        log.info("支付宝支付申请,调用支付中心,orderNo={}, 请求参数={}", orderNo, JSON.toJSONString(bizData));

        PayCenterReply reply;
        long startNanos = System.nanoTime();
        try {
            reply = payCenterPort.requestPay(bizData);
        } catch (RuntimeException e) {
            long elapsed = elapsedMillis(startNanos);
            msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, null, elapsed,
                    "调用支付中心抛异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("支付宝支付申请调用支付中心抛异常，状态保持 PROCESSING 等回查、NEVER 置 FAIL, orderNo={}", orderNo, e);
            return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "调用支付中心失败", orderNo);
        }
        long elapsedMs = elapsedMillis(startNanos);
        msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, reply, elapsedMs, null);

        return applyReply(reply, signInfo, orderNo);
    }

    /**
     * 按支付中心应答回写状态并组装响应。
     *
     * <p>三分支的处置刻意不同，MUST 逐条读懂：
     * <ul>
     *   <li>{@code Accepted} + {@code SUCCESS}：只是<b>受理</b>，最终结果等回调或回查，
     *       因此写 {@code PROCESSING} 而<b>不是</b> {@code SUCCESS}。
     *       <b>NEVER 在这里写 SUCCESS</b> —— 那等于拿「受理成功」冒充「扣款成功」。</li>
     *   <li>{@code Accepted} + 非 {@code SUCCESS}：拿到了业务拒绝，一次即终态 {@code FAIL}，并加黑名单
     *       （沿用旧实现口径：只有这一支加黑）。</li>
     *   <li>{@code Rejected}：拿到响应但传输层判据不成立，同样写 {@code FAIL}，但 <b>NEVER 加黑名单</b> ——
     *       拿不到业务应答不等于扣款被拒。</li>
     *   <li>{@code NoAnswer}：<b>一个字都不回写</b>，状态保持 {@code markRequesting} 置好的
     *       {@code PROCESSING}。钱可能已经扣了，写 {@code FAIL} 会让这笔单子被当成失败、既不回查也不补偿，
     *       是实打实的资损路径。<b>NEVER 在这一支置 FAIL。</b></li>
     * </ul>
     */
    private AlipayTripRequestPayRespDTO applyReply(PayCenterReply reply, AlipaySignInfo signInfo, String orderNo) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝支付申请,支付中心业务应答: orderNo={}, retCode={}, retMsg={}",
                        orderNo, accepted.retCode(), accepted.retMsg());
                if (PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    updateRequestResult(orderNo, PAY_STATUS_PROCESSING,
                            accepted.field("payCenterOrderNo"), accepted.field("channelOrderNo"));
                    String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付受理成功";
                    return response(FepAppErrorCodeEnum.SUCCESS.getCode(), msg, orderNo);
                }
                updateRequestResult(orderNo, PAY_STATUS_FAIL, null, accepted.field("channelOrderNo"));
                String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付失败";
                addBlackListIfNeeded(signInfo, msg);
                return response(FepAppErrorCodeEnum.FAIL.getCode(), msg, orderNo);
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("支付宝支付申请未拿到业务应答，置 FAIL、NEVER 加黑名单, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                updateRequestResult(orderNo, PAY_STATUS_FAIL, null, null);
                return response(FepAppErrorCodeEnum.FAIL.getCode(),
                        rejected.msg() != null ? rejected.msg() : "支付失败", orderNo);
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("支付宝支付申请无响应，状态保持 PROCESSING 等回查、NEVER 置 FAIL、NEVER 加黑名单, orderNo={}", orderNo);
                return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "调用支付中心失败", orderNo);
            }
        }
    }

    /**
     * 从订单主表取 {@code TXN_DATE}。
     *
     * <p>本表的 {@code TXN_DATE} 是分区键 + 唯一键第二列，值 MUST 与 {@code GATE_TXN_PAY} 那行一致 ——
     * <b>NEVER 用本地当天日期兜底</b>：跨零点时会把行写进错误的月分区，而且与主表的账期口径分叉，
     * 对账时两边永远对不上，且这种错**建表时和运行时都不会报错**。
     *
     * <p>入向报文里没有这个字段（{@code AlipayTripRequestPayReqDTO} 的 16 个字段里没有它，上游
     * {@code AlipayTripPayRequestFactory} 也没送），所以只能回查主表。这个依赖是自洽的：请求本来就来自
     * gate-txn-pay，它不可达的话这个请求根本进不来，因此不构成新增失败点。
     *
     * <p>取不到就抛错、让上游重推，<b>NEVER 编一个日期继续落库</b>。
     */
    private String resolveTxnDate(String orderNo) {
        GateTxnPayListDTO order = gateTxnPayClient.queryByOrderNo(orderNo);
        if (order == null || !StringUtils.hasText(order.getTxnDate())) {
            log.error("查订单主表拿不到 txnDate，拒绝落支付明细、让上游重推, orderNo={}, order={}", orderNo, order);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "订单不存在或缺少交易日期");
        }
        return order.getTxnDate();
    }

    /**
     * 出网前落一行支付明细，状态 {@code INIT}。
     *
     * <p>并发下两条请求可能都查不到已存在的行，第二条 INSERT 会撞 {@code UK_APTD_ORDER}。
     * 那不是错误、是幂等生效：本方法把它当「已有别人落好了」处理。
     * <b>判定 MUST 沿 {@code getCause()} 链走</b>，NEVER 只 catch 最外层的
     * {@code DuplicateKeyException} —— 本模块开了 tracing，观测切面会把异常重新包一层，
     * 只认最外层类名的写法会静默失效（ADR-D53 已在卡池上真实发生过）。
     */
    private void insertDetail(AlipayTripRequestPayReqDTO request, AlipaySignInfo signInfo, String orderNo, String txnDate) {
        AlipayPayTxnDetail record = new AlipayPayTxnDetail();
        record.setOrderNo(orderNo);
        record.setTxnDate(txnDate);
        record.setPayStatus(PAY_STATUS_INIT);
        record.setAmount(request.getAmount());
        record.setRefundStatus(REFUND_STATUS_NONE);
        record.setRefundAmount(0);
        record.setRequestSignSeq(signInfo.getAgreementCode());
        record.setChannelAgreementNo(signInfo.getChannelAgreementCode());
        record.setRequestCount(0);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            alipayPayTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.info("支付明细已被并发请求落好，按幂等继续, orderNo={}", orderNo);
        }
    }

    private void updateRequestResult(String orderNo, String payStatus, String payCenterOrderNo, String channelOrderNo) {
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setPayCenterOrderNo(payCenterOrderNo);
        update.setChannelOrderNo(channelOrderNo);
        int affected = alipayPayTxnDetailMapper.updateRequestResult(update);
        if (affected == 0) {
            log.error("回写支付申请结果影响 0 行，明细行可能不存在、MUST 人工核对, orderNo={}, payStatus={}", orderNo, payStatus);
        }
    }

    /**
     * 扣款被业务拒绝后把该卡加入黑名单。
     *
     * <p>处置逐字沿用旧实现：三种结果都只打日志、不落库、不重试 —— 这意味着<b>加黑失败即永久丢失、
     * 该卡仍可过闸</b>。要补偿得先有载体表（本模块目前没有），<b>NEVER 在这里加静默重试</b>。
     */
    private void addBlackListIfNeeded(AlipaySignInfo signInfo, String reason) {
        if (signInfo == null || !StringUtils.hasText(signInfo.getCardId()) || !StringUtils.hasText(signInfo.getThirdUserId())) {
            return;
        }
        String cardId = signInfo.getCardId();
        String thirdUserId = signInfo.getThirdUserId();
        String blackReason = StringUtils.hasText(reason) ? reason : "地铁扣款失败";
        RpcOutcome outcome = blacklistPort.addBlackList(cardId, thirdUserId, signInfo.getCardType(), blackReason);
        switch (outcome) {
            case RpcOutcome.Ok ok -> log.info("扣款失败加黑名单完成, cardId={}", cardId);
            case RpcOutcome.BizRejected rejected -> log.error(
                    "加黑名单被业务拒绝，重试无意义、该卡仍可过闸、MUST 人工核对, cardId={}, retCode={}, retMsg={}",
                    cardId, rejected.retCode(), rejected.retMsg());
            case RpcOutcome.Unreachable unreachable -> log.error(
                    "加黑名单未获答复，该卡仍可过闸、MUST 人工核对, cardId={}, cause={}",
                    cardId, unreachable.cause().getClass().getSimpleName(), unreachable.cause());
        }
    }

    /**
     * 必填校验。比旧实现多一个 {@code thirdUserId}：旧实现漏了它，null 时会一路走到查签约、
     * 被当成「用户未签约」返回，把参数问题伪装成业务问题。
     */
    private void validate(AlipayTripRequestPayReqDTO request) {
        boolean valid = request != null
                && StringUtils.hasText(request.getOrderNo())
                && StringUtils.hasText(request.getThirdUserId())
                && request.getAmount() != null
                && StringUtils.hasText(request.getIndustryType())
                && StringUtils.hasText(request.getSubject())
                && StringUtils.hasText(request.getBody())
                && StringUtils.hasText(request.getIndustryDetail());
        if (!valid) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "订单号/第三方用户号/支付金额/行业类型/订单标题/订单描述/行业详情不能为空");
        }
    }

    private AlipayTripRequestPayRespDTO response(String retCode, String retMsg, String orderNo) {
        AlipayTripRequestPayRespDTO response = new AlipayTripRequestPayRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        response.setOrderNo(orderNo);
        log.info("支付宝支付申请完成（新链路）, orderNo={}, retCode={}", orderNo, retCode);
        return response;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 沿 cause 链判完整性冲突，理由见 {@link #insertDetail} 的注释。 */
    private boolean isIntegrityViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("DuplicateKey") || name.contains("IntegrityConstraintViolation")) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}

package com.chinasofti.huateng.alipay.paysign.service.impl.pay;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.BlacklistPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRequestService;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.BizDataBuilder;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.IndustryDetailEnricher;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 扣费申请编排（{@link AlipayPayRequestService} 的唯一实现）。
 *
 * <p>五步，顺序 MUST 不变：① 校验收口成 {@link PayCommand} → ② 查生效签约 → ③ <b>终态短路</b> →
 * ④ 落库并提交（{@link PayTxnRepository#openAttempt}）→ ⑤ 出网 + 按应答回写。
 *
 * <p><b>③ 刻意排在 ④ 之前、也排在取 {@code txnDate} 的 RPC 之前</b>，这是本实现与
 * {@code AlipayTxnPayService} 的<b>唯一行为差异</b>：那份实现先无条件调 gate-txn-pay 取 {@code txnDate}
 * 再判终态，于是**对一笔已经 SUCCESS 的单子重推时，只要 gate-txn-pay 不可达就返
 * {@code FAIL 订单不存在或缺少交易日期}** —— 把一笔已成功的扣款报成失败，是实打实的误判。
 * 短路分支根本不需要 {@code txnDate}。<b>NEVER 把这两步的顺序换回去。</b>
 *
 * <p><b>三件套里的「sealed 结果」在本链路由既有 {@link PayCenterReply} 承担</b>（ADR-D131）：
 * 出网应答的三分支处置完全不同，穷尽 {@code switch} 少写一支即编译失败。落库那一步只有
 * 「缺行就落 / 已有就复用」两条路、且调用方处置**完全相同**，
 * <b>NEVER 为了跟签约链路对称再造一个 PayOutcome</b> —— 那种类型只会让人以为两条路要分别处置。
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：本方法内有一次 RPC（取 {@code txnDate}）与一次支付中心
 * HTTP 调用。事务包住它们会让行锁持有时长等于对端响应时长，上游对同一笔的重推全部堆在同一行上串行
 * 等待，超过 Druid {@code remove-abandoned-timeout} 后连接被强杀、连「留证据」的 INSERT 一起回滚
 * —— pay-sign 侧 2026-08-26 已因此出过循环重推 8 分钟、单请求 287 秒的生产事故。每条 SQL 自动提交
 * 才是这里要的语义。
 *
 * <p><b>与旧实现互不调用</b>：{@code AlipayTxnPayService} 原样保留作回滚位，本类 NEVER 转发给它、
 * 它也 NEVER 反向调本类 —— 否则「线上跑的是哪一侧」无法判断。回滚只需把
 * {@code AlipayTxnPayController.requestPay} 改回注那个类。
 */
@Service
public class AlipayPayRequestServiceImpl implements AlipayPayRequestService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayRequestServiceImpl.class);

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String API_REQUEST_PAY = "requestPay";
    /** 支付中心业务成功的 {@code retCode}。 */
    private static final String PAY_CENTER_SUCCESS = "SUCCESS";
    /** 支付宝渠道的卡机构编号，payQuery 出向 bizData 固定值，与 {@code AlipayTxnPayQueryService} 一致。 */
    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";

    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final PayTxnRepository payTxnRepository;
    private final AlipayPayCenterMsgLogWriter msgLogWriter;
    private final GateTxnPayClient gateTxnPayClient;
    private final IndustryDetailEnricher industryDetailEnricher;
    private final BizDataBuilder bizDataBuilder;
    private final PayCenterProperties payCenterProperties;
    private final PayCenterPort payCenterPort;
    private final BlacklistPort blacklistPort;

    public AlipayPayRequestServiceImpl(AlipaySignInfoMapper alipaySignInfoMapper,
                                       PayTxnRepository payTxnRepository,
                                       AlipayPayCenterMsgLogWriter msgLogWriter,
                                       GateTxnPayClient gateTxnPayClient,
                                       IndustryDetailEnricher industryDetailEnricher,
                                       BizDataBuilder bizDataBuilder,
                                       PayCenterProperties payCenterProperties,
                                       PayCenterPort payCenterPort,
                                       BlacklistPort blacklistPort) {
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.payTxnRepository = payTxnRepository;
        this.msgLogWriter = msgLogWriter;
        this.gateTxnPayClient = gateTxnPayClient;
        this.industryDetailEnricher = industryDetailEnricher;
        this.bizDataBuilder = bizDataBuilder;
        this.payCenterProperties = payCenterProperties;
        this.payCenterPort = payCenterPort;
        this.blacklistPort = blacklistPort;
    }

    @Override
    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        log.info("接收到支付宝支付申请报文: {}", JSON.toJSONString(request));
        PayCommand command = PayCommand.from(request);
        String orderNo = command.orderNo();

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(command.thirdUserId(), CHANNEL_ALIPAY);
        if (signInfo == null) {
            throw new BusinessException(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), "用户未签约");
        }

        AlipayPayTxnDetail existing = payTxnRepository.findByOrderNo(orderNo);
        if (PayTxnRepository.isSettled(existing)) {
            log.info("支付明细已是终态 SUCCESS，按重推短路返成功, orderNo={}", orderNo);
            return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "支付成功", orderNo);
        }

        String txnDate = resolveTxnDate(orderNo);
        payTxnRepository.openAttempt(command, signInfo, txnDate, existing);

        return callPayCenter(request, command, signInfo, txnDate);
    }

    /**
     * 组装 bizData 并出网，报文原文一律留痕。
     *
     * <p>两处对入向 DTO 的**就地改写是沿用既有口径、不是疏漏**：
     * ① {@code requestSignSeq} 用本地生效签约的 {@code agreementCode} 覆盖（入参带的那个不可信）；
     * ② {@code industryDetail} 由 {@link IndustryDetailEnricher} 补齐。
     * 之所以还是把 DTO 传给 {@link BizDataBuilder}，是因为出网报文要用到另外 9 个字段，
     * 那些字段的契约归 builder 管，**NEVER 为了「不改入参」把它们复制进 {@link PayCommand}**。
     */
    private AlipayTripRequestPayRespDTO callPayCenter(AlipayTripRequestPayReqDTO request,
                                                      PayCommand command,
                                                      AlipaySignInfo signInfo,
                                                      String txnDate) {
        String orderNo = command.orderNo();
        request.setRequestSignSeq(signInfo.getAgreementCode());
        request.setIndustryDetail(industryDetailEnricher.enrich(command.industryDetail(), signInfo.getThirdUserId()));
        Map<String, Object> bizData = bizDataBuilder.build(request, signInfo, payCenterProperties);
        log.info("支付宝支付申请,调用支付中心,orderNo={}, 请求参数={}", orderNo, JSON.toJSONString(bizData));

        PayCenterReply reply;
        long startNanos = System.nanoTime();
        try {
            reply = payCenterPort.requestPay(bizData);
        } catch (RuntimeException e) {
            msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, null, elapsedMillis(startNanos),
                    "调用支付中心抛异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("支付宝支付申请调用支付中心抛异常，状态保持 PROCESSING 等回查、NEVER 置 FAIL, orderNo={}", orderNo, e);
            return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "调用支付中心失败", orderNo);
        }
        msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, reply, elapsedMillis(startNanos), null);

        return applyReply(reply, signInfo, orderNo);
    }

    /**
     * 按支付中心应答回写状态并组装响应。
     *
     * <p>三分支的处置刻意不同，MUST 逐条读懂：
     * <ul>
     *   <li>{@code Accepted} + {@code SUCCESS}：只是<b>受理</b>，最终结果等回调或回查，因此写
     *       {@code PROCESSING} 而<b>不是</b> {@code SUCCESS}。<b>NEVER 在这里写 SUCCESS</b> ——
     *       那等于拿「受理成功」冒充「扣款成功」。</li>
     *   <li>{@code Accepted} + 非 {@code SUCCESS}：拿到了业务拒绝，一次即终态 {@code FAIL}，
     *       并在 {@link #confirmedFailedByPayQuery} 回查确认「这笔钱真的没扣成」后才加黑名单
     *       （沿用既有口径：只有这一支加黑）。</li>
     *   <li>{@code Rejected}：拿到响应但传输层判据不成立，同样写 {@code FAIL}，但 <b>NEVER 加黑名单</b>
     *       —— 拿不到业务应答不等于扣款被拒。</li>
     *   <li>{@code NoAnswer}：<b>一个字都不回写</b>，状态保持 {@code openAttempt} 置好的
     *       {@code PROCESSING}。钱可能已经扣了，写 {@code FAIL} 会让这笔单子被当成失败、既不回查也不
     *       补偿，是实打实的资损路径。<b>NEVER 在这一支置 FAIL。</b></li>
     * </ul>
     *
     * <p><b>幂等拒答不再连带误加黑（2026-09-18 修）</b>：支付中心对「同一笔已支付成功」的重推会返
     * 非 {@code SUCCESS} 的幂等拒答（「订单已支付成功，请勿重复支付」），它落在第二支，此前被当成
     * 扣款失败 + 加黑名单 —— 一个已经付过钱的用户被拉进黑名单、过不了闸。现在加黑前先走
     * {@link #confirmedFailedByPayQuery} 用只读 payQuery 回查交易状态，只有确认 {@code FAIL} 才加黑。
     * <b>NEVER 改回「拿到非 SUCCESS 就加黑」，也 NEVER 靠匹配中文文案区分</b> —— 文案由对端随时可改，
     * 匹配它等于把资金判定挂在一句话上。
     */
    private AlipayTripRequestPayRespDTO applyReply(PayCenterReply reply, AlipaySignInfo signInfo, String orderNo) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝支付申请,支付中心业务应答: orderNo={}, retCode={}, retMsg={}",
                        orderNo, accepted.retCode(), accepted.retMsg());
                if (PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    payTxnRepository.writeRequestResult(orderNo, PayTxnRepository.STATUS_PROCESSING,
                            accepted.field("payCenterOrderNo"), accepted.field("channelOrderNo"));
                    String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付受理成功";
                    return response(FepAppErrorCodeEnum.SUCCESS.getCode(), msg, orderNo);
                }
                payTxnRepository.writeRequestResult(orderNo, PayTxnRepository.STATUS_FAIL,
                        null, accepted.field("channelOrderNo"));
                String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付失败";
                if (confirmedFailedByPayQuery(orderNo, signInfo)) {
                    addBlackListIfNeeded(signInfo, msg);
                } else {
                    log.warn("扣费被拒但回查确认不了这笔真的失败，跳过加黑名单、MUST 人工核对, orderNo={}, retCode={}, retMsg={}",
                            orderNo, accepted.retCode(), accepted.retMsg());
                }
                return response(FepAppErrorCodeEnum.FAIL.getCode(), msg, orderNo);
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("支付宝支付申请未拿到业务应答，置 FAIL、NEVER 加黑名单, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                payTxnRepository.writeRequestResult(orderNo, PayTxnRepository.STATUS_FAIL, null, null);
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
     * <b>NEVER 用本地当天日期兜底</b>：跨零点时会把行写进错误的月分区、且与主表账期口径分叉，对账两边
     * 永远对不上，而这种错**建表时和运行时都不会报错**。
     *
     * <p>入向报文里没有这个字段（{@code AlipayTripRequestPayReqDTO} 的 16 个字段里没有它），只能回查主表。
     * 这个依赖是自洽的：请求本来就来自 gate-txn-pay，它不可达时这个请求根本进不来。
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
     * 只读回查：这笔单子在支付中心是不是<b>确定失败</b>。
     *
     * <p>存在的唯一理由是把「支付中心拒绝了这次请求」与「这笔钱没扣成」分开。第二支拿到的非
     * {@code SUCCESS} 里混着幂等拒答（同一笔已成功），拿它直接加黑会把付过钱的用户拉黑。
     *
     * <p><b>判据是保守的：只有明确 {@code FAIL} 才返 true</b>，其余（查询被拒 / 无响应 / 状态判不出 /
     * 抛异常）一律 false。代价是**确认不了就漏加黑**，与 pay-sign 侧
     * {@code PaymentDomainServiceImpl.addBlacklistForPaymentFailure} 同口径 —— 该口径是有意的：
     * 误加黑让已付款乘客过不了闸（当场可见、需人工解黑），漏加黑只是欠费卡多过一次闸（可后续补加），
     * 两者代价不对称。<b>NEVER 反过来「判不出就当失败」。</b>
     *
     * <p>出向 bizData 与 {@code AlipayTxnPayQueryService.buildBizData} 保持同形（{@code orderNo} +
     * {@code cardIssueCode}，签约号有才送），<b>NEVER 为了这次回查再去查一遍支付明细表</b> —— 签约信息
     * 调用方已经拿在手上了。本方法只读、不回写任何本地状态：回写的责任在 payQuery 那条链路上。
     */
    private boolean confirmedFailedByPayQuery(String orderNo, AlipaySignInfo signInfo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", orderNo);
        bizData.put("cardIssueCode", CARD_ISSUE_CODE_ALIPAY);
        if (signInfo != null && StringUtils.hasText(signInfo.getChannelAgreementCode())) {
            bizData.put("channelAgreementNo", signInfo.getChannelAgreementCode());
        }

        PayCenterReply reply;
        try {
            reply = payCenterPort.payQuery(bizData);
        } catch (RuntimeException e) {
            log.error("加黑前回查支付中心抛异常，按「确认不了」处置、不加黑, orderNo={}", orderNo, e);
            return false;
        }

        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                String tradeStatus = firstNonBlank(accepted.field("status"), accepted.field("tradeStatus"));
                log.info("加黑前回查支付中心应答: orderNo={}, retCode={}, retMsg={}, tradeStatus={}",
                        orderNo, accepted.retCode(), accepted.retMsg(), tradeStatus);
                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    log.error("加黑前回查被支付中心拒绝，按「确认不了」处置、不加黑, orderNo={}, retCode={}, retMsg={}",
                            orderNo, accepted.retCode(), accepted.retMsg());
                    return false;
                }
                if (PayCenterTradeStatus.isConfirmedFail(tradeStatus)) {
                    return true;
                }
                log.error("加黑前回查拿到的交易状态不是确定失败，不加黑、MUST 人工到支付中心核对, orderNo={}, tradeStatus={}",
                        orderNo, tradeStatus);
                return false;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("加黑前回查未拿到业务应答，按「确认不了」处置、不加黑, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                return false;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("加黑前回查无响应，按「确认不了」处置、不加黑, orderNo={}", orderNo);
                return false;
            }
        }
    }

    /**
     * 扣款被业务拒绝后把该卡加入黑名单。
     *
     * <p><b>调用前提</b>：{@link #confirmedFailedByPayQuery} 已确认这笔真的失败。
     * <b>NEVER 在别处直接调本方法</b> —— 绕过那次回查就退回了「幂等拒答也加黑」的旧缺陷。
     *
     * <p>处置沿用既有口径：三种结果都只打日志、不落库、不重试 —— 这意味着<b>加黑失败即永久丢失、
     * 该卡仍可过闸</b>。要补偿得先有载体表（本模块目前没有），<b>NEVER 在这里加静默重试</b>。
     */
    private void addBlackListIfNeeded(AlipaySignInfo signInfo, String reason) {
        if (signInfo == null || !StringUtils.hasText(signInfo.getCardId()) || !StringUtils.hasText(signInfo.getThirdUserId())) {
            return;
        }
        String cardId = signInfo.getCardId();
        String blackReason = StringUtils.hasText(reason) ? reason : "地铁扣款失败";
        RpcOutcome outcome = blacklistPort.addBlackList(cardId, signInfo.getThirdUserId(), signInfo.getCardType(), blackReason);
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

    private AlipayTripRequestPayRespDTO response(String retCode, String retMsg, String orderNo) {
        AlipayTripRequestPayRespDTO response = new AlipayTripRequestPayRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        response.setOrderNo(orderNo);
        log.info("支付宝支付申请完成, orderNo={}, retCode={}", orderNo, retCode);
        return response;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}

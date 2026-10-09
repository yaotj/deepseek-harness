package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 退款申请编排（**新表那一份**）：原支付读 {@code ALIPAY_PAY_TXN_DETAIL}、明细落
 * {@code ALIPAY_REFUND_TXN_DETAIL}、汇总回写 {@code ALIPAY_PAY_TXN_DETAIL}。
 *
 * <p><b>为什么要有这一份</b>：{@link AlipayPayRefundServiceImpl} 读的 {@code ALIPAY_PAY_LOG}
 * 自「落单收口到 gate-txn-pay」之后**已无写入方**（{@code PayLogBuilder} 是死代码），于是运营后台点退款
 * 必然返 {@code 9999 原支付记录不存在} —— 2026-09-20 实测：分流已生效、请求确实到了本模块，卡在这一步。
 * 新表里同一笔 {@code orderNo} 有完整的 {@code PAY_STATUS} / {@code AMOUNT} / {@code REFUND_AMOUNT} /
 * {@code CHANNEL_AGREEMENT_NO}，够走完退款。
 *
 * <p><b>与旧实现严格正交、互不调用</b>：旧端点 {@code /internal/alipay/payment/requestRefund} 与
 * {@link AlipayPayRefundServiceImpl} 原样保留作回滚位，本类 NEVER 转发给它、它也 NEVER 反向调本类。
 * 两侧的状态字面量、单号生成、汇总口径各自独立，<b>合并等于让「线上跑的是哪一侧」无法判断</b>。
 *
 * <p>六步，顺序 MUST 不变：① 校验入参 → ② 查原支付并校验「已支付成功」→ ③ <b>幂等短路</b>（同一原订单存在
 * 未收口的退款明细即拒绝）→ ④ 按原支付的渠道协议号回查签约拿逻辑卡号 → ⑤ 落明细 + {@code markRequesting}
 * 并提交 → ⑥ 出网并按应答回写明细。
 *
 * <p><b>本类只负责「申请与受理」，终态与汇总不在这里</b>：退款申请的应答里没有结果字段（见
 * {@link #applyReply}），因此本类最多把明细写到 {@code PROCESSING}；置 {@code SUCCESS}/{@code FAIL}
 * 与刷 {@code ALIPAY_PAY_TXN_DETAIL} 汇总都由退款回调收口
 * （{@code TxnRefundCallbackSettler}）负责。<b>NEVER 在本类里刷汇总。</b>
 *
 * <p><b>③ 排在落库之前是本链路的资金防线</b>：上一笔结果未知（{@code PROCESSING}）时放行第二笔，等于对同一笔
 * 原支付发两次退款。<b>NEVER 挪到落库之后</b> —— 金额校验读的是 {@code ALIPAY_PAY_TXN_DETAIL.REFUND_AMOUNT}
 * 汇总列，而未收口那笔**还没进汇总**，拦不住。
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：本方法内有一次支付中心 HTTP 调用。事务包住它会让行锁持有时长
 * 等于对端响应时长，超过 Druid {@code remove-abandoned-timeout} 后连接被强杀、连「留证据」的 INSERT 一起
 * 回滚 —— pay-sign 侧 2026-08-26 已因此出过循环重推 8 分钟、单请求 287 秒的生产事故。
 *
 * <p><b>已知缺口（本次未修）</b>：本端点无鉴权、无归属校验，按 AGENTS.md §5.2 本应有 —— 它既改状态又出网
 * 发起真实退款。这是 {@code /internal/alipay/**} 全族的既有缺口、不是本次引入的，见控制器类注释。
 */
@Service
public class AlipayTxnRefundService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnRefundService.class);

    /** 支付宝渠道的卡机构编号，出网报文用。 */
    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";
    /** 原支付已成功才允许退款，白名单只有这一个值。 */
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    /** 支付中心 API 名，只用于 {@code ALIPAY_PAY_CENTER_MSG_LOG} 归类。 */
    private static final String API_NAME_REQUEST_REFUND = "requestRefund";

    /**
     * 落库时的初始状态：<b>出网前先 {@code INIT}、再由 {@code markRequesting} 压成
     * {@code PROCESSING}</b>，与支付方向（{@code insert} + {@code markRequesting}）同形。
     * 两条语句两次提交、刻意不合并成一条，是为了让将来的退款重试补偿能复用 {@code markRequesting}
     * 把次数 +1，<b>NEVER 为了省一次往返就在 insert 里直接写 PROCESSING</b>。
     */
    private static final String REFUND_STATUS_INIT = "INIT";
    private static final String REFUND_STATUS_PROCESSING = "PROCESSING";

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;
    private final AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper;
    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final PayCenterPort payCenterPort;
    private final PayCenterProperties payCenterProperties;
    private final AlipayPayCenterMsgLogWriter payCenterMsgLogWriter;

    public AlipayTxnRefundService(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper,
                                 AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper,
                                 AlipaySignInfoMapper alipaySignInfoMapper,
                                 PayCenterPort payCenterPort,
                                 PayCenterProperties payCenterProperties,
                                 AlipayPayCenterMsgLogWriter payCenterMsgLogWriter) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
        this.alipayRefundTxnDetailMapper = alipayRefundTxnDetailMapper;
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.payCenterPort = payCenterPort;
        this.payCenterProperties = payCenterProperties;
        this.payCenterMsgLogWriter = payCenterMsgLogWriter;
    }

    public AlipayTripTxnRefundRespDTO requestRefund(AlipayTripTxnRefundReqDTO request) {
        log.info("接收到支付宝退款申请报文（新表链路）: {}", JSON.toJSONString(request));
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        String orderNo = request.getOrderNo().trim();

        AlipayPayTxnDetail payTxn = loadSettledPayTxn(orderNo);
        rejectIfProcessingRefundExists(orderNo);

        String channelAgreementNo = payTxn.getChannelAgreementNo();
        if (!StringUtils.hasText(channelAgreementNo)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录缺少渠道协议号");
        }
        String cardNum = resolveCardNum(channelAgreementNo);
        int refundAmount = resolveRefundAmount(request.getRefundAmount(), payTxn);

        String refundOrderNo = "R" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        String txnDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        openRefund(orderNo, refundOrderNo, txnDate, refundAmount, request.getRefundReason());

        return callPayCenter(orderNo, refundOrderNo, txnDate, cardNum, channelAgreementNo, refundAmount);
    }

    /**
     * 查原支付并校验「可退」。
     *
     * <p><b>白名单只认 {@code SUCCESS}</b>（AGENTS.md §5.2：状态机用白名单不用黑名单）——
     * {@code PROCESSING} 的单子钱还没确定扣没扣，退它是凭空出账。
     *
     * <p>文案「原支付记录不存在」与旧链路<b>逐字一致</b>，这是刻意的：运维侧已按这句话排错。要区分是哪条链路
     * 报的，看日志里有没有「（新表链路）」那个后缀，<b>NEVER 靠改文案来区分</b>。
     */
    private AlipayPayTxnDetail loadSettledPayTxn(String orderNo) {
        AlipayPayTxnDetail payTxn = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        if (payTxn == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录不存在");
        }
        if (!PAY_STATUS_SUCCESS.equals(payTxn.getPayStatus())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付订单未支付成功");
        }
        return payTxn;
    }

    /** 幂等短路：同一原订单存在未收口（{@code PROCESSING}）的退款明细即拒绝新申请。 */
    private void rejectIfProcessingRefundExists(String orderNo) {
        int processingCount = alipayRefundTxnDetailMapper.countByOrderNoAndStatus(orderNo, REFUND_STATUS_PROCESSING);
        if (processingCount > 0) {
            log.error("该订单存在未收口的退款明细，拒绝重复退款，MUST 人工到支付中心核对上一笔结果, orderNo={}, processingCount={}",
                    orderNo, processingCount);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该订单存在处理中的退款，请先确认上一笔结果");
        }
    }

    /**
     * 按渠道协议号回查签约拿逻辑卡号 —— 出网报文的 {@code cardNum} 键只能从这里来。
     *
     * <p><b>为什么不让调用方送卡号</b>：卡号定位的是扣款账户，送错就把钱退到别人账上；而
     * {@code ALIPAY_REFUND_TXN_DETAIL} 里**刻意没有** {@code CARD_ID} 列（主体维度的权威是
     * {@code GATE_TXN_PAY}，见该实体类注释），所以卡号只在出网那一刻用、不落本表。
     *
     * <p>查不到就拒绝、<b>NEVER 送空卡号出网</b>。另注：{@code selectByChannelAgreementCode} 不带
     * {@code SIGN_STATUS} 谓词，<b>这是刻意的</b> —— 用户已解约但历史订单仍可退，加上 {@code SIGNED}
     * 会把「解约后退旧单」整条挡死。
     *
     * <p>该协议号在 {@code ALIPAY_SIGN_INFO} 上<b>没有唯一约束</b>（2026-09-20 实测全表非空仅 1 行，
     * 样本不足以证明全局唯一）。真出现一码多行时 MyBatis 会抛 {@code TooManyResultsException}、
     * 本次退款失败 —— <b>那是有意的拒绝，NEVER 改成 {@code LIMIT 1} 或取第一行</b>：分不清该退哪张卡时，
     * 不退比退错强。
     */
    private String resolveCardNum(String channelAgreementNo) {
        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByChannelAgreementCode(channelAgreementNo);
        String cardNum = signInfo != null ? signInfo.getCardId() : null;
        if (!StringUtils.hasText(cardNum)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(),
                    "退款申请未查到签约信息, channelAgreementNo=" + channelAgreementNo);
        }
        return cardNum;
    }

    /**
     * 算本次退款金额（单位分）。
     *
     * <p>可退余额 = {@code AMOUNT} - {@code REFUND_AMOUNT}（后者是按 {@code ALIPAY_REFUND_TXN_DETAIL}
     * 的成功明细重算出来的汇总列）。入参为空即全额退 —— 运维侧全额退款一直是不传这个字段，
     * <b>NEVER 把它改成必填</b>。
     */
    private int resolveRefundAmount(String requestRefundAmount, AlipayPayTxnDetail payTxn) {
        int paid = payTxn.getAmount() != null ? payTxn.getAmount() : 0;
        int refunded = payTxn.getRefundAmount() != null ? payTxn.getRefundAmount() : 0;
        int available = paid - refunded;
        if (available <= 0) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(),
                    "原支付订单已无可退金额, orderNo=" + payTxn.getOrderNo());
        }
        if (!StringUtils.hasText(requestRefundAmount)) {
            return available;
        }
        int current;
        try {
            current = Integer.parseInt(requestRefundAmount.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "退款金额格式异常: " + requestRefundAmount);
        }
        if (current <= 0) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "退款金额必须大于0");
        }
        if (current > available) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款金额超出可退金额");
        }
        return current;
    }

    /**
     * 开一次退款尝试：落一行明细并提交、随后 {@code markRequesting} 压成 {@code PROCESSING}。
     *
     * <p><b>顺序 MUST 是「落库并提交 → 出网」</b>：先留痕才有失败可核对、对账有源。
     *
     * <p>撞 {@code UK_ARTD_REFUND_ORDER}（{@code REFUND_ORDER_NO + TXN_DATE}）时的处置与扣费方向
     * <b>刻意不同</b>：扣费方向撞唯一索引是「同一 {@code orderNo} 已有人落好」，可以按幂等继续；
     * 退款方向的 {@code refundOrderNo} 是**本次现生成的**，撞上只能是重复提交 —— 继续下去等于对同一笔原支付
     * 发两次退款，<b>MUST 拒绝、NEVER 按幂等放行</b>。
     *
     * <p><b>NEVER 把这条兜底当成「并发双提交的防线」</b>：两条并发请求各自生成的 {@code refundOrderNo}
     * 本就不同、都能插入成功。同一 {@code orderNo} 的并发双提交只靠上面
     * {@link #rejectIfProcessingRefundExists} 的状态短路（「先查后插」、库层没有互斥）。
     */
    private void openRefund(String orderNo, String refundOrderNo, String txnDate, int refundAmount,
                            String refundReason) {
        AlipayRefundTxnDetail record = new AlipayRefundTxnDetail();
        record.setRefundOrderNo(refundOrderNo);
        record.setOrderNo(orderNo);
        record.setRefundStatus(REFUND_STATUS_INIT);
        record.setRefundAmount(refundAmount);
        record.setRefundReason(refundReason);
        record.setMerchantRefundNo(refundOrderNo);
        record.setRequestCount(0);
        record.setTxnDate(txnDate);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            alipayRefundTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!RefundLogRepository.isIntegrityViolation(e)) {
                throw e;
            }
            log.warn("退款明细插入命中唯一索引，判定为重复提交, orderNo={}, refundOrderNo={}", orderNo, refundOrderNo);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请重复提交");
        }
        int marked = alipayRefundTxnDetailMapper.markRequesting(refundOrderNo, txnDate);
        if (marked == 0) {
            log.error("退款明细刚插入却标记不到，MUST 人工核对 ALIPAY_REFUND_TXN_DETAIL, orderNo={}, refundOrderNo={}, txnDate={}",
                    orderNo, refundOrderNo, txnDate);
            throw new BusinessException(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "退款明细留痕失败，请稍后重试");
        }
    }

    /**
     * 组装 bizData 并出网。
     *
     * <p>七个键名是供方契约，<b>NEVER 改</b>（含 {@code cardNum} 这个与本模块内部用词不一致的键）。
     * {@code refundReason} <b>NEVER 加进 bizData</b> —— 对端契约里没有这个键，只落本方明细留痕。
     *
     * <p><b>{@code notifyUrl} 是契约 §3.1 的必填键</b>，支付中心只往「本次请求带的这个地址」推退款结果。
     * 配置为空时只打 WARN、不送该键、<b>NEVER 阻断退款申请</b>：申请本身能成功，缺的只是终态回调。
     *
     * <p>出网报文与应答原文落 {@code ALIPAY_PAY_CENTER_MSG_LOG}（本表没有 {@code REQUEST_BODY} /
     * {@code RESPONSE_BODY} 列，这是唯一留证处）。<b>NEVER 因为「新表少两列」就把报文塞进 {@code REMARK}</b>。
     */
    private AlipayTripTxnRefundRespDTO callPayCenter(String orderNo, String refundOrderNo, String txnDate,
                                                    String cardNum, String channelAgreementNo, int refundAmount) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", orderNo);
        bizData.put("cardIssueCode", CARD_ISSUE_CODE_ALIPAY);
        bizData.put("cardNum", cardNum);
        bizData.put("channelAgreementNo", channelAgreementNo);
        bizData.put("refundAmount", String.valueOf(refundAmount));
        bizData.put("refundOrderNo", refundOrderNo);
        String refundNotifyUrl = payCenterProperties.getRefundNotifyUrl();
        if (StringUtils.hasText(refundNotifyUrl)) {
            bizData.put("notifyUrl", refundNotifyUrl);
        } else {
            log.warn("pay.center.refund-notify-url 未配置，本次退款申请不送 notifyUrl，支付中心将无法回推退款结果，MUST 配置后重试, orderNo={}, refundOrderNo={}",
                    orderNo, refundOrderNo);
        }

        log.info("支付宝出行-退款申请（新表链路）,调用支付中心退款接口,请求参数: {}", JSON.toJSONString(bizData));
        long startedAt = System.currentTimeMillis();
        PayCenterReply reply;
        try {
            reply = payCenterPort.requestRefund(bizData);
        } catch (RuntimeException e) {
            payCenterMsgLogWriter.record(orderNo, txnDate, API_NAME_REQUEST_REFUND, refundOrderNo, bizData, null,
                    System.currentTimeMillis() - startedAt, "退款申请出网异常: " + e.getClass().getSimpleName());
            throw e;
        }
        long elapsedMs = System.currentTimeMillis() - startedAt;
        payCenterMsgLogWriter.record(orderNo, txnDate, API_NAME_REQUEST_REFUND, refundOrderNo, bizData, reply,
                elapsedMs, null);
        log.info("支付宝退款申请（新表链路）,支付中心响应结果: code={}, success={}, msg={}",
                reply.code(), reply.success(), reply.msg());

        AlipayTripTxnRefundRespDTO response = applyReply(reply, orderNo, refundOrderNo, txnDate);
        log.info("支付宝退款申请完成（新表链路）, orderNo={}, refundOrderNo={}, cardNum={}, channelAgreementNo={}, refundAmount={}, retCode={}",
                orderNo, refundOrderNo, cardNum, channelAgreementNo, refundAmount, response.getRetCode());
        return response;
    }

    /**
     * 按支付中心应答回写明细。
     *
     * <p><b>退款申请只判「受理」，NEVER 判「退款成功」</b> —— 这是 2026-09-20 实测 + 契约取证后定下的口径，
     * 与支付方向刻意不同：
     * <ul>
     *   <li>契约 §3.1 请求退款的应答里**一个结果字段都没有**（`data` 只有 {@code merchantRefundNo} /
     *       {@code refundNo} / {@code channelRefundNo} / {@code refundTime}），退款结果只出现在
     *       §5.2 回调的 {@code refundResult}（SUCCESS/FAIL/PROCESSING）与 §3.2 查询的 {@code status}；</li>
     *   <li>实测本次应答体就是 <b>{@code {"code":200,"msg":"操作成功"}}</b> —— 顶层 code 成功、**连 data 都没有**。</li>
     * </ul>
     * 所以这里**能走到 {@code Accepted} 就等于受理成功**（{@code PayCenterRpcAdapter} 已按
     * {@code code==200 || success==true} 判过传输层），明细一律置 {@code PROCESSING} 等回调收口。
     *
     * <p><b>NEVER 改回 {@code "SUCCESS".equals(accepted.retCode())}</b>：那是从支付方向
     * （{@code AlipayPayRequestServiceImpl}，它的应答**确实**带 {@code data.retCode}）抄过来的判据，
     * 退款应答里根本没有 {@code retCode} 这个键 ⇒ 恒为 null ⇒ 把每一笔已受理的退款都写成 {@code FAIL}。
     * 2026-09-20 首单 {@code R1789899554336b230c487} 就是这么错的：库里 {@code FAIL}、
     * {@code REMARK} 却是「操作成功」。<b>「置 FAIL」比「留 PROCESSING」危险得多</b> ——
     * 钱已经退出去了，落 FAIL 会让人再退一次。
     *
     * <p><b>也 NEVER 在这里刷 {@code updateRefundSummary}</b>：汇总只应在拿到终态 {@code SUCCESS}
     * （回调或回查）那一刻重算。受理时就把已退金额加上去，等于让一笔还没确认的退款占掉可退余额。
     *
     * <p>{@code Rejected} / {@code NoAnswer} 同样保持 {@code PROCESSING}、只回写 {@code REMARK}。
     * 两者处置相同仍各写一个 case：那是「本方向的」结论，{@code requestPay} 对两者处置不同，
     * <b>NEVER 因为这里能合并就去合并那处</b>。
     */
    private AlipayTripTxnRefundRespDTO applyReply(PayCenterReply reply, String orderNo, String refundOrderNo,
                                                  String txnDate) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝退款申请（新表链路）已受理,支付中心应答: orderNo={}, refundOrderNo={}, code={}, msg={}",
                        orderNo, refundOrderNo, accepted.code(), accepted.msg());
                acceptRefund(accepted, refundOrderNo, txnDate);
                return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "退款申请已受理", refundOrderNo);
            }
            case PayCenterReply.Rejected rejected -> {
                return keepProcessing(orderNo, refundOrderNo, txnDate, rejected.code(), rejected.msg());
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                return keepProcessing(orderNo, refundOrderNo, txnDate, noAnswer.code(), noAnswer.msg());
            }
        }
    }

    /**
     * 受理成功：明细留 {@code PROCESSING}，把契约 §3.1 应答 {@code data} 里的三个单号与退款时间回填。
     *
     * <p>四个键名出自契约 §3.1 应答参数表，<b>不是猜的</b>；但实测应答可能**整个 data 都没有**
     * （{@code {"code":200,"msg":"操作成功"}}），此时 {@code accepted.field(...)} 全是 null ——
     * 而 {@code updateRequestResult} 对这几列是 {@code NVL(#{...}, 原列)}，传 null 就是不动，
     * 正好把位置留给回调/回查去填。<b>NEVER 为了「让它有值」就在这里塞占位符</b>。
     *
     * <p>{@code merchantRefundNo} 兜底用我方 {@code refundOrderNo}：契约里它就是「商户退款单号」，
     * 对端不回显时它等于我方送出去的那个值。
     */
    private void acceptRefund(PayCenterReply.Accepted accepted, String refundOrderNo, String txnDate) {
        AlipayRefundTxnDetail record = new AlipayRefundTxnDetail();
        record.setRefundOrderNo(refundOrderNo);
        record.setTxnDate(txnDate);
        record.setRefundStatus(REFUND_STATUS_PROCESSING);
        record.setMerchantRefundNo(accepted.field("merchantRefundNo") != null
                ? accepted.field("merchantRefundNo") : refundOrderNo);
        record.setRefundNo(accepted.field("refundNo"));
        record.setChannelRefundNo(accepted.field("channelRefundNo"));
        record.setRefundTime(accepted.field("refundTime"));
        record.setRemark(truncate("退款申请已受理: " + accepted.msg()));
        int affected = alipayRefundTxnDetailMapper.updateRequestResult(record);
        if (affected == 0) {
            log.error("退款明细回写未命中，MUST 人工核对 ALIPAY_REFUND_TXN_DETAIL, refundOrderNo={}, txnDate={}",
                    refundOrderNo, txnDate);
        }
    }

    /** 拿不到业务应答：明细保持 {@code PROCESSING}，只回写 {@code REMARK} 留痕，等人工核对或回查补偿收口。 */
    private AlipayTripTxnRefundRespDTO keepProcessing(String orderNo, String refundOrderNo, String txnDate,
                                                     Integer code, String msg) {
        log.error("支付宝退款申请未拿到支付中心业务应答，退款结果未知、明细保持 PROCESSING，MUST 人工核对, orderNo={}, refundOrderNo={}, code={}, msg={}",
                orderNo, refundOrderNo, code, msg);
        writeResult(refundOrderNo, txnDate, REFUND_STATUS_PROCESSING, "退款结果未知，待人工核对: code=" + code);
        return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "退款结果未知，请稍后核对", refundOrderNo);
    }

    /** 回写明细状态与备注；{@code REMARK} 是本表唯一能放人可读结论的列。 */
    private void writeResult(String refundOrderNo, String txnDate, String refundStatus, String remark) {
        AlipayRefundTxnDetail record = new AlipayRefundTxnDetail();
        record.setRefundOrderNo(refundOrderNo);
        record.setTxnDate(txnDate);
        record.setRefundStatus(refundStatus);
        record.setRemark(truncate(remark));
        int affected = alipayRefundTxnDetailMapper.updateRequestResult(record);
        if (affected == 0) {
            log.error("退款明细回写未命中，MUST 人工核对 ALIPAY_REFUND_TXN_DETAIL, refundOrderNo={}, txnDate={}, refundStatus={}",
                    refundOrderNo, txnDate, refundStatus);
        }
    }

    /** {@code REMARK} 是 {@code VARCHAR2(512 CHAR)}，超长会 {@code ORA-12899} 把已出网的退款变成报错。 */
    private String truncate(String remark) {
        if (remark == null || remark.length() <= 512) {
            return remark;
        }
        return remark.substring(0, 512);
    }

    private AlipayTripTxnRefundRespDTO response(String retCode, String retMsg, String refundOrderNo) {
        AlipayTripTxnRefundRespDTO response = new AlipayTripTxnRefundRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        response.setRefundOrderNo(refundOrderNo);
        return response;
    }
}

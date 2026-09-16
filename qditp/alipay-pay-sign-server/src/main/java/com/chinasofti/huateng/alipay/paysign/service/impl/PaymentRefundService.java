package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentRefundService {
    private static final Logger log = LoggerFactory.getLogger(PaymentRefundService.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    /**
     * 退款明细状态。{@code PROCESSING} 是本次新增的中间态：
     * 明细在调支付中心**之前**就以该状态落地，业务应答到达后才收口成 SUCCESS / FAIL。
     *
     * <p>此前初始状态直接写 {@code FAIL} 而 {@code RESULT_MSG} 写「退款处理中」，
     * 两者自相矛盾，且「远端不可达」与「业务拒绝」都落成同一个 FAIL，无法区分。</p>
     */
    private static final String REFUND_STATUS_PROCESSING = "PROCESSING";
    private static final String REFUND_STATUS_SUCCESS = "SUCCESS";
    private static final String REFUND_STATUS_FAIL = "FAIL";
    private static final String RESULT_CODE_INIT = "INIT";

    @Autowired
    private AlipayPayLogMapper alipayPayLogMapper;

    @Autowired
    private AlipayRefundLogMapper alipayRefundLogMapper;

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private RefundAmountCalculator refundAmountCalculator;

    @Autowired
    private PayCenterClient payCenterClient;

    /**
     * 支付宝出行退款申请。
     *
     * <p><b>NEVER 给本方法加 {@code @Transactional}</b>（2026-09-14 移除）：方法体内要调支付中心
     * （OkHttp readTimeout 30s），事务包住网络调用会把行锁持有时长拉成对端响应时长，
     * 上游对同一笔的重试全部堆在同一行上串行等待，超过 Druid {@code remove-abandoned-timeout}
     * 后连接被强杀、{@code commit} 抛 {@code connection closed}，**整个事务连同刚插入的退款明细一起丢弃**
     * —— 而支付中心那边可能已经受理了退款。这与 AGENTS.md §5.2 记录的 2026-08-26 生产事故同一成因。
     * 去掉事务后每条 SQL 自动提交，明细行在调远端之前就已落地，是这笔退款存在过的唯一凭据。</p>
     *
     * <p>顺序 MUST 保持「先落 PROCESSING 明细 → 再调支付中心 → 按业务应答收口」。
     * 三种收口分别对应 {@code Ok} / {@code BizRejected} / {@code Unreachable}：</p>
     * <ul>
     *   <li>业务成功 → 明细置 SUCCESS，再按明细重算原订单退款汇总；</li>
     *   <li>业务明确拒绝 → 明细置 FAIL（终态，允许重新申请）；</li>
     *   <li><b>拿不到业务应答（响应为空 / 传输层失败）→ 明细保持 PROCESSING</b>，
     *       返非 0000 并打 ERROR。<b>NEVER 在这个分支置 FAIL</b>：远端可能已受理，
     *       置 FAIL 会让汇总少记一笔已退金额，随后同一笔还能再退一次，直接造成重复退款。</li>
     * </ul>
     */
    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        AlipayTripRequestRefundRespDTO response = new AlipayTripRequestRefundRespDTO();
        log.info("接收到支付宝退款申请报文: {}", JSON.toJSONString(request));

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }

        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录不存在");
        }

        if (!"SUCCESS".equals(payLog.getPayStatus())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付订单未支付成功");
        }

        String cardNum = payLog.getCardId();
        if (!StringUtils.hasText(cardNum)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录缺少逻辑卡号");
        }

        // 幂等短路：同一原订单只要还有未收口（PROCESSING）的退款明细，就拒绝新的退款申请。
        // 本表没有调用方提供的幂等键（REFUND_ORDER_NO 每次新生成），这条状态判断是唯一能挡住
        // 「远端已受理、本地结果未回写」时重复退款的防线，NEVER 去掉。
        int processingCount = alipayRefundLogMapper.countByOrderNoAndStatus(request.getOrderNo(), REFUND_STATUS_PROCESSING);
        if (processingCount > 0) {
            log.error("该订单存在未收口的退款明细，拒绝重复退款，MUST 人工到支付中心核对上一笔结果, orderNo={}, processingCount={}",
                    request.getOrderNo(), processingCount);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该订单存在处理中的退款，请先确认上一笔结果");
        }

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByCardIdAndChannel(cardNum, CHANNEL_ALIPAY);
        String channelAgreementNo = signInfo != null ? signInfo.getChannelAgreementCode() : null;
        if (!StringUtils.hasText(channelAgreementNo)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请未查到签约信息, cardNum=" + cardNum);
        }

        String refundAmount = refundAmountCalculator.resolveRefundAmount(request.getRefundAmount(), payLog);
        refundAmountCalculator.validateRefundAmount(refundAmount, payLog);

        String refundOrderNo = "R" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);

        AlipayRefundLog refundLog = new AlipayRefundLog();
        refundLog.setRefundSeq(UUID.randomUUID().toString().replaceAll("-", ""));
        refundLog.setThirdUserId(payLog.getThirdUserId());
        refundLog.setCardId(payLog.getCardId());
        refundLog.setOrderNo(request.getOrderNo());
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundStatus(REFUND_STATUS_PROCESSING);
        refundLog.setCardIssueCode("0007");
        refundLog.setChannelAgreementNo(channelAgreementNo);
        refundLog.setRefundOrderNo(refundOrderNo);
        refundLog.setRequestBody(JSON.toJSONString(request));
        refundLog.setResponseBody("");
        refundLog.setResultCode(RESULT_CODE_INIT);
        refundLog.setResultMsg("退款处理中");
        refundLog.setDeleteFlag("0");
        refundLog.setVersion("1");
        refundLog.setCreateTime(LocalDateTime.now());
        refundLog.setUpdateTime(LocalDateTime.now());
        try {
            alipayRefundLogMapper.insert(refundLog);
        } catch (Exception e) {
            // UK_ARL_REFUND_ORDER_NO 竞态兜底。沿 getCause 链判定，NEVER 只看最外层类名：
            // 本模块虽然当前没开 tracing，但一旦打开，观测切面会把异常重新包一层
            // （AGENTS.md §5.2 记录的 ADR-D53），只 catch DuplicateKeyException 会静默落空。
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.warn("退款明细插入命中唯一索引，判定为重复提交, orderNo={}, refundOrderNo={}", request.getOrderNo(), refundOrderNo);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请重复提交");
        }

        Map<String, Object> bizDataMap = new java.util.LinkedHashMap<>();
        bizDataMap.put("orderNo", request.getOrderNo());
        bizDataMap.put("cardIssueCode", "0007");
        bizDataMap.put("cardNum", cardNum);
        bizDataMap.put("channelAgreementNo", channelAgreementNo);
        bizDataMap.put("refundAmount", refundAmount);
        bizDataMap.put("refundOrderNo", refundOrderNo);

        log.info("支付宝出行-退款申请,调用支付中心退款接口,请求参数: {}", JSON.toJSONString(bizDataMap));
        com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse payCenterResponse = payCenterClient.requestRefund(bizDataMap);
        log.info("支付宝退款申请,支付中心响应结果: code={}, success={}, msg={}",
                payCenterResponse != null ? payCenterResponse.getCode() : "null",
                payCenterResponse != null ? payCenterResponse.getSuccess() : "null",
                payCenterResponse != null ? payCenterResponse.getMsg() : "null");

        boolean transportOk = payCenterResponse != null
                && ((payCenterResponse.getCode() != null && payCenterResponse.getCode() == 200)
                    || Boolean.TRUE.equals(payCenterResponse.getSuccess()));
        if (!transportOk) {
            // 拿不到业务应答：远端可能已受理，明细保持 PROCESSING，只回写响应体留证。
            // NEVER 置 FAIL —— 汇总会少记一笔已退金额，同一笔随后还能再退一次。
            log.error("支付宝退款申请未拿到支付中心业务应答，退款结果未知、明细保持 PROCESSING，MUST 人工核对, orderNo={}, refundOrderNo={}, code={}, msg={}",
                    request.getOrderNo(), refundOrderNo,
                    payCenterResponse != null ? payCenterResponse.getCode() : "null",
                    payCenterResponse != null ? payCenterResponse.getMsg() : "null");
            alipayRefundLogMapper.updateRefundStatus(
                    refundLog.getRefundSeq(),
                    REFUND_STATUS_PROCESSING,
                    RESULT_CODE_INIT,
                    "退款结果未知，待人工核对",
                    JSON.toJSONString(payCenterResponse),
                    LocalDateTime.now()
            );
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("退款结果未知，请稍后核对");
            return response;
        }

        // 与 requestPay / payQuery 同一套判定：HTTP 与网关层通了之后，还要解开 data 看业务 retCode。
        // 此前只判 code==200 就当退款成功，于是「网关受理、业务拒绝」会被写成 SUCCESS 并进汇总，
        // 账面凭空多出一笔已退金额。
        String dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "retCode");
        if (dataRetCode == null) {
            dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "returnCode");
        }
        String dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "retMsg");
        if (dataRetMsg == null) {
            dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "returnMsg");
        }
        log.info("支付宝退款申请,支付中心解密后数据: retCode={}, retMsg={}", dataRetCode, dataRetMsg);

        boolean refundSuccess = "SUCCESS".equals(dataRetCode);
        String resultCode = refundSuccess ? FepAppErrorCodeEnum.SUCCESS.getCode() : FepAppErrorCodeEnum.FAIL.getCode();
        String resultMsg;
        if (refundSuccess) {
            resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "退款成功";
        } else {
            resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg
                    : (StringUtils.hasText(payCenterResponse.getMsg()) ? payCenterResponse.getMsg() : "退款失败");
        }

        alipayRefundLogMapper.updateRefundStatus(
                refundLog.getRefundSeq(),
                refundSuccess ? REFUND_STATUS_SUCCESS : REFUND_STATUS_FAIL,
                resultCode,
                resultMsg,
                JSON.toJSONString(payCenterResponse),
                LocalDateTime.now()
        );

        if (refundSuccess) {
            // 汇总 MUST 在明细置为 SUCCESS 之后执行：SQL 是按 ALIPAY_REFUND_LOG 重算的，
            // 顺序颠倒会漏掉本笔。金额与状态都不再由这里算，避免用 UPDATE 前的旧快照做加法、
            // 以及重复执行重复累加（详见 AlipayPayLogMapper.xml 内注释）。
            int summaryAffected = alipayPayLogMapper.updateRefundSummary(request.getOrderNo());
            if (summaryAffected == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 ALIPAY_PAY_LOG 与 ALIPAY_REFUND_LOG, orderNo={}, refundOrderNo={}",
                        request.getOrderNo(), refundOrderNo);
            }
        }

        response.setRetCode(resultCode);
        response.setRetMsg(resultMsg);
        log.info("支付宝退款申请完成, orderNo={}, refundOrderNo={}, cardNum={}, channelAgreementNo={}, refundAmount={}, status={}",
                request.getOrderNo(), refundOrderNo, cardNum, channelAgreementNo, refundAmount, refundSuccess ? "SUCCESS" : "FAIL");
        return response;
    }

    /**
     * 沿 {@code getCause()} 链判断是否为唯一约束冲突。
     *
     * <p>样板取自 {@code card-pool-server} 的 {@code CardPoolServiceImpl}：
     * <b>NEVER 只 catch 最外层的 {@code DuplicateKeyException}</b>——观测切面一旦生效
     * 会把它重新包一层，兜底 catch 静默落空（AGENTS.md §5.2 / ADR-D53）。</p>
     */
    private boolean isIntegrityViolation(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}


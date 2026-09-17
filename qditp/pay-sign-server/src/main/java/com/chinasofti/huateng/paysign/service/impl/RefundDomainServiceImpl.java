package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PayRefundRules.buildPayRefundDetail;
import static com.chinasofti.huateng.paysign.support.PayRefundRules.fillRefundResponseFields;
import static com.chinasofti.huateng.paysign.support.PayRefundRules.validateRefundPayTxn;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildRequestRefundBizData;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestRefund;
import static com.chinasofti.huateng.paysign.support.PaySignValues.convertPayStatus;
import static com.chinasofti.huateng.paysign.support.PaySignValues.defaultString;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.RefundGatewayPort;
import com.chinasofti.huateng.paysign.service.RefundDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 退款领域服务：退款发起（支付 API 3.1）与两套退款补偿的真实现。 */
@Service
public class RefundDomainServiceImpl implements RefundDomainService {
    private static final Logger log = LoggerFactory.getLogger(RefundDomainServiceImpl.class);

    private final PayTxnDetailMapper payTxnDetailMapper;
    private final PayRefundDetailMapper payRefundDetailMapper;
    /** 支付中心**退款方向**出向调用的唯一出口（2026-09-16，ADR-D113 续）。 */
    private final RefundGatewayPort refundGatewayPort;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public RefundDomainServiceImpl(
            PayTxnDetailMapper payTxnDetailMapper,
            PayRefundDetailMapper payRefundDetailMapper,
            RefundGatewayPort refundGatewayPort) {
        this.payTxnDetailMapper = payTxnDetailMapper;
        this.payRefundDetailMapper = payRefundDetailMapper;
        this.refundGatewayPort = refundGatewayPort;
    }

    /** 支付 API 3.1 请求退款。 */
    /** 本方法 NEVER 加回 @Transactional（批次 5B / 2026-09-15 摘除，ADR-D8 三件套同批完成）。 */
    @Override
    public RequestRefundResult requestRefund(RequestRefundReqDTO request) {
        RequestRefundResult response = new RequestRefundResult();
        try {
            String validMsg = validateRequestRefund(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_REFUND 参数校验失败, request={}, response={}", JSON.toJSONString(request), JSON.toJSONString(response));
                return response;
            }

            PayTxnDetail payTxn = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
            validMsg = validateRefundPayTxn(payTxn, request.getRefundAmount());
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_REFUND 原支付订单校验失败, request={}, payTxn={}, response={}",
                        JSON.toJSONString(request), JSON.toJSONString(payTxn), JSON.toJSONString(response));
                return response;
            }

            PayRefundDetail refundDetail = buildPayRefundDetail(request, payTxn);
            payRefundDetailMapper.insert(refundDetail);

            Map<String, Object> bizData = buildRequestRefundBizData(refundDetail, payTxn);
            payRefundDetailMapper.markRequesting(refundDetail.getRefundOrderNo(), refundDetail.getTxnDate(), JSON.toJSONString(bizData));
            log.info("REQUEST_REFUND 调用支付平台, orderNo={}, refundOrderNo={}, bizData={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(bizData));
            GatewayReply reply = refundGatewayPort.requestRefund(bizData);
            PaySignGatewayResponse gatewayResponse = reply.raw();
            log.info("REQUEST_REFUND 支付平台返回, orderNo={}, refundOrderNo={}, gatewayResponse={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(gatewayResponse));

            response.setOrderNo(refundDetail.getOrderNo());
            response.setRefundOrderNo(refundDetail.getRefundOrderNo());
            fillGatewayFields(response, reply);

            if (reply instanceof GatewayReply.Rejected rejected) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, rejected.messageOr("请求退款接口失败"));
                fillGatewayFields(response, reply);
                updateRefundRequestResult(refundDetail, "RETRY", response, gatewayResponse);
                return response;
            }
            fillSuccess(response);
            fillRefundResponseFields(response, refundDetail, gatewayResponse);
            updateRefundRequestResult(refundDetail, "SUCCESS", response, gatewayResponse);
            int summaryAffected = payTxnDetailMapper.updateRefundSummary(payTxn.getOrderNo());
            if (summaryAffected == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, orderNo={}, refundOrderNo={}",
                        payTxn.getOrderNo(), refundDetail.getRefundOrderNo());
            }
            return response;
        } catch (Exception e) {
            log.error("处理请求退款异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /** 退款回查的扫描窗口（天）：只回查最近 N 天的 TXN_DATE，同时用于分区裁剪。 */
    private static final int REFUND_QUERY_SCAN_DAYS = 7;

    /** 距最近一次发起退款至少多少分钟才回查，避开正常同步应答的时间窗。 */
    private static final int REFUND_QUERY_STALE_MINUTES = 5;

    /** 未得终态时的退避间隔（秒），写进 NEXT_REQUEST_TIME。 */
    private static final int REFUND_QUERY_RETRY_DELAY_SECONDS = 300;

    /** 单轮扫描上限，与解约侧的 BATCH_SIZE 口径一致。 */
    private static final int REFUND_QUERY_BATCH_SIZE = 200;

    /** 退款回查补偿（批次 5B 新增，与 requestRefund 摘事务同批 —— ADR-D8 要求「移出事务 + 落状态 + 补偿」一起做完）。 */
    @Override
    public CompensateNotifyRespDTO compensateRefundQuery() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            if (!refundGatewayPort.refundQueryConfigured()) {
                log.error("未配置 pay.sign.refund-query-url，退款回查无法进行，停在 PROCESSING 的退款单本轮无人收口");
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "未配置退款查询地址");
                return response;
            }
            String txnDateFrom = LocalDateTime.now().minusDays(REFUND_QUERY_SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<PayRefundDetail> pending = payRefundDetailMapper.selectCompensableRefundQuery(
                    txnDateFrom, REFUND_QUERY_STALE_MINUTES, REFUND_QUERY_BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            OutboxScan.Result scan = OutboxScan.run(pending,
                    this::settleRefundByQuery,
                    row -> log.warn("退款回查本轮未收口，等下次重扫, refundOrderNo={}, txnDate={}",
                            row.getRefundOrderNo(), row.getTxnDate()),
                    (row, e) -> log.error("单条退款回查异常，NEVER 因此中断整批, refundOrderNo={}, txnDate={}",
                            row.getRefundOrderNo(), row.getTxnDate(), e));
            response.setScanned(scan.scanned());
            response.setSubmitted(scan.success());
            response.setSkipped(scan.failed());
            log.info("退款回查补偿完成, txnDateFrom={}, scanned={}, settled={}, pendingAgain={}",
                    txnDateFrom, scan.scanned(), scan.success(), scan.failed());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("退款回查补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 回查一笔停在 {@code PROCESSING} 的退款并尝试收口。
     *
     * @return {@code true} 仅当「支付中心给出终态」且「CAS 真的推进了这一行」
     */
    private boolean settleRefundByQuery(PayRefundDetail row) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        // MUST 同时送 merchantRefundNo 与 refundOrderNo（ADR-D92 实测：只送 refundOrderNo 返 9999）。
        bizData.put("refundOrderNo", row.getRefundOrderNo());
        bizData.put("merchantRefundNo", row.getRefundOrderNo());
        GatewayReply queryReply = refundGatewayPort.queryRefund(bizData);
        log.info("退款回查支付中心返回, refundOrderNo={}, txnDate={}, gatewayResponse={}",
                row.getRefundOrderNo(), row.getTxnDate(), JSON.toJSONString(queryReply.raw()));

        String settledStatus = resolveRefundQueryStatus(queryReply);
        if (settledStatus == null) {
            int delayed = payRefundDetailMapper.delayNextRefundQuery(
                    row.getRefundOrderNo(), row.getTxnDate(), REFUND_QUERY_RETRY_DELAY_SECONDS);
            if (delayed == 0) {
                log.info("退款回查未得终态且退避 CAS 命中 0 行，说明这一行已被别的路径收口, refundOrderNo={}, txnDate={}",
                        row.getRefundOrderNo(), row.getTxnDate());
            }
            return false;
        }

        PayRefundDetail update = new PayRefundDetail();
        update.setRefundOrderNo(row.getRefundOrderNo());
        update.setTxnDate(row.getTxnDate());
        update.setRefundStatus(settledStatus);
        PaySignGatewayResponse gatewayResponse = queryReply.raw();
        Map<String, Object> data = gatewayResponse.getData();
        update.setMerchantRefundNo(stringValue(data.get("merchantRefundNo"), null));
        update.setRefundNo(stringValue(data.get("refundNo"), null));
        update.setChannelRefundNo(stringValue(data.get("channelRefundNo"), null));
        update.setRefundTime(stringValue(data.get("refundTime"), null));
        update.setPayCenterCode(gatewayResponse.getCode() == null ? null : String.valueOf(gatewayResponse.getCode()));
        update.setPayCenterMsg(gatewayResponse.getMsg());
        update.setResponseBody(JSON.toJSONString(gatewayResponse));

        int affected = payRefundDetailMapper.finishFromQuery(update);
        if (affected == 0) {
            log.warn("退款回查收口 CAS 命中 0 行，已被其它路径收口，本轮不重算汇总, refundOrderNo={}, txnDate={}, 回查状态={}",
                    row.getRefundOrderNo(), row.getTxnDate(), settledStatus);
            return false;
        }

        int summaryAffected = payTxnDetailMapper.updateRefundSummary(row.getOrderNo());
        if (summaryAffected == 0) {
            log.error("退款回查后汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, orderNo={}, refundOrderNo={}",
                    row.getOrderNo(), row.getRefundOrderNo());
        }
        log.info("退款回查已收口, refundOrderNo={}, txnDate={}, refundStatus={}, summaryAffected={}",
                row.getRefundOrderNo(), row.getTxnDate(), settledStatus, summaryAffected);
        return true;
    }

    /** 把 §3.2 退款查询应答里的 {@code status} 归一成本地终态，无法判定时返回 {@code null}。 */
    private String resolveRefundQueryStatus(GatewayReply reply) {
        if (!(reply instanceof GatewayReply.Accepted accepted) || accepted.data() == null) {
            return null;
        }
        String status = stringValue(accepted.data().get("status"), null);
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = convertPayStatus(status);
        if ("SUCCESS".equals(normalized) || "FAIL".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    /** 退款汇总跨表对账的扫描窗口（天）：只对最近 N 天的 TXN_DATE，同时用于分区裁剪。 */
    private static final int REFUND_SUMMARY_SCAN_DAYS = 7;

    /** 单轮扫描上限，与退款回查侧的 BATCH_SIZE 口径一致。 */
    private static final int REFUND_SUMMARY_BATCH_SIZE = 200;

    /** 退款汇总跨表对账补偿：把 {@code PAY_TXN_DETAIL} 的两列汇总重算回与。 */
    @Override
    public CompensateNotifyRespDTO compensateRefundSummary() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            String txnDateFrom = LocalDateTime.now().minusDays(REFUND_SUMMARY_SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<String> drifted = payRefundDetailMapper.selectDriftedRefundSummary(
                    txnDateFrom, REFUND_SUMMARY_BATCH_SIZE);
            List<String> orphans = payRefundDetailMapper.selectOrphanRefundOrders(
                    txnDateFrom, REFUND_SUMMARY_BATCH_SIZE);

            OutboxScan.Result driftScan = OutboxScan.run(drifted,
                    orderNo -> payTxnDetailMapper.updateRefundSummary(orderNo) > 0,
                    orderNo -> log.warn("退款汇总重算影响 0 行，该单在重算前已被别的路径改掉或已消失，"
                            + "本轮不计入已修，留给下一轮, orderNo={}", orderNo),
                    (orderNo, e) -> log.error("单条退款汇总重算异常，NEVER 因此中断整批, orderNo={}", orderNo, e));

            OutboxScan.Result orphanScan = OutboxScan.run(orphans,
                    orderNo -> false,
                    orderNo -> log.warn("退款明细已 SUCCESS 但原支付订单不存在，MUST 人工核对这笔退款对应哪张原单，"
                            + "本任务不会自愈, orderNo={}", orderNo),
                    (orderNo, e) -> log.error("单条孤儿退款订单登记异常，NEVER 因此中断整批, orderNo={}", orderNo, e));

            response.setScanned(driftScan.scanned() + orphanScan.scanned());
            response.setSubmitted(driftScan.success() + orphanScan.success());
            response.setSkipped(driftScan.failed() + orphanScan.failed());
            log.info("退款汇总跨表对账补偿完成, txnDateFrom={}, scanned={}, submitted={}, skipped={}, "
                            + "其中不可自愈（原单不存在）={}",
                    txnDateFrom, response.getScanned(), response.getSubmitted(), response.getSkipped(),
                    orphanScan.scanned());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("退款汇总跨表对账补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    private void updateRefundRequestResult(PayRefundDetail refundDetail, String refundStatus,
                                           RequestRefundResult response, PaySignGatewayResponse gatewayResponse) {
        PayRefundDetail update = new PayRefundDetail();
        update.setRefundOrderNo(refundDetail.getRefundOrderNo());
        update.setTxnDate(refundDetail.getTxnDate());
        update.setRefundStatus(refundStatus);
        update.setMerchantRefundNo(response.getMerchantRefundNo());
        update.setRefundNo(response.getRefundNo());
        update.setChannelRefundNo(response.getChannelRefundNo());
        update.setRefundTime(response.getRefundTime());
        update.setRetCode(response.getRetCode());
        update.setRetMsg(response.getRetMsg());
        update.setPayCenterCode(gatewayResponse == null ? null : String.valueOf(gatewayResponse.getCode()));
        update.setPayCenterMsg(gatewayResponse == null ? null : gatewayResponse.getMsg());
        update.setResponseBody(JSON.toJSONString(gatewayResponse));
        payRefundDetailMapper.updateRequestResult(update);
    }
    /** 入参由应答体换成 {@link GatewayReply}（ADR-D113 续）：成功码判定已收进端口。 */
    private void fillGatewayFields(RequestRefundResult response, GatewayReply reply) {
        PaySignGatewayResponse gatewayResponse = reply == null ? null : reply.raw();
        if (gatewayResponse == null) {
            return;
        }
        response.setCode(gatewayResponse.getCode());
        response.setMsg(gatewayResponse.getMsg());
        response.setSuccess(reply instanceof GatewayReply.Accepted);
        response.setData(gatewayResponse.getData());
    }
}

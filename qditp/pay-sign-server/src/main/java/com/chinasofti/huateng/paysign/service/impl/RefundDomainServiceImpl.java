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
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
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

            int inFlight = payRefundDetailMapper.countInFlightByOrderNo(payTxn.getOrderNo());
            if (inFlight > 0) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "该订单存在处理中的退款，请先确认上一笔结果");
                log.warn("REQUEST_REFUND 同一原单存在在途退款，拒绝重复提交, orderNo={}, inFlight={}, request={}",
                        payTxn.getOrderNo(), inFlight, JSON.toJSONString(request));
                return response;
            }

            PayRefundDetail refundDetail = buildPayRefundDetail(request, payTxn);
            payRefundDetailMapper.insert(refundDetail);

            String notifyUrl = refundGatewayPort.refundNotifyUrl();
            if (!StringUtils.hasText(notifyUrl)) {
                log.warn("未配置 pay.sign.request-refund-notify-url，本笔退款不送 notifyUrl，"
                                + "支付中心无处推 §5.2 退款回调，只能靠 §3.2 回查收敛, orderNo={}, refundOrderNo={}",
                        refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            }
            Map<String, Object> bizData = buildRequestRefundBizData(refundDetail, payTxn, notifyUrl);
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
                settleRejectedRequestByQuery(refundDetail, payTxn, response, gatewayResponse);
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

    /**
     * 请求退款被支付中心拒（活样例 {@code code=600「操作失败」}）时的收口：
     * **MUST 调 §3.2 退款查询回查真实结果，NEVER 直接按失败落账**。
     *
     * <p>2026-09-22 实测事实：同一笔 {@code code=600} 的退款，隔日回查支付中心返「该订单已全部退款」
     * —— 即**同步应答的失败码不代表退款没成功**。此前这里写的是 {@code updateRefundRequestResult(..., "RETRY", ...)}，
     * 于是每一笔 600 都在 {@code PAY_REFUND_DETAIL} 留下一条**假失败**，而 {@code RETRY} 当时
     * **谁都不扫**（{@code selectCompensableRefundQuery} 只取 {@code PROCESSING}、
     * {@code selectDriftedRefundSummary} 只取 {@code SUCCESS}）⇒ 永久悬挂、不自愈。</p>
     *
     * <p>因此未得终态时一律落 {@code PROCESSING} 交给 {@code sys_job} 305「退款回查补偿」，
     * **NEVER 回退成 {@code RETRY}**。回查本身抛异常也只降级成 {@code PROCESSING}、NEVER 打断调用方。</p>
     *
     * <p>注：{@code RETRY} 已于 2026-09-22 纳入 {@code selectCompensableRefundQuery} 的状态白名单
     * （为了捞回那 17 行历史遗留），但**那只是给旧数据补的收口通道，不是让新代码可以再写它** ——
     * 新写入仍 MUST 是 {@code PROCESSING}。</p>
     */
    private void settleRejectedRequestByQuery(PayRefundDetail refundDetail, PayTxnDetail payTxn,
                                              RequestRefundResult response, PaySignGatewayResponse gatewayResponse) {
        if (!refundGatewayPort.refundQueryConfigured()) {
            log.error("请求退款被支付中心拒但未配置 pay.sign.refund-query-url，无法回查定性，本笔落 PROCESSING 等配置补齐后由退款回查收口, "
                    + "orderNo={}, refundOrderNo={}", refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            updateRefundRequestResult(refundDetail, "PROCESSING", response, gatewayResponse);
            return;
        }

        GatewayReply queryReply = null;
        String settledStatus = null;
        try {
            Map<String, Object> bizData = new LinkedHashMap<>();
            bizData.put("refundOrderNo", refundDetail.getRefundOrderNo());
            bizData.put("merchantRefundNo", refundDetail.getRefundOrderNo());
            queryReply = refundGatewayPort.queryRefund(bizData);
            log.info("请求退款被拒后回查支付中心, orderNo={}, refundOrderNo={}, gatewayResponse={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(queryReply.raw()));
            settledStatus = resolveRefundQueryStatus(queryReply);
        } catch (Exception e) {
            log.error("请求退款被拒后回查异常，本笔落 PROCESSING 等退款回查补偿收口, orderNo={}, refundOrderNo={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), e);
        }

        if (settledStatus == null) {
            log.warn("请求退款被拒且回查未得终态，本笔落 PROCESSING 等退款回查补偿收口, orderNo={}, refundOrderNo={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            updateRefundRequestResult(refundDetail, "PROCESSING", response, gatewayResponse);
            return;
        }

        PaySignGatewayResponse queryResponse = queryReply.raw();
        if ("FAIL".equals(settledStatus)) {
            log.info("请求退款被拒且回查确认失败，按终态 FAIL 收口, orderNo={}, refundOrderNo={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            updateRefundRequestResult(refundDetail, "FAIL", response, queryResponse);
            return;
        }

        fillSuccess(response);
        fillGatewayFields(response, queryReply);
        fillRefundResponseFields(response, refundDetail, queryResponse);
        updateRefundRequestResult(refundDetail, "SUCCESS", response, queryResponse);
        int summaryAffected = payTxnDetailMapper.updateRefundSummary(payTxn.getOrderNo());
        if (summaryAffected == 0) {
            log.error("请求退款被拒但回查已成功，退款汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, "
                    + "orderNo={}, refundOrderNo={}", payTxn.getOrderNo(), refundDetail.getRefundOrderNo());
        }
        log.info("请求退款同步应答失败但回查确认已退款，按 SUCCESS 收口, orderNo={}, refundOrderNo={}, summaryAffected={}",
                refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), summaryAffected);
    }

    /**
     * 只读回查支付中心 §3.2，把网关原始应答原样带回，用于给「我方账不平」的退款单逐笔定性。
     *
     * <p>**本方法 NEVER 写库**：它存在的意义就是在不动账本的前提下拿到支付中心的真实口径
     * （2026-09-22 那 4 笔持续返「该订单已全部退款」就是靠它定性的）。要推进状态走
     * {@code POST /internal/payment/compensateRefundQuery}。</p>
     */
    @Override
    public BaseRespDTO queryRefundResult(String refundOrderNo) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (!StringUtils.hasText(refundOrderNo)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "退款流水号不能为空");
                return response;
            }
            if (!refundGatewayPort.refundQueryConfigured()) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "未配置退款查询地址");
                return response;
            }
            Map<String, Object> bizData = new LinkedHashMap<>();
            bizData.put("refundOrderNo", refundOrderNo);
            bizData.put("merchantRefundNo", refundOrderNo);
            GatewayReply reply = refundGatewayPort.queryRefund(bizData);
            PaySignGatewayResponse gatewayResponse = reply.raw();
            log.info("只读退款回查, refundOrderNo={}, gatewayResponse={}",
                    refundOrderNo, JSON.toJSONString(gatewayResponse));
            fillSuccess(response);
            response.setCode(gatewayResponse == null ? null : gatewayResponse.getCode());
            response.setMsg(gatewayResponse == null ? null : gatewayResponse.getMsg());
            response.setSuccess(reply instanceof GatewayReply.Accepted);
            response.setData(gatewayResponse == null ? null : gatewayResponse.getData());
            return response;
        } catch (Exception e) {
            log.error("只读退款回查异常, refundOrderNo={}", refundOrderNo, e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 支付中心网关 §5.2 退款回调（2026-09-22 新增，P1-3）。
     *
     * <p>与 {@link #compensateRefundQuery()} 是**同一个收口口径的两条路**：回调是快速路径、回查是兜底，
     * 两者复用同一条 {@code finishFromQuery} CAS，**NEVER 给回调另写一条 UPDATE**。</p>
     *
     * <p>三条易错口径：①定位本地行只能靠 {@code outRefundNo}（我方 {@code REFUND_ORDER_NO}），
     * {@code refundNo} 是支付中心自己的号、{@code orderNo} 是支付中心的 {@code PAY_CENTER_ORDER_NO}；
     * ②回调报文**不带 {@code txnDate}**，而表键是 {@code REFUND_ORDER_NO + TXN_DATE}，
     * 所以 MUST 先 {@code selectByRefundOrderNo} 取回 {@code TXN_DATE} 再做 CAS；
     * ③非终态（处理中）**一律只应答成功、不写库**，交 {@code sys_job} 305 收口 ——
     * 把处理中写进状态列会毁掉 CAS 的前置状态白名单。</p>
     *
     * <p>CAS 命中 0 行时**仍应答成功**：那说明这一行已被回查收口，属正常竞态；
     * 返错只会引来支付中心重推。本方法 NEVER 加 {@code @Transactional}（ADR-D48 / ADR-D8）。</p>
     */
    @Override
    public PaySignCallbackResult receiveRefundResult(ReceiveRefundResultReqDTO request) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        try {
            if (request == null || !StringUtils.hasText(request.getOutRefundNo())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "商户退款流水号（outRefundNo）不能为空");
                log.warn("§5.2 退款回调缺少 outRefundNo，无法定位本地退款单, request={}", JSON.toJSONString(request));
                return response;
            }

            String refundOrderNo = request.getOutRefundNo().trim();
            PayRefundDetail row = payRefundDetailMapper.selectByRefundOrderNo(refundOrderNo);
            if (row == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "未找到对应的退款单");
                log.warn("§5.2 退款回调找不到本地退款单，MUST 人工核对这笔退款归属, outRefundNo={}, request={}",
                        refundOrderNo, JSON.toJSONString(request));
                return response;
            }

            String settledStatus = convertPayStatus(request.getRefundResult());
            if (!"SUCCESS".equals(settledStatus) && !"FAIL".equals(settledStatus)) {
                fillSuccess(response);
                log.info("§5.2 退款回调非终态，不写库、交 sys_job 305 退款回查收口, refundOrderNo={}, txnDate={}, refundResult={}",
                        refundOrderNo, row.getTxnDate(), request.getRefundResult());
                return response;
            }

            PayRefundDetail update = new PayRefundDetail();
            update.setRefundOrderNo(row.getRefundOrderNo());
            update.setTxnDate(row.getTxnDate());
            update.setRefundStatus(settledStatus);
            update.setRefundNo(request.getRefundNo());
            update.setRefundTime(request.getRefundDate());
            update.setPayCenterCode(request.getRefundResult());
            update.setPayCenterMsg(request.getRefundResultDesc());
            update.setResponseBody(JSON.toJSONString(request));

            warnIfRefundAmountMismatch(row, request.getRefundAmount());

            int affected = payRefundDetailMapper.finishFromQuery(update);
            if (affected == 0) {
                fillSuccess(response);
                log.info("§5.2 退款回调收口 CAS 命中 0 行，已被退款回查收口，本次不重算汇总, refundOrderNo={}, txnDate={}, 回调状态={}",
                        refundOrderNo, row.getTxnDate(), settledStatus);
                return response;
            }

            int summaryAffected = payTxnDetailMapper.updateRefundSummary(row.getOrderNo());
            if (summaryAffected == 0) {
                log.error("§5.2 退款回调后汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, "
                        + "orderNo={}, refundOrderNo={}", row.getOrderNo(), refundOrderNo);
            }
            fillSuccess(response);
            log.info("§5.2 退款回调已收口, refundOrderNo={}, txnDate={}, refundStatus={}, summaryAffected={}",
                    refundOrderNo, row.getTxnDate(), settledStatus, summaryAffected);
            return response;
        } catch (Exception e) {
            log.error("处理 §5.2 退款回调异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /** 退款回查的扫描窗口（天）：只回查最近 N 天的 TXN_DATE，同时用于分区裁剪。 */    private static final int REFUND_QUERY_SCAN_DAYS = 7;

    /** 距最近一次发起退款至少多少分钟才回查，避开正常同步应答的时间窗。 */
    private static final int REFUND_QUERY_STALE_MINUTES = 5;

    /** 未得终态时的退避间隔（秒），写进 NEXT_REQUEST_TIME。 */
    private static final int REFUND_QUERY_RETRY_DELAY_SECONDS = 300;

    /**
     * 回查放弃龄期（小时）：退款单创建超过该时长仍拿不到终态，即落 {@code CLOSED} 转人工。
     *
     * <p>**为什么必须有这道闸**：在它之前，唯一的退出条件是 {@code TXN_DATE} 掉出
     * {@link #REFUND_QUERY_SCAN_DAYS} 天窗口 —— 那不是状态收敛、是**静默消失**：
     * 行永久停在 {@code PROCESSING}，而端点每轮照返 {@code 0000}、调度日志一片绿，既不报警也不自愈
     * （2026-09-22 实测样例 {@code RF2026092211305514180000104}：支付中心 §3.1 返「该订单已全部退款」、
     * §3.2 回查却返「未找到数据」，两个应答互斥，回查再跑一万次也拿不到终态）。
     *
     * <p>**为什么按龄期而不是按次数**：{@code REQUEST_COUNT} 计的是「发起退款次数」
     * （{@code markRequesting} 在发起时 +1，全库 89 行恒为 1），把回查轮次也累加进去会毁掉那一列的语义 ——
     * 而「这笔退款请求发了几次」正是排查重复退款时要看的信息。
     * 龄期还有一个好处：**与调度频率解耦**，`sys_job` 305 停过一段时间再恢复，超龄行会立刻被判定，
     * 而次数上限在停摆期间不会推进。
     *
     * <p>取 6 小时：远大于退避间隔 300 秒 × 调度周期 10 分钟所能覆盖的正常收敛窗口，
     * 又能在当班时间内暴露出来。**NEVER 调到大于 {@link #REFUND_QUERY_SCAN_DAYS} 天** ——
     * 那样行会先掉出扫描窗口，这道闸就永远不触发。
     */
    private static final int REFUND_QUERY_GIVE_UP_HOURS = 6;

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
            if (exceededGiveUpAge(row)) {
                settleExhausted(row, queryReply);
                return false;
            }
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

        warnIfRefundAmountMismatch(row, data.get("refundAmount"));

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

    /**
     * 这一行是否已超过 {@link #REFUND_QUERY_GIVE_UP_HOURS}、该放弃自动定性。
     *
     * <p>{@code CREATE_TIME} 为空时**返回 false**（继续回查）：宁可多查几轮，
     * 也 NEVER 因为缺一个时间戳就把一笔可能已退成功的单判成需人工。
     */
    private boolean exceededGiveUpAge(PayRefundDetail row) {
        LocalDateTime createTime = row.getCreateTime();
        if (createTime == null) {
            return false;
        }
        return createTime.isBefore(LocalDateTime.now().minusHours(REFUND_QUERY_GIVE_UP_HOURS));
    }

    /**
     * 超过放弃龄期：落 {@code CLOSED} 并打 ERROR 要求人工到支付中心侧核账。
     *
     * <p>**NEVER 改成落 {@code FAIL} 或 {@code SUCCESS}** —— 放弃的语义是「我方无法自动定性」，
     * 猜任一端都会让对账按错的方向走（猜 {@code FAIL} 会引来重复退款，猜 {@code SUCCESS} 会虚增已退金额）。
     * CAS 命中 0 行说明已被别的路径收口，属正常竞态、只记日志。
     */
    private void settleExhausted(PayRefundDetail row, GatewayReply queryReply) {
        PaySignGatewayResponse gatewayResponse = queryReply == null ? null : queryReply.raw();
        String payCenterCode = gatewayResponse == null || gatewayResponse.getCode() == null
                ? null : String.valueOf(gatewayResponse.getCode());
        String payCenterMsg = gatewayResponse == null ? null : gatewayResponse.getMsg();
        int affected = payRefundDetailMapper.exhaustFromQuery(
                row.getRefundOrderNo(), row.getTxnDate(), payCenterCode, payCenterMsg);
        if (affected == 0) {
            log.info("退款回查已超龄但收口 CAS 命中 0 行，说明这一行已被别的路径收口, refundOrderNo={}, txnDate={}",
                    row.getRefundOrderNo(), row.getTxnDate());
            return;
        }
        log.error("退款回查超过 {} 小时仍拿不到终态，已落 CLOSED 停止轮询，MUST 人工到支付中心侧核账这笔到底退没退, "
                        + "refundOrderNo={}, orderNo={}, txnDate={}, refundAmount={}, createTime={}, "
                        + "支付中心最后应答 code={}, msg={}",
                REFUND_QUERY_GIVE_UP_HOURS, row.getRefundOrderNo(), row.getOrderNo(), row.getTxnDate(),
                row.getRefundAmount(), row.getCreateTime(), payCenterCode, payCenterMsg);
    }

    /**
     * §3.2 退款查询应答的 {@code refundAmount}（Integer、单位分）与本地 {@code PAY_REFUND_DETAIL.REFUND_AMOUNT} 比对。
     *
     * <p>不一致只打 WARN、**NEVER 阻断收口** —— 部分退款口径未定，拒绝收口会让退款单永久停在 {@code PROCESSING}；
     * 解析不出数字同样只打 WARN。</p>
     */
    private void warnIfRefundAmountMismatch(PayRefundDetail row, Object queriedAmount) {
        if (queriedAmount == null) {
            return;
        }
        Integer localAmount = row.getRefundAmount();
        if (localAmount == null) {
            return;
        }
        try {
            long queried = queriedAmount instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(queriedAmount).trim());
            if (localAmount.longValue() != queried) {
                log.warn("退款回查：金额与本地退款单不一致，只告警不阻断收口, refundOrderNo={}, txnDate={}, localAmount={}, queriedAmount={}",
                        row.getRefundOrderNo(), row.getTxnDate(), localAmount, queried);
            }
        } catch (NumberFormatException e) {
            log.warn("退款回查：refundAmount 不是整数分，跳过金额比对, refundOrderNo={}, txnDate={}, refundAmount={}",
                    row.getRefundOrderNo(), row.getTxnDate(), queriedAmount);
        }
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

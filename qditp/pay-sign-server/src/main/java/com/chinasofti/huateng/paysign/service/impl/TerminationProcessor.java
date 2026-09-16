package com.chinasofti.huateng.paysign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 单条解约申请的处理器。
 *
 * PENDING：先查未结清扣费订单，再决定是否真正发起支付平台解约。
 * SCANNING：主动向支付平台查协议状态收口，不依赖解约回调。
 *
 * 独立成 Bean 是为了让批处理入口经 Spring 代理调用本类（同类自调用不走代理），
 * 单条异常不影响本批其余记录。
 *
 * 本类所有方法 NEVER 加 @Transactional，与 TerminationInternalServiceImpl.executeTermination
 * 同一口径（见其上方注释）：方法体内有 4 处网络调用——查未结清欠费、查支付平台协议状态、
 * 发起支付平台解约、收口时经 receiveTerminationResult 再调 account 清理支付通道。
 * 事务包住网络调用会让 APP_TERMINATION_REQUEST 这一行的排他锁持满整个往返，
 * 超过 Druid remove-abandoned-timeout 后连接被强杀、commit 抛 connection closed，
 * 连「留证据」的流水日志 INSERT 一起被丢弃（2026-08-26 receivePayResult 生产事故同型）。
 *
 * 去事务后每条 SQL 自动提交，没有回滚可用，因此状态流转全部走 mapper 里的 CAS 语句：
 *   markScanning            PENDING → SCANNING，抢执行权，影响 0 行即放弃，NEVER 继续调支付中心；
 *   rejectPending           PENDING → FAILED（存在未结清欠费），一条语句原子落终态；
 *   revertScanningToPending 仅在支付平台**明确**答复失败时交还执行权；
 *   expireScanning          SCANNING 收口超时置 FAILED。
 * 结果未知（超时 / 连接异常）时刻意保持 SCANNING，由 SCANNING 分支主动查协议状态收口，
 * NEVER 退回 PENDING —— 支付平台可能已受理，退回会重复发解约。
 */
@Service
public class TerminationProcessor {

    private static final Logger log = LoggerFactory.getLogger(TerminationProcessor.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();

    /** 支付平台协议状态：已解约。 */
    private static final String GATEWAY_STATUS_UNSIGNED = "UNSIGNED";
    /**
     * 解约回调报文的成功状态值，见 CallbackDomainServiceImpl.receiveTerminationResult。
     *
     * <p><b>NEVER 换成 {@code TerminationStatus.SUCCESS.name()}</b>：这是<b>支付平台报文</b>
     * 里的字段取值，与我方 {@code TERMINATION_STATUS} 列只是字面量恰好相同。
     * 对端改了报文取值时只应改这一行，不该牵动我方状态机。
     */
    private static final String CALLBACK_STATUS_SUCCESS = "SUCCESS";

    private static final DateTimeFormatter DISMISSAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * SCANNING 多久（分钟）没能确认解约就判为收口超时，置 FAILED 并通知 APP。
     *
     * 取 24 小时：按外部调度 5 分钟一轮，相当于重试约 288 次仍未确认。**MUST 取得足够宽松**——
     * 打早了会在支付平台其实已经解约、只是查询链路暂时不通的情况下告诉 APP「解约失败」，
     * 而本地签约记录与 account 支付通道都还没清，用户以为还签着、免密扣款却已失效。
     * 反之留在 SCANNING 是无上限静默重试，没有任何人会知道，这才是本常量要解决的问题。
     */
    private static final int SCANNING_TIMEOUT_MINUTES = 1440;


    /** 处理结论。 */
    public enum Outcome {
        /** 已发起支付平台解约，等下一轮扫表或回调收口。 */
        TERMINATED,
        /** 主动查询确认支付平台已解约，已收口为 SUCCESS。 */
        CONFIRMED,
        /** 存在未结清扣费订单，拒绝解约并已置 FAILED。 */
        REJECTED,
        /** SCANNING 超过 SCANNING_TIMEOUT_MINUTES 仍未确认解约，已置 FAILED 并通知 APP。 */
        EXPIRED,
        /** 未能定论，状态原样保留，下次扫表重试。 */
        SKIPPED
    }

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final ContractDomainService contractDomainService;

    /**
     * 网关应答判读的收口点。
     *
     * <p>2026-09-15 起本类不再调 {@code paySignWorkflow.isGatewaySuccess}（那只是
     * {@code payGatewayClient.isSuccess} 的薄壳，随 {@code PaySignWorkflow} 一同删除），
     * 改为直接注入 {@link PaySignGateway} 调 {@code isSuccess}，语义完全等价。</p>
     */
    private final PaySignGateway paySignGateway;

    /**
     * 解约回调的收口实现，2026-09-15 随回调组由 {@code PaySignWorkflow} 搬到
     * {@code CallbackDomainServiceImpl}，本类的调用点同批改指向这里（**纯换宿主类，语义不变**：
     * 该方法仍然刻意不带 {@code @Transactional}）。
     */
    private final CallbackDomainService callbackDomainService;

    /** 审计流水的唯一写入点（2026-09-14 抽出）；此前是 {@code paySignWorkflow.writeLog}，属反向依赖。 */
    private final PaySignAuditLogger auditLogger;

    private final GateTxnPayClient gateTxnPayClient;

    private final AppNotifyService appNotifyService;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public TerminationProcessor(
            AppTerminationRequestMapper terminationRequestMapper,
            ContractDomainService contractDomainService,
            PaySignGateway paySignGateway,
            CallbackDomainService callbackDomainService,
            PaySignAuditLogger auditLogger,
            GateTxnPayClient gateTxnPayClient,
            AppNotifyService appNotifyService) {
        this.terminationRequestMapper = terminationRequestMapper;
        this.contractDomainService = contractDomainService;
        this.paySignGateway = paySignGateway;
        this.callbackDomainService = callbackDomainService;
        this.auditLogger = auditLogger;
        this.gateTxnPayClient = gateTxnPayClient;
        this.appNotifyService = appNotifyService;
    }

    public Outcome processOne(AppTerminationRequest record) {
        String status = record.getTerminationStatus();
        // 状态白名单：PENDING 走「查欠费 → 发起解约」，SCANNING 走「主动查询收口」，
        // 其余（SUCCESS / FAILED）已终态，一律不动。
        if (STATUS_PENDING.equals(status)) {
            return processPending(record);
        }
        if (STATUS_SCANNING.equals(status)) {
            return confirmTermination(record);
        }
        log.info("解约申请状态不在处理白名单内，跳过, requestSignSeq={}, status={}",
                record.getRequestSignSeq(), status);
        return Outcome.SKIPPED;
    }

    /** PENDING：确认无未结清欠费后发起支付平台解约。 */
    private Outcome processPending(AppTerminationRequest record) {
        String requestSignSeq = record.getRequestSignSeq();
        GateTxnPayFailedOrderRespDTO orderResp = queryUnsettledOrder(record);
        // 查询未成功执行时 NEVER 继续解约：这里放行等于在用户可能仍欠费的情况下解约。
        // 不改任何状态，记录留在 PENDING 等下次扫表重试。
        if (orderResp == null || !PaySignErrorCodeEnum.SUCCESS.getCode().equals(orderResp.getResultCode())) {
            log.error("查询未结清扣费订单未成功，本次不处理, requestSignSeq={}, response={}",
                    requestSignSeq, orderResp == null ? null : JSON.toJSONString(orderResp));
            return Outcome.SKIPPED;
        }

        if (orderResp.isHasFailedOrder()) {
            return rejectByUnsettledOrder(record, requestSignSeq);
        }
        return requestTermination(record, requestSignSeq);
    }

    /**
     * SCANNING：主动向支付平台查协议状态收口。
     *
     * 支付平台的解约请求报文不含 notifyUrl（网关文档 §2.3），回调地址只能由支付中心在商户侧
     * 配置，我方无法确保回调一定到达。没有这条主动查询的路，记录会永久卡在 SCANNING、
     * 用户实际已解约但 ITP 侧仍显示已签约。
     *
     * 每个「本轮无法确认」的出口都 MUST 走 expireIfTimedOut 而不是直接 return SKIPPED：
     * 否则查不通 / 支付平台迟迟不解约的记录就是无上限静默重试，既没有终态也没有人知道。
     */
    private Outcome confirmTermination(AppTerminationRequest record) {
        String requestSignSeq = record.getRequestSignSeq();
        PaySignGatewayResponse gatewayResponse;
        try {
            gatewayResponse = contractDomainService.queryPayPlatformContractStatus(requestSignSeq);
        } catch (Exception e) {
            log.error("查询支付平台协议状态异常，保持 SCANNING, requestSignSeq={}", requestSignSeq, e);
            return expireIfTimedOut(record, "查询支付平台协议状态异常");
        }
        if (!paySignGateway.isSuccess(gatewayResponse)) {
            log.warn("查询支付平台协议状态未成功，保持 SCANNING, requestSignSeq={}, response={}",
                    requestSignSeq, JSON.toJSONString(gatewayResponse));
            return expireIfTimedOut(record, "查询支付平台协议状态未成功");
        }

        String contractStatus = readGatewayStatus(gatewayResponse);
        if (!GATEWAY_STATUS_UNSIGNED.equalsIgnoreCase(contractStatus)) {
            log.info("支付平台协议尚未解约，保持 SCANNING, requestSignSeq={}, status={}",
                    requestSignSeq, contractStatus);
            return expireIfTimedOut(record, "支付平台协议状态=" + contractStatus);
        }

        // 复用回调处理逻辑收口：清理账户支付通道、删除签约记录、置 SUCCESS、通知 APP 全在里面，
        // 且对已终态记录有幂等短路。NEVER 在这里重写一遍那套写操作。
        ReceiveTerminationResultReqDTO callback = new ReceiveTerminationResultReqDTO();
        callback.setThirdUserId(record.getThirdUserId());
        callback.setRequestSignSeq(requestSignSeq);
        callback.setPaymentVendor(record.getPaymentVendor());
        callback.setCardId(record.getCardId());
        callback.setCardType(record.getCardType());
        callback.setStatus(CALLBACK_STATUS_SUCCESS);
        // queryResult 不返回解约时间，取本次确认时间。
        callback.setDismissalTime(LocalDateTime.now().format(DISMISSAL_TIME_FORMATTER));

        BaseRespDTO callbackResp = callbackDomainService.receiveTerminationResult(
                callback, SignChannelEnum.METRO_APP.getCode());
        if (callbackResp == null || !PaySignErrorCodeEnum.SUCCESS.getCode().equals(callbackResp.getRetCode())) {
            // 内部清理未成功，抛异常交给批处理入口记账；本方法没改过状态，
            // 记录仍是 SCANNING，下一轮扫表继续重试。
            // receiveTerminationResult 自 ADR-D48（2026-09-12）摘掉 @Transactional 起**不再自带事务**：
            // 它内部用 transactionTemplate 起短事务、出网段在事务外，因此**调用方 NEVER 能假定
            // 「抛异常就全回滚」** —— 它可能已经清了账户支付通道、也可能已把状态推进过一段。
            // 这里之所以仍然只抛异常不做补偿，是因为收口本身对已终态记录幂等短路，重试安全。
            throw new TerminationException("解约收口处理失败，requestSignSeq=" + requestSignSeq
                    + ", response=" + JSON.toJSONString(callbackResp));
        }

        log.info("支付平台已解约，解约申请已收口, requestSignSeq={}", requestSignSeq);
        return Outcome.CONFIRMED;
    }

    /** 从网关响应的 data 中读取协议状态。 */
    private String readGatewayStatus(PaySignGatewayResponse gatewayResponse) {
        Map<String, Object> data = gatewayResponse == null ? null : gatewayResponse.getData();
        if (data == null) {
            return null;
        }
        Object status = data.get("status");
        return status == null ? null : String.valueOf(status);
    }


    /**
     * 查询该用户该支付渠道下是否还有未结清扣费订单。
     * requestTime 不传：业务口径是「这个用户还有没有未结清的欠费」，不限时间范围。
     */
    private GateTxnPayFailedOrderRespDTO queryUnsettledOrder(AppTerminationRequest record) {
        try {
            GateTxnPayFailedOrderReqDTO request = new GateTxnPayFailedOrderReqDTO();
            request.setThirdUserId(record.getThirdUserId());
            request.setPaymentVendor(record.getPaymentVendor());
            return gateTxnPayClient.hasFailedOrder(request);
        } catch (Exception e) {
            log.error("查询未结清扣费订单异常, requestSignSeq={}", record.getRequestSignSeq(), e);
            return null;
        }
    }

    /** 存在未结清欠费：不调支付平台，置 FAILED 并通知 APP 解约失败。 */
    private Outcome rejectByUnsettledOrder(AppTerminationRequest record, String requestSignSeq) {
        String failReason = "存在未结清扣费订单";
        // rejectPending 的 WHERE 带 TERMINATION_STATUS = 'PENDING'，是 CAS。
        // 判定 PENDING 与本次写入之间隔着一次查欠费 RPC，期间这条可能已被 execute 接口抢成 SCANNING
        // 或被回调收口成 SUCCESS，影响 0 行即 MUST 什么都不做，NEVER 覆盖别人的状态、
        // 更 NEVER 在支付平台已受理解约的情况下给 APP 发解约失败通知。
        int rejected = terminationRequestMapper.rejectPending(requestSignSeq, failReason, LocalDateTime.now());
        if (rejected == 0) {
            log.info("解约申请已被其它路径接手，放弃拒绝处理, requestSignSeq={}", requestSignSeq);
            return Outcome.SKIPPED;
        }

        // 无事务，上面的 CAS 已提交，此处直接提交通知即可：不存在「通知已发、状态回滚」的窗口。
        notifyTerminationFailed(record, failReason);

        log.info("存在未结清扣费订单，拒绝解约, requestSignSeq={}", requestSignSeq);
        return Outcome.REJECTED;
    }

    /**
     * SCANNING 收口超时判定：滞留未超阈值就原样保留，超了则置 FAILED 并通知 APP。
     *
     * 滞留起点取 SCAN_TIME（进入 SCANNING、调支付平台解约的那一刻）；confirmTermination 不会刷新
     * 该列，所以它就是「在 SCANNING 待了多久」。SCAN_TIME 为空时退到 REQUEST_TIME。
     *
     * @param detail 本轮没能确认的原因，进 FAIL_REASON 供人工核查（列长 1024，够用）
     */
    private Outcome expireIfTimedOut(AppTerminationRequest record, String detail) {
        String requestSignSeq = record.getRequestSignSeq();
        LocalDateTime since = record.getScanTime() != null ? record.getScanTime() : record.getRequestTime();
        if (since == null) {
            // 两列都为空的历史脏数据：无从判断滞留时长。保持 SCANNING 并留日志，
            // NEVER 凭空打 FAILED——那会给 APP 发一条毫无依据的解约失败通知。
            log.warn("SCANNING 记录缺少 SCAN_TIME 与 REQUEST_TIME，无法判定收口超时，保持 SCANNING, requestSignSeq={}",
                    requestSignSeq);
            return Outcome.SKIPPED;
        }
        LocalDateTime now = LocalDateTime.now();
        if (since.plusMinutes(SCANNING_TIMEOUT_MINUTES).isAfter(now)) {
            return Outcome.SKIPPED;
        }

        String failReason = "解约收口超时（" + SCANNING_TIMEOUT_MINUTES + "分钟未确认）：" + detail;
        // expireScanning 的 WHERE 带 TERMINATION_STATUS = 'SCANNING'，是 CAS。
        // 影响 0 行说明这条已被解约回调收口成 SUCCESS，MUST 什么都不做，NEVER 覆盖终态。
        int expired = terminationRequestMapper.expireScanning(requestSignSeq, failReason, now);
        if (expired == 0) {
            log.info("解约申请已被其它路径收口，放弃超时处理, requestSignSeq={}", requestSignSeq);
            return Outcome.SKIPPED;
        }

        notifyTerminationFailed(record, failReason);

        // 用 log.error 而不是 warn：这条一定要有人看到。它意味着我方口径与支付平台侧可能已经不一致，
        // 需要人工到支付中心核对该协议究竟解没解约——本地签约记录和 account 支付通道此时都还没清。
        log.error("解约收口超时，已置 FAILED 并通知 APP, requestSignSeq={}, scanTime={}, requestTime={}, failReason={}",
                requestSignSeq, record.getScanTime(), record.getRequestTime(), failReason);
        return Outcome.EXPIRED;
    }

    /**
     * 提交「解约失败」通知。
     * 本类无事务，调用点的状态 CAS 已提交，因此直接提交通知即可——不存在「通知已发、状态回滚」的窗口。
     * MUST 在状态 CAS 成功之后才调用本方法，NEVER 在 CAS 之前或影响 0 行时调用。
     */
    private void notifyTerminationFailed(AppTerminationRequest record, String failReason) {
        NotifyTerminationFailedReqDTO notifyRequest = new NotifyTerminationFailedReqDTO();
        notifyRequest.setThirdUserId(record.getThirdUserId());
        notifyRequest.setRequestSignSeq(record.getRequestSignSeq());
        notifyRequest.setPaymentVendor(record.getPaymentVendor());
        notifyRequest.setCardId(record.getCardId());
        notifyRequest.setCardType(record.getCardType());
        notifyRequest.setFailReason(failReason);
        appNotifyService.asyncNotifyTerminationFailed(record, notifyRequest);
    }

    /**
     * 无未结清欠费：CAS 抢执行权置 SCANNING，再调支付平台解约。
     * 成功状态由回调或下一轮主动查询写入。
     */
    private Outcome requestTermination(AppTerminationRequest record, String requestSignSeq) {
        // markScanning 的 WHERE 带 TERMINATION_STATUS = 'PENDING'，是状态机白名单 + CAS。
        // 影响 0 行说明这条已被 /internal/termination/execute 或另一轮扫表抢走，MUST 直接放弃，
        // NEVER 继续调支付中心——否则同一笔重复发解约。
        if (terminationRequestMapper.markScanning(requestSignSeq, LocalDateTime.now()) == 0) {
            log.info("解约申请已被并发接手（未命中 PENDING），本次不再发起, requestSignSeq={}", requestSignSeq);
            return Outcome.SKIPPED;
        }

        PaySignGatewayResponse gatewayResponse;
        try {
            gatewayResponse = contractDomainService.requestPayPlatformTermination(requestSignSeq);
        } catch (Exception e) {
            // 结果未知：MUST 保持 SCANNING 等下一轮主动查询收口，NEVER 退回 PENDING。
            // 流水日志的 THIRD_USER_ID / PAYMENT_VENDOR 是 NOT NULL，MUST 取自解约申请记录。
            log.error("调用支付平台解约异常，保持 SCANNING 待主动查询收口, requestSignSeq={}", requestSignSeq, e);
            auditLogger.write("EXECUTE_TERMINATION", record.getThirdUserId(), requestSignSeq,
                    record.getPaymentVendor(), null, record, null);
            throw new TerminationException("调用支付平台解约异常，requestSignSeq=" + requestSignSeq, e);
        }

        // 无事务，本条日志立即提交，无论后续成败都留得下请求响应快照。
        auditLogger.write("EXECUTE_TERMINATION", record.getThirdUserId(), requestSignSeq,
                record.getPaymentVendor(), null, record, gatewayResponse);

        if (!paySignGateway.isSuccess(gatewayResponse)) {
            // 明确失败：把执行权交还扫表任务。CAS 影响 0 行说明已被回调收口，不再干预。
            if (terminationRequestMapper.revertScanningToPending(requestSignSeq) == 0) {
                log.warn("支付平台解约失败但状态已被改走，放弃回退 PENDING, requestSignSeq={}", requestSignSeq);
            }
            throw new TerminationException("调用支付平台解约失败，requestSignSeq=" + requestSignSeq
                    + ", gatewayResponse=" + JSON.toJSONString(gatewayResponse));
        }

        log.info("已发起支付平台解约，等待回调, requestSignSeq={}", requestSignSeq);
        return Outcome.TERMINATED;
    }
}

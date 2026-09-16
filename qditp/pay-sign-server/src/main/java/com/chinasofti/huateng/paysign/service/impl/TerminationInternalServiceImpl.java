package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;

import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.domain.TerminationStatusTransition;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class TerminationInternalServiceImpl implements TerminationInternalService {

    private static final Logger log = LoggerFactory.getLogger(TerminationInternalServiceImpl.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    /**
     * 扫表补偿三兄弟（解约申请批处理 / 解约通知补发 / 通道清理补发）的实现所在，2026-09-16 拆出。
     *
     * <p>本类对那三个入口只剩一行委托 —— 它们由 web-admin Quartz 驱动、输入是「一批记录」，
     * 与单笔命令入口是两套不相干的依赖，硬放一个类里任一侧的协作者变动都惊动另一侧。
     * <b>NEVER 把那三个方法体搬回来</b>；新增扫表补偿一律加到
     * {@link TerminationCompensationService} 上。</p>
     */
    private final TerminationCompensationService terminationCompensationService;

    /**
     * 内部执行解约与 IF8A-75 直接解绑的实现所在，2026-09-16 拆出（接着补偿那一刀）。
     *
     * <p>这两个入口共用一整套东西 —— 状态白名单、{@code markScanning} /
     * {@code revertScanningToPending} 两条 CAS、支付中心调用、审计流水、补建申请、唯一索引竞态兜底；
     * 而本类剩下的两个（拒绝解约、失败订单核对）一条都不碰，因此
     * {@code paySignInfoMapper} / {@code contractDomainService} / {@code paySignGateway} /
     * {@code auditLogger} / {@code accountDomainPort} 五个协作者与 {@code WALLET_PAYMENT_VENDOR}
     * 都随之搬到了 {@link TerminationExecutor}。<b>NEVER 把它们加回本类</b>。</p>
     */
    private final TerminationExecutor terminationExecutor;

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final GateTxnPayClient gateTxnPayClient;

    private final AppNotifyService appNotifyService;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public TerminationInternalServiceImpl(
            TerminationCompensationService terminationCompensationService,
            TerminationExecutor terminationExecutor,
            AppTerminationRequestMapper terminationRequestMapper,
            GateTxnPayClient gateTxnPayClient,
            AppNotifyService appNotifyService) {
        this.terminationCompensationService = terminationCompensationService;
        this.terminationExecutor = terminationExecutor;
        this.terminationRequestMapper = terminationRequestMapper;
        this.gateTxnPayClient = gateTxnPayClient;
        this.appNotifyService = appNotifyService;
    }

    @Override
    public ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request) {
        return terminationCompensationService.processTermination(request);
    }

    @Override
    public CompensateNotifyRespDTO compensateTerminationNotify() {
        return terminationCompensationService.compensateTerminationNotify();
    }

    @Override
    public CompensateNotifyRespDTO compensateChannelSync() {
        return terminationCompensationService.compensateChannelSync();
    }

    @Override
    public CheckFailedOrdersRespDTO checkFailedOrders(CheckFailedOrdersReqDTO request) {
        CheckFailedOrdersRespDTO response = new CheckFailedOrdersRespDTO();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())
                    || !StringUtils.hasText(request.getPaymentVendor())
                    || request.getRequestTime() == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                return response;
            }

            GateTxnPayFailedOrderReqDTO hasFailedOrderReq = new GateTxnPayFailedOrderReqDTO();
            hasFailedOrderReq.setThirdUserId(request.getThirdUserId());
            hasFailedOrderReq.setPaymentVendor(request.getPaymentVendor());
            hasFailedOrderReq.setRequestTime(request.getRequestTime());
            GateTxnPayFailedOrderRespDTO result = gateTxnPayClient.hasFailedOrder(hasFailedOrderReq);

            if (result == null) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "查询扣费订单失败");
                return response;
            }

            response.setHasFailedOrder(result.isHasFailedOrder());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("查询扣费失败订单异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }


    @Override
    public BaseRespDTO executeTermination(ExecuteTerminationReqDTO request) {
        return terminationExecutor.executeTermination(request);
    }

    @Override
    public UnbindAgreementResult unbindAgreement(UnbindAgreementReqDTO request) {
        return terminationExecutor.unbindAgreement(request);
    }

    /**
     * 拒绝解约并通知 APP。<b>刻意不带 {@code @Transactional}，NEVER 加回</b>（批次 5C，ADR-D90）。
     *
     * <p>原先带 {@code @Transactional(rollbackFor = Exception.class)}，是本模块最后一条
     * 「事务包住出网」。摘掉的依据是逐条核对出来的，不是风格偏好：
     * <ul>
     *   <li><b>事务保护不了任何东西</b>：整个方法只有一次写库，就是下面那条 {@code rejectPending}。
     *       它是<b>单条 CAS UPDATE</b>（一条语句同时落 {@code FAILED} / 失败原因 / 完成时间 /
     *       {@code NOTIFY_STATUS='PENDING'} / 轮次归零，mapper XML 里写明了「MUST 只用一条语句」的理由）。
     *       单语句本身就是原子的，外面套不套事务落库结果完全一样。</li>
     *   <li><b>事务反而制造了两个真实窗口</b>：① {@code asyncNotifyTerminationFailed} 把通知任务
     *       提交到线程池，<b>这一步发生在 commit 之前</b> —— 通知线程可能在事务提交前就去读那一行、
     *       读到旧状态；② 本方法的 {@code catch} 分支是 {@code throw new TerminationException(...)}，
     *       **异常会真的传播出去、事务会真的回滚**（与批次 5A 那几个吞掉异常、事务形同虚设的方法不同），
     *       于是「APP 已收到解约失败通知、库里状态却回退成 PENDING」不是理论风险而是可达路径。
     *       这正是 AGENTS.md §5.2「{@code @Transactional} 方法内 NEVER 提交异步通知任务」那一条。</li>
     *   <li><b>ADR-D8 要求的「落同步状态 + 补偿」这里本来就齐了</b>，所以这是一次纯摘注解、
     *       不需要同批新写补偿：同步状态就是 {@code rejectPending} 一并写入的
     *       {@code NOTIFY_STATUS='PENDING'} + {@code NOTIFY_RETRY_COUNT=0}；补偿是已在跑的
     *       {@code sys_job} 7「解约结果通知补发」→ {@code POST /internal/termination/compensateNotify}
     *       → {@code compensateTerminationNotify}，它扫的就是 {@code APP_TERMINATION_REQUEST} 上
     *       通知未成功的记录。即：异步通知这一发丢了，补偿会重发；<b>NEVER 因为「摘了事务怕丢通知」
     *       而把注解加回来</b> —— 丢通知有人补，事务回滚把状态一起撤掉才是没人能补的那种。</li>
     * </ul>
     *
     * <p>与 {@code CallbackDomainServiceImpl#receiveSignResult} 的差别值得记一笔：那条同样是
     * 「事务 + 通知」，但它事务内有<b>多张表的多次写</b>、确实需要原子性，因此走的是保留事务 +
     * {@code AFTER_COMMIT} 事件（2.0.75 / {@code SignResultCommittedEvent}）；本方法只有一条语句，
     * 引入事件类反而多一层没有收益的间接。<b>NEVER 照抄那边的做法给本方法挂监听器。</b>
     */
    @Override
    public BaseRespDTO notifyTerminationFailed(NotifyTerminationFailedReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (request == null || !StringUtils.hasText(request.getRequestSignSeq())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                return response;
            }

            AppTerminationRequest terminationRequest = terminationRequestMapper
                    .selectByRequestSignSeq(request.getRequestSignSeq());
            if (terminationRequest == null) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在");
                return response;
            }

            if (STATUS_FAILED.equals(terminationRequest.getTerminationStatus())) {
                fillSuccess(response);
                return response;
            }

            if (!STATUS_PENDING.equals(terminationRequest.getTerminationStatus())) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请状态不正确");
                return response;
            }

            String failReason = StringUtils.hasText(request.getFailReason())
                    ? request.getFailReason() : "存在扣费失败订单";

            // PENDING -> FAILED 用单条 CAS（rejectPending），NEVER 退回
            // updateFailReason + updateCompleteTime + updateNotifyStatus 三条无 CAS 语句：
            // 上面 :534 的 PENDING 判断与此处之间没有行锁（本方法无事务，文档 P2 已记为
            // select-then-update），期间这条可能已被 execute 抢成 SCANNING、甚至被回调收口成
            // SUCCESS，三条无 CAS 会把它们覆盖成 FAILED 并给 APP 发一条与支付平台实际状态相反的
            // 解约失败通知。rejectPending 一条语句同时落 FAILED / 原因 / 完成时间 / 通知待发 +
            // 轮次归零，mapper XML 的注释里写明了这条禁令。
            TerminationStatusTransition.Result transit = TerminationStatusTransition.classify(
                    terminationRequestMapper.rejectPending(request.getRequestSignSeq(), failReason, LocalDateTime.now()),
                    TerminationStatus.FAILED,
                    () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));

            if (!transit.isDone()) {
                // 与回调路径的处置刻意不同：这里是内部接口，调用方需要知道这条没推动。
                // IDEMPOTENT（已是 FAILED）在 :529 就短路返回成功了，走到这里只会是 CONFLICT。
                log.warn("拒绝解约未命中 PENDING，已跳过失败通知, requestSignSeq={}, outcome={}, observedStatus={}",
                        request.getRequestSignSeq(), transit.outcome(), transit.observedStatus());
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请状态已变更，本次未处理");
                return response;
            }

            appNotifyService.asyncNotifyTerminationFailed(terminationRequest, request);

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("通知APP解约失败异常", e);
            throw new TerminationException("通知APP解约失败异常，requestSignSeq="
                    + (request != null ? request.getRequestSignSeq() : null), e);
        }
    }

}

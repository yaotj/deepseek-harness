package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.service.impl.channelsync.ChannelSyncDeliverer;
import com.chinasofti.huateng.alipay.paysign.service.impl.contract.AlipayContractServiceImpl;
import com.chinasofti.huateng.alipay.paysign.service.impl.contract.SignLogRecorder;
import com.chinasofti.huateng.alipay.paysign.service.impl.termination.TerminationCoordinator;
import com.chinasofti.huateng.alipay.paysign.service.impl.termination.TerminationNotifier;
import com.chinasofti.huateng.alipay.paysign.service.impl.termination.TerminationRegistrationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.alipay.paysign.port.AccountChannelPort;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

/**
 * 特征断言：钉住支付宝渠道**签约 / 解约登记 / 解约执行**三条链路的现有对外行为。
 *
 * <p>手法照 {@code pay-sign-server} 的 ADR-D87：**断言挂在对外门面上、不挂被拆的类**，
 * 于是后续批次无论怎么搬迁内部结构，只要这里全绿就证明对外行为等价。</p>
 *
 * <p><b>本文件已按批次 1（ADR-D129）改过三处</b>：原先带 {@code _currentDefect} 后缀的
 * 三条断言钉的是「事务内 RPC」「异常被吞掉导致事务回滚不了」「先改本地后调远端」，
 * 缺陷修掉后它们已改写成新契约（通道同步走 outbox / 异常穿透 / 委派 `TerminationNotifier`）。
 * 后续批次是纯搬迁，这些断言 MUST 保持全绿；要改动 MUST 先确认是有意的对外行为变更。</p>
 */
class AlipayContractCharacterizationTest {

    private static final String THIRD_USER_ID = "2088000000000001";
    private static final String AGREEMENT_CODE = "AL20260917000000000000001";
    private static final String CHANNEL_USER_ACCOUNT = "138****0001";
    private static final String CARD_ID = "0007000000000001";

    private AlipaySignInfoMapper signInfoMapper;
    private AlipayTerminationRequestMapper terminationRequestMapper;
    private AlipayAccountClient alipayAccountClient;
    private AccountChannelPort accountChannelPort;
    private TerminationNotifier terminationNotifier;
    private TerminationRegistrationService terminationRegistrationService;
    private SignLogRecorder signLogRecorder;

    private AlipayContractServiceImpl contractService;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        signInfoMapper = mock(AlipaySignInfoMapper.class);
        terminationRequestMapper = mock(AlipayTerminationRequestMapper.class);
        alipayAccountClient = mock(AlipayAccountClient.class);
        accountChannelPort = mock(AccountChannelPort.class);
        terminationNotifier = mock(TerminationNotifier.class);
        signLogRecorder = mock(SignLogRecorder.class);

        terminationRegistrationService = new TerminationRegistrationService();
        inject(terminationRegistrationService, "alipayTerminationRequestMapper", terminationRequestMapper);
        inject(terminationRegistrationService, "alipaySignInfoMapper", signInfoMapper);

        ChannelSyncDeliverer channelSyncDeliverer = new ChannelSyncDeliverer();
        inject(channelSyncDeliverer, "accountChannelPort", accountChannelPort);
        inject(channelSyncDeliverer, "alipaySignInfoMapper", signInfoMapper);

        TerminationCoordinator terminationCoordinator = new TerminationCoordinator();
        inject(terminationCoordinator, "alipayTerminationRequestMapper", terminationRequestMapper);
        inject(terminationCoordinator, "terminationNotifier", terminationNotifier);
        inject(terminationCoordinator, "terminationRegistrationService", terminationRegistrationService);

        contractService = new AlipayContractServiceImpl();
        inject(contractService, "alipaySignInfoMapper", signInfoMapper);
        inject(contractService, "alipayAccountClient", alipayAccountClient);
        inject(contractService, "channelSyncDeliverer", channelSyncDeliverer);
        inject(contractService, "alipayTerminationService", terminationCoordinator);
        inject(contractService, "signLogRecorder", signLogRecorder);
    }

    // ---------- 签约 addContract ----------

    @Test
    void blankMandatoryFieldIsRejectedWithInvalidParam() {
        AlipayTripAddContractReqDTO request = addContractRequest();
        request.setChannelUserAccount(" ");

        BusinessException thrown = assertThrows(BusinessException.class, () -> contractService.addContract(request));

        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), thrown.getCode(),
                "四个必填字段任一为空 MUST 返参数异常");
        verify(signInfoMapper, never()).insert(any());
    }

    /**
     * ADR-D135：**同一个协议号**重复上送才是幂等重复请求，原样返成功、不再落库、不再出网。
     */
    @Test
    void sameAgreementCodeResubmitIsIdempotent() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(), "同号重复签约 MUST 幂等返成功");
        assertEquals(AGREEMENT_CODE, response.getAgreementCode(), "幂等分支 MUST 回库内协议号");
        verify(signInfoMapper, never()).insert(any());
        verify(accountChannelPort, never()).updatePaymentChannel(anyString(), anyString(), anyString());
    }

    /**
     * ADR-D135：已有生效签约却换了协议号，**MUST 拒绝**、NEVER 静默返旧号。
     *
     * <p>原实现不比对协议号就返成功 + 库内旧号：新号不落库、上游却以为签成功了，
     * 后续按新号发起的扣款与解约在我方全部查不到。覆盖更新同样不行 ——
     * {@code CHANNEL_AGREEMENT_CODE} 是销卡通知发给支付中心的号，覆盖后旧协议再也解不了约。</p>
     */
    @Test
    void differentAgreementCodeOnActiveSignIsRejected() {
        AlipaySignInfo existing = signInfo();
        existing.setAgreementCode("AL_OLD_0001");
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(existing);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> contractService.addContract(addContractRequest()));

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), thrown.getCode());
        assertEquals("该用户已存在生效中的签约，请先解约后再签约", thrown.getMsg());
        verify(signInfoMapper, never()).insert(any());
        verify(accountChannelPort, never()).updatePaymentChannel(anyString(), anyString(), anyString());
    }

    /**
     * ADR-D135：撞主键说明并发请求刚把同一用户签上，结果与幂等重复请求等价 ⇒ 返成功。
     *
     * <p>MUST 沿 cause 链判定：这里刻意把 {@code DuplicateKeyException} 包进一层
     * {@code RuntimeException}，模拟本模块 tracing 切面换类型的形态（AGENTS.md §5.2）。</p>
     */
    @Test
    void primaryKeyConflictDegradesToIdempotentSuccess() {
        givenAccountOpened();
        doThrow(new RuntimeException("观测切面包装", new DuplicateKeyException("ORA-00001")))
                .when(signInfoMapper).insert(any());
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY"))
                .thenReturn(null, signInfo());

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(),
                "并发竞态 MUST NOT 把 ORA-00001 抛给上游");
        assertEquals(AGREEMENT_CODE, response.getAgreementCode());
        verify(accountChannelPort, never()).updatePaymentChannel(anyString(), anyString(), anyString());
    }

    /**
     * ADR-D135：已解约用户重签 MUST 就地把那一行改回 SIGNED，NEVER 再 INSERT 一行。
     *
     * <p>本表主键是 {@code THIRD_USER_ID} 单列、解约只改状态不删行，因此 INSERT 必撞主键 ——
     * 原实现在这条路上直接报错，已解约用户永远签不回来。</p>
     */
    @Test
    void terminatedUserIsReSignedInPlace() {
        givenAccountOpened();
        AlipaySignInfo terminated = signInfo();
        terminated.setSignStatus("TERMINATED");
        terminated.setAgreementCode("AL_OLD_0001");
        when(signInfoMapper.selectAnyByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(terminated);
        when(signInfoMapper.reactivateSign(any())).thenReturn(1);

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        ArgumentCaptor<AlipaySignInfo> captor = ArgumentCaptor.forClass(AlipaySignInfo.class);
        verify(signInfoMapper).reactivateSign(captor.capture());
        assertEquals(AGREEMENT_CODE, captor.getValue().getAgreementCode(), "重签 MUST 写入本次的新协议号");
        assertEquals("SIGNED", captor.getValue().getSignStatus());
        verify(signInfoMapper, never()).insert(any());
        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        assertEquals(AGREEMENT_CODE, response.getAgreementCode());
        verify(accountChannelPort).updatePaymentChannel(THIRD_USER_ID, CHANNEL_USER_ACCOUNT, AGREEMENT_CODE);
    }

    /** ADR-D135：重签 CAS 影响 0 行说明状态已被别人改动，MUST 回查后按幂等处理、NEVER 当成成功放过去。 */
    @Test
    void reactivateCasMissDegradesToIdempotentSuccess() {
        givenAccountOpened();
        AlipaySignInfo terminated = signInfo();
        terminated.setSignStatus("TERMINATED");
        when(signInfoMapper.selectAnyByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(terminated);
        when(signInfoMapper.reactivateSign(any())).thenReturn(0);
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(null, signInfo());

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(signInfoMapper, never()).insert(any());
        verify(accountChannelPort, never()).updatePaymentChannel(anyString(), anyString(), anyString());
    }

    /** 非唯一约束的落库异常 MUST 原样穿透，NEVER 被兜底分支吞成成功。 */
    @Test
    void nonConflictInsertFailurePropagates() {
        givenAccountOpened();
        doThrow(new IllegalStateException("模拟落库失败")).when(signInfoMapper).insert(any());

        assertThrows(IllegalStateException.class, () -> contractService.addContract(addContractRequest()));
    }

    @Test
    void unopenedAccountIsRejectedBeforeAnyLocalWrite() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(null);
        when(alipayAccountClient.selectByThirdUserId(THIRD_USER_ID)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> contractService.addContract(addContractRequest()));

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), thrown.getCode());
        assertEquals("用户未开户，无法签约", thrown.getMsg());
        verify(signInfoMapper, never()).insert(any());
    }

    @Test
    void accountWithoutCardIdIsRejected() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(null);
        AlipayUserInfoDTO userInfo = new AlipayUserInfoDTO();
        userInfo.setThirdUserId(THIRD_USER_ID);
        when(alipayAccountClient.selectByThirdUserId(THIRD_USER_ID)).thenReturn(userInfo);

        assertThrows(BusinessException.class, () -> contractService.addContract(addContractRequest()),
                "开户信息里 cardId 为空 MUST 与「未开户」同等处理");
        verify(signInfoMapper, never()).insert(any());
    }

    @Test
    void signedRowIsInsertedWithSignedStatusAndDefaultFlags() {
        givenAccountOpened();
        when(alipayAccountClient.updatePaymentChannel(anyString(), anyString(), anyString())).thenReturn(true);

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        ArgumentCaptor<AlipaySignInfo> captor = ArgumentCaptor.forClass(AlipaySignInfo.class);
        verify(signInfoMapper).insert(captor.capture());
        AlipaySignInfo inserted = captor.getValue();
        assertEquals("SIGNED", inserted.getSignStatus(), "签约落库状态 MUST 是 SIGNED（本模块词表，NEVER 归一成 pay-sign 的 SUCCESS）");
        assertEquals("SIGN", inserted.getOperationType());
        assertEquals("ALIPAY", inserted.getChannel());
        assertEquals(CARD_ID, inserted.getCardId(), "cardId MUST 取自账户域应答，NEVER 取入参");
        assertEquals("0", inserted.getDeleteFlag());
        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        assertEquals(AGREEMENT_CODE, response.getAgreementCode());
        verify(signLogRecorder).recordSignSuccess(any(), any(), any());
    }

    /**
     * ADR-D131：账户域**明确拒绝**（对端答了 false）落 {@code FAILED} 且结果文案带 {@code BIZ_REJECTED:} 前缀。
     *
     * <p>前缀不是装饰：批次 5 的补偿扫描据它判断值不值得重推 —— 业务拒绝重推一万次也不会成功。</p>
     */
    @Test
    void channelSyncBizRejectionStillReturnsSuccessAndMarksOutboxFailed() {
        givenAccountOpened();
        when(accountChannelPort.updatePaymentChannel(anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected("ACCOUNT_CHANNEL_REJECTED", "账户域返回 false"));

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(),
                "ADR-D129：签约行已落库、协议号已生成，通道同步失败 MUST NOT 把已成立的签约抹掉");
        verify(signInfoMapper).insert(any());
        verify(signInfoMapper).updateChannelSync(eq(AGREEMENT_CODE), eq("FAILED"),
                eq("BIZ_REJECTED: ACCOUNT_CHANNEL_REJECTED"), any());
        verify(signLogRecorder).recordSignSuccess(any(), any(), any());
    }

    /**
     * ADR-D131：**不可达**同样落 {@code FAILED}，但前缀是 {@code UNREACHABLE:} —— 这一支才该进补偿队列。
     *
     * <p>注意异常已在 {@code AccountChannelRpcAdapter} 里收成 {@code Unreachable}，
     * 端口 <b>NEVER 抛异常</b>，因此这里 stub 的是返回值、不是 {@code thenThrow}。</p>
     */
    @Test
    void channelSyncUnreachableIsMarkedFailedWithRetryablePrefix() {
        givenAccountOpened();
        when(accountChannelPort.updatePaymentChannel(anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Unreachable(new IllegalStateException("模拟账户域不可达")));

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(),
                "出网失败 MUST NOT 穿透到上游，否则会引来重推、而签约已经成立");
        verify(signInfoMapper).updateChannelSync(eq(AGREEMENT_CODE), eq("FAILED"),
                eq("UNREACHABLE: IllegalStateException"), any());
    }

    /**
     * ADR-D129 后的正序：本地 INSERT → 出网同步通道 → 回写 outbox。
     *
     * <p>本顺序与 {@code TerminationNotifier} 那条「先远端后本地」**刻意相反**，不是笔误：
     * 签约的本地行是「本次业务已成立」的唯一证据，先落库才有东西可补偿；而销卡是把一个
     * 已存在的协议在两侧都关掉，远端没关成功时本地 NEVER 能先置 TERMINATED。</p>
     */
    @Test
    void signRowLandsFirstThenChannelSyncThenOutboxWriteBack() {
        givenAccountOpened();
        when(accountChannelPort.updatePaymentChannel(anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());

        contractService.addContract(addContractRequest());

        InOrder order = inOrder(signInfoMapper, accountChannelPort);
        order.verify(signInfoMapper).insert(any());
        order.verify(accountChannelPort).updatePaymentChannel(THIRD_USER_ID, CHANNEL_USER_ACCOUNT, AGREEMENT_CODE);
        order.verify(signInfoMapper).updateChannelSync(eq(AGREEMENT_CODE), eq("SUCCESS"), anyString(), any());
    }

    // ---------- 解约登记 terminateContract ----------

    @Test
    void terminationRegistrationRejectsBlankAgreementCode() {
        AlipayTripTerminateContractReqDTO request = new AlipayTripTerminateContractReqDTO();
        request.setAgreementCode(" ");

        AlipayTripTerminateContractRespDTO response = contractService.terminateContract(request);

        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), response.getRetCode());
        verify(terminationRequestMapper, never()).insert(any());
    }

    @Test
    void terminationRegistrationIsIdempotentByCount() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(1);

        AlipayTripTerminateContractRespDTO response = contractService.terminateContract(terminateRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(), "已登记 MUST 幂等返成功");
        verify(terminationRequestMapper, never()).insert(any());
        verify(signInfoMapper, never()).selectByAgreementCode(anyString());
    }

    @Test
    void terminationRegistrationRejectsWhenSignRowHasNoThirdUserId() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(0);
        AlipaySignInfo withoutUser = signInfo();
        withoutUser.setThirdUserId("  ");
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(withoutUser);

        AlipayTripTerminateContractRespDTO response = contractService.terminateContract(terminateRequest());

        assertEquals(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), response.getRetCode(),
                "销卡批处理按 THIRD_USER_ID 做用户维度校验，回填不到 MUST 拒绝登记");
        verify(terminationRequestMapper, never()).insert(any());
    }

    @Test
    void terminationRegistrationLandsPendingRowWithSignInfoBackfilled() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(0);
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());

        AlipayTripTerminateContractRespDTO response = contractService.terminateContract(terminateRequest());

        ArgumentCaptor<AlipayTerminationRequest> captor = ArgumentCaptor.forClass(AlipayTerminationRequest.class);
        verify(terminationRequestMapper).insert(captor.capture());
        AlipayTerminationRequest inserted = captor.getValue();
        assertEquals("PENDING", inserted.getStatus(), "登记初始状态 MUST 是 PENDING（FAIL 是永久卡死的终态）");
        assertEquals("TERMINATE", inserted.getOperationType());
        assertEquals(THIRD_USER_ID, inserted.getThirdUserId());
        assertEquals(CARD_ID, inserted.getCardId());
        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
    }

    /**
     * ADR-D129：落库异常 MUST 穿透出来，{@code @Transactional} 才真的会回滚。
     *
     * <p>原实现把整段包在 catch-all 里转成 {@code 9001}，异常不穿透 ⇒ 事务永不回滚，
     * 而上游只看到一个含义模糊的系统错误。<b>NEVER 把 catch-all 加回去。</b></p>
     */
    @Test
    void terminationRegistrationLetsPersistenceFailurePropagate() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(0);
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        doThrow(new IllegalStateException("模拟落库失败")).when(terminationRequestMapper).insert(any());

        assertThrows(IllegalStateException.class, () -> contractService.terminateContract(terminateRequest()),
                "异常 MUST 穿透，否则 @Transactional 形同虚设");
    }

    // ---------- 解约执行 executeTermination ----------

    @Test
    void executeTerminationRejectsBlankAgreementCode() {
        AlipayCommonResponse response = contractService.executeTermination(" ");

        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), response.getRetCode());
        verify(terminationNotifier, never()).execute(any());
    }

    @Test
    void executeTerminationBackfillsRegistrationWhenMissing() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(0);
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(terminationRequestMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(terminationRow());
        when(terminationNotifier.execute(any())).thenReturn(TerminationNotifier.Outcome.TERMINATED);

        AlipayCommonResponse response = contractService.executeTermination(AGREEMENT_CODE);

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(terminationRequestMapper).insert(any());
        verify(terminationNotifier).execute(any());
    }

    @Test
    void executeTerminationRejectsWhenSignInfoMissingSoRegistrationIsRefused() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(0);
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(null);

        AlipayCommonResponse response = contractService.executeTermination(AGREEMENT_CODE);

        assertEquals(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), response.getRetCode(),
                "查不到签约信息时补登记被拒，其应答码原样透出（不再是笼统的 9999）");
        verify(terminationNotifier, never()).execute(any());
    }

    /**
     * ADR-D129：{@code executeTermination} 已收口成委派 {@link TerminationNotifier}，
     * 因此「先远端后本地」与 CAS 收口只有一份实现。
     *
     * <p>原实现自带一份「先把签约置 TERMINATED、再通知支付中心」，顺序与批处理链路相反，
     * 远端失败会留下「我方已解约、支付中心仍在签约」。<b>NEVER 在本方法里重新实现通知与状态收口。</b></p>
     */
    @Test
    void executeTerminationDelegatesAndNeverTouchesSignStatusItself() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(1);
        when(terminationRequestMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(terminationRow());
        when(terminationNotifier.execute(any())).thenReturn(TerminationNotifier.Outcome.RETRY_LATER);

        AlipayCommonResponse response = contractService.executeTermination(AGREEMENT_CODE);

        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getRetCode(),
                "RETRY_LATER MUST 返 9001：登记还在 PENDING、下一轮会重试，NEVER 报成终态失败");
        verify(signInfoMapper, never()).markTerminated(anyString(), any());
        verify(terminationRequestMapper, never()).insert(any());
    }

    @Test
    void executeTerminationMapsTerminalFailureToFail() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(1);
        when(terminationRequestMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(terminationRow());
        when(terminationNotifier.execute(any())).thenReturn(TerminationNotifier.Outcome.FAILED);

        AlipayCommonResponse response = contractService.executeTermination(AGREEMENT_CODE);

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), response.getRetCode(),
                "FAILED 是明确终态（签约记录不存在这类），MUST 与「待重试」区分开");
    }

    @Test
    void executeTerminationRejectsWhenRegistrationRowStillMissing() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(1);
        when(terminationRequestMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(null);

        AlipayCommonResponse response = contractService.executeTermination(AGREEMENT_CODE);

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), response.getRetCode());
        verify(terminationNotifier, never()).execute(any());
    }

    // ---------- fixtures ----------

    /**
     * 「已开户、通道同步会成功」这个默认前提。
     *
     * <p>ADR-D131 起 <b>MUST 给 {@code accountChannelPort} 一个默认返回值</b>：
     * {@code syncPaymentChannel} 里是穷尽 {@code switch}，mock 默认返 {@code null} 会直接 NPE
     * —— 那是测试前提没建全，不是生产缺陷（adapter 的三个分支都返实例）。
     * 要测别的分支就在用例里重新 stub 覆盖。</p>
     */
    private void givenAccountOpened() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(null);
        AlipayUserInfoDTO userInfo = new AlipayUserInfoDTO();
        userInfo.setThirdUserId(THIRD_USER_ID);
        userInfo.setCardId(CARD_ID);
        userInfo.setCardType("0441");
        when(alipayAccountClient.selectByThirdUserId(THIRD_USER_ID)).thenReturn(userInfo);
        when(accountChannelPort.updatePaymentChannel(anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());
    }

    private AlipayTripAddContractReqDTO addContractRequest() {
        AlipayTripAddContractReqDTO request = new AlipayTripAddContractReqDTO();
        request.setThirdUserId(THIRD_USER_ID);
        request.setChannel("ALIPAY");
        request.setAgreementCode(AGREEMENT_CODE);
        request.setChannelAgreementCode("2088CHANNEL0001");
        request.setChannelUserAccount(CHANNEL_USER_ACCOUNT);
        return request;
    }

    private AlipayTripTerminateContractReqDTO terminateRequest() {
        AlipayTripTerminateContractReqDTO request = new AlipayTripTerminateContractReqDTO();
        request.setAgreementCode(AGREEMENT_CODE);
        return request;
    }

    private AlipaySignInfo signInfo() {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setAgreementCode(AGREEMENT_CODE);
        signInfo.setChannelAgreementCode("2088CHANNEL0001");
        signInfo.setThirdUserId(THIRD_USER_ID);
        signInfo.setCardId(CARD_ID);
        signInfo.setCardType("0441");
        signInfo.setChannel("ALIPAY");
        signInfo.setSignStatus("SIGNED");
        return signInfo;
    }

    private AlipayTerminationRequest terminationRow() {
        AlipayTerminationRequest request = new AlipayTerminationRequest();
        request.setTerminationSeq("TS20260917000001");
        request.setAgreementCode(AGREEMENT_CODE);
        request.setThirdUserId(THIRD_USER_ID);
        request.setCardId(CARD_ID);
        request.setCardType("0441");
        request.setChannel("ALIPAY");
        request.setOperationType("TERMINATE");
        request.setStatus("PENDING");
        return request;
    }

    private static void inject(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

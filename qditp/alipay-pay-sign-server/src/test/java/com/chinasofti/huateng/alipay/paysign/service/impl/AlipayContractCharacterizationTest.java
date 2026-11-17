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


    @Test
    void blankMandatoryFieldIsRejectedWithInvalidParam() {
        AlipayTripAddContractReqDTO request = addContractRequest();
        request.setChannelUserAccount(" ");

        BusinessException thrown = assertThrows(BusinessException.class, () -> contractService.addContract(request));

        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), thrown.getCode(),
                "四个必填字段任一为空 MUST 返参数异常");
        verify(signInfoMapper, never()).insert(any());
    }


    @Test
    void sameAgreementCodeResubmitIsIdempotent() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());

        AlipayTripAddContractRespDTO response = contractService.addContract(addContractRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(), "同号重复签约 MUST 幂等返成功");
        assertEquals(AGREEMENT_CODE, response.getAgreementCode(), "幂等分支 MUST 回库内协议号");
        verify(signInfoMapper, never()).insert(any());
        verify(accountChannelPort, never()).updatePaymentChannel(anyString(), anyString(), anyString());
    }


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


    @Test
    void terminationRegistrationLetsPersistenceFailurePropagate() {
        when(terminationRequestMapper.countByAgreementCode(AGREEMENT_CODE)).thenReturn(0);
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        doThrow(new IllegalStateException("模拟落库失败")).when(terminationRequestMapper).insert(any());

        assertThrows(IllegalStateException.class, () -> contractService.terminateContract(terminateRequest()),
                "异常 MUST 穿透，否则 @Transactional 形同虚设");
    }


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

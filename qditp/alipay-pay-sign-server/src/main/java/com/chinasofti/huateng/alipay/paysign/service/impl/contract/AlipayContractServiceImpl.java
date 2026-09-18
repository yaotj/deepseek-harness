package com.chinasofti.huateng.alipay.paysign.service.impl.contract;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayContractService;
import com.chinasofti.huateng.alipay.paysign.service.impl.channelsync.ChannelSyncDeliverer;
import com.chinasofti.huateng.alipay.paysign.service.impl.termination.TerminationCoordinator;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.domain.AlipaySignStatus;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * <b>旧实现，迁移期原样保留、行为一行未改；NEVER 在本类新增能力。</b>
 *
 * <p>签约链路的新落点是 {@code service.AlipaySignContractService} +
 * {@code service/impl/sign/AlipaySignContractServiceImpl}（新抽象：{@code SignCommand} 值对象、
 * sealed {@code SignOutcome} 三态、{@code SignRepository} 落库收口、{@code SignLogWriter} 流水）；
 * 解约链路的新落点是 {@code service.AlipayTerminationService}。
 *
 * <p>2026-09-18 的口径是**新旧两套接口与实现完全分开、并行存在**，逐条把正确的行为搬到新服务、
 * 搬一条切一条 URL。因此本类仍是当前在跑的那一侧：三条 URL 的 handler 依旧注
 * {@code AlipayContractService}。**切换某条端点时 MUST 只改对应 controller 的注入**，
 * NEVER 让本类去调新服务、也 NEVER 让新服务反过来调本类 —— 那会让「哪一侧在跑」无法判断。
 *
 * <p>已知行为要点（与新实现逐条对照用）：{@code addContract} 无 {@code @Transactional}（ADR-D129）、
 * 落库三支（短路 / {@code reactivateSign} / INSERT，ADR-D135）、换号一律拒绝、
 * 冲突沿 cause 链判定后回查、通道同步失败仍返 {@code 0000}（留 outbox 给补偿）。
 */
@Service
public class AlipayContractServiceImpl implements AlipayContractService {

    private static final Logger log = LoggerFactory.getLogger(AlipayContractServiceImpl.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    /** 只用查询方向；ADR-D131 刻意没把它收成端口（查不到就拒绝签约，异常冒泡即正确行为）。 */
    @Autowired
    private AlipayAccountClient alipayAccountClient;

    /** 支付通道同步的唯一出网 + 回写，首推与补偿共用（ADR-D132）。 */
    @Autowired
    private ChannelSyncDeliverer channelSyncDeliverer;

    /** 解约域编排入口；本类只转发，NEVER 把解约的三个协作者注回来。 */
    @Autowired
    private TerminationCoordinator terminationCoordinator;

    @Autowired
    private SignLogRecorder signLogRecorder;

    @Override
    public AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request) {
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        log.info("接收到支付宝添加签约信息报文: {}", JSON.toJSONString(request));

        if (request == null || request.getThirdUserId() == null || request.getThirdUserId().trim().isEmpty()
                || request.getChannel() == null || request.getChannel().trim().isEmpty()
                || request.getAgreementCode() == null || request.getAgreementCode().trim().isEmpty()
                || request.getChannelUserAccount() == null || request.getChannelUserAccount().trim().isEmpty()) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "thirdUserId/channel/agreementCode/channelUserAccount不能为空");
        }

        AlipaySignInfo existingSign = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (existingSign != null) {
            return onExistingActiveSign(request, existingSign, response);
        }

        AlipayUserInfoDTO userInfo = alipayAccountClient.selectByThirdUserId(request.getThirdUserId());
        if (userInfo == null || userInfo.getCardId() == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "用户未开户，无法签约");
        }

        AlipaySignInfo signInfo = buildSignInfo(request, userInfo);
        if (alipaySignInfoMapper.selectAnyByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY) != null) {
            if (alipaySignInfoMapper.reactivateSign(signInfo) == 0) {
                return onWriteConflict(request, response);
            }
        } else {
            try {
                alipaySignInfoMapper.insert(signInfo);
            } catch (RuntimeException e) {
                if (!isUniqueConflict(e)) {
                    throw e;
                }
                return onWriteConflict(request, response);
            }
        }

        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        response.setAgreementCode(request.getAgreementCode());

        signLogRecorder.recordSignSuccess(signInfo, request, response);

        syncPaymentChannel(request);

        log.info("支付宝签约成功, thirdUserId={}, agreementCode={}", request.getThirdUserId(), request.getAgreementCode());
        return response;
    }

    /** 命中生效签约：同号幂等返成功，换号一律拒绝（NEVER 覆盖更新，旧协议会再也解不了约，ADR-D135）。 */
    private AlipayTripAddContractRespDTO onExistingActiveSign(AlipayTripAddContractReqDTO request,
                                                              AlipaySignInfo existingSign,
                                                              AlipayTripAddContractRespDTO response) {
        if (!request.getAgreementCode().equals(existingSign.getAgreementCode())) {
            log.error("该用户已有生效签约且协议号不一致，拒绝签约, thirdUserId={}, 库内协议号={}, 入参协议号={}",
                    request.getThirdUserId(), existingSign.getAgreementCode(), request.getAgreementCode());
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该用户已存在生效中的签约，请先解约后再签约");
        }
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("该用户已签约");
        response.setAgreementCode(existingSign.getAgreementCode());
        return response;
    }

    /** 撞唯一约束或 CAS 0 行：判为并发重复请求，回查生效行后按幂等返成功；回查为空才报错。 */
    private AlipayTripAddContractRespDTO onWriteConflict(AlipayTripAddContractReqDTO request,
                                                        AlipayTripAddContractRespDTO response) {
        log.warn("签约落库与并发请求冲突，判为重复请求, thirdUserId={}, agreementCode={}",
                request.getThirdUserId(), request.getAgreementCode());
        AlipaySignInfo committed = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (committed == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "签约冲突且回查不到生效签约，请重试");
        }
        return onExistingActiveSign(request, committed, response);
    }

    /** 沿 cause 链判唯一约束冲突，NEVER 只看最外层类型（本模块开着 tracing，切面会包一层，AGENTS.md §5.2）。 */
    private boolean isUniqueConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DuplicateKeyException || t instanceof DataIntegrityViolationException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private AlipaySignInfo buildSignInfo(AlipayTripAddContractReqDTO request, AlipayUserInfoDTO userInfo) {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setAgreementCode(request.getAgreementCode());
        signInfo.setThirdUserId(request.getThirdUserId());
        signInfo.setCardId(userInfo.getCardId());
        signInfo.setCardType(userInfo.getCardType());
        signInfo.setChannel(CHANNEL_ALIPAY);
        signInfo.setChannelAgreementCode(request.getChannelAgreementCode());
        signInfo.setChannelUserAccount(request.getChannelUserAccount());
        signInfo.setSignStatus(AlipaySignStatus.SIGNED.name());
        signInfo.setOperationType("SIGN");
        signInfo.setSignTime(LocalDateTime.now());
        signInfo.setDeleteFlag("0");
        signInfo.setVersion("1");
        signInfo.setCreateTime(LocalDateTime.now());
        signInfo.setUpdateTime(LocalDateTime.now());
        return signInfo;
    }

    /** 签约已成立后的通道同步：本方法 NEVER 抛异常，未成功的行留在 outbox 上等补偿。 */
    private void syncPaymentChannel(AlipayTripAddContractReqDTO request) {
        channelSyncDeliverer.deliver(request.getThirdUserId(), request.getChannelUserAccount(),
                request.getAgreementCode());
    }

    /** 解约登记 —— 转发；事务在 {@code TerminationRegistrationService#terminateContract} 上，本层 NEVER 加事务。 */
    @Override
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        return terminationCoordinator.terminateContract(request);
    }

    @Override
    public AlipaySignInfoDTO selectSignInfo(String thirdUserId) {
        if (!StringUtils.hasText(thirdUserId)) {
            return null;
        }
        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(thirdUserId, CHANNEL_ALIPAY);
        if (signInfo == null) {
            return null;
        }
        AlipaySignInfoDTO dto = new AlipaySignInfoDTO();
        dto.setThirdUserId(signInfo.getThirdUserId());
        dto.setChannelAgreementCode(signInfo.getChannelAgreementCode());
        dto.setCardId(signInfo.getCardId());
        dto.setCardType(signInfo.getCardType());
        return dto;
    }

    /** 立即执行一条解约 —— 转发；编排与三分支映射都在 coordinator 里，NEVER 在本类重实现。 */
    @Override
    public AlipayCommonResponse executeTermination(String agreementCode) {
        return terminationCoordinator.executeTermination(agreementCode);
    }
}

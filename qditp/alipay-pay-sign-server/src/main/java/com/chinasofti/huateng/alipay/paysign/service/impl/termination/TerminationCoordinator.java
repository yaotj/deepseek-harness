package com.chinasofti.huateng.alipay.paysign.service.impl.termination;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 解约域的编排入口：登记转发 + 立即执行一条解约。
 *
 * <p><b>2026-09-18 从 {@code AlipayContractServiceImpl} 拆出</b>（推翻 ADR-D133 的「刻意不拆」，
 * 见同日新立的 ADR）。拆分理由不是「依赖簇不相交」这一条本身 —— 那条判据 D133 已经承认成立、
 * 但当时判定 230 行不足以产生收益。现在的实测事实是：那个类已涨到 301 行，且**签约与解约两簇
 * 的依赖完全不重叠** —— 签约只用 {@code AlipaySignInfoMapper} / {@code AlipayAccountClient} /
 * {@code ChannelSyncDeliverer} / {@code SignLogRecorder}，解约只用
 * {@code AlipayTerminationRequestMapper} / {@code TerminationNotifier} /
 * {@code TerminationRegistrationService}，交集为空。拆开后解约链路的三个协作者与
 * {@code TerminationNotifier} / {@code TerminationRegistrationService} 同包，**跨包依赖归零**。
 *
 * <p>本类**只做编排、不自己动任何状态**：状态收口与「先远端后本地」的顺序只有
 * {@link TerminationNotifier#execute} 一份实现，事务只在
 * {@link TerminationRegistrationService#terminateContract} 上。
 * <b>NEVER 在本类里加 {@code @Transactional}，也 NEVER 在这里重新实现一遍通知与 CAS 收口。</b>
 */
@Service
public class TerminationCoordinator implements AlipayTerminationService {

    private static final Logger log = LoggerFactory.getLogger(TerminationCoordinator.class);

    @Autowired
    private AlipayTerminationRequestMapper alipayTerminationRequestMapper;

    @Autowired
    private TerminationNotifier terminationNotifier;

    @Autowired
    private TerminationRegistrationService terminationRegistrationService;

    /**
     * 解约登记。
     *
     * <p>事务在 {@link TerminationRegistrationService#terminateContract} 上，本层只做委派，
     * NEVER 在这里再套一层 {@code @Transactional} —— 那层事务什么都不做，只会掩盖真正的边界。</p>
     */
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        return terminationRegistrationService.terminateContract(request);
    }

    /**
     * 立即执行一条解约（按协议号）。
     *
     * <p>ADR-D129 起本方法**只做编排、不自己动状态**：登记缺失就先补一条 PENDING 登记，随后
     * 一律委派 {@link TerminationNotifier#execute}。这样「远端先、本地后」与 CAS 收口只有
     * 一份实现 —— 此前本方法自带的一份是「先把签约置 TERMINATED、再通知支付中心」，顺序与
     * 批处理链路相反，远端失败时会留下「我方已解约、支付中心仍在签约」且无法自愈。
     * <b>NEVER 在这里重新实现一遍通知与状态收口。</b>
     */
    public AlipayCommonResponse executeTermination(String agreementCode) {
        log.info("支付宝出行-执行解约, agreementCode={}", agreementCode);

        if (!org.springframework.util.StringUtils.hasText(agreementCode)) {
            return commonResponse(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "无效的参数：agreementCode不能为空");
        }

        if (alipayTerminationRequestMapper.countByAgreementCode(agreementCode) == 0) {
            AlipayTripTerminateContractReqDTO registerRequest = new AlipayTripTerminateContractReqDTO();
            registerRequest.setAgreementCode(agreementCode);
            AlipayTripTerminateContractRespDTO registerResponse = terminationRegistrationService.terminateContract(registerRequest);
            if (!FepAppErrorCodeEnum.SUCCESS.getCode().equals(registerResponse.getRetCode())) {
                log.warn("支付宝出行-执行解约,补登记未成功, agreementCode={}, retCode={}", agreementCode, registerResponse.getRetCode());
                return commonResponse(registerResponse.getRetCode(), registerResponse.getRetMsg());
            }
        }

        AlipayTerminationRequest terminationRequest = alipayTerminationRequestMapper.selectByAgreementCode(agreementCode);
        if (terminationRequest == null) {
            log.warn("支付宝出行-执行解约,解约登记记录不存在, agreementCode={}", agreementCode);
            return commonResponse(FepAppErrorCodeEnum.FAIL.getCode(), "解约登记记录不存在");
        }

        TerminationNotifier.Outcome outcome = terminationNotifier.execute(terminationRequest);
        return switch (outcome) {
            case TERMINATED -> commonResponse(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
            case FAILED -> commonResponse(FepAppErrorCodeEnum.FAIL.getCode(), "解约失败，已置终态");
            case RETRY_LATER -> commonResponse(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "解约未完成，登记保持 PENDING 待重试");
        };
    }

    private AlipayCommonResponse commonResponse(String retCode, String retMsg) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}

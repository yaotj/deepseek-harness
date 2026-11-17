package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipaySignContractService;
import com.chinasofti.huateng.alipay.paysign.service.impl.channelsync.ChannelSyncDeliverer;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
public class AlipaySignContractServiceImpl implements AlipaySignContractService {

    private static final Logger log = LoggerFactory.getLogger(AlipaySignContractServiceImpl.class);

    @Autowired
    private SignRepository signRepository;


    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Autowired
    private ChannelSyncDeliverer channelSyncDeliverer;

    @Autowired
    private SignLogWriter signLogWriter;

    @Override
    public AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request) {
        log.info("接收到支付宝添加签约信息报文: {}", JSON.toJSONString(request));
        SignCommand command = SignCommand.from(request);

        AlipaySignInfo activeSign = signRepository.findActiveSign(command.thirdUserId());
        if (activeSign != null) {
            return onAlreadySigned(command, activeSign);
        }

        AlipayUserInfoDTO userInfo = alipayAccountClient.selectByThirdUserId(command.thirdUserId());
        if (userInfo == null || userInfo.getCardId() == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "用户未开户，无法签约");
        }

        SignOutcome outcome = signRepository.save(command.toSignRow(userInfo));
        return switch (outcome) {
            case SignOutcome.AlreadySigned(AlipaySignInfo existing) -> onAlreadySigned(command, existing);
            case SignOutcome.Reactivated(AlipaySignInfo signInfo) -> onSignEstablished(command, signInfo, request);
            case SignOutcome.Created(AlipaySignInfo signInfo) -> onSignEstablished(command, signInfo, request);
        };
    }


    private AlipayTripAddContractRespDTO onAlreadySigned(SignCommand command, AlipaySignInfo existing) {
        if (!command.agreementCode().equals(existing.getAgreementCode())) {
            log.error("该用户已有生效签约且协议号不一致，拒绝签约, thirdUserId={}, 库内协议号={}, 入参协议号={}",
                    command.thirdUserId(), existing.getAgreementCode(), command.agreementCode());
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该用户已存在生效中的签约，请先解约后再签约");
        }
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("该用户已签约");
        response.setAgreementCode(existing.getAgreementCode());
        return response;
    }


    private AlipayTripAddContractRespDTO onSignEstablished(SignCommand command, AlipaySignInfo signInfo,
                                                          AlipayTripAddContractReqDTO request) {
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        response.setAgreementCode(command.agreementCode());

        signLogWriter.recordSignSuccess(signInfo, request, response);
        channelSyncDeliverer.deliver(command.thirdUserId(), command.channelUserAccount(), command.agreementCode());

        log.info("支付宝签约成功, thirdUserId={}, agreementCode={}", command.thirdUserId(), command.agreementCode());
        return response;
    }

    @Override
    public AlipaySignInfoDTO selectSignInfo(String thirdUserId) {
        if (!StringUtils.hasText(thirdUserId)) {
            return null;
        }
        AlipaySignInfo signInfo = signRepository.findActiveSign(thirdUserId);
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
}

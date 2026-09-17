package com.chinasofti.huateng.alipay.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.account.entity.AlipayRegLog;
import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.entity.AlipayPhoneChangeLog;
import com.chinasofti.huateng.alipay.account.mapper.AlipayRegLogMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayPhoneChangeLogMapper;
import com.chinasofti.huateng.alipay.account.service.AlipayAccountService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReserveResult;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AlipayAccountServiceImpl implements AlipayAccountService {
    private static final Logger log = LoggerFactory.getLogger(AlipayAccountServiceImpl.class);

    @Autowired
    private AlipayUserInfoMapper alipayUserInfoMapper;

    @Autowired
    private AlipayPhoneChangeLogMapper alipayPhoneChangeLogMapper;

    @Autowired
    private AlipayRegLogMapper alipayRegLogMapper;

    @Autowired
    private TicketClient ticketClient;

    @Autowired
    private CardPoolClient cardPoolClient;

    /** 开卡链路的落库部分用它显式开短事务。 */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /** 支付宝出行-开卡申请（规范 3.69）。 */
    @Override
    public AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request) {
        AlipayTripRequestApplicationRespDTO response = new AlipayTripRequestApplicationRespDTO();
        CardPoolReserveResult cardPool = null;
        boolean reservationSettled = false;
        try {
            log.info("接收到支付宝出行-开卡申请报文: {}", JSON.toJSONString(request));

            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("开卡申请参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            AlipayUserInfo existed = alipayUserInfoMapper.selectByThirdUserId(thirdUserId);
            if (existed != null) {
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("用户已开户");
                response.setCardId(existed.getCardId());
                response.setCardType(existed.getCardType());
                response.setStatus(existed.getStatus());
                log.warn("用户已开户, thirdUserId={}, cardId={}, cardType={}",
                        existed.getThirdUserId(), existed.getCardId(), existed.getCardType());
                return response;
            }

            cardPool = reserveCard(thirdUserId, request.getCardType());
            if (!cardPool.isSuccess()) {
                fillReserveFailure(response, cardPool, thirdUserId, request.getCardType());
                return response;
            }

            String requestSeq = UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
            String cardId = cardPool.getData().getCardNo();

            RegisterRideStatusReqDTO registerReq = new RegisterRideStatusReqDTO();
            registerReq.setThirdUserId(thirdUserId);
            registerReq.setCardId(cardId);
            registerReq.setCardType(request.getCardType());
            RegisterRideStatusRespDTO registerResp = ticketClient.registerRideStatus(registerReq);
            if (registerResp == null || !FepAppErrorCodeEnum.SUCCESS.getCode().equals(registerResp.getRetCode())) {
                throw new RuntimeException("调用 ticket-server 注册乘车状态失败, thirdUserId=" + thirdUserId + ", cardId=" + cardId + ", resp=" + registerResp);
            }

            AlipayUserInfo userInfo = buildAlipayUserInfo(request, cardId);
            AlipayRegLog regLog = buildAlipayRegLog(request, cardId, requestSeq);
            transactionTemplate.executeWithoutResult(status -> {
                alipayUserInfoMapper.insert(userInfo);
                alipayRegLogMapper.insert(regLog);
            });

            CardPoolActionResult confirmResult = cardPoolClient.confirm(cardPool.getData().getReservationId(),
                    reservationBusinessId(thirdUserId, request.getCardType()));
            if (!confirmResult.isSuccess()) {
                log.error("确认逻辑卡号预占失败，卡号已发给用户、NEVER 回滚开户数据，留人工核对, thirdUserId={}, cardId={}, outcome={}, msg={}",
                        thirdUserId, cardId, confirmResult.getOutcome(), confirmResult.getMessage());
            }
            reservationSettled = true;

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            response.setCardId(cardId);
            response.setCardType(request.getCardType());
            response.setStatus("ACTIVE");
            log.info("开卡申请成功, thirdUserId={}, cardId={}, cardType={}", thirdUserId, cardId, request.getCardType());
            return response;
        } catch (Exception e) {
            if (cardPool != null && cardPool.isSuccess() && !reservationSettled
                    && request != null && StringUtils.hasText(request.getThirdUserId())) {
                String thirdUserId = request.getThirdUserId().trim();
                releaseReservation(cardPool.getData().getReservationId(),
                        reservationBusinessId(thirdUserId, request.getCardType()), thirdUserId);
            }
            log.error("开卡申请异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("系统内部错误");
            return response;
        }
    }

    @Override
    public AlipayUserInfoDTO selectByThirdUserId(String thirdUserId) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty()) {
            return null;
        }
        return toUserInfoDto(alipayUserInfoMapper.selectByThirdUserId(thirdUserId.trim()));
    }

    @Override
    public AlipayUserInfoDTO selectByCardId(String cardId) {
        if (cardId == null || cardId.trim().isEmpty()) {
            return null;
        }
        return toUserInfoDto(alipayUserInfoMapper.selectByCardId(cardId.trim()));
    }

    private AlipayUserInfoDTO toUserInfoDto(AlipayUserInfo userInfo) {
        if (userInfo == null) {
            return null;
        }
        AlipayUserInfoDTO dto = new AlipayUserInfoDTO();
        dto.setThirdUserId(userInfo.getThirdUserId());
        dto.setCardId(userInfo.getCardId());
        dto.setCardType(userInfo.getCardType());
        dto.setThirdPayId(userInfo.getThirdPayId());
        dto.setReqContractNo(userInfo.getReqContractNo());
        dto.setChannel(userInfo.getChannel());
        dto.setPhone(userInfo.getMsisdn());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty()) {
            log.warn("更新用户支付通道失败, thirdUserId 为空");
            return false;
        }
        try {
            AlipayUserInfo userInfo = new AlipayUserInfo();
            userInfo.setThirdUserId(thirdUserId);
            userInfo.setThirdPayId(thirdPayId);
            userInfo.setReqContractNo(reqContractNo);
            int updated = alipayUserInfoMapper.updatePaymentChannel(userInfo);
            if (updated > 0) {
                log.info("更新用户支付通道成功, thirdUserId={}, thirdPayId={}, reqContractNo={}",
                        thirdUserId, thirdPayId, reqContractNo);
                return true;
            }
            log.warn("更新用户支付通道失败，用户不存在, thirdUserId={}", thirdUserId);
            return false;
        } catch (Exception e) {
            log.error("更新用户支付通道异常, thirdUserId={}", thirdUserId, e);
            return false;
        }
    }

    /** 支付宝渠道换号：只改 {@code ALIPAY_USER_INFO} 并落一条 {@code ALIPAY_PHONE_CHANGE_LOG}。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePhone(String thirdUserId, String newMsisdn) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty() || newMsisdn == null || newMsisdn.trim().isEmpty()) {
            log.warn("更换手机号参数校验失败, thirdUserId={}, newMsisdn={}", thirdUserId, newMsisdn);
            return false;
        }
        try {
            AlipayUserInfo userInfo = alipayUserInfoMapper.selectByThirdUserId(thirdUserId.trim());
            if (userInfo == null) {
                log.warn("更换手机号未找到有效用户, thirdUserId={}", thirdUserId);
                return false;
            }
            String oldMsisdn = userInfo.getMsisdn();
            if (oldMsisdn != null && oldMsisdn.equals(newMsisdn)) {
                log.info("新旧手机号相同，无需更换, thirdUserId={}, msisdn={}", thirdUserId, newMsisdn);
                return true;
            }
            int updated = alipayUserInfoMapper.updateMsisdnByThirdUserId(thirdUserId.trim(), newMsisdn.trim());
            if (updated == 0) {
                log.warn("更换手机号更新失败, thirdUserId={}", thirdUserId);
                return false;
            }
            AlipayPhoneChangeLog changeLog = new AlipayPhoneChangeLog();
            changeLog.setThirdUserId(thirdUserId.trim());
            changeLog.setOldMsisdn(oldMsisdn);
            changeLog.setNewMsisdn(newMsisdn.trim());
            changeLog.setOperType("CHANGE_PHONE");
            changeLog.setOperTime(LocalDateTime.now());
            changeLog.setOperator("SYSTEM");
            changeLog.setRemark("支付宝用户更换手机号");
            changeLog.setCreateTms(LocalDateTime.now());
            alipayPhoneChangeLogMapper.insert(changeLog);
            log.info("支付宝用户更换手机号成功, thirdUserId={}, oldMsisdn={}, newMsisdn={}",
                    thirdUserId, oldMsisdn, newMsisdn);
            return true;
        } catch (Exception e) {
            log.error("更换手机号异常, thirdUserId={}", thirdUserId, e);
            return false;
        }
    }

    /**
     * 向逻辑卡号池预占一个卡号。
     * @param thirdUserId 支付宝用户标识，作为预占归属方
     * @param appCardType APP 侧票种码，内部转换为发卡票种码
     * @return 预占结果，非 SUCCESS 时 data 为空，由调用方按 outcome 分流
     */
    private CardPoolReserveResult reserveCard(String thirdUserId, String appCardType) {
        String cardType = CardTypeMapping.toIssueCardType(appCardType);
        CardPoolReservationReqDTO request = new CardPoolReservationReqDTO();
        request.setCardType(cardType);
        request.setBusinessType("ALIPAY_ACCOUNT_OPEN");
        request.setBusinessId(reservationBusinessId(thirdUserId, appCardType));
        request.setOwnerId(thirdUserId);
        return cardPoolClient.reserve(request);
    }

    /**
     * 按预占失败分类填充对外响应并落日志。
     * @param response    待填充的对外响应
     * @param result      预占结果，outcome 必为非 SUCCESS
     * @param thirdUserId 支付宝用户标识，仅用于日志定位
     * @param cardType    APP 侧票种码，仅用于日志定位
     */
    private void fillReserveFailure(AlipayTripRequestApplicationRespDTO response, CardPoolReserveResult result,
                                    String thirdUserId, String cardType) {
        switch (result.getOutcome()) {
            case POOL_EMPTY -> {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("逻辑卡号池暂无可用卡号，请稍后重试");
                log.warn("逻辑卡号池已空, thirdUserId={}, cardType={}, msg={}",
                        thirdUserId, cardType, result.getMessage());
            }
            case REJECTED -> {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("票种配置错误，无法开卡");
                log.error("逻辑卡号预占被拒绝，票种不走卡池或归属冲突，重试无用, thirdUserId={}, cardType={}, msg={}",
                        thirdUserId, cardType, result.getMessage());
            }
            default -> {
                response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
                response.setRetMsg("发卡服务暂不可用，请稍后重试");
                log.error("调用逻辑卡号池预占失败, thirdUserId={}, cardType={}, outcome={}, msg={}",
                        thirdUserId, cardType, result.getOutcome(), result.getMessage());
            }
        }
    }

    /**
     * 释放已预占的逻辑卡号，失败只记 WARN。
     * @param reservationId 预占记录标识
     * @param businessId    预占时使用的业务流水号，须完全一致
     * @param thirdUserId   支付宝用户标识，仅用于日志定位
     */
    private void releaseReservation(String reservationId, String businessId, String thirdUserId) {
        CardPoolActionResult releaseResult = cardPoolClient.release(reservationId, businessId);
        if (!releaseResult.isSuccess()) {
            log.warn("释放逻辑卡号预占失败，不影响对外结论，靠预占超时回收兜底, thirdUserId={}, reservationId={}, outcome={}, msg={}",
                    thirdUserId, reservationId, releaseResult.getOutcome(), releaseResult.getMessage());
        }
    }

    private String reservationBusinessId(String thirdUserId, String appCardType) {
        return "ALIPAY_ACCOUNT_OPEN:" + thirdUserId + ":" + CardTypeMapping.toIssueCardType(appCardType);
    }

    private String validateRequest(AlipayTripRequestApplicationReqDTO request) {
        if (request == null) {
            return "请求参数为空";
        }
        if (request.getThirdUserId() == null || request.getThirdUserId().trim().isEmpty()) {
            return "thirdUserId 不能为空";
        }
        if (request.getCardType() == null || request.getCardType().trim().isEmpty()) {
            return "cardType 不能为空";
        }
        String issueCardType = CardTypeMapping.toIssueCardType(request.getCardType());
        if (CardTypeCodeEnum.isHceCard(issueCardType)) {
            return "HCE卡仅支持安全服务实时发卡";
        }
        if (CardTypeCodeEnum.isEmployeeCard(issueCardType)) {
            return "员工票仅支持APP静默开户";
        }
        if (request.getCardIssueCode() == null || request.getCardIssueCode().trim().isEmpty()) {
            return "cardIssueCode 不能为空";
        }
        return null;
    }

    private AlipayUserInfo buildAlipayUserInfo(AlipayTripRequestApplicationReqDTO request, String cardId) {
        AlipayUserInfo userInfo = new AlipayUserInfo();
        userInfo.setThirdUserId(request.getThirdUserId().trim());
        userInfo.setCardId(cardId);
        userInfo.setCardType(request.getCardType());
        userInfo.setCardIssueCode(request.getCardIssueCode());
        userInfo.setMsisdn(request.getMsisdn());
        userInfo.setExtend1(request.getExtend1());
        userInfo.setExtend2(request.getExtend2());
        userInfo.setChannel(IssueChannelCodeEnum.ALIPAY.getCode());
        userInfo.setStatus("ACTIVE");
        userInfo.setDeleteFlag("0");
        userInfo.setVersion("1");
        return userInfo;
    }

    private AlipayRegLog buildAlipayRegLog(AlipayTripRequestApplicationReqDTO request, String cardId, String requestSeq) {
        AlipayRegLog regLog = new AlipayRegLog();
        regLog.setRequestSeq(requestSeq);
        regLog.setThirdUserId(request.getThirdUserId().trim());
        regLog.setCardId(cardId);
        regLog.setCardType(request.getCardType());
        regLog.setCardIssueCode(request.getCardIssueCode());
        regLog.setChannel(IssueChannelCodeEnum.ALIPAY.getCode());
        regLog.setMsisdn(request.getMsisdn());
        regLog.setExtend1(request.getExtend1());
        regLog.setExtend2(request.getExtend2());
        regLog.setRequestBody(JSON.toJSONString(request));
        regLog.setResultCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        regLog.setResultMsg("成功");
        regLog.setVersion("1");
        regLog.setDeleteFlag("0");
        regLog.setCreateTime(LocalDateTime.now());
        regLog.setUpdateTime(LocalDateTime.now());
        return regLog;
    }
}

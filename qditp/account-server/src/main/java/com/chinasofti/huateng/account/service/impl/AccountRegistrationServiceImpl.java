package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.mapper.AccountExceptionTicketMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.service.AccountRegistrationService;
import com.chinasofti.huateng.account.service.CardPoolAllocationService;
import com.chinasofti.huateng.account.service.CardPoolAllocationService.CardAllocation;
import com.chinasofti.huateng.account.service.CardPoolAllocationService.HceCardAllocation;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.account.service.RegistrationCommitService;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.enums.CardIssueOrgEnum;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
public class AccountRegistrationServiceImpl implements AccountRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(AccountRegistrationServiceImpl.class);


    private static final String ALIPAY_PAYMENT_CHANNEL = "03";


    private static final String CARD_POOL_BUSINESS_TYPE_ACCOUNT_OPEN = "ACCOUNT_OPEN";


    private final CardPoolAllocationService cardPoolAllocationService;


    private final UserItpRegInfoMapper userItpRegInfoMapper;


    private final RegistrationCommitService registrationCommitService;


    private final EmployeeCardPersistenceService employeeCardPersistenceService;


    private final AccountExceptionTicketMapper accountExceptionTicketMapper;


    public AccountRegistrationServiceImpl(CardPoolAllocationService cardPoolAllocationService,
                                          UserItpRegInfoMapper userItpRegInfoMapper,
                                          RegistrationCommitService registrationCommitService,
                                          EmployeeCardPersistenceService employeeCardPersistenceService,
                                          AccountExceptionTicketMapper accountExceptionTicketMapper) {
        this.cardPoolAllocationService = cardPoolAllocationService;
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.registrationCommitService = registrationCommitService;
        this.employeeCardPersistenceService = employeeCardPersistenceService;
        this.accountExceptionTicketMapper = accountExceptionTicketMapper;
    }


    @Override
    public RequestApplicationResult requestApplication(RequestApplicationReqDTO request) {
        RequestApplicationResult response = new RequestApplicationResult();
        CardAllocation allocation = null;
        try {
            log.info("开始处理IF8A-01请求开户, request={}", JSON.toJSONString(request));

            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-01参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardType = CardTypeMapping.toIssueCardType(request.getCardType());
            String issueOrgCode = request.getCardIssueCode().trim();
            boolean uniqueTicket = "1".equals(request.getTicketLimit().trim());
            if (uniqueTicket) {
                UserItpRegInfo existed = userItpRegInfoMapper.selectActiveUniqueCard(thirdUserId, issueOrgCode, cardType);
                if (existed != null && existed.isActive()) {
                    response.setRetCode(AccountErrorCodeEnum.ALREADY_REGISTERED.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.ALREADY_REGISTERED.getMsg());
                    response.setCardId(existed.getCardId());
                    response.setCardType(existed.getCardType());
                    response.setSignType("00");
                    response.setSign("");
                    log.warn("IF8A-01用户已开户, thirdUserId={}, cardId={}, cardType={}",
                            existed.getThirdUserId(), existed.getCardId(), existed.getCardType());
                    return response;
                }
            }

            allocation = allocateCard(request, thirdUserId, cardType);
            if (allocation == null || !StringUtils.hasText(allocation.cardId())) {
                response.setRetCode(AccountErrorCodeEnum.NO_CARD_RESOURCE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_CARD_RESOURCE.getMsg());
                log.warn("IF8A-01无可分配卡资源, thirdUserId={}, cardType={}", thirdUserId, cardType);
                return response;
            }

            UserItpRegInfo regInfo = buildRegInfo(request, allocation.cardId(), allocation.hceData());
            RegisterRideStatusRespDTO ticketResponse = registrationCommitService.registerRideStatus(regInfo);
            if (ticketResponse == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(ticketResponse.getRetCode())) {
                allocation = null;
                response.setRetCode(AccountErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg(ticketResponse == null ? "ticket-server不可用" : ticketResponse.getRetMsg());
                log.error("IF8A-01调用ticket-server失败，预占不释放、等卡池超时回收, thirdUserId={}, response={}",
                        thirdUserId, JSON.toJSONString(ticketResponse));
                return response;
            }

            try {
                registrationCommitService.persistRegistration(regInfo);
            } catch (Exception e) {
                if (!uniqueTicket || !isDuplicateKeyViolation(e)) {
                    throw e;
                }
                RequestApplicationResult duplicated = handleDuplicateRegistration(thirdUserId, issueOrgCode, cardType, e);
                allocation = null;
                return duplicated;
            }

            boolean confirmed = cardPoolAllocationService.confirmReservation(allocation, "IF8A-01开户");
            CardAllocation confirmedAllocation = allocation;
            allocation = null;

            if (!confirmed) {
                openCardPoolConfirmTicketQuietly(confirmedAllocation, regInfo);
                response.setRetCode(AccountErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg("卡号确认失败，请稍后重试");
                log.error("IF8A-01开户已落库但卡池确认失败，已开工单转人工, thirdUserId={}, cardId={}, reservationId={}",
                        regInfo.getThirdUserId(), regInfo.getCardId(), confirmedAllocation.reservationId());
                return response;
            }

            employeeCardPersistenceService.attachEmployeeCardsQuietly(regInfo.getThirdUserId(), regInfo.getMsisdn());

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setCardId(regInfo.getCardId());
            response.setCardType(regInfo.getCardType());
            response.setSignType("00");
            response.setSign("");
            log.info("IF8A-01开户成功, thirdUserId={}, cardId={}, cardType={}",
                    regInfo.getThirdUserId(), regInfo.getCardId(), regInfo.getCardType());
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-01请求开户异常，预占不释放、等卡池超时回收, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }


    private void openCardPoolConfirmTicketQuietly(CardAllocation allocation, UserItpRegInfo regInfo) {
        try {
            AccountExceptionTicket ticket = new AccountExceptionTicket();
            ticket.setTicketType(AccountExceptionTicket.TYPE_CARD_POOL_CONFIRM_REJECTED);
            ticket.setBizKey(allocation.reservationId());
            ticket.setThirdUserId(regInfo.getThirdUserId());
            ticket.setTicketStatus(AccountExceptionTicket.STATUS_OPEN);
            ticket.setRetryCount(0);
            ticket.setDetail("IF8A-01开户已落库但卡池确认被拒，cardId=" + regInfo.getCardId()
                    + ", cardType=" + regInfo.getCardType() + ", businessId=" + allocation.businessId());
            ticket.setCreateTms(LocalDateTime.now());
            accountExceptionTicketMapper.insert(ticket);
        } catch (Exception ex) {
            log.error("卡池确认异常工单开立失败或已存在, reservationId={}, cardId={}",
                    allocation.reservationId(), regInfo.getCardId(), ex);
        }
    }


    private RequestApplicationResult handleDuplicateRegistration(String thirdUserId, String issueOrgCode, String cardType,
                                                                 Exception cause) {
        RequestApplicationResult response = new RequestApplicationResult();
        response.setSignType("00");
        response.setSign("");
        UserItpRegInfo existed = userItpRegInfoMapper.selectActiveUniqueCard(thirdUserId, issueOrgCode, cardType);
        if (existed == null || !existed.isActive() || !StringUtils.hasText(existed.getCardId())) {
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            log.error("IF8A-01唯一冲突后未查到有效唯一卡, thirdUserId={}, issueOrgCode={}, cardType={}",
                    thirdUserId, issueOrgCode, cardType, cause);
            return response;
        }
        response.setRetCode(AccountErrorCodeEnum.ALREADY_REGISTERED.getCode());
        response.setRetMsg(AccountErrorCodeEnum.ALREADY_REGISTERED.getMsg());
        response.setCardId(existed.getCardId());
        response.setCardType(existed.getCardType());
        log.warn("IF8A-01开户撞唯一约束，按已开户返回, thirdUserId={}, issueOrgCode={}, cardType={}, existedCardId={}",
                thirdUserId, issueOrgCode, cardType, existed.getCardId(), cause);
        return response;
    }


    private boolean isDuplicateKeyViolation(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }


    private CardAllocation allocateCard(RequestApplicationReqDTO request, String thirdUserId, String issueCardType) {
        if (cardPoolAllocationService.isHceCard(request.getCardType())) {
            HceCardAllocation hce = cardPoolAllocationService.requestHceCardData(thirdUserId, request.getTicketCard());
            return hce == null ? null : new CardAllocation(hce.cardId(), hce.hceData(), null, null);
        }
        return cardPoolAllocationService.reserveFromPool(issueCardType, CARD_POOL_BUSINESS_TYPE_ACCOUNT_OPEN,
                buildAccountOpenBusinessId(request, thirdUserId, issueCardType), thirdUserId);
    }


    private String buildAccountOpenBusinessId(RequestApplicationReqDTO request, String thirdUserId,
                                              String issueCardType) {
        if ("2".equals(request.getTicketLimit().trim())) {
            return "MULTI:" + UUID.randomUUID();
        }
        String issueOrgCode = request.getCardIssueCode().trim();
        return "UNIQUE:" + thirdUserId.length() + ":" + thirdUserId
                + issueOrgCode.length() + ":" + issueOrgCode + ":" + issueCardType;
    }

    private String validateRequest(RequestApplicationReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (request.getThirdUserId().trim().codePointCount(0, request.getThirdUserId().trim().length()) > 64) {
            return "thirdUserId不能超过64个字符";
        }
        if (!StringUtils.hasText(request.getCardIssueCode())) {
            return "cardIssueCode不能为空";
        }
        String issueOrgCode = request.getCardIssueCode().trim();
        if (issueOrgCode.codePointCount(0, issueOrgCode.length()) > 16) {
            return "cardIssueCode不能超过16个字符";
        }
        if (!StringUtils.hasText(request.getTicketLimit())
                || !("1".equals(request.getTicketLimit().trim()) || "2".equals(request.getTicketLimit().trim()))) {
            return "ticketLimit必须为1或2";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!CardTypeMapping.isSupportedAppCardType(request.getCardType())) {
            return "cardType不支持";
        }
        if (CardTypeMapping.isAiShanDong(request.getCardType())
                && StringUtils.hasText(request.getChannel())
                && !ALIPAY_PAYMENT_CHANNEL.equals(request.getChannel().trim())) {
            return "鲁通码支付渠道必须为03";
        }
        if (CardTypeMapping.isAiShanDong(request.getCardType())
                && "07".equals(request.getCardIssueCode() == null ? null : request.getCardIssueCode().trim())) {
            return "鲁通码发行渠道必须为APP推送渠道";
        }
        if (!StringUtils.hasText(request.getMsisdn())) {
            return "msisdn不能为空";
        }
        return null;
    }


    private UserItpRegInfo buildRegInfo(RequestApplicationReqDTO request, String cardId, String hceData) {
        UserItpRegInfo regInfo = new UserItpRegInfo();
        regInfo.setCardId(cardId);
        regInfo.setCardType(CardTypeMapping.toIssueCardType(request.getCardType()));
        regInfo.setItpCardType(request.getCardType().trim());
        regInfo.setThirdUserId(request.getThirdUserId().trim());
        regInfo.setMsisdn(request.getMsisdn());
        regInfo.setRegTms(LocalDateTime.now());
        regInfo.setDelYn(1);
        regInfo.setUserName(request.getUserName());
        regInfo.setUserId(request.getUserId());
        regInfo.setIssueOrgCode(registrationCommitService.normalizeIssueOrgCode(request.getCardIssueCode()));
        regInfo.setCardIssueCode(CardIssueOrgEnum.toIssueChannelCode4(request.getCardIssueCode()));
        regInfo.setThirdPayId(request.getThirdPayId());
        regInfo.setChannel(resolveDefaultChannel(request));
        regInfo.setReqContractNo(request.getReqContractNo());
        regInfo.setHceData(hceData);
        regInfo.setCompanionFlag(request.getCompanionFlag());
        regInfo.setTicketLimit(request.getTicketLimit().trim());
        return regInfo;
    }

    private String resolveDefaultChannel(RequestApplicationReqDTO request) {
        if (CardTypeMapping.isAiShanDong(request.getCardType())) {
            return ALIPAY_PAYMENT_CHANNEL;
        }
        String issueCardType = CardTypeMapping.toIssueCardType(request.getCardType());
        if (CardTypeCodeEnum.isDailyTicket(issueCardType) || CardTypeCodeEnum.isEmployeeCard(issueCardType)) {
            return request.getCardType().trim();
        }
        return request.getChannel();
    }

}

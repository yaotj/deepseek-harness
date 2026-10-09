package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.mapper.AccountExceptionTicketMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.service.AlipayTripRegistrationService;
import com.chinasofti.huateng.account.service.CardPoolAllocationService;
import com.chinasofti.huateng.account.service.CardPoolAllocationService.CardAllocation;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.account.service.RegistrationCommitService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付宝出行开卡实现，见 {@link AlipayTripRegistrationService}（**保留但不在链路上**，ADR-D15）。
 */
@Service
public class AlipayTripRegistrationServiceImpl implements AlipayTripRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(AlipayTripRegistrationServiceImpl.class);

    /**
     * 支付宝出行开卡走卡池发号时的业务类型，与 {@code LOGIC_CARD_POOL_CARD.BUSINESS_TYPE} 对应。
     */
    private static final String CARD_POOL_BUSINESS_TYPE_ALIPAY_TRIP_OPEN = "ALIPAY_TRIP_OPEN";

    /**
     * 卡池预占 / 确认 / 释放的出网协作者。
     */
    private final CardPoolAllocationService cardPoolAllocationService;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    /**
     * 渠道无关的开户收口：注册乘车状态、两行落库、字段归一。
     */
    private final RegistrationCommitService registrationCommitService;

    /**
     * 开户成功后按手机号挂接员工码（ADR-D33 从 {@link RegistrationCommitService} 迁来）。
     */
    private final EmployeeCardPersistenceService employeeCardPersistenceService;

    /**
     * 卡池确认被拒时开人工工单（ADR-D52），与 {@code AccountRegistrationServiceImpl} 同口径。
     */
    private final AccountExceptionTicketMapper accountExceptionTicketMapper;

    /**
     * 构造器注入（ADR-D37）。
     */
    public AlipayTripRegistrationServiceImpl(CardPoolAllocationService cardPoolAllocationService,
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
    public AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(AlipayTripRequestApplicationReqDTO request) {
        AlipayTripRequestApplicationRespDTO response = new AlipayTripRequestApplicationRespDTO();
        CardAllocation allocation = null;
        try {
            log.info("开始处理支付宝出行-开卡申请, request={}", JSON.toJSONString(request));

            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("支付宝出行-开卡申请参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardIssueCode = request.getCardIssueCode().trim();

            UserItpRegInfo existed = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
            if (existed != null && existed.isActive()) {
                if (cardIssueCode.equals(existed.getCardIssueCode())) {
                    response.setRetCode(AccountErrorCodeEnum.ALREADY_REGISTERED.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.ALREADY_REGISTERED.getMsg());
                    response.setCardId(existed.getCardId());
                    response.setCardType(existed.getCardType());
                    log.warn("支付宝出行-开卡申请用户已开户, thirdUserId={}, cardId={}, cardType={}",
                            existed.getThirdUserId(), existed.getCardId(), existed.getCardType());
                    return response;
                }
            }

            allocation = cardPoolAllocationService.reserveFromPool(CardTypeMapping.toIssueCardType(request.getCardType()),
                    CARD_POOL_BUSINESS_TYPE_ALIPAY_TRIP_OPEN, thirdUserId, thirdUserId);
            if (allocation == null) {
                response.setRetCode(AccountErrorCodeEnum.NO_CARD_RESOURCE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_CARD_RESOURCE.getMsg());
                log.warn("支付宝出行-开卡申请无可分配卡资源, thirdUserId={}", thirdUserId);
                return response;
            }

            UserItpRegInfo regInfo = buildRegInfo(request, allocation.cardId());
            RegisterRideStatusRespDTO ticketResponse = registrationCommitService.registerRideStatus(regInfo);
            if (ticketResponse == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(ticketResponse.getRetCode())) {
                // NEVER 在失败分支 releaseReservation：预占按 businessId 幂等、是并发请求共享的，超时回收交 sys_job 240（2026-09-21 由 107 改号为 240，ADR-D52）。
                allocation = null;
                response.setRetCode(AccountErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg(ticketResponse == null ? "ticket-server不可用" : ticketResponse.getRetMsg());
                log.error("支付宝出行-开卡申请调用ticket-server失败，预占不释放、等卡池超时回收, thirdUserId={}, response={}",
                        thirdUserId, JSON.toJSONString(ticketResponse));
                return response;
            }

            registrationCommitService.persistRegistration(regInfo);

            boolean confirmed = cardPoolAllocationService.confirmReservation(allocation, "支付宝出行-开卡");
            CardAllocation confirmedAllocation = allocation;
            allocation = null;

            if (!confirmed) {
                // 最后一步 confirm 失败 MUST NOT 返成功、MUST 落 ACCOUNT_EXCEPTION_TICKET 的 CARD_POOL_CONFIRM_REJECTED。
                openCardPoolConfirmTicketQuietly(confirmedAllocation, regInfo);
                response.setRetCode(AccountErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg("卡号确认失败，请稍后重试");
                log.error("支付宝出行-开卡已落库但卡池确认失败，已开工单转人工, thirdUserId={}, cardId={}, reservationId={}",
                        thirdUserId, regInfo.getCardId(), confirmedAllocation.reservationId());
                return response;
            }

            employeeCardPersistenceService.attachEmployeeCardsQuietly(regInfo.getThirdUserId(), regInfo.getMsisdn());

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setCardId(regInfo.getCardId());
            response.setCardType(regInfo.getCardType());
            log.info("支付宝出行-开卡申请成功, thirdUserId={}, cardId={}, cardType={}",
                    regInfo.getThirdUserId(), regInfo.getCardId(), regInfo.getCardType());
            return response;
        } catch (Exception e) {
            log.error("处理支付宝出行-开卡申请异常，预占不释放、等卡池超时回收, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 卡池确认被拒时开一条 {@code CARD_POOL_CONFIRM_REJECTED} 工单转人工。
     */
    private void openCardPoolConfirmTicketQuietly(CardAllocation allocation, UserItpRegInfo regInfo) {
        try {
            AccountExceptionTicket ticket = new AccountExceptionTicket();
            ticket.setTicketType(AccountExceptionTicket.TYPE_CARD_POOL_CONFIRM_REJECTED);
            ticket.setBizKey(allocation.reservationId());
            ticket.setThirdUserId(regInfo.getThirdUserId());
            ticket.setTicketStatus(AccountExceptionTicket.STATUS_OPEN);
            ticket.setRetryCount(0);
            ticket.setDetail("支付宝出行-开卡已落库但卡池确认被拒，cardId=" + regInfo.getCardId()
                    + ", cardType=" + regInfo.getCardType() + ", businessId=" + allocation.businessId());
            ticket.setCreateTms(LocalDateTime.now());
            accountExceptionTicketMapper.insert(ticket);
        } catch (Exception ex) {
            log.error("卡池确认异常工单开立失败或已存在, reservationId={}, cardId={}",
                    allocation.reservationId(), regInfo.getCardId(), ex);
        }
    }

    private String validateRequest(AlipayTripRequestApplicationReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!CardTypeMapping.isSupportedAppCardType(request.getCardType())) {
            return "cardType不支持";
        }
        if (!StringUtils.hasText(request.getMsisdn())) {
            return "msisdn不能为空";
        }
        if (!StringUtils.hasText(request.getCardIssueCode())) {
            return "cardIssueCode不能为空";
        }
        return null;
    }

    /**
     * 支付宝出行开卡的 {@code USER_ITP_REG_INFO} 行。
     */
    private UserItpRegInfo buildRegInfo(AlipayTripRequestApplicationReqDTO request, String cardId) {
        UserItpRegInfo regInfo = new UserItpRegInfo();
        regInfo.setCardId(cardId);
        regInfo.setCardType(CardTypeMapping.toIssueCardType(request.getCardType()));
        regInfo.setItpCardType(request.getCardType().trim());
        regInfo.setThirdUserId(request.getThirdUserId().trim());
        regInfo.setMsisdn(request.getMsisdn());
        regInfo.setRegTms(LocalDateTime.now());
        regInfo.setDelYn(1);
        regInfo.setIssueOrgCode(registrationCommitService.normalizeIssueOrgCode(request.getCardIssueCode()));
        regInfo.setCardIssueCode(request.getCardIssueCode().trim());
        regInfo.setThirdPayId(null);
        regInfo.setChannel(CardTypeCodeEnum.isDailyTicket(CardTypeMapping.toIssueCardType(request.getCardType()))
                ? request.getCardType().trim() : null);
        regInfo.setReqContractNo(null);
        regInfo.setUserName(null);
        regInfo.setUserId(null);
        return regInfo;
    }
}

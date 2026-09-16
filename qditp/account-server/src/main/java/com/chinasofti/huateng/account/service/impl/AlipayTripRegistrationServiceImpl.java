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
 *
 * <p>2026-09-11（ADR-D31）从 {@code AccountRegistrationServiceImpl} 按渠道拆出，<b>逐行照搬、行为不变</b>。
 * 渠道无关的两步（注册乘车状态 / 两行落库）委托给 {@link RegistrationCommitService}，
 * 挂接员工码委托给 {@link EmployeeCardPersistenceService}，
 * <b>NEVER 在本类重新实现它们</b> —— 那正是 ADR-D29 刚清掉的逐字节重复。</p>
 *
 * <p><b>本方法故意不带 {@code @Transactional}</b>：体内有卡池预占 / 注册乘车状态 / 确认与释放，
 * 全是 RPC（AGENTS.md §5.2 禁止事务包住网络调用，2026-08-26 生产事故）。</p>
 */
@Service
public class AlipayTripRegistrationServiceImpl implements AlipayTripRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(AlipayTripRegistrationServiceImpl.class);

    /** 支付宝出行开卡走卡池发号时的业务类型，与 {@code LOGIC_CARD_POOL_CARD.BUSINESS_TYPE} 对应。 */
    private static final String CARD_POOL_BUSINESS_TYPE_ALIPAY_TRIP_OPEN = "ALIPAY_TRIP_OPEN";

    /** 卡池预占 / 确认 / 释放的出网协作者。 */
    private final CardPoolAllocationService cardPoolAllocationService;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    /** 渠道无关的开户收口：注册乘车状态、两行落库、字段归一。 */
    private final RegistrationCommitService registrationCommitService;

    /**
     * 开户成功后按手机号挂接员工码（ADR-D33 从 {@link RegistrationCommitService} 迁来）。
     * <b>MUST 在 confirmReservation 之后调用</b>，它自己吞异常、NEVER 影响开卡结果。
     */
    private final EmployeeCardPersistenceService employeeCardPersistenceService;

    /** 卡池确认被拒时开人工工单（ADR-D52），与 {@code AccountRegistrationServiceImpl} 同口径。 */
    private final AccountExceptionTicketMapper accountExceptionTicketMapper;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
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

            // 1. 参数校验
            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("支付宝出行-开卡申请参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardIssueCode = request.getCardIssueCode().trim();

            // 2. 检查用户是否已在支付宝渠道开户
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

            // 3. 从卡池预占卡号（事务外 RPC）。票种口径与 buildRegInfo 写入的 CARD_TYPE 保持一致。
            allocation = cardPoolAllocationService.reserveFromPool(CardTypeMapping.toIssueCardType(request.getCardType()),
                    CARD_POOL_BUSINESS_TYPE_ALIPAY_TRIP_OPEN, thirdUserId, thirdUserId);
            if (allocation == null) {
                response.setRetCode(AccountErrorCodeEnum.NO_CARD_RESOURCE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_CARD_RESOURCE.getMsg());
                log.warn("支付宝出行-开卡申请无可分配卡资源, thirdUserId={}", thirdUserId);
                return response;
            }

            // 4. 构建注册信息并先注册乘车状态（远端），再落本地
            UserItpRegInfo regInfo = buildRegInfo(request, allocation.cardId());
            RegisterRideStatusRespDTO ticketResponse = registrationCommitService.registerRideStatus(regInfo);
            if (ticketResponse == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(ticketResponse.getRetCode())) {
                // NEVER 在这里 releaseReservation：预占按 businessId 幂等、是并发请求共享的，
                // 释放会把兄弟请求正要 confirm 的卡号抽走（ADR-D52 实测）。滞留的预占交给
                // sys_job 107「卡池维护」的超时回收。
                allocation = null;
                response.setRetCode(AccountErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg(ticketResponse == null ? "ticket-server不可用" : ticketResponse.getRetMsg());
                log.error("支付宝出行-开卡申请调用ticket-server失败，预占不释放、等卡池超时回收, thirdUserId={}, response={}",
                        thirdUserId, JSON.toJSONString(ticketResponse));
                return response;
            }

            // 5. 短事务落库
            registrationCommitService.persistRegistration(regInfo);

            // 6. 确认预占。返回 false 意味着「账户表已把卡号发出去、池子那边却不是 ASSIGNED」，
            //    MUST NOT 返成功（ADR-D52）。
            boolean confirmed = cardPoolAllocationService.confirmReservation(allocation, "支付宝出行-开卡");
            CardAllocation confirmedAllocation = allocation;
            allocation = null;

            if (!confirmed) {
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
            // NEVER releaseReservation，理由同上（ADR-D52）。
            log.error("处理支付宝出行-开卡申请异常，预占不释放、等卡池超时回收, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 卡池确认被拒时开一条 {@code CARD_POOL_CONFIRM_REJECTED} 工单转人工。
     *
     * <p>与 {@code AccountRegistrationServiceImpl#openCardPoolConfirmTicketQuietly} 同口径：
     * {@code BIZ_KEY} 用 {@code reservationId}，靠 {@code UK_ACCT_EXC_TICKET_TYPE_KEY} 去重，
     * 自己吞异常、<b>NEVER 影响已经定型的失败响应</b>。</p>
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
     *
     * <p><b>与 IF8A-01 的 {@code AccountRegistrationServiceImpl#buildRegInfo} 有三处刻意的差异，
     * NEVER 当成漏写去「补齐」</b>：</p>
     * <ul>
     *   <li>{@code COMPANION_FLAG} 留空 —— 该值在 IF8A-01 里来自 APP 上送的
     *       {@code request.getCompanionFlag()}，而支付宝出行的报文里**没有这个字段**（见
     *       {@link #validateRequest} 的必填集合），本渠道天然是单卡本人卡。
     *       连带后果：IF8A-77 换默认支付方式按 {@code COMPANION_FLAG = 'C'} 定位
     *       （{@code selectByThirdUserIdAndCardIssueCodeAndCompanionFlag}），因此**支付宝出行开的卡
     *       走不到 IF8A-77**。要让它能走，MUST 先由支付宝渠道在报文里给出该标识，
     *       <b>NEVER 在这里硬填 'C'</b>——那等于把本人卡错误并入亲情卡集合。</li>
     *   <li>{@code HCE_DATA} 留空 —— 本方法只接 {@code cardId}，压根没有 HCE 分配环节。</li>
     *   <li>{@code CARD_ISSUE_CODE} 存的是**原值**，而 IF8A-01 存的是
     *       {@code CardIssueOrgEnum.toIssueChannelCode4()} 的归一化 4 位码。
     *       <b>2026-09-11 实测：这个差异当前不产生任何后果，此前记的「MUST 先与甲方对齐口径、
     *       改它要迁移历史数据」是过度谨慎，已撤回</b>。理由：该列的真实语义是
     *       <b>发行渠道码</b>（{@code "00" + IssueChannelCodeEnum.code}），机构原值在
     *       {@code ISSUE_ORG_CODE}；而支付宝出行的机构码 {@code 0007} 经
     *       {@code toIssueChannelCode4} 得到的正是 {@code "00" + ALIPAY("07")} = {@code 0007}
     *       —— <b>与原值逐字相同</b>。全库 32 行该列单一取值 {@code 0001}（= 青岛 5412 / 成都 0008
     *       两个 NORMAL 机构归一化的结果），**零行需要迁移**。
     *       只在支付宝渠道上送 {@code 0007} 之外的机构码时才会分叉（本侧存原值、IF8A-01 会归成
     *       {@code 0001}），属边缘情形。**真要改就直接改成调 {@code toIssueChannelCode4}，
     *       NEVER 再把它当成需要甲方决策的阻塞项。**</li>
     * </ul>
     *
     * <p>2026-09-11 只补齐了 {@code ISSUE_ORG_CODE}：IF8A-01 的口径是「原值进 {@code ISSUE_ORG_CODE}」，
     * 此处原先整列不写，导致同一张表的两条写入路径对同名列语义不一致、运营后台按发卡机构统计时
     * 支付宝出行卡全部漏掉。补写只新增列值、不改任何既有列，无需迁移历史数据。</p>
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
        // 开户阶段未提供，留空，后续签约时更新
        regInfo.setThirdPayId(null);
        regInfo.setChannel(CardTypeCodeEnum.isDailyTicket(CardTypeMapping.toIssueCardType(request.getCardType()))
                ? request.getCardType().trim() : null);
        regInfo.setReqContractNo(null);
        regInfo.setUserName(null);
        regInfo.setUserId(null);
        return regInfo;
    }
}

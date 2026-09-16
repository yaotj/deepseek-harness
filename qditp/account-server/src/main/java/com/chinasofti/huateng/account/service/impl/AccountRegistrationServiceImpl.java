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

/**
 * IF8A-01 APP 开户发号实现，见 {@link AccountRegistrationService}。
 *
 * <p>2026-09-11（ADR-D31）按渠道拆分：**支付宝出行开卡已搬到
 * {@code AlipayTripRegistrationServiceImpl}**，本类只留 APP 渠道。
 * <b>NEVER 把任何渠道的开卡入口加回本类</b> —— 此前两个渠道塞在一个类里、靠方法名前缀区分，
 * 直接后果是产生过两对逐字节重复的 helper（ADR-D29 清理）。</p>
 *
 * <p>渠道无关的三步（注册乘车状态 / 两行落库 / 字段归一）委托给
 * {@link RegistrationCommitService}，挂接员工码委托给 {@link EmployeeCardPersistenceService}，
 * <b>NEVER 在本类重新实现</b>。</p>
 *
 * <p><b>本方法故意不带 {@code @Transactional}</b>：卡池预占、安全服务发号、ticket-server 注册乘车状态、
 * 卡池确认与释放全是 RPC，AGENTS.md §5.2 禁止事务包住网络调用（2026-08-26 生产事故）。
 * 落库由 {@code RegistrationCommitService#persistRegistration} 自开短事务。</p>
 *
 * <p><b>NEVER 把销户、换号、支付通道、查询挪进本类</b>：它们分别在 {@code AccountCancelServiceImpl}、
 * {@code PhoneChangeServiceImpl}、{@code PayChannelServiceImpl}、{@code AccountProfileServiceImpl}。</p>
 */
@Service
public class AccountRegistrationServiceImpl implements AccountRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(AccountRegistrationServiceImpl.class);

    /**
     * 支付宝<b>渠道</b>码。鲁通码校验与默认通道推导要用，**与支付宝出行渠道的开卡入口无关**。
     *
     * <p><b>同值异义警告</b>：{@code CardPoolAllocationServiceImpl.APP_CARD_TYPE_HCE} 也是
     * {@code "03"}，但那是 APP 上送的<b>票种</b>码（HCE 卡）。两者取值相同、含义无关，
     * 全局 grep {@code "03"} 会同时命中。<b>NEVER 把两者合并、互相引用，或据「另一处也是 03」
     * 推断本处语义。</b></p>
     */
    private static final String ALIPAY_PAYMENT_CHANNEL = "03";

    /**
     * IF8A-01 开户走卡池发号时的业务类型，与 {@code LOGIC_CARD_POOL_CARD.BUSINESS_TYPE} 对应。
     * 该值参与卡池的归属唯一约束，**NEVER** 与其它场景复用同一取值。
     */
    private static final String CARD_POOL_BUSINESS_TYPE_ACCOUNT_OPEN = "ACCOUNT_OPEN";

    /** 开户发号（卡池预占 / 确认 / 释放、HCE 取卡）的出网协作者。 */
    private final CardPoolAllocationService cardPoolAllocationService;

    /** 只用于开户前的重复开户查重；两行落库在 {@link RegistrationCommitService} 里。 */
    private final UserItpRegInfoMapper userItpRegInfoMapper;

    /** 渠道无关的开户收口：注册乘车状态、两行落库、字段归一。 */
    private final RegistrationCommitService registrationCommitService;

    /**
     * 开户成功后按手机号挂接员工码（ADR-D33 从 {@link RegistrationCommitService} 迁来）。
     * <b>MUST 在 confirmReservation 之后调用</b>，它自己吞异常、NEVER 影响开户结果。
     */
    private final EmployeeCardPersistenceService employeeCardPersistenceService;

    /**
     * 卡池确认被拒时开异常工单（ADR-D52）。直接注入 Mapper 而不是 {@code AccountExceptionTicketService}：
     * 那个接口只有运营侧的 {@code list} / {@code close}，且其类注释明确写着
     * <b>NEVER 让自动流程调 close</b>；开单在本项目一直是各业务实现自己 insert
     * （见 {@code PhoneChangeServiceImpl} 与 {@code EmployeeCardServiceImpl} 的同款写法），
     * <b>NEVER 为了「统一」把开单加进那个运营接口</b>。
     */
    private final AccountExceptionTicketMapper accountExceptionTicketMapper;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
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

    /**
     * IF8A-01 请求开户。
     *
     * <p><b>本方法故意不带 {@code @Transactional}</b>：卡池预占、安全服务发号、ticket-server 注册乘车状态、
     * 卡池确认 / 释放全是 RPC，AGENTS.md §5.2 禁止把网络调用包在事务里。编排顺序按同节「先调远端、后改本地」：
     * 预占卡号 → 注册乘车状态 → 短事务落库 → 确认预占。落库失败时乘车状态留在远端等重推（cardId 由卡池按
     * businessId 幂等发放，重推拿到的是同一张卡号，不会产生第二条乘车状态）；<b>任何失败分支都不 release 预占</b>
     * （ADR-D52：预占按 businessId 幂等、为并发同 businessId 请求共享，释放会把兄弟请求正要 confirm 的卡号抽走），
     * 滞留的预占一律由 {@code sys_job} 107「卡池维护」的超时回收兜底。</p>
     */
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
            if (!isMultiCardCompanionFlag(request.getCompanionFlag())) {
                UserItpRegInfo existed = userItpRegInfoMapper.selectActiveByThirdUserIdAndCardType(thirdUserId, cardType);
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
                // NEVER 在这里 releaseReservation：预占按 businessId 幂等、是并发请求共享的，
                // 释放会把兄弟请求正要 confirm 的卡号抽走（ADR-D52 实测）。滞留的预占交给
                // sys_job 107「卡池维护」的超时回收。
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
                if (!isDuplicateKeyViolation(e)) {
                    throw e;
                }
                RequestApplicationResult duplicated = handleDuplicateRegistration(thirdUserId, cardType, e);
                allocation = null;
                return duplicated;
            }

            boolean confirmed = cardPoolAllocationService.confirmReservation(allocation, "IF8A-01开户");
            CardAllocation confirmedAllocation = allocation;
            allocation = null;

            if (!confirmed) {
                // 账户表已落库、卡号已归属该用户，但池子那边不是 ASSIGNED（很可能被并发的兄弟请求
                // release 回 AVAILABLE），同一卡号随时会被再发给别人。NEVER 返 0000（ADR-D52 实测过
                // 那样 APP 完全不知情）；也 NEVER 在这里去改卡池状态——分不清「该对齐」还是「该真释放」。
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
            // NEVER 在这里 releaseReservation（ADR-D52）：预占是同 businessId 并发请求共享的，
            // 本请求异常不代表兄弟请求也失败，释放会把兄弟正要 confirm 的卡号抽走。
            // 滞留预占由 sys_job 107「卡池维护」超时回收。
            log.error("处理IF8A-01请求开户异常，预占不释放、等卡池超时回收, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 卡池确认被拒时开工单转人工。**本方法 NEVER 抛异常**，工单开立失败也只记 ERROR，
     * 避免掩盖上一层的真实失败原因（与 {@code EmployeeCardServiceImpl.openActivationTicketQuietly} 同口径）。
     *
     * <p>{@code BIZ_KEY} 取 {@code reservationId}，配合
     * {@code UK_ACCT_EXC_TICKET_TYPE_KEY (TICKET_TYPE, BIZ_KEY)} 天然幂等：
     * APP 重推同一笔时预占 id 不变，只会有一张工单。</p>
     */
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

    /**
     * 落库撞唯一索引时的兜底：释放预占并按「已开户」返回，与 {@code requestApplication} 前置查重同一口径。
     *
     * <p>存在这段的原因是前置查重 {@code selectActiveByThirdUserIdAndCardType} 与 INSERT 之间有窗口，
     * 两条并发的 IF8A-01（或 APP 超时重推）会双双通过查重、双双落库，用户拿到两张有效卡。
     * 真正的防线是数据库唯一索引 {@code UK_UIRI_ACTIVE_USER_CARDTYPE}
     * （{@code account-server/src/main/resources/sql/account-server-schema.sql}），本方法只负责把
     * 索引抛出的冲突翻译成业务响应。</p>
     *
     * <p><b>该索引已于 2026-09-14 在 AFCITPDB 建好并回查</b>（`UNIQUE` / `FUNCTION-BASED NORMAL` /
     * `VALID`，两列表达式与 schema 文件一致），因此本分支现在是可达的 —— 本段此前写「尚未执行、
     * 本分支不可达」，**已作废、NEVER 回退**。<b>NEVER</b> 把索引改成普通索引或删掉本方法。</p>
     *
     * <p><b>但本分支至今没有被真实请求走到过</b>：2026-09-14 用两条并发 IF8A-01 实测（同一
     * {@code thirdUserId} + {@code cardType}），两条**根本没有双双到达 INSERT** —— 卡池
     * {@code reserveFromPool} 按 {@code businessId=thirdUserId:票种} 幂等，两条拿到的是
     * <b>同一个 cardNo 与同一个 reservationId</b>，其中一条在 ticket-server 环节先失败并
     * {@code releaseReservation} 掉那个共享预占，另一条随后落库成功却 confirm 失败。详见
     * ADR-D52。也就是说唯一索引防的是「两条都插进来」，而当前编排下更早暴露的是「共享预占被
     * 兄弟请求释放」，两者是不同的缺陷，<b>NEVER 把本分支的存在当成并发已闭环的证据</b>。</p>
     *
     * <p>返回字段 <b>MUST</b> 与前置查重分支逐字段一致（{@code cardId} / {@code cardType} /
     * {@code signType="00"} / {@code sign=""}）：APP 侧对这两条路径用同一段解析代码。
     * 回查为空时（冲突后另一条并发把该行销户了）只填错误码，<b>NEVER</b> 回填本次未落库的
     * {@code regInfo.cardId}。</p>
     *
     * <p><b>本方法 NEVER releaseReservation</b>（2026-09-14 / ADR-D52 起，此前会释放）。
     * 撞唯一索引恰恰证明**兄弟请求已经落库成功**，而单卡场景下两条请求共享同一个
     * {@code businessId} ⇒ 同一个 {@code reservationId} ⇒ **同一张卡号**，那张卡此刻正是
     * 兄弟刚写进 {@code USER_ITP_REG_INFO} 的那张。在这里释放等于把已发出去的卡号退回池子，
     * 就是 ADR-D52 那个「账户表有效、池子 AVAILABLE」缺陷的另一条触发路径。</p>
     */
    private RequestApplicationResult handleDuplicateRegistration(String thirdUserId, String cardType,
                                                                 Exception cause) {
        RequestApplicationResult response = new RequestApplicationResult();
        response.setRetCode(AccountErrorCodeEnum.ALREADY_REGISTERED.getCode());
        response.setRetMsg(AccountErrorCodeEnum.ALREADY_REGISTERED.getMsg());
        response.setSignType("00");
        response.setSign("");
        UserItpRegInfo existed = userItpRegInfoMapper.selectActiveByThirdUserIdAndCardType(thirdUserId, cardType);
        if (existed != null) {
            response.setCardId(existed.getCardId());
            response.setCardType(existed.getCardType());
        }
        log.warn("IF8A-01开户撞唯一约束，按已开户返回, thirdUserId={}, cardType={}, existedCardId={}",
                thirdUserId, cardType, existed == null ? null : existed.getCardId(), cause);
        return response;
    }

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     *
     * <p><b>MUST</b> 逐层遍历 cause，<b>NEVER</b> 直接 {@code catch (DuplicateKeyException)}：
     * {@code MapperAspectToTrace}（{@code resource/micro/web/src/main/java/com/chinasofti/huateng/
     * micro/monitor/trace/MapperAspectToTrace.java:51}）把 mapper 抛出的任何异常统一包成
     * {@code RuntimeException}，单层类型判断在本项目里捕不到。</p>
     *
     * <p>与 {@code PayChannelServiceImpl} / {@code PhoneChangeServiceImpl} 的同名方法同源，
     * 按项目「NEVER 主动新建工具类」的约定各类保留一份私有副本（另见
     * {@code SupplementOrderServiceImpl.isDuplicateKeyViolation}）。</p>
     */
    private boolean isDuplicateKeyViolation(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 APP 卡类型获取开户使用的逻辑卡号。
     *
     * <p>{@code 03}（HCE卡）和 {@code 04}（新版HCE卡）由安全服务生成 HCE 卡数据并返回逻辑卡号，
     * 不进卡池、也没有预占可确认；其余票种一律从 card-pool-server 预占。
     * <b>本方法内部全是 RPC，MUST 在事务外调用。</b></p>
     *
     * @param request      APP 开户请求
     * @param thirdUserId  第三方用户标识
     * @param issueCardType 转换后的票种码（044X）
     * @return 开户逻辑卡号；无法获取时返回 {@code null}
     */
    private CardAllocation allocateCard(RequestApplicationReqDTO request, String thirdUserId, String issueCardType) {
        if (cardPoolAllocationService.isHceCard(request.getCardType())) {
            HceCardAllocation hce = cardPoolAllocationService.requestHceCardData(thirdUserId, request.getTicketCard());
            return hce == null ? null : new CardAllocation(hce.cardId(), hce.hceData(), null, null);
        }
        return cardPoolAllocationService.reserveFromPool(issueCardType, CARD_POOL_BUSINESS_TYPE_ACCOUNT_OPEN,
                buildAccountOpenBusinessId(request, thirdUserId, issueCardType), thirdUserId);
    }

    /**
     * 组装开户预占的业务流水号，决定卡池发号的幂等边界。
     *
     * <p>单卡场景（{@code companionFlag} 非 Y/C）用 {@code thirdUserId:票种}，重复请求拿到同一张卡号，
     * 上游重推不会额外消耗号段。同行票 / 第三方票（Y/C）按业务定义「每次请求都给一张新卡」，
     * 因此追加一次性 UUID —— 这类请求 <b>不具备幂等性</b>，重推会多发一张卡，这是业务要求而非缺陷。</p>
     *
     * @param request       APP 开户请求
     * @param thirdUserId   第三方用户标识
     * @param issueCardType 票种码
     * @return 业务流水号
     */
    private String buildAccountOpenBusinessId(RequestApplicationReqDTO request, String thirdUserId,
                                              String issueCardType) {
        String base = thirdUserId + ":" + issueCardType;
        return isMultiCardCompanionFlag(request.getCompanionFlag())
                ? base + ":" + UUID.randomUUID()
                : base;
    }

    private String validateRequest(RequestApplicationReqDTO request) {
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

    /**
     * IF8A-01 的 {@code USER_ITP_REG_INFO} 行。
     *
     * <p>与支付宝出行渠道那份（{@code AlipayTripRegistrationServiceImpl#buildRegInfo}）**入参 DTO 类型不同、
     * 字段集合真实分叉**（{@code COMPANION_FLAG} / {@code HCE_DATA} / {@code CARD_ISSUE_CODE} 归一化），
     * 属正当分化，<b>NEVER 合并成一个方法</b> —— 合并只能靠 {@code instanceof} 或再造中间 DTO，两者都更差。</p>
     */
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
        return regInfo;
    }

    private String resolveDefaultChannel(RequestApplicationReqDTO request) {
        if (CardTypeMapping.isAiShanDong(request.getCardType())) {
            return ALIPAY_PAYMENT_CHANNEL;
        }
        return CardTypeCodeEnum.isDailyTicket(CardTypeMapping.toIssueCardType(request.getCardType()))
                ? request.getCardType().trim() : request.getChannel();
    }

    private boolean isMultiCardCompanionFlag(String companionFlag) {
        return "Y".equals(companionFlag == null ? null : companionFlag.trim())
                || "C".equals(companionFlag == null ? null : companionFlag.trim());
    }
}

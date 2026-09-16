package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.service.RegistrationCommitService;
import com.chinasofti.huateng.model.enums.CardIssueOrgEnum;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 开户收口实现，见 {@link RegistrationCommitService}。
 *
 * <p>2026-09-11（ADR-D31）从 {@code AccountRegistrationServiceImpl} 逐行搬来，<b>行为不变</b>：
 * 注册乘车状态的报文组装、两行落库的短事务、发卡机构码归一的「打 ERROR 不拒绝」全部照搬。
 * 同批搬来的 {@code attachEmployeeCardsQuietly} 已于 ADR-D33 收尾时迁往
 * {@code EmployeeCardPersistenceService}，<b>NEVER 迁回</b>。</p>
 *
 * <p><b>本类不带 `@Transactional`</b>：唯一需要事务的是 {@link #persistRegistration}，
 * 它自己用 {@link #transactionTemplate} 开短事务，好处是**调用方的 RPC 不会被卷进来**
 * （AGENTS.md §5.2，2026-08-26 生产事故）。<b>NEVER 给本类或本类方法加 `@Transactional`</b>。</p>
 */
@Service
public class RegistrationCommitServiceImpl implements RegistrationCommitService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationCommitServiceImpl.class);

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserItpRegLogMapper userItpRegLogMapper;

    private final TicketClient ticketClient;

    /**
     * {@link #persistRegistration} 的两行写用它开短事务。
     * <b>NEVER</b> 改成给方法加 `@Transactional` —— 那会把调用方的 RPC 一起圈进事务。
     */
    private final TransactionTemplate transactionTemplate;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public RegistrationCommitServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper,
                                        UserItpRegLogMapper userItpRegLogMapper,
                                        TicketClient ticketClient,
                                        TransactionTemplate transactionTemplate) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userItpRegLogMapper = userItpRegLogMapper;
        this.ticketClient = ticketClient;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public RegisterRideStatusRespDTO registerRideStatus(UserItpRegInfo regInfo) {
        return ticketClient.registerRideStatus(buildTicketRequest(regInfo));
    }

    @Override
    public void persistRegistration(UserItpRegInfo regInfo) {
        transactionTemplate.executeWithoutResult(status -> {
            userItpRegInfoMapper.insert(regInfo);
            userItpRegLogMapper.insert(buildRegLog(regInfo));
        });
    }

    /**
     * 组装 ticket-server 的注册乘车状态请求。**两条开户链路共用**。
     *
     * <p>2026-09-11 先由 ADR-D29 合并掉逐字节重复的 {@code buildAlipayTicketRequest}，
     * 再由 ADR-D31 随本类搬出。{@link RegisterRideStatusReqDTO} 在 `model` 模块、是跨模块契约，
     * 加字段时漏改<b>不会编译失败</b>、只会让 ticket-server 收到 null，
     * 因此**全仓库只准有这一处组装**。</p>
     */
    private RegisterRideStatusReqDTO buildTicketRequest(UserItpRegInfo regInfo) {
        RegisterRideStatusReqDTO request = new RegisterRideStatusReqDTO();
        request.setThirdUserId(regInfo.getThirdUserId());
        request.setCardId(regInfo.getCardId());
        request.setCardType(regInfo.getCardType());
        request.setMsisdn(regInfo.getMsisdn());
        request.setCardIssueCode(regInfo.getCardIssueCode());
        request.setChannel(regInfo.getChannel());
        request.setThirdPayId(regInfo.getThirdPayId());
        return request;
    }

    /** 开户流水行。**两条开户链路共用**，`OPER_TYPE=0` 表示开户。 */
    private UserItpRegLog buildRegLog(UserItpRegInfo regInfo) {
        UserItpRegLog regLog = new UserItpRegLog();
        regLog.setCardId(regInfo.getCardId());
        regLog.setCardType(regInfo.getCardType());
        regLog.setThirdUserId(regInfo.getThirdUserId());
        regLog.setMsisdn(regInfo.getMsisdn());
        regLog.setOperDateTime(LocalDateTime.now());
        regLog.setOperType(0);
        return regLog;
    }

    @Override
    public String normalizeIssueOrgCode(String cardIssueCode) {
        String trimmed = cardIssueCode == null ? null : cardIssueCode.trim();
        if (CardIssueOrgEnum.fromCode(trimmed) == null) {
            log.error("开户遇到未知发卡机构码, cardIssueCode={}, 已归一为发行渠道 {}",
                    trimmed, CardIssueOrgEnum.DEFAULT_ISSUE_CHANNEL_CODE_4);
        }
        return trimmed;
    }
}
